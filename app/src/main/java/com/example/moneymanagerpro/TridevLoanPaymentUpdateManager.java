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

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Source-authoritative EDIT propagation for LoanManagerPro payments.
 *
 * Stable identity is loanId + paymentId. An edit may update only a MoneyManager
 * ledger row that was auto-created by the LoanManager integration marker. A
 * manual/existing MoneyManager row that was merely reconciled is never changed.
 *
 * Historical routing is preserved deliberately:
 * - Account/card stays exactly where the original MoneyManager row was posted.
 * - FAMILY/PERSONAL scope stays exactly as originally posted.
 * - Category stays unchanged for amount/date edits.
 * - If EMI <-> PREPAYMENT type changes, only then is the new configured expense
 *   category resolved and applied.
 *
 * Recovery rule:
 * Older/failed edit builds could accidentally post more than one source-owned
 * ledger row for the same loanId + paymentId. A later trusted EDIT collapses
 * those rows back to one canonical transaction instead of creating another one.
 */
public final class TridevLoanPaymentUpdateManager {

    private static final String QUEUE_DB = "TridevIntegrationQueue.db";
    private static final String QUEUE_TABLE = "integration_events";
    private static final String MARKER_PREFIX = "TRIDEV_EVENT:";

    public static final class Result {
        public final boolean handled;
        public final boolean finalized;
        public final boolean familyVisible;
        @NonNull public final String status;
        @NonNull public final String canonicalEventId;
        @Nullable public final String transactionId;
        @NonNull public final String reason;

        private Result(
                boolean handled,
                boolean finalized,
                boolean familyVisible,
                @NonNull String status,
                @NonNull String canonicalEventId,
                @Nullable String transactionId,
                @NonNull String reason) {
            this.handled = handled;
            this.finalized = finalized;
            this.familyVisible = familyVisible;
            this.status = status;
            this.canonicalEventId = canonicalEventId;
            this.transactionId = transactionId;
            this.reason = reason;
        }
    }

    private static final class QueueLoanEvent {
        @NonNull final String eventId;
        @NonNull final String sourceRecordId;
        @NonNull final String scope;
        @NonNull final String moneyTransactionRef;
        @NonNull final String moneyCategoryRef;
        @NonNull final String categoryHint;

        QueueLoanEvent(
                @Nullable String eventId,
                @Nullable String sourceRecordId,
                @Nullable String scope,
                @Nullable String moneyTransactionRef,
                @Nullable String moneyCategoryRef,
                @Nullable String categoryHint) {
            this.eventId = clean(eventId);
            this.sourceRecordId = clean(sourceRecordId);
            this.scope = clean(scope);
            this.moneyTransactionRef = clean(moneyTransactionRef);
            this.moneyCategoryRef = clean(moneyCategoryRef);
            this.categoryHint = clean(categoryHint);
        }
    }

    private static final class OwnedLedgerRow {
        final long transactionId;
        @NonNull final String account;
        @NonNull final String category;
        @NonNull final String note;
        @NonNull final QueueLoanEvent queueEvent;

        OwnedLedgerRow(
                long transactionId,
                @Nullable String account,
                @Nullable String category,
                @Nullable String note,
                @NonNull QueueLoanEvent queueEvent) {
            this.transactionId = transactionId;
            this.account = clean(account);
            this.category = clean(category);
            this.note = clean(note);
            this.queueEvent = queueEvent;
        }
    }

    private final Context appContext;
    private final AppDatabase ledgerDatabase;
    private final TridevMoneyMappingEngine mapper;

    public TridevLoanPaymentUpdateManager(@NonNull Context context) {
        appContext = context.getApplicationContext();
        ledgerDatabase = DatabaseClient.getInstance(appContext).getAppDatabase();
        mapper = new TridevMoneyMappingEngine(appContext);
    }

    @NonNull
    public Result update(
            @NonNull String loanId,
            @NonNull String paymentId,
            @NonNull String newSourceRecordId,
            @NonNull String paymentType,
            long amountMinor,
            long occurredAt,
            @Nullable String categoryHint) {
        String safeLoanId = structuredId(loanId);
        String safePaymentId = structuredId(paymentId);
        String safeSourceRecord = structuredSource(newSourceRecordId);
        String safeType = clean(paymentType).toUpperCase(Locale.ROOT);
        String safeCategoryHint = metadata(categoryHint, 160);

        if (safeLoanId.isEmpty() || safePaymentId.isEmpty() || safeSourceRecord.isEmpty()
                || amountMinor <= 0L || occurredAt <= 0L
                || (!"EMI".equals(safeType) && !"PREPAYMENT".equals(safeType))) {
            return result(false, false, false, "REJECTED", "", null,
                    "Edited loan payment payload is invalid");
        }

        SQLiteDatabase queueDb = openQueueDatabase();
        if (queueDb == null) {
            return result(false, false, false, "FAILED", "", null,
                    "MoneyManager integration queue is unavailable");
        }

        try {
            List<QueueLoanEvent> events = findLoanEvents(queueDb, safeLoanId, safePaymentId);
            if (events.isEmpty()) {
                return result(true, false, false, "NOT_FOUND", "", null,
                        "No earlier MoneyManager representation exists for this LoanManager payment");
            }

            List<OwnedLedgerRow> ownedRows = findOwnedLedgerRows(events);
            Set<Long> ownedIds = new HashSet<>();
            for (OwnedLedgerRow row : ownedRows) ownedIds.add(row.transactionId);

            QueueLoanEvent manualLinked = firstManualLinkedEvent(events, ownedIds);
            if (manualLinked != null) {
                // If an old failed build also created source-owned duplicates beside
                // the verified manual/existing ledger row, remove only those
                // integration-owned duplicates. The manual row itself is preserved.
                if (!ownedRows.isEmpty()) {
                    deleteOwnedDuplicates(ownedRows, -1L);
                    markAllEventsPreserved(queueDb, events, manualLinked);
                }
                return result(true, false, "FAMILY".equals(manualLinked.scope),
                        "PRESERVED", manualLinked.eventId, manualLinked.moneyTransactionRef,
                        "The LoanManager payment is linked to an existing/manual MoneyManager transaction, so that manual row was preserved");
            }

            if (ownedRows.isEmpty()) {
                QueueLoanEvent first = events.get(0);
                return result(true, false, "FAMILY".equals(first.scope),
                        "REPOST_REQUIRED", first.eventId, null,
                        "The earlier source-owned MoneyManager row is missing; edit was not reposted to avoid a duplicate");
            }

            // Events are ordered oldest first. Preserve the earliest surviving
            // source-owned transaction so its historical account/category route
            // and MoneyManager transaction id remain stable.
            OwnedLedgerRow owned = ownedRows.get(0);
            QueueLoanEvent canonical = owned.queueEvent;
            boolean familyVisible = "FAMILY".equals(canonical.scope);
            String oldType = paymentTypeFromSourceRecord(canonical.sourceRecordId);
            String categoryValue = owned.category;
            String categoryRef = canonical.moneyCategoryRef;
            String queueCategoryHint = canonical.categoryHint;

            if (!oldType.isEmpty() && !oldType.equals(safeType)) {
                TridevMoneyMappingEngine.MappingResult mapped = mapper.resolveCategory(
                        "loan-edit:" + safeLoanId + ":" + safePaymentId + ":" + safeType,
                        safeCategoryHint,
                        "Expense");
                if (mapped == null
                        || mapped.needsReview
                        || mapped.confidence != TridevIntegrationContract.MatchConfidence.EXACT
                        || clean(mapped.transactionValue).isEmpty()
                        || clean(mapped.canonicalRef).isEmpty()) {
                    return result(true, false, familyVisible, "NEEDS_REVIEW",
                            canonical.eventId, String.valueOf(owned.transactionId),
                            "EMI/Prepayment type changed, but the new MoneyManager expense category could not be resolved safely");
                }
                categoryValue = clean(mapped.transactionValue);
                categoryRef = clean(mapped.canonicalRef);
                queueCategoryHint = safeCategoryHint;
            }

            updateOwnedLedgerRow(owned, amountMinor, occurredAt, categoryValue);

            int removedDuplicates = deleteOwnedDuplicates(ownedRows, owned.transactionId);
            collapseQueueEvents(
                    queueDb,
                    events,
                    canonical,
                    safeLoanId,
                    safePaymentId,
                    safeSourceRecord,
                    amountMinor,
                    occurredAt,
                    categoryRef,
                    queueCategoryHint,
                    owned.transactionId);

            return result(true, true, familyVisible, "UPDATED",
                    canonical.eventId, String.valueOf(owned.transactionId),
                    removedDuplicates > 0
                            ? "Existing LoanManager transaction updated and duplicate MoneyManager rows collapsed"
                            : "Existing LoanManager-created MoneyManager transaction updated in place");
        } catch (RuntimeException failure) {
            return result(false, false, false, "FAILED", "", null,
                    "Loan payment edit failed safely in MoneyManager");
        } finally {
            queueDb.close();
        }
    }

    @NonNull
    private List<QueueLoanEvent> findLoanEvents(
            @NonNull SQLiteDatabase db,
            @NonNull String loanId,
            @NonNull String paymentId) {
        List<QueueLoanEvent> result = new ArrayList<>();
        String sourcePrefix = "bank:" + loanId + ":" + paymentId + ":%";
        try (Cursor cursor = db.rawQuery(
                "SELECT event_id, source_record_id, scope, money_transaction_ref, "
                        + "money_category_ref, category_hint FROM " + QUEUE_TABLE
                        + " WHERE source_app = ? AND event_type = ?"
                        + " AND ((loan_ref = ? AND loan_payment_ref = ?)"
                        + " OR source_record_id LIKE ?)"
                        + " ORDER BY id ASC",
                new String[]{
                        TridevIntegrationContract.APP_LOAN_MANAGER,
                        TridevIntegrationContract.EventType.LOAN_PAYMENT.name(),
                        loanId,
                        paymentId,
                        sourcePrefix
                })) {
            while (cursor.moveToNext()) {
                String eventId = cursor.isNull(0) ? "" : cursor.getString(0);
                if (clean(eventId).isEmpty()) continue;
                result.add(new QueueLoanEvent(
                        eventId,
                        cursor.isNull(1) ? "" : cursor.getString(1),
                        cursor.isNull(2) ? "" : cursor.getString(2),
                        cursor.isNull(3) ? "" : cursor.getString(3),
                        cursor.isNull(4) ? "" : cursor.getString(4),
                        cursor.isNull(5) ? "" : cursor.getString(5)));
            }
        }
        return result;
    }

    @NonNull
    private List<OwnedLedgerRow> findOwnedLedgerRows(@NonNull List<QueueLoanEvent> events) {
        List<OwnedLedgerRow> result = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        SupportSQLiteDatabase ledger = ledgerDatabase.getOpenHelper().getReadableDatabase();
        for (QueueLoanEvent event : events) {
            String marker = marker(event.eventId);
            if (marker.isEmpty()) continue;
            try (Cursor cursor = ledger.query(
                    "SELECT id, account, category, note FROM transactions"
                            + " WHERE instr(note, ?) > 0"
                            + " AND instr(note, 'Synced from LoanManagerPro') > 0"
                            + " ORDER BY id ASC",
                    new Object[]{marker})) {
                while (cursor.moveToNext()) {
                    long id = cursor.getLong(0);
                    if (id <= 0L || !seen.add(id)) continue;
                    result.add(new OwnedLedgerRow(
                            id,
                            cursor.isNull(1) ? "" : cursor.getString(1),
                            cursor.isNull(2) ? "" : cursor.getString(2),
                            cursor.isNull(3) ? "" : cursor.getString(3),
                            event));
                }
            }
        }
        return result;
    }

    @Nullable
    private QueueLoanEvent firstManualLinkedEvent(
            @NonNull List<QueueLoanEvent> events,
            @NonNull Set<Long> ownedIds) {
        SupportSQLiteDatabase ledger = ledgerDatabase.getOpenHelper().getReadableDatabase();
        for (QueueLoanEvent event : events) {
            long id = parseLong(event.moneyTransactionRef);
            if (id <= 0L || ownedIds.contains(id)) continue;
            try (Cursor cursor = ledger.query(
                    "SELECT id, note FROM transactions WHERE id = ? LIMIT 1",
                    new Object[]{id})) {
                if (!cursor.moveToFirst()) continue;
                String note = cursor.isNull(1) ? "" : cursor.getString(1);
                if (!isLoanManagerOwnedNote(note)) return event;
            }
        }
        return null;
    }

    private boolean isLoanManagerOwnedNote(@Nullable String note) {
        String safe = clean(note);
        return safe.contains(MARKER_PREFIX)
                && safe.contains("Synced from LoanManagerPro");
    }

    private void updateOwnedLedgerRow(
            @NonNull OwnedLedgerRow owned,
            long amountMinor,
            long occurredAt,
            @NonNull String categoryValue) {
        final double amount = BigDecimal.valueOf(amountMinor).movePointLeft(2).doubleValue();
        final String date = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                .format(new Date(occurredAt));
        final String marker = marker(owned.queueEvent.eventId);

        ledgerDatabase.runInTransaction(() -> {
            SupportSQLiteDatabase ledger = ledgerDatabase.getOpenHelper().getWritableDatabase();
            ledger.execSQL(
                    "UPDATE transactions SET amount = ?, date = ?, category = ?, type = 'EXPENSE'"
                            + " WHERE id = ? AND instr(note, ?) > 0"
                            + " AND instr(note, 'Synced from LoanManagerPro') > 0",
                    new Object[]{amount, date, categoryValue, owned.transactionId, marker});
        });
    }

    private int deleteOwnedDuplicates(
            @NonNull List<OwnedLedgerRow> ownedRows,
            long canonicalTransactionId) {
        final int[] removed = {0};
        ledgerDatabase.runInTransaction(() -> {
            SupportSQLiteDatabase ledger = ledgerDatabase.getOpenHelper().getWritableDatabase();
            for (OwnedLedgerRow row : ownedRows) {
                if (row.transactionId <= 0L || row.transactionId == canonicalTransactionId) continue;
                String marker = marker(row.queueEvent.eventId);
                if (marker.isEmpty()) continue;
                try (Cursor cursor = ledger.query(
                        "SELECT id FROM transactions WHERE id = ?"
                                + " AND instr(note, ?) > 0"
                                + " AND instr(note, 'Synced from LoanManagerPro') > 0 LIMIT 1",
                        new Object[]{row.transactionId, marker})) {
                    if (!cursor.moveToFirst()) continue;
                }
                ledger.execSQL(
                        "DELETE FROM transactions WHERE id = ? AND instr(note, ?) > 0"
                                + " AND instr(note, 'Synced from LoanManagerPro') > 0",
                        new Object[]{row.transactionId, marker});
                removed[0]++;
            }
        });
        return removed[0];
    }

    private void collapseQueueEvents(
            @NonNull SQLiteDatabase db,
            @NonNull List<QueueLoanEvent> events,
            @NonNull QueueLoanEvent canonical,
            @NonNull String loanId,
            @NonNull String paymentId,
            @NonNull String newSourceRecord,
            long amountMinor,
            long occurredAt,
            @NonNull String categoryRef,
            @NonNull String categoryHint,
            long canonicalTransactionId) {
        for (QueueLoanEvent event : events) {
            if (canonical.eventId.equals(event.eventId)) continue;
            ContentValues duplicate = new ContentValues();
            duplicate.putNull("source_record_id");
            duplicate.put("sync_state", TridevIntegrationContract.SyncState.SUPERSEDED.name());
            duplicate.put("duplicate_of_event_id", canonical.eventId);
            duplicate.put("duplicate_score", 100);
            duplicate.putNull("money_transaction_ref");
            duplicate.put("last_error", "Collapsed duplicate LoanManager edit into canonical payment");
            duplicate.put("updated_at", System.currentTimeMillis());
            db.update(QUEUE_TABLE, duplicate, "event_id = ?", new String[]{event.eventId});
        }

        // Release any unexpected row that already holds the edited source identity
        // but was not returned by the legacy stable-reference query.
        ContentValues release = new ContentValues();
        release.putNull("source_record_id");
        release.put("sync_state", TridevIntegrationContract.SyncState.SUPERSEDED.name());
        release.putNull("duplicate_of_event_id");
        release.put("duplicate_score", 0);
        release.putNull("money_transaction_ref");
        release.put("last_error", "Superseded by source-authoritative LoanManager edit");
        release.put("updated_at", System.currentTimeMillis());
        db.update(
                QUEUE_TABLE,
                release,
                "source_app = ? AND event_type = ? AND source_record_id = ? AND event_id <> ?",
                new String[]{
                        TridevIntegrationContract.APP_LOAN_MANAGER,
                        TridevIntegrationContract.EventType.LOAN_PAYMENT.name(),
                        newSourceRecord,
                        canonical.eventId
                });

        ContentValues values = new ContentValues();
        values.put("source_record_id", newSourceRecord);
        values.put("loan_ref", loanId);
        values.put("loan_payment_ref", paymentId);
        values.put("amount_minor", amountMinor);
        values.put("occurred_at", occurredAt);
        values.put("sync_state", TridevIntegrationContract.SyncState.SYNCED.name());
        values.putNull("duplicate_of_event_id");
        values.put("duplicate_score", 0);
        values.put("money_transaction_ref", String.valueOf(canonicalTransactionId));
        values.put("last_error", "");
        values.put("updated_at", System.currentTimeMillis());
        if (!categoryRef.isEmpty()) values.put("money_category_ref", categoryRef);
        if (!categoryHint.isEmpty()) values.put("category_hint", categoryHint);
        db.update(QUEUE_TABLE, values, "event_id = ?", new String[]{canonical.eventId});
    }

    private void markAllEventsPreserved(
            @NonNull SQLiteDatabase db,
            @NonNull List<QueueLoanEvent> events,
            @NonNull QueueLoanEvent manualLinked) {
        for (QueueLoanEvent event : events) {
            if (manualLinked.eventId.equals(event.eventId)) continue;
            ContentValues values = new ContentValues();
            values.putNull("source_record_id");
            values.put("sync_state", TridevIntegrationContract.SyncState.SUPERSEDED.name());
            values.put("duplicate_of_event_id", manualLinked.eventId);
            values.put("duplicate_score", 100);
            values.putNull("money_transaction_ref");
            values.put("last_error", "Source-owned duplicate removed; manual MoneyManager representation preserved");
            values.put("updated_at", System.currentTimeMillis());
            db.update(QUEUE_TABLE, values, "event_id = ?", new String[]{event.eventId});
        }
    }

    @NonNull
    private String paymentTypeFromSourceRecord(@Nullable String sourceRecord) {
        String safe = clean(sourceRecord);
        int split = safe.lastIndexOf(':');
        if (split < 0 || split + 1 >= safe.length()) return "";
        String type = safe.substring(split + 1).trim().toUpperCase(Locale.ROOT);
        return "EMI".equals(type) || "PREPAYMENT".equals(type) ? type : "";
    }

    @NonNull
    private String marker(@Nullable String eventId) {
        String safe = clean(eventId).replaceAll("[^A-Za-z0-9:_\\-]", "");
        if (safe.isEmpty()) return "";
        if (safe.length() > 100) safe = safe.substring(0, 100);
        return MARKER_PREFIX + safe;
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

    private long parseLong(@Nullable String value) {
        try {
            return Long.parseLong(clean(value));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    @NonNull
    private String structuredId(@Nullable String value) {
        String safe = clean(value);
        if (safe.length() > 40 || safe.indexOf('\n') >= 0 || safe.indexOf('\r') >= 0) return "";
        return safe.replaceAll("[^A-Za-z0-9:_\\-]", "");
    }

    @NonNull
    private String structuredSource(@Nullable String value) {
        String safe = clean(value);
        if (safe.length() > 160 || safe.indexOf('\n') >= 0 || safe.indexOf('\r') >= 0) return "";
        return safe.replaceAll("[^A-Za-z0-9:_\\-]", "_");
    }

    @NonNull
    private String metadata(@Nullable String value, int max) {
        String safe = clean(value).replace('\n', ' ').replace('\r', ' ')
                .replaceAll("\\s+", " ");
        return safe.length() <= max ? safe : safe.substring(0, max).trim();
    }

    @NonNull
    private Result result(
            boolean handled,
            boolean finalized,
            boolean familyVisible,
            @NonNull String status,
            @NonNull String canonicalEventId,
            @Nullable String transactionId,
            @NonNull String reason) {
        return new Result(
                handled,
                finalized,
                familyVisible,
                clean(status),
                clean(canonicalEventId),
                transactionId == null ? null : clean(transactionId),
                clean(reason));
    }

    @NonNull
    private static String clean(@Nullable String value) {
        return value == null ? "" : value.trim();
    }
}
