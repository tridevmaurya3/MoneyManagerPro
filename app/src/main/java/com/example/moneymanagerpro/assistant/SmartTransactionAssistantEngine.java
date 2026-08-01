package com.example.moneymanagerpro.assistant;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.moneymanagerpro.model.Transaction;

import java.text.NumberFormat;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Private, offline transaction intelligence used by the Smart Transaction
 * Assistant screen.
 *
 * No transaction leaves the device. The engine performs deterministic local
 * analysis only, so it does not require an API key, internet connection or
 * cloud quota.
 */
public final class SmartTransactionAssistantEngine {

    private static final long HOUR_MILLIS =
            60L * 60L * 1000L;

    private static final long DAY_MILLIS =
            24L * HOUR_MILLIS;

    private static final long DUPLICATE_LOOKBACK_MILLIS =
            180L * DAY_MILLIS;

    private static final long UNUSUAL_LOOKBACK_MILLIS =
            120L * DAY_MILLIS;

    private static final String[] DATE_PATTERNS = {
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "yyyy-MM-dd",
            "dd-MM-yyyy HH:mm:ss",
            "dd-MM-yyyy HH:mm",
            "dd-MM-yyyy",
            "dd/MM/yyyy HH:mm:ss",
            "dd/MM/yyyy HH:mm",
            "dd/MM/yyyy",
            "MMM dd, yyyy HH:mm",
            "MMM dd, yyyy"
    };

    @NonNull
    public Analysis analyse(
            @Nullable List<Transaction> transactions
    ) {
        List<TransactionSnapshot> snapshots =
                createSnapshots(transactions);

        Calendar now =
                Calendar.getInstance();

        Calendar currentMonth =
                monthStart(now);

        Calendar previousMonth =
                monthStart(now);

        previousMonth.add(
                Calendar.MONTH,
                -1
        );

        MonthMetrics currentMetrics =
                calculateMonthMetrics(
                        snapshots,
                        currentMonth
                );

        MonthMetrics previousMetrics =
                calculateMonthMetrics(
                        snapshots,
                        previousMonth
                );

        List<DuplicateGroup> duplicateGroups =
                detectDuplicates(
                        snapshots,
                        now.getTimeInMillis()
                );

        List<UnusualTransaction> unusualTransactions =
                detectUnusualTransactions(
                        snapshots,
                        currentMetrics,
                        now.getTimeInMillis()
                );

        int healthScore =
                calculateHealthScore(
                        currentMetrics,
                        previousMetrics,
                        duplicateGroups,
                        unusualTransactions
                );

        List<Insight> insights =
                buildInsights(
                        currentMetrics,
                        previousMetrics,
                        duplicateGroups,
                        unusualTransactions
                );

        return new Analysis(
                formatMonth(currentMonth),
                currentMetrics,
                previousMetrics,
                snapshots,
                duplicateGroups,
                unusualTransactions,
                insights,
                healthScore
        );
    }

    @NonNull
    public Answer answer(
            @Nullable String rawQuestion,
            @NonNull Analysis analysis
    ) {
        String question =
                safe(rawQuestion)
                        .trim();

        if (question.isEmpty()) {
            return new Answer(
                    "Ask a finance question",
                    "Try: this month expense, highest category, duplicate transactions, unusual expenses, recent transactions, or category for petrol.",
                    AnswerTone.INFO
            );
        }

        String normalized =
                normalize(question);

        MonthMetrics targetMetrics =
                isPreviousMonthQuestion(normalized)
                        ? analysis.getPreviousMonth()
                        : analysis.getCurrentMonth();

        String targetLabel =
                isPreviousMonthQuestion(normalized)
                        ? "previous month"
                        : "this month";

        if (containsAny(
                normalized,
                "duplicate",
                "duplicates",
                "same transaction",
                "double entry",
                "दो बार",
                "डुप्लीकेट",
                "एक जैसा"
        )) {
            return buildDuplicateAnswer(
                    analysis
            );
        }

        if (containsAny(
                normalized,
                "unusual",
                "abnormal",
                "high expense",
                "large expense",
                "बड़ा खर्च",
                "ज्यादा खर्च",
                "असामान्य"
        )) {
            return buildUnusualAnswer(
                    analysis
            );
        }

        if (containsAny(
                normalized,
                "which category",
                "suggest category",
                "category for",
                "कैटेगरी",
                "श्रेणी",
                "किसमें डाल",
                "किस category"
        )) {
            String category =
                    suggestCategory(
                            normalized
                    );

            return new Answer(
                    "Suggested category",
                    "Based on the description, use “"
                            + category
                            + "”. You can still choose another category before saving the transaction.",
                    AnswerTone.PURPLE
            );
        }

        String matchedCategory =
                findCategoryMention(
                        normalized,
                        targetMetrics.getCategoryExpenses()
                );

        if (!matchedCategory.isEmpty()) {
            double amount =
                    valueForKeyIgnoreCase(
                            targetMetrics.getCategoryExpenses(),
                            matchedCategory
                    );

            return new Answer(
                    matchedCategory + " spending",
                    "Your "
                            + targetLabel
                            + " expense in “"
                            + matchedCategory
                            + "” is "
                            + formatMoney(amount)
                            + ". This represents "
                            + formatPercentage(
                            percentage(
                                    amount,
                                    targetMetrics.getExpense()
                            )
                    )
                            + " of the period’s total expense.",
                    amount > 0
                            ? AnswerTone.WARNING
                            : AnswerTone.NEUTRAL
            );
        }

        if (containsAny(
                normalized,
                "top category",
                "highest category",
                "largest category",
                "most spent",
                "सबसे ज्यादा",
                "सबसे अधिक",
                "टॉप कैटेगरी"
        )) {
            if (targetMetrics.getTopCategory().isEmpty()) {
                return new Answer(
                        "No category spending found",
                        "There are no expense entries for "
                                + targetLabel
                                + ".",
                        AnswerTone.NEUTRAL
                );
            }

            return new Answer(
                    "Highest spending category",
                    targetMetrics.getTopCategory()
                            + " is the largest category for "
                            + targetLabel
                            + " at "
                            + formatMoney(
                            targetMetrics.getTopCategoryAmount()
                    )
                            + " ("
                            + formatPercentage(
                            percentage(
                                    targetMetrics.getTopCategoryAmount(),
                                    targetMetrics.getExpense()
                            )
                    )
                            + " of total expense).",
                    AnswerTone.WARNING
            );
        }

        if (containsAny(
                normalized,
                "top account",
                "highest account",
                "which account",
                "खाता",
                "account spending"
        )) {
            String topAccount =
                    largestKey(
                            targetMetrics.getAccountExpenses()
                    );

            double topAccountAmount =
                    valueForKeyIgnoreCase(
                            targetMetrics.getAccountExpenses(),
                            topAccount
                    );

            if (topAccount.isEmpty()) {
                return new Answer(
                        "No account spending found",
                        "There are no expense entries for "
                                + targetLabel
                                + ".",
                        AnswerTone.NEUTRAL
                );
            }

            return new Answer(
                    "Highest-use account",
                    topAccount
                            + " has the highest recorded expense for "
                            + targetLabel
                            + ": "
                            + formatMoney(topAccountAmount)
                            + ".",
                    AnswerTone.INFO
            );
        }

        if (containsAny(
                normalized,
                "recent",
                "latest",
                "last transaction",
                "हाल का",
                "नया transaction",
                "अंतिम transaction"
        )) {
            return buildRecentAnswer(
                    analysis
            );
        }

        if (containsAny(
                normalized,
                "projected",
                "projection",
                "month end",
                "पूरे महीने",
                "महीने के अंत"
        )) {
            return new Answer(
                    "Projected month-end expense",
                    "At the current daily pace, this month may end near "
                            + formatMoney(
                            analysis
                                    .getCurrentMonth()
                                    .getProjectedExpense()
                    )
                            + ". The estimate changes whenever new transactions are added.",
                    AnswerTone.INFO
            );
        }

        if (containsAny(
                normalized,
                "average",
                "daily expense",
                "per day",
                "औसत",
                "रोज का खर्च",
                "प्रतिदिन"
        )) {
            return new Answer(
                    "Daily expense average",
                    "Your average expense for this month is "
                            + formatMoney(
                            analysis
                                    .getCurrentMonth()
                                    .getDailyExpenseAverage()
                    )
                            + " per elapsed day.",
                    AnswerTone.INFO
            );
        }

        if (containsAny(
                normalized,
                "saving",
                "savings",
                "balance left",
                "बचत",
                "कितना बचा"
        )) {
            double saving =
                    targetMetrics.getSaving();

            return new Answer(
                    "Saving position",
                    saving >= 0
                            ? "Your saving for "
                            + targetLabel
                            + " is "
                            + formatMoney(saving)
                            + ", equal to "
                            + formatPercentage(
                            targetMetrics.getSavingRate()
                    )
                            + " of recorded income."
                            : "Expense is above income for "
                            + targetLabel
                            + " by "
                            + formatMoney(
                            Math.abs(saving)
                    )
                            + ".",
                    saving >= 0
                            ? AnswerTone.SUCCESS
                            : AnswerTone.EXPENSE
            );
        }

        if (containsAny(
                normalized,
                "income",
                "salary total",
                "आय",
                "कमाई",
                "इनकम"
        )) {
            return new Answer(
                    "Income total",
                    "Recorded income for "
                            + targetLabel
                            + " is "
                            + formatMoney(
                            targetMetrics.getIncome()
                    )
                            + " from "
                            + targetMetrics.getIncomeCount()
                            + " entr"
                            + (targetMetrics.getIncomeCount() == 1
                            ? "y."
                            : "ies."),
                    AnswerTone.SUCCESS
            );
        }

        if (containsAny(
                normalized,
                "expense",
                "spent",
                "spending",
                "खर्च",
                "एक्सपेंस"
        )) {
            return new Answer(
                    "Expense total",
                    "Recorded expense for "
                            + targetLabel
                            + " is "
                            + formatMoney(
                            targetMetrics.getExpense()
                    )
                            + " from "
                            + targetMetrics.getExpenseCount()
                            + " entr"
                            + (targetMetrics.getExpenseCount() == 1
                            ? "y."
                            : "ies."),
                    AnswerTone.EXPENSE
            );
        }

        if (containsAny(
                normalized,
                "count",
                "how many",
                "कितने",
                "संख्या"
        )) {
            return new Answer(
                    "Transaction count",
                    targetMetrics.getTotalCount()
                            + " income/expense transactions are recorded for "
                            + targetLabel
                            + ".",
                    AnswerTone.INFO
            );
        }

        return new Answer(
                "Finance summary",
                targetLabel
                        + ": income "
                        + formatMoney(
                        targetMetrics.getIncome()
                )
                        + ", expense "
                        + formatMoney(
                        targetMetrics.getExpense()
                )
                        + ", saving "
                        + formatSignedMoney(
                        targetMetrics.getSaving()
                )
                        + ". Ask about a category, duplicates, unusual expenses, daily average or projected month-end spending.",
                targetMetrics.getSaving() >= 0
                        ? AnswerTone.SUCCESS
                        : AnswerTone.WARNING
        );
    }

    @NonNull
    private List<TransactionSnapshot> createSnapshots(
            @Nullable List<Transaction> transactions
    ) {
        List<TransactionSnapshot> snapshots =
                new ArrayList<>();

        if (transactions == null) {
            return snapshots;
        }

        for (Transaction transaction : transactions) {
            if (transaction == null) {
                continue;
            }

            Date parsedDate =
                    parseDate(
                            transaction.getDate()
                    );

            if (parsedDate == null) {
                continue;
            }

            double amount =
                    Math.abs(
                            transaction.getAmount()
                    );

            if (Double.isNaN(amount)
                    || Double.isInfinite(amount)
                    || amount <= 0.0001d) {

                continue;
            }

            snapshots.add(
                    new TransactionSnapshot(
                            transaction.getId(),
                            safe(transaction.getType()),
                            amount,
                            fallback(
                                    transaction.getCategory(),
                                    "Other"
                            ),
                            fallback(
                                    transaction.getAccount(),
                                    "Cash"
                            ),
                            safe(transaction.getNote()),
                            parsedDate.getTime(),
                            formatDate(parsedDate)
                    )
            );
        }

        Collections.sort(
                snapshots,
                (first, second) ->
                        Long.compare(
                                second.getTimestampMillis(),
                                first.getTimestampMillis()
                        )
        );

        return snapshots;
    }

    @NonNull
    private MonthMetrics calculateMonthMetrics(
            @NonNull List<TransactionSnapshot> snapshots,
            @NonNull Calendar month
    ) {
        Calendar start =
                monthStart(month);

        Calendar end =
                monthStart(month);

        end.add(
                Calendar.MONTH,
                1
        );

        long startMillis =
                start.getTimeInMillis();

        long endMillis =
                end.getTimeInMillis();

        double income = 0;
        double expense = 0;

        int incomeCount = 0;
        int expenseCount = 0;

        LinkedHashMap<String, Double> categoryExpenses =
                new LinkedHashMap<>();

        LinkedHashMap<String, Double> accountExpenses =
                new LinkedHashMap<>();

        for (TransactionSnapshot snapshot : snapshots) {
            long timestamp =
                    snapshot.getTimestampMillis();

            if (timestamp < startMillis
                    || timestamp >= endMillis) {

                continue;
            }

            if ("INCOME".equalsIgnoreCase(
                    snapshot.getType()
            )) {
                income += snapshot.getAmount();
                incomeCount++;

            } else if ("EXPENSE".equalsIgnoreCase(
                    snapshot.getType()
            )) {
                expense += snapshot.getAmount();
                expenseCount++;

                addAmount(
                        categoryExpenses,
                        snapshot.getCategory(),
                        snapshot.getAmount()
                );

                addAmount(
                        accountExpenses,
                        snapshot.getAccount(),
                        snapshot.getAmount()
                );
            }
        }

        String topCategory =
                largestKey(
                        categoryExpenses
                );

        double topCategoryAmount =
                valueForKeyIgnoreCase(
                        categoryExpenses,
                        topCategory
                );

        double saving =
                income - expense;

        double savingRate =
                income > 0
                        ? percentage(
                        saving,
                        income
                )
                        : 0;

        Calendar today =
                Calendar.getInstance();

        int elapsedDays;
        int daysInMonth =
                start.getActualMaximum(
                        Calendar.DAY_OF_MONTH
                );

        if (today.get(Calendar.YEAR)
                == start.get(Calendar.YEAR)
                && today.get(Calendar.MONTH)
                == start.get(Calendar.MONTH)) {

            elapsedDays =
                    Math.max(
                            1,
                            today.get(Calendar.DAY_OF_MONTH)
                    );

        } else {
            elapsedDays =
                    daysInMonth;
        }

        double dailyAverage =
                expense / elapsedDays;

        double projectedExpense =
                dailyAverage * daysInMonth;

        return new MonthMetrics(
                formatMonth(start),
                income,
                expense,
                saving,
                savingRate,
                incomeCount,
                expenseCount,
                categoryExpenses,
                accountExpenses,
                topCategory,
                topCategoryAmount,
                dailyAverage,
                projectedExpense
        );
    }

    @NonNull
    private List<DuplicateGroup> detectDuplicates(
            @NonNull List<TransactionSnapshot> snapshots,
            long nowMillis
    ) {
        LinkedHashMap<String, List<TransactionSnapshot>> grouped =
                new LinkedHashMap<>();

        long cutoff =
                nowMillis - DUPLICATE_LOOKBACK_MILLIS;

        for (TransactionSnapshot snapshot : snapshots) {
            if (snapshot.getTimestampMillis() < cutoff) {
                continue;
            }

            String key =
                    normalize(snapshot.getType())
                            + "|"
                            + Math.round(
                            snapshot.getAmount() * 100d
                    )
                            + "|"
                            + normalize(snapshot.getCategory())
                            + "|"
                            + normalize(snapshot.getAccount());

            List<TransactionSnapshot> group =
                    grouped.get(key);

            if (group == null) {
                group =
                        new ArrayList<>();

                grouped.put(
                        key,
                        group
                );
            }

            group.add(snapshot);
        }

        List<DuplicateGroup> duplicates =
                new ArrayList<>();

        for (List<TransactionSnapshot> group
                : grouped.values()) {

            if (group.size() < 2) {
                continue;
            }

            Collections.sort(
                    group,
                    Comparator.comparingLong(
                            TransactionSnapshot::getTimestampMillis
                    )
            );

            List<TransactionSnapshot> currentCluster =
                    new ArrayList<>();

            for (TransactionSnapshot snapshot : group) {
                if (currentCluster.isEmpty()) {
                    currentCluster.add(snapshot);
                    continue;
                }

                TransactionSnapshot previous =
                        currentCluster.get(
                                currentCluster.size() - 1
                        );

                long difference =
                        snapshot.getTimestampMillis()
                                - previous.getTimestampMillis();

                if (difference <= DAY_MILLIS) {
                    currentCluster.add(snapshot);

                } else {
                    addDuplicateCluster(
                            duplicates,
                            currentCluster
                    );

                    currentCluster =
                            new ArrayList<>();

                    currentCluster.add(snapshot);
                }
            }

            addDuplicateCluster(
                    duplicates,
                    currentCluster
            );
        }

        Collections.sort(
                duplicates,
                (first, second) ->
                        Long.compare(
                                second.getLatestTimestampMillis(),
                                first.getLatestTimestampMillis()
                        )
        );

        if (duplicates.size() > 8) {
            return new ArrayList<>(
                    duplicates.subList(
                            0,
                            8
                    )
            );
        }

        return duplicates;
    }

    private void addDuplicateCluster(
            @NonNull List<DuplicateGroup> destination,
            @NonNull List<TransactionSnapshot> cluster
    ) {
        if (cluster.size() < 2) {
            return;
        }

        TransactionSnapshot first =
                cluster.get(0);

        TransactionSnapshot last =
                cluster.get(
                        cluster.size() - 1
                );

        destination.add(
                new DuplicateGroup(
                        first.getType(),
                        first.getCategory(),
                        first.getAccount(),
                        first.getAmount(),
                        cluster.size(),
                        first.getDisplayDate(),
                        last.getDisplayDate(),
                        last.getTimestampMillis()
                )
        );
    }

    @NonNull
    private List<UnusualTransaction> detectUnusualTransactions(
            @NonNull List<TransactionSnapshot> snapshots,
            @NonNull MonthMetrics currentMetrics,
            long nowMillis
    ) {
        long cutoff =
                nowMillis - UNUSUAL_LOOKBACK_MILLIS;

        List<Double> allExpenseAmounts =
                new ArrayList<>();

        LinkedHashMap<String, List<Double>> categoryAmounts =
                new LinkedHashMap<>();

        for (TransactionSnapshot snapshot : snapshots) {
            if (snapshot.getTimestampMillis() < cutoff
                    || !"EXPENSE".equalsIgnoreCase(
                    snapshot.getType()
            )) {

                continue;
            }

            allExpenseAmounts.add(
                    snapshot.getAmount()
            );

            String categoryKey =
                    normalizedKey(
                            snapshot.getCategory()
                    );

            List<Double> amounts =
                    categoryAmounts.get(
                            categoryKey
                    );

            if (amounts == null) {
                amounts =
                        new ArrayList<>();

                categoryAmounts.put(
                        categoryKey,
                        amounts
                );
            }

            amounts.add(
                    snapshot.getAmount()
            );
        }

        double overallMedian =
                median(allExpenseAmounts);

        List<UnusualTransaction> unusual =
                new ArrayList<>();

        for (TransactionSnapshot snapshot : snapshots) {
            if (!"EXPENSE".equalsIgnoreCase(
                    snapshot.getType()
            )) {
                continue;
            }

            List<Double> categoryValues =
                    categoryAmounts.get(
                            normalizedKey(
                                    snapshot.getCategory()
                            )
                    );

            double categoryMedian =
                    median(categoryValues);

            double statisticalThreshold =
                    Math.max(
                            categoryMedian > 0
                                    ? categoryMedian * 2.5d
                                    : 0,
                            overallMedian > 0
                                    ? overallMedian * 3d
                                    : 0
                    );

            statisticalThreshold =
                    Math.max(
                            statisticalThreshold,
                            1000d
                    );

            boolean statisticallyHigh =
                    snapshot.getAmount()
                            >= statisticalThreshold;

            boolean highMonthlyShare =
                    currentMetrics.getExpense() > 0
                            && isInCurrentCalendarMonth(
                            snapshot.getTimestampMillis()
                    )
                            && percentage(
                            snapshot.getAmount(),
                            currentMetrics.getExpense()
                    ) >= 45d
                            && snapshot.getAmount() >= 500d;

            if (!statisticallyHigh
                    && !highMonthlyShare) {

                continue;
            }

            String reason;

            if (highMonthlyShare) {
                reason =
                        "This entry forms "
                                + formatPercentage(
                                percentage(
                                        snapshot.getAmount(),
                                        currentMetrics.getExpense()
                                )
                        )
                                + " of this month’s expense.";

            } else {
                reason =
                        "This amount is much higher than the recent typical expense pattern.";
            }

            unusual.add(
                    new UnusualTransaction(
                            snapshot.getId(),
                            snapshot.getCategory(),
                            snapshot.getAccount(),
                            snapshot.getNote(),
                            snapshot.getAmount(),
                            snapshot.getDisplayDate(),
                            snapshot.getTimestampMillis(),
                            reason
                    )
            );
        }

        Collections.sort(
                unusual,
                (first, second) ->
                        Double.compare(
                                second.getAmount(),
                                first.getAmount()
                        )
        );

        if (unusual.size() > 8) {
            return new ArrayList<>(
                    unusual.subList(
                            0,
                            8
                    )
            );
        }

        return unusual;
    }

    private int calculateHealthScore(
            @NonNull MonthMetrics current,
            @NonNull MonthMetrics previous,
            @NonNull List<DuplicateGroup> duplicates,
            @NonNull List<UnusualTransaction> unusual
    ) {
        if (current.getTotalCount() == 0) {
            return 0;
        }

        int score = 100;

        if (current.getIncome() <= 0) {
            score -= 30;

        } else if (current.getSaving() < 0) {
            score -= 38;

        } else if (current.getSavingRate() < 5) {
            score -= 24;

        } else if (current.getSavingRate() < 10) {
            score -= 17;

        } else if (current.getSavingRate() < 20) {
            score -= 9;

        } else if (current.getSavingRate() >= 30) {
            score += 3;
        }

        double topShare =
                percentage(
                        current.getTopCategoryAmount(),
                        current.getExpense()
                );

        if (topShare >= 60) {
            score -= 14;

        } else if (topShare >= 45) {
            score -= 7;
        }

        if (previous.getExpense() > 0
                && current.getExpense()
                > previous.getExpense() * 1.25d) {

            score -= 10;
        }

        score -= Math.min(
                10,
                duplicateExtraCount(duplicates) * 2
        );

        score -= Math.min(
                10,
                unusual.size() * 2
        );

        return Math.max(
                0,
                Math.min(
                        100,
                        score
                )
        );
    }

    @NonNull
    private List<Insight> buildInsights(
            @NonNull MonthMetrics current,
            @NonNull MonthMetrics previous,
            @NonNull List<DuplicateGroup> duplicates,
            @NonNull List<UnusualTransaction> unusual
    ) {
        List<Insight> insights =
                new ArrayList<>();

        if (current.getTotalCount() == 0) {
            insights.add(
                    new Insight(
                            "Start tracking this month",
                            "Add income and expense entries to receive category trends, duplicate checks and private smart answers.",
                            InsightTone.INFO
                    )
            );

            return insights;
        }

        if (current.getIncome() <= 0) {
            insights.add(
                    new Insight(
                            "Add this month’s income",
                            "Income is not recorded, so the saving rate and affordability guidance cannot be fully calculated.",
                            InsightTone.WARNING
                    )
            );

        } else if (current.getSaving() < 0) {
            insights.add(
                    new Insight(
                            "Expense is above income",
                            "Reduce flexible categories by at least "
                                    + formatMoney(
                                    Math.abs(
                                            current.getSaving()
                                    )
                            )
                                    + " to return to a positive monthly position.",
                            InsightTone.EXPENSE
                    )
            );

        } else if (current.getSavingRate() < 10) {
            insights.add(
                    new Insight(
                            "Saving buffer is low",
                            "The current saving rate is "
                                    + formatPercentage(
                                    current.getSavingRate()
                            )
                                    + ". A gradual target of 10–20% can provide a stronger buffer.",
                            InsightTone.WARNING
                    )
            );

        } else {
            insights.add(
                    new Insight(
                            "Positive monthly saving",
                            "You are currently saving "
                                    + formatMoney(
                                    current.getSaving()
                            )
                                    + " ("
                                    + formatPercentage(
                                    current.getSavingRate()
                            )
                                    + " of income).",
                            InsightTone.SUCCESS
                    )
            );
        }

        if (!current.getTopCategory().isEmpty()) {
            double share =
                    percentage(
                            current.getTopCategoryAmount(),
                            current.getExpense()
                    );

            insights.add(
                    new Insight(
                            "Largest category: "
                                    + current.getTopCategory(),
                            formatMoney(
                                    current.getTopCategoryAmount()
                            )
                                    + " or "
                                    + formatPercentage(share)
                                    + " of this month’s expense is concentrated here.",
                            share >= 45
                                    ? InsightTone.WARNING
                                    : InsightTone.INFO
                    )
            );
        }

        if (previous.getExpense() > 0) {
            double difference =
                    current.getExpense()
                            - previous.getExpense();

            double change =
                    percentage(
                            Math.abs(difference),
                            previous.getExpense()
                    );

            if (difference > 0.01d) {
                insights.add(
                        new Insight(
                                "Expense increased",
                                "This month’s recorded expense is "
                                        + formatPercentage(change)
                                        + " higher than the previous month.",
                                change >= 20
                                        ? InsightTone.WARNING
                                        : InsightTone.INFO
                        )
                );

            } else if (difference < -0.01d) {
                insights.add(
                        new Insight(
                                "Expense is lower",
                                "This month’s recorded expense is "
                                        + formatPercentage(change)
                                        + " below the previous month.",
                                InsightTone.SUCCESS
                        )
                );
            }
        }

        int duplicateExtra =
                duplicateExtraCount(duplicates);

        if (duplicateExtra > 0) {
            insights.add(
                    new Insight(
                            "Review possible duplicates",
                            duplicateExtra
                                    + " possible repeated entr"
                                    + (duplicateExtra == 1
                                    ? "y was"
                                    : "ies were")
                                    + " detected within 24 hours of a matching transaction.",
                            InsightTone.WARNING
                    )
            );
        }

        if (!unusual.isEmpty()) {
            insights.add(
                    new Insight(
                            "Check unusual expenses",
                            unusual.size()
                                    + " high-value entr"
                                    + (unusual.size() == 1
                                    ? "y needs"
                                    : "ies need")
                                    + " review against your recent spending pattern.",
                            InsightTone.EXPENSE
                    )
            );
        }

        if (insights.size() > 6) {
            return new ArrayList<>(
                    insights.subList(
                            0,
                            6
                    )
            );
        }

        return insights;
    }

    @NonNull
    private Answer buildDuplicateAnswer(
            @NonNull Analysis analysis
    ) {
        int extraCount =
                duplicateExtraCount(
                        analysis.getDuplicateGroups()
                );

        if (extraCount == 0) {
            return new Answer(
                    "No likely duplicates found",
                    "The last 180 days were checked for matching type, amount, category and account entries created within 24 hours.",
                    AnswerTone.SUCCESS
            );
        }

        DuplicateGroup first =
                analysis
                        .getDuplicateGroups()
                        .get(0);

        return new Answer(
                "Possible duplicate transactions",
                extraCount
                        + " extra matching entr"
                        + (extraCount == 1
                        ? "y was"
                        : "ies were")
                        + " detected. The latest group is "
                        + first.getCategory()
                        + " • "
                        + formatMoney(
                        first.getAmount()
                )
                        + " • "
                        + first.getCount()
                        + " matching entries. Review before deleting anything.",
                AnswerTone.WARNING
        );
    }

    @NonNull
    private Answer buildUnusualAnswer(
            @NonNull Analysis analysis
    ) {
        if (analysis.getUnusualTransactions().isEmpty()) {
            return new Answer(
                    "No unusual high expense found",
                    "Recent expenses are within the locally calculated amount pattern. This is an automated check, not a fraud decision.",
                    AnswerTone.SUCCESS
            );
        }

        UnusualTransaction first =
                analysis
                        .getUnusualTransactions()
                        .get(0);

        return new Answer(
                "Unusual expense review",
                analysis.getUnusualTransactions().size()
                        + " high-value entr"
                        + (analysis.getUnusualTransactions().size() == 1
                        ? "y was"
                        : "ies were")
                        + " flagged. Highest: "
                        + first.getCategory()
                        + " • "
                        + formatMoney(
                        first.getAmount()
                )
                        + " • "
                        + first.getDisplayDate()
                        + ". "
                        + first.getReason(),
                AnswerTone.EXPENSE
        );
    }

    @NonNull
    private Answer buildRecentAnswer(
            @NonNull Analysis analysis
    ) {
        if (analysis.getRecentTransactions().isEmpty()) {
            return new Answer(
                    "No transaction found",
                    "Add an income or expense entry first.",
                    AnswerTone.NEUTRAL
            );
        }

        StringBuilder builder =
                new StringBuilder();

        int limit =
                Math.min(
                        3,
                        analysis
                                .getRecentTransactions()
                                .size()
                );

        for (int index = 0;
             index < limit;
             index++) {

            TransactionSnapshot snapshot =
                    analysis
                            .getRecentTransactions()
                            .get(index);

            if (index > 0) {
                builder.append("\n");
            }

            builder.append(index + 1)
                    .append(". ")
                    .append(snapshot.getCategory())
                    .append(" • ")
                    .append(formatMoney(snapshot.getAmount()))
                    .append(" • ")
                    .append(snapshot.getDisplayDate());
        }

        return new Answer(
                "Recent transactions",
                builder.toString(),
                AnswerTone.INFO
        );
    }

    @NonNull
    private String suggestCategory(
            @NonNull String normalizedDescription
    ) {
        if (containsAny(
                normalizedDescription,
                "salary",
                "वेतन",
                "salary credit",
                "income"
        )) {
            return "Salary";
        }

        if (containsAny(
                normalizedDescription,
                "petrol",
                "diesel",
                "fuel",
                "auto",
                "taxi",
                "cab",
                "bus",
                "metro",
                "पेट्रोल",
                "डीजल",
                "किराया"
        )) {
            return "Transport";
        }

        if (containsAny(
                normalizedDescription,
                "grocery",
                "kirana",
                "vegetable",
                "milk",
                "food",
                "restaurant",
                "zomato",
                "swiggy",
                "किराना",
                "सब्जी",
                "दूध",
                "खाना"
        )) {
            return "Food & Grocery";
        }

        if (containsAny(
                normalizedDescription,
                "electricity",
                "mobile recharge",
                "internet",
                "broadband",
                "water bill",
                "gas bill",
                "बिजली",
                "रिचार्ज",
                "बिल"
        )) {
            return "Bills & Utilities";
        }

        if (containsAny(
                normalizedDescription,
                "doctor",
                "medicine",
                "hospital",
                "medical",
                "pharmacy",
                "दवा",
                "डॉक्टर",
                "अस्पताल"
        )) {
            return "Health";
        }

        if (containsAny(
                normalizedDescription,
                "school",
                "college",
                "tuition",
                "book",
                "course",
                "fee",
                "स्कूल",
                "किताब",
                "फीस",
                "पढ़ाई"
        )) {
            return "Education";
        }

        if (containsAny(
                normalizedDescription,
                "rent",
                "house",
                "maintenance",
                "repair",
                "किराया",
                "घर",
                "मरम्मत"
        )) {
            return "Housing";
        }

        if (containsAny(
                normalizedDescription,
                "shopping",
                "clothes",
                "shoe",
                "amazon",
                "flipkart",
                "कपड़ा",
                "खरीदारी"
        )) {
            return "Shopping";
        }

        if (containsAny(
                normalizedDescription,
                "travel",
                "hotel",
                "flight",
                "train",
                "trip",
                "यात्रा",
                "होटल",
                "ट्रेन"
        )) {
            return "Travel";
        }

        if (containsAny(
                normalizedDescription,
                "sip",
                "mutual fund",
                "share",
                "stock",
                "investment",
                "निवेश"
        )) {
            return "Investment";
        }

        if (containsAny(
                normalizedDescription,
                "emi",
                "loan",
                "interest",
                "कर्ज",
                "ऋण"
        )) {
            return "Loan & EMI";
        }

        if (containsAny(
                normalizedDescription,
                "movie",
                "netflix",
                "game",
                "entertainment",
                "मनोरंजन"
        )) {
            return "Entertainment";
        }

        return "Other";
    }

    private boolean isPreviousMonthQuestion(
            @NonNull String normalized
    ) {
        return containsAny(
                normalized,
                "last month",
                "previous month",
                "पिछले महीने",
                "पिछला महीना",
                "गत माह"
        );
    }

    @NonNull
    private String findCategoryMention(
            @NonNull String normalizedQuestion,
            @NonNull Map<String, Double> categories
    ) {
        for (String category : categories.keySet()) {
            String normalizedCategory =
                    normalize(category);

            if (!normalizedCategory.isEmpty()
                    && normalizedQuestion.contains(
                    normalizedCategory
            )) {

                return category;
            }
        }

        return "";
    }

    private int duplicateExtraCount(
            @NonNull List<DuplicateGroup> groups
    ) {
        int count = 0;

        for (DuplicateGroup group : groups) {
            count += Math.max(
                    0,
                    group.getCount() - 1
            );
        }

        return count;
    }

    private boolean isInCurrentCalendarMonth(
            long timestampMillis
    ) {
        Calendar transactionCalendar =
                Calendar.getInstance();

        transactionCalendar.setTimeInMillis(
                timestampMillis
        );

        Calendar now =
                Calendar.getInstance();

        return transactionCalendar.get(Calendar.YEAR)
                == now.get(Calendar.YEAR)
                && transactionCalendar.get(Calendar.MONTH)
                == now.get(Calendar.MONTH);
    }

    private void addAmount(
            @NonNull LinkedHashMap<String, Double> values,
            @Nullable String rawKey,
            double amount
    ) {
        String key =
                fallback(
                        rawKey,
                        "Other"
                );

        String existingKey =
                "";

        for (String candidate : values.keySet()) {
            if (candidate.equalsIgnoreCase(key)) {
                existingKey = candidate;
                break;
            }
        }

        if (existingKey.isEmpty()) {
            values.put(
                    key,
                    amount
            );

        } else {
            Double oldValue =
                    values.get(existingKey);

            values.put(
                    existingKey,
                    (oldValue == null ? 0 : oldValue)
                            + amount
            );
        }
    }

    @NonNull
    private static String largestKey(
            @NonNull Map<String, Double> values
    ) {
        String largestKey = "";
        double largestValue = -1;

        for (Map.Entry<String, Double> entry
                : values.entrySet()) {

            double value =
                    entry.getValue() == null
                            ? 0
                            : entry.getValue();

            if (value > largestValue) {
                largestValue = value;
                largestKey = entry.getKey();
            }
        }

        return largestKey;
    }

    private double valueForKeyIgnoreCase(
            @NonNull Map<String, Double> values,
            @Nullable String requestedKey
    ) {
        String safeRequested =
                safe(requestedKey);

        for (Map.Entry<String, Double> entry
                : values.entrySet()) {

            if (entry.getKey().equalsIgnoreCase(
                    safeRequested
            )) {
                return entry.getValue() == null
                        ? 0
                        : entry.getValue();
            }
        }

        return 0;
    }

    private double median(
            @Nullable List<Double> values
    ) {
        if (values == null
                || values.isEmpty()) {

            return 0;
        }

        List<Double> copy =
                new ArrayList<>();

        for (Double value : values) {
            if (value != null
                    && !Double.isNaN(value)
                    && !Double.isInfinite(value)
                    && value > 0) {

                copy.add(value);
            }
        }

        if (copy.isEmpty()) {
            return 0;
        }

        Collections.sort(copy);

        int middle =
                copy.size() / 2;

        if (copy.size() % 2 == 1) {
            return copy.get(middle);
        }

        return (
                copy.get(middle - 1)
                        + copy.get(middle)
        ) / 2d;
    }

    @Nullable
    private Date parseDate(
            @Nullable String rawDate
    ) {
        String value =
                safe(rawDate)
                        .trim();

        if (value.isEmpty()) {
            return null;
        }

        for (String pattern : DATE_PATTERNS) {
            SimpleDateFormat formatter =
                    new SimpleDateFormat(
                            pattern,
                            Locale.ENGLISH
                    );

            formatter.setLenient(false);

            ParsePosition position =
                    new ParsePosition(0);

            Date date =
                    formatter.parse(
                            value,
                            position
                    );

            if (date != null
                    && position.getIndex()
                    == value.length()) {

                return date;
            }
        }

        return null;
    }

    @NonNull
    private Calendar monthStart(
            @NonNull Calendar source
    ) {
        Calendar result =
                (Calendar) source.clone();

        result.set(
                Calendar.DAY_OF_MONTH,
                1
        );

        result.set(
                Calendar.HOUR_OF_DAY,
                0
        );

        result.set(
                Calendar.MINUTE,
                0
        );

        result.set(
                Calendar.SECOND,
                0
        );

        result.set(
                Calendar.MILLISECOND,
                0
        );

        return result;
    }

    @NonNull
    private String formatMonth(
            @NonNull Calendar calendar
    ) {
        return new SimpleDateFormat(
                "MMMM yyyy",
                Locale.ENGLISH
        ).format(
                calendar.getTime()
        );
    }

    @NonNull
    private String formatDate(
            @NonNull Date date
    ) {
        return new SimpleDateFormat(
                "dd MMM yyyy, hh:mm a",
                Locale.ENGLISH
        ).format(date);
    }

    @NonNull
    private String formatMoney(
            double amount
    ) {
        NumberFormat formatter =
                NumberFormat.getCurrencyInstance(
                        new Locale(
                                "en",
                                "IN"
                        )
                );

        formatter.setMaximumFractionDigits(2);
        formatter.setMinimumFractionDigits(2);

        return formatter.format(
                Math.abs(amount)
        );
    }

    @NonNull
    private String formatSignedMoney(
            double amount
    ) {
        if (amount > 0.0001d) {
            return "+" + formatMoney(amount);
        }

        if (amount < -0.0001d) {
            return "-" + formatMoney(amount);
        }

        return formatMoney(0);
    }

    @NonNull
    private String formatPercentage(
            double value
    ) {
        if (Double.isNaN(value)
                || Double.isInfinite(value)) {

            value = 0;
        }

        return String.format(
                Locale.ENGLISH,
                "%.1f%%",
                Math.max(
                        0,
                        value
                )
        );
    }

    private double percentage(
            double part,
            double whole
    ) {
        if (Math.abs(whole) <= 0.0001d) {
            return 0;
        }

        return (part / whole) * 100d;
    }

    @NonNull
    private String normalizedKey(
            @Nullable String value
    ) {
        return normalize(
                fallback(
                        value,
                        "Other"
                )
        );
    }

    @NonNull
    private String normalize(
            @Nullable String value
    ) {
        return safe(value)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    private boolean containsAny(
            @NonNull String text,
            @NonNull String... values
    ) {
        for (String value : values) {
            if (text.contains(
                    normalize(value)
            )) {
                return true;
            }
        }

        return false;
    }

    @NonNull
    private String fallback(
            @Nullable String value,
            @NonNull String fallback
    ) {
        String safeValue =
                safe(value)
                        .trim();

        return safeValue.isEmpty()
                ? fallback
                : safeValue;
    }

    @NonNull
    private String safe(
            @Nullable String value
    ) {
        return value == null
                ? ""
                : value;
    }

    public enum AnswerTone {
        SUCCESS,
        EXPENSE,
        WARNING,
        INFO,
        PURPLE,
        NEUTRAL
    }

    public enum InsightTone {
        SUCCESS,
        EXPENSE,
        WARNING,
        INFO
    }

    public static final class Answer {

        private final String title;
        private final String message;
        private final AnswerTone tone;

        private Answer(
                @NonNull String title,
                @NonNull String message,
                @NonNull AnswerTone tone
        ) {
            this.title = title;
            this.message = message;
            this.tone = tone;
        }

        @NonNull
        public String getTitle() {
            return title;
        }

        @NonNull
        public String getMessage() {
            return message;
        }

        @NonNull
        public AnswerTone getTone() {
            return tone;
        }
    }

    public static final class Analysis {

        private final String currentMonthLabel;
        private final MonthMetrics currentMonth;
        private final MonthMetrics previousMonth;
        private final List<TransactionSnapshot> recentTransactions;
        private final List<DuplicateGroup> duplicateGroups;
        private final List<UnusualTransaction> unusualTransactions;
        private final List<Insight> insights;
        private final int healthScore;

        private Analysis(
                @NonNull String currentMonthLabel,
                @NonNull MonthMetrics currentMonth,
                @NonNull MonthMetrics previousMonth,
                @NonNull List<TransactionSnapshot> recentTransactions,
                @NonNull List<DuplicateGroup> duplicateGroups,
                @NonNull List<UnusualTransaction> unusualTransactions,
                @NonNull List<Insight> insights,
                int healthScore
        ) {
            this.currentMonthLabel = currentMonthLabel;
            this.currentMonth = currentMonth;
            this.previousMonth = previousMonth;
            this.recentTransactions =
                    Collections.unmodifiableList(
                            new ArrayList<>(
                                    recentTransactions
                            )
                    );
            this.duplicateGroups =
                    Collections.unmodifiableList(
                            new ArrayList<>(
                                    duplicateGroups
                            )
                    );
            this.unusualTransactions =
                    Collections.unmodifiableList(
                            new ArrayList<>(
                                    unusualTransactions
                            )
                    );
            this.insights =
                    Collections.unmodifiableList(
                            new ArrayList<>(
                                    insights
                            )
                    );
            this.healthScore = healthScore;
        }

        @NonNull
        public String getCurrentMonthLabel() {
            return currentMonthLabel;
        }

        @NonNull
        public MonthMetrics getCurrentMonth() {
            return currentMonth;
        }

        @NonNull
        public MonthMetrics getPreviousMonth() {
            return previousMonth;
        }

        @NonNull
        public List<TransactionSnapshot> getRecentTransactions() {
            return recentTransactions;
        }

        @NonNull
        public List<DuplicateGroup> getDuplicateGroups() {
            return duplicateGroups;
        }

        @NonNull
        public List<UnusualTransaction> getUnusualTransactions() {
            return unusualTransactions;
        }

        @NonNull
        public List<Insight> getInsights() {
            return insights;
        }

        public int getHealthScore() {
            return healthScore;
        }

        public int getDuplicateExtraCount() {
            int count = 0;

            for (DuplicateGroup group : duplicateGroups) {
                count += Math.max(
                        0,
                        group.getCount() - 1
                );
            }

            return count;
        }
    }

    public static final class MonthMetrics {

        private final String label;
        private final double income;
        private final double expense;
        private final double saving;
        private final double savingRate;
        private final int incomeCount;
        private final int expenseCount;
        private final Map<String, Double> categoryExpenses;
        private final Map<String, Double> accountExpenses;
        private final String topCategory;
        private final double topCategoryAmount;
        private final double dailyExpenseAverage;
        private final double projectedExpense;

        private MonthMetrics(
                @NonNull String label,
                double income,
                double expense,
                double saving,
                double savingRate,
                int incomeCount,
                int expenseCount,
                @NonNull Map<String, Double> categoryExpenses,
                @NonNull Map<String, Double> accountExpenses,
                @NonNull String topCategory,
                double topCategoryAmount,
                double dailyExpenseAverage,
                double projectedExpense
        ) {
            this.label = label;
            this.income = income;
            this.expense = expense;
            this.saving = saving;
            this.savingRate = savingRate;
            this.incomeCount = incomeCount;
            this.expenseCount = expenseCount;
            this.categoryExpenses =
                    Collections.unmodifiableMap(
                            new LinkedHashMap<>(
                                    categoryExpenses
                            )
                    );
            this.accountExpenses =
                    Collections.unmodifiableMap(
                            new LinkedHashMap<>(
                                    accountExpenses
                            )
                    );
            this.topCategory = topCategory;
            this.topCategoryAmount = topCategoryAmount;
            this.dailyExpenseAverage = dailyExpenseAverage;
            this.projectedExpense = projectedExpense;
        }

        @NonNull
        public String getLabel() {
            return label;
        }

        public double getIncome() {
            return income;
        }

        public double getExpense() {
            return expense;
        }

        public double getSaving() {
            return saving;
        }

        public double getSavingRate() {
            return savingRate;
        }

        public int getIncomeCount() {
            return incomeCount;
        }

        public int getExpenseCount() {
            return expenseCount;
        }

        public int getTotalCount() {
            return incomeCount + expenseCount;
        }

        @NonNull
        public Map<String, Double> getCategoryExpenses() {
            return categoryExpenses;
        }

        @NonNull
        public Map<String, Double> getAccountExpenses() {
            return accountExpenses;
        }

        @NonNull
        public String getTopCategory() {
            return topCategory;
        }

        public double getTopCategoryAmount() {
            return topCategoryAmount;
        }

        public double getDailyExpenseAverage() {
            return dailyExpenseAverage;
        }

        public double getProjectedExpense() {
            return projectedExpense;
        }
    }

    public static final class TransactionSnapshot {

        private final int id;
        private final String type;
        private final double amount;
        private final String category;
        private final String account;
        private final String note;
        private final long timestampMillis;
        private final String displayDate;

        private TransactionSnapshot(
                int id,
                @NonNull String type,
                double amount,
                @NonNull String category,
                @NonNull String account,
                @NonNull String note,
                long timestampMillis,
                @NonNull String displayDate
        ) {
            this.id = id;
            this.type = type;
            this.amount = amount;
            this.category = category;
            this.account = account;
            this.note = note;
            this.timestampMillis = timestampMillis;
            this.displayDate = displayDate;
        }

        public int getId() {
            return id;
        }

        @NonNull
        public String getType() {
            return type;
        }

        public double getAmount() {
            return amount;
        }

        @NonNull
        public String getCategory() {
            return category;
        }

        @NonNull
        public String getAccount() {
            return account;
        }

        @NonNull
        public String getNote() {
            return note;
        }

        public long getTimestampMillis() {
            return timestampMillis;
        }

        @NonNull
        public String getDisplayDate() {
            return displayDate;
        }
    }

    public static final class DuplicateGroup {

        private final String type;
        private final String category;
        private final String account;
        private final double amount;
        private final int count;
        private final String firstDate;
        private final String latestDate;
        private final long latestTimestampMillis;

        private DuplicateGroup(
                @NonNull String type,
                @NonNull String category,
                @NonNull String account,
                double amount,
                int count,
                @NonNull String firstDate,
                @NonNull String latestDate,
                long latestTimestampMillis
        ) {
            this.type = type;
            this.category = category;
            this.account = account;
            this.amount = amount;
            this.count = count;
            this.firstDate = firstDate;
            this.latestDate = latestDate;
            this.latestTimestampMillis = latestTimestampMillis;
        }

        @NonNull
        public String getType() {
            return type;
        }

        @NonNull
        public String getCategory() {
            return category;
        }

        @NonNull
        public String getAccount() {
            return account;
        }

        public double getAmount() {
            return amount;
        }

        public int getCount() {
            return count;
        }

        @NonNull
        public String getFirstDate() {
            return firstDate;
        }

        @NonNull
        public String getLatestDate() {
            return latestDate;
        }

        public long getLatestTimestampMillis() {
            return latestTimestampMillis;
        }
    }

    public static final class UnusualTransaction {

        private final int transactionId;
        private final String category;
        private final String account;
        private final String note;
        private final double amount;
        private final String displayDate;
        private final long timestampMillis;
        private final String reason;

        private UnusualTransaction(
                int transactionId,
                @NonNull String category,
                @NonNull String account,
                @NonNull String note,
                double amount,
                @NonNull String displayDate,
                long timestampMillis,
                @NonNull String reason
        ) {
            this.transactionId = transactionId;
            this.category = category;
            this.account = account;
            this.note = note;
            this.amount = amount;
            this.displayDate = displayDate;
            this.timestampMillis = timestampMillis;
            this.reason = reason;
        }

        public int getTransactionId() {
            return transactionId;
        }

        @NonNull
        public String getCategory() {
            return category;
        }

        @NonNull
        public String getAccount() {
            return account;
        }

        @NonNull
        public String getNote() {
            return note;
        }

        public double getAmount() {
            return amount;
        }

        @NonNull
        public String getDisplayDate() {
            return displayDate;
        }

        public long getTimestampMillis() {
            return timestampMillis;
        }

        @NonNull
        public String getReason() {
            return reason;
        }
    }

    public static final class Insight {

        private final String title;
        private final String message;
        private final InsightTone tone;

        private Insight(
                @NonNull String title,
                @NonNull String message,
                @NonNull InsightTone tone
        ) {
            this.title = title;
            this.message = message;
            this.tone = tone;
        }

        @NonNull
        public String getTitle() {
            return title;
        }

        @NonNull
        public String getMessage() {
            return message;
        }

        @NonNull
        public InsightTone getTone() {
            return tone;
        }
    }
}
