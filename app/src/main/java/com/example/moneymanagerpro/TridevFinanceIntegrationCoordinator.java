package com.example.moneymanagerpro;

import android.content.Context;
import android.database.Cursor;

import androidx.annotation.Nullable;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.moneymanagerpro.cloud.TridevIntegrationCloudScheduler;
import com.example.moneymanagerpro.database.DatabaseClient;

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

    private static final String NOTE_MARKER_PREFIX = "TRIDEV_EVENT:";

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

    private final Context appContext;
    private final TridevEventQueue queue;
    private final TridevTransactionPostingEngine postingEngine;
    private final TridevAdvancedReconciliationGate advancedGate;

    public TridevFinanceIntegrationCoordinator(Context context) {
        appContext = context.getApplicationContext();
        queue = TridevEventQueue.getInstance(appContext);
        postingEngine = new TridevTransactionPostingEngine(appContext);
        advancedGate = new TridevAdvancedReconciliationGate(appContext);
        TridevIntegrationCloudScheduler.ensurePeriodic(appContext);
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
            // Debounced WorkManager sync snapshots the final queue state after
            // posting/reconciliation settles; no raw SMS body is ever included.
            TridevIntegrationCloudScheduler.scheduleSoon(appContext);
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
            // A queue flag is not sufficient proof after the user manually deletes
            // the corresponding MoneyManager transaction. Verify that the ledger
            // row still exists. If it does not, reopen the canonical queue event
            // and let the normal crash-safe posting engine recreate it exactly once.
            if (!isLedgerRepresentationAlive(canonicalEventId, effectiveTransactionId)
                    && queue.confirmNotDuplicate(canonicalEventId)) {
                TridevTransactionPostingEngine.Result recovered =
                        postingEngine.process(canonicalEventId);
                return resultFromPosting(enqueue.eventId, canonicalEventId, recovered);
            }
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
        return resultFromPosting(enqueue.eventId, processingEventId, posting);
    }

    private Result resultFromPosting(
            String inboundEventId,
            String processingEventId,
            TridevTransactionPostingEngine.Result posting) {
        switch (posting.outcome) {
            case POSTED:
                return new Result(
                        Outcome.POSTED,
                        inboundEventId,
                        cleanToNull(processingEventId),
                        posting.moneyManagerTransactionId,
                        posting.reason);
            case RECONCILED_EXISTING:
            case ALREADY_HANDLED:
                return new Result(
                        Outcome.RECONCILED,
                        inboundEventId,
                        cleanToNull(processingEventId),
                        posting.moneyManagerTransactionId,
                        posting.reason);
            case NEEDS_REVIEW:
                return new Result(
                        Outcome.NEEDS_REVIEW,
                        inboundEventId,
                        cleanToNull(processingEventId),
                        null,
                        posting.reason);
            case FAILED:
                return new Result(
                        Outcome.FAILED,
                        inboundEventId,
                        cleanToNull(processingEventId),
                        null,
                        posting.reason);
            case NOT_FOUND:
            default:
                return new Result(
                        Outcome.QUEUED,
                        inboundEventId,
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
            String transactionId = item == null || item.event.references == null
                    ? null
                    : cleanToNull(item.event.references.moneyManagerTransactionId);
            if (!isLedgerRepresentationAlive(canonicalEventId, transactionId)
                    && queue.confirmNotDuplicate(canonicalEventId)) {
                TridevTransactionPostingEngine.Result recovered =
                        postingEngine.process(canonicalEventId);
                return resultFromPosting(enqueue.eventId, canonicalEventId, recovered);
            }
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

    /**
     * Returns true only when a queue reconciliation still has a real ledger row.
     * Existing MoneyManager rows reconciled by fuzzy/exact matching may not carry
     * a TRIDEV_EVENT marker, so the stored transaction id is checked first. Rows
     * posted by the integration engine are also recoverable by their marker.
     */
    private boolean isLedgerRepresentationAlive(
            @Nullable String canonicalEventId,
            @Nullable String transactionId) {
        try {
            SupportSQLiteDatabase ledger = DatabaseClient.getInstance(appContext)
                    .getAppDatabase()
                    .getOpenHelper()
                    .getReadableDatabase();

            Long parsedId = parsePositiveLong(transactionId);
            if (parsedId != null) {
                try (Cursor cursor = ledger.query(
                        "SELECT 1 FROM transactions WHERE id = ? LIMIT 1",
                        new Object[]{parsedId})) {
                    if (cursor.moveToFirst()) return true;
                }
            }

            String marker = eventMarker(canonicalEventId);
            if (!marker.isEmpty()) {
                try (Cursor cursor = ledger.query(
                        "SELECT 1 FROM transactions WHERE instr(note, ?) > 0 LIMIT 1",
                        new Object[]{marker})) {
                    if (cursor.moveToFirst()) return true;
                }
            }
        } catch (RuntimeException ignored) {
            // Fail closed: if the ledger cannot be verified, do not manufacture a
            // second transaction. The caller will retain the existing reconciliation.
            return true;
        }
        return false;
    }

    @Nullable
    private Long parsePositiveLong(@Nullable String value) {
        String safe = cleanToNull(value);
        if (safe == null) return null;
        try {
            long parsed = Long.parseLong(safe);
            return parsed > 0L ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String eventMarker(@Nullable String eventId) {
        String safe = eventId == null ? "" : eventId.trim()
                .replaceAll("[^A-Za-z0-9:_\\-]", "");
        if (safe.isEmpty()) return "";
        if (safe.length() > 100) safe = safe.substring(0, 100);
        return NOTE_MARKER_PREFIX + safe;
    }

    @Nullable
    private String cleanToNull(@Nullable String value) {
        if (value == null) return null;
        String safe = value.trim();
        return safe.isEmpty() ? null : safe;
    }
}
