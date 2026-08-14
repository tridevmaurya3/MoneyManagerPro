package com.example.moneymanagerpro;

import android.content.Context;
import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.moneymanagerpro.cloud.TridevIntegrationCloudScheduler;
import com.example.moneymanagerpro.database.DatabaseClient;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * STEP 12 UI-facing wrapper around STEP 7's reconciliation actions.
 *
 * The original action engine remains authoritative for transfers/refunds and
 * explicit duplicate confirmation. This wrapper strengthens only candidate
 * discovery/validation: existing ledger rows are ranked with the same advanced
 * scorer used by the automatic guard and weak candidates are not exposed.
 */
public final class TridevAdvancedReconciliationManager {

    private static final int MAX_ITEMS = 100;
    private static final int MAX_RANKED_CANDIDATES = 8;

    public static final class RankedLedgerCandidate {
        public final long transactionId;
        public final int score;
        public final String label;
        public final String reason;

        private RankedLedgerCandidate(
                long transactionId,
                int score,
                String label,
                String reason) {
            this.transactionId = transactionId;
            this.score = Math.max(0, Math.min(100, score));
            this.label = safe(label);
            this.reason = safe(reason);
        }
    }

    public static final class AdvancedItem {
        public final TridevSpecialReconciliationManager.SpecialItem base;
        public final List<RankedLedgerCandidate> rankedLedgerCandidates;
        public final String candidateNotice;

        private AdvancedItem(
                TridevSpecialReconciliationManager.SpecialItem base,
                List<RankedLedgerCandidate> rankedLedgerCandidates,
                String candidateNotice) {
            this.base = base;
            this.rankedLedgerCandidates = Collections.unmodifiableList(rankedLedgerCandidates);
            this.candidateNotice = safe(candidateNotice);
        }
    }

    private final Context appContext;
    private final TridevSpecialReconciliationManager baseManager;
    private final TridevEventQueue queue;
    private final SupportSQLiteDatabase ledger;

    public TridevAdvancedReconciliationManager(Context context) {
        appContext = context.getApplicationContext();
        baseManager = new TridevSpecialReconciliationManager(appContext);
        queue = TridevEventQueue.getInstance(appContext);
        ledger = DatabaseClient.getInstance(appContext)
                .getAppDatabase()
                .getOpenHelper()
                .getReadableDatabase();
    }

    @NonNull
    public List<AdvancedItem> loadItems(int requestedLimit) {
        int limit = Math.max(1, Math.min(MAX_ITEMS, requestedLimit));
        List<TridevSpecialReconciliationManager.SpecialItem> baseItems =
                baseManager.loadItems(limit);
        if (baseItems.isEmpty()) return Collections.emptyList();

        List<AdvancedItem> result = new ArrayList<>();
        for (TridevSpecialReconciliationManager.SpecialItem item : baseItems) {
            if (item == null) continue;
            List<RankedLedgerCandidate> candidates = rankCandidates(item.eventId);
            String notice = candidateNotice(candidates);
            result.add(new AdvancedItem(item, candidates, notice));
        }
        return Collections.unmodifiableList(result);
    }

    public TridevSpecialReconciliationManager.ActionResult linkExistingTransaction(
            String eventId,
            long transactionId) {
        if (transactionId <= 0L) {
            return baseManager.linkExistingTransaction(eventId, transactionId);
        }
        List<RankedLedgerCandidate> candidates = rankCandidates(eventId);
        RankedLedgerCandidate selected = null;
        for (RankedLedgerCandidate candidate : candidates) {
            if (candidate.transactionId == transactionId) {
                selected = candidate;
                break;
            }
        }
        if (selected == null
                || selected.score < TridevCrossAppReconciliationScorer.REVIEW_SCORE) {
            // Deliberately call the base method with an invalid id so callers get
            // its standard fail-closed ActionResult without exposing a new result constructor.
            return baseManager.linkExistingTransaction(eventId, -1L);
        }
        return syncIfHandled(baseManager.linkExistingTransaction(eventId, transactionId));
    }

    public TridevSpecialReconciliationManager.ActionResult confirmQueuedDuplicate(String eventId) {
        return syncIfHandled(baseManager.confirmQueuedDuplicate(eventId));
    }

    public TridevSpecialReconciliationManager.ActionResult returnToMappingReview(String eventId) {
        return syncIfHandled(baseManager.returnToMappingReview(eventId));
    }

    public TridevSpecialReconciliationManager.ActionResult processTransfer(
            String eventId,
            String fromCanonicalRef,
            String toCanonicalRef) {
        return syncIfHandled(baseManager.processTransfer(eventId, fromCanonicalRef, toCanonicalRef));
    }

    public TridevSpecialReconciliationManager.ActionResult processRefund(
            String eventId,
            String accountCanonicalRef,
            String incomeCategoryCanonicalRef) {
        return syncIfHandled(baseManager.processRefund(
                eventId,
                accountCanonicalRef,
                incomeCategoryCanonicalRef));
    }

    private TridevSpecialReconciliationManager.ActionResult syncIfHandled(
            TridevSpecialReconciliationManager.ActionResult result) {
        if (result != null && result.handled) {
            TridevIntegrationCloudScheduler.scheduleSoon(appContext);
        }
        return result;
    }

    @NonNull
    private List<RankedLedgerCandidate> rankCandidates(String eventId) {
        TridevEventQueue.QueueItem queueItem = queue.find(eventId);
        if (queueItem == null || queueItem.event == null) return Collections.emptyList();
        TridevIntegrationContract.Event event = queueItem.event;
        if (event.amountMinor <= 0L) return Collections.emptyList();

        double amount = event.amountMinor / 100.0d;
        List<RankedLedgerCandidate> candidates = new ArrayList<>();
        try (Cursor cursor = ledger.query(
                "SELECT id, type, account, category, note, date FROM transactions "
                        + "WHERE ABS(amount - ?) < 0.005 ORDER BY id DESC LIMIT 120",
                new Object[]{amount})) {
            while (cursor.moveToNext()) {
                long transactionId = cursor.getLong(0);
                String type = text(cursor, 1);
                String account = text(cursor, 2);
                String category = text(cursor, 3);
                String note = text(cursor, 4);
                String date = text(cursor, 5);
                long time = parseDate(date);
                TridevCrossAppReconciliationScorer.Evaluation evaluation =
                        TridevCrossAppReconciliationScorer.scoreLedger(
                                event,
                                type,
                                account,
                                category,
                                note,
                                time);
                if (evaluation.hardConflict
                        || evaluation.score < TridevCrossAppReconciliationScorer.REVIEW_SCORE) {
                    continue;
                }
                String label = "Match " + evaluation.score + "% • #" + transactionId
                        + " • " + fallback(type, "Transaction")
                        + " • " + fallback(account, "Unknown account")
                        + " • " + fallback(category, "Uncategorised")
                        + " • " + fallback(date, "Unknown date");
                candidates.add(new RankedLedgerCandidate(
                        transactionId,
                        evaluation.score,
                        label,
                        evaluation.reason));
            }
        } catch (RuntimeException ignored) {
            return Collections.emptyList();
        }

        Collections.sort(candidates, new Comparator<RankedLedgerCandidate>() {
            @Override
            public int compare(RankedLedgerCandidate left, RankedLedgerCandidate right) {
                int scoreCompare = Integer.compare(right.score, left.score);
                if (scoreCompare != 0) return scoreCompare;
                return Long.compare(right.transactionId, left.transactionId);
            }
        });
        if (candidates.size() > MAX_RANKED_CANDIDATES) {
            return new ArrayList<>(candidates.subList(0, MAX_RANKED_CANDIDATES));
        }
        return candidates;
    }

    private String candidateNotice(List<RankedLedgerCandidate> candidates) {
        if (candidates.isEmpty()) {
            return "No existing MoneyManager row has enough evidence to be offered as a safe candidate.";
        }
        RankedLedgerCandidate best = candidates.get(0);
        if (best.score >= TridevCrossAppReconciliationScorer.AUTO_MATCH_SCORE
                && candidates.size() > 1
                && best.score - candidates.get(1).score
                < TridevCrossAppReconciliationScorer.UNIQUE_BEST_MARGIN) {
            return "Two existing transactions match closely. Choose explicitly; automatic linking is blocked.";
        }
        if (best.score >= TridevCrossAppReconciliationScorer.AUTO_MATCH_SCORE) {
            return "Strong existing-ledger candidate found. Verify the account/category/date before linking.";
        }
        return "Possible existing transaction found. Explicit confirmation is required.";
    }

    private long parseDate(String raw) {
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
                Date parsed = format.parse(raw);
                if (parsed != null) return parsed.getTime();
            } catch (ParseException ignored) { }
        }
        return 0L;
    }

    private String text(Cursor cursor, int index) {
        return index < 0 || index >= cursor.getColumnCount() || cursor.isNull(index)
                ? "" : safe(cursor.getString(index));
    }

    private static String fallback(@Nullable String value, String fallback) {
        String safe = safe(value);
        return safe.isEmpty() ? fallback : safe;
    }

    private static String safe(@Nullable String value) {
        return value == null ? "" : value.trim();
    }
}
