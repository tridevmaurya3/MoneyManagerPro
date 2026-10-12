package com.example.moneymanagerpro;

import android.content.Context;
import java.math.BigDecimal;

/** Preserves full bank reconciliation amounts while projecting personal spending. */
final class LoanPersonalExpenseShare {
    private static final String PREFS = "loan_personal_expense_shares_v1";
    private static final String LABEL = "\nLoan payment breakdown: ";
    private LoanPersonalExpenseShare() { }
    static void validate(long total, long personal) {
        if (total <= 0L || personal < 0L || personal > total)
            throw new IllegalArgumentException("Personal expense exceeds the total loan payment");
    }
    static void save(Context context, String loan, String payment, long total, long personal) {
        validate(total, personal);
        if (!context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong(loan + "/" + payment + "/total", total)
                .putLong(loan + "/" + payment + "/personal", personal).commit())
            throw new IllegalStateException("Personal share could not be saved");
    }
    static long read(Context context, TridevIntegrationContract.Event event) {
        if (!TridevIntegrationContract.APP_LOAN_MANAGER.equals(event.sourceApp)) return event.amountMinor;
        String key = event.references.loanManagerLoanId + "/" + event.references.loanManagerPaymentId;
        android.content.SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (prefs.getLong(key + "/total", -1L) != event.amountMinor) return event.amountMinor;
        long personal = prefs.getLong(key + "/personal", event.amountMinor);
        validate(event.amountMinor, personal);
        return personal;
    }
    static String note(String original, long total, long personal) {
        validate(total, personal);
        int old = original.indexOf(LABEL);
        String base = old < 0 ? original : original.substring(0, old);
        if (total == personal) return base;
        return base + LABEL + "Total ₹" + rupees(total) + " · Yours ₹" + rupees(personal)
                + " · Others ₹" + rupees(total - personal);
    }
    private static String rupees(long minor) {
        return BigDecimal.valueOf(minor, 2).stripTrailingZeros().toPlainString();
    }
}
