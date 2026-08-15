package com.example.moneymanagerpro;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Master-catalog and structured finance endpoint for Family Hub and
 * LoanManagerPro.
 *
 * Unlike the legacy same-signature endpoints, each companion may keep its own
 * Android signing identity. MoneyManager pins that companion certificate on the
 * first trusted Binder/package connection and requires the same certificate on
 * later calls.
 *
 * Only active account/card labels, stable refs, category names/types and
 * structured finance metadata are exposed. Family Hub may additionally read a
 * current-month aggregate summary and category aggregates. Individual
 * transaction rows, notes, contacts and raw SMS bodies are never exposed here.
 */
public final class TridevCompanionFinanceProvider extends ContentProvider {

    public static final String AUTHORITY =
            "com.example.moneymanagerpro.tridev.companion";

    public static final String METHOD_MASTER_CATALOG = "get_master_catalog_v1";
    public static final String METHOD_FINANCE_SUMMARY = "get_finance_summary_v1";
    public static final String METHOD_ACCEPT_FAMILY = "accept_family_event_v1";
    public static final String METHOD_CANCEL_GROCERY = "cancel_family_grocery_v1";
    public static final String METHOD_CANCEL_FAMILY_FINANCE = "cancel_family_finance_event_v1";
    public static final String METHOD_ACCEPT_LOAN = "accept_loan_payment_v1";

    private enum CallerKind { FAMILY_HUB, LOAN_MANAGER, NONE }

    @Override
    public boolean onCreate() {
        return getContext() != null;
    }

    @Nullable
    @Override
    public Bundle call(
            @NonNull String method,
            @Nullable String arg,
            @Nullable Bundle extras) {
        Context context = getContext();
        if (context == null) return response("FAILED", "", null, null,
                "MoneyManager context is unavailable");

        CallerKind caller = trustedCaller(context);
        if (caller == CallerKind.NONE) {
            return response("REJECTED", "", null, null,
                    "Companion package or pinned signing certificate is not trusted");
        }

        if (METHOD_MASTER_CATALOG.equals(method)) {
            return masterCatalog(context);
        }
        if (METHOD_FINANCE_SUMMARY.equals(method)) {
            if (caller != CallerKind.FAMILY_HUB) {
                return response("REJECTED", "", null, null,
                        "Only Family Hub can read the finance summary");
            }
            return financeSummary(context);
        }
        if (METHOD_ACCEPT_FAMILY.equals(method)) {
            if (caller != CallerKind.FAMILY_HUB) {
                return response("REJECTED", "", null, null,
                        "Only Family Hub can submit family finance events");
            }
            return acceptFamily(context, extras);
        }
        if (METHOD_CANCEL_GROCERY.equals(method)) {
            if (caller != CallerKind.FAMILY_HUB) {
                return response("REJECTED", "", null, null,
                        "Only Family Hub can cancel grocery events");
            }
            return cancelFamilyGrocery(context, extras);
        }
        if (METHOD_CANCEL_FAMILY_FINANCE.equals(method)) {
            if (caller != CallerKind.FAMILY_HUB) {
                return response("REJECTED", "", null, null,
                        "Only Family Hub can cancel finance events");
            }
            return cancelFamilyFinance(context, extras);
        }
        if (METHOD_ACCEPT_LOAN.equals(method)) {
            if (caller != CallerKind.LOAN_MANAGER) {
                return response("REJECTED", "", null, null,
                        "Only LoanManagerPro can submit loan payments");
            }
            return acceptLoan(context, extras);
        }
        return response("REJECTED", "", null, null,
                "Unsupported companion integration request");
    }

    @NonNull
    private Bundle masterCatalog(@NonNull Context context) {
        try {
            TridevMoneyMappingEngine.Catalog catalog =
                    new TridevMoneyMappingEngine(context).readCatalog();
            ArrayList<String> accountRefs = new ArrayList<>();
            ArrayList<String> accountLabels = new ArrayList<>();
            ArrayList<String> categoryRefs = new ArrayList<>();
            ArrayList<String> categoryLabels = new ArrayList<>();
            ArrayList<String> categoryTypes = new ArrayList<>();

            for (TridevMoneyMappingEngine.CatalogItem item : catalog.accounts) {
                addAccount(item, accountRefs, accountLabels);
            }
            for (TridevMoneyMappingEngine.CatalogItem item : catalog.creditCards) {
                addAccount(item, accountRefs, accountLabels);
            }
            for (TridevMoneyMappingEngine.CategoryCatalogItem item : catalog.categories) {
                if (item == null || safe(item.canonicalRef).isEmpty()
                        || safe(item.name).isEmpty()) continue;
                categoryRefs.add(item.canonicalRef);
                categoryLabels.add(item.name);
                categoryTypes.add(safe(item.type));
            }

            Bundle result = response("OK", "", null, null,
                    "MoneyManager master catalog ready");
            result.putStringArrayList("account_refs", accountRefs);
            result.putStringArrayList("account_labels", accountLabels);
            result.putStringArrayList("category_refs", categoryRefs);
            result.putStringArrayList("category_labels", categoryLabels);
            result.putStringArrayList("category_types", categoryTypes);
            return result;
        } catch (RuntimeException unavailable) {
            return response("FAILED", "", null, null,
                    "MoneyManager master catalog is unavailable");
        }
    }

    @NonNull
    private Bundle financeSummary(@NonNull Context context) {
        try {
            TridevFinanceMasterSummary.Snapshot snapshot =
                    TridevFinanceMasterSummary.loadCurrentMonth(context);
            Bundle result = response("OK", "", null, null,
                    "MoneyManager current-month finance summary ready");
            result.putString("currency", TridevIntegrationContract.DEFAULT_CURRENCY);
            result.putLong("income_minor", snapshot.incomeMinor);
            result.putLong("expense_minor", snapshot.expenseMinor);
            result.putLong("remaining_minor", snapshot.remainingMinor);
            result.putLong("total_account_balance_minor", snapshot.totalAccountBalanceMinor);
            result.putInt("transaction_count", snapshot.transactionCount);
            result.putInt("account_count", snapshot.accountCount);
            result.putInt("active_card_count", snapshot.activeCardCount);
            result.putString("period_start", snapshot.periodStart);
            result.putString("period_end", snapshot.periodEnd);
            result.putString("period_label", snapshot.periodLabel);
            result.putStringArray("expense_category_labels", snapshot.expenseCategoryLabels);
            result.putLongArray("expense_category_totals_minor", snapshot.expenseCategoryTotalsMinor);
            result.putStringArray("income_category_labels", snapshot.incomeCategoryLabels);
            result.putLongArray("income_category_totals_minor", snapshot.incomeCategoryTotalsMinor);
            result.putLong("generated_at", snapshot.generatedAt);
            return result;
        } catch (RuntimeException unavailable) {
            return response("FAILED", "", null, null,
                    "MoneyManager finance summary is unavailable");
        }
    }

    private void addAccount(
            @Nullable TridevMoneyMappingEngine.CatalogItem item,
            @NonNull ArrayList<String> refs,
            @NonNull ArrayList<String> labels) {
        if (item == null || item.unavailableForNewPosting) return;
        String ref = safe(item.canonicalRef);
        String label = safe(item.displayName);
        if (ref.isEmpty() || label.isEmpty()) return;
        String type = safe(item.type);
        if (!type.isEmpty()) label += " • " + type;
        refs.add(ref);
        labels.add(limit(label, 120));
    }

    @NonNull
    private Bundle acceptFamily(
            @NonNull Context context,
            @Nullable Bundle extras) {
        if (extras == null) return response("REJECTED", "", null, null,
                "Family finance payload is missing");
        try {
            String eventId = structured(extras.getString("event_id"), 120, false);
            String sourceRecordId = structured(
                    extras.getString("source_record_id"), 160, false);
            String eventTypeValue = structured(
                    extras.getString("event_type"), 40, false);
            String directionValue = structured(
                    extras.getString("direction"), 20, false);
            String scopeValue = structured(extras.getString("scope"), 20, true);
            long amountMinor = extras.getLong("amount_minor", 0L);
            long occurredAt = extras.getLong("occurred_at", 0L);
            String currency = structured(extras.getString("currency"), 8, false)
                    .toUpperCase(Locale.ROOT);
            String accountHint = metadata(extras.getString("account_hint"), 160);
            String merchantHint = metadata(extras.getString("merchant_hint"), 120);
            String categoryHint = metadata(extras.getString("category_hint"), 80);
            String fingerprint = structured(extras.getString("fingerprint"), 160, true);
            boolean forceReview = extras.getBoolean("force_review", false);

            if (amountMinor <= 0L || occurredAt <= 0L) throw new IllegalArgumentException();
            if (!TridevIntegrationContract.DEFAULT_CURRENCY.equalsIgnoreCase(currency)) {
                throw new IllegalArgumentException();
            }

            TridevIntegrationContract.EventType eventType =
                    familyEventType(eventTypeValue);
            TridevIntegrationContract.Direction direction = direction(directionValue);
            validateFamilyDirection(eventType, direction);
            TridevIntegrationContract.Scope scope = familyScope(eventType, scopeValue);
            boolean grocery = eventType
                    == TridevIntegrationContract.EventType.GROCERY_PURCHASE;

            TridevIntegrationContract.References references =
                    new TridevIntegrationContract.References(
                            "", "", "",
                            grocery ? "" : sourceRecordId,
                            grocery ? sourceRecordId : "",
                            "", "");

            TridevIntegrationContract.Event event = new TridevIntegrationContract.Event(
                    eventId,
                    TridevIntegrationContract.APP_FAMILY_HUB,
                    sourceRecordId,
                    eventType,
                    direction,
                    scope,
                    amountMinor,
                    TridevIntegrationContract.DEFAULT_CURRENCY,
                    occurredAt,
                    System.currentTimeMillis(),
                    accountHint,
                    merchantHint,
                    categoryHint,
                    "",
                    fingerprint,
                    forceReview
                            ? TridevIntegrationContract.SyncState.NEEDS_REVIEW
                            : TridevIntegrationContract.SyncState.PENDING,
                    TridevIntegrationContract.MatchConfidence.UNMATCHED,
                    references);

            TridevFinanceIntegrationCoordinator.Result result =
                    new TridevFinanceIntegrationCoordinator(context).acceptAndProcess(event);
            return response(result.outcome.name(), result.eventId,
                    result.canonicalEventId, result.moneyManagerTransactionId,
                    result.reason);
        } catch (RuntimeException invalid) {
            return response("REJECTED", "", null, null,
                    "Family finance event failed validation");
        }
    }

    @NonNull
    private Bundle cancelFamilyGrocery(
            @NonNull Context context,
            @Nullable Bundle extras) {
        if (extras == null) return response("REJECTED", "", null, null,
                "Grocery cancellation payload is missing");
        try {
            String eventId = structured(extras.getString("event_id"), 120, false);
            String sourceRecordId = structured(
                    extras.getString("source_record_id"), 160, false);
            TridevFamilyHubCancellationManager.Result result =
                    new TridevFamilyHubCancellationManager(context)
                            .cancelGroceryPurchase(eventId, sourceRecordId);
            return response(result.handled ? "CANCELLED" : "REJECTED",
                    eventId, null, null, result.reason);
        } catch (RuntimeException invalid) {
            return response("REJECTED", "", null, null,
                    "Grocery cancellation failed validation");
        }
    }

    @NonNull
    private Bundle cancelFamilyFinance(
            @NonNull Context context,
            @Nullable Bundle extras) {
        if (extras == null) return response("REJECTED", "", null, null,
                "Finance cancellation payload is missing");
        try {
            String eventId = structured(extras.getString("event_id"), 120, false);
            String sourceRecordId = structured(
                    extras.getString("source_record_id"), 160, false);
            TridevFamilyHubCancellationManager.Result result =
                    new TridevFamilyHubCancellationManager(context)
                            .cancelFinanceEntry(eventId, sourceRecordId);
            String status = result.handled
                    ? (result.ledgerRemoved ? "CANCELLED" : "PRESERVED")
                    : "REJECTED";
            return response(status, eventId, null, null, result.reason);
        } catch (RuntimeException invalid) {
            return response("REJECTED", "", null, null,
                    "Finance cancellation failed validation");
        }
    }

    @NonNull
    private Bundle acceptLoan(
            @NonNull Context context,
            @Nullable Bundle extras) {
        if (extras == null) return response("REJECTED", "", null, null,
                "Loan payment payload is missing");
        try {
            String eventId = structured(extras.getString("event_id"), 120, false);
            String sourceRecordId = structured(
                    extras.getString("source_record_id"), 160, false);
            String loanId = structured(extras.getString("loan_id"), 40, false);
            String paymentId = structured(extras.getString("payment_id"), 40, false);
            String paymentType = structured(extras.getString("payment_type"), 24, false)
                    .toUpperCase(Locale.ROOT);
            long amountMinor = extras.getLong("amount_minor", 0L);
            long occurredAt = extras.getLong("occurred_at", 0L);
            String accountHint = metadata(extras.getString("account_hint"), 160);
            String lenderHint = metadata(extras.getString("lender_hint"), 120);
            String categoryHint = metadata(extras.getString("category_hint"), 80);
            String scopeValue = structured(extras.getString("scope"), 20, false)
                    .toUpperCase(Locale.ROOT);
            String fingerprint = structured(extras.getString("fingerprint"), 160, true);
            boolean forceReview = extras.getBoolean("force_review", false);

            if (!"EMI".equals(paymentType) && !"PREPAYMENT".equals(paymentType)) {
                throw new IllegalArgumentException();
            }
            if (amountMinor <= 0L || occurredAt <= 0L) throw new IllegalArgumentException();

            TridevIntegrationContract.Scope scope;
            if ("FAMILY".equals(scopeValue)) {
                scope = TridevIntegrationContract.Scope.FAMILY;
            } else if ("PERSONAL".equals(scopeValue)) {
                scope = TridevIntegrationContract.Scope.PERSONAL;
            } else {
                throw new IllegalArgumentException();
            }

            TridevIntegrationContract.References references =
                    new TridevIntegrationContract.References(
                            "", "", "", "", "", loanId, paymentId);
            TridevIntegrationContract.Event event = new TridevIntegrationContract.Event(
                    eventId,
                    TridevIntegrationContract.APP_LOAN_MANAGER,
                    sourceRecordId,
                    TridevIntegrationContract.EventType.LOAN_PAYMENT,
                    TridevIntegrationContract.Direction.DEBIT,
                    scope,
                    amountMinor,
                    TridevIntegrationContract.DEFAULT_CURRENCY,
                    occurredAt,
                    System.currentTimeMillis(),
                    accountHint,
                    lenderHint,
                    categoryHint,
                    "",
                    fingerprint,
                    forceReview
                            ? TridevIntegrationContract.SyncState.NEEDS_REVIEW
                            : TridevIntegrationContract.SyncState.PENDING,
                    TridevIntegrationContract.MatchConfidence.UNMATCHED,
                    references);

            TridevFinanceIntegrationCoordinator.Result result =
                    new TridevFinanceIntegrationCoordinator(context).acceptAndProcess(event);
            if (result.outcome == TridevFinanceIntegrationCoordinator.Outcome.DUPLICATE) {
                Bundle reconciled = finalizedDuplicateResponse(context, event);
                if (reconciled != null) return reconciled;
            }
            return response(result.outcome.name(), result.eventId,
                    result.canonicalEventId, result.moneyManagerTransactionId,
                    result.reason);
        } catch (RuntimeException invalid) {
            return response("REJECTED", "", null, null,
                    "Loan payment failed validation");
        }
    }

    /**
     * A duplicate-chain is finalized only when its canonical MoneyManager ledger
     * representation can still be strongly verified. If the canonical queue row
     * still says SYNCED but the ledger row was deleted/reused, reopen this exact
     * LoanManager payment and post it through the normal safe mapping engine.
     */
    @Nullable
    private Bundle finalizedDuplicateResponse(
            @NonNull Context context,
            @NonNull TridevIntegrationContract.Event loanEvent) {
        String loanEventId = safe(loanEvent.eventId);
        TridevEventQueue queue = TridevEventQueue.getInstance(context);
        TridevEventQueue.QueueItem current = queue.find(loanEventId);
        if (current == null || current.event == null) return null;

        String nextId = safe(current.duplicateOfEventId);
        for (int depth = 0; depth < 6 && !nextId.isEmpty(); depth++) {
            TridevEventQueue.QueueItem canonical = queue.find(nextId);
            if (canonical == null || canonical.event == null) return null;

            String transactionId = canonical.event.references == null
                    ? ""
                    : safe(canonical.event.references.moneyManagerTransactionId);
            if (canonical.event.syncState == TridevIntegrationContract.SyncState.SYNCED) {
                TridevExistingTransactionMatcher.Match verified =
                        new TridevExistingTransactionMatcher(context).findBest(canonical.event);
                if (verified != null && verified.score >= 90) {
                    String verifiedId = String.valueOf(verified.transactionId);
                    if (!verifiedId.equals(transactionId)) {
                        queue.confirmExistingMoneyManagerTransaction(
                                canonical.event.eventId, verified.transactionId);
                    }
                    return response("RECONCILED", loanEventId,
                            canonical.event.eventId, verifiedId,
                            "Loan payment reconciled to a verified MoneyManager transaction");
                }

                // The canonical queue row is stale: no strong ledger transaction
                // represents it anymore. Reopen this exact LoanManager payment.
                if (!queue.confirmNotDuplicate(loanEventId)) {
                    return response("NEEDS_REVIEW", loanEventId,
                            canonical.event.eventId, null,
                            "Stale duplicate link could not be reopened safely");
                }

                TridevFinanceIntegrationCoordinator.Result recovered =
                        new TridevFinanceIntegrationCoordinator(context)
                                .acceptAndProcess(loanEvent);
                if (recovered.outcome == TridevFinanceIntegrationCoordinator.Outcome.POSTED
                        || recovered.outcome
                        == TridevFinanceIntegrationCoordinator.Outcome.RECONCILED) {
                    if (!canonical.event.eventId.equals(loanEventId)) {
                        queue.confirmDuplicate(canonical.event.eventId, loanEventId);
                    }
                    return response(recovered.outcome.name(), loanEventId,
                            loanEventId, recovered.moneyManagerTransactionId,
                            "Stale duplicate link recovered through the LoanManager payment: "
                                    + recovered.reason);
                }

                return response(recovered.outcome.name(), loanEventId,
                        recovered.canonicalEventId, recovered.moneyManagerTransactionId,
                        "Stale duplicate recovery did not finalize: " + recovered.reason);
            }

            if (canonical.event.syncState
                    != TridevIntegrationContract.SyncState.SUPERSEDED) return null;
            nextId = safe(canonical.duplicateOfEventId);
        }
        return null;
    }

    private CallerKind trustedCaller(@NonNull Context context) {
        int uid = Binder.getCallingUid();
        if (TridevCompanionTrust.verifyCaller(
                context, uid, TridevCompanionTrust.FAMILY_HUB_PACKAGE)) {
            return CallerKind.FAMILY_HUB;
        }
        if (TridevCompanionTrust.verifyCaller(
                context, uid, TridevCompanionTrust.LOAN_MANAGER_PACKAGE)) {
            return CallerKind.LOAN_MANAGER;
        }
        return CallerKind.NONE;
    }

    @NonNull
    private TridevIntegrationContract.EventType familyEventType(@NonNull String value) {
        TridevIntegrationContract.EventType type =
                TridevIntegrationContract.EventType.valueOf(value.toUpperCase(Locale.ROOT));
        if (type == TridevIntegrationContract.EventType.GROCERY_PURCHASE
                || type == TridevIntegrationContract.EventType.EXPENSE
                || type == TridevIntegrationContract.EventType.INCOME) return type;
        throw new IllegalArgumentException();
    }

    private void validateFamilyDirection(
            @NonNull TridevIntegrationContract.EventType type,
            @NonNull TridevIntegrationContract.Direction direction) {
        if (type == TridevIntegrationContract.EventType.INCOME) {
            if (direction != TridevIntegrationContract.Direction.CREDIT) {
                throw new IllegalArgumentException();
            }
        } else if (direction != TridevIntegrationContract.Direction.DEBIT) {
            throw new IllegalArgumentException();
        }
    }

    @NonNull
    private TridevIntegrationContract.Scope familyScope(
            @NonNull TridevIntegrationContract.EventType type,
            @NonNull String value) {
        if (type == TridevIntegrationContract.EventType.GROCERY_PURCHASE) {
            return TridevIntegrationContract.Scope.FAMILY;
        }
        if ("PERSONAL".equalsIgnoreCase(value)) {
            return TridevIntegrationContract.Scope.PERSONAL;
        }
        if ("FAMILY".equalsIgnoreCase(value)) {
            return TridevIntegrationContract.Scope.FAMILY;
        }
        throw new IllegalArgumentException();
    }

    @NonNull
    private TridevIntegrationContract.Direction direction(@NonNull String value) {
        TridevIntegrationContract.Direction direction =
                TridevIntegrationContract.Direction.valueOf(value.toUpperCase(Locale.ROOT));
        if (direction == TridevIntegrationContract.Direction.DEBIT
                || direction == TridevIntegrationContract.Direction.CREDIT) return direction;
        throw new IllegalArgumentException();
    }

    @NonNull
    private Bundle response(
            @Nullable String status,
            @Nullable String eventId,
            @Nullable String canonicalEventId,
            @Nullable String transactionId,
            @Nullable String reason) {
        Bundle result = new Bundle();
        result.putString("status", safe(status));
        result.putString("event_id", safe(eventId));
        result.putString("canonical_event_id", safe(canonicalEventId));
        result.putString("transaction_id", safe(transactionId));
        result.putString("reason", limit(safe(reason).replace('\n', ' ').replace('\r', ' '), 240));
        return result;
    }

    @NonNull
    private String structured(@Nullable String value, int max, boolean optional) {
        String clean = safe(value);
        if (!optional && clean.isEmpty()) throw new IllegalArgumentException();
        if (clean.length() > max || clean.indexOf('\n') >= 0 || clean.indexOf('\r') >= 0) {
            throw new IllegalArgumentException();
        }
        return clean;
    }

    @NonNull
    private String metadata(@Nullable String value, int max) {
        return limit(safe(value).replace('\n', ' ').replace('\r', ' ')
                .replaceAll("\\s+", " "), max);
    }

    @NonNull
    private String limit(@NonNull String value, int max) {
        return value.length() <= max ? value : value.substring(0, max).trim();
    }

    @NonNull
    private String safe(@Nullable String value) {
        return value == null ? "" : value.trim();
    }

    @Nullable @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection,
            @Nullable String selection, @Nullable String[] selectionArgs,
            @Nullable String sortOrder) { return null; }
    @Nullable @Override public String getType(@NonNull Uri uri) { return null; }
    @Nullable @Override public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) { return null; }
    @Override public int delete(@NonNull Uri uri, @Nullable String selection,
            @Nullable String[] selectionArgs) { return 0; }
    @Override public int update(@NonNull Uri uri, @Nullable ContentValues values,
            @Nullable String selection, @Nullable String[] selectionArgs) { return 0; }
}
