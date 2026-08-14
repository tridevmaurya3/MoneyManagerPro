package com.example.moneymanagerpro;

import android.content.Context;
import android.database.Cursor;

import androidx.annotation.Nullable;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.moneymanagerpro.credit.CreditCardCycleCalculator;
import com.example.moneymanagerpro.database.AppDatabase;
import com.example.moneymanagerpro.database.DatabaseClient;
import com.example.moneymanagerpro.model.CreditCard;
import com.example.moneymanagerpro.model.CreditCardPayment;
import com.example.moneymanagerpro.model.Transaction;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * STEP 7 - Special Reconciliation Center backend.
 *
 * Handles finance events which the normal posting engine intentionally refuses:
 * transfers, ATM cash movement, credit-card bill payments, refunds/reversals and
 * possible duplicates. Every destructive/accounting decision requires an
 * explicit user action from MoneyManagerPro's private UI.
 *
 * Safety rules:
 * - Existing MoneyManager ledger rows are never edited or deleted here.
 * - Transfer reconciliation uses the same TRANSFER_OUT / TRANSFER_IN model as
 *   TransferActivity and also creates CreditCardPayment when the destination is
 *   an existing credit-card account.
 * - Refund reconciliation creates one INCOME row against an existing account and
 *   an existing Income category; this naturally reduces card spend in the
 *   existing card net-spend queries.
 * - Deterministic TRIDEV_EVENT markers make retries crash-safe.
 * - Raw SMS bodies are not available to this class.
 */
public final class TridevSpecialReconciliationManager {

    private static final String NOTE_MARKER_PREFIX = "TRIDEV_EVENT:";
    private static final int MAX_ITEMS = 100;
    private static final int MAX_CANDIDATES = 12;
    private static final long CANDIDATE_WINDOW_MILLIS = 3L * 24L * 60L * 60L * 1000L;

    public static final class Choice {
        public final String canonicalRef;
        public final String label;

        private Choice(String canonicalRef, String label) {
            this.canonicalRef = clean(canonicalRef);
            this.label = clean(label);
        }
    }

    public static final class LedgerCandidate {
        public final long transactionId;
        public final String label;

        private LedgerCandidate(long transactionId, String label) {
            this.transactionId = transactionId;
            this.label = clean(label);
        }
    }

    public static final class SpecialItem {
        public final String eventId;
        public final String sourceLabel;
        public final String eventType;
        public final String direction;
        public final long amountMinor;
        public final long occurredAt;
        public final String accountHint;
        public final String merchantHint;
        public final String categoryHint;
        public final boolean transferLike;
        public final boolean refundLike;
        public final boolean duplicateEvidence;
        public final String duplicateOfEventId;
        public final String existingTransactionRef;
        public final String defaultFromRef;
        public final String defaultToRef;
        public final String defaultRefundAccountRef;
        public final String defaultRefundCategoryRef;
        public final List<Choice> accountChoices;
        public final List<Choice> incomeCategoryChoices;
        public final List<LedgerCandidate> ledgerCandidates;

        private SpecialItem(
                String eventId,
                String sourceLabel,
                String eventType,
                String direction,
                long amountMinor,
                long occurredAt,
                String accountHint,
                String merchantHint,
                String categoryHint,
                boolean transferLike,
                boolean refundLike,
                boolean duplicateEvidence,
                String duplicateOfEventId,
                String existingTransactionRef,
                String defaultFromRef,
                String defaultToRef,
                String defaultRefundAccountRef,
                String defaultRefundCategoryRef,
                List<Choice> accountChoices,
                List<Choice> incomeCategoryChoices,
                List<LedgerCandidate> ledgerCandidates) {
            this.eventId = clean(eventId);
            this.sourceLabel = clean(sourceLabel);
            this.eventType = clean(eventType);
            this.direction = clean(direction);
            this.amountMinor = amountMinor;
            this.occurredAt = occurredAt;
            this.accountHint = clean(accountHint);
            this.merchantHint = clean(merchantHint);
            this.categoryHint = clean(categoryHint);
            this.transferLike = transferLike;
            this.refundLike = refundLike;
            this.duplicateEvidence = duplicateEvidence;
            this.duplicateOfEventId = clean(duplicateOfEventId);
            this.existingTransactionRef = clean(existingTransactionRef);
            this.defaultFromRef = clean(defaultFromRef);
            this.defaultToRef = clean(defaultToRef);
            this.defaultRefundAccountRef = clean(defaultRefundAccountRef);
            this.defaultRefundCategoryRef = clean(defaultRefundCategoryRef);
            this.accountChoices = Collections.unmodifiableList(accountChoices);
            this.incomeCategoryChoices = Collections.unmodifiableList(incomeCategoryChoices);
            this.ledgerCandidates = Collections.unmodifiableList(ledgerCandidates);
        }
    }

    public static final class ActionResult {
        public final boolean handled;
        public final String message;

        private ActionResult(boolean handled, String message) {
            this.handled = handled;
            this.message = clean(message);
        }
    }

    private final Context appContext;
    private final AppDatabase database;
    private final TridevEventQueue queue;
    private final TridevMoneyMappingEngine mapper;

    public TridevSpecialReconciliationManager(Context context) {
        appContext = context.getApplicationContext();
        database = DatabaseClient.getInstance(appContext).getAppDatabase();
        queue = TridevEventQueue.getInstance(appContext);
        mapper = new TridevMoneyMappingEngine(appContext);
    }

    public List<SpecialItem> loadItems(int requestedLimit) {
        int limit = Math.max(1, Math.min(MAX_ITEMS, requestedLimit));
        List<TridevEventQueue.QueueItem> reviewItems = queue.getReviewBatch(limit);
        if (reviewItems.isEmpty()) return Collections.emptyList();

        TridevMoneyMappingEngine.Catalog catalog = mapper.readCatalog();
        List<Choice> accounts = buildAccountChoices(catalog);
        List<Choice> incomeCategories = buildIncomeCategoryChoices(catalog);
        List<SpecialItem> result = new ArrayList<>();

        for (TridevEventQueue.QueueItem queueItem : reviewItems) {
            if (queueItem == null || queueItem.event == null) continue;
            TridevIntegrationContract.Event event = queueItem.event;
            boolean transferLike = isTransferLike(event);
            boolean refundLike = isRefundLike(event);
            boolean duplicateEvidence = hasDuplicateEvidence(queueItem);
            if (!transferLike && !refundLike && !duplicateEvidence) continue;

            String accountSuggestion = suggestedAccountRef(event);
            String cashRef = findCashRef(catalog);
            String defaultFrom = "";
            String defaultTo = "";

            if (transferLike) {
                if (event.direction == TridevIntegrationContract.Direction.CREDIT) {
                    defaultTo = accountSuggestion;
                } else {
                    defaultFrom = accountSuggestion;
                }
                if (isAtmLike(event) && !cashRef.isEmpty()) {
                    defaultTo = cashRef;
                }
            }

            String refundCategory = suggestedRefundCategoryRef(catalog, event);
            result.add(new SpecialItem(
                    event.eventId,
                    sourceLabel(event.sourceApp),
                    event.eventType.name(),
                    event.direction.name(),
                    event.amountMinor,
                    effectiveTime(event),
                    event.accountHint,
                    event.merchantHint,
                    event.categoryHint,
                    transferLike,
                    refundLike,
                    duplicateEvidence,
                    queueItem.duplicateOfEventId,
                    event.references == null ? "" : event.references.moneyManagerTransactionId,
                    defaultFrom,
                    defaultTo,
                    accountSuggestion,
                    refundCategory,
                    new ArrayList<>(accounts),
                    new ArrayList<>(incomeCategories),
                    findLedgerCandidates(event)));
        }

        return Collections.unmodifiableList(result);
    }

    /** Explicitly link the incoming event to an already-existing MoneyManager row. */
    public ActionResult linkExistingTransaction(String eventId, long transactionId) {
        TridevEventQueue.QueueItem item = queue.find(eventId);
        if (item == null || item.event == null) return failure("Review item is no longer available.");
        if (!isValidLedgerCandidate(item.event, transactionId)) {
            return failure("The selected MoneyManager transaction no longer safely matches this event.");
        }
        boolean saved = queue.confirmExistingMoneyManagerTransaction(eventId, transactionId);
        return saved
                ? success("Linked to the existing MoneyManager transaction. No new entry was created.")
                : failure("The existing transaction could not be linked safely.");
    }

    /** Explicitly confirm that the queue's suggested canonical event is the same transaction. */
    public ActionResult confirmQueuedDuplicate(String eventId) {
        TridevEventQueue.QueueItem item = queue.find(eventId);
        if (item == null || item.event == null) return failure("Review item is no longer available.");
        String canonical = clean(item.duplicateOfEventId);
        if (canonical.isEmpty()) return failure("No queued duplicate candidate is available.");
        boolean saved = queue.confirmDuplicate(eventId, canonical);
        return saved
                ? success("Confirmed as the same queued transaction. No duplicate ledger entry will be created.")
                : failure("Queued duplicate could not be confirmed safely.");
    }

    /** Explicitly reject duplicate evidence so the event can return to normal mapping review. */
    public ActionResult returnToMappingReview(String eventId) {
        TridevEventQueue.QueueItem item = queue.find(eventId);
        if (item == null || item.event == null) return failure("Review item is no longer available.");
        if (isTransferLike(item.event) || isRefundLike(item.event)) {
            return failure("This transfer/refund still needs special reconciliation.");
        }
        boolean reopened = queue.confirmNotDuplicate(eventId);
        return reopened
                ? success("Marked as a separate event. It can now be mapped in Integration Review.")
                : failure("The event could not be reopened safely.");
    }

    /**
     * Explicitly process a transfer/ATM/card-payment event as a separate transfer.
     * The user must choose both existing destinations.
     */
    public ActionResult processTransfer(
            String eventId,
            String fromCanonicalRef,
            String toCanonicalRef) {
        TridevEventQueue.QueueItem item = queue.find(eventId);
        if (item == null || item.event == null) return failure("Review item is no longer available.");
        TridevIntegrationContract.Event event = item.event;
        if (!isTransferLike(event)) return failure("This event is not a transfer-style transaction.");

        TridevMoneyMappingEngine.Catalog catalog = mapper.readCatalog();
        TridevMoneyMappingEngine.CatalogItem from = findActiveAccount(catalog, fromCanonicalRef);
        TridevMoneyMappingEngine.CatalogItem to = findActiveAccount(catalog, toCanonicalRef);
        if (from == null || to == null) return failure("Choose two active existing MoneyManager accounts/cards.");
        if (clean(from.transactionValue).equalsIgnoreCase(clean(to.transactionValue))) {
            return failure("From and To accounts must be different.");
        }

        long existingMarkerId = findMarkerTransactionId(event.eventId);
        if (existingMarkerId > 0L) {
            queue.markSynced(event.eventId, String.valueOf(existingMarkerId));
            return success("Recovered the already-created transfer. No duplicate was added.");
        }

        if (!queue.confirmNotDuplicate(event.eventId)) {
            return failure("The event could not be reopened for separate transfer processing.");
        }

        final long[] outId = {0L};
        try {
            Transaction transferOut = new Transaction();
            transferOut.setType("TRANSFER_OUT");
            transferOut.setAmount(minorToDouble(event.amountMinor));
            transferOut.setCategory("Account Transfer");
            transferOut.setAccount(from.transactionValue);
            transferOut.setDate(formatDate(event));
            transferOut.setNote(transferNote(event, "Transfer to " + to.transactionValue));

            Transaction transferIn = new Transaction();
            transferIn.setType("TRANSFER_IN");
            transferIn.setAmount(minorToDouble(event.amountMinor));
            transferIn.setCategory("Account Transfer");
            transferIn.setAccount(to.transactionValue);
            transferIn.setDate(formatDate(event));
            transferIn.setNote(transferNote(event, "Transfer from " + from.transactionValue));

            CreditCard destinationCard = database.creditCardDao().findByAccountName(to.transactionValue);
            CreditCardPayment cardPayment = buildCardPayment(
                    destinationCard,
                    event,
                    from.transactionValue);

            database.runInTransaction(() -> {
                long markerId = findMarkerTransactionId(event.eventId);
                if (markerId > 0L) {
                    outId[0] = markerId;
                    return;
                }
                long insertedOut = database.transactionDao().insert(transferOut);
                long insertedIn = database.transactionDao().insert(transferIn);
                if (insertedOut <= 0L || insertedIn <= 0L) {
                    throw new IllegalStateException("Transfer rows were not persisted");
                }
                outId[0] = insertedOut;
                if (cardPayment != null) {
                    database.creditCardPaymentDao().insert(cardPayment);
                }
            });

            if (outId[0] <= 0L) throw new IllegalStateException("Transfer reference is missing");
            queue.markSynced(event.eventId, String.valueOf(outId[0]));
            return success(destinationCard == null
                    ? "Transfer reconciled safely. No expense/income duplicate was created."
                    : "Credit-card payment reconciled as a transfer and card payment record was updated.");
        } catch (RuntimeException failure) {
            queue.markNeedsReview(event.eventId, null, 0);
            return failure("Transfer reconciliation failed safely; no retry duplicate will be created.");
        }
    }

    /** Explicitly process a refund/reversal as a separate INCOME row. */
    public ActionResult processRefund(
            String eventId,
            String accountCanonicalRef,
            String incomeCategoryCanonicalRef) {
        TridevEventQueue.QueueItem item = queue.find(eventId);
        if (item == null || item.event == null) return failure("Review item is no longer available.");
        TridevIntegrationContract.Event event = item.event;
        if (!isRefundLike(event)) return failure("This event is not a refund/reversal transaction.");

        TridevMoneyMappingEngine.Catalog catalog = mapper.readCatalog();
        TridevMoneyMappingEngine.CatalogItem account = findActiveAccount(catalog, accountCanonicalRef);
        TridevMoneyMappingEngine.CategoryCatalogItem category =
                findIncomeCategory(catalog, incomeCategoryCanonicalRef);
        if (account == null) return failure("Choose an active existing MoneyManager account/card.");
        if (category == null) return failure("Choose an existing Income category for this refund.");

        long markerId = findMarkerTransactionId(event.eventId);
        if (markerId > 0L) {
            queue.markSynced(event.eventId, String.valueOf(markerId));
            return success("Recovered the already-created refund. No duplicate was added.");
        }
        if (!queue.confirmNotDuplicate(event.eventId)) {
            return failure("The event could not be reopened for separate refund processing.");
        }

        try {
            Transaction refund = new Transaction();
            refund.setType("INCOME");
            refund.setAmount(minorToDouble(event.amountMinor));
            refund.setCategory(category.name);
            refund.setAccount(account.transactionValue);
            refund.setDate(formatDate(event));
            refund.setNote(refundNote(event));

            final long[] insertedId = {0L};
            database.runInTransaction(() -> {
                long existing = findMarkerTransactionId(event.eventId);
                if (existing > 0L) {
                    insertedId[0] = existing;
                    return;
                }
                insertedId[0] = database.transactionDao().insert(refund);
                if (insertedId[0] <= 0L) {
                    throw new IllegalStateException("Refund row was not persisted");
                }
            });

            queue.markSynced(event.eventId, String.valueOf(insertedId[0]));
            return success("Refund/reversal reconciled safely as a credit. No duplicate income was created.");
        } catch (RuntimeException failure) {
            queue.markNeedsReview(event.eventId, null, 0);
            return failure("Refund reconciliation failed safely and remains in review.");
        }
    }

    private List<Choice> buildAccountChoices(TridevMoneyMappingEngine.Catalog catalog) {
        List<Choice> result = new ArrayList<>();
        for (TridevMoneyMappingEngine.CatalogItem item : catalog.accounts) {
            if (item == null || item.unavailableForNewPosting) continue;
            result.add(new Choice(item.canonicalRef, accountLabel(item)));
        }
        for (TridevMoneyMappingEngine.CatalogItem item : catalog.creditCards) {
            if (item == null || item.unavailableForNewPosting) continue;
            result.add(new Choice(item.canonicalRef, accountLabel(item)));
        }
        return result;
    }

    private List<Choice> buildIncomeCategoryChoices(TridevMoneyMappingEngine.Catalog catalog) {
        List<Choice> result = new ArrayList<>();
        for (TridevMoneyMappingEngine.CategoryCatalogItem item : catalog.categories) {
            if (item == null || !"income".equalsIgnoreCase(clean(item.type))) continue;
            result.add(new Choice(item.canonicalRef, item.name));
        }
        return result;
    }

    private String suggestedAccountRef(TridevIntegrationContract.Event event) {
        TridevMoneyMappingEngine.MappingResult suggestion = mapper.resolveAccount(
                accountExternalKey(event),
                event.accountHint,
                TridevEventFingerprint.lastFour(event.accountHint));
        if (suggestion == null || suggestion.canonicalRef == null) return "";
        if (suggestion.confidence != TridevIntegrationContract.MatchConfidence.EXACT
                || suggestion.needsReview) return "";
        return suggestion.canonicalRef;
    }

    private String suggestedRefundCategoryRef(
            TridevMoneyMappingEngine.Catalog catalog,
            TridevIntegrationContract.Event event) {
        for (TridevMoneyMappingEngine.CategoryCatalogItem item : catalog.categories) {
            if (item == null || !"income".equalsIgnoreCase(clean(item.type))) continue;
            if ("refund".equalsIgnoreCase(clean(item.name))) return item.canonicalRef;
        }
        TridevMoneyMappingEngine.MappingResult suggestion = mapper.resolveCategory(
                categoryExternalKey(event, "INCOME"),
                event.categoryHint,
                "Income");
        if (suggestion == null || suggestion.canonicalRef == null) return "";
        return suggestion.confidence == TridevIntegrationContract.MatchConfidence.EXACT
                && !suggestion.needsReview ? suggestion.canonicalRef : "";
    }

    private List<LedgerCandidate> findLedgerCandidates(TridevIntegrationContract.Event event) {
        List<LedgerCandidate> result = new ArrayList<>();
        SupportSQLiteDatabase db = database.getOpenHelper().getReadableDatabase();
        long center = effectiveTime(event);
        String start = formatDate(center - CANDIDATE_WINDOW_MILLIS);
        String end = formatDate(center + CANDIDATE_WINDOW_MILLIS);
        double amount = minorToDouble(event.amountMinor);

        try (Cursor cursor = db.query(
                "SELECT id, type, account, category, date FROM transactions "
                        + "WHERE ABS(amount - ?) < 0.005 AND date BETWEEN ? AND ? "
                        + "ORDER BY id DESC LIMIT " + MAX_CANDIDATES,
                new Object[]{amount, start, end})) {
            while (cursor.moveToNext()) {
                long id = cursor.getLong(0);
                String type = safeCursor(cursor, 1);
                String account = safeCursor(cursor, 2);
                String category = safeCursor(cursor, 3);
                String date = safeCursor(cursor, 4);
                result.add(new LedgerCandidate(
                        id,
                        "#" + id + " • " + safeLabel(type, "Transaction")
                                + " • " + safeLabel(account, "Unknown account")
                                + " • " + safeLabel(category, "Uncategorised")
                                + " • " + safeLabel(date, "Unknown date")));
            }
        } catch (RuntimeException ignored) {
            // Candidate discovery is optional; reconciliation remains fail-closed.
        }
        return result;
    }

    private boolean isValidLedgerCandidate(
            TridevIntegrationContract.Event event,
            long transactionId) {
        if (transactionId <= 0L) return false;
        SupportSQLiteDatabase db = database.getOpenHelper().getReadableDatabase();
        try (Cursor cursor = db.query(
                "SELECT amount, date FROM transactions WHERE id = ? LIMIT 1",
                new Object[]{transactionId})) {
            if (!cursor.moveToFirst()) return false;
            double amount = cursor.getDouble(0);
            String date = safeCursor(cursor, 1);
            if (Math.abs(amount - minorToDouble(event.amountMinor)) >= 0.005d) return false;
            long parsed = parseDate(date);
            return parsed > 0L && Math.abs(parsed - effectiveTime(event)) <= CANDIDATE_WINDOW_MILLIS;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    @Nullable
    private TridevMoneyMappingEngine.CatalogItem findActiveAccount(
            TridevMoneyMappingEngine.Catalog catalog,
            String canonicalRef) {
        String ref = clean(canonicalRef);
        for (TridevMoneyMappingEngine.CatalogItem item : catalog.accounts) {
            if (item != null && !item.unavailableForNewPosting
                    && ref.equalsIgnoreCase(clean(item.canonicalRef))) return item;
        }
        for (TridevMoneyMappingEngine.CatalogItem item : catalog.creditCards) {
            if (item != null && !item.unavailableForNewPosting
                    && ref.equalsIgnoreCase(clean(item.canonicalRef))) return item;
        }
        return null;
    }

    @Nullable
    private TridevMoneyMappingEngine.CategoryCatalogItem findIncomeCategory(
            TridevMoneyMappingEngine.Catalog catalog,
            String canonicalRef) {
        String ref = clean(canonicalRef);
        for (TridevMoneyMappingEngine.CategoryCatalogItem item : catalog.categories) {
            if (item != null && "income".equalsIgnoreCase(clean(item.type))
                    && ref.equalsIgnoreCase(clean(item.canonicalRef))) return item;
        }
        return null;
    }

    private String findCashRef(TridevMoneyMappingEngine.Catalog catalog) {
        for (TridevMoneyMappingEngine.CatalogItem item : catalog.accounts) {
            if (item == null || item.unavailableForNewPosting) continue;
            if ("cash".equalsIgnoreCase(clean(item.displayName))
                    || "cash".equalsIgnoreCase(clean(item.transactionValue))) {
                return item.canonicalRef;
            }
        }
        return "";
    }

    @Nullable
    private CreditCardPayment buildCardPayment(
            @Nullable CreditCard destinationCard,
            TridevIntegrationContract.Event event,
            String sourceAccount) {
        if (destinationCard == null) return null;
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(effectiveTime(event));
        CreditCardCycleCalculator.Cycle cycle =
                CreditCardCycleCalculator.calculate(destinationCard, calendar);

        CreditCardPayment payment = new CreditCardPayment();
        payment.setCreditCardId(destinationCard.getId());
        payment.setStatementEndDate(cycle.closedEnd);
        payment.setAmount(minorToDouble(event.amountMinor));
        payment.setPaymentDate(formatDate(event));
        payment.setSourceAccount(sourceAccount);
        payment.setNote(marker(event.eventId) + " • Synced card payment");
        return payment;
    }

    private boolean hasDuplicateEvidence(TridevEventQueue.QueueItem item) {
        if (item.duplicateScore > 0 || !clean(item.duplicateOfEventId).isEmpty()) return true;
        return item.event != null && item.event.references != null
                && !clean(item.event.references.moneyManagerTransactionId).isEmpty();
    }

    private boolean isTransferLike(TridevIntegrationContract.Event event) {
        if (event.eventType == TridevIntegrationContract.EventType.TRANSFER
                || event.direction == TridevIntegrationContract.Direction.TRANSFER) return true;
        String text = normalizedEvidence(event);
        return containsAny(text,
                "atm", "cash withdrawal", "cash withdrawn",
                "credit card payment", "card payment", "credit card bill", "card bill",
                "cc payment", "statement payment", "self transfer", "own account",
                "internal transfer", "bank transfer");
    }

    private boolean isAtmLike(TridevIntegrationContract.Event event) {
        String text = normalizedEvidence(event);
        return containsAny(text, "atm", "cash withdrawal", "cash withdrawn");
    }

    private boolean isRefundLike(TridevIntegrationContract.Event event) {
        if (event.eventType == TridevIntegrationContract.EventType.REFUND) return true;
        String text = normalizedEvidence(event);
        return event.direction == TridevIntegrationContract.Direction.CREDIT
                && containsAny(text, "refund", "reversal", "cashback");
    }

    private String normalizedEvidence(TridevIntegrationContract.Event event) {
        return (TridevEventFingerprint.normalizeHint(event.categoryHint) + " "
                + TridevEventFingerprint.normalizeHint(event.merchantHint)).trim();
    }

    private boolean containsAny(String text, String... values) {
        String safe = clean(text).toLowerCase(Locale.ROOT);
        for (String value : values) {
            if (safe.contains(value)) return true;
        }
        return false;
    }

    private long findMarkerTransactionId(String eventId) {
        String marker = marker(eventId);
        if (marker.isEmpty()) return 0L;
        SupportSQLiteDatabase db = database.getOpenHelper().getReadableDatabase();
        try (Cursor cursor = db.query(
                "SELECT id FROM transactions WHERE instr(note, ?) > 0 ORDER BY id ASC LIMIT 1",
                new Object[]{marker})) {
            return cursor.moveToFirst() ? cursor.getLong(0) : 0L;
        } catch (RuntimeException ignored) {
            return 0L;
        }
    }

    private String transferNote(TridevIntegrationContract.Event event, String label) {
        return safeNote(marker(event.eventId) + " • " + label + " • Synced from "
                + sourceLabel(event.sourceApp));
    }

    private String refundNote(TridevIntegrationContract.Event event) {
        String merchant = safeMetadata(event.merchantHint, 50);
        String note = marker(event.eventId) + " • Refund synced from " + sourceLabel(event.sourceApp);
        if (!merchant.isEmpty()) note += " • " + merchant;
        return safeNote(note);
    }

    private String marker(String eventId) {
        String safe = clean(eventId).replaceAll("[^A-Za-z0-9:_\\-]", "");
        if (safe.isEmpty()) return "";
        if (safe.length() > 100) safe = safe.substring(0, 100);
        return NOTE_MARKER_PREFIX + safe;
    }

    private String safeNote(String value) {
        String safe = safeMetadata(value, 240);
        return safe.length() <= 240 ? safe : safe.substring(0, 240);
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

    private String safeKey(String value) {
        String safe = clean(value).toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9:_\\- ]", " ")
                .replaceAll("\\s+", " ").trim();
        return safe.length() <= 150 ? safe : safe.substring(0, 150).trim();
    }

    private String accountLabel(TridevMoneyMappingEngine.CatalogItem item) {
        String type = clean(item.type);
        return type.isEmpty() ? clean(item.displayName) : clean(item.displayName) + " • " + type;
    }

    private String sourceLabel(String sourceApp) {
        if (TridevIntegrationContract.APP_SMART_SMS.equals(sourceApp)) return "SmartSMSPro";
        if (TridevIntegrationContract.APP_FAMILY_HUB.equals(sourceApp)) return "Family Hub";
        if (TridevIntegrationContract.APP_LOAN_MANAGER.equals(sourceApp)) return "LoanManagerPro";
        if (TridevIntegrationContract.APP_MONEY_MANAGER.equals(sourceApp)) return "MoneyManagerPro";
        return "Tridev Integration";
    }

    private long effectiveTime(TridevIntegrationContract.Event event) {
        return event.occurredAt > 0L ? event.occurredAt : event.createdAt;
    }

    private double minorToDouble(long amountMinor) {
        return BigDecimal.valueOf(amountMinor).movePointLeft(2).doubleValue();
    }

    private String formatDate(TridevIntegrationContract.Event event) {
        return formatDate(effectiveTime(event));
    }

    private String formatDate(long time) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date(time));
    }

    private long parseDate(String value) {
        try {
            Date parsed = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).parse(clean(value));
            return parsed == null ? 0L : parsed.getTime();
        } catch (ParseException ignored) {
            return 0L;
        }
    }

    private String safeCursor(Cursor cursor, int index) {
        if (cursor == null || index < 0 || index >= cursor.getColumnCount() || cursor.isNull(index)) return "";
        try {
            String value = cursor.getString(index);
            return value == null ? "" : value.trim();
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private String safeMetadata(@Nullable String value, int maxLength) {
        String safe = clean(value).replace('\n', ' ').replace('\r', ' ')
                .replaceAll("\\s+", " ").trim();
        return safe.length() <= maxLength ? safe : safe.substring(0, maxLength).trim();
    }

    private String safeLabel(String value, String fallback) {
        String safe = clean(value);
        return safe.isEmpty() ? fallback : safe;
    }

    private ActionResult success(String message) {
        return new ActionResult(true, message);
    }

    private ActionResult failure(String message) {
        return new ActionResult(false, message);
    }

    private static String clean(@Nullable String value) {
        return value == null ? "" : value.trim();
    }
}
