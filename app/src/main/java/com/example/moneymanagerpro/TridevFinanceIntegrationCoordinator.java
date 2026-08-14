package com.example.moneymanagerpro;

import android.content.Context;

import androidx.annotation.Nullable;

/**
 * Single MoneyManagerPro entry point for structured events coming from the
 * Tridev app ecosystem.
 *
 * Flow:
 *   validate/enqueue -> advanced unique-best reconciliation guard
 *   -> safe mapping -> posting
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
    private final TridevAdvancedReconciliationGate advancedGate;

    public TridevFinanceIntegrationCoordinator(Context context) {
        Context appContext = context.getApplicationContext();
        queue = TridevEventQueue.getInstance(appContext);
        postingEngine = new TridevTransactionPostingEngine(appContext);
        advancedGate = new TridevAdvancedReconciliationGate(appContext);
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

        final TridevAdvancedReconciliationGate.GuardResult guard;
        try {
            guard = advancedGate.apply(event, enqueue);
        } catch (RuntimeException guardFailure) {
            // Fail closed. A fuzzy auto decision must never bypass the stricter
            // guard just because its read-only verification failed.
            TridevEventQueue.QueueItem newItem = queue.find(event.eventId);
            if (newItem != null
                    && (newItem.event.syncState == TridevIntegrationContract.SyncState.SUPERSEDED
                    || newItem.event.syncState == TridevIntegrationContract.SyncState.SYNCED)) {
                queue.markNeedsReview(
                        event.eventId,
                        newItem.duplicateOfEventId,
                        newItem.duplicateScore);
                return new Result(
                        Outcome.NEEDS_REVIEW,
                        event.eventId,
                        cleanToNull(newItem.duplicateOfEventId),
                        newItem.event.references == null
                                ? null
                                : cleanToNull(newItem.event.references.moneyManagerTransactionId),
                        "Advanced reconciliation verification failed safely; review is required");
            }
            // Exact source/event idempotency and already-review/pending states are
            // still safe even if the optional advanced read-only pass failed.
            return resultFromEnqueueWithoutAdvancedGuard(enqueue);
        }

        // Exact event/source duplicates may not create a new incoming event row.
        // Newly inserted fuzzy rows are always re-read after the advanced gate so
        // a downgrade/promotion immediately becomes authoritative.
        TridevEventQueue.QueueItem incomingItem = queue.find(event.eventId);
        TridevIntegrationContract.SyncState effectiveState = incomingItem == null
                ? enqueue.syncState
                : incomingItem.event.syncState;

        String effectiveDuplicateOf = incomingItem == null
                ? cleanToNull(enqueue.duplicateOfEventId)
                : cleanToNull(incomingItem.duplicateOfEventId);
        String effectiveTransactionId = incomingItem == null
                || incomingItem.event.references == null
                ? null
                : cleanToNull(incomingItem.event.references.moneyManagerTransactionId);
        if (effectiveTransactionId == null) {
            effectiveTransactionId = cleanToNull(guard.moneyManagerTransactionId);
        }

        String canonicalEventId = effectiveDuplicateOf == null
                ? enqueue.eventId
                : effectiveDuplicateOf;
        String reason = guard.reason == null || guard.reason.trim().isEmpty()
                ? enqueue.reason
                : guard.reason;

        if (effectiveState == TridevIntegrationContract.SyncState.SYNCED) {
            return new Result(
                    Outcome.RECONCILED,
                    enqueue.eventId,
                    cleanToNull(canonicalEventId),
                    effectiveTransactionId,
                    reason);
        }

        if (effectiveState == TridevIntegrationContract.SyncState.SUPERSEDED) {
            return new Result(
                    Outcome.DUPLICATE,
                    enqueue.eventId,
                    cleanToNull(canonicalEventId),
                    effectiveTransactionId,
                    reason);
        }

        if (effectiveState == TridevIntegrationContract.SyncState.NEEDS_REVIEW) {
            return new Result(
                    Outcome.NEEDS_REVIEW,
                    enqueue.eventId,
                    cleanToNull(canonicalEventId),
                    effectiveTransactionId,
                    reason);
        }

        // Exact event/source duplicate can point to an existing pending row. It
        // is safe to process that canonical queue event. A newly inserted normal
        // event processes its own id.
        String processingEventId = incomingItem == null
                ? canonicalEventId
                : event.eventId;
        TridevTransactionPostingEngine.Result posting =
                postingEngine.process(processingEventId);

        switch (posting.outcome) {
            case POSTED:
                return new Result(
                        Outcome.POSTED,
                        enqueue.eventId,
                        cleanToNull(processingEventId),
                        posting.moneyManagerTransactionId,
                        posting.reason);
            case RECONCILED_EXISTING:
            case ALREADY_HANDLED:
                return new Result(
                        Outcome.RECONCILED,
                        enqueue.eventId,
                        cleanToNull(processingEventId),
                        posting.moneyManagerTransactionId,
                        posting.reason);
            case NEEDS_REVIEW:
                return new Result(
                        Outcome.NEEDS_REVIEW,
                        enqueue.eventId,
                        cleanToNull(processingEventId),
                        null,
                        posting.reason);
            case FAILED:
                return new Result(
                        Outcome.FAILED,
                        enqueue.eventId,
                        cleanToNull(processingEventId),
                        null,
                        posting.reason);
            case NOT_FOUND:
            default:
                return new Result(
                        Outcome.QUEUED,
                        enqueue.eventId,
                        cleanToNull(processingEventId),
                        null,
                        "Event remains safely queued for a later retry");
        }
    }

    private Result resultFromEnqueueWithoutAdvancedGuard(
            TridevEventQueue.EnqueueResult enqueue) {
        String canonicalEventId = cleanToNull(enqueue.duplicateOfEventId);
        if (canonicalEventId == null) canonicalEventId = enqueue.eventId;
        if (enqueue.syncState == TridevIntegrationContract.SyncState.SYNCED) {
            TridevEventQueue.QueueItem item = queue.find(canonicalEventId);
            return new Result(
                    Outcome.RECONCILED,
                    enqueue.eventId,
                    cleanToNull(canonicalEventId),
                    item == null || item.event.references == null
                            ? null
                            : cleanToNull(item.event.references.moneyManagerTransactionId),
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
        if (enqueue.syncState == TridevIntegrationContract.SyncState.NEEDS_REVIEW) {
            return new Result(
                    Outcome.NEEDS_REVIEW,
                    enqueue.eventId,
                    cleanToNull(canonicalEventId),
                    null,
                    enqueue.reason);
        }
        return new Result(
                Outcome.QUEUED,
                enqueue.eventId,
                cleanToNull(canonicalEventId),
                null,
                enqueue.reason);
    }

    @Nullable
    private String cleanToNull(@Nullable String value) {
        if (value == null) return null;
        String safe = value.trim();
        return safe.isEmpty() ? null : safe;
    }
}
