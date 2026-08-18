package com.example.moneymanagerpro;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.moneymanagerpro.database.DatabaseClient;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Applies edits to an already-linked Family Hub transaction without deleting and
 * recreating the MoneyManager ledger row.
 *
 * Safety rules:
 * - only exact Family Hub queue identities are accepted;
 * - only MoneyManager rows carrying the exact TRIDEV_EVENT marker and
 *   "Synced from Family Hub" provenance may be changed;
 * - manually-created/reconciled MoneyManager rows are preserved untouched;
 * - account/card/category values must still resolve to active existing
 *   MoneyManager masters; nothing is created or renamed here.
 */
public final class TridevFamilyHubEditManager {

    private static final String QUEUE_DB = "TridevIntegrationQueue.db";
    private static final String QUEUE_TABLE = "integration_events";
    private static final String MARKER_PREFIX = "TRIDEV_EVENT:";

    public static final class Result {
        public final boolean handled;
        public final boolean updated;
        public final boolean preserved;
        @Nullable public final String transactionId;
        public final String reason;

        private Result(boolean handled, boolean updated, boolean preserved,
                       @Nullable String transactionId, @NonNull String reason) {
            this.handled = handled;
            this.updated = updated;
            this.preserved = preserved;
            this.transactionId = transactionId;
            this.reason = reason;
        }
    }

    private static final class Destination {
        final String account;
        final String category;
        final String moneyType;

        Destination(String account, String category, String moneyType) {
            this.account = account;
            this.category = category;
            this.moneyType = moneyType;
        }
    }

    private final Context appContext;
    private final TridevEventQueue queue;

    public TridevFamilyHubEditManager(@NonNull Context context) {
        appContext = context.getApplicationContext();
        queue = TridevEventQueue.getInstance(appContext);
    }

    @NonNull
    public Result updateFamilyEvent(
            @NonNull String canonicalEventId,
            @NonNull String canonicalSourceRecordId,
            @NonNull TridevIntegrationContract.Event updatedEvent) {
        String eventId = clean(canonicalEventId);
        String sourceRecordId = clean(canonicalSourceRecordId);
        if (eventId.isEmpty() || sourceRecordId.isEmpty()
                || updatedEvent == null
                || !TridevIntegrationContract.APP_FAMILY_HUB.equals(updatedEvent.sourceApp)) {
            return failure("Family Hub edit identity is invalid");
        }

        TridevEventQueue.QueueItem original = queue.find(eventId);
        if (original == null || original.event == null
                || !TridevIntegrationContract.APP_FAMILY_HUB.equals(original.event.sourceApp)
                || !sourceRecordId.equals(clean(original.event.sourceRecordId))) {
            return failure("Original Family Hub link could not be verified");
        }

        if (!compatibleFamilyTypes(original.event.eventType, updatedEvent.eventType)) {
            return failure("Edited Family Hub event does not match the original transaction type");
        }

        String linkedId = original.event.references == null
                ? "" : clean(original.event.references.moneyManagerTransactionId);
        long transactionId = findAutoCreatedFamilyHubTransaction(eventId, linkedId);
        if (transactionId <= 0L) {
            if (parsePositiveLong(linkedId) > 0L) {
                return preserved(linkedId,
                        "Existing/manual MoneyManager transaction was preserved; Family Hub may not rewrite it automatically");
            }
            return failure("Original Family Hub transaction is not available in MoneyManager");
        }

        Destination destination = resolveDestination(updatedEvent);
        if (destination == null) {
            return failure("Edited account/card or category is not an active exact MoneyManager master");
        }

        if (!updateLedgerRow(eventId, transactionId, updatedEvent, destination)) {
            return failure("Linked MoneyManager transaction could not be updated safely");
        }

        // Queue metadata is refreshed best-effort while its original identity is
        // intentionally retained. That original identity remains the canonical
        // key for later edits and safe deletion/undo.
        refreshCanonicalQueueMetadata(
                eventId, sourceRecordId, String.valueOf(transactionId), updatedEvent);

        return new Result(true, true, false, String.valueOf(transactionId),
                "Linked MoneyManager transaction updated in place");
    }

    private boolean compatibleFamilyTypes(
            @NonNull TridevIntegrationContract.EventType original,
            @NonNull TridevIntegrationContract.EventType updated) {
        if (original == TridevIntegrationContract.EventType.GROCERY_PURCHASE) {
            return updated == TridevIntegrationContract.EventType.GROCERY_PURCHASE;
        }
        boolean originalFinance = original == TridevIntegrationContract.EventType.EXPENSE
                || original == TridevIntegrationContract.EventType.INCOME;
        boolean updatedFinance = updated == TridevIntegrationContract.EventType.EXPENSE
                || updated == TridevIntegrationContract.EventType.INCOME;
        return originalFinance && updatedFinance;
    }

    @Nullable
    private Destination resolveDestination(@NonNull TridevIntegrationContract.Event event) {
        String moneyType;
        if (event.eventType == TridevIntegrationContract.EventType.INCOME) {
            moneyType = "INCOME";
        } else if (event.eventType == TridevIntegrationContract.EventType.EXPENSE
                || event.eventType == TridevIntegrationContract.EventType.GROCERY_PURCHASE) {
            moneyType = "EXPENSE";
        } else {
            return null;
        }

        String accountRef = clean(event.accountHint);
        String categoryRef = clean(event.categoryHint);
        if (accountRef.isEmpty() || categoryRef.isEmpty()) return null;

        TridevMoneyMappingEngine.Catalog catalog =
                new TridevMoneyMappingEngine(appContext).readCatalog();
        String accountValue = "";
        for (TridevMoneyMappingEngine.CatalogItem item : catalog.accounts) {
            if (item != null && !item.unavailableForNewPosting
                    && accountRef.equalsIgnoreCase(clean(item.canonicalRef))) {
                accountValue = clean(item.transactionValue);
                break;
            }
        }
        if (accountValue.isEmpty()) {
            for (TridevMoneyMappingEngine.CatalogItem item : catalog.creditCards) {
                if (item != null && !item.unavailableForNewPosting
                        && accountRef.equalsIgnoreCase(clean(item.canonicalRef))) {
                    accountValue = clean(item.transactionValue);
                    break;
                }
            }
        }
        if (accountValue.isEmpty()) return null;

        String categoryValue = "";
        for (TridevMoneyMappingEngine.CategoryCatalogItem item : catalog.categories) {
            if (item == null || !categoryRef.equalsIgnoreCase(clean(item.canonicalRef))) continue;
            String type = clean(item.type);
            if (!type.isEmpty() && !moneyType.equalsIgnoreCase(type)) continue;
            categoryValue = clean(item.name);
            break;
        }
        if (categoryValue.isEmpty()) return null;
        return new Destination(accountValue, categoryValue, moneyType);
    }

    private long findAutoCreatedFamilyHubTransaction(
            @NonNull String eventId,
            @Nullable String linkedTransactionId) {
        SupportSQLiteDatabase db = DatabaseClient.getInstance(appContext)
                .getAppDatabase().getOpenHelper().getReadableDatabase();
        String marker = marker(eventId);
        long linked = parsePositiveLong(linkedTransactionId);
        if (linked > 0L) {
            try (Cursor cursor = db.query(
                    "SELECT id, note FROM transactions WHERE id = ? LIMIT 1",
                    new Object[]{linked})) {
                if (cursor.moveToFirst() && isOwnedNote(cursor.getString(1), marker)) {
                    return cursor.getLong(0);
                }
            } catch (RuntimeException ignored) { }
        }
        try (Cursor cursor = db.query(
                "SELECT id, note FROM transactions WHERE instr(note, ?) > 0 "
                        + "ORDER BY id DESC LIMIT 1",
                new Object[]{marker})) {
            if (!cursor.moveToFirst()) return 0L;
            return isOwnedNote(cursor.getString(1), marker) ? cursor.getLong(0) : 0L;
        } catch (RuntimeException ignored) {
            return 0L;
        }
    }

    private boolean updateLedgerRow(
            @NonNull String canonicalEventId,
            long transactionId,
            @NonNull TridevIntegrationContract.Event event,
            @NonNull Destination destination) {
        if (transactionId <= 0L || event.amountMinor <= 0L) return false;
        String marker = marker(canonicalEventId);
        String note = buildSafeNote(marker, event.merchantHint);
        String date = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                .format(new Date(event.occurredAt > 0L
                        ? event.occurredAt : System.currentTimeMillis()));
        double amount = BigDecimal.valueOf(event.amountMinor)
                .movePointLeft(2).doubleValue();
        final boolean[] updated = {false};
        try {
            DatabaseClient.getInstance(appContext).getAppDatabase().runInTransaction(() -> {
                SupportSQLiteDatabase db = DatabaseClient.getInstance(appContext)
                        .getAppDatabase().getOpenHelper().getWritableDatabase();
                db.execSQL(
                        "UPDATE transactions SET amount = ?, type = ?, category = ?, "
                                + "account = ?, date = ?, note = ? "
                                + "WHERE id = ? AND instr(note, ?) > 0 "
                                + "AND instr(note, 'Synced from Family Hub') > 0",
                        new Object[]{amount, destination.moneyType, destination.category,
                                destination.account, date, note, transactionId, marker});
                try (Cursor cursor = db.query(
                        "SELECT amount, type, category, account, note FROM transactions "
                                + "WHERE id = ? LIMIT 1",
                        new Object[]{transactionId})) {
                    if (cursor.moveToFirst()) {
                        String storedNote = cursor.isNull(4) ? "" : cursor.getString(4);
                        updated[0] = isOwnedNote(storedNote, marker)
                                && destination.moneyType.equalsIgnoreCase(clean(cursor.getString(1)))
                                && destination.category.equals(clean(cursor.getString(2)))
                                && destination.account.equals(clean(cursor.getString(3)));
                    }
                }
            });
        } catch (RuntimeException ignored) {
            return false;
        }
        return updated[0];
    }

    private void refreshCanonicalQueueMetadata(
            @NonNull String canonicalEventId,
            @NonNull String canonicalSourceRecordId,
            @NonNull String transactionId,
            @NonNull TridevIntegrationContract.Event event) {
        SQLiteDatabase db = null;
        try {
            db = SQLiteDatabase.openDatabase(
                    appContext.getDatabasePath(QUEUE_DB).getAbsolutePath(),
                    null, SQLiteDatabase.OPEN_READWRITE);
            ContentValues values = new ContentValues();
            values.put("event_type", event.eventType.name());
            values.put("direction", event.direction.name());
            values.put("scope", event.scope.name());
            values.put("amount_minor", event.amountMinor);
            values.put("currency", TridevIntegrationContract.DEFAULT_CURRENCY);
            values.put("occurred_at", event.occurredAt);
            values.put("account_hint", safeMetadata(event.accountHint, 160));
            values.put("merchant_hint", safeMetadata(event.merchantHint, 120));
            values.put("category_hint", safeMetadata(event.categoryHint, 80));
            values.put("dedupe_fingerprint", TridevEventFingerprint.build(event));
            values.put("sync_state", TridevIntegrationContract.SyncState.SYNCED.name());
            values.put("money_transaction_ref", transactionId);
            values.putNull("duplicate_of_event_id");
            values.put("duplicate_score", 0);
            values.put("last_error", "");
            values.put("updated_at", System.currentTimeMillis());
            db.update(QUEUE_TABLE, values,
                    "event_id = ? AND source_app = ? AND source_record_id = ?",
                    new String[]{canonicalEventId,
                            TridevIntegrationContract.APP_FAMILY_HUB,
                            canonicalSourceRecordId});
        } catch (RuntimeException ignored) {
            // Ledger update remains authoritative; later edits can retry this
            // metadata refresh because the canonical identity never changes.
        } finally {
            if (db != null) {
                try { db.close(); } catch (RuntimeException ignored) { }
            }
        }
    }

    private boolean isOwnedNote(@Nullable String note, @NonNull String marker) {
        String value = note == null ? "" : note;
        return value.contains(marker) && value.contains("Synced from Family Hub");
    }

    @NonNull
    private String buildSafeNote(@NonNull String marker, @Nullable String merchantHint) {
        String merchant = safeMetadata(merchantHint, 60);
        StringBuilder note = new StringBuilder(marker).append(" • Synced from Family Hub");
        if (!merchant.isEmpty()) note.append(" • ").append(merchant);
        return note.length() <= 240 ? note.toString() : note.substring(0, 240);
    }

    @NonNull
    private String marker(@NonNull String eventId) {
        String safe = clean(eventId).replaceAll("[^A-Za-z0-9:_\\-]", "");
        if (safe.length() > 100) safe = safe.substring(0, 100);
        return MARKER_PREFIX + safe;
    }

    private long parsePositiveLong(@Nullable String value) {
        try {
            long parsed = Long.parseLong(clean(value));
            return parsed > 0L ? parsed : 0L;
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    @NonNull
    private String safeMetadata(@Nullable String value, int max) {
        String safe = clean(value).replace('\n', ' ').replace('\r', ' ')
                .replaceAll("\\s+", " ");
        return safe.length() <= max ? safe : safe.substring(0, max).trim();
    }

    @NonNull
    private Result failure(@NonNull String reason) {
        return new Result(false, false, false, null, reason);
    }

    @NonNull
    private Result preserved(@Nullable String transactionId, @NonNull String reason) {
        return new Result(true, false, true, transactionId, reason);
    }

    @NonNull
    private String clean(@Nullable String value) {
        return value == null ? "" : value.trim();
    }
}
