package com.example.moneymanagerpro;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.moneymanagerpro.database.DatabaseClient;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Reconciliation-only helper for SmartSMS history that predates automatic sync.
 *
 * Historical MoneyManager rows may contain only amount, direction and a date.
 * We auto-link only when there is exactly one same-day transaction with the same
 * two-decimal amount and Income/Expense direction. Ambiguity stays in the visible
 * Reconciliation Center. This class never creates, edits or deletes a ledger row.
 */
public final class TridevHistoricalSmartSmsReconciler {

    private static final String QUEUE_DB = "TridevIntegrationQueue.db";
    private static final String QUEUE_TABLE = "integration_events";
    private static final int MAX_AMOUNT_CANDIDATES = 250;

    public static final class Preparation {
        @NonNull public final String queueEventId;
        @NonNull public final TridevIntegrationContract.SyncState state;
        @Nullable public final String transactionId;
        public final boolean existingSource;

        private Preparation(
                @NonNull String queueEventId,
                @NonNull TridevIntegrationContract.SyncState state,
                @Nullable String transactionId,
                boolean existingSource) {
            this.queueEventId = queueEventId;
            this.state = state;
            this.transactionId = cleanToNull(transactionId);
            this.existingSource = existingSource;
        }
    }

    public static final class Result {
        public final boolean linked;
        public final long transactionId;
        @NonNull public final String reason;

        private Result(boolean linked, long transactionId, @NonNull String reason) {
            this.linked = linked;
            this.transactionId = transactionId;
            this.reason = reason;
        }
    }

    private final Context appContext;
    private final TridevEventQueue queue;

    public TridevHistoricalSmartSmsReconciler(@NonNull Context context) {
        appContext = context.getApplicationContext();
        queue = TridevEventQueue.getInstance(appContext);
    }

    /**
     * Upgrade safety: an older build may already have the same sms:<id> queued as
     * PENDING/FAILED. Before the new history-only request reaches the coordinator,
     * force that existing source into NEEDS_REVIEW so it can never be posted as a
     * fresh transaction merely because an app update retried it.
     */
    @NonNull
    public Preparation prepare(@NonNull TridevIntegrationContract.Event incoming) {
        if (!isHistoricalSmartSms(incoming)) {
            return new Preparation(
                    incoming.eventId,
                    incoming.syncState,
                    null,
                    false);
        }

        File file = appContext.getDatabasePath(QUEUE_DB);
        if (file == null || !file.exists()) {
            return new Preparation(
                    incoming.eventId,
                    TridevIntegrationContract.SyncState.NEEDS_REVIEW,
                    null,
                    false);
        }

        SQLiteDatabase db = null;
        try {
            db = SQLiteDatabase.openDatabase(
                    file.getAbsolutePath(),
                    null,
                    SQLiteDatabase.OPEN_READWRITE);

            String existingEventId = "";
            String rawState = "";
            String transactionRef = "";
            try (Cursor cursor = db.rawQuery(
                    "SELECT event_id, sync_state, money_transaction_ref FROM " + QUEUE_TABLE
                            + " WHERE source_app = ? AND source_record_id = ?"
                            + " ORDER BY id DESC LIMIT 1",
                    new String[]{
                            TridevIntegrationContract.APP_SMART_SMS,
                            incoming.sourceRecordId
                    })) {
                if (!cursor.moveToFirst()) {
                    return new Preparation(
                            incoming.eventId,
                            TridevIntegrationContract.SyncState.NEEDS_REVIEW,
                            null,
                            false);
                }
                existingEventId = clean(cursor.getString(0));
                rawState = clean(cursor.getString(1));
                transactionRef = cursor.isNull(2) ? "" : clean(cursor.getString(2));
            }

            TridevIntegrationContract.SyncState state = parseState(rawState);
            if (state == TridevIntegrationContract.SyncState.SYNCED
                    && !ledgerTransactionExists(transactionRef)) {
                state = TridevIntegrationContract.SyncState.NEEDS_REVIEW;
            }

            if (state == TridevIntegrationContract.SyncState.PENDING
                    || state == TridevIntegrationContract.SyncState.FAILED
                    || state == TridevIntegrationContract.SyncState.LOCAL_ONLY
                    || state == TridevIntegrationContract.SyncState.NEEDS_REVIEW) {
                ContentValues values = new ContentValues();
                values.put("sync_state", TridevIntegrationContract.SyncState.NEEDS_REVIEW.name());
                values.put("amount_minor", incoming.amountMinor);
                values.put("direction", incoming.direction.name());
                values.put("occurred_at", incoming.occurredAt);
                values.put("account_hint", safeMetadata(incoming.accountHint));
                values.put("merchant_hint", safeMetadata(incoming.merchantHint));
                values.put("category_hint", safeMetadata(incoming.categoryHint));
                values.put("dedupe_fingerprint", TridevEventFingerprint.build(incoming));
                values.put("duplicate_score", 0);
                values.putNull("duplicate_of_event_id");
                values.put("last_error", "Historical SmartSMS source is reconciliation-only");
                values.put("updated_at", System.currentTimeMillis());
                if (state != TridevIntegrationContract.SyncState.SYNCED) {
                    values.putNull("money_transaction_ref");
                }
                db.update(
                        QUEUE_TABLE,
                        values,
                        "source_app = ? AND source_record_id = ?",
                        new String[]{
                                TridevIntegrationContract.APP_SMART_SMS,
                                incoming.sourceRecordId
                        });
                state = TridevIntegrationContract.SyncState.NEEDS_REVIEW;
                transactionRef = "";
            }

            return new Preparation(
                    existingEventId.isEmpty() ? incoming.eventId : existingEventId,
                    state,
                    transactionRef,
                    true);
        } catch (RuntimeException unavailable) {
            // Fail closed. The provider will keep the incoming event in review.
            return new Preparation(
                    incoming.eventId,
                    TridevIntegrationContract.SyncState.NEEDS_REVIEW,
                    null,
                    false);
        } finally {
            if (db != null) {
                try { db.close(); } catch (RuntimeException ignored) { }
            }
        }
    }

    @NonNull
    public Result reconcile(
            @NonNull TridevIntegrationContract.Event event,
            @NonNull String queueEventId) {
        if (!isHistoricalSmartSms(event)) {
            return new Result(false, 0L, "Event is not a historical SmartSMS reconciliation signal");
        }
        if (event.amountMinor <= 0L || event.occurredAt <= 0L) {
            return new Result(false, 0L, "Historical signal is missing amount or date");
        }

        String safeQueueEventId = clean(queueEventId);
        if (safeQueueEventId.isEmpty()) {
            return new Result(false, 0L, "Historical queue identity is unavailable");
        }

        String expectedType = expectedMoneyType(event);
        if (expectedType.isEmpty()) {
            return new Result(false, 0L, "Historical signal direction requires manual review");
        }

        List<Long> sameDay = findSameDayCandidates(event, expectedType);
        if (sameDay.size() != 1) {
            return new Result(
                    false,
                    0L,
                    sameDay.isEmpty()
                            ? "No same-day manual MoneyManager transaction matched this historical SMS"
                            : "More than one same-day MoneyManager transaction could match this historical SMS");
        }

        long transactionId = sameDay.get(0);
        if (!queue.confirmExistingMoneyManagerTransaction(safeQueueEventId, transactionId)) {
            return new Result(false, 0L, "Historical match could not be linked safely");
        }

        return new Result(
                true,
                transactionId,
                "Historical SmartSMS signal linked to the unique same-day manual MoneyManager transaction");
    }

    @NonNull
    private List<Long> findSameDayCandidates(
            @NonNull TridevIntegrationContract.Event event,
            @NonNull String expectedType) {
        List<Long> matches = new ArrayList<>();
        double amount = event.amountMinor / 100.0d;
        SupportSQLiteDatabase db = DatabaseClient
                .getInstance(appContext)
                .getAppDatabase()
                .getOpenHelper()
                .getReadableDatabase();

        try (Cursor cursor = db.query(
                "SELECT id, type, date FROM transactions "
                        + "WHERE ABS(amount - ?) < 0.005 "
                        + "ORDER BY id DESC LIMIT " + MAX_AMOUNT_CANDIDATES,
                new Object[]{amount})) {
            while (cursor.moveToNext()) {
                long id = cursor.isNull(0) ? 0L : cursor.getLong(0);
                String type = cursor.isNull(1) ? "" : cursor.getString(1);
                String date = cursor.isNull(2) ? "" : cursor.getString(2);
                if (id <= 0L || !expectedType.equalsIgnoreCase(clean(type))) continue;

                Long candidateTime = parseMoneyManagerDate(date);
                if (candidateTime == null) continue;
                if (sameLocalDay(event.occurredAt, candidateTime)) {
                    matches.add(id);
                    if (matches.size() > 1) break;
                }
            }
        } catch (RuntimeException ignored) {
            return new ArrayList<>();
        }
        return matches;
    }

    private boolean ledgerTransactionExists(@Nullable String transactionRef) {
        long transactionId = parseLong(transactionRef);
        if (transactionId <= 0L) return false;
        SupportSQLiteDatabase db = DatabaseClient
                .getInstance(appContext)
                .getAppDatabase()
                .getOpenHelper()
                .getReadableDatabase();
        try (Cursor cursor = db.query(
                "SELECT id FROM transactions WHERE id = ? LIMIT 1",
                new Object[]{transactionId})) {
            return cursor.moveToFirst();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private boolean sameLocalDay(long left, long right) {
        SimpleDateFormat day = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        return day.format(new Date(left)).equals(day.format(new Date(right)));
    }

    @Nullable
    private Long parseMoneyManagerDate(@Nullable String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        String value = raw.trim();
        String[] patterns = {
                "yyyy-MM-dd HH:mm",
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd",
                "dd-MM-yyyy HH:mm",
                "dd/MM/yyyy HH:mm",
                "dd-MM-yyyy",
                "dd/MM/yyyy"
        };
        for (String pattern : patterns) {
            SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
            format.setLenient(false);
            try {
                Date parsed = format.parse(value);
                if (parsed != null) return parsed.getTime();
            } catch (ParseException ignored) {
            }
        }
        return null;
    }

    @NonNull
    private TridevIntegrationContract.SyncState parseState(@Nullable String value) {
        try {
            return TridevIntegrationContract.SyncState.valueOf(clean(value));
        } catch (IllegalArgumentException ignored) {
            return TridevIntegrationContract.SyncState.NEEDS_REVIEW;
        }
    }

    @NonNull
    private String expectedMoneyType(@NonNull TridevIntegrationContract.Event event) {
        if (event.direction == TridevIntegrationContract.Direction.DEBIT) return "EXPENSE";
        if (event.direction == TridevIntegrationContract.Direction.CREDIT) return "INCOME";
        return "";
    }

    private boolean isHistoricalSmartSms(@NonNull TridevIntegrationContract.Event event) {
        return TridevIntegrationContract.APP_SMART_SMS.equals(event.sourceApp)
                && clean(event.eventId).startsWith("smartsms:history:")
                && clean(event.sourceRecordId).matches("sms:[0-9]+");
    }

    @NonNull
    private String safeMetadata(@Nullable String value) {
        String safe = clean(value)
                .replace('\n', ' ')
                .replace('\r', ' ')
                .replaceAll("\\s+", " ");
        return safe.length() <= 200 ? safe : safe.substring(0, 200).trim();
    }

    private long parseLong(@Nullable String value) {
        try {
            return Long.parseLong(clean(value));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    @NonNull
    private static String clean(@Nullable String value) {
        return value == null ? "" : value.trim();
    }

    @Nullable
    private static String cleanToNull(@Nullable String value) {
        String safe = clean(value);
        return safe.isEmpty() ? null : safe;
    }
}
