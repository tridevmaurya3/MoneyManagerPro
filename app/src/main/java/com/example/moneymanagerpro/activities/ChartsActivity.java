package com.example.moneymanagerpro.activities;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.widget.TextView;
import java.text.NumberFormat;
import androidx.appcompat.app.AppCompatActivity;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.charts.FinanceChartView;
import com.example.moneymanagerpro.database.DatabaseClient;
import com.example.moneymanagerpro.model.Transaction;
import com.google.android.material.button.MaterialButton;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
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

    private AnalyticsData analyticsData = new AnalyticsData();
    private boolean isCategoryChart = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_charts);

        txtTotalIncome = findViewById(R.id.txtTotalIncome);
        txtTotalExpense = findViewById(R.id.txtTotalExpense);
        txtNetBalance = findViewById(R.id.txtNetBalance);
        txtChartTitle = findViewById(R.id.txtChartTitle);
        txtInsight = findViewById(R.id.txtInsight);

        btnCategoryChart = findViewById(R.id.btnCategoryChart);
        btnMonthlyChart = findViewById(R.id.btnMonthlyChart);

        financeChartView = findViewById(R.id.financeChartView);

        btnCategoryChart.setOnClickListener(v -> {
            isCategoryChart = true;
            showSelectedChart();
        });

        btnMonthlyChart.setOnClickListener(v -> {
            isCategoryChart = false;
            showSelectedChart();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadAnalytics();
    }

    private void loadAnalytics() {
        new Thread(() -> {
            List<Transaction> transactions = DatabaseClient
                    .getInstance(getApplicationContext())
                    .getAppDatabase()
                    .transactionDao()
                    .getAllTransactions();

            AnalyticsData result = calculateAnalytics(transactions);

            runOnUiThread(() -> {
                analyticsData = result;
                updateSummaryCards();
                showSelectedChart();
            });
        }).start();
    }

    private AnalyticsData calculateAnalytics(List<Transaction> transactions) {
        AnalyticsData result = new AnalyticsData();

        Calendar currentMonth = Calendar.getInstance();
        int currentYear = currentMonth.get(Calendar.YEAR);
        int currentMonthNumber = currentMonth.get(Calendar.MONTH);

        List<Calendar> lastSixMonths = createLastSixMonths();

        for (Calendar calendar : lastSixMonths) {
            String key = monthKey(calendar);
            result.monthLabels.add(
                    new SimpleDateFormat("MMM", Locale.ENGLISH)
                            .format(calendar.getTime())
            );
            result.monthIncome.put(key, 0d);
            result.monthExpense.put(key, 0d);
        }

        for (Transaction transaction : transactions) {
            String type = transaction.getType();
            double amount = transaction.getAmount();

            if ("INCOME".equalsIgnoreCase(type)) {
                result.totalIncome += amount;
            } else if ("EXPENSE".equalsIgnoreCase(type)) {
                result.totalExpense += amount;
            } else {
                continue;
            }

            Date transactionDate = parseDate(transaction.getDate());

            if (transactionDate == null) {
                continue;
            }

            Calendar dateCalendar = Calendar.getInstance();
            dateCalendar.setTime(transactionDate);

            String key = monthKey(dateCalendar);

            if ("INCOME".equalsIgnoreCase(type) && result.monthIncome.containsKey(key)) {
                result.monthIncome.put(
                        key,
                        result.monthIncome.get(key) + amount
                );
            }

            if ("EXPENSE".equalsIgnoreCase(type) && result.monthExpense.containsKey(key)) {
                result.monthExpense.put(
                        key,
                        result.monthExpense.get(key) + amount
                );
            }

            if ("EXPENSE".equalsIgnoreCase(type)
                    && dateCalendar.get(Calendar.YEAR) == currentYear
                    && dateCalendar.get(Calendar.MONTH) == currentMonthNumber) {

                String category = transaction.getCategory();

                if (category == null || category.trim().isEmpty()) {
                    category = "Other";
                }

                double existingAmount = result.categoryExpense.containsKey(category)
                        ? result.categoryExpense.get(category)
                        : 0d;

                result.categoryExpense.put(category, existingAmount + amount);
            }
        }

        result.netBalance = result.totalIncome - result.totalExpense;

        for (Calendar calendar : lastSixMonths) {
            String key = monthKey(calendar);
            result.monthIncomeValues.add(result.monthIncome.get(key));
            result.monthExpenseValues.add(result.monthExpense.get(key));
        }

        result.categoryExpense = keepTopCategories(result.categoryExpense);

        return result;
    }

    private List<Calendar> createLastSixMonths() {
        List<Calendar> months = new ArrayList<>();
        Calendar base = Calendar.getInstance();
        base.set(Calendar.DAY_OF_MONTH, 1);

        for (int i = 5; i >= 0; i--) {
            Calendar month = (Calendar) base.clone();
            month.add(Calendar.MONTH, -i);
            months.add(month);
        }

        return months;
    }

    private LinkedHashMap<String, Double> keepTopCategories(
            LinkedHashMap<String, Double> originalData
    ) {
        List<Map.Entry<String, Double>> entries = new ArrayList<>(originalData.entrySet());

        Collections.sort(entries, (first, second) ->
                Double.compare(second.getValue(), first.getValue())
        );

        LinkedHashMap<String, Double> finalData = new LinkedHashMap<>();
        double otherAmount = 0;

        for (int i = 0; i < entries.size(); i++) {
            if (i < 5) {
                finalData.put(entries.get(i).getKey(), entries.get(i).getValue());
            } else {
                otherAmount += entries.get(i).getValue();
            }
        }

        if (otherAmount > 0) {
            double existingOther = finalData.containsKey("Other")
                    ? finalData.get("Other")
                    : 0d;

            finalData.put("Other", existingOther + otherAmount);
        }

        return finalData;
    }

    private void updateSummaryCards() {
        txtTotalIncome.setText(formatAmount(analyticsData.totalIncome));
        txtTotalExpense.setText(formatAmount(analyticsData.totalExpense));
        txtNetBalance.setText(formatAmount(analyticsData.netBalance));
    }

    private void showSelectedChart() {
        if (isCategoryChart) {
            showCategoryChart();
        } else {
            showMonthlyChart();
        }
    }

    private void showCategoryChart() {
        txtChartTitle.setText("This Month's Spending");

        financeChartView.setMode(FinanceChartView.MODE_PIE);
        financeChartView.setPieData(analyticsData.categoryExpense);

        if (analyticsData.categoryExpense.isEmpty()) {
            txtInsight.setText(
                    "इस महीने अभी कोई expense entry नहीं है। Expense जोड़ने पर category analysis यहाँ दिखेगा।"
            );
        } else {
            String topCategory = "";
            double topAmount = 0;

            for (Map.Entry<String, Double> entry : analyticsData.categoryExpense.entrySet()) {
                if (entry.getValue() > topAmount) {
                    topAmount = entry.getValue();
                    topCategory = entry.getKey();
                }
            }

            txtInsight.setText(
                    "सबसे अधिक खर्च " + topCategory
                            + " में हुआ है: " + formatAmount(topAmount)
            );
        }

        updateChartButtons();
    }

    private void showMonthlyChart() {
        txtChartTitle.setText("Income vs Expense — Last 6 Months");

        financeChartView.setMode(FinanceChartView.MODE_BAR);
        financeChartView.setMonthlyData(
                analyticsData.monthLabels,
                analyticsData.monthIncomeValues,
                analyticsData.monthExpenseValues
        );

        if (analyticsData.totalIncome == 0 && analyticsData.totalExpense == 0) {
            txtInsight.setText(
                    "Income और expense जोड़ने के बाद यहाँ 6 महीने का cash-flow trend दिखेगा।"
            );
        } else if (analyticsData.netBalance >= 0) {
            txtInsight.setText(
                    "Overall net saving: " + formatAmount(analyticsData.netBalance)
                            + ". आपकी income expenses से अधिक है।"
            );
        } else {
            txtInsight.setText(
                    "Overall deficit: " + formatAmount(Math.abs(analyticsData.netBalance))
                            + ". इस महीने expenses पर ध्यान देना अच्छा रहेगा।"
            );
        }

        updateChartButtons();
    }

    private void updateChartButtons() {
        int selectedColor = getColor(R.color.primary);
        int unselectedColor = getColor(android.R.color.white);

        int selectedTextColor = getColor(android.R.color.white);
        int unselectedTextColor = getColor(R.color.primary);

        btnCategoryChart.setBackgroundTintList(
                ColorStateList.valueOf(isCategoryChart ? selectedColor : unselectedColor)
        );
        btnCategoryChart.setTextColor(
                isCategoryChart ? selectedTextColor : unselectedTextColor
        );

        btnMonthlyChart.setBackgroundTintList(
                ColorStateList.valueOf(isCategoryChart ? unselectedColor : selectedColor)
        );
        btnMonthlyChart.setTextColor(
                isCategoryChart ? unselectedTextColor : selectedTextColor
        );
    }

    private Date parseDate(String dateText) {
        if (dateText == null || dateText.trim().isEmpty()) {
            return null;
        }

        String[] patterns = {
                "dd MMMM yyyy",
                "dd MMM yyyy",
                "yyyy-MM-dd",
                "dd/MM/yyyy"
        };

        for (String pattern : patterns) {
            try {
                SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.ENGLISH);
                format.setLenient(false);
                return format.parse(dateText.trim());
            } catch (ParseException ignored) {
            }
        }

        return null;
    }

    private String monthKey(Calendar calendar) {
        return new SimpleDateFormat("yyyy-MM", Locale.ENGLISH)
                .format(calendar.getTime());
    }

    private String formatAmount(double amount) {
        NumberFormat formatter = NumberFormat.getNumberInstance(new Locale("en", "IN"));
        formatter.setMinimumFractionDigits(2);
        formatter.setMaximumFractionDigits(2);

        return "₹" + formatter.format(amount);
    }

    private static class AnalyticsData {
        double totalIncome;
        double totalExpense;
        double netBalance;

        LinkedHashMap<String, Double> categoryExpense = new LinkedHashMap<>();

        List<String> monthLabels = new ArrayList<>();
        List<Double> monthIncomeValues = new ArrayList<>();
        List<Double> monthExpenseValues = new ArrayList<>();

        HashMap<String, Double> monthIncome = new HashMap<>();
        HashMap<String, Double> monthExpense = new HashMap<>();
    }
}