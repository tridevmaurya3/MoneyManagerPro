package com.example.moneymanagerpro;

import android.content.Context;

import androidx.annotation.NonNull;

import com.example.moneymanagerpro.database.DatabaseClient;
import com.example.moneymanagerpro.model.AccountBalance;
import com.example.moneymanagerpro.model.CreditCard;
import com.example.moneymanagerpro.model.Transaction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Read-only canonical finance summary exposed to trusted companion apps.
 *
 * MoneyManagerPro remains the accounting owner. This snapshot never mutates
 * the ledger and never exposes individual transaction rows, notes or balances
 * by account. It intentionally mirrors the monthly Income/Expense semantics
 * already used by MoneyManager widgets: Remaining = Income - Expense.
 */
public final class TridevFinanceMasterSummary {

    public static final class Snapshot {
        public final long incomeMinor;
        public final long expenseMinor;
        public final long remainingMinor;
        public final long totalAccountBalanceMinor;
        public final int transactionCount;
        public final int accountCount;
        public final int activeCardCount;
        @NonNull public final String periodStart;
        @NonNull public final String periodEnd;
        @NonNull public final String periodLabel;
        public final long generatedAt;

        private Snapshot(
                long incomeMinor,
                long expenseMinor,
                long remainingMinor,
                long totalAccountBalanceMinor,
                int transactionCount,
                int accountCount,
                int activeCardCount,
                @NonNull String periodStart,
                @NonNull String periodEnd,
                @NonNull String periodLabel,
                long generatedAt) {
            this.incomeMinor = incomeMinor;
            this.expenseMinor = expenseMinor;
            this.remainingMinor = remainingMinor;
            this.totalAccountBalanceMinor = totalAccountBalanceMinor;
            this.transactionCount = transactionCount;
            this.accountCount = accountCount;
            this.activeCardCount = activeCardCount;
            this.periodStart = periodStart;
            this.periodEnd = periodEnd;
            this.periodLabel = periodLabel;
            this.generatedAt = generatedAt;
        }
    }

    private TridevFinanceMasterSummary() { }

    @NonNull
    public static Snapshot loadCurrentMonth(@NonNull Context context) {
        Context app = context.getApplicationContext();

        Calendar start = Calendar.getInstance();
        start.set(Calendar.DAY_OF_MONTH, 1);
        start.set(Calendar.HOUR_OF_DAY, 0);
        start.set(Calendar.MINUTE, 0);
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);

        Calendar end = (Calendar) start.clone();
        end.set(Calendar.DAY_OF_MONTH, end.getActualMaximum(Calendar.DAY_OF_MONTH));
        end.set(Calendar.HOUR_OF_DAY, 23);
        end.set(Calendar.MINUTE, 59);
        end.set(Calendar.SECOND, 59);
        end.set(Calendar.MILLISECOND, 999);

        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expense = BigDecimal.ZERO;
        int transactionCount = 0;

        List<Transaction> transactions = DatabaseClient.getInstance(app)
                .getAppDatabase()
                .transactionDao()
                .getAllTransactions();
        if (transactions != null) {
            for (Transaction transaction : transactions) {
                if (transaction == null || !inRange(transaction.getDate(), start, end)) {
                    continue;
                }
                String type = safe(transaction.getType()).toUpperCase(Locale.ROOT);
                BigDecimal amount = BigDecimal.valueOf(Math.abs(transaction.getAmount()));
                if ("INCOME".equals(type)) {
                    income = income.add(amount);
                    transactionCount++;
                } else if ("EXPENSE".equals(type)) {
                    expense = expense.add(amount);
                    transactionCount++;
                }
            }
        }

        BigDecimal totalAccountBalance = BigDecimal.ZERO;
        List<AccountBalance> balances = DatabaseClient.getInstance(app)
                .getAppDatabase()
                .accountDao()
                .getAccountBalances();
        if (balances != null) {
            for (AccountBalance balance : balances) {
                if (balance != null) {
                    totalAccountBalance = totalAccountBalance.add(
                            BigDecimal.valueOf(balance.currentBalance));
                }
            }
        }

        List<CreditCard> cards = DatabaseClient.getInstance(app)
                .getAppDatabase()
                .creditCardDao()
                .getActiveCreditCards();

        long incomeMinor = toMinor(income);
        long expenseMinor = toMinor(expense);
        long remainingMinor = subtractSafe(incomeMinor, expenseMinor);

        SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        SimpleDateFormat label = new SimpleDateFormat("MMMM yyyy", Locale.ENGLISH);
        return new Snapshot(
                incomeMinor,
                expenseMinor,
                remainingMinor,
                toMinor(totalAccountBalance),
                transactionCount,
                balances == null ? 0 : balances.size(),
                cards == null ? 0 : cards.size(),
                iso.format(start.getTime()),
                iso.format(end.getTime()),
                label.format(start.getTime()),
                System.currentTimeMillis());
    }

    private static long subtractSafe(long left, long right) {
        try {
            return Math.subtractExact(left, right);
        } catch (ArithmeticException overflow) {
            return left >= 0L ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
    }

    private static long toMinor(@NonNull BigDecimal amount) {
        try {
            return amount.movePointRight(2)
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValueExact();
        } catch (ArithmeticException overflow) {
            return amount.signum() >= 0 ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
    }

    private static boolean inRange(
            String value,
            @NonNull Calendar start,
            @NonNull Calendar end) {
        Date date = parseDate(value);
        return date != null && !date.before(start.getTime()) && !date.after(end.getTime());
    }

    private static Date parseDate(String value) {
        String clean = safe(value);
        if (clean.isEmpty()) return null;
        String[] patterns = {
                "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy-MM-dd",
                "dd-MM-yyyy HH:mm:ss", "dd-MM-yyyy HH:mm", "dd-MM-yyyy",
                "dd/MM/yyyy HH:mm:ss", "dd/MM/yyyy HH:mm", "dd/MM/yyyy",
                "dd MMM yyyy", "dd MMMM yyyy"
        };
        for (String pattern : patterns) {
            SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
            format.setLenient(false);
            ParsePosition position = new ParsePosition(0);
            Date date = format.parse(clean, position);
            if (date != null && position.getIndex() == clean.length()) return date;
        }
        return null;
    }

    @NonNull
    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
