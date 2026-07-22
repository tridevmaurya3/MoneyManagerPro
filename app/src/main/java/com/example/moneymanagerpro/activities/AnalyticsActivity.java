package com.example.moneymanagerpro.activities;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.database.DatabaseClient;
import com.example.moneymanagerpro.model.CategoryTotal;
import com.google.android.material.card.MaterialCardView;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class AnalyticsActivity extends AppCompatActivity {

    private Spinner spinnerAnalyticsPeriod;

    private TextView txtAnalyticsIncome;
    private TextView txtAnalyticsExpense;
    private TextView txtNetCashFlow;
    private TextView txtNoAnalytics;

    private LinearLayout insightContainer;
    private LinearLayout incomeExpenseChartContainer;
    private LinearLayout categoryChartContainer;

    private final int[] categoryAccentColors = {
            Color.parseColor("#6C63FF"),
            Color.parseColor("#1565C0"),
            Color.parseColor("#00897B"),
            Color.parseColor("#F57C00"),
            Color.parseColor("#C62828"),
            Color.parseColor("#8E24AA"),
            Color.parseColor("#00838F"),
            Color.parseColor("#5D4037")
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_analytics);

        bindViews();
        setupPeriodSpinner();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (spinnerAnalyticsPeriod != null
                && spinnerAnalyticsPeriod.getSelectedItem() != null) {

            loadAnalytics();
        }
    }

    private void bindViews() {
        spinnerAnalyticsPeriod =
                findViewById(
                        R.id.spinnerAnalyticsPeriod
                );

        txtAnalyticsIncome =
                findViewById(
                        R.id.txtAnalyticsIncome
                );

        txtAnalyticsExpense =
                findViewById(
                        R.id.txtAnalyticsExpense
                );

        txtNetCashFlow =
                findViewById(
                        R.id.txtNetCashFlow
                );

        txtNoAnalytics =
                findViewById(
                        R.id.txtNoAnalytics
                );

        insightContainer =
                findViewById(
                        R.id.insightContainer
                );

        incomeExpenseChartContainer =
                findViewById(
                        R.id.incomeExpenseChartContainer
                );

        categoryChartContainer =
                findViewById(
                        R.id.categoryChartContainer
                );
    }

    private void setupPeriodSpinner() {
        String[] options = {
                "This Month",
                "This Year"
        };

        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_spinner_item,
                        options
                );

        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        spinnerAnalyticsPeriod.setAdapter(adapter);

        spinnerAnalyticsPeriod.setSelection(0);

        spinnerAnalyticsPeriod.setOnItemSelectedListener(
                new android.widget.AdapterView.OnItemSelectedListener() {

                    @Override
                    public void onItemSelected(
                            android.widget.AdapterView<?> parent,
                            View view,
                            int position,
                            long id
                    ) {
                        loadAnalytics();
                    }

                    @Override
                    public void onNothingSelected(
                            android.widget.AdapterView<?> parent
                    ) {
                        // No action required.
                    }
                }
        );
    }

    private void loadAnalytics() {
        String[] range =
                getSelectedDateRange();

        String startDate =
                range[0];

        String endDate =
                range[1];

        new Thread(() -> {
            try {
                double income =
                        DatabaseClient
                                .getInstance(
                                        getApplicationContext()
                                )
                                .getAppDatabase()
                                .transactionDao()
                                .getTotalAmountByTypeForPeriod(
                                        "INCOME",
                                        startDate,
                                        endDate
                                );

                double expense =
                        DatabaseClient
                                .getInstance(
                                        getApplicationContext()
                                )
                                .getAppDatabase()
                                .transactionDao()
                                .getTotalAmountByTypeForPeriod(
                                        "EXPENSE",
                                        startDate,
                                        endDate
                                );

                List<CategoryTotal> categoryTotals =
                        DatabaseClient
                                .getInstance(
                                        getApplicationContext()
                                )
                                .getAppDatabase()
                                .transactionDao()
                                .getCategoryTotalsForPeriod(
                                        "EXPENSE",
                                        startDate,
                                        endDate
                                );

                List<CategoryTotal> validCategoryTotals =
                        prepareCategoryTotals(
                                categoryTotals
                        );

                runOnUiThread(() ->
                        showAnalytics(
                                income,
                                expense,
                                validCategoryTotals
                        )
                );

            } catch (Exception exception) {
                runOnUiThread(() -> {
                    clearAnalytics();

                    Toast.makeText(
                            AnalyticsActivity.this,
                            "Unable to load analytics",
                            Toast.LENGTH_SHORT
                    ).show();
                });
            }
        }).start();
    }

    private List<CategoryTotal> prepareCategoryTotals(
            List<CategoryTotal> categoryTotals
    ) {
        List<CategoryTotal> validTotals =
                new ArrayList<>();

        if (categoryTotals == null) {
            return validTotals;
        }

        for (CategoryTotal categoryTotal :
                categoryTotals) {

            if (categoryTotal != null
                    && categoryTotal.total > 0) {

                validTotals.add(categoryTotal);
            }
        }

        Collections.sort(
                validTotals,
                (first, second) ->
                        Double.compare(
                                second.total,
                                first.total
                        )
        );

        return validTotals;
    }

    private void showAnalytics(
            double income,
            double expense,
            List<CategoryTotal> categoryTotals
    ) {
        double netCashFlow =
                income - expense;

        txtAnalyticsIncome.setText(
                formatAmount(income)
        );

        txtAnalyticsExpense.setText(
                formatAmount(expense)
        );

        txtNetCashFlow.setText(
                formatAmount(netCashFlow)
        );

        txtAnalyticsIncome.setTextColor(
                getColorValue(
                        R.color.success
                )
        );

        txtAnalyticsExpense.setTextColor(
                getColorValue(
                        R.color.expense
                )
        );

        txtNetCashFlow.setTextColor(
                getColorValue(
                        netCashFlow >= 0
                                ? R.color.success
                                : R.color.expense
                )
        );

        showSmartInsights(
                income,
                expense,
                categoryTotals
        );

        showIncomeExpenseChart(
                income,
                expense
        );

        showCategoryChart(
                categoryTotals,
                expense
        );

        boolean hasNoData =
                income == 0
                        && expense == 0;

        txtNoAnalytics.setVisibility(
                hasNoData
                        ? View.VISIBLE
                        : View.GONE
        );
    }

    private void clearAnalytics() {
        txtAnalyticsIncome.setText("₹0.00");
        txtAnalyticsExpense.setText("₹0.00");
        txtNetCashFlow.setText("₹0.00");

        insightContainer.removeAllViews();
        incomeExpenseChartContainer.removeAllViews();
        categoryChartContainer.removeAllViews();

        txtNoAnalytics.setVisibility(
                View.VISIBLE
        );
    }

    private void showSmartInsights(
            double income,
            double expense,
            List<CategoryTotal> categoryTotals
    ) {
        insightContainer.removeAllViews();

        if (income == 0 && expense == 0) {
            addInsight(
                    "i",
                    "No financial data yet",
                    "Income या Expense add करने के बाद इस अवधि के smart insights दिखाई देंगे।",
                    getColorValue(R.color.secondary),
                    getColorValue(R.color.info_surface),
                    getColorValue(R.color.info_outline)
            );

            return;
        }

        double netCashFlow =
                income - expense;

        if (netCashFlow >= 0) {
            addInsight(
                    "✓",
                    "Positive cash flow",
                    "इस अवधि में आपने "
                            + formatAmount(netCashFlow)
                            + " की positive saving बनाई है।",
                    getColorValue(R.color.success),
                    getColorValue(R.color.success_surface),
                    getColorValue(R.color.success_outline)
            );

        } else {
            addInsight(
                    "!",
                    "Spending warning",
                    "इस अवधि में Expense, Income से "
                            + formatAmount(
                            Math.abs(netCashFlow)
                    )
                            + " अधिक है।",
                    getColorValue(R.color.expense),
                    getColorValue(R.color.expense_surface),
                    getColorValue(R.color.expense_outline)
            );
        }

        if (income > 0) {
            double savingsRate =
                    (netCashFlow / income) * 100;

            if (savingsRate >= 0) {
                addInsight(
                        "%",
                        "Savings rate",
                        "इस अवधि में आपकी savings rate लगभग "
                                + String.format(
                                Locale.getDefault(),
                                "%.0f%%",
                                savingsRate
                        )
                                + " है।",
                        Color.parseColor("#00838F"),
                        Color.parseColor("#E5F7F7"),
                        Color.parseColor("#A8DDDD")
                );

            } else {
                addInsight(
                        "%",
                        "Negative savings rate",
                        "इस अवधि में खर्च Income से अधिक होने के कारण savings rate negative है।",
                        getColorValue(R.color.expense),
                        getColorValue(R.color.expense_surface),
                        getColorValue(R.color.expense_outline)
                );
            }
        }

        if (categoryTotals != null
                && !categoryTotals.isEmpty()) {

            CategoryTotal topCategory =
                    categoryTotals.get(0);

            String categoryName =
                    safeText(
                            topCategory.category
                    );

            addInsight(
                    "◎",
                    "Top spending category",
                    categoryName
                            + " में सबसे अधिक "
                            + formatAmount(
                            topCategory.total
                    )
                            + " खर्च हुआ है।",
                    getColorValue(R.color.purple),
                    getColorValue(R.color.purple_surface),
                    getColorValue(R.color.purple_outline)
            );

            if (expense > 0) {
                double categoryPercentage =
                        (
                                topCategory.total
                                        / expense
                        ) * 100;

                if (categoryPercentage >= 50) {
                    addInsight(
                            "!",
                            "High category concentration",
                            categoryName
                                    + " आपके total expense का लगभग "
                                    + String.format(
                                    Locale.getDefault(),
                                    "%.0f%%",
                                    categoryPercentage
                            )
                                    + " है। इस category को review करें।",
                            getColorValue(R.color.warning),
                            getColorValue(R.color.warning_surface),
                            getColorValue(R.color.warning_outline)
                    );
                }
            }
        }

        int dayCount =
                getSelectedPeriodDayCount();

        double averageDailyExpense =
                expense
                        / Math.max(
                        dayCount,
                        1
                );

        addInsight(
                "↗",
                "Average daily expense",
                "इस अवधि में आपका average daily expense "
                        + formatAmount(
                        averageDailyExpense
                )
                        + " है।",
                getColorValue(R.color.secondary),
                getColorValue(R.color.info_surface),
                getColorValue(R.color.info_outline)
        );
    }

    private void addInsight(
            String iconText,
            String title,
            String message,
            int accentColor,
            int backgroundColor,
            int outlineColor
    ) {
        MaterialCardView card =
                new MaterialCardView(this);

        card.setCardBackgroundColor(
                backgroundColor
        );

        card.setCardElevation(0);
        card.setRadius(dp(15));
        card.setStrokeWidth(dp(1));
        card.setStrokeColor(outlineColor);

        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        cardParams.setMargins(
                0,
                dp(5),
                0,
                dp(5)
        );

        card.setLayoutParams(cardParams);

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
                dp(12),
                dp(13),
                dp(12)
        );

        TextView iconView =
                createIconView(
                        iconText,
                        accentColor,
                        backgroundColor,
                        outlineColor
                );

        content.addView(iconView);

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

        TextView titleView =
                createText(
                        title,
                        14,
                        accentColor,
                        true
                );

        TextView messageView =
                createText(
                        message,
                        12,
                        getColorValue(
                                R.color.app_text_secondary
                        ),
                        false
                );

        messageView.setLineSpacing(
                dp(2),
                1f
        );

        LinearLayout.LayoutParams messageParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        messageParams.setMargins(
                0,
                dp(4),
                0,
                0
        );

        messageView.setLayoutParams(
                messageParams
        );

        textContainer.addView(titleView);
        textContainer.addView(messageView);

        content.addView(textContainer);

        card.addView(content);

        insightContainer.addView(card);
    }

    private TextView createIconView(
            String iconText,
            int accentColor,
            int backgroundColor,
            int outlineColor
    ) {
        TextView icon =
                new TextView(this);

        icon.setText(iconText);
        icon.setTextColor(accentColor);
        icon.setTextSize(16);

        icon.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        icon.setGravity(
                Gravity.CENTER
        );

        GradientDrawable background =
                new GradientDrawable();

        background.setColor(
                backgroundColor
        );

        background.setStroke(
                dp(1),
                outlineColor
        );

        background.setCornerRadius(
                dp(12)
        );

        icon.setBackground(background);

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        dp(38),
                        dp(38)
                );

        icon.setLayoutParams(params);

        return icon;
    }

    private void showIncomeExpenseChart(
            double income,
            double expense
    ) {
        incomeExpenseChartContainer.removeAllViews();

        double maximumValue =
                Math.max(
                        income,
                        expense
                );

        addAmountBar(
                incomeExpenseChartContainer,
                "Income",
                "Total money received",
                income,
                maximumValue,
                getColorValue(R.color.success),
                getColorValue(R.color.success_surface),
                getColorValue(R.color.success_outline),
                "↓"
        );

        addAmountBar(
                incomeExpenseChartContainer,
                "Expense",
                "Total money spent",
                expense,
                maximumValue,
                getColorValue(R.color.expense),
                getColorValue(R.color.expense_surface),
                getColorValue(R.color.expense_outline),
                "↑"
        );
    }

    private void showCategoryChart(
            List<CategoryTotal> categoryTotals,
            double totalExpense
    ) {
        categoryChartContainer.removeAllViews();

        if (categoryTotals == null
                || categoryTotals.isEmpty()) {

            addEmptyChartMessage(
                    categoryChartContainer,
                    "इस अवधि में कोई expense category उपलब्ध नहीं है।"
            );

            return;
        }

        double maximumValue = 0;

        for (CategoryTotal categoryTotal :
                categoryTotals) {

            maximumValue =
                    Math.max(
                            maximumValue,
                            categoryTotal.total
                    );
        }

        for (int index = 0;
             index < categoryTotals.size();
             index++) {

            CategoryTotal categoryTotal =
                    categoryTotals.get(index);

            int accentColor =
                    categoryAccentColors[
                            index
                                    % categoryAccentColors.length
                            ];

            int share =
                    totalExpense <= 0
                            ? 0
                            : (int) Math.round(
                            (
                                    categoryTotal.total
                                    / totalExpense
                            ) * 100
                    );

            addAmountBar(
                    categoryChartContainer,
                    safeText(
                            categoryTotal.category
                    ),
                    share
                            + "% of total expenses",
                    categoryTotal.total,
                    maximumValue,
                    accentColor,
                    createTranslucentColor(
                            accentColor,
                            16
                    ),
                    createTranslucentColor(
                            accentColor,
                            60
                    ),
                    String.valueOf(
                            index + 1
                    )
            );
        }
    }

    private void addEmptyChartMessage(
            LinearLayout container,
            String message
    ) {
        TextView emptyText =
                createText(
                        message,
                        13,
                        getColorValue(
                                R.color.app_text_secondary
                        ),
                        false
                );

        emptyText.setGravity(
                Gravity.CENTER
        );

        emptyText.setPadding(
                dp(12),
                dp(24),
                dp(12),
                dp(24)
        );

        container.addView(emptyText);
    }

    private void addAmountBar(
            LinearLayout container,
            String title,
            String subtitle,
            double amount,
            double maximumAmount,
            int accentColor,
            int backgroundColor,
            int outlineColor,
            String symbol
    ) {
        MaterialCardView card =
                new MaterialCardView(this);

        card.setCardBackgroundColor(
                getColorValue(
                        R.color.app_surface_soft
                )
        );

        card.setCardElevation(0);
        card.setRadius(dp(15));
        card.setStrokeWidth(dp(1));

        card.setStrokeColor(
                outlineColor
        );

        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        cardParams.setMargins(
                0,
                dp(6),
                0,
                dp(6)
        );

        card.setLayoutParams(cardParams);

        LinearLayout content =
                new LinearLayout(this);

        content.setOrientation(
                LinearLayout.VERTICAL
        );

        content.setPadding(
                dp(13),
                dp(12),
                dp(13),
                dp(12)
        );

        LinearLayout headerRow =
                new LinearLayout(this);

        headerRow.setOrientation(
                LinearLayout.HORIZONTAL
        );

        headerRow.setGravity(
                Gravity.CENTER_VERTICAL
        );

        TextView symbolView =
                createBarSymbol(
                        symbol,
                        accentColor,
                        backgroundColor,
                        outlineColor
                );

        headerRow.addView(symbolView);

        LinearLayout titleContainer =
                new LinearLayout(this);

        titleContainer.setOrientation(
                LinearLayout.VERTICAL
        );

        LinearLayout.LayoutParams titleParams =
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                );

        titleParams.setMargins(
                dp(10),
                0,
                dp(8),
                0
        );

        titleContainer.setLayoutParams(
                titleParams
        );

        TextView titleView =
                createText(
                        title,
                        14,
                        getColorValue(
                                R.color.app_text_primary
                        ),
                        true
                );

        TextView subtitleView =
                createText(
                        subtitle,
                        10,
                        getColorValue(
                                R.color.app_text_secondary
                        ),
                        false
                );

        LinearLayout.LayoutParams subtitleParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        subtitleParams.setMargins(
                0,
                dp(2),
                0,
                0
        );

        subtitleView.setLayoutParams(
                subtitleParams
        );

        titleContainer.addView(titleView);
        titleContainer.addView(subtitleView);

        headerRow.addView(titleContainer);

        TextView amountView =
                createText(
                        formatAmount(amount),
                        14,
                        accentColor,
                        true
                );

        amountView.setGravity(
                Gravity.END
        );

        headerRow.addView(amountView);

        content.addView(headerRow);

        LinearLayout track =
                new LinearLayout(this);

        track.setOrientation(
                LinearLayout.HORIZONTAL
        );

        GradientDrawable trackBackground =
                new GradientDrawable();

        trackBackground.setColor(
                getColorValue(
                        R.color.app_outline_soft
                )
        );

        trackBackground.setCornerRadius(
                dp(20)
        );

        track.setBackground(
                trackBackground
        );

        LinearLayout.LayoutParams trackParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(9)
                );

        trackParams.setMargins(
                0,
                dp(11),
                0,
                0
        );

        track.setLayoutParams(
                trackParams
        );

        double rawRatio =
                maximumAmount <= 0
                        ? 0
                        : amount / maximumAmount;

        float ratio =
                (float) Math.max(
                        0,
                        Math.min(
                                rawRatio,
                                1
                        )
                );

        if (amount > 0 && ratio < 0.04f) {
            ratio = 0.04f;
        }

        View amountBar =
                new View(this);

        GradientDrawable barBackground =
                new GradientDrawable();

        barBackground.setColor(
                accentColor
        );

        barBackground.setCornerRadius(
                dp(20)
        );

        amountBar.setBackground(
                barBackground
        );

        LinearLayout.LayoutParams barParams =
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ratio
                );

        amountBar.setLayoutParams(
                barParams
        );

        track.addView(amountBar);

        View remainingSpace =
                new View(this);

        LinearLayout.LayoutParams remainingParams =
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Math.max(
                                1f - ratio,
                                0f
                        )
                );

        remainingSpace.setLayoutParams(
                remainingParams
        );

        track.addView(remainingSpace);

        content.addView(track);

        card.addView(content);

        container.addView(card);
    }

    private TextView createBarSymbol(
            String symbol,
            int accentColor,
            int backgroundColor,
            int outlineColor
    ) {
        TextView symbolView =
                new TextView(this);

        symbolView.setText(symbol);
        symbolView.setTextColor(accentColor);
        symbolView.setTextSize(13);

        symbolView.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        symbolView.setGravity(
                Gravity.CENTER
        );

        GradientDrawable background =
                new GradientDrawable();

        background.setColor(
                backgroundColor
        );

        background.setStroke(
                dp(1),
                outlineColor
        );

        background.setCornerRadius(
                dp(11)
        );

        symbolView.setBackground(
                background
        );

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        dp(36),
                        dp(36)
                );

        symbolView.setLayoutParams(
                params
        );

        return symbolView;
    }

    private String[] getSelectedDateRange() {
        Calendar startCalendar =
                Calendar.getInstance();

        Calendar endCalendar =
                Calendar.getInstance();

        String selectedPeriod =
                getSelectedPeriod();

        if ("This Year".equals(
                selectedPeriod
        )) {
            startCalendar.set(
                    Calendar.MONTH,
                    Calendar.JANUARY
            );

            startCalendar.set(
                    Calendar.DAY_OF_MONTH,
                    1
            );

        } else {
            startCalendar.set(
                    Calendar.DAY_OF_MONTH,
                    1
            );
        }

        startCalendar.set(
                Calendar.HOUR_OF_DAY,
                0
        );

        startCalendar.set(
                Calendar.MINUTE,
                0
        );

        startCalendar.set(
                Calendar.SECOND,
                0
        );

        startCalendar.set(
                Calendar.MILLISECOND,
                0
        );

        endCalendar.set(
                Calendar.HOUR_OF_DAY,
                23
        );

        endCalendar.set(
                Calendar.MINUTE,
                59
        );

        endCalendar.set(
                Calendar.SECOND,
                59
        );

        endCalendar.set(
                Calendar.MILLISECOND,
                999
        );

        SimpleDateFormat formatter =
                new SimpleDateFormat(
                        "yyyy-MM-dd HH:mm",
                        Locale.US
                );

        return new String[]{
                formatter.format(
                        startCalendar.getTime()
                ),
                formatter.format(
                        endCalendar.getTime()
                )
        };
    }

    private int getSelectedPeriodDayCount() {
        Calendar calendar =
                Calendar.getInstance();

        if ("This Year".equals(
                getSelectedPeriod()
        )) {
            return calendar.get(
                    Calendar.DAY_OF_YEAR
            );
        }

        return calendar.get(
                Calendar.DAY_OF_MONTH
        );
    }

    private String getSelectedPeriod() {
        if (spinnerAnalyticsPeriod == null
                || spinnerAnalyticsPeriod
                .getSelectedItem() == null) {

            return "This Month";
        }

        return spinnerAnalyticsPeriod
                .getSelectedItem()
                .toString();
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

    private String formatAmount(
            double amount
    ) {
        NumberFormat formatter =
                NumberFormat.getNumberInstance(
                        new Locale("en", "IN")
                );

        formatter.setMinimumFractionDigits(2);
        formatter.setMaximumFractionDigits(2);

        return "₹"
                + formatter.format(amount);
    }

    private String safeText(
            String value
    ) {
        if (value == null
                || value.trim().isEmpty()) {

            return "Other";
        }

        return value.trim();
    }

    private int createTranslucentColor(
            int baseColor,
            int alpha
    ) {
        return Color.argb(
                alpha,
                Color.red(baseColor),
                Color.green(baseColor),
                Color.blue(baseColor)
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
}