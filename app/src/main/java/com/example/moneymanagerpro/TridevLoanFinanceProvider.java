package com.example.moneymanagerpro;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Process;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Locale;

/**
 * STEP 10 - signature-verified same-device endpoint dedicated to LoanManagerPro.
 *
 * Only actual loan bank payments are accepted. Contribution-plan rows, future
 * schedules, notes and participant details are never part of this endpoint.
 */
public final class TridevLoanFinanceProvider extends ContentProvider {

    public static final String AUTHORITY =
            "com.example.moneymanagerpro.tridev.loanfinance";
    public static final String METHOD_ACCEPT_V1 = "accept_loan_payment_v1";
    public static final String METHOD_ACCOUNT_CATALOG_V1 = "get_account_catalog_v1";

    private static final String TRUSTED_PACKAGE = "com.tridev.loanmanagerpro";

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
        if (context == null) return response("FAILED", "", "", "", "MoneyManager unavailable");
        if (!trustedCaller(context)) {
            return response("REJECTED", "", "", "", "LoanManager caller is not trusted");
        }

        if (METHOD_ACCOUNT_CATALOG_V1.equals(method)) {
            return accountCatalog(context);
        }
        if (!METHOD_ACCEPT_V1.equals(method) || extras == null) {
            return response("REJECTED", "", "", "", "Unsupported loan integration request");
        }

        try {
            String eventId = structured(extras.getString("event_id"), 120, false);
            String sourceRecordId = structured(extras.getString("source_record_id"), 160, false);
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
                throw new IllegalArgumentException("Unsupported loan payment type");
            }
            if (amountMinor <= 0L || occurredAt <= 0L) {
                throw new IllegalArgumentException("Loan payment amount/date is invalid");
            }

            TridevIntegrationContract.Scope scope;
            if ("FAMILY".equals(scopeValue)) {
                scope = TridevIntegrationContract.Scope.FAMILY;
            } else if ("PERSONAL".equals(scopeValue)) {
                scope = TridevIntegrationContract.Scope.PERSONAL;
            } else {
                throw new IllegalArgumentException("Loan visibility scope is required");
            }

            TridevIntegrationContract.References refs =
                    new TridevIntegrationContract.References(
                            "", "", "", "", "", loanId, paymentId);

            TridevIntegrationContract.Event event =
                    new TridevIntegrationContract.Event(
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
                            refs);

            TridevFinanceIntegrationCoordinator.Result result =
                    new TridevFinanceIntegrationCoordinator(context)
                            .acceptAndProcess(event);

            // A LoanManager payment may have been superseded by the same bank
            // debit already reported by SmartSMSPro. Once that canonical event
            // is finalized in MoneyManager, report RECONCILED here as well. This
            // allows a FAMILY loan to project exactly one finalized payment to
            // Family Hub without ever creating a second MoneyManager expense.
            if (result.outcome == TridevFinanceIntegrationCoordinator.Outcome.DUPLICATE) {
                Bundle reconciled = finalizedDuplicateResponse(context, result.eventId);
                if (reconciled != null) return reconciled;
            }

            return response(
                    result.outcome.name(),
                    result.eventId,
                    result.canonicalEventId,
                    result.moneyManagerTransactionId,
                    result.reason);
        } catch (RuntimeException invalid) {
            return response("REJECTED", "", "", "", "Loan payment failed validation");
        }
    }

    @Nullable
    private Bundle finalizedDuplicateResponse(
            @NonNull Context context,
            @NonNull String loanEventId) {
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
            if (canonical.event.syncState == TridevIntegrationContract.SyncState.SYNCED
                    && !transactionId.isEmpty()) {
                return response(
                        "RECONCILED",
                        loanEventId,
                        canonical.event.eventId,
                        transactionId,
                        "Loan payment reconciled to finalized cross-app transaction");
            }

            if (canonical.event.syncState != TridevIntegrationContract.SyncState.SUPERSEDED) {
                return null;
            }
            nextId = safe(canonical.duplicateOfEventId);
        }
        return null;
    }

    private boolean trustedCaller(@NonNull Context context) {
        int callingUid = Binder.getCallingUid();
        PackageManager pm = context.getPackageManager();
        String[] packages = pm.getPackagesForUid(callingUid);
        boolean packageMatch = false;
        if (packages != null) {
            for (String packageName : packages) {
                if (TRUSTED_PACKAGE.equals(packageName)) {
                    packageMatch = true;
                    break;
                }
            }
        }
        return packageMatch
                && pm.checkSignatures(callingUid, Process.myUid())
                == PackageManager.SIGNATURE_MATCH;
    }

    @NonNull
    private Bundle accountCatalog(@NonNull Context context) {
        try {
            TridevMoneyMappingEngine.Catalog catalog =
                    new TridevMoneyMappingEngine(context).readCatalog();
            ArrayList<String> refs = new ArrayList<>();
            ArrayList<String> labels = new ArrayList<>();
            for (TridevMoneyMappingEngine.CatalogItem item : catalog.accounts) {
                addCatalogItem(item, refs, labels);
            }
            for (TridevMoneyMappingEngine.CatalogItem item : catalog.creditCards) {
                addCatalogItem(item, refs, labels);
            }
            Bundle result = response("OK", "", "", "",
                    refs.isEmpty() ? "No active account/card" : "Active MoneyManager accounts");
            result.putStringArrayList("account_refs", refs);
            result.putStringArrayList("account_labels", labels);
            return result;
        } catch (RuntimeException failure) {
            return response("FAILED", "", "", "", "MoneyManager account catalog unavailable");
        }
    }

    private void addCatalogItem(
            @Nullable TridevMoneyMappingEngine.CatalogItem item,
            @NonNull ArrayList<String> refs,
            @NonNull ArrayList<String> labels) {
        if (item == null || item.unavailableForNewPosting) return;
        refs.add(item.canonicalRef);
        String label = safe(item.displayName);
        if (item.type != null && !item.type.trim().isEmpty()) {
            label += " • " + item.type.trim();
        }
        labels.add(label.length() <= 120 ? label : label.substring(0, 120));
    }

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
        result.putString("reason", safeReason(reason));
        return result;
    }

    private String structured(@Nullable String value, int max, boolean optional) {
        String safe = safe(value);
        if (!optional && safe.isEmpty()) throw new IllegalArgumentException();
        if (safe.length() > max || safe.indexOf('\n') >= 0 || safe.indexOf('\r') >= 0) {
            throw new IllegalArgumentException();
        }
        return safe;
    }

    private String metadata(@Nullable String value, int max) {
        String safe = safe(value).replace('\n', ' ').replace('\r', ' ')
                .replaceAll("\\s+", " ");
        return safe.length() <= max ? safe : safe.substring(0, max).trim();
    }

    private String safe(@Nullable String value) {
        return value == null ? "" : value.trim();
    }

    private String safeReason(@Nullable String value) {
        String safe = safe(value).replace('\n', ' ').replace('\r', ' ');
        return safe.length() <= 240 ? safe : safe.substring(0, 240).trim();
    }

    @Nullable @Override public Cursor query(@NonNull Uri uri, @Nullable String[] projection,
            @Nullable String selection, @Nullable String[] selectionArgs,
            @Nullable String sortOrder) { return null; }
    @Nullable @Override public String getType(@NonNull Uri uri) { return null; }
    @Nullable @Override public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) { return null; }
    @Override public int delete(@NonNull Uri uri, @Nullable String selection,
            @Nullable String[] selectionArgs) { return 0; }
    @Override public int update(@NonNull Uri uri, @Nullable ContentValues values,
            @Nullable String selection, @Nullable String[] selectionArgs) { return 0; }
}
