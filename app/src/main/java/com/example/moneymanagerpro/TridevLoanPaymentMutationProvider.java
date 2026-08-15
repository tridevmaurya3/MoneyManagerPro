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

/** Trusted source-mutation endpoint used only by LoanManagerPro. */
public final class TridevLoanPaymentMutationProvider extends ContentProvider {

    public static final String AUTHORITY =
            "com.example.moneymanagerpro.tridev.loanmutation";
    public static final String METHOD_CANCEL_LOAN = "cancel_loan_payment_v1";
    public static final String METHOD_UPDATE_LOAN = "update_loan_payment_v1";

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
        if (context == null) return response("FAILED", "MoneyManager unavailable", false);
        if (!TridevCompanionTrust.verifyCaller(
                context,
                Binder.getCallingUid(),
                TridevCompanionTrust.LOAN_MANAGER_PACKAGE)) {
            return response("REJECTED", "LoanManager caller is not trusted", false);
        }
        if (extras == null) {
            return response("REJECTED", "Loan mutation payload is missing", false);
        }

        if (METHOD_CANCEL_LOAN.equals(method)) {
            return cancelLoan(context, extras);
        }
        if (METHOD_UPDATE_LOAN.equals(method)) {
            return updateLoan(context, extras);
        }
        return response("REJECTED", "Unsupported loan mutation request", false);
    }

    @NonNull
    private Bundle cancelLoan(@NonNull Context context, @NonNull Bundle extras) {
        String loanId = structured(extras.getString("loan_id"));
        String paymentId = structured(extras.getString("payment_id"));
        if (loanId.isEmpty() || paymentId.isEmpty()) {
            return response("REJECTED", "Loan payment identity is missing", false);
        }

        try {
            TridevLoanPaymentCancellationManager.Result result =
                    new TridevLoanPaymentCancellationManager(context)
                            .cancel(loanId, paymentId);
            String status = result.handled
                    ? (result.ledgerRemoved ? "CANCELLED" : "PRESERVED")
                    : "FAILED";
            return response(status, result.reason, result.ledgerRemoved);
        } catch (RuntimeException failure) {
            return response("FAILED", "Loan payment cancellation failed safely", false);
        }
    }

    @NonNull
    private Bundle updateLoan(@NonNull Context context, @NonNull Bundle extras) {
        String loanId = structured(extras.getString("loan_id"));
        String paymentId = structured(extras.getString("payment_id"));
        String sourceRecordId = structuredSource(extras.getString("source_record_id"));
        String paymentType = structured(extras.getString("payment_type")).toUpperCase();
        long amountMinor = extras.getLong("amount_minor", 0L);
        long occurredAt = extras.getLong("occurred_at", 0L);
        String categoryHint = metadata(extras.getString("category_hint"), 160);

        if (loanId.isEmpty() || paymentId.isEmpty() || sourceRecordId.isEmpty()
                || amountMinor <= 0L || occurredAt <= 0L
                || (!"EMI".equals(paymentType) && !"PREPAYMENT".equals(paymentType))) {
            return editResponse(
                    "REJECTED",
                    "Edited loan payment payload is invalid",
                    false,
                    false,
                    "",
                    null);
        }

        try {
            TridevLoanPaymentUpdateManager.Result result =
                    new TridevLoanPaymentUpdateManager(context).update(
                            loanId,
                            paymentId,
                            sourceRecordId,
                            paymentType,
                            amountMinor,
                            occurredAt,
                            categoryHint);
            return editResponse(
                    result.status,
                    result.reason,
                    result.finalized,
                    result.familyVisible,
                    result.canonicalEventId,
                    result.transactionId);
        } catch (RuntimeException failure) {
            return editResponse(
                    "FAILED",
                    "Loan payment edit failed safely",
                    false,
                    false,
                    "",
                    null);
        }
    }

    @NonNull
    private Bundle response(
            @NonNull String status,
            @NonNull String reason,
            boolean ledgerRemoved) {
        Bundle out = new Bundle();
        out.putString("status", status);
        out.putString("reason", reason);
        out.putBoolean("ledger_removed", ledgerRemoved);
        return out;
    }

    @NonNull
    private Bundle editResponse(
            @NonNull String status,
            @NonNull String reason,
            boolean finalized,
            boolean familyVisible,
            @NonNull String canonicalEventId,
            @Nullable String transactionId) {
        Bundle out = new Bundle();
        out.putString("status", status);
        out.putString("reason", reason);
        out.putBoolean("finalized", finalized);
        out.putBoolean("family_visible", familyVisible);
        out.putString("canonical_event_id", canonicalEventId);
        if (transactionId != null && !transactionId.trim().isEmpty()) {
            out.putString("money_transaction_id", transactionId.trim());
        }
        return out;
    }

    @NonNull
    private String structured(@Nullable String value) {
        String safe = value == null ? "" : value.trim();
        if (safe.length() > 40 || safe.indexOf('\n') >= 0 || safe.indexOf('\r') >= 0) {
            return "";
        }
        return safe.replaceAll("[^A-Za-z0-9:_\\-]", "");
    }

    @NonNull
    private String structuredSource(@Nullable String value) {
        String safe = value == null ? "" : value.trim();
        if (safe.length() > 160 || safe.indexOf('\n') >= 0 || safe.indexOf('\r') >= 0) {
            return "";
        }
        return safe.replaceAll("[^A-Za-z0-9:_\\-]", "_");
    }

    @NonNull
    private String metadata(@Nullable String value, int max) {
        String safe = value == null ? "" : value.trim();
        safe = safe.replace('\n', ' ').replace('\r', ' ').replaceAll("\\s+", " ");
        return safe.length() <= max ? safe : safe.substring(0, max).trim();
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
