package com.example.moneymanagerpro;

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
import java.util.Date;
import java.util.Locale;

/**
 * STEP 12 post-enqueue safety gate.
 *
 * STEP 3's queue remains the durable source of truth. This guard re-checks fuzzy
 * auto decisions with stricter cross-app semantics and a unique-best rule before
 * the coordinator is allowed to treat a transaction as reconciled/duplicate.
 * Exact event/source idempotency is never changed.
 */
public final class TridevAdvancedReconciliationGate {

    private static final String QUEUE_DB = "TridevIntegrationQueue.db";
    private static final String QUEUE_TABLE = "integration_events";
    private static final long MAX_QUERY_WINDOW = 36L * 60L * 60L * 1000L;
    private static final int MAX_QUEUE_CANDIDATES = 100;
    private static final int MAX_LEDGER_CANDIDATES = 120;

    public static final class GuardResult {
        public final boolean changed;
        public final TridevIntegrationContract.SyncState state;
        @Nullable public final String duplicateOfEventId;
        @Nullable public final String moneyManagerTransactionId;
        public final int score;
        public final String reason;

        private GuardResult(
                boolean changed,
                TridevIntegrationContract.SyncState state,
                @Nullable String duplicateOfEventId,
                @Nullable String moneyManagerTransactionId,
                int score,
                String reason) {
            this.changed = changed;
            this.state = state;
            this.duplicateOfEventId = cleanToNull(duplicateOfEventId);
            this.moneyManagerTransactionId = cleanToNull(moneyManagerTransactionId);
            this.score = Math.max(0, Math.min(100, score));
            this.reason = reason == null ? "" : reason;
        }
    }

    private static final class QueueBest {
        @Nullable TridevIntegrationContract.Event event;
        @Nullable TridevCrossAppReconciliationScorer.Evaluation evaluation;
        int secondBestScore;
    }

    private static final class LedgerBest {
        long transactionId;
        @Nullable TridevCrossAppReconciliationScorer.Evaluation evaluation;
        int secondBestScore;
    }

    private final Context appContext;
    private final TridevEventQueue queue;

    public TridevAdvancedReconciliationGate(Context context) {
        appContext = context.getApplicationContext();
        queue = TridevEventQueue.getInstance(appContext);
    }

    /**
     * Re-check only newly inserted fuzzy decisions. Exact event-id/source-record
     * duplicates return without touching their existing canonical row.
     */
    @NonNull
    public GuardResult apply(
            @NonNull TridevIntegrationContract.Event incoming,
            @NonNull TridevEventQueue.EnqueueResult enqueue) {
        if (isExactIdempotencyResult(incoming, enqueue)) {
            return unchanged(enqueue, "Exact source/event idempotency remains authoritative");
        }

        TridevEventQueue.QueueItem inserted = queue.find(incoming.eventId);
        if (inserted == null || inserted.event == null) {
            return unchanged(enqueue, "No newly inserted fuzzy event requires an advanced guard");
        }

        TridevIntegrationContract.SyncState current = inserted.event.syncState;

        if (current == TridevIntegrationContract.SyncState.SUPERSEDED) {
            QueueBest best = findBestQueueMatch(incoming);
            if (best.event != null
                    && best.evaluation != null
                    && TridevCrossAppReconciliationScorer.isAutoSafe(
                            best.evaluation,
                            best.secondBestScore)
                    && same(best.event.eventId, inserted.duplicateOfEventId)) {
                return new GuardResult(
                        false,
                        current,
                        inserted.duplicateOfEventId,
                        null,
                        best.evaluation.score,
                        best.evaluation.reason);
            }

            int score = best.evaluation == null ? enqueue.duplicateScore : best.evaluation.score;
            String candidate = best.event == null
                    ? inserted.duplicateOfEventId
                    : best.event.eventId;
            queue.markNeedsReview(incoming.eventId, candidate, score);
            return new GuardResult(
                    true,
                    TridevIntegrationContract.SyncState.NEEDS_REVIEW,
                    candidate,
                    null,
                    score,
                    ambiguityReason(best.evaluation, best.secondBestScore,
                            "Cross-app auto-merge was downgraded to review"));
        }

        if (current == TridevIntegrationContract.SyncState.SYNCED) {
            LedgerBest best = findBestLedgerMatch(incoming);
            String storedRef = inserted.event.references == null
                    ? ""
                    : clean(inserted.event.references.moneyManagerTransactionId);
            long storedId = parseLong(storedRef);
            if (best.transactionId > 0L
                    && best.evaluation != null
                    && best.transactionId == storedId
                    && TridevCrossAppReconciliationScorer.isAutoSafe(
                            best.evaluation,
                            best.secondBestScore)) {
                return new GuardResult(
                        false,
                        current,
                        null,
                        storedRef,
                        best.evaluation.score,
                        best.evaluation.reason);
            }

            int score = best.evaluation == null ? enqueue.duplicateScore : best.evaluation.score;
            queue.markNeedsReview(incoming.eventId, null, score);
            return new GuardResult(
                    true,
                    TridevIntegrationContract.SyncState.NEEDS_REVIEW,
                    null,
                    storedRef,
                    score,
                    ambiguityReason(best.evaluation, best.secondBestScore,
                            "Existing-ledger auto-link was downgraded to review"));
        }

        if (current == TridevIntegrationContract.SyncState.NEEDS_REVIEW) {
            QueueBest best = findBestQueueMatch(incoming);
            if (best.event != null
                    && best.evaluation != null
                    && best.evaluation.deterministicLink
                    && TridevCrossAppReconciliationScorer.isAutoSafe(
                            best.evaluation,
                            best.secondBestScore)) {
                if (queue.confirmDuplicate(incoming.eventId, best.event.eventId)) {
                    return new GuardResult(
                            true,
                            TridevIntegrationContract.SyncState.SUPERSEDED,
                            best.event.eventId,
                            null,
                            100,
                            "Explicit cross-app reference confirmed the same transaction");
                }
            }
        }

        return new GuardResult(
                false,
                current,
                inserted.duplicateOfEventId,
                inserted.event.references == null
                        ? null
                        : inserted.event.references.moneyManagerTransactionId,
                inserted.duplicateScore,
                "Advanced reconciliation left the queue decision unchanged");
    }

    @NonNull
    private QueueBest findBestQueueMatch(@NonNull TridevIntegrationContract.Event incoming) {
        QueueBest result = new QueueBest();
        File file = appContext.getDatabasePath(QUEUE_DB);
        if (file == null || !file.exists()) return result;

        long center = effectiveTime(incoming);
        long from = Math.max(0L, center - MAX_QUERY_WINDOW);
        long to = center + MAX_QUERY_WINDOW;
        SQLiteDatabase db = null;
        int bestScore = -1;
        int secondScore = 0;
        try {
            db = SQLiteDatabase.openDatabase(
                    file.getAbsolutePath(),
                    null,
                    SQLiteDatabase.OPEN_READONLY);
            String sql = "SELECT * FROM " + QUEUE_TABLE
                    + " WHERE event_id != ? AND amount_minor = ? AND currency = ?"
                    + " AND occurred_at BETWEEN ? AND ? AND sync_state != ?"
                    + " ORDER BY ABS(occurred_at - ?) ASC LIMIT " + MAX_QUEUE_CANDIDATES;
            try (Cursor cursor = db.rawQuery(sql, new String[]{
                    incoming.eventId,
                    String.valueOf(incoming.amountMinor),
                    currency(incoming.currency),
                    String.valueOf(from),
                    String.valueOf(to),
                    TridevIntegrationContract.SyncState.SUPERSEDED.name(),
                    String.valueOf(center)
            })) {
                while (cursor.moveToNext()) {
                    TridevIntegrationContract.Event candidate = readQueueEvent(cursor);
                    if (candidate == null) continue;
                    TridevCrossAppReconciliationScorer.Evaluation evaluation =
                            TridevCrossAppReconciliationScorer.scoreEvents(incoming, candidate);
                    int score = evaluation.score;
                    if (score > bestScore) {
                        secondScore = Math.max(0, bestScore);
                        bestScore = score;
                        result.event = candidate;
                        result.evaluation = evaluation;
                    } else if (score > secondScore) {
                        secondScore = score;
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // Fail closed: caller will retain/revert to review rather than guess.
        } finally {
            if (db != null) {
                try { db.close(); } catch (RuntimeException ignored) { }
            }
        }
        result.secondBestScore = secondScore;
        return result;
    }

    @NonNull
    private LedgerBest findBestLedgerMatch(@NonNull TridevIntegrationContract.Event incoming) {
        LedgerBest result = new LedgerBest();
        SupportSQLiteDatabase db = DatabaseClient
                .getInstance(appContext)
                .getAppDatabase()
                .getOpenHelper()
                .getReadableDatabase();
        double amount = incoming.amountMinor / 100.0d;
        int bestScore = -1;
        int secondScore = 0;
        try (Cursor cursor = db.query(
                "SELECT id, type, account, category, note, date FROM transactions "
                        + "WHERE ABS(amount - ?) < 0.005 ORDER BY id DESC LIMIT "
                        + MAX_LEDGER_CANDIDATES,
                new Object[]{amount})) {
            while (cursor.moveToNext()) {
                long when = parseMoneyManagerDate(safeCursor(cursor, 5));
                TridevCrossAppReconciliationScorer.Evaluation evaluation =
                        TridevCrossAppReconciliationScorer.scoreLedger(
                                incoming,
                                safeCursor(cursor, 1),
                                safeCursor(cursor, 2),
                                safeCursor(cursor, 3),
                                safeCursor(cursor, 4),
                                when);
                int score = evaluation.score;
                if (score > bestScore) {
                    secondScore = Math.max(0, bestScore);
                    bestScore = score;
                    result.transactionId = cursor.getLong(0);
                    result.evaluation = evaluation;
                } else if (score > secondScore) {
                    secondScore = score;
                }
            }
        } catch (RuntimeException ignored) {
            // Fail closed.
        }
        result.secondBestScore = secondScore;
        return result;
    }

    @Nullable
    private TridevIntegrationContract.Event readQueueEvent(Cursor cursor) {
        try {
            TridevIntegrationContract.References references =
                    new TridevIntegrationContract.References(
                            column(cursor, "money_account_ref"),
                            column(cursor, "money_category_ref"),
                            column(cursor, "money_transaction_ref"),
                            column(cursor, "family_finance_ref"),
                            column(cursor, "family_grocery_ref"),
                            column(cursor, "loan_ref"),
                            column(cursor, "loan_payment_ref"));
            return new TridevIntegrationContract.Event(
                    column(cursor, "event_id"),
                    column(cursor, "source_app"),
                    column(cursor, "source_record_id"),
                    enumValue(TridevIntegrationContract.EventType.class,
                            column(cursor, "event_type"),
                            TridevIntegrationContract.EventType.EXPENSE),
                    enumValue(TridevIntegrationContract.Direction.class,
                            column(cursor, "direction"),
                            TridevIntegrationContract.Direction.UNKNOWN),
                    enumValue(TridevIntegrationContract.Scope.class,
                            column(cursor, "scope"),
                            TridevIntegrationContract.Scope.UNKNOWN),
                    longColumn(cursor, "amount_minor"),
                    column(cursor, "currency"),
                    longColumn(cursor, "occurred_at"),
                    longColumn(cursor, "created_at"),
                    column(cursor, "account_hint"),
                    column(cursor, "merchant_hint"),
                    column(cursor, "category_hint"),
                    column(cursor, "linked_event_id"),
                    column(cursor, "dedupe_fingerprint"),
                    enumValue(TridevIntegrationContract.SyncState.class,
                            column(cursor, "sync_state"),
                            TridevIntegrationContract.SyncState.NEEDS_REVIEW),
                    enumValue(TridevIntegrationContract.MatchConfidence.class,
                            column(cursor, "match_confidence"),
                            TridevIntegrationContract.MatchConfidence.UNMATCHED),
                    references);
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    private boolean isExactIdempotencyResult(
            TridevIntegrationContract.Event incoming,
            TridevEventQueue.EnqueueResult enqueue) {
        if (enqueue.duplicateScore != 100 || enqueue.decision != TridevEventQueue.Decision.DUPLICATE) {
            return false;
        }
        if (same(incoming.eventId, enqueue.duplicateOfEventId)) return true;
        // A source-record duplicate does not create the incoming event id.
        return queue.find(incoming.eventId) == null;
    }

    private GuardResult unchanged(TridevEventQueue.EnqueueResult enqueue, String reason) {
        return new GuardResult(
                false,
                enqueue.syncState,
                enqueue.duplicateOfEventId,
                null,
                enqueue.duplicateScore,
                reason);
    }

    private String ambiguityReason(
            @Nullable TridevCrossAppReconciliationScorer.Evaluation evaluation,
            int secondBestScore,
            String prefix) {
        if (evaluation == null) return prefix + ": strong identity could not be verified.";
        if (evaluation.hardConflict) return prefix + ": " + evaluation.reason + ".";
        if (!evaluation.identityEvidence && !evaluation.deterministicLink) {
            return prefix + ": amount/time alone is not enough identity evidence.";
        }
        if (!evaluation.deterministicLink
                && evaluation.score - secondBestScore
                < TridevCrossAppReconciliationScorer.UNIQUE_BEST_MARGIN) {
            return prefix + ": two candidates are too close to choose automatically.";
        }
        return prefix + ": " + evaluation.reason + ".";
    }

    private long parseMoneyManagerDate(String raw) {
        if (raw.isEmpty()) return 0L;
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
                Date date = format.parse(raw);
                if (date != null) return date.getTime();
            } catch (ParseException ignored) { }
        }
        return 0L;
    }

    private String safeCursor(Cursor cursor, int index) {
        return index < 0 || index >= cursor.getColumnCount() || cursor.isNull(index)
                ? "" : clean(cursor.getString(index));
    }

    private String column(Cursor cursor, String name) {
        int index = cursor.getColumnIndex(name);
        return index < 0 || cursor.isNull(index) ? "" : clean(cursor.getString(index));
    }

    private long longColumn(Cursor cursor, String name) {
        int index = cursor.getColumnIndex(name);
        return index < 0 || cursor.isNull(index) ? 0L : cursor.getLong(index);
    }

    private <T extends Enum<T>> T enumValue(Class<T> type, String raw, T fallback) {
        try {
            return Enum.valueOf(type, raw);
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private long effectiveTime(TridevIntegrationContract.Event event) {
        return event.occurredAt > 0L ? event.occurredAt : event.createdAt;
    }

    private String currency(@Nullable String value) {
        String safe = clean(value).toUpperCase(Locale.ROOT);
        return safe.isEmpty() ? TridevIntegrationContract.DEFAULT_CURRENCY : safe;
    }

    private static boolean same(@Nullable String left, @Nullable String right) {
        String a = clean(left);
        String b = clean(right);
        return !a.isEmpty() && a.equals(b);
    }

    private static long parseLong(@Nullable String value) {
        try {
            return Long.parseLong(clean(value));
        } catch (RuntimeException ignored) {
            return 0L;
        }
    }

    private static String clean(@Nullable String value) {
        return value == null ? "" : value.trim();
    }

    @Nullable
    private static String cleanToNull(@Nullable String value) {
        String safe = clean(value);
        return safe.isEmpty() ? null : safe;
    }
}
