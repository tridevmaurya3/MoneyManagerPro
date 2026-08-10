package com.example.moneymanagerpro.widget;

import android.content.Context;

import androidx.annotation.NonNull;

import com.example.moneymanagerpro.database.DatabaseClient;
import com.example.moneymanagerpro.model.AccountBalance;
import com.example.moneymanagerpro.model.CreditCard;
import com.example.moneymanagerpro.model.CreditCardPayment;
import com.example.moneymanagerpro.model.Loan;
import com.example.moneymanagerpro.model.Subscription;
import com.example.moneymanagerpro.model.Transaction;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** One Room-backed snapshot shared by every home-screen widget. */
final class WidgetFinanceSnapshot {

    final double totalBalance;
    final double income;
    final double expense;
    final double cardPayments;
    final double availableCash;
    final double monthlySaving;
    final int accountCount;
    final int activeCardCount;
    final String monthLabel;
    final DueItem nearestDue;
    final int dueCount;

    private WidgetFinanceSnapshot(
            double totalBalance,
            double income,
            double expense,
            double cardPayments,
            int accountCount,
            int activeCardCount,
            @NonNull String monthLabel,
            DueItem nearestDue,
            int dueCount
    ) {
        this.totalBalance = totalBalance;
        this.income = income;
        this.expense = expense;
        this.cardPayments = cardPayments;
        this.availableCash = income - expense - cardPayments;
        this.monthlySaving = income - expense;
        this.accountCount = accountCount;
        this.activeCardCount = activeCardCount;
        this.monthLabel = monthLabel;
        this.nearestDue = nearestDue;
        this.dueCount = dueCount;
    }

    @NonNull
    static WidgetFinanceSnapshot load(@NonNull Context context) {
        Context app = context.getApplicationContext();

        List<AccountBalance> balances = DatabaseClient.getInstance(app)
                .getAppDatabase()
                .accountDao()
                .getAccountBalances();

        double totalBalance = 0d;
        Map<String, Double> balanceByAccount = new HashMap<>();
        if (balances != null) {
            for (AccountBalance balance : balances) {
                if (balance == null) continue;
                totalBalance += balance.currentBalance;
                balanceByAccount.put(normalize(balance.name), balance.currentBalance);
            }
        }

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

        double income = 0d;
        double expense = 0d;
        List<Transaction> transactions = DatabaseClient.getInstance(app)
                .getAppDatabase()
                .transactionDao()
                .getAllTransactions();

        if (transactions != null) {
            for (Transaction transaction : transactions) {
                if (transaction == null || !inRange(transaction.getDate(), start, end)) continue;
                if ("INCOME".equalsIgnoreCase(transaction.getType())) {
                    income += Math.abs(transaction.getAmount());
                } else if ("EXPENSE".equalsIgnoreCase(transaction.getType())) {
                    expense += Math.abs(transaction.getAmount());
                }
            }
        }

        double cardPayments = 0d;
        List<CreditCardPayment> payments = DatabaseClient.getInstance(app)
                .getAppDatabase()
                .creditCardPaymentDao()
                .getAllPayments();
        if (payments != null) {
            for (CreditCardPayment payment : payments) {
                if (payment != null && inRange(payment.getPaymentDate(), start, end)) {
                    cardPayments += Math.abs(payment.getAmount());
                }
            }
        }

        List<CreditCard> cards = DatabaseClient.getInstance(app)
                .getAppDatabase()
                .creditCardDao()
                .getActiveCreditCards();
        List<Loan> loans = DatabaseClient.getInstance(app)
                .getAppDatabase()
                .loanDao()
                .getActiveLoans();
        List<Subscription> subscriptions = DatabaseClient.getInstance(app)
                .getAppDatabase()
                .subscriptionDao()
                .getActiveSubscriptions();

        List<DueItem> dues = new ArrayList<>();
        collectCardDues(cards, balanceByAccount, dues);
        collectLoanDues(loans, dues);
        collectSubscriptionDues(subscriptions, dues);

        DueItem nearest = null;
        for (DueItem due : dues) {
            if (nearest == null || due.dueAt < nearest.dueAt) nearest = due;
        }

        return new WidgetFinanceSnapshot(
                totalBalance,
                income,
                expense,
                cardPayments,
                balances == null ? 0 : balances.size(),
                cards == null ? 0 : cards.size(),
                new SimpleDateFormat("MMMM yyyy", Locale.ENGLISH).format(start.getTime()),
                nearest,
                dues.size()
        );
    }

    private static void collectCardDues(
            List<CreditCard> cards,
            @NonNull Map<String, Double> balanceByAccount,
            @NonNull List<DueItem> out
    ) {
        if (cards == null) return;
        for (CreditCard card : cards) {
            if (card == null || card.getDueDay() <= 0) continue;
            double balance = balanceByAccount.containsKey(normalize(card.getAccountName()))
                    ? balanceByAccount.get(normalize(card.getAccountName())) : 0d;
            double outstanding = Math.max(0d, -balance);
            if (outstanding <= 0.005d) continue;

            Calendar due = Calendar.getInstance();
            due.set(Calendar.HOUR_OF_DAY, 23);
            due.set(Calendar.MINUTE, 59);
            due.set(Calendar.SECOND, 59);
            due.set(Calendar.MILLISECOND, 999);
            due.set(Calendar.DAY_OF_MONTH, Math.min(card.getDueDay(), due.getActualMaximum(Calendar.DAY_OF_MONTH)));

            String suffix = card.getLastFour().trim().isEmpty() ? "" : " •••• " + card.getLastFour().trim();
            out.add(new DueItem(
                    "Credit Card",
                    card.getName().trim() + suffix,
                    outstanding,
                    due.getTimeInMillis()
            ));
        }
    }

    private static void collectLoanDues(List<Loan> loans, @NonNull List<DueItem> out) {
        if (loans == null) return;
        for (Loan loan : loans) {
            if (loan == null || loan.getOutstandingAmount() <= 0.005d) continue;
            Date due = parseDate(loan.getDueDate());
            if (due == null) continue;
            double amount = loan.getEmiAmount() > 0.005d ? loan.getEmiAmount() : loan.getOutstandingAmount();
            String detail = loan.getPersonName().trim();
            if (!loan.getLoanType().trim().isEmpty()) detail += " • " + loan.getLoanType().trim();
            out.add(new DueItem("Loan / EMI", detail, amount, endOfDay(due)));
        }
    }

    private static void collectSubscriptionDues(
            List<Subscription> subscriptions,
            @NonNull List<DueItem> out
    ) {
        if (subscriptions == null) return;
        for (Subscription subscription : subscriptions) {
            if (subscription == null) continue;
            Date due = parseDate(subscription.getNextDueDate());
            if (due == null) continue;
            String name = subscription.getName() == null || subscription.getName().trim().isEmpty()
                    ? "Subscription" : subscription.getName().trim();
            out.add(new DueItem("Subscription", name, Math.abs(subscription.getAmount()), endOfDay(due)));
        }
    }

    private static long endOfDay(@NonNull Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        calendar.set(Calendar.MILLISECOND, 999);
        return calendar.getTimeInMillis();
    }

    static long daysUntil(long timeMillis) {
        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);

        Calendar due = Calendar.getInstance();
        due.setTimeInMillis(timeMillis);
        due.set(Calendar.HOUR_OF_DAY, 0);
        due.set(Calendar.MINUTE, 0);
        due.set(Calendar.SECOND, 0);
        due.set(Calendar.MILLISECOND, 0);

        return Math.round((due.getTimeInMillis() - today.getTimeInMillis()) / 86400000d);
    }

    private static boolean inRange(String value, Calendar start, Calendar end) {
        Date date = parseDate(value);
        return date != null && !date.before(start.getTime()) && !date.after(end.getTime());
    }

    private static Date parseDate(String value) {
        String clean = value == null ? "" : value.trim();
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
    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.US);
    }

    static final class DueItem {
        final String type;
        final String detail;
        final double amount;
        final long dueAt;

        DueItem(String type, String detail, double amount, long dueAt) {
            this.type = type;
            this.detail = detail;
            this.amount = amount;
            this.dueAt = dueAt;
        }
    }
}
