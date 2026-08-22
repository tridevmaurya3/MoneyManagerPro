package com.example.moneymanagerpro;

import android.content.Context;
import android.database.Cursor;

import androidx.annotation.Nullable;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.moneymanagerpro.database.AppDatabase;
import com.example.moneymanagerpro.database.DatabaseClient;
import com.example.moneymanagerpro.model.Transaction;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * STEP 4 - Safe Transaction Posting + Reconciliation Engine.
 *
 * Converts already-queued Tridev integration events into MoneyManagerPro ledger
 * rows only after duplicate protection and exact/user-confirmed account/category
 * mapping have succeeded.
 *
 * Safety design:
 * - Never creates accounts, cards or categories.
 * - Never posts TRANSFER, REFUND or recognised credit-card bill-payment events
 *   automatically; those require specialised reconciliation to avoid double counting.
 * - Archived/inactive destinations and uncertain mappings go to NEEDS_REVIEW.
 * - Uses a deterministic event marker in the transaction note. If the app dies
 *   after the Room insert but before the separate queue DB is updated, retry
 *   finds that marker and reconciles instead of inserting a duplicate.
 * - Raw SMS bodies must never be passed into the integration Event.
 *
 * Run this class from a worker/executor, never from the Android main thread.
 */
public final class TridevTransactionPostingEngine {

    private static final int MAX_BATCH = 50;
    private static final String NOTE_MARKER_PREFIX = "TRIDEV_EVENT:";

    public enum Outcome {
        POSTED,
        RECONCILED_EXISTING,
        ALREADY_HANDLED,
        NEEDS_REVIEW,
        FAILED,
        NOT_FOUND
    }

    public static final class Result {
        public final Outcome outcome;
        public final String eventId;
        @Nullable public final String moneyManagerTransactionId;
        @Nullable public final String accountRef;
        @Nullable public final String categoryRef;
        public final String reason;

        private Result(
                Outcome outcome,
                String eventId,
                @Nullable String moneyManagerTransactionId,
                @Nullable String accountRef,
                @Nullable String categoryRef,
                String reason) {
            this.outcome = outcome;
            this.eventId = eventId == null ? "" : eventId;
            this.moneyManagerTransactionId = moneyManagerTransactionId;
            this.accountRef = accountRef;
            this.categoryRef = categoryRef;
            this.reason = reason == null ? "" : reason;
        }
    }

    private final Context appContext;
    private final TridevEventQueue queue;
    private final TridevMoneyMappingEngine mapper;
    private final AppDatabase database;

    public TridevTransactionPostingEngine(Context context) {
        appContext = context.getApplicationContext();
        queue = TridevEventQueue.getInstance(appContext);
        mapper = new TridevMoneyMappingEngine(appContext);
        database = DatabaseClient.getInstance(appContext).getAppDatabase();
    }

    /** Process pending/failed events oldest first. */
    public List<Result> processPendingBatch(int requestedLimit) {
        int limit = Math.max(1, Math.min(MAX_BATCH, requestedLimit));
        List<TridevEventQueue.QueueItem> pending = queue.getPendingBatch(limit);
        if (pending.isEmpty()) return Collections.emptyList();

        List<Result> results = new ArrayList<>();
        for (TridevEventQueue.QueueItem item : pending) {
            if (item == null || item.event == null) continue;
            results.add(process(item.event.eventId));
        }
        return Collections.unmodifiableList(results);
    }

    /**
     * Process one queued event. NEEDS_REVIEW items are intentionally not forced
     * through this method; a future review UI must first resolve their mapping or
     * reconciliation choice.
     */
    public Result process(String eventId) {
        TridevEventQueue.QueueItem item = queue.find(eventId);
        if (item == null || item.event == null) {
            return result(Outcome.NOT_FOUND, eventId, null, null, null,
                    "Integration event was not found in the local queue");
        }

        TridevIntegrationContract.Event event = item.event;
        if (event.syncState == TridevIntegrationContract.SyncState.SYNCED
                || event.syncState == TridevIntegrationContract.SyncState.SUPERSEDED) {
            return result(Outcome.ALREADY_HANDLED, event.eventId,
                    event.references.moneyManagerTransactionId,
                    event.references.moneyManagerAccountId,
                    event.references.moneyManagerCategoryId,
                    "Event is already reconciled or superseded");
        }
        if (event.syncState == TridevIntegrationContract.SyncState.NEEDS_REVIEW) {
            return result(Outcome.NEEDS_REVIEW, event.eventId, null, null, null,
                    "Event already requires user review");
        }

        if (event.amountMinor <= 0L) {
            return needsReview(event, null, null,
                    "Amount is missing or zero");
        }
        if (!TridevIntegrationContract.DEFAULT_CURRENCY.equalsIgnoreCase(event.currency)) {
            return needsReview(event, null, null,
                    "Automatic posting currently supports INR events only");
        }

        // MoneyManager-originated events are outbound echoes, not new inbound rows.
        if (TridevIntegrationContract.APP_MONEY_MANAGER.equals(event.sourceApp)) {
            String existingRef = clean(event.references.moneyManagerTransactionId);
            if (!existingRef.isEmpty()) {
                queue.markSynced(event.eventId, existingRef);
                return result(Outcome.ALREADY_HANDLED, event.eventId, existingRef,
                        cleanToNull(event.references.moneyManagerAccountId),
                        cleanToNull(event.references.moneyManagerCategoryId),
                        "MoneyManager-originated event already references its ledger row");
            }
            return needsReview(event, null, null,
                    "MoneyManager-originated event has no ledger reference");
        }

        if (requiresSpecialReconciliation(event)) {
            return needsReview(event, null, null,
                    "Transfer/refund/card-payment style event needs specialised reconciliation");
        }

        // Crash-safe idempotency: check deterministic event marker before mapping/posting.
        long markerTransactionId = findTransactionIdByMarker(event.eventId);
        if (markerTransactionId > 0L) {
            queue.markSynced(event.eventId, String.valueOf(markerTransactionId));
            return result(Outcome.RECONCILED_EXISTING, event.eventId,
                    String.valueOf(markerTransactionId), null, null,
                    "Recovered an already-posted transaction using the event marker");
        }

        String moneyType = moneyManagerType(event);
        if (moneyType == null) {
            return needsReview(event, null, null,
                    "This event type needs specialised reconciliation before posting");
        }

        String accountDirectRef = clean(event.references.moneyManagerAccountId);
        String accountHint = accountDirectRef.isEmpty()
                ? clean(event.accountHint)
                : accountDirectRef;
        String accountExternalKey = buildAccountExternalKey(event);
        TridevMoneyMappingEngine.MappingResult account = mapper.resolveAccount(
                accountExternalKey,
                accountHint,
                TridevEventFingerprint.lastFour(event.accountHint));

        if (!isSafeAutomaticMapping(account)) {
            return needsReview(event, account, null,
                    account == null
                            ? "No MoneyManager account/card mapping was found"
                            : "Account/card mapping requires confirmation: " + account.reason);
        }

        String categoryDirectRef = clean(event.references.moneyManagerCategoryId);
        String categoryHint = categoryDirectRef.isEmpty()
                ? clean(event.categoryHint)
                : categoryDirectRef;
        String categoryExternalKey = buildCategoryExternalKey(event, moneyType);
        TridevMoneyMappingEngine.MappingResult category = mapper.resolveCategory(
                categoryExternalKey,
                categoryHint,
                titleCaseType(moneyType));

        if (!isSafeAutomaticMapping(category)) {
            return needsReview(event, account, category,
                    category == null
                            ? "No MoneyManager category mapping was found"
                            : "Category mapping requires confirmation: " + category.reason);
        }

        if (!moneyType.equalsIgnoreCase(clean(category.categoryType))) {
            return needsReview(event, account, category,
                    "Mapped category type does not match transaction direction");
        }

        String accountValue = clean(account.transactionValue);
        String categoryValue = clean(category.transactionValue);
        if (accountValue.isEmpty() || categoryValue.isEmpty()) {
            return needsReview(event, account, category,
                    "Mapped MoneyManager account/category is incomplete");
        }

        try {
            final long[] transactionId = {0L};
            final boolean[] alreadyExists = {false};
            final Transaction transaction = buildTransaction(
                    event,
                    moneyType,
                    accountValue,
                    categoryValue);

            database.runInTransaction(() -> {
                // Recheck inside the Room transaction to close retry/race windows.
                long existing = findTransactionIdByMarker(event.eventId);
                if (existing > 0L) {
                    transactionId[0] = existing;
                    alreadyExists[0] = true;
                    return;
                }

                long inserted = database.transactionDao().insert(transaction);
                if (inserted <= 0L) {
                    throw new IllegalStateException("Invalid MoneyManager transaction id");
                }
                transactionId[0] = inserted;
            });

            if (transactionId[0] <= 0L) {
                throw new IllegalStateException("Transaction was not persisted");
            }

            boolean queueUpdated = queue.markSynced(
                    event.eventId,
                    String.valueOf(transactionId[0]));

            if (!queueUpdated) {
                // Do not retry the insert here. The marker guarantees the next run
                // will reconcile this exact ledger row without creating a duplicate.
                return result(
                        alreadyExists[0]
                                ? Outcome.RECONCILED_EXISTING
                                : Outcome.POSTED,
                        event.eventId,
                        String.valueOf(transactionId[0]),
                        account.canonicalRef,
                        category.canonicalRef,
                        "Transaction is safe in MoneyManager; queue status will self-recover by event marker");
            }

            return result(
                    alreadyExists[0]
                            ? Outcome.RECONCILED_EXISTING
                            : Outcome.POSTED,
                    event.eventId,
                    String.valueOf(transactionId[0]),
                    account.canonicalRef,
                    category.canonicalRef,
                    alreadyExists[0]
                            ? "Existing event-marked transaction reconciled"
                            : "Transaction safely posted and queue marked synced");
        } catch (RuntimeException failure) {
            queue.markFailed(event.eventId, safeFailure(failure));
            return result(Outcome.FAILED, event.eventId, null,
                    account.canonicalRef,
                    category.canonicalRef,
                    "MoneyManager posting failed safely; event remains retryable");
        }
    }

    private Result needsReview(
            TridevIntegrationContract.Event event,
            @Nullable TridevMoneyMappingEngine.MappingResult account,
            @Nullable TridevMoneyMappingEngine.MappingResult category,
            String reason) {
        queue.markNeedsReview(event.eventId, null, 0);
        return result(
                Outcome.NEEDS_REVIEW,
                event.eventId,
                null,
                account == null ? null : account.canonicalRef,
                category == null ? null : category.canonicalRef,
                reason);
    }

    private boolean requiresSpecialReconciliation(TridevIntegrationContract.Event event) {
        if (event.eventType == TridevIntegrationContract.EventType.TRANSFER
                || event.direction == TridevIntegrationContract.Direction.TRANSFER
                || event.eventType == TridevIntegrationContract.EventType.REFUND) {
            return true;
        }

        String category = TridevEventFingerprint.normalizeHint(event.categoryHint);
        String merchant = TridevEventFingerprint.normalizeHint(event.merchantHint);
        String combined = (category + " " + merchant).trim();

        if (containsAny(combined,
                "credit card payment",
                "card payment",
                "credit card bill",
                "card bill",
                "cc payment",
                "statement payment")) {
            return true;
        }

        return containsAny(combined,
                "self transfer",
                "own account",
                "internal transfer");
    }

    private boolean containsAny(String text, String... phrases) {
        if (text == null || text.isEmpty()) return false;
        for (String phrase : phrases) {
            if (text.contains(phrase)) return true;
        }
        return false;
    }

    @Nullable
    private String moneyManagerType(TridevIntegrationContract.Event event) {
        if (event.eventType == TridevIntegrationContract.EventType.TRANSFER
                || event.direction == TridevIntegrationContract.Direction.TRANSFER
                || event.eventType == TridevIntegrationContract.EventType.REFUND) {
            return null;
        }

        switch (event.eventType) {
            case INCOME:
                return "INCOME";
            case EXPENSE:
            case GROCERY_PURCHASE:
            case LOAN_PAYMENT:
            case BILL_PAYMENT:
                return "EXPENSE";
            case SMS_FINANCIAL_SIGNAL:
            case FINANCE_TRANSACTION:
                if (event.direction == TridevIntegrationContract.Direction.DEBIT) {
                    return "EXPENSE";
                }
                if (event.direction == TridevIntegrationContract.Direction.CREDIT) {
                    return "INCOME";
                }
                return null;
            default:
                return null;
        }
    }

    private boolean isSafeAutomaticMapping(
            @Nullable TridevMoneyMappingEngine.MappingResult mapping) {
        if (mapping == null
                || mapping.canonicalRef == null
                || mapping.transactionValue == null
                || mapping.needsReview) {
            return false;
        }
        return mapping.confidence == TridevIntegrationContract.MatchConfidence.EXACT;
    }

    private Transaction buildTransaction(
            TridevIntegrationContract.Event event,
            String moneyType,
            String account,
            String category) {
        Transaction transaction = new Transaction();
        transaction.setType(moneyType);
        transaction.setAmount(minorToDouble(event.amountMinor));
        transaction.setCategory(category);
        transaction.setAccount(account);
        transaction.setDate(formatMoneyManagerDate(event));
        transaction.setNote(buildSafeNote(event, category, account));
        return transaction;
    }

    private double minorToDouble(long amountMinor) {
        return BigDecimal.valueOf(amountMinor)
                .movePointLeft(2)
                .doubleValue();
    }

    private String formatMoneyManagerDate(TridevIntegrationContract.Event event) {
        long time = event.occurredAt > 0L ? event.occurredAt : event.createdAt;
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                .format(new Date(time));
    }

    private String buildSafeNote(TridevIntegrationContract.Event event,
                                 String mappedCategory,
                                 String mappedAccount) {
        String marker = marker(event.eventId);
        String source = sourceLabel(event.sourceApp);
        String merchant = safeMetadata(event.merchantHint, 60);
        String category = safeMetadata(mappedCategory, 50);
        String account = safeMetadata(mappedAccount, 70);
        StringBuilder note = new StringBuilder(marker)
                .append(" • Synced from ")
                .append(source);
        if (!category.isEmpty()) {
            note.append(" • Category: ").append(category);
        }
        if (!merchant.isEmpty()) {
            note.append(" • Merchant: ").append(merchant);
        }
        if (!account.isEmpty()) {
            note.append(" • Account: ").append(account);
        }
        if (note.length() > 240) {
            return note.substring(0, 240);
        }
        return note.toString();
    }

    private long findTransactionIdByMarker(String eventId) {
        String marker = marker(eventId);
        if (marker.isEmpty()) return 0L;

        SupportSQLiteDatabase ledger = database
                .getOpenHelper()
                .getReadableDatabase();
        try (Cursor cursor = ledger.query(
                "SELECT id FROM transactions WHERE instr(note, ?) > 0 ORDER BY id DESC LIMIT 1",
                new Object[]{marker})) {
            if (cursor.moveToFirst()) return cursor.getLong(0);
        } catch (RuntimeException ignored) {
            // Fail closed. Normal posting path will still use Room transaction safety.
        }
        return 0L;
    }

    private String marker(String eventId) {
        String safe = clean(eventId)
                .replaceAll("[^A-Za-z0-9:_\\-]", "");
        if (safe.isEmpty()) return "";
        if (safe.length() > 100) safe = safe.substring(0, 100);
        return NOTE_MARKER_PREFIX + safe;
    }

    private String buildAccountExternalKey(TridevIntegrationContract.Event event) {
        String canonical = TridevEventFingerprint.canonicalAccountHint(event.accountHint);
        if (canonical.isEmpty()) canonical = "unknown";
        return safeKey(event.sourceApp + ":account:" + canonical);
    }

    private String buildCategoryExternalKey(
            TridevIntegrationContract.Event event,
            String moneyType) {
        String hint = TridevEventFingerprint.normalizeHint(event.categoryHint);
        if (hint.isEmpty()) hint = event.eventType.name().toLowerCase(Locale.ROOT);
        return safeKey(event.sourceApp + ":category:"
                + moneyType.toLowerCase(Locale.ROOT) + ":" + hint);
    }

    private String safeKey(String value) {
        String safe = clean(value)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9:_\\- ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return safe.length() <= 150 ? safe : safe.substring(0, 150).trim();
    }

    private String sourceLabel(String sourceApp) {
        if (TridevIntegrationContract.APP_SMART_SMS.equals(sourceApp)) return "SmartSMSPro";
        if (TridevIntegrationContract.APP_FAMILY_HUB.equals(sourceApp)) return "Family Hub";
        if (TridevIntegrationContract.APP_LOAN_MANAGER.equals(sourceApp)) return "LoanManagerPro";
        if (TridevIntegrationContract.APP_MONEY_MANAGER.equals(sourceApp)) return "MoneyManagerPro";
        return "Tridev Integration";
    }

    private String titleCaseType(String moneyType) {
        return "INCOME".equalsIgnoreCase(moneyType) ? "Income" : "Expense";
    }

    private String safeMetadata(@Nullable String value, int maxLength) {
        String safe = clean(value)
                .replace('\n', ' ')
                .replace('\r', ' ')
                .replaceAll("\\s+", " ")
                .trim();
        if (safe.length() > maxLength) safe = safe.substring(0, maxLength).trim();
        return safe;
    }

    private String safeFailure(RuntimeException failure) {
        String name = failure == null
                ? "RuntimeException"
                : failure.getClass().getSimpleName();
        if (name == null || name.trim().isEmpty()) name = "RuntimeException";
        return "Posting:" + name;
    }

    private Result result(
            Outcome outcome,
            String eventId,
            @Nullable String transactionId,
            @Nullable String accountRef,
            @Nullable String categoryRef,
            String reason) {
        return new Result(
                outcome,
                eventId,
                cleanToNull(transactionId),
                cleanToNull(accountRef),
                cleanToNull(categoryRef),
                reason);
    }

    private String clean(@Nullable String value) {
        return value == null ? "" : value.trim();
    }

    @Nullable
    private String cleanToNull(@Nullable String value) {
        String safe = clean(value);
        return safe.isEmpty() ? null : safe;
    }
}
