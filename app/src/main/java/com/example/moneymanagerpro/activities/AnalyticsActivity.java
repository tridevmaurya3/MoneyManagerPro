package com.example.moneymanagerpro.activities;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.database.DatabaseClient;
import com.example.moneymanagerpro.model.CategoryTotal;

import java.text.SimpleDateFormat;
import java.util.Calendar;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_analytics);

        spinnerAnalyticsPeriod = findViewById(
                R.id.spinnerAnalyticsPeriod
        );

        txtAnalyticsIncome = findViewById(
                R.id.txtAnalyticsIncome
        );

        txtAnalyticsExpense = findViewById(
                R.id.txtAnalyticsExpense
        );

        txtNetCashFlow = findViewById(
                R.id.txtNetCashFlow
        );

        txtNoAnalytics = findViewById(
                R.id.txtNoAnalytics
        );

        insightContainer = findViewById(R.id.insightContainer);

        incomeExpenseChartContainer = findViewById(
                R.id.incomeExpenseChartContainer
        );

        categoryChartContainer = findViewById(
                R.id.categoryChartContainer
        );

        setupPeriodSpinner();
    }

    private void setupPeriodSpinner() {
        String[] options = {
                "This Month",
                "This Year"
        };

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                options
        );

        spinnerAnalyticsPeriod.setAdapter(adapter);

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
                    }
                }
        );
    }

    private void loadAnalytics() {
        String[] range = getSelectedDateRange();

        String startDate = range[0];
        String endDate = range[1];

        new Thread(() -> {
            double income = DatabaseClient
                    .getInstance(getApplicationContext())
                    .getAppDatabase()
                    .transactionDao()
                    .getTotalAmountByTypeForPeriod(
                            "INCOME",
                            startDate,
                            endDate
                    );

            double expense = DatabaseClient
                    .getInstance(getApplicationContext())
                    .getAppDatabase()
                    .transactionDao()
                    .getTotalAmountByTypeForPeriod(
                            "EXPENSE",
                            startDate,
                            endDate
                    );

            List<CategoryTotal> categoryTotals = DatabaseClient
                    .getInstance(getApplicationContext())
                    .getAppDatabase()
                    .transactionDao()
                    .getCategoryTotalsForPeriod(
                            "EXPENSE",
                            startDate,
                            endDate
                    );

            runOnUiThread(() ->
                    showAnalytics(income, expense, categoryTotals)
            );
        }).start();
    }

    private void showAnalytics(
            double income,
            double expense,
            List<CategoryTotal> categoryTotals
    ) {
        double netCashFlow = income - expense;

        txtAnalyticsIncome.setText(formatAmount(income));
        txtAnalyticsExpense.setText(formatAmount(expense));
        txtNetCashFlow.setText(formatAmount(netCashFlow));

        if (netCashFlow >= 0) {
            txtNetCashFlow.setTextColor(Color.parseColor("#188038"));
        } else {
            txtNetCashFlow.setTextColor(Color.parseColor("#D93025"));
        }

        showSmartInsights(income, expense, categoryTotals);
        showIncomeExpenseChart(income, expense);
        showCategoryChart(categoryTotals);

        if (income == 0 && expense == 0) {
            txtNoAnalytics.setVisibility(View.VISIBLE);
        } else {
            txtNoAnalytics.setVisibility(View.GONE);
        }
    }

    private void showSmartInsights(
            double income,
            double expense,
            List<CategoryTotal> categoryTotals
    ) {
        insightContainer.removeAllViews();

        if (income == 0 && expense == 0) {
            addInsight(
                    "No data yet",
                    "Income या Expense add करने के बाद smart insights दिखेंगे।",
                    "#EEF4FF",
                    "#1565C0"
            );
            return;
        }

        double netCashFlow = income - expense;

        if (netCashFlow >= 0) {
            addInsight(
                    "Good cash flow",
                    "इस अवधि में आपने " +
                            formatAmount(netCashFlow) +
                            " बचाया है।",
                    "#E8F5E9",
                    "#188038"
            );
        } else {
            addInsight(
                    "Spending warning",
                    "Expense income से " +
                            formatAmount(Math.abs(netCashFlow)) +
                            " ज्यादा है।",
                    "#FFEBEE",
                    "#D93025"
            );
        }

        if (!categoryTotals.isEmpty()) {
            CategoryTotal topCategory = categoryTotals.get(0);

            addInsight(
                    "Top spending category",
                    safeText(topCategory.category) +
                            " में सबसे ज्यादा " +
                            formatAmount(topCategory.total) +
                            " खर्च हुआ है।",
                    "#F3E8FF",
                    "#7B1FA2"
            );

            if (expense > 0) {
                double categoryPercentage =
                        (topCategory.total / expense) * 100;

                if (categoryPercentage >= 50) {
                    addInsight(
                            "Category concentration",
                            safeText(topCategory.category) +
                                    " आपके total expense का " +
                                    String.format(
                                            Locale.getDefault(),
                                            "%.0f%%",
                                            categoryPercentage
                                    ) +
                                    " है। इस खर्च को review करें।",
                            "#FFF8E1",
                            "#F57C00"
                    );
                }
            }
        }

        int dayCount = getSelectedPeriodDayCount();
        double averageDailyExpense = expense / Math.max(1, dayCount);

        addInsight(
                "Average daily expense",
                "इस अवधि में आपका average daily expense " +
                        formatAmount(averageDailyExpense) +
                        " है।",
                "#E3F2FD",
                "#1565C0"
        );
    }

    private void addInsight(
            String title,
            String message,
            String backgroundColor,
            String titleColor
    ) {
        LinearLayout insightCard = new LinearLayout(this);
        insightCard.setOrientation(LinearLayout.VERTICAL);
        insightCard.setPadding(dp(14), dp(12), dp(14), dp(12));
        insightCard.setBackground(
                createRoundedBackground(backgroundColor)
        );

        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        cardParams.setMargins(0, dp(6), 0, dp(6));
        insightCard.setLayoutParams(cardParams);

        TextView txtTitle = new TextView(this);
        txtTitle.setText(title);
        txtTitle.setTextColor(Color.parseColor(titleColor));
        txtTitle.setTextSize(15);
        txtTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);

        TextView txtMessage = new TextView(this);
        txtMessage.setText(message);
        txtMessage.setTextColor(Color.parseColor("#344054"));
        txtMessage.setTextSize(13);

        LinearLayout.LayoutParams messageParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        messageParams.setMargins(0, dp(5), 0, 0);
        txtMessage.setLayoutParams(messageParams);

        insightCard.addView(txtTitle);
        insightCard.addView(txtMessage);

        insightContainer.addView(insightCard);
    }

    private void showIncomeExpenseChart(
            double income,
            double expense
    ) {
        incomeExpenseChartContainer.removeAllViews();

        double maximumValue = Math.max(income, expense);

        addAmountBar(
                incomeExpenseChartContainer,
                "Income",
                income,
                maximumValue,
                "#188038"
        );

        addAmountBar(
                incomeExpenseChartContainer,
                "Expense",
                expense,
                maximumValue,
                "#D93025"
        );
    }

    private void showCategoryChart(List<CategoryTotal> categoryTotals) {
        categoryChartContainer.removeAllViews();

        if (categoryTotals.isEmpty()) {
            TextView emptyText = new TextView(this);
            emptyText.setText(
                    "इस अवधि में कोई expense category नहीं है।"
            );
            emptyText.setTextColor(Color.parseColor("#667085"));
            emptyText.setPadding(0, dp(8), 0, dp(8));

            categoryChartContainer.addView(emptyText);
            return;
        }

        double maximumValue = 0;

        for (CategoryTotal categoryTotal : categoryTotals) {
            maximumValue = Math.max(
                    maximumValue,
                    categoryTotal.total
            );
        }

        String[] colors = {
                "#6C63FF",
                "#1565C0",
                "#00897B",
                "#F57C00",
                "#C62828",
                "#8E24AA"
        };

        for (int index = 0; index < categoryTotals.size(); index++) {
            CategoryTotal categoryTotal = categoryTotals.get(index);

            addAmountBar(
                    categoryChartContainer,
                    safeText(categoryTotal.category),
                    categoryTotal.total,
                    maximumValue,
                    colors[index % colors.length]
            );
        }
    }

    private void addAmountBar(
            LinearLayout container,
            String title,
            double amount,
            double maximumAmount,
            String color
    ) {
        LinearLayout itemLayout = new LinearLayout(this);
        itemLayout.setOrientation(LinearLayout.VERTICAL);

        LinearLayout.LayoutParams itemParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        itemParams.setMargins(0, dp(10), 0, dp(4));
        itemLayout.setLayoutParams(itemParams);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);

        TextView txtTitle = new TextView(this);
        txtTitle.setText(title);
        txtTitle.setTextColor(Color.parseColor("#344054"));
        txtTitle.setTextSize(14);

        LinearLayout.LayoutParams titleParams =
                new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1
                );

        txtTitle.setLayoutParams(titleParams);

        TextView txtAmount = new TextView(this);
        txtAmount.setText(formatAmount(amount));
        txtAmount.setTextColor(Color.parseColor(color));
        txtAmount.setTextSize(14);

        titleRow.addView(txtTitle);
        titleRow.addView(txtAmount);

        LinearLayout track = new LinearLayout(this);
        track.setBackground(createRoundedBackground("#E4E7EC"));

        LinearLayout.LayoutParams trackParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(12)
                );

        trackParams.setMargins(0, dp(6), 0, 0);
        track.setLayoutParams(trackParams);

        View bar = new View(this);
        bar.setBackground(createRoundedBackground(color));

        double ratio = maximumAmount == 0 ? 0 : amount / maximumAmount;

        int maxWidth =
                getResources().getDisplayMetrics().widthPixels - dp(70);

        int barWidth = (int) (maxWidth * ratio);

        if (amount > 0 && barWidth < dp(8)) {
            barWidth = dp(8);
        }

        LinearLayout.LayoutParams barParams =
                new LinearLayout.LayoutParams(
                        barWidth,
                        LinearLayout.LayoutParams.MATCH_PARENT
                );

        bar.setLayoutParams(barParams);
        track.addView(bar);

        itemLayout.addView(titleRow);
        itemLayout.addView(track);

        container.addView(itemLayout);
    }

    private String[] getSelectedDateRange() {
        Calendar startCalendar = Calendar.getInstance();
        Calendar endCalendar = Calendar.getInstance();

        String selectedPeriod = spinnerAnalyticsPeriod
                .getSelectedItem()
                .toString();

        if (selectedPeriod.equals("This Year")) {
            startCalendar.set(Calendar.MONTH, Calendar.JANUARY);
            startCalendar.set(Calendar.DAY_OF_MONTH, 1);
        } else {
            startCalendar.set(Calendar.DAY_OF_MONTH, 1);
        }

        startCalendar.set(Calendar.HOUR_OF_DAY, 0);
        startCalendar.set(Calendar.MINUTE, 0);
        startCalendar.set(Calendar.SECOND, 0);

        endCalendar.set(Calendar.HOUR_OF_DAY, 23);
        endCalendar.set(Calendar.MINUTE, 59);
        endCalendar.set(Calendar.SECOND, 59);

        SimpleDateFormat formatter = new SimpleDateFormat(
                "yyyy-MM-dd HH:mm",
                Locale.getDefault()
        );

        return new String[]{
                formatter.format(startCalendar.getTime()),
                formatter.format(endCalendar.getTime())
        };
    }

    private int getSelectedPeriodDayCount() {
        Calendar calendar = Calendar.getInstance();

        String selectedPeriod = spinnerAnalyticsPeriod
                .getSelectedItem()
                .toString();

        if (selectedPeriod.equals("This Year")) {
            return calendar.get(Calendar.DAY_OF_YEAR);
        }

        return calendar.get(Calendar.DAY_OF_MONTH);
    }

    private String formatAmount(double amount) {
        return String.format(
                Locale.getDefault(),
                "₹%.2f",
                amount
        );
    }

    private String safeText(String value) {
        return value == null ? "Other" : value;
    }

    private GradientDrawable createRoundedBackground(String color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.parseColor(color));
        drawable.setCornerRadius(dp(20));
        return drawable;
    }

    private int dp(int value) {
        return (int) (
                value * getResources().getDisplayMetrics().density
        );
    }
}