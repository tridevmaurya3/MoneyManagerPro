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

import java.util.ArrayList;
import java.util.List;

/**
 * Source-authoritative cancellation for SmartSMSPro finance evidence.
 *
 * Only a ledger row carrying the exact TRIDEV_EVENT marker and SmartSMSPro
 * provenance may be deleted. A manual/existing MoneyManager transaction that
 * was merely reconciled to the SMS is always preserved. If another Tridev app
 * independently corroborates the same transaction, that evidence is retained
 * and reopened when necessary.
 */
public final class TridevSmartSmsFinanceCancellationManager {

    private static final String QUEUE_DB = "TridevIntegrationQueue.db";
    private static final String QUEUE_TABLE = "integration_events";
    private static final String MARKER_PREFIX = "TRIDEV_EVENT:";
    private static final long EVIDENCE_WINDOW_MILLIS = 6L * 60L * 60L * 1000L;

    public static final class Result {
        public final boolean handled;
        public final boolean ledgerRemoved;
        @NonNull public final String reason;

        private Result(boolean handled, boolean ledgerRemoved, @NonNull String reason) {
            this.handled = handled;
            this.ledgerRemoved = ledgerRemoved;
            this.reason = reason;
        }
    }

    private static final class SmsEvent {
        @NonNull final String eventId;
        final long amountMinor;
        final long occurredAt;
        @NonNull final String direction;

        SmsEvent(@Nullable String eventId,
                 long amountMinor,
                 long occurredAt,
                 @Nullable String direction) {
            this.eventId = clean(eventId);
            this.amountMinor = amountMinor;
            this.occurredAt = occurredAt;
            this.direction = clean(direction);
        }
    }

    private static final class ExternalEvidence {
        @NonNull final String eventId;
        @NonNull final String state;

        ExternalEvidence(@Nullable String eventId, @Nullable String state) {
            this.eventId = clean(eventId);
            this.state = clean(state);
        }
    }

    private final Context appContext;
    private final AppDatabase ledgerDatabase;

    public TridevSmartSmsFinanceCancellationManager(@NonNull Context context) {
        appContext = context.getApplicationContext();
        ledgerDatabase = DatabaseClient.getInstance(appContext).getAppDatabase();
    }

    @NonNull
    public Result cancel(@NonNull String sourceRecordId) {
        String safeSourceRecordId = structuredSourceRecordId(sourceRecordId);
        if (safeSourceRecordId.isEmpty()) {
            return new Result(false, false, "SmartSMS source identity is invalid");
        }

        SQLiteDatabase queueDb = openQueueDatabase();
        if (queueDb == null) {
            return new Result(false, false, "MoneyManager integration queue is unavailable");
        }

        try {
            List<SmsEvent> events = findSmsEvents(queueDb, safeSourceRecordId);
            if (events.isEmpty()) {
                return new Result(true, false,
                        "SmartSMS finance event was already absent from MoneyManager integration");
            }

            ExternalEvidence evidence = findExternalEvidence(queueDb, events);
            boolean ledgerRemoved = false;

            if (evidence == null) {
                for (SmsEvent event : events) {
                    if (deleteOnlyAutoCreatedSmsRow(event.eventId)) {
                        ledgerRemoved = true;
                    }
                }
            }

            markSmsEventsCancelled(queueDb, safeSourceRecordId, ledgerRemoved);

            if (evidence != null) {
                reopenExternalEvidence(queueDb, evidence);
                if (!evidence.eventId.isEmpty()
                        && !TridevIntegrationContract.SyncState.SYNCED.name()
                        .equals(evidence.state)) {
                    new TridevTransactionPostingEngine(appContext).process(evidence.eventId);
                }
                return new Result(true, false,
                        "SmartSMS evidence was cancelled; independent finance evidence was preserved");
            }

            return new Result(true, ledgerRemoved,
                    ledgerRemoved
                            ? "SmartSMS-created MoneyManager transaction was deleted"
                            : "SmartSMS link was cancelled; no source-owned ledger row remained");
        } finally {
            queueDb.close();
        }
    }

    @NonNull
    private List<SmsEvent> findSmsEvents(
            @NonNull SQLiteDatabase db,
            @NonNull String sourceRecordId) {
        List<SmsEvent> result = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(
                "SELECT event_id, amount_minor, occurred_at, direction FROM " + QUEUE_TABLE
                        + " WHERE source_app = ? AND source_record_id = ?"
                        + " ORDER BY id DESC",
                new String[]{
                        TridevIntegrationContract.APP_SMART_SMS,
                        sourceRecordId
                })) {
            while (cursor.moveToNext()) {
                String eventId = cursor.isNull(0) ? "" : cursor.getString(0);
                if (clean(eventId).isEmpty()) continue;
                result.add(new SmsEvent(
                        eventId,
                        cursor.isNull(1) ? 0L : cursor.getLong(1),
                        cursor.isNull(2) ? 0L : cursor.getLong(2),
                        cursor.isNull(3) ? "" : cursor.getString(3)));
            }
        }
        return result;
    }

    @Nullable
    private ExternalEvidence findExternalEvidence(
            @NonNull SQLiteDatabase db,
            @NonNull List<SmsEvent> smsEvents) {
        for (SmsEvent smsEvent : smsEvents) {
            if (smsEvent.amountMinor <= 0L || smsEvent.occurredAt <= 0L
                    || smsEvent.direction.isEmpty()) continue;

            long start = Math.max(0L, smsEvent.occurredAt - EVIDENCE_WINDOW_MILLIS);
            long end = smsEvent.occurredAt + EVIDENCE_WINDOW_MILLIS;
            try (Cursor cursor = db.rawQuery(
                    "SELECT event_id, sync_state FROM " + QUEUE_TABLE
                            + " WHERE source_app <> ? AND amount_minor = ?"
                            + " AND direction = ? AND occurred_at BETWEEN ? AND ?"
                            + " AND sync_state IN (?, ?, ?, ?)"
                            + " ORDER BY id ASC LIMIT 1",
                    new String[]{
                            TridevIntegrationContract.APP_SMART_SMS,
                            String.valueOf(smsEvent.amountMinor),
                            smsEvent.direction,
                            String.valueOf(start),
                            String.valueOf(end),
                            TridevIntegrationContract.SyncState.SUPERSEDED.name(),
                            TridevIntegrationContract.SyncState.NEEDS_REVIEW.name(),
                            TridevIntegrationContract.SyncState.PENDING.name(),
                            TridevIntegrationContract.SyncState.SYNCED.name()
                    })) {
                if (cursor.moveToFirst()) {
                    return new ExternalEvidence(cursor.getString(0), cursor.getString(1));
                }
            }
        }
        return null;
    }

    private boolean deleteOnlyAutoCreatedSmsRow(@NonNull String eventId) {
        String marker = marker(eventId);
        if (marker.isEmpty()) return false;

        SupportSQLiteDatabase ledger = ledgerDatabase.getOpenHelper().getWritableDatabase();
        final List<Long> ids = new ArrayList<>();
        try (Cursor cursor = ledger.query(
                "SELECT id FROM transactions WHERE instr(note, ?) > 0"
                        + " AND instr(note, 'Synced from SmartSMSPro') > 0",
                new Object[]{marker})) {
            while (cursor.moveToNext()) ids.add(cursor.getLong(0));
        }
        if (ids.isEmpty()) return false;

        ledgerDatabase.runInTransaction(() -> {
            for (Long id : ids) {
                ledger.execSQL(
                        "DELETE FROM transactions WHERE id = ? AND instr(note, ?) > 0"
                                + " AND instr(note, 'Synced from SmartSMSPro') > 0",
                        new Object[]{id, marker});
            }
        });
        return true;
    }

    private void markSmsEventsCancelled(
            @NonNull SQLiteDatabase db,
            @NonNull String sourceRecordId,
            boolean clearLedgerReference) {
        ContentValues values = new ContentValues();
        values.put("sync_state", TridevIntegrationContract.SyncState.SUPERSEDED.name());
        values.putNull("duplicate_of_event_id");
        values.put("duplicate_score", 0);
        values.put("last_error", "Cancelled by SmartSMS source");
        values.put("updated_at", System.currentTimeMillis());
        if (clearLedgerReference) values.putNull("money_transaction_ref");
        db.update(
                QUEUE_TABLE,
                values,
                "source_app = ? AND source_record_id = ?",
                new String[]{TridevIntegrationContract.APP_SMART_SMS, sourceRecordId});
    }

    private void reopenExternalEvidence(
            @NonNull SQLiteDatabase db,
            @NonNull ExternalEvidence evidence) {
        if (evidence.eventId.isEmpty()
                || TridevIntegrationContract.SyncState.SYNCED.name().equals(evidence.state)
                || TridevIntegrationContract.SyncState.PENDING.name().equals(evidence.state)) {
            return;
        }
        ContentValues values = new ContentValues();
        values.put("sync_state", TridevIntegrationContract.SyncState.PENDING.name());
        values.putNull("duplicate_of_event_id");
        values.put("duplicate_score", 0);
        values.put("last_error", "");
        values.put("updated_at", System.currentTimeMillis());
        db.update(QUEUE_TABLE, values, "event_id = ?", new String[]{evidence.eventId});
    }

    @Nullable
    private SQLiteDatabase openQueueDatabase() {
        try {
            return SQLiteDatabase.openDatabase(
                    appContext.getDatabasePath(QUEUE_DB).getPath(),
                    null,
                    SQLiteDatabase.OPEN_READWRITE);
        } catch (RuntimeException unavailable) {
            return null;
        }
    }

    @NonNull
    private static String marker(@Nullable String eventId) {
        String safe = clean(eventId).replaceAll("[^A-Za-z0-9:_\\-]", "");
        if (safe.isEmpty()) return "";
        if (safe.length() > 100) safe = safe.substring(0, 100);
        return MARKER_PREFIX + safe;
    }

    @NonNull
    private static String structuredSourceRecordId(@Nullable String value) {
        String safe = clean(value);
        if (!safe.matches("sms:[0-9]+")) return "";
        return safe.length() <= 160 ? safe : "";
    }

    @NonNull
    private static String clean(@Nullable String value) {
        return value == null ? "" : value.trim();
    }
}
