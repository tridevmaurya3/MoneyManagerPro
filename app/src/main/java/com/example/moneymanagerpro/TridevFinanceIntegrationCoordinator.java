package com.example.moneymanagerpro;

import android.content.Context;

import androidx.annotation.Nullable;

/**
 * Single MoneyManagerPro entry point for structured events coming from the
 * Tridev app ecosystem.
 *
 * Flow:
 *   validate/enqueue -> duplicate/reconciliation gate -> safe mapping -> posting
 *
 * External adapters should call this from a worker/executor. They must never
 * bypass TridevEventQueue and write directly to MoneyManager transactions.
 */
public final class TridevFinanceIntegrationCoordinator {

    public enum Outcome {
        POSTED,
        RECONCILED,
        QUEUED,
        DUPLICATE,
        NEEDS_REVIEW,
        FAILED
    }

    public static final class Result {
        public final Outcome outcome;
        public final String eventId;
        @Nullable public final String canonicalEventId;
        @Nullable public final String moneyManagerTransactionId;
        public final String reason;

        private Result(
                Outcome outcome,
                String eventId,
                @Nullable String canonicalEventId,
                @Nullable String moneyManagerTransactionId,
                String reason) {
            this.outcome = outcome;
            this.eventId = eventId == null ? "" : eventId;
            this.canonicalEventId = canonicalEventId;
            this.moneyManagerTransactionId = moneyManagerTransactionId;
            this.reason = reason == null ? "" : reason;
        }
    }

    private final TridevEventQueue queue;
    private final TridevTransactionPostingEngine postingEngine;

    public TridevFinanceIntegrationCoordinator(Context context) {
        Context appContext = context.getApplicationContext();
        queue = TridevEventQueue.getInstance(appContext);
        postingEngine = new TridevTransactionPostingEngine(appContext);
    }

    /**
     * Safely accept one structured event and attempt automatic posting only when
     * every gate is exact/safe. Review and duplicate states never create ledger
     * rows.
     */
    public Result acceptAndProcess(TridevIntegrationContract.Event event) {
        if (event == null) {
            return new Result(
                    Outcome.FAILED,
                    "",
                    null,
                    null,
                    "Integration event is required");
        }

        final TridevEventQueue.EnqueueResult enqueue;
        try {
            enqueue = queue.enqueue(event);
        } catch (RuntimeException invalidEvent) {
            return new Result(
                    Outcome.FAILED,
                    event.eventId,
                    null,
                    null,
                    "Event was rejected by integration validation");
        }

        String canonicalEventId = enqueue.duplicateOfEventId == null
                || enqueue.duplicateOfEventId.trim().isEmpty()
                ? enqueue.eventId
                : enqueue.duplicateOfEventId.trim();

        if (enqueue.syncState == TridevIntegrationContract.SyncState.SYNCED) {
            TridevEventQueue.QueueItem item = queue.find(canonicalEventId);
            String transactionId = item == null
                    ? null
                    : cleanToNull(item.event.references.moneyManagerTransactionId);
            return new Result(
                    Outcome.RECONCILED,
                    enqueue.eventId,
                    cleanToNull(canonicalEventId),
                    transactionId,
                    enqueue.reason);
        }

        if (enqueue.syncState == TridevIntegrationContract.SyncState.SUPERSEDED) {
            return new Result(
                    Outcome.DUPLICATE,
                    enqueue.eventId,
                    cleanToNull(canonicalEventId),
                    null,
                    enqueue.reason);
        }

        if (enqueue.syncState == TridevIntegrationContract.SyncState.NEEDS_REVIEW
                || enqueue.decision == TridevEventQueue.Decision.NEEDS_REVIEW) {
            return new Result(
                    Outcome.NEEDS_REVIEW,
                    enqueue.eventId,
                    cleanToNull(canonicalEventId),
                    null,
                    enqueue.reason);
        }

        // Exact event/source duplicate can point to an existing pending row. It
        // is safe to process the canonical queue event rather than inserting a new one.
        TridevTransactionPostingEngine.Result posting =
                postingEngine.process(canonicalEventId);

        switch (posting.outcome) {
            case POSTED:
                return new Result(
                        Outcome.POSTED,
                        enqueue.eventId,
                        cleanToNull(canonicalEventId),
                        posting.moneyManagerTransactionId,
                        posting.reason);
            case RECONCILED_EXISTING:
            case ALREADY_HANDLED:
                return new Result(
                        Outcome.RECONCILED,
                        enqueue.eventId,
                        cleanToNull(canonicalEventId),
                        posting.moneyManagerTransactionId,
                        posting.reason);
            case NEEDS_REVIEW:
                return new Result(
                        Outcome.NEEDS_REVIEW,
                        enqueue.eventId,
                        cleanToNull(canonicalEventId),
                        null,
                        posting.reason);
            case FAILED:
                return new Result(
                        Outcome.FAILED,
                        enqueue.eventId,
                        cleanToNull(canonicalEventId),
                        null,
                        posting.reason);
            case NOT_FOUND:
            default:
                return new Result(
                        Outcome.QUEUED,
                        enqueue.eventId,
                        cleanToNull(canonicalEventId),
                        null,
                        "Event remains safely queued for a later retry");
        }
    }

    @Nullable
    private String cleanToNull(@Nullable String value) {
        if (value == null) return null;
        String safe = value.trim();
        return safe.isEmpty() ? null : safe;
    }
}
