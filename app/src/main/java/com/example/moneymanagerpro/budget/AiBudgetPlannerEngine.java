package com.example.moneymanagerpro.budget;

import androidx.annotation.NonNull;

import com.example.moneymanagerpro.model.Transaction;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Private, offline budget-planning engine.
 *
 * The engine reviews recent income and expense history and recommends monthly
 * category limits. No transaction data leaves the device.
 */
public final class AiBudgetPlannerEngine {

    private static final double MINIMUM_CATEGORY_BUDGET = 100.0d;
    private static final double DEFAULT_SAVING_TARGET_RATE = 0.20d;

    private static final String[] SUPPORTED_DATE_PATTERNS = {
            "yyyy-MM-dd HH:mm",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd",
            "dd-MM-yyyy HH:mm",
            "dd-MM-yyyy",
            "dd/MM/yyyy HH:mm",
            "dd/MM/yyyy"
    };

    @NonNull
    public Plan buildPlan(
            List<Transaction> transactions
    ) {
        Calendar now = Calendar.getInstance();
        Calendar analysisStart = (Calendar) now.clone();
        analysisStart.add(Calendar.MONTH, -2);
        analysisStart.set(Calendar.DAY_OF_MONTH, 1);
        analysisStart.set(Calendar.HOUR_OF_DAY, 0);
        analysisStart.set(Calendar.MINUTE, 0);
        analysisStart.set(Calendar.SECOND, 0);
        analysisStart.set(Calendar.MILLISECOND, 0);

        Map<String, Double> categoryTotals = new LinkedHashMap<>();
        double totalIncome = 0.0d;
        double totalExpense = 0.0d;
        final int analysedMonths = 3;
        int validTransactions = 0;

        if (transactions != null) {
            for (Transaction transaction : transactions) {
                if (transaction == null) {
                    continue;
                }

                Date date = parseDate(transaction.getDate());
                if (date == null || date.before(analysisStart.getTime())) {
                    continue;
                }

                double amount = Math.abs(transaction.getAmount());
                if (Double.isNaN(amount)
                        || Double.isInfinite(amount)
                        || amount <= 0.0d) {
                    continue;
                }

                String type = safe(transaction.getType());
                if ("INCOME".equalsIgnoreCase(type)) {
                    totalIncome += amount;
                    validTransactions++;
                    continue;
                }

                if (!"EXPENSE".equalsIgnoreCase(type)) {
                    continue;
                }

                String category = safe(transaction.getCategory());
                if (category.isEmpty()) {
                    category = "Other Expense";
                }

                String matchingKey = findMatchingKey(categoryTotals, category);
                categoryTotals.put(
                        matchingKey == null ? category : matchingKey,
                        categoryTotals.getOrDefault(
                                matchingKey == null ? category : matchingKey,
                                0.0d
                        ) + amount
                );

                totalExpense += amount;
                validTransactions++;
            }
        }

        double averageMonthlyIncome = totalIncome / analysedMonths;
        double averageMonthlyExpense = totalExpense / analysedMonths;
        double targetSaving = averageMonthlyIncome > 0.0d
                ? averageMonthlyIncome * DEFAULT_SAVING_TARGET_RATE
                : 0.0d;

        double availableExpensePool = averageMonthlyIncome > 0.0d
                ? Math.max(0.0d, averageMonthlyIncome - targetSaving)
                : averageMonthlyExpense;

        double scale = 1.0d;
        if (averageMonthlyExpense > 0.0d
                && availableExpensePool > 0.0d
                && averageMonthlyExpense > availableExpensePool) {
            scale = availableExpensePool / averageMonthlyExpense;
        }

        List<Suggestion> suggestions = new ArrayList<>();
        for (Map.Entry<String, Double> entry : categoryTotals.entrySet()) {
            double average = entry.getValue() / analysedMonths;
            double recommended = roundToNearestHundred(
                    Math.max(
                            MINIMUM_CATEGORY_BUDGET,
                            average * scale * 1.05d
                    )
            );

            int confidence = calculateConfidence(
                    validTransactions,
                    averageMonthlyIncome,
                    averageMonthlyExpense
            );

            suggestions.add(
                    new Suggestion(
                            entry.getKey(),
                            average,
                            recommended,
                            confidence,
                            buildReason(
                                    average,
                                    recommended,
                                    scale,
                                    averageMonthlyIncome
                            )
                    )
            );
        }

        Collections.sort(
                suggestions,
                Comparator.comparingDouble(
                        Suggestion::getRecommendedLimit
                ).reversed()
        );

        return new Plan(
                averageMonthlyIncome,
                averageMonthlyExpense,
                targetSaving,
                availableExpensePool,
                validTransactions,
                suggestions
        );
    }

    private int calculateConfidence(
            int transactionCount,
            double income,
            double expense
    ) {
        int confidence = 40;

        if (transactionCount >= 10) {
            confidence += 15;
        }

        if (transactionCount >= 30) {
            confidence += 15;
        }

        if (income > 0.0d) {
            confidence += 15;
        }

        if (expense > 0.0d) {
            confidence += 10;
        }

        return Math.min(95, confidence);
    }

    @NonNull
    private String buildReason(
            double average,
            double recommended,
            double scale,
            double income
    ) {
        if (income <= 0.0d) {
            return "Based on the average expense recorded during the last three months.";
        }

        if (scale < 0.999d) {
            return "Adjusted below the recent average so that the plan keeps about 20% of income available for saving.";
        }

        if (recommended > average) {
            return "Uses the recent monthly average with a small safety buffer for normal variation.";
        }

        return "Based on the recent three-month category trend.";
    }

    private double roundToNearestHundred(
            double amount
    ) {
        return Math.max(
                MINIMUM_CATEGORY_BUDGET,
                Math.round(amount / 100.0d) * 100.0d
        );
    }

    private String findMatchingKey(
            Map<String, Double> values,
            String requested
    ) {
        for (String existing : values.keySet()) {
            if (existing.equalsIgnoreCase(requested)) {
                return existing;
            }
        }

        return null;
    }

    private Date parseDate(
            String value
    ) {
        String clean = safe(value);
        if (clean.isEmpty()) {
            return null;
        }

        for (String pattern : SUPPORTED_DATE_PATTERNS) {
            SimpleDateFormat formatter = new SimpleDateFormat(
                    pattern,
                    Locale.US
            );
            formatter.setLenient(false);

            ParsePosition position = new ParsePosition(0);
            Date parsed = formatter.parse(clean, position);
            if (parsed != null && position.getIndex() == clean.length()) {
                return parsed;
            }
        }

        return null;
    }

    @NonNull
    private String safe(
            String value
    ) {
        return value == null ? "" : value.trim();
    }

    public static final class Plan {

        private final double averageMonthlyIncome;
        private final double averageMonthlyExpense;
        private final double targetSaving;
        private final double availableExpensePool;
        private final int analysedTransactionCount;
        private final List<Suggestion> suggestions;

        private Plan(
                double averageMonthlyIncome,
                double averageMonthlyExpense,
                double targetSaving,
                double availableExpensePool,
                int analysedTransactionCount,
                List<Suggestion> suggestions
        ) {
            this.averageMonthlyIncome = averageMonthlyIncome;
            this.averageMonthlyExpense = averageMonthlyExpense;
            this.targetSaving = targetSaving;
            this.availableExpensePool = availableExpensePool;
            this.analysedTransactionCount = analysedTransactionCount;
            this.suggestions = Collections.unmodifiableList(
                    new ArrayList<>(suggestions)
            );
        }

        public double getAverageMonthlyIncome() {
            return averageMonthlyIncome;
        }

        public double getAverageMonthlyExpense() {
            return averageMonthlyExpense;
        }

        public double getTargetSaving() {
            return targetSaving;
        }

        public double getAvailableExpensePool() {
            return availableExpensePool;
        }

        public int getAnalysedTransactionCount() {
            return analysedTransactionCount;
        }

        @NonNull
        public List<Suggestion> getSuggestions() {
            return suggestions;
        }
    }

    public static final class Suggestion {

        private final String category;
        private final double recentMonthlyAverage;
        private final double recommendedLimit;
        private final int confidencePercent;
        private final String reason;

        private Suggestion(
                String category,
                double recentMonthlyAverage,
                double recommendedLimit,
                int confidencePercent,
                String reason
        ) {
            this.category = category;
            this.recentMonthlyAverage = recentMonthlyAverage;
            this.recommendedLimit = recommendedLimit;
            this.confidencePercent = confidencePercent;
            this.reason = reason;
        }

        @NonNull
        public String getCategory() {
            return category;
        }

        public double getRecentMonthlyAverage() {
            return recentMonthlyAverage;
        }

        public double getRecommendedLimit() {
            return recommendedLimit;
        }

        public int getConfidencePercent() {
            return confidencePercent;
        }

        @NonNull
        public String getReason() {
            return reason;
        }
    }
}
