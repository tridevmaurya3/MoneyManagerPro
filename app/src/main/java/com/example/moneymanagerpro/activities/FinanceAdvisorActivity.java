package com.example.moneymanagerpro.activities;

import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.database.DatabaseClient;
import com.example.moneymanagerpro.model.Transaction;
import com.example.moneymanagerpro.utils.BubbleTouchAnimator;
import com.google.android.material.card.MaterialCardView;

import java.text.NumberFormat;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class FinanceAdvisorActivity extends AppCompatActivity {

    private TextView txtScore;
    private TextView txtScoreLabel;
    private TextView txtIncome;
    private TextView txtExpense;
    private TextView txtSaving;
    private TextView txtOverview;

    private LinearLayout recommendationContainer;

    private int adviceRequestVersion = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_finance_advisor);

        bindViews();
        prepareScreen();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadAdvice();
    }

    private void bindViews() {
        TextView btnBack =
                findViewById(R.id.btnBack);

        txtScore =
                findViewById(R.id.txtScore);

        txtScoreLabel =
                findViewById(R.id.txtScoreLabel);

        txtIncome =
                findViewById(R.id.txtAdvisorIncome);

        txtExpense =
                findViewById(R.id.txtAdvisorExpense);

        txtSaving =
                findViewById(R.id.txtAdvisorSaving);

        txtOverview =
                findViewById(R.id.txtAdvisorOverview);

        recommendationContainer =
                findViewById(R.id.recommendationContainer);

        btnBack.setOnClickListener(
                view -> finish()
        );

        BubbleTouchAnimator.apply(
                btnBack
        );
    }

    private void prepareScreen() {
        showLoadingState();
    }

    private void showLoadingState() {
        txtScore.setText("—");

        txtScore.setTextColor(
                getColorValue(
                        R.color.purple
                )
        );

        txtScoreLabel.setText(
                "Analysing your finance data"
        );

        txtIncome.setText("₹0.00");
        txtExpense.setText("₹0.00");
        txtSaving.setText("₹0.00");

        txtSaving.setTextColor(
                getColorValue(
                        R.color.secondary
                )
        );

        txtOverview.setText(
                "Reviewing this month’s income, expenses and spending patterns."
        );

        recommendationContainer.removeAllViews();

        addStatusCard(
                "Analysing transactions",
                "Your personal suggestions will appear here after the calculation is complete.",
                "✦",
                AdviceTone.PURPLE
        );
    }

    private void loadAdvice() {
        int currentRequest =
                ++adviceRequestVersion;

        showLoadingState();

        new Thread(() -> {
            try {
                List<Transaction> transactions =
                        DatabaseClient
                                .getInstance(
                                        getApplicationContext()
                                )
                                .getAppDatabase()
                                .transactionDao()
                                .getAllTransactions();

                AdvisorData advisorData =
                        calculateAdvisorData(
                                transactions
                        );

                runOnUiThread(() -> {
                    if (currentRequest
                            != adviceRequestVersion
                            || isFinishing()
                            || isDestroyed()) {

                        return;
                    }

                    showAdvisorData(
                            advisorData
                    );
                });

            } catch (Exception exception) {
                runOnUiThread(() -> {
                    if (currentRequest
                            != adviceRequestVersion
                            || isFinishing()
                            || isDestroyed()) {

                        return;
                    }

                    showAdvisorError();
                });
            }
        }).start();
    }

    private AdvisorData calculateAdvisorData(
            List<Transaction> transactions
    ) {
        AdvisorData data =
                new AdvisorData();

        Calendar today =
                Calendar.getInstance();

        data.currentYear =
                today.get(
                        Calendar.YEAR
                );

        data.currentMonth =
                today.get(
                        Calendar.MONTH
                );

        data.currentDay =
                today.get(
                        Calendar.DAY_OF_MONTH
                );

        data.daysInCurrentMonth =
                today.getActualMaximum(
                        Calendar.DAY_OF_MONTH
                );

        Calendar previousMonth =
                (Calendar) today.clone();

        previousMonth.add(
                Calendar.MONTH,
                -1
        );

        int previousYear =
                previousMonth.get(
                        Calendar.YEAR
                );

        int previousMonthNumber =
                previousMonth.get(
                        Calendar.MONTH
                );

        if (transactions != null) {
            for (Transaction transaction : transactions) {
                if (transaction == null) {
                    continue;
                }

                Date transactionDate =
                        parseDate(
                                transaction.getDate()
                        );

                if (transactionDate == null) {
                    continue;
                }

                double amount =
                        transaction.getAmount();

                if (Double.isNaN(amount)
                        || Double.isInfinite(amount)
                        || amount == 0) {

                    continue;
                }

                amount =
                        Math.abs(amount);

                Calendar transactionCalendar =
                        Calendar.getInstance();

                transactionCalendar.setTime(
                        transactionDate
                );

                boolean currentMonthTransaction =
                        transactionCalendar.get(
                                Calendar.YEAR
                        ) == data.currentYear
                                && transactionCalendar.get(
                                Calendar.MONTH
                        ) == data.currentMonth;

                boolean previousMonthTransaction =
                        transactionCalendar.get(
                                Calendar.YEAR
                        ) == previousYear
                                && transactionCalendar.get(
                                Calendar.MONTH
                        ) == previousMonthNumber;

                String type =
                        safeText(
                                transaction.getType(),
                                ""
                        );

                if (currentMonthTransaction) {
                    if ("INCOME".equalsIgnoreCase(type)) {
                        data.currentIncome += amount;
                        data.currentIncomeEntries++;
                        data.currentEntries++;

                    } else if ("EXPENSE".equalsIgnoreCase(type)) {
                        data.currentExpense += amount;
                        data.currentExpenseEntries++;
                        data.currentEntries++;

                        addCategoryExpense(
                                data.categoryExpenses,
                                safeText(
                                        transaction.getCategory(),
                                        "Other"
                                ),
                                amount
                        );
                    }
                }

                if (previousMonthTransaction) {
                    if ("INCOME".equalsIgnoreCase(type)) {
                        data.previousIncome += amount;
                        data.previousEntries++;

                    } else if ("EXPENSE".equalsIgnoreCase(type)) {
                        data.previousExpense += amount;
                        data.previousEntries++;
                    }
                }
            }
        }

        data.saving =
                data.currentIncome
                        - data.currentExpense;

        if (data.currentIncome > 0) {
            data.savingRate =
                    (
                            data.saving
                                    / data.currentIncome
                    ) * 100;
        }

        data.dailyExpenseAverage =
                data.currentExpense
                        / Math.max(
                        data.currentDay,
                        1
                );

        data.projectedExpense =
                data.dailyExpenseAverage
                        * data.daysInCurrentMonth;

        data.largestCategoryName =
                getLargestCategoryName(
                        data.categoryExpenses
                );

        data.largestCategoryAmount =
                getLargestCategoryAmount(
                        data.categoryExpenses
                );

        if (data.currentExpense > 0) {
            data.largestCategoryPercentage =
                    (
                            data.largestCategoryAmount
                                    / data.currentExpense
                    ) * 100;
        }

        if (data.previousExpense > 0) {
            data.expenseDifference =
                    data.currentExpense
                            - data.previousExpense;

            data.expenseTrendPercentage =
                    (
                            Math.abs(
                                    data.expenseDifference
                            )
                                    / data.previousExpense
                    ) * 100;
        }

        data.score =
                calculateScore(data);

        return data;
    }

    private void addCategoryExpense(
            LinkedHashMap<String, Double> categoryExpenses,
            String category,
            double amount
    ) {
        String safeCategory =
                safeText(
                        category,
                        "Other"
                );

        String matchingCategory =
                null;

        for (String existingCategory
                : categoryExpenses.keySet()) {

            if (existingCategory.equalsIgnoreCase(
                    safeCategory
            )) {
                matchingCategory =
                        existingCategory;

                break;
            }
        }

        if (matchingCategory == null) {
            categoryExpenses.put(
                    safeCategory,
                    amount
            );

        } else {
            double oldAmount =
                    categoryExpenses.get(
                            matchingCategory
                    ) == null
                            ? 0
                            : categoryExpenses.get(
                            matchingCategory
                    );

            categoryExpenses.put(
                    matchingCategory,
                    oldAmount + amount
            );
        }
    }

    private int calculateScore(
            AdvisorData data
    ) {
        if (data.currentEntries == 0) {
            return 0;
        }

        int score = 100;

        if (data.currentIncome <= 0) {
            score -= 35;

        } else if (data.saving < 0) {
            score -= 40;

        } else if (data.savingRate < 5) {
            score -= 25;

        } else if (data.savingRate < 10) {
            score -= 18;

        } else if (data.savingRate < 20) {
            score -= 10;

        } else if (data.savingRate >= 30) {
            score += 3;
        }

        if (data.largestCategoryPercentage >= 60) {
            score -= 15;

        } else if (data.largestCategoryPercentage >= 45) {
            score -= 8;
        }

        if (data.previousExpense > 0) {
            if (data.expenseDifference > 0
                    && data.expenseTrendPercentage >= 25) {

                score -= 12;

            } else if (data.expenseDifference > 0
                    && data.expenseTrendPercentage >= 10) {

                score -= 6;
            }
        }

        if (data.currentDay >= 10
                && data.currentEntries < 4) {

            score -= 5;
        }

        return Math.max(
                0,
                Math.min(
                        score,
                        100
                )
        );
    }

    private void showAdvisorData(
            AdvisorData data
    ) {
        txtScore.setText(
                String.valueOf(
                        data.score
                )
        );

        txtScore.setTextColor(
                getScoreColor(
                        data.score
                )
        );

        txtScoreLabel.setText(
                getScoreLabel(
                        data.score
                )
        );

        txtIncome.setText(
                formatMoney(
                        data.currentIncome
                )
        );

        txtExpense.setText(
                formatMoney(
                        data.currentExpense
                )
        );

        txtSaving.setText(
                formatSignedMoney(
                        data.saving
                )
        );

        if (data.saving > 0) {
            txtSaving.setTextColor(
                    getColorValue(
                            R.color.success
                    )
            );

        } else if (data.saving < 0) {
            txtSaving.setTextColor(
                    getColorValue(
                            R.color.expense
                    )
            );

        } else {
            txtSaving.setTextColor(
                    getColorValue(
                            R.color.app_text_secondary
                    )
            );
        }

        txtOverview.setText(
                buildOverviewText(
                        data
                )
        );

        recommendationContainer.removeAllViews();

        List<AdviceItem> adviceItems =
                buildAdvice(data);

        if (adviceItems.isEmpty()) {
            addStatusCard(
                    "No suggestion available",
                    "Add more income and expense entries to generate useful monthly insights.",
                    "i",
                    AdviceTone.NEUTRAL
            );

            return;
        }

        for (int index = 0;
             index < adviceItems.size();
             index++) {

            addAdviceCard(
                    adviceItems.get(index),
                    index + 1
            );
        }
    }

    private String buildOverviewText(
            AdvisorData data
    ) {
        String monthName =
                new SimpleDateFormat(
                        "MMMM yyyy",
                        Locale.ENGLISH
                ).format(
                        createMonthDate(
                                data.currentYear,
                                data.currentMonth
                        )
                );

        if (data.currentEntries == 0) {
            return "No income or expense entries were found for "
                    + monthName
                    + ". Add transactions to calculate your money health score.";
        }

        StringBuilder overview =
                new StringBuilder();

        overview.append("In ")
                .append(monthName)
                .append(", ")
                .append(data.currentEntries)
                .append(
                        data.currentEntries == 1
                                ? " transaction has"
                                : " transactions have"
                )
                .append(" been analysed. ");

        overview.append("Income is ")
                .append(
                        formatMoney(
                                data.currentIncome
                        )
                )
                .append(" and expense is ")
                .append(
                        formatMoney(
                                data.currentExpense
                        )
                )
                .append(". ");

        if (data.saving > 0) {
            overview.append("You currently have a positive saving of ")
                    .append(
                            formatMoney(
                                    data.saving
                            )
                    );

            if (data.currentIncome > 0) {
                overview.append(" (")
                        .append(
                                formatPercentage(
                                        data.savingRate
                                )
                        )
                        .append(" of income)");
            }

            overview.append(".");

        } else if (data.saving < 0) {
            overview.append("Expenses are above income by ")
                    .append(
                            formatMoney(
                                    Math.abs(
                                            data.saving
                                    )
                            )
                    )
                    .append(".");

        } else {
            overview.append(
                    "Income and expense are currently equal."
            );
        }

        return overview.toString();
    }

    private List<AdviceItem> buildAdvice(
            AdvisorData data
    ) {
        List<AdviceItem> items =
                new ArrayList<>();

        if (data.currentEntries == 0) {
            items.add(
                    new AdviceItem(
                            "Start tracking this month",
                            "Add income and expense entries to receive a personal score, savings analysis and category-based suggestions.",
                            "＋",
                            AdviceTone.INFO
                    )
            );

            return items;
        }

        /*
         * Income and saving position
         */

        if (data.currentIncome <= 0) {
            items.add(
                    new AdviceItem(
                            "Monthly income is missing",
                            "Expense entries are available, but no income has been recorded for this month. Add income to calculate an accurate savings rate.",
                            "!",
                            AdviceTone.WARNING
                    )
            );

        } else if (data.saving < 0) {
            items.add(
                    new AdviceItem(
                            "Spending is above income",
                            "Your expenses exceed income by "
                                    + formatMoney(
                                    Math.abs(
                                            data.saving
                                    )
                            )
                                    + ". Review flexible and non-essential categories first.",
                            "↓",
                            AdviceTone.EXPENSE
                    )
            );

        } else if (data.savingRate < 10) {
            items.add(
                    new AdviceItem(
                            "Savings margin is currently low",
                            "You have retained "
                                    + formatPercentage(
                                    data.savingRate
                            )
                                    + " of this month’s income. A small reduction in flexible spending could improve the monthly buffer.",
                            "₹",
                            AdviceTone.WARNING
                    )
            );

        } else if (data.savingRate < 20) {
            items.add(
                    new AdviceItem(
                            "Savings are moving positively",
                            "Your current savings rate is "
                                    + formatPercentage(
                                    data.savingRate
                            )
                                    + ". Continue tracking expenses to protect this positive margin.",
                            "✓",
                            AdviceTone.INFO
                    )
            );

        } else {
            items.add(
                    new AdviceItem(
                            "Healthy current savings rate",
                            "You have retained "
                                    + formatPercentage(
                                    data.savingRate
                            )
                                    + " of this month’s income, equal to "
                                    + formatMoney(
                                    data.saving
                            )
                                    + ".",
                            "✓",
                            AdviceTone.SUCCESS
                    )
            );
        }

        /*
         * Largest spending category
         */

        if (!data.largestCategoryName.isEmpty()
                && data.currentExpense > 0) {

            AdviceTone categoryTone;

            if (data.largestCategoryPercentage >= 50) {
                categoryTone =
                        AdviceTone.WARNING;

            } else {
                categoryTone =
                        AdviceTone.PURPLE;
            }

            items.add(
                    new AdviceItem(
                            "Top spending category",
                            data.largestCategoryName
                                    + " accounts for "
                                    + formatPercentage(
                                    data.largestCategoryPercentage
                            )
                                    + " of monthly expenses, totalling "
                                    + formatMoney(
                                    data.largestCategoryAmount
                            )
                                    + ".",
                            "◎",
                            categoryTone
                    )
            );
        }

        /*
         * Daily spending and projection
         */

        if (data.currentExpense > 0) {
            String projectionText =
                    "Average daily expense is "
                            + formatMoney(
                            data.dailyExpenseAverage
                    )
                            + ". At the same pace, estimated month-end expense is about "
                            + formatMoney(
                            data.projectedExpense
                    )
                            + ".";

            AdviceTone projectionTone =
                    data.currentIncome > 0
                            && data.projectedExpense
                            > data.currentIncome
                            ? AdviceTone.EXPENSE
                            : AdviceTone.INFO;

            items.add(
                    new AdviceItem(
                            "Daily spending pace",
                            projectionText,
                            "↗",
                            projectionTone
                    )
            );
        }

        /*
         * Previous month comparison
         */

        if (data.previousExpense > 0) {
            if (data.expenseDifference > 0) {
                items.add(
                        new AdviceItem(
                                "Expense trend has increased",
                                "Current month expense is "
                                        + formatPercentage(
                                        data.expenseTrendPercentage
                                )
                                        + " higher than the previous month, a difference of "
                                        + formatMoney(
                                        data.expenseDifference
                                )
                                        + ".",
                                "↑",
                                AdviceTone.WARNING
                        )
                );

            } else if (data.expenseDifference < 0) {
                items.add(
                        new AdviceItem(
                                "Expense trend has improved",
                                "Current month expense is "
                                        + formatPercentage(
                                        data.expenseTrendPercentage
                                )
                                        + " lower than the previous month, a reduction of "
                                        + formatMoney(
                                        Math.abs(
                                                data.expenseDifference
                                        )
                                )
                                        + ".",
                                "↓",
                                AdviceTone.SUCCESS
                        )
                );

            } else {
                items.add(
                        new AdviceItem(
                                "Expense level is unchanged",
                                "Your current expense matches the previous month at "
                                        + formatMoney(
                                        data.currentExpense
                                )
                                        + ".",
                                "＝",
                                AdviceTone.NEUTRAL
                        )
                );
            }

        } else {
            items.add(
                    new AdviceItem(
                            "Build a monthly comparison",
                            "Continue recording transactions so the advisor can compare this month with your previous spending pattern.",
                            "↔",
                            AdviceTone.NEUTRAL
                    )
            );
        }

        /*
         * Tracking consistency
         */

        if (data.currentDay >= 10
                && data.currentEntries < 4) {

            items.add(
                    new AdviceItem(
                            "More entries will improve accuracy",
                            "Only "
                                    + data.currentEntries
                                    + (
                                    data.currentEntries == 1
                                            ? " transaction has"
                                            : " transactions have"
                            )
                                    + " been recorded this month. Regular tracking creates a more reliable score.",
                            "≡",
                            AdviceTone.PURPLE
                    )
            );
        }

        return items;
    }

    private void addAdviceCard(
            AdviceItem item,
            int position
    ) {
        AdviceStyle style =
                getAdviceStyle(
                        item.tone
                );

        MaterialCardView cardView =
                new MaterialCardView(this);

        cardView.setCardBackgroundColor(
                style.surfaceColor
        );

        cardView.setRadius(
                dp(17)
        );

        cardView.setCardElevation(0);

        cardView.setStrokeWidth(
                dp(1)
        );

        cardView.setStrokeColor(
                style.outlineColor
        );

        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        cardParams.setMargins(
                0,
                dp(4),
                0,
                dp(6)
        );

        cardView.setLayoutParams(
                cardParams
        );

        LinearLayout content =
                new LinearLayout(this);

        content.setOrientation(
                LinearLayout.HORIZONTAL
        );

        content.setGravity(
                Gravity.TOP
        );

        content.setPadding(
                dp(13),
                dp(13),
                dp(13),
                dp(13)
        );

        TextView icon =
                createAdviceIcon(
                        item.icon,
                        style
                );

        content.addView(
                icon
        );

        LinearLayout textContainer =
                new LinearLayout(this);

        textContainer.setOrientation(
                LinearLayout.VERTICAL
        );

        LinearLayout.LayoutParams textParams =
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                );

        textParams.setMargins(
                dp(11),
                0,
                0,
                0
        );

        textContainer.setLayoutParams(
                textParams
        );

        TextView positionLabel =
                createText(
                        "INSIGHT "
                                + position,
                        8,
                        style.accentColor,
                        true
                );

        TextView title =
                createText(
                        item.title,
                        14,
                        getColorValue(
                                R.color.app_text_primary
                        ),
                        true
                );

        LinearLayout.LayoutParams titleParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        titleParams.setMargins(
                0,
                dp(3),
                0,
                0
        );

        title.setLayoutParams(
                titleParams
        );

        TextView description =
                createText(
                        item.description,
                        11,
                        getColorValue(
                                R.color.app_text_secondary
                        ),
                        false
                );

        description.setLineSpacing(
                dp(2),
                1f
        );

        LinearLayout.LayoutParams descriptionParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        descriptionParams.setMargins(
                0,
                dp(5),
                0,
                0
        );

        description.setLayoutParams(
                descriptionParams
        );

        textContainer.addView(
                positionLabel
        );

        textContainer.addView(
                title
        );

        textContainer.addView(
                description
        );

        content.addView(
                textContainer
        );

        cardView.addView(
                content
        );

        BubbleTouchAnimator.apply(
                cardView
        );

        recommendationContainer.addView(
                cardView
        );
    }

    private void addStatusCard(
            String title,
            String description,
            String iconText,
            AdviceTone tone
    ) {
        addAdviceCard(
                new AdviceItem(
                        title,
                        description,
                        iconText,
                        tone
                ),
                1
        );
    }

    private TextView createAdviceIcon(
            String symbol,
            AdviceStyle style
    ) {
        TextView icon =
                createText(
                        symbol,
                        symbol.length() > 1
                                ? 13
                                : 18,
                        style.accentColor,
                        true
                );

        icon.setGravity(
                Gravity.CENTER
        );

        icon.setBackground(
                createRoundedDrawable(
                        getColorValue(
                                R.color.app_surface
                        ),
                        style.outlineColor,
                        14
                )
        );

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        dp(46),
                        dp(46)
                );

        icon.setLayoutParams(
                params
        );

        return icon;
    }

    private void showAdvisorError() {
        txtScore.setText("0");

        txtScore.setTextColor(
                getColorValue(
                        R.color.app_text_secondary
                )
        );

        txtScoreLabel.setText(
                "Unable to calculate score"
        );

        txtIncome.setText("₹0.00");
        txtExpense.setText("₹0.00");
        txtSaving.setText("₹0.00");

        txtSaving.setTextColor(
                getColorValue(
                        R.color.app_text_secondary
                )
        );

        txtOverview.setText(
                "Finance data could not be analysed. Please reopen this screen and try again."
        );

        recommendationContainer.removeAllViews();

        addStatusCard(
                "Advisor data unavailable",
                "The saved transactions could not be read at this time.",
                "!",
                AdviceTone.EXPENSE
        );

        Toast.makeText(
                this,
                "Unable to load finance advisor",
                Toast.LENGTH_SHORT
        ).show();
    }

    private int getScoreColor(
            int score
    ) {
        if (score >= 80) {
            return getColorValue(
                    R.color.success
            );
        }

        if (score >= 60) {
            return getColorValue(
                    R.color.purple
            );
        }

        if (score >= 40) {
            return getColorValue(
                    R.color.warning
            );
        }

        if (score > 0) {
            return getColorValue(
                    R.color.expense
            );
        }

        return getColorValue(
                R.color.app_text_secondary
        );
    }

    private String getScoreLabel(
            int score
    ) {
        if (score >= 85) {
            return "Excellent money control";
        }

        if (score >= 70) {
            return "Healthy financial position";
        }

        if (score >= 55) {
            return "Good, with room to improve";
        }

        if (score >= 40) {
            return "Review current spending habits";
        }

        if (score > 0) {
            return "Financial attention is needed";
        }

        return "Add entries to calculate score";
    }

    private AdviceStyle getAdviceStyle(
            AdviceTone tone
    ) {
        switch (tone) {
            case SUCCESS:
                return new AdviceStyle(
                        getColorValue(
                                R.color.success
                        ),
                        getColorValue(
                                R.color.success_surface
                        ),
                        getColorValue(
                                R.color.success_outline
                        )
                );

            case EXPENSE:
                return new AdviceStyle(
                        getColorValue(
                                R.color.expense
                        ),
                        getColorValue(
                                R.color.expense_surface
                        ),
                        getColorValue(
                                R.color.expense_outline
                        )
                );

            case WARNING:
                return new AdviceStyle(
                        getColorValue(
                                R.color.warning
                        ),
                        getColorValue(
                                R.color.warning_surface
                        ),
                        getColorValue(
                                R.color.warning_outline
                        )
                );

            case INFO:
                return new AdviceStyle(
                        getColorValue(
                                R.color.secondary
                        ),
                        getColorValue(
                                R.color.info_surface
                        ),
                        getColorValue(
                                R.color.info_outline
                        )
                );

            case PURPLE:
                return new AdviceStyle(
                        getColorValue(
                                R.color.purple
                        ),
                        getColorValue(
                                R.color.purple_surface
                        ),
                        getColorValue(
                                R.color.purple_outline
                        )
                );

            case NEUTRAL:
            default:
                return new AdviceStyle(
                        getColorValue(
                                R.color.app_text_secondary
                        ),
                        getColorValue(
                                R.color.app_surface_soft
                        ),
                        getColorValue(
                                R.color.app_outline_soft
                        )
                );
        }
    }

    private String getLargestCategoryName(
            LinkedHashMap<String, Double> categoryExpenses
    ) {
        String largestCategory =
                "";

        double largestAmount =
                0;

        for (Map.Entry<String, Double> entry
                : categoryExpenses.entrySet()) {

            if (entry.getValue() != null
                    && entry.getValue() > largestAmount) {

                largestAmount =
                        entry.getValue();

                largestCategory =
                        entry.getKey();
            }
        }

        return largestCategory;
    }

    private double getLargestCategoryAmount(
            LinkedHashMap<String, Double> categoryExpenses
    ) {
        double largestAmount =
                0;

        for (Double amount
                : categoryExpenses.values()) {

            if (amount != null) {
                largestAmount =
                        Math.max(
                                largestAmount,
                                amount
                        );
            }
        }

        return largestAmount;
    }

    private Date createMonthDate(
            int year,
            int month
    ) {
        Calendar calendar =
                Calendar.getInstance();

        calendar.set(
                Calendar.YEAR,
                year
        );

        calendar.set(
                Calendar.MONTH,
                month
        );

        calendar.set(
                Calendar.DAY_OF_MONTH,
                1
        );

        clearTime(calendar);

        return calendar.getTime();
    }

    private Date parseDate(
            String dateText
    ) {
        if (dateText == null
                || dateText.trim().isEmpty()) {

            return null;
        }

        String cleanDate =
                dateText.trim();

        String[] patterns = {
                "yyyy-MM-dd HH:mm:ss.SSS",
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd HH:mm",
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd'T'HH:mm:ss.SSS",
                "yyyy-MM-dd'T'HH:mm:ss",
                "yyyy-MM-dd'T'HH:mm",
                "yyyy-MM-dd",
                "yyyy/MM/dd HH:mm:ss",
                "yyyy/MM/dd HH:mm",
                "yyyy/MM/dd",
                "dd/MM/yyyy HH:mm:ss",
                "dd/MM/yyyy HH:mm",
                "dd/MM/yyyy",
                "dd-MM-yyyy HH:mm:ss",
                "dd-MM-yyyy HH:mm",
                "dd-MM-yyyy",
                "dd MMM yyyy HH:mm:ss",
                "dd MMM yyyy HH:mm",
                "dd MMM yyyy",
                "dd MMMM yyyy HH:mm:ss",
                "dd MMMM yyyy HH:mm",
                "dd MMMM yyyy"
        };

        for (String pattern : patterns) {
            Date parsedDate =
                    parseStrictDate(
                            cleanDate,
                            pattern
                    );

            if (parsedDate != null) {
                return parsedDate;
            }
        }

        return null;
    }

    private Date parseStrictDate(
            String value,
            String pattern
    ) {
        try {
            SimpleDateFormat formatter =
                    new SimpleDateFormat(
                            pattern,
                            Locale.ENGLISH
                    );

            formatter.setLenient(false);

            ParsePosition parsePosition =
                    new ParsePosition(0);

            Date parsedDate =
                    formatter.parse(
                            value,
                            parsePosition
                    );

            if (parsedDate == null
                    || parsePosition.getIndex()
                    != value.length()) {

                return null;
            }

            return parsedDate;

        } catch (Exception exception) {
            return null;
        }
    }

    private String formatMoney(
            double amount
    ) {
        NumberFormat formatter =
                NumberFormat.getNumberInstance(
                        new Locale(
                                "en",
                                "IN"
                        )
                );

        formatter.setMinimumFractionDigits(2);
        formatter.setMaximumFractionDigits(2);

        return "₹"
                + formatter.format(
                amount
        );
    }

    private String formatSignedMoney(
            double amount
    ) {
        if (amount > 0) {
            return "+"
                    + formatMoney(amount);
        }

        if (amount < 0) {
            return "-"
                    + formatMoney(
                    Math.abs(amount)
            );
        }

        return formatMoney(0);
    }

    private String formatPercentage(
            double percentage
    ) {
        return String.format(
                Locale.US,
                "%.1f%%",
                percentage
        );
    }

    private TextView createText(
            String text,
            float textSize,
            int textColor,
            boolean bold
    ) {
        TextView textView =
                new TextView(this);

        textView.setText(text);
        textView.setTextSize(textSize);
        textView.setTextColor(textColor);

        if (bold) {
            textView.setTypeface(
                    Typeface.DEFAULT,
                    Typeface.BOLD
            );
        }

        return textView;
    }

    private GradientDrawable createRoundedDrawable(
            int backgroundColor,
            int outlineColor,
            int radiusDp
    ) {
        GradientDrawable drawable =
                new GradientDrawable();

        drawable.setColor(
                backgroundColor
        );

        drawable.setStroke(
                dp(1),
                outlineColor
        );

        drawable.setCornerRadius(
                dp(radiusDp)
        );

        return drawable;
    }

    private String safeText(
            String value,
            String fallback
    ) {
        if (value == null
                || value.trim().isEmpty()) {

            return fallback;
        }

        return value.trim();
    }

    private void clearTime(
            Calendar calendar
    ) {
        calendar.set(
                Calendar.HOUR_OF_DAY,
                0
        );

        calendar.set(
                Calendar.MINUTE,
                0
        );

        calendar.set(
                Calendar.SECOND,
                0
        );

        calendar.set(
                Calendar.MILLISECOND,
                0
        );
    }

    private int getColorValue(
            int colorResource
    ) {
        return ContextCompat.getColor(
                this,
                colorResource
        );
    }

    private int dp(
            int value
    ) {
        return Math.round(
                value
                        * getResources()
                        .getDisplayMetrics()
                        .density
        );
    }

    private enum AdviceTone {
        SUCCESS,
        EXPENSE,
        WARNING,
        INFO,
        PURPLE,
        NEUTRAL
    }

    private static class AdviceStyle {

        private final int accentColor;
        private final int surfaceColor;
        private final int outlineColor;

        private AdviceStyle(
                int accentColor,
                int surfaceColor,
                int outlineColor
        ) {
            this.accentColor =
                    accentColor;

            this.surfaceColor =
                    surfaceColor;

            this.outlineColor =
                    outlineColor;
        }
    }

    private static class AdvisorData {

        private double currentIncome;
        private double currentExpense;
        private double saving;
        private double savingRate;

        private double previousIncome;
        private double previousExpense;

        private double expenseDifference;
        private double expenseTrendPercentage;

        private double dailyExpenseAverage;
        private double projectedExpense;

        private String largestCategoryName =
                "";

        private double largestCategoryAmount;
        private double largestCategoryPercentage;

        private int currentIncomeEntries;
        private int currentExpenseEntries;
        private int currentEntries;
        private int previousEntries;

        private int currentYear;
        private int currentMonth;
        private int currentDay;
        private int daysInCurrentMonth;

        private int score;

        private final LinkedHashMap<String, Double> categoryExpenses =
                new LinkedHashMap<>();
    }

    private static class AdviceItem {

        private final String title;
        private final String description;
        private final String icon;
        private final AdviceTone tone;

        private AdviceItem(
                String title,
                String description,
                String icon,
                AdviceTone tone
        ) {
            this.title =
                    title;

            this.description =
                    description;

            this.icon =
                    icon;

            this.tone =
                    tone;
        }
    }
}