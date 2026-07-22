package com.example.moneymanagerpro.activities;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.charts.FinanceChartView;
import com.example.moneymanagerpro.database.DatabaseClient;
import com.example.moneymanagerpro.model.Transaction;
import com.example.moneymanagerpro.utils.BubbleTouchAnimator;
import com.google.android.material.button.MaterialButton;

import android.widget.TextView;
import android.widget.Toast;

import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ChartsActivity extends AppCompatActivity {

    private TextView txtTotalIncome;
    private TextView txtTotalExpense;
    private TextView txtNetBalance;
    private TextView txtChartTitle;
    private TextView txtInsight;

    private MaterialButton btnCategoryChart;
    private MaterialButton btnMonthlyChart;

    private FinanceChartView financeChartView;

    private AnalyticsData analyticsData =
            new AnalyticsData();

    private boolean isCategoryChart = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_charts);

        bindViews();
        prepareScreen();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadAnalytics();
    }

    private void bindViews() {
        txtTotalIncome =
                findViewById(
                        R.id.txtTotalIncome
                );

        txtTotalExpense =
                findViewById(
                        R.id.txtTotalExpense
                );

        txtNetBalance =
                findViewById(
                        R.id.txtNetBalance
                );

        txtChartTitle =
                findViewById(
                        R.id.txtChartTitle
                );

        txtInsight =
                findViewById(
                        R.id.txtInsight
                );

        btnCategoryChart =
                findViewById(
                        R.id.btnCategoryChart
                );

        btnMonthlyChart =
                findViewById(
                        R.id.btnMonthlyChart
                );

        financeChartView =
                findViewById(
                        R.id.financeChartView
                );
    }

    private void prepareScreen() {
        btnCategoryChart.setOnClickListener(
                view -> {
                    isCategoryChart = true;
                    showSelectedChart();
                }
        );

        btnMonthlyChart.setOnClickListener(
                view -> {
                    isCategoryChart = false;
                    showSelectedChart();
                }
        );

        BubbleTouchAnimator.apply(
                btnCategoryChart
        );

        BubbleTouchAnimator.apply(
                btnMonthlyChart
        );

        updateChartButtons();
    }

    private void loadAnalytics() {
        setChartLoadingState(true);

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

                AnalyticsData result =
                        calculateAnalytics(
                                transactions
                        );

                runOnUiThread(() -> {
                    analyticsData = result;

                    updateSummaryCards();
                    showSelectedChart();
                    setChartLoadingState(false);
                });

            } catch (Exception exception) {
                runOnUiThread(() -> {
                    analyticsData =
                            new AnalyticsData();

                    updateSummaryCards();
                    showSelectedChart();
                    setChartLoadingState(false);

                    Toast.makeText(
                            ChartsActivity.this,
                            "Unable to load chart data",
                            Toast.LENGTH_SHORT
                    ).show();
                });
            }
        }).start();
    }

    private void setChartLoadingState(
            boolean loading
    ) {
        btnCategoryChart.setEnabled(!loading);
        btnMonthlyChart.setEnabled(!loading);

        if (loading) {
            txtChartTitle.setText(
                    "Loading Financial Data..."
            );

            txtInsight.setText(
                    "Please wait while your transactions are analysed."
            );

            txtInsight.setTextColor(
                    getColorValue(
                            R.color.app_text_secondary
                    )
            );
        }
    }

    private AnalyticsData calculateAnalytics(
            List<Transaction> transactions
    ) {
        AnalyticsData result =
                new AnalyticsData();

        Calendar currentCalendar =
                Calendar.getInstance();

        int currentYear =
                currentCalendar.get(
                        Calendar.YEAR
                );

        int currentMonthNumber =
                currentCalendar.get(
                        Calendar.MONTH
                );

        List<Calendar> lastSixMonths =
                createLastSixMonths();

        for (Calendar calendar :
                lastSixMonths) {

            String key =
                    monthKey(calendar);

            String visibleMonth =
                    new SimpleDateFormat(
                            "MMM",
                            Locale.ENGLISH
                    ).format(
                            calendar.getTime()
                    );

            result.monthLabels.add(
                    visibleMonth
            );

            result.monthIncome.put(
                    key,
                    0d
            );

            result.monthExpense.put(
                    key,
                    0d
            );
        }

        if (transactions != null) {
            for (Transaction transaction :
                    transactions) {

                if (transaction == null) {
                    continue;
                }

                String type =
                        safeText(
                                transaction.getType(),
                                ""
                        );

                double amount =
                        Math.max(
                                transaction.getAmount(),
                                0
                        );

                if ("INCOME".equalsIgnoreCase(
                        type
                )) {
                    result.totalIncome += amount;

                } else if ("EXPENSE".equalsIgnoreCase(
                        type
                )) {
                    result.totalExpense += amount;

                } else {
                    continue;
                }

                Date transactionDate =
                        parseDate(
                                transaction.getDate()
                        );

                if (transactionDate == null) {
                    continue;
                }

                Calendar dateCalendar =
                        Calendar.getInstance();

                dateCalendar.setTime(
                        transactionDate
                );

                String monthKey =
                        monthKey(
                                dateCalendar
                        );

                if ("INCOME".equalsIgnoreCase(
                        type
                )
                        && result.monthIncome
                        .containsKey(monthKey)) {

                    double currentIncome =
                            safeMapAmount(
                                    result.monthIncome,
                                    monthKey
                            );

                    result.monthIncome.put(
                            monthKey,
                            currentIncome + amount
                    );
                }

                if ("EXPENSE".equalsIgnoreCase(
                        type
                )
                        && result.monthExpense
                        .containsKey(monthKey)) {

                    double currentExpense =
                            safeMapAmount(
                                    result.monthExpense,
                                    monthKey
                            );

                    result.monthExpense.put(
                            monthKey,
                            currentExpense + amount
                    );
                }

                boolean isCurrentMonthExpense =
                        "EXPENSE".equalsIgnoreCase(
                                type
                        )
                                && dateCalendar.get(
                                Calendar.YEAR
                        ) == currentYear
                                && dateCalendar.get(
                                Calendar.MONTH
                        ) == currentMonthNumber;

                if (isCurrentMonthExpense) {
                    String category =
                            safeText(
                                    transaction.getCategory(),
                                    "Other"
                            );

                    double existingAmount =
                            safeMapAmount(
                                    result.categoryExpense,
                                    category
                            );

                    result.categoryExpense.put(
                            category,
                            existingAmount + amount
                    );
                }
            }
        }

        result.netBalance =
                result.totalIncome
                        - result.totalExpense;

        for (Calendar calendar :
                lastSixMonths) {

            String key =
                    monthKey(calendar);

            result.monthIncomeValues.add(
                    safeMapAmount(
                            result.monthIncome,
                            key
                    )
            );

            result.monthExpenseValues.add(
                    safeMapAmount(
                            result.monthExpense,
                            key
                    )
            );
        }

        result.categoryExpense =
                keepTopCategories(
                        result.categoryExpense
                );

        return result;
    }

    private List<Calendar> createLastSixMonths() {
        List<Calendar> months =
                new ArrayList<>();

        Calendar base =
                Calendar.getInstance();

        base.set(
                Calendar.DAY_OF_MONTH,
                1
        );

        clearTime(base);

        for (int index = 5;
             index >= 0;
             index--) {

            Calendar month =
                    (Calendar) base.clone();

            month.add(
                    Calendar.MONTH,
                    -index
            );

            months.add(month);
        }

        return months;
    }

    private LinkedHashMap<String, Double> keepTopCategories(
            LinkedHashMap<String, Double> originalData
    ) {
        LinkedHashMap<String, Double> finalData =
                new LinkedHashMap<>();

        if (originalData == null
                || originalData.isEmpty()) {

            return finalData;
        }

        List<Map.Entry<String, Double>> entries =
                new ArrayList<>(
                        originalData.entrySet()
                );

        Collections.sort(
                entries,
                (first, second) ->
                        Double.compare(
                                second.getValue(),
                                first.getValue()
                        )
        );

        double otherAmount = 0;

        for (int index = 0;
             index < entries.size();
             index++) {

            Map.Entry<String, Double> entry =
                    entries.get(index);

            String categoryName =
                    safeText(
                            entry.getKey(),
                            "Other"
                    );

            double categoryAmount =
                    entry.getValue() == null
                            ? 0
                            : Math.max(
                            entry.getValue(),
                            0
                    );

            if (categoryAmount <= 0) {
                continue;
            }

            if (index < 5) {
                double existingAmount =
                        safeMapAmount(
                                finalData,
                                categoryName
                        );

                finalData.put(
                        categoryName,
                        existingAmount
                                + categoryAmount
                );

            } else {
                otherAmount +=
                        categoryAmount;
            }
        }

        if (otherAmount > 0) {
            double existingOther =
                    safeMapAmount(
                            finalData,
                            "Other"
                    );

            finalData.put(
                    "Other",
                    existingOther
                            + otherAmount
            );
        }

        return finalData;
    }

    private void updateSummaryCards() {
        txtTotalIncome.setText(
                formatAmount(
                        analyticsData.totalIncome
                )
        );

        txtTotalExpense.setText(
                formatAmount(
                        analyticsData.totalExpense
                )
        );

        txtNetBalance.setText(
                formatAmount(
                        analyticsData.netBalance
                )
        );

        txtTotalIncome.setTextColor(
                getColorValue(
                        R.color.success
                )
        );

        txtTotalExpense.setTextColor(
                getColorValue(
                        R.color.expense
                )
        );

        txtNetBalance.setTextColor(
                getColorValue(
                        analyticsData.netBalance >= 0
                                ? R.color.success
                                : R.color.expense
                )
        );
    }

    private void showSelectedChart() {
        if (isCategoryChart) {
            showCategoryChart();

        } else {
            showMonthlyChart();
        }

        updateChartButtons();
    }

    private void showCategoryChart() {
        txtChartTitle.setText(
                "This Month's Spending"
        );

        financeChartView.setMode(
                FinanceChartView.MODE_PIE
        );

        financeChartView.setPieData(
                analyticsData.categoryExpense
        );

        if (analyticsData.categoryExpense == null
                || analyticsData.categoryExpense
                .isEmpty()) {

            txtInsight.setText(
                    "इस महीने अभी कोई expense entry नहीं है। Expense जोड़ने पर category analysis यहाँ दिखाई देगा।"
            );

            txtInsight.setTextColor(
                    getColorValue(
                            R.color.app_text_secondary
                    )
            );

            return;
        }

        String topCategory = "Other";
        double topAmount = 0;
        double currentMonthExpense = 0;

        for (Map.Entry<String, Double> entry :
                analyticsData.categoryExpense.entrySet()) {

            double amount =
                    entry.getValue() == null
                            ? 0
                            : entry.getValue();

            currentMonthExpense += amount;

            if (amount > topAmount) {
                topAmount = amount;

                topCategory =
                        safeText(
                                entry.getKey(),
                                "Other"
                        );
            }
        }

        double topPercentage =
                currentMonthExpense <= 0
                        ? 0
                        : (
                        topAmount
                        / currentMonthExpense
                ) * 100;

        String message =
                "सबसे अधिक खर्च "
                        + topCategory
                        + " में हुआ है: "
                        + formatAmount(topAmount)
                        + " ("
                        + String.format(
                        Locale.getDefault(),
                        "%.0f%%",
                        topPercentage
                )
                        + " of this month's expenses)।";

        txtInsight.setText(message);

        txtInsight.setTextColor(
                topPercentage >= 50
                        ? getColorValue(
                        R.color.warning
                )
                        : getColorValue(
                        R.color.success
                )
        );
    }

    private void showMonthlyChart() {
        txtChartTitle.setText(
                "Income vs Expense — Last 6 Months"
        );

        financeChartView.setMode(
                FinanceChartView.MODE_BAR
        );

        financeChartView.setMonthlyData(
                analyticsData.monthLabels,
                analyticsData.monthIncomeValues,
                analyticsData.monthExpenseValues
        );

        double sixMonthIncome =
                calculateListTotal(
                        analyticsData.monthIncomeValues
                );

        double sixMonthExpense =
                calculateListTotal(
                        analyticsData.monthExpenseValues
                );

        double sixMonthNet =
                sixMonthIncome
                        - sixMonthExpense;

        if (sixMonthIncome == 0
                && sixMonthExpense == 0) {

            txtInsight.setText(
                    "Income और Expense जोड़ने के बाद यहाँ पिछले छह महीनों का cash-flow trend दिखाई देगा।"
            );

            txtInsight.setTextColor(
                    getColorValue(
                            R.color.app_text_secondary
                    )
            );

        } else if (sixMonthNet >= 0) {
            txtInsight.setText(
                    "पिछले छह महीनों की net saving "
                            + formatAmount(sixMonthNet)
                            + " है। इस अवधि में Income, Expenses से अधिक रही है।"
            );

            txtInsight.setTextColor(
                    getColorValue(
                            R.color.success
                    )
            );

        } else {
            txtInsight.setText(
                    "पिछले छह महीनों का deficit "
                            + formatAmount(
                            Math.abs(sixMonthNet)
                    )
                            + " है। Expenses को review करना उपयोगी रहेगा।"
            );

            txtInsight.setTextColor(
                    getColorValue(
                            R.color.expense
                    )
            );
        }
    }

    private void updateChartButtons() {
        int selectedBackground =
                getColorValue(
                        R.color.primary
                );

        int unselectedBackground =
                getColorValue(
                        R.color.app_surface
                );

        int selectedText =
                getColorValue(
                        R.color.white
                );

        int unselectedText =
                getColorValue(
                        R.color.primary
                );

        int selectedStroke =
                getColorValue(
                        R.color.primary
                );

        int unselectedStroke =
                getColorValue(
                        R.color.app_outline
                );

        applyButtonState(
                btnCategoryChart,
                isCategoryChart,
                selectedBackground,
                unselectedBackground,
                selectedText,
                unselectedText,
                selectedStroke,
                unselectedStroke
        );

        applyButtonState(
                btnMonthlyChart,
                !isCategoryChart,
                selectedBackground,
                unselectedBackground,
                selectedText,
                unselectedText,
                selectedStroke,
                unselectedStroke
        );
    }

    private void applyButtonState(
            MaterialButton button,
            boolean selected,
            int selectedBackground,
            int unselectedBackground,
            int selectedText,
            int unselectedText,
            int selectedStroke,
            int unselectedStroke
    ) {
        button.setBackgroundTintList(
                ColorStateList.valueOf(
                        selected
                                ? selectedBackground
                                : unselectedBackground
                )
        );

        button.setTextColor(
                selected
                        ? selectedText
                        : unselectedText
        );

        button.setStrokeColor(
                ColorStateList.valueOf(
                        selected
                                ? selectedStroke
                                : unselectedStroke
                )
        );

        button.setStrokeWidth(
                dp(1)
        );

        button.setTypeface(
                Typeface.DEFAULT,
                selected
                        ? Typeface.BOLD
                        : Typeface.NORMAL
        );

        button.setAlpha(
                selected
                        ? 1f
                        : 0.92f
        );
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
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd HH:mm",
                "yyyy-MM-dd'T'HH:mm:ss",
                "yyyy-MM-dd'T'HH:mm",
                "yyyy-MM-dd",
                "dd MMMM yyyy HH:mm:ss",
                "dd MMMM yyyy HH:mm",
                "dd MMM yyyy HH:mm:ss",
                "dd MMM yyyy HH:mm",
                "dd MMMM yyyy",
                "dd MMM yyyy",
                "dd/MM/yyyy HH:mm:ss",
                "dd/MM/yyyy HH:mm",
                "dd/MM/yyyy"
        };

        for (String pattern :
                patterns) {

            try {
                SimpleDateFormat format =
                        new SimpleDateFormat(
                                pattern,
                                Locale.ENGLISH
                        );

                format.setLenient(false);

                Date parsedDate =
                        format.parse(
                                cleanDate
                        );

                if (parsedDate != null) {
                    return parsedDate;
                }

            } catch (ParseException ignored) {
                // Try the next supported date format.
            }
        }

        return null;
    }

    private String monthKey(
            Calendar calendar
    ) {
        return new SimpleDateFormat(
                "yyyy-MM",
                Locale.ENGLISH
        ).format(
                calendar.getTime()
        );
    }

    private double calculateListTotal(
            List<Double> values
    ) {
        double total = 0;

        if (values == null) {
            return total;
        }

        for (Double value :
                values) {

            if (value != null) {
                total += value;
            }
        }

        return total;
    }

    private double safeMapAmount(
            Map<String, Double> map,
            String key
    ) {
        if (map == null
                || key == null) {

            return 0;
        }

        Double value =
                map.get(key);

        return value == null
                ? 0
                : value;
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

    private static class AnalyticsData {

        private double totalIncome;
        private double totalExpense;
        private double netBalance;

        private LinkedHashMap<String, Double> categoryExpense =
                new LinkedHashMap<>();

        private final List<String> monthLabels =
                new ArrayList<>();

        private final List<Double> monthIncomeValues =
                new ArrayList<>();

        private final List<Double> monthExpenseValues =
                new ArrayList<>();

        private final HashMap<String, Double> monthIncome =
                new HashMap<>();

        private final HashMap<String, Double> monthExpense =
                new HashMap<>();
    }
}