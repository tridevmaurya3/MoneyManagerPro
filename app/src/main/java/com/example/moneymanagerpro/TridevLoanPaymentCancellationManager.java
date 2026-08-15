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
 * Source-authoritative cancellation for LoanManagerPro payments.
 *
 * A LoanManager delete may remove only a MoneyManager row that was auto-created
 * by the exact LoanManager integration event marker. Existing/manual ledger rows
 * that were merely reconciled are never deleted. If another app independently
 * corroborates the same bank transaction, that evidence is reopened/preserved.
 */
public final class TridevLoanPaymentCancellationManager {

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

    private static final class LoanEvent {
        @NonNull final String eventId;
        final long amountMinor;
        final long occurredAt;

        LoanEvent(@Nullable String eventId, long amountMinor, long occurredAt) {
            this.eventId = clean(eventId);
            this.amountMinor = amountMinor;
            this.occurredAt = occurredAt;
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

    public TridevLoanPaymentCancellationManager(@NonNull Context context) {
        appContext = context.getApplicationContext();
        ledgerDatabase = DatabaseClient.getInstance(appContext).getAppDatabase();
    }

    @NonNull
    public Result cancel(@NonNull String loanId, @NonNull String paymentId) {
        String safeLoanId = structuredId(loanId);
        String safePaymentId = structuredId(paymentId);
        if (safeLoanId.isEmpty() || safePaymentId.isEmpty()) {
            return new Result(false, false, "Loan payment identity is invalid");
        }

        SQLiteDatabase queueDb = openQueueDatabase();
        if (queueDb == null) {
            return new Result(false, false, "MoneyManager integration queue is unavailable");
        }

        try {
            List<LoanEvent> events = findLoanEvents(queueDb, safeLoanId, safePaymentId);
            if (events.isEmpty()) {
                // Idempotent delete: no queue row means there is nothing left to remove.
                return new Result(true, false,
                        "Loan payment was already absent from MoneyManager integration");
            }

            ExternalEvidence evidence = findExternalEvidence(queueDb, events);
            boolean ledgerRemoved = false;

            if (evidence == null) {
                for (LoanEvent event : events) {
                    if (deleteOnlyAutoCreatedLoanRow(event.eventId)) {
                        ledgerRemoved = true;
                    }
                }
            }

            markLoanEventsCancelled(queueDb, safeLoanId, safePaymentId, ledgerRemoved);

            if (evidence != null) {
                reopenExternalEvidence(queueDb, evidence);
                if (!evidence.eventId.isEmpty()
                        && !TridevIntegrationContract.SyncState.SYNCED.name()
                        .equals(evidence.state)) {
                    new TridevTransactionPostingEngine(appContext).process(evidence.eventId);
                }
                return new Result(true, false,
                        "LoanManager link was cancelled; independent finance evidence was preserved");
            }

            return new Result(true, ledgerRemoved,
                    ledgerRemoved
                            ? "LoanManager-created MoneyManager transaction was deleted"
                            : "LoanManager link was cancelled; no source-owned ledger row remained");
        } finally {
            queueDb.close();
        }
    }

    @NonNull
    private List<LoanEvent> findLoanEvents(
            @NonNull SQLiteDatabase db,
            @NonNull String loanId,
            @NonNull String paymentId) {
        List<LoanEvent> result = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(
                "SELECT event_id, amount_minor, occurred_at FROM " + QUEUE_TABLE
                        + " WHERE source_app = ? AND event_type = ?"
                        + " AND loan_ref = ? AND loan_payment_ref = ?"
                        + " ORDER BY id DESC",
                new String[]{
                        TridevIntegrationContract.APP_LOAN_MANAGER,
                        TridevIntegrationContract.EventType.LOAN_PAYMENT.name(),
                        loanId,
                        paymentId
                })) {
            while (cursor.moveToNext()) {
                String eventId = cursor.isNull(0) ? "" : cursor.getString(0);
                if (clean(eventId).isEmpty()) continue;
                result.add(new LoanEvent(
                        eventId,
                        cursor.isNull(1) ? 0L : cursor.getLong(1),
                        cursor.isNull(2) ? 0L : cursor.getLong(2)));
            }
        }
        return result;
    }

    @Nullable
    private ExternalEvidence findExternalEvidence(
            @NonNull SQLiteDatabase db,
            @NonNull List<LoanEvent> loanEvents) {
        for (LoanEvent loanEvent : loanEvents) {
            if (loanEvent.amountMinor <= 0L || loanEvent.occurredAt <= 0L) continue;
            long start = Math.max(0L, loanEvent.occurredAt - EVIDENCE_WINDOW_MILLIS);
            long end = loanEvent.occurredAt + EVIDENCE_WINDOW_MILLIS;
            try (Cursor cursor = db.rawQuery(
                    "SELECT event_id, sync_state FROM " + QUEUE_TABLE
                            + " WHERE source_app <> ? AND amount_minor = ?"
                            + " AND direction = ? AND occurred_at BETWEEN ? AND ?"
                            + " AND sync_state IN (?, ?, ?, ?)"
                            + " ORDER BY id ASC LIMIT 1",
                    new String[]{
                            TridevIntegrationContract.APP_LOAN_MANAGER,
                            String.valueOf(loanEvent.amountMinor),
                            TridevIntegrationContract.Direction.DEBIT.name(),
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

    private boolean deleteOnlyAutoCreatedLoanRow(@NonNull String eventId) {
        String marker = marker(eventId);
        if (marker.isEmpty()) return false;
        SupportSQLiteDatabase ledger = ledgerDatabase.getOpenHelper().getWritableDatabase();
        final int[] affected = {0};
        ledgerDatabase.runInTransaction(() -> {
            try (Cursor cursor = ledger.query(
                    "SELECT id, note FROM transactions WHERE instr(note, ?) > 0"
                            + " AND instr(note, 'Synced from LoanManagerPro') > 0",
                    new Object[]{marker})) {
                List<Long> ids = new ArrayList<>();
                while (cursor.moveToNext()) ids.add(cursor.getLong(0));
                for (Long id : ids) {
                    ledger.execSQL(
                            "DELETE FROM transactions WHERE id = ? AND instr(note, ?) > 0"
                                    + " AND instr(note, 'Synced from LoanManagerPro') > 0",
                            new Object[]{id, marker});
                    affected[0]++;
                }
            }
        });
        return affected[0] > 0;
    }

    private void markLoanEventsCancelled(
            @NonNull SQLiteDatabase db,
            @NonNull String loanId,
            @NonNull String paymentId,
            boolean clearLedgerReference) {
        ContentValues values = new ContentValues();
        values.put("sync_state", TridevIntegrationContract.SyncState.SUPERSEDED.name());
        values.putNull("duplicate_of_event_id");
        values.put("duplicate_score", 0);
        values.put("last_error", "Cancelled by LoanManager source");
        values.put("updated_at", System.currentTimeMillis());
        if (clearLedgerReference) values.putNull("money_transaction_ref");
        db.update(
                QUEUE_TABLE,
                values,
                "source_app = ? AND event_type = ? AND loan_ref = ? AND loan_payment_ref = ?",
                new String[]{
                        TridevIntegrationContract.APP_LOAN_MANAGER,
                        TridevIntegrationContract.EventType.LOAN_PAYMENT.name(),
                        loanId,
                        paymentId
                });
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
    private static String structuredId(@Nullable String value) {
        String safe = clean(value).replaceAll("[^A-Za-z0-9:_\\-]", "");
        return safe.length() <= 40 ? safe : safe.substring(0, 40);
    }

    @NonNull
    private static String clean(@Nullable String value) {
        return value == null ? "" : value.trim();
    }
}
