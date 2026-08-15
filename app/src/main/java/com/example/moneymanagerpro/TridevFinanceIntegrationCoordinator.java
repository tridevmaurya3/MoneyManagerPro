package com.example.moneymanagerpro;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.Nullable;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.moneymanagerpro.cloud.TridevIntegrationCloudScheduler;
import com.example.moneymanagerpro.database.DatabaseClient;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

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
    private static final String QUEUE_DB = "TridevIntegrationQueue.db";
    private static final String QUEUE_TABLE = "integration_events";
    private static final long LEDGER_RECONCILIATION_TIME_WINDOW_MS =
            24L * 60L * 60L * 1000L;

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
            // A trusted LoanManager payment keeps a stable event/source identity
            // even when the user later changes its MoneyManager bank/category or
            // Family visibility. Refresh only routing metadata for that exact same
            // payment before reconciliation. This fixes stale mappings without
            // weakening cross-event duplicate protection.
            refreshTrustedLoanRoutingIfSafe(event);
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
            // A queue flag/reference is not sufficient proof after the user deletes
            // a transaction. SQLite row ids can later be reused, and stale refs must
            // never make a different ledger row look like this EMI. Verify the
            // referenced row still represents the canonical event (amount/type/date
            // or exact event marker). If not, reopen and recreate exactly once.
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

    /**
     * Refreshes only mutable routing metadata for an exact, trusted LoanManager
     * payment identity. A mapping-only NEEDS_REVIEW row (no duplicate candidate,
     * score zero) is reopened to PENDING so a corrected account/category can be
     * tried again. Duplicate/reconciliation review states are intentionally left
     * untouched.
     */
    private void refreshTrustedLoanRoutingIfSafe(TridevIntegrationContract.Event incoming) {
        if (incoming == null
                || !TridevIntegrationContract.APP_LOAN_MANAGER.equals(incoming.sourceApp)
                || incoming.eventType != TridevIntegrationContract.EventType.LOAN_PAYMENT) {
            return;
        }

        String eventId = incoming.eventId == null ? "" : incoming.eventId.trim();
        String sourceRecordId = incoming.sourceRecordId == null
                ? "" : incoming.sourceRecordId.trim();
        if (eventId.isEmpty() || sourceRecordId.isEmpty()) return;

        TridevEventQueue.QueueItem existing = queue.find(eventId);
        if (existing == null || existing.event == null) return;
        if (!TridevIntegrationContract.APP_LOAN_MANAGER.equals(existing.event.sourceApp)) return;
        String existingSourceRecord = existing.event.sourceRecordId == null
                ? "" : existing.event.sourceRecordId.trim();
        if (!sourceRecordId.equals(existingSourceRecord)) return;

        TridevIntegrationContract.SyncState state = existing.event.syncState;
        if (state == TridevIntegrationContract.SyncState.SUPERSEDED) return;

        boolean mappingOnlyReview = state == TridevIntegrationContract.SyncState.NEEDS_REVIEW
                && cleanToNull(existing.duplicateOfEventId) == null
                && existing.duplicateScore == 0;
        boolean failedRetry = state == TridevIntegrationContract.SyncState.FAILED;
        if (state == TridevIntegrationContract.SyncState.NEEDS_REVIEW && !mappingOnlyReview) {
            return;
        }

        File queueFile = appContext.getDatabasePath(QUEUE_DB);
        if (queueFile == null || !queueFile.exists()) return;

        SQLiteDatabase db = null;
        try {
            db = SQLiteDatabase.openDatabase(
                    queueFile.getAbsolutePath(),
                    null,
                    SQLiteDatabase.OPEN_READWRITE);
            ContentValues values = new ContentValues();
            values.put("scope", incoming.scope.name());
            values.put("account_hint", safeMetadata(incoming.accountHint));
            values.put("merchant_hint", safeMetadata(incoming.merchantHint));
            values.put("category_hint", safeMetadata(incoming.categoryHint));
            values.put("dedupe_fingerprint", TridevEventFingerprint.build(incoming));
            values.put("updated_at", System.currentTimeMillis());

            if (mappingOnlyReview || failedRetry) {
                values.put("sync_state", TridevIntegrationContract.SyncState.PENDING.name());
                values.putNull("duplicate_of_event_id");
                values.put("duplicate_score", 0);
                values.put("last_error", "");
            }

            db.update(
                    QUEUE_TABLE,
                    values,
                    "event_id = ? AND source_app = ? AND source_record_id = ?",
                    new String[]{eventId, incoming.sourceApp, sourceRecordId});
        } catch (RuntimeException ignored) {
            // Fail closed. Normal queue state remains authoritative if the refresh
            // cannot be applied safely.
        } finally {
            if (db != null) {
                try { db.close(); } catch (RuntimeException ignored) { }
            }
        }
    }

    private String safeMetadata(@Nullable String value) {
        String safe = value == null ? "" : value.trim()
                .replace('\n', ' ')
                .replace('\r', ' ')
                .replaceAll("\\s+", " ");
        if (safe.length() > 200) safe = safe.substring(0, 200).trim();
        return safe;
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
     * A stored transaction id is not proof by itself: after a ledger delete,
     * SQLite may reuse that id for another row. A reconciliation is alive only
     * when the referenced row still represents the canonical queued event.
     *
     * Integration-owned rows with the exact TRIDEV_EVENT marker are accepted only
     * when amount/type still match. Manually-entered rows (no marker) must also be
     * within the original 24-hour reconciliation window.
     */
    private boolean isLedgerRepresentationAlive(
            @Nullable String canonicalEventId,
            @Nullable String transactionId) {
        TridevEventQueue.QueueItem canonicalItem = canonicalEventId == null
                ? null
                : queue.find(canonicalEventId);
        TridevIntegrationContract.Event expected = canonicalItem == null
                ? null
                : canonicalItem.event;

        try {
            SupportSQLiteDatabase ledger = DatabaseClient.getInstance(appContext)
                    .getAppDatabase()
                    .getOpenHelper()
                    .getReadableDatabase();

            Long parsedId = parsePositiveLong(transactionId);
            if (parsedId != null) {
                try (Cursor cursor = ledger.query(
                        "SELECT amount, type, note, date FROM transactions WHERE id = ? LIMIT 1",
                        new Object[]{parsedId})) {
                    if (cursor.moveToFirst()) {
                        if (expected == null) return true; // fail closed when queue identity is unavailable
                        if (ledgerRowRepresentsEvent(cursor, expected, canonicalEventId)) return true;
                    }
                }
            }

            String marker = eventMarker(canonicalEventId);
            if (!marker.isEmpty()) {
                try (Cursor cursor = ledger.query(
                        "SELECT amount, type, note, date FROM transactions "
                                + "WHERE instr(note, ?) > 0 ORDER BY id DESC LIMIT 1",
                        new Object[]{marker})) {
                    if (cursor.moveToFirst()) {
                        if (expected == null) return true;
                        return ledgerRowRepresentsEvent(cursor, expected, canonicalEventId);
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // Fail closed: if the ledger cannot be verified at all, do not create
            // a second transaction just because a read temporarily failed.
            return true;
        }
        return false;
    }

    private boolean ledgerRowRepresentsEvent(
            Cursor cursor,
            TridevIntegrationContract.Event expected,
            @Nullable String canonicalEventId) {
        if (cursor == null || expected == null || cursor.getColumnCount() < 4) return false;

        long ledgerMinor;
        try {
            ledgerMinor = Math.round(Math.abs(cursor.getDouble(0)) * 100.0d);
        } catch (RuntimeException invalidAmount) {
            return false;
        }
        if (ledgerMinor != expected.amountMinor) return false;

        String expectedType = expectedMoneyManagerType(expected);
        String ledgerType = cursor.isNull(1) ? "" : cursor.getString(1).trim();
        if (!expectedType.isEmpty() && !expectedType.equalsIgnoreCase(ledgerType)) return false;

        String note = cursor.isNull(2) ? "" : cursor.getString(2);
        String marker = eventMarker(canonicalEventId);
        if (!marker.isEmpty() && note != null && note.contains(marker)) {
            return true;
        }

        String date = cursor.isNull(3) ? "" : cursor.getString(3);
        long ledgerTime = parseMoneyManagerDate(date);
        long expectedTime = expected.occurredAt > 0L ? expected.occurredAt : expected.createdAt;
        if (ledgerTime <= 0L || expectedTime <= 0L) return false;
        return Math.abs(ledgerTime - expectedTime) <= LEDGER_RECONCILIATION_TIME_WINDOW_MS;
    }

    private String expectedMoneyManagerType(TridevIntegrationContract.Event event) {
        if (event == null) return "";
        if (event.direction == TridevIntegrationContract.Direction.DEBIT) return "EXPENSE";
        if (event.direction == TridevIntegrationContract.Direction.CREDIT) return "INCOME";
        switch (event.eventType) {
            case INCOME:
            case REFUND:
                return "INCOME";
            case EXPENSE:
            case GROCERY_PURCHASE:
            case BILL_PAYMENT:
            case LOAN_PAYMENT:
                return "EXPENSE";
            default:
                return "";
        }
    }

    private long parseMoneyManagerDate(@Nullable String raw) {
        if (raw == null || raw.trim().isEmpty()) return 0L;
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
        return 0L;
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
