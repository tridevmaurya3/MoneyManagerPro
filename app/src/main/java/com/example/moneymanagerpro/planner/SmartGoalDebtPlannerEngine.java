package com.example.moneymanagerpro.planner;

import androidx.annotation.NonNull;

import com.example.moneymanagerpro.model.Goal;
import com.example.moneymanagerpro.model.Loan;
import com.example.moneymanagerpro.model.Transaction;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Local-only planner for debts and savings goals. */
public final class SmartGoalDebtPlannerEngine {

    private static final String[] DATE_PATTERNS = {
            "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy-MM-dd",
            "dd-MM-yyyy HH:mm", "dd-MM-yyyy", "dd/MM/yyyy HH:mm", "dd/MM/yyyy"
    };

    @NonNull
    public Plan buildPlan(
            List<Loan> loans,
            List<Goal> goals,
            List<Transaction> transactions,
            double extraMonthlyPayment,
            Strategy strategy
    ) {
        double averageIncome = 0d;
        double averageExpense = 0d;
        Calendar start = Calendar.getInstance();
        start.add(Calendar.MONTH, -3);
        start.set(Calendar.DAY_OF_MONTH, 1);
        start.set(Calendar.HOUR_OF_DAY, 0);
        start.set(Calendar.MINUTE, 0);
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);

        if (transactions != null) {
            for (Transaction transaction : transactions) {
                if (transaction == null) continue;
                Date date = parseDate(transaction.getDate());
                if (date == null || date.before(start.getTime())) continue;
                double amount = Math.abs(transaction.getAmount());
                if (amount <= 0d || Double.isNaN(amount) || Double.isInfinite(amount)) continue;
                if ("INCOME".equalsIgnoreCase(transaction.getType())) averageIncome += amount;
                if ("EXPENSE".equalsIgnoreCase(transaction.getType())) averageExpense += amount;
            }
        }

        averageIncome /= 3d;
        averageExpense /= 3d;
        double observedSurplus = Math.max(0d, averageIncome - averageExpense);
        double safeExtra = Math.max(0d, extraMonthlyPayment);
        double suggestedExtra = safeExtra > 0d
                ? safeExtra
                : Math.max(0d, observedSurplus * 0.50d);

        List<DebtItem> debts = new ArrayList<>();
        if (loans != null) {
            for (Loan loan : loans) {
                if (loan == null || !loan.isActive()) continue;
                if (!"Loan Taken".equalsIgnoreCase(loan.getLoanType())) continue;
                double balance = Math.max(0d, loan.getOutstandingAmount());
                if (balance <= 0d) continue;
                double minimum = Math.max(loan.getEmiAmount(), Math.max(500d, balance * 0.02d));
                debts.add(new DebtItem(
                        safe(loan.getPersonName(), "Loan"),
                        balance,
                        Math.max(0d, loan.getInterestRate()),
                        minimum
                ));
            }
        }

        Comparator<DebtItem> comparator = strategy == Strategy.AVALANCHE
                ? Comparator.comparingDouble(DebtItem::getAnnualRate).reversed()
                : Comparator.comparingDouble(DebtItem::getBalance);
        Collections.sort(debts, comparator);

        Simulation base = simulate(debts, 0d);
        Simulation accelerated = simulate(debts, suggestedExtra);

        List<GoalItem> goalItems = new ArrayList<>();
        double goalPool = Math.max(0d, observedSurplus - suggestedExtra);
        if (goals != null) {
            for (Goal goal : goals) {
                if (goal == null) continue;
                double remaining = Math.max(0d, goal.getTargetAmount() - goal.getSavedAmount());
                if (remaining <= 0d) continue;
                int monthsToTarget = monthsUntil(goal.getTargetDate());
                double required = monthsToTarget > 0 ? remaining / monthsToTarget : remaining;
                double recommended = goalPool > 0d
                        ? Math.min(required, goalPool / Math.max(1, countOpenGoals(goals)))
                        : required;
                int estimatedMonths = recommended > 0d
                        ? (int) Math.ceil(remaining / recommended)
                        : Integer.MAX_VALUE;
                goalItems.add(new GoalItem(
                        safe(goal.getName(), "Savings Goal"),
                        goal.getTargetAmount(),
                        goal.getSavedAmount(),
                        remaining,
                        goal.getTargetDate(),
                        required,
                        recommended,
                        estimatedMonths,
                        required <= recommended + 0.01d
                ));
            }
        }

        Collections.sort(goalItems, Comparator.comparingDouble(GoalItem::getRemaining));

        return new Plan(
                strategy,
                averageIncome,
                averageExpense,
                observedSurplus,
                suggestedExtra,
                base,
                accelerated,
                debts,
                goalItems
        );
    }

    private int countOpenGoals(List<Goal> goals) {
        int count = 0;
        if (goals != null) {
            for (Goal goal : goals) {
                if (goal != null && goal.getTargetAmount() > goal.getSavedAmount()) count++;
            }
        }
        return count;
    }

    private Simulation simulate(List<DebtItem> source, double extra) {
        List<MutableDebt> debts = new ArrayList<>();
        for (DebtItem item : source) debts.add(new MutableDebt(item));
        double interest = 0d;
        int month = 0;
        int guard = 600;

        while (hasBalance(debts) && month < guard) {
            month++;
            for (MutableDebt debt : debts) {
                if (debt.balance <= 0d) continue;
                double monthlyInterest = debt.balance * debt.rate / 1200d;
                debt.balance += monthlyInterest;
                interest += monthlyInterest;
            }

            double rollover = extra;
            for (MutableDebt debt : debts) {
                if (debt.balance <= 0d) continue;
                double payment = Math.min(debt.balance, debt.minimum);
                debt.balance -= payment;
                if (debt.balance <= 0.01d) {
                    rollover += Math.max(0d, debt.minimum - payment) + debt.minimum;
                    debt.balance = 0d;
                }
            }

            for (MutableDebt debt : debts) {
                if (debt.balance <= 0d) continue;
                double payment = Math.min(debt.balance, rollover);
                debt.balance -= payment;
                rollover -= payment;
                if (debt.balance <= 0.01d) debt.balance = 0d;
                if (rollover <= 0d) break;
            }
        }

        Calendar payoff = Calendar.getInstance();
        payoff.add(Calendar.MONTH, month);
        String payoffDate = new SimpleDateFormat("MMM yyyy", Locale.ENGLISH).format(payoff.getTime());
        return new Simulation(month, interest, payoffDate);
    }

    private boolean hasBalance(List<MutableDebt> debts) {
        for (MutableDebt debt : debts) if (debt.balance > 0.01d) return true;
        return false;
    }

    private int monthsUntil(String dateValue) {
        Date target = parseDate(dateValue);
        if (target == null) return 0;
        Calendar now = Calendar.getInstance();
        Calendar targetCal = Calendar.getInstance();
        targetCal.setTime(target);
        int months = (targetCal.get(Calendar.YEAR) - now.get(Calendar.YEAR)) * 12
                + targetCal.get(Calendar.MONTH) - now.get(Calendar.MONTH);
        if (targetCal.get(Calendar.DAY_OF_MONTH) > now.get(Calendar.DAY_OF_MONTH)) months++;
        return Math.max(0, months);
    }

    private Date parseDate(String value) {
        String clean = value == null ? "" : value.trim();
        if (clean.isEmpty()) return null;
        for (String pattern : DATE_PATTERNS) {
            SimpleDateFormat formatter = new SimpleDateFormat(pattern, Locale.US);
            formatter.setLenient(false);
            ParsePosition position = new ParsePosition(0);
            Date parsed = formatter.parse(clean, position);
            if (parsed != null && position.getIndex() == clean.length()) return parsed;
        }
        return null;
    }

    private String safe(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    public enum Strategy { SNOWBALL, AVALANCHE }

    public static final class Plan {
        private final Strategy strategy;
        private final double averageIncome;
        private final double averageExpense;
        private final double observedSurplus;
        private final double suggestedExtraPayment;
        private final Simulation normalPlan;
        private final Simulation acceleratedPlan;
        private final List<DebtItem> debts;
        private final List<GoalItem> goals;

        private Plan(Strategy strategy, double averageIncome, double averageExpense,
                     double observedSurplus, double suggestedExtraPayment,
                     Simulation normalPlan, Simulation acceleratedPlan,
                     List<DebtItem> debts, List<GoalItem> goals) {
            this.strategy = strategy;
            this.averageIncome = averageIncome;
            this.averageExpense = averageExpense;
            this.observedSurplus = observedSurplus;
            this.suggestedExtraPayment = suggestedExtraPayment;
            this.normalPlan = normalPlan;
            this.acceleratedPlan = acceleratedPlan;
            this.debts = Collections.unmodifiableList(new ArrayList<>(debts));
            this.goals = Collections.unmodifiableList(new ArrayList<>(goals));
        }
        public Strategy getStrategy() { return strategy; }
        public double getAverageIncome() { return averageIncome; }
        public double getAverageExpense() { return averageExpense; }
        public double getObservedSurplus() { return observedSurplus; }
        public double getSuggestedExtraPayment() { return suggestedExtraPayment; }
        public Simulation getNormalPlan() { return normalPlan; }
        public Simulation getAcceleratedPlan() { return acceleratedPlan; }
        @NonNull public List<DebtItem> getDebts() { return debts; }
        @NonNull public List<GoalItem> getGoals() { return goals; }
    }

    public static final class Simulation {
        private final int months;
        private final double interest;
        private final String payoffDate;
        private Simulation(int months, double interest, String payoffDate) {
            this.months = months; this.interest = interest; this.payoffDate = payoffDate;
        }
        public int getMonths() { return months; }
        public double getInterest() { return interest; }
        @NonNull public String getPayoffDate() { return payoffDate; }
    }

    public static final class DebtItem {
        private final String name; private final double balance; private final double annualRate; private final double minimumPayment;
        private DebtItem(String name, double balance, double annualRate, double minimumPayment) {
            this.name = name; this.balance = balance; this.annualRate = annualRate; this.minimumPayment = minimumPayment;
        }
        @NonNull public String getName() { return name; }
        public double getBalance() { return balance; }
        public double getAnnualRate() { return annualRate; }
        public double getMinimumPayment() { return minimumPayment; }
    }

    public static final class GoalItem {
        private final String name; private final double target; private final double saved; private final double remaining;
        private final String targetDate; private final double requiredMonthly; private final double recommendedMonthly;
        private final int estimatedMonths; private final boolean onTrack;
        private GoalItem(String name, double target, double saved, double remaining, String targetDate,
                         double requiredMonthly, double recommendedMonthly, int estimatedMonths, boolean onTrack) {
            this.name = name; this.target = target; this.saved = saved; this.remaining = remaining;
            this.targetDate = targetDate; this.requiredMonthly = requiredMonthly;
            this.recommendedMonthly = recommendedMonthly; this.estimatedMonths = estimatedMonths; this.onTrack = onTrack;
        }
        @NonNull public String getName() { return name; }
        public double getTarget() { return target; }
        public double getSaved() { return saved; }
        public double getRemaining() { return remaining; }
        @NonNull public String getTargetDate() { return targetDate; }
        public double getRequiredMonthly() { return requiredMonthly; }
        public double getRecommendedMonthly() { return recommendedMonthly; }
        public int getEstimatedMonths() { return estimatedMonths; }
        public boolean isOnTrack() { return onTrack; }
    }

    private static final class MutableDebt {
        double balance; final double rate; final double minimum;
        MutableDebt(DebtItem item) { balance = item.balance; rate = item.annualRate; minimum = item.minimumPayment; }
    }
}
