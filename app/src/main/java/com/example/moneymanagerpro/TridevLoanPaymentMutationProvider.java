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
        if (!METHOD_CANCEL_LOAN.equals(method) || extras == null) {
            return response("REJECTED", "Unsupported loan mutation request", false);
        }

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
    private String structured(@Nullable String value) {
        String safe = value == null ? "" : value.trim();
        if (safe.length() > 40 || safe.indexOf('\n') >= 0 || safe.indexOf('\r') >= 0) {
            return "";
        }
        return safe.replaceAll("[^A-Za-z0-9:_\\-]", "");
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
