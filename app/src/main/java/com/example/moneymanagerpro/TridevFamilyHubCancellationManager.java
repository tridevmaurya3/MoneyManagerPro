package com.example.moneymanagerpro;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.moneymanagerpro.database.AppDatabase;
import com.example.moneymanagerpro.database.DatabaseClient;

/**
 * STEP 8 - safe cancellation for a Family Hub grocery purchase that was undone.
 *
 * Only a MoneyManager row carrying the exact deterministic TRIDEV_EVENT marker
 * and "Synced from Family Hub" provenance may be deleted. A manual/existing
 * MoneyManager transaction that was merely reconciled to the Family Hub event is
 * never deleted.
 *
 * If SmartSMS or another source independently corroborates the same transaction,
 * the ledger representation is retained. Evidence can be an explicit queue
 * duplicate link OR a same amount/direction event in the reconciliation window.
 * If no ledger row exists yet, the independent event is reopened for normal
 * MoneyManager processing.
 */
public final class TridevFamilyHubCancellationManager {

    private static final String QUEUE_DB = "TridevIntegrationQueue.db";
    private static final String QUEUE_TABLE = "integration_events";
    private static final String MARKER_PREFIX = "TRIDEV_EVENT:";
    private static final long EVIDENCE_WINDOW_MILLIS = 6L * 60L * 60L * 1000L;

    public static final class Result {
        public final boolean handled;
        public final String reason;

        private Result(boolean handled, String reason) {
            this.handled = handled;
            this.reason = reason == null ? "" : reason;
        }
    }

    private final Context appContext;
    private final TridevEventQueue queue;
    private final AppDatabase ledgerDatabase;

    public TridevFamilyHubCancellationManager(@NonNull Context context) {
        appContext = context.getApplicationContext();
        queue = TridevEventQueue.getInstance(appContext);
        ledgerDatabase = DatabaseClient.getInstance(appContext).getAppDatabase();
    }

    @NonNull
    public Result cancelGroceryPurchase(
            @NonNull String eventId,
            @NonNull String sourceRecordId) {
        TridevEventQueue.QueueItem item = queue.find(eventId);
        if (item == null || item.event == null) {
            return success("Family Hub purchase was not present in the integration queue");
        }
        TridevIntegrationContract.Event event = item.event;
        if (!TridevIntegrationContract.APP_FAMILY_HUB.equals(event.sourceApp)
                || event.eventType != TridevIntegrationContract.EventType.GROCERY_PURCHASE
                || !clean(sourceRecordId).equals(clean(event.sourceRecordId))) {
            return failure("Cancellation did not match the original Family Hub purchase");
        }

        long autoCreatedTransactionId = findAutoCreatedTransaction(event.eventId);
        long linkedTransactionId = parseLong(event.references == null
                ? "" : event.references.moneyManagerTransactionId);

        ExternalEvidence evidence = findExternalEvidence(event);
        if (evidence != null) {
            // Another app independently describes the same real-world payment.
            // Never remove the ledger row simply because the Family Hub checklist
            // was undone.
            markCancelled(event.eventId, false);
            if (autoCreatedTransactionId <= 0L && linkedTransactionId <= 0L
                    && !evidence.eventId.isEmpty()) {
                reopenExternalEvidence(evidence.eventId);
                new TridevTransactionPostingEngine(appContext)
                        .process(evidence.eventId);
            }
            return success("Family Hub purchase was cancelled, but an independent finance signal remains active");
        }

        if (autoCreatedTransactionId > 0L) {
            deleteOnlyAutoCreatedFamilyHubRow(event.eventId, autoCreatedTransactionId);
            markCancelled(event.eventId, true);
            return success("Auto-created MoneyManager grocery entry was removed after Family Hub undo");
        }

        // A linked existing/manual MoneyManager row has no Family Hub marker and
        // must remain untouched.
        markCancelled(event.eventId, false);
        if (linkedTransactionId > 0L) {
            return success("Family Hub link was cancelled; existing MoneyManager transaction was preserved");
        }
        return success("Family Hub grocery event was cancelled before any MoneyManager row was created");
    }

    private long findAutoCreatedTransaction(@NonNull String eventId) {
        String marker = marker(eventId);
        SupportSQLiteDatabase db = ledgerDatabase.getOpenHelper().getReadableDatabase();
        try (Cursor cursor = db.query(
                "SELECT id, note FROM transactions WHERE instr(note, ?) > 0 ORDER BY id DESC LIMIT 1",
                new Object[]{marker})) {
            if (!cursor.moveToFirst()) return 0L;
            String note = cursor.isNull(1) ? "" : cursor.getString(1);
            if (note == null || !note.contains("Synced from Family Hub")) return 0L;
            return cursor.getLong(0);
        } catch (RuntimeException ignored) {
            return 0L;
        }
    }

    private void deleteOnlyAutoCreatedFamilyHubRow(
            @NonNull String eventId,
            long transactionId) {
        String marker = marker(eventId);
        ledgerDatabase.runInTransaction(() -> {
            SupportSQLiteDatabase db = ledgerDatabase.getOpenHelper().getWritableDatabase();
            db.execSQL(
                    "DELETE FROM transactions WHERE id = ? AND instr(note, ?) > 0 "
                            + "AND instr(note, 'Synced from Family Hub') > 0",
                    new Object[]{transactionId, marker});
        });
    }

    @Nullable
    private ExternalEvidence findExternalEvidence(
            @NonNull TridevIntegrationContract.Event familyEvent) {
        SQLiteDatabase db = openQueueDatabase();
        if (db == null) return null;
        long center = familyEvent.occurredAt > 0L
                ? familyEvent.occurredAt : familyEvent.createdAt;
        long start = Math.max(0L, center - EVIDENCE_WINDOW_MILLIS);
        long end = center + EVIDENCE_WINDOW_MILLIS;
        try (Cursor cursor = db.rawQuery(
                "SELECT event_id, sync_state FROM " + QUEUE_TABLE
                        + " WHERE event_id <> ? AND source_app <> ? "
                        + "AND amount_minor = ? AND direction = ? "
                        + "AND occurred_at BETWEEN ? AND ? "
                        + "AND sync_state IN (?, ?, ?, ?) "
                        + "ORDER BY CASE WHEN duplicate_of_event_id = ? THEN 0 ELSE 1 END, id ASC LIMIT 1",
                new String[]{
                        familyEvent.eventId,
                        TridevIntegrationContract.APP_FAMILY_HUB,
                        String.valueOf(familyEvent.amountMinor),
                        familyEvent.direction.name(),
                        String.valueOf(start),
                        String.valueOf(end),
                        TridevIntegrationContract.SyncState.SUPERSEDED.name(),
                        TridevIntegrationContract.SyncState.NEEDS_REVIEW.name(),
                        TridevIntegrationContract.SyncState.PENDING.name(),
                        TridevIntegrationContract.SyncState.SYNCED.name(),
                        familyEvent.eventId
                })) {
            if (!cursor.moveToFirst()) return null;
            return new ExternalEvidence(cursor.getString(0), cursor.getString(1));
        } catch (RuntimeException ignored) {
            return null;
        } finally {
            db.close();
        }
    }

    private void reopenExternalEvidence(@NonNull String eventId) {
        SQLiteDatabase db = openQueueDatabase();
        if (db == null) return;
        ContentValues values = new ContentValues();
        values.put("sync_state", TridevIntegrationContract.SyncState.PENDING.name());
        values.putNull("duplicate_of_event_id");
        values.put("duplicate_score", 0);
        values.put("last_error", "");
        values.put("updated_at", System.currentTimeMillis());
        try {
            db.update(QUEUE_TABLE, values, "event_id = ?", new String[]{eventId});
        } finally {
            db.close();
        }
    }

    private void markCancelled(@NonNull String eventId, boolean clearLedgerReference) {
        SQLiteDatabase db = openQueueDatabase();
        if (db == null) return;
        ContentValues values = new ContentValues();
        values.put("sync_state", TridevIntegrationContract.SyncState.SUPERSEDED.name());
        values.putNull("duplicate_of_event_id");
        values.put("duplicate_score", 0);
        values.put("last_error", "Cancelled by Family Hub source");
        values.put("updated_at", System.currentTimeMillis());
        if (clearLedgerReference) values.putNull("money_transaction_ref");
        try {
            db.update(QUEUE_TABLE, values, "event_id = ?", new String[]{eventId});
        } finally {
            db.close();
        }
    }

    @Nullable
    private SQLiteDatabase openQueueDatabase() {
        try {
            // queue.find() above ensures the helper/database has been created.
            return SQLiteDatabase.openDatabase(
                    appContext.getDatabasePath(QUEUE_DB).getPath(),
                    null,
                    SQLiteDatabase.OPEN_READWRITE);
        } catch (RuntimeException unavailable) {
            return null;
        }
    }

    @NonNull
    private String marker(@NonNull String eventId) {
        String safe = clean(eventId).replaceAll("[^A-Za-z0-9:_\\-]", "");
        if (safe.length() > 100) safe = safe.substring(0, 100);
        return MARKER_PREFIX + safe;
    }

    private long parseLong(@Nullable String value) {
        try {
            return Long.parseLong(clean(value));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    @NonNull
    private Result success(@NonNull String reason) {
        return new Result(true, reason);
    }

    @NonNull
    private Result failure(@NonNull String reason) {
        return new Result(false, reason);
    }

    @NonNull
    private String clean(@Nullable String value) {
        return value == null ? "" : value.trim();
    }

    private static final class ExternalEvidence {
        final String eventId;
        final String state;

        ExternalEvidence(@Nullable String eventId, @Nullable String state) {
            this.eventId = eventId == null ? "" : eventId.trim();
            this.state = state == null ? "" : state.trim();
        }
    }
}
