package com.example.moneymanagerpro;

import android.database.Cursor;

import androidx.annotation.Nullable;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.moneymanagerpro.database.DatabaseClient;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * STEP 6 - Account & Category Mapping / Review Center backend.
 *
 * Historical SmartSMS rows are reconciliation-only: they may link an existing
 * manual MoneyManager transaction but may never be mapped into a newly posted row.
 */
public final class TridevIntegrationReviewManager {

    private static final String HISTORICAL_REVIEW_MARKER =
            "Historical SmartSMS source is reconciliation-only";
    private static final int MAX_HISTORICAL_CANDIDATES = 20;

    public static final class Choice {
        public final String canonicalRef;
        public final String label;

        private Choice(String canonicalRef, String label) {
            this.canonicalRef = clean(canonicalRef);
            this.label = clean(label);
        }
    }

    public static final class ReviewItem {
        public final String eventId;
        public final String sourceLabel;
        public final String direction;
        public final long amountMinor;
        public final long occurredAt;
        public final String accountHint;
        public final String merchantHint;
        public final String categoryHint;
        public final String moneyType;
        public final String accountSuggestionRef;
        public final String categorySuggestionRef;
        public final String accountReason;
        public final String categoryReason;
        public final boolean canConfirmMapping;
        public final boolean duplicateLocked;
        public final boolean specialLocked;
        public final boolean historicalReconciliationOnly;
        public final String lockReason;
        public final List<Choice> accountChoices;
        public final List<Choice> categoryChoices;
        public final List<Choice> historicalTransactionChoices;

        private ReviewItem(
                String eventId,
                String sourceLabel,
                String direction,
                long amountMinor,
                long occurredAt,
                String accountHint,
                String merchantHint,
                String categoryHint,
                String moneyType,
                String accountSuggestionRef,
                String categorySuggestionRef,
                String accountReason,
                String categoryReason,
                boolean canConfirmMapping,
                boolean duplicateLocked,
                boolean specialLocked,
                boolean historicalReconciliationOnly,
                String lockReason,
                List<Choice> accountChoices,
                List<Choice> categoryChoices,
                List<Choice> historicalTransactionChoices) {
            this.eventId = clean(eventId);
            this.sourceLabel = clean(sourceLabel);
            this.direction = clean(direction);
            this.amountMinor = amountMinor;
            this.occurredAt = occurredAt;
            this.accountHint = clean(accountHint);
            this.merchantHint = clean(merchantHint);
            this.categoryHint = clean(categoryHint);
            this.moneyType = clean(moneyType);
            this.accountSuggestionRef = clean(accountSuggestionRef);
            this.categorySuggestionRef = clean(categorySuggestionRef);
            this.accountReason = clean(accountReason);
            this.categoryReason = clean(categoryReason);
            this.canConfirmMapping = canConfirmMapping;
            this.duplicateLocked = duplicateLocked;
            this.specialLocked = specialLocked;
            this.historicalReconciliationOnly = historicalReconciliationOnly;
            this.lockReason = clean(lockReason);
            this.accountChoices = Collections.unmodifiableList(accountChoices);
            this.categoryChoices = Collections.unmodifiableList(categoryChoices);
            this.historicalTransactionChoices =
                    Collections.unmodifiableList(historicalTransactionChoices);
        }
    }

    public static final class ConfirmResult {
        public final boolean mappingSaved;
        public final boolean ledgerHandled;
        public final TridevTransactionPostingEngine.Outcome outcome;
        public final String message;

        private ConfirmResult(
                boolean mappingSaved,
                boolean ledgerHandled,
                TridevTransactionPostingEngine.Outcome outcome,
                String message) {
            this.mappingSaved = mappingSaved;
            this.ledgerHandled = ledgerHandled;
            this.outcome = outcome;
            this.message = clean(message);
        }
    }

    private final TridevEventQueue queue;
    private final TridevMoneyMappingEngine mapper;
    private final TridevTransactionPostingEngine postingEngine;
    private final SupportSQLiteDatabase ledger;

    public TridevIntegrationReviewManager(android.content.Context context) {
        android.content.Context appContext = context.getApplicationContext();
        queue = TridevEventQueue.getInstance(appContext);
        mapper = new TridevMoneyMappingEngine(appContext);
        postingEngine = new TridevTransactionPostingEngine(appContext);
        ledger = DatabaseClient.getInstance(appContext)
                .getAppDatabase()
                .getOpenHelper()
                .getReadableDatabase();
    }

    public List<ReviewItem> loadReviewItems(int requestedLimit) {
        List<TridevEventQueue.QueueItem> queued = queue.getReviewBatch(requestedLimit);
        if (queued.isEmpty()) return Collections.emptyList();

        TridevMoneyMappingEngine.Catalog catalog = mapper.readCatalog();
        List<Choice> accountChoices = buildAccountChoices(catalog);
        List<ReviewItem> result = new ArrayList<>();

        for (TridevEventQueue.QueueItem queueItem : queued) {
            if (queueItem == null || queueItem.event == null) continue;
            TridevIntegrationContract.Event event = queueItem.event;
            String moneyType = moneyManagerType(event);
            boolean historicalLocked = isHistoricalSmartSmsReview(queueItem);
            boolean duplicateLocked = hasDuplicateEvidence(queueItem);
            boolean specialLocked = historicalLocked
                    || requiresSpecialReconciliation(event)
                    || moneyType == null;

            List<Choice> categories = moneyType == null
                    ? Collections.emptyList()
                    : buildCategoryChoices(catalog, expectedCategoryType(moneyType));
            List<Choice> historicalChoices = historicalLocked
                    ? buildHistoricalTransactionChoices(event)
                    : Collections.emptyList();

            TridevMoneyMappingEngine.MappingResult accountSuggestion = null;
            TridevMoneyMappingEngine.MappingResult categorySuggestion = null;
            if (!duplicateLocked && !specialLocked) {
                accountSuggestion = mapper.resolveAccount(
                        accountExternalKey(event),
                        event.accountHint,
                        TridevEventFingerprint.lastFour(event.accountHint));
                categorySuggestion = mapper.resolveCategory(
                        categoryExternalKey(event, moneyType),
                        event.categoryHint,
                        expectedCategoryType(moneyType));
            }

            boolean canConfirm = !duplicateLocked
                    && !specialLocked
                    && !accountChoices.isEmpty()
                    && !categories.isEmpty();

            String lockReason = "";
            if (historicalLocked) {
                lockReason = historicalChoices.isEmpty()
                        ? "Historical SmartSMS is reconciliation-only. No same-date manual transaction matches this amount and direction, so no new row will be created."
                        : "Historical SmartSMS is reconciliation-only. Choose the correct existing manual transaction below; no new row will be created.";
            } else if (duplicateLocked) {
                lockReason = "Possible duplicate/existing MoneyManager transaction needs reconciliation before mapping can post it.";
            } else if (specialLocked) {
                lockReason = "Transfer, refund, card-payment or other special event requires reconciliation and will not be auto-posted.";
            } else if (accountChoices.isEmpty()) {
                lockReason = "No active MoneyManager account or credit card is available.";
            } else if (categories.isEmpty()) {
                lockReason = "No matching MoneyManager " + expectedCategoryType(moneyType) + " category is available.";
            }

            result.add(new ReviewItem(
                    event.eventId,
                    sourceLabel(event.sourceApp),
                    event.direction.name(),
                    event.amountMinor,
                    event.occurredAt > 0L ? event.occurredAt : event.createdAt,
                    event.accountHint,
                    event.merchantHint,
                    event.categoryHint,
                    moneyType,
                    refOf(accountSuggestion),
                    refOf(categorySuggestion),
                    reasonOf(accountSuggestion),
                    reasonOf(categorySuggestion),
                    canConfirm,
                    duplicateLocked,
                    specialLocked,
                    historicalLocked,
                    lockReason,
                    new ArrayList<>(accountChoices),
                    new ArrayList<>(categories),
                    new ArrayList<>(historicalChoices)));
        }

        return Collections.unmodifiableList(result);
    }

    public ConfirmResult linkHistoricalExisting(
            String eventId,
            String transactionCanonicalRef) {
        TridevEventQueue.QueueItem item = queue.find(eventId);
        if (item == null || item.event == null) {
            return confirm(false, false,
                    TridevTransactionPostingEngine.Outcome.NOT_FOUND,
                    "Review item is no longer available.");
        }
        if (!isHistoricalSmartSmsReview(item)) {
            return confirm(false, false,
                    TridevTransactionPostingEngine.Outcome.NEEDS_REVIEW,
                    "This item is not a historical SmartSMS reconciliation record.");
        }

        long transactionId = parseTransactionRef(transactionCanonicalRef);
        if (transactionId <= 0L || !isHistoricalCandidate(item.event, transactionId)) {
            return confirm(false, false,
                    TridevTransactionPostingEngine.Outcome.NEEDS_REVIEW,
                    "The selected MoneyManager transaction no longer matches amount, direction and date.");
        }

        boolean linked = queue.confirmExistingMoneyManagerTransaction(eventId, transactionId);
        return linked
                ? confirm(false, true,
                        TridevTransactionPostingEngine.Outcome.RECONCILED_EXISTING,
                        "Historical SMS linked to the existing manual MoneyManager transaction. No new entry was created.")
                : confirm(false, false,
                        TridevTransactionPostingEngine.Outcome.FAILED,
                        "The existing manual transaction could not be linked safely.");
    }

    public ConfirmResult confirmMappingsAndProcess(
            String eventId,
            String accountCanonicalRef,
            String categoryCanonicalRef) {
        TridevEventQueue.QueueItem item = queue.find(eventId);
        if (item == null || item.event == null) {
            return confirm(false, false,
                    TridevTransactionPostingEngine.Outcome.NOT_FOUND,
                    "Review item is no longer available.");
        }

        TridevIntegrationContract.Event event = item.event;
        String moneyType = moneyManagerType(event);
        if (isHistoricalSmartSmsReview(item)) {
            return confirm(false, false,
                    TridevTransactionPostingEngine.Outcome.NEEDS_REVIEW,
                    "Historical SmartSMS is reconciliation-only. Link an existing manual transaction; a new row cannot be posted.");
        }
        if (hasDuplicateEvidence(item)) {
            return confirm(false, false,
                    TridevTransactionPostingEngine.Outcome.NEEDS_REVIEW,
                    "Possible duplicate must be reconciled before this event can be posted.");
        }
        if (requiresSpecialReconciliation(event) || moneyType == null) {
            return confirm(false, false,
                    TridevTransactionPostingEngine.Outcome.NEEDS_REVIEW,
                    "This event requires special transfer/refund reconciliation.");
        }

        String accountRef = clean(accountCanonicalRef).toLowerCase(Locale.ROOT);
        String categoryRef = clean(categoryCanonicalRef).toLowerCase(Locale.ROOT);
        TridevMoneyMappingEngine.Catalog catalog = mapper.readCatalog();

        if (!isValidActiveAccount(catalog, accountRef)) {
            return confirm(false, false,
                    TridevTransactionPostingEngine.Outcome.NEEDS_REVIEW,
                    "Choose an active existing account or credit card.");
        }
        if (!isValidCategory(catalog, categoryRef, expectedCategoryType(moneyType))) {
            return confirm(false, false,
                    TridevTransactionPostingEngine.Outcome.NEEDS_REVIEW,
                    "Choose an existing category matching the transaction type.");
        }

        String accountKey = accountExternalKey(event);
        String categoryKey = categoryExternalKey(event, moneyType);
        boolean accountSaved = mapper.rememberConfirmedAccountMapping(accountKey, accountRef);
        boolean categorySaved = mapper.rememberConfirmedCategoryMapping(categoryKey, categoryRef);
        if (!accountSaved || !categorySaved) {
            return confirm(false, false,
                    TridevTransactionPostingEngine.Outcome.NEEDS_REVIEW,
                    "The selected mapping changed or became unavailable. Refresh and choose again.");
        }

        if (!queue.confirmNotDuplicate(event.eventId)) {
            return confirm(true, false,
                    TridevTransactionPostingEngine.Outcome.FAILED,
                    "Mapping was remembered, but the queued event could not be reopened safely.");
        }

        TridevTransactionPostingEngine.Result posting = postingEngine.process(event.eventId);
        switch (posting.outcome) {
            case POSTED:
                return confirm(true, true, posting.outcome,
                        "Mapping saved and transaction posted safely.");
            case RECONCILED_EXISTING:
            case ALREADY_HANDLED:
                return confirm(true, true, posting.outcome,
                        "Mapping saved and the existing MoneyManager transaction was reconciled.");
            case NEEDS_REVIEW:
                return confirm(true, false, posting.outcome,
                        "Mapping saved. The event still needs a separate reconciliation review.");
            case FAILED:
                return confirm(true, false, posting.outcome,
                        "Mapping saved. Posting failed safely and remains retryable.");
            case NOT_FOUND:
            default:
                return confirm(true, false, posting.outcome,
                        "Mapping saved, but the queued event was not found for processing.");
        }
    }

    private List<Choice> buildHistoricalTransactionChoices(
            TridevIntegrationContract.Event event) {
        List<Choice> result = new ArrayList<>();
        String expectedType = moneyManagerType(event);
        if (expectedType == null || expectedType.isEmpty() || event.amountMinor <= 0L) {
            return result;
        }

        double amount = event.amountMinor / 100.0d;
        try (Cursor cursor = ledger.query(
                "SELECT id, type, account, category, date FROM transactions "
                        + "WHERE ABS(amount - ?) < 0.005 ORDER BY id DESC LIMIT 250",
                new Object[]{amount})) {
            while (cursor.moveToNext() && result.size() < MAX_HISTORICAL_CANDIDATES) {
                long id = cursor.getLong(0);
                String type = text(cursor, 1);
                String account = text(cursor, 2);
                String category = text(cursor, 3);
                String date = text(cursor, 4);
                if (id <= 0L || !expectedType.equalsIgnoreCase(type)) continue;
                Long candidateTime = parseLedgerDate(date);
                if (candidateTime == null || !sameLocalDay(effectiveTime(event), candidateTime)) {
                    continue;
                }
                String label = "#" + id
                        + " • " + safeLabel(account, "Account not set")
                        + " • " + safeLabel(category, "Category not set")
                        + " • " + safeLabel(date, "Date not set");
                result.add(new Choice("transaction:" + id, label));
            }
        } catch (RuntimeException ignored) {
            return new ArrayList<>();
        }
        return result;
    }

    private boolean isHistoricalCandidate(
            TridevIntegrationContract.Event event,
            long transactionId) {
        if (transactionId <= 0L || event.amountMinor <= 0L) return false;
        String expectedType = moneyManagerType(event);
        if (expectedType == null || expectedType.isEmpty()) return false;
        try (Cursor cursor = ledger.query(
                "SELECT amount, type, date FROM transactions WHERE id = ? LIMIT 1",
                new Object[]{transactionId})) {
            if (!cursor.moveToFirst()) return false;
            double amount = cursor.getDouble(0);
            String type = text(cursor, 1);
            String date = text(cursor, 2);
            if (Math.abs(amount - (event.amountMinor / 100.0d)) >= 0.005d) return false;
            if (!expectedType.equalsIgnoreCase(type)) return false;
            Long candidateTime = parseLedgerDate(date);
            return candidateTime != null
                    && sameLocalDay(effectiveTime(event), candidateTime);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private List<Choice> buildAccountChoices(TridevMoneyMappingEngine.Catalog catalog) {
        List<Choice> result = new ArrayList<>();
        for (TridevMoneyMappingEngine.CatalogItem item : catalog.accounts) {
            if (item == null || item.unavailableForNewPosting) continue;
            result.add(new Choice(item.canonicalRef, accountChoiceLabel(item)));
        }
        for (TridevMoneyMappingEngine.CatalogItem item : catalog.creditCards) {
            if (item == null || item.unavailableForNewPosting) continue;
            result.add(new Choice(item.canonicalRef, accountChoiceLabel(item)));
        }
        return result;
    }

    private List<Choice> buildCategoryChoices(
            TridevMoneyMappingEngine.Catalog catalog,
            String expectedType) {
        List<Choice> result = new ArrayList<>();
        for (TridevMoneyMappingEngine.CategoryCatalogItem item : catalog.categories) {
            if (item == null) continue;
            if (!expectedType.equalsIgnoreCase(clean(item.type))) continue;
            result.add(new Choice(item.canonicalRef, item.name));
        }
        return result;
    }

    private boolean isValidActiveAccount(
            TridevMoneyMappingEngine.Catalog catalog,
            String canonicalRef) {
        for (TridevMoneyMappingEngine.CatalogItem item : catalog.accounts) {
            if (item != null && !item.unavailableForNewPosting
                    && canonicalRef.equalsIgnoreCase(clean(item.canonicalRef))) return true;
        }
        for (TridevMoneyMappingEngine.CatalogItem item : catalog.creditCards) {
            if (item != null && !item.unavailableForNewPosting
                    && canonicalRef.equalsIgnoreCase(clean(item.canonicalRef))) return true;
        }
        return false;
    }

    private boolean isValidCategory(
            TridevMoneyMappingEngine.Catalog catalog,
            String canonicalRef,
            String expectedType) {
        for (TridevMoneyMappingEngine.CategoryCatalogItem item : catalog.categories) {
            if (item == null) continue;
            if (canonicalRef.equalsIgnoreCase(clean(item.canonicalRef))
                    && expectedType.equalsIgnoreCase(clean(item.type))) return true;
        }
        return false;
    }

    private boolean isHistoricalSmartSmsReview(TridevEventQueue.QueueItem item) {
        if (item == null || item.event == null) return false;
        TridevIntegrationContract.Event event = item.event;
        if (!TridevIntegrationContract.APP_SMART_SMS.equals(event.sourceApp)) return false;
        if (!clean(event.sourceRecordId).matches("sms:[0-9]+")) return false;
        return clean(event.eventId).startsWith("smartsms:history:")
                || clean(item.lastError).contains(HISTORICAL_REVIEW_MARKER);
    }

    private boolean hasDuplicateEvidence(TridevEventQueue.QueueItem item) {
        if (item.duplicateScore > 0 || !clean(item.duplicateOfEventId).isEmpty()) return true;
        return item.event != null
                && item.event.references != null
                && !clean(item.event.references.moneyManagerTransactionId).isEmpty();
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
        return containsAny(combined,
                "credit card payment",
                "card payment",
                "credit card bill",
                "card bill",
                "cc payment",
                "statement payment",
                "self transfer",
                "own account",
                "internal transfer");
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
                if (event.direction == TridevIntegrationContract.Direction.DEBIT) return "EXPENSE";
                if (event.direction == TridevIntegrationContract.Direction.CREDIT) return "INCOME";
                return null;
            default:
                return null;
        }
    }

    private String accountExternalKey(TridevIntegrationContract.Event event) {
        String canonical = TridevEventFingerprint.canonicalAccountHint(event.accountHint);
        if (canonical.isEmpty()) canonical = "unknown";
        return safeKey(event.sourceApp + ":account:" + canonical);
    }

    private String categoryExternalKey(
            TridevIntegrationContract.Event event,
            String moneyType) {
        String hint = TridevEventFingerprint.normalizeHint(event.categoryHint);
        if (hint.isEmpty()) hint = event.eventType.name().toLowerCase(Locale.ROOT);
        return safeKey(event.sourceApp + ":category:"
                + moneyType.toLowerCase(Locale.ROOT) + ":" + hint);
    }

    private String expectedCategoryType(String moneyType) {
        return "INCOME".equalsIgnoreCase(moneyType) ? "Income" : "Expense";
    }

    private long effectiveTime(TridevIntegrationContract.Event event) {
        return event.occurredAt > 0L ? event.occurredAt : event.createdAt;
    }

    private boolean sameLocalDay(long left, long right) {
        SimpleDateFormat day = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        return day.format(new Date(left)).equals(day.format(new Date(right)));
    }

    @Nullable
    private Long parseLedgerDate(@Nullable String raw) {
        String value = clean(raw);
        if (value.isEmpty()) return null;
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

    private long parseTransactionRef(@Nullable String value) {
        String safe = clean(value).toLowerCase(Locale.ROOT);
        if (!safe.matches("transaction:[0-9]+")) return 0L;
        try {
            return Long.parseLong(safe.substring("transaction:".length()));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private String text(Cursor cursor, int index) {
        return cursor == null || index < 0 || index >= cursor.getColumnCount() || cursor.isNull(index)
                ? "" : clean(cursor.getString(index));
    }

    private String safeLabel(String value, String fallback) {
        String safe = clean(value);
        return safe.isEmpty() ? fallback : safe;
    }

    private String safeKey(String value) {
        String safe = clean(value)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9:_\\- ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return safe.length() <= 150 ? safe : safe.substring(0, 150).trim();
    }

    private String accountChoiceLabel(TridevMoneyMappingEngine.CatalogItem item) {
        String type = clean(item.type);
        if (type.isEmpty()) return clean(item.displayName);
        return clean(item.displayName) + " • " + type;
    }

    private String sourceLabel(String sourceApp) {
        if (TridevIntegrationContract.APP_SMART_SMS.equals(sourceApp)) return "SmartSMSPro";
        if (TridevIntegrationContract.APP_FAMILY_HUB.equals(sourceApp)) return "Family Hub";
        if (TridevIntegrationContract.APP_LOAN_MANAGER.equals(sourceApp)) return "LoanManagerPro";
        if (TridevIntegrationContract.APP_MONEY_MANAGER.equals(sourceApp)) return "MoneyManagerPro";
        return "Tridev Integration";
    }

    private boolean containsAny(String text, String... phrases) {
        if (text == null || text.isEmpty()) return false;
        for (String phrase : phrases) {
            if (text.contains(phrase)) return true;
        }
        return false;
    }

    private String refOf(@Nullable TridevMoneyMappingEngine.MappingResult result) {
        return result == null ? "" : clean(result.canonicalRef);
    }

    private String reasonOf(@Nullable TridevMoneyMappingEngine.MappingResult result) {
        return result == null ? "" : clean(result.reason);
    }

    private ConfirmResult confirm(
            boolean mappingSaved,
            boolean ledgerHandled,
            TridevTransactionPostingEngine.Outcome outcome,
            String message) {
        return new ConfirmResult(mappingSaved, ledgerHandled, outcome, message);
    }

    private static String clean(@Nullable String value) {
        return value == null ? "" : value.trim();
    }
}
