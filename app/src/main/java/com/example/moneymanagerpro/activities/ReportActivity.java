package com.example.moneymanagerpro.activities;

import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.model.CategoryTotal;
import com.example.moneymanagerpro.repository.TransactionRepository;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class ReportActivity extends AppCompatActivity {

    private TextView txtTodayReport;
    private TextView txtWeekReport;
    private TextView txtMonthReport;
    private TextView txtYearReport;
    private TextView txtBudgetStatus;
    private TextView txtEmptyCategoryChart;

    private EditText etMonthlyBudget;
    private Button btnSaveBudget;
    private ProgressBar progressMonthlyBudget;
    private LinearLayout categoryChartContainer;

    private TransactionRepository transactionRepository;
    private SharedPreferences budgetPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report);

        txtTodayReport = findViewById(R.id.txtTodayReport);
        txtWeekReport = findViewById(R.id.txtWeekReport);
        txtMonthReport = findViewById(R.id.txtMonthReport);
        txtYearReport = findViewById(R.id.txtYearReport);

        etMonthlyBudget = findViewById(R.id.etMonthlyBudget);
        btnSaveBudget = findViewById(R.id.btnSaveBudget);
        progressMonthlyBudget = findViewById(R.id.progressMonthlyBudget);
        txtBudgetStatus = findViewById(R.id.txtBudgetStatus);

        txtEmptyCategoryChart = findViewById(R.id.txtEmptyCategoryChart);
        categoryChartContainer = findViewById(R.id.categoryChartContainer);

        transactionRepository = new TransactionRepository(this);

        budgetPreferences = getSharedPreferences(
                "MoneyManagerBudget",
                MODE_PRIVATE
        );

        loadSavedBudget();
        btnSaveBudget.setOnClickListener(view -> saveMonthlyBudget());

        loadAllReports();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadAllReports();
    }

    private void loadSavedBudget() {
        String savedBudget = budgetPreferences.getString(
                "monthly_budget",
                ""
        );

        etMonthlyBudget.setText(savedBudget);
    }

    private void saveMonthlyBudget() {
        String budgetText = etMonthlyBudget.getText().toString().trim();

        if (budgetText.isEmpty()) {
            etMonthlyBudget.setError("Monthly budget डालें");
            etMonthlyBudget.requestFocus();
            return;
        }

        double budget;

        try {
            budget = Double.parseDouble(budgetText);
        } catch (NumberFormatException exception) {
            etMonthlyBudget.setError("सही amount डालें");
            etMonthlyBudget.requestFocus();
            return;
        }

        if (budget <= 0) {
            etMonthlyBudget.setError("Budget 0 से बड़ा होना चाहिए");
            etMonthlyBudget.requestFocus();
            return;
        }

        budgetPreferences.edit()
                .putString("monthly_budget", String.valueOf(budget))
                .apply();

        Toast.makeText(
                this,
                "Monthly budget saved",
                Toast.LENGTH_SHORT
        ).show();

        loadMonthlyBudgetStatus();
    }

    private void loadAllReports() {
        loadTodayReport();
        loadWeekReport();
        loadMonthReport();
        loadYearReport();
        loadMonthlyBudgetStatus();
        loadMonthlyCategoryChart();
    }

    private void loadTodayReport() {
        Calendar start = Calendar.getInstance();
        Calendar end = Calendar.getInstance();

        setStartOfDay(start);
        setEndOfDay(end);

        loadReportForPeriod(txtTodayReport, start, end);
    }

    private void loadWeekReport() {
        Calendar start = Calendar.getInstance();

        int dayOfWeek = start.get(Calendar.DAY_OF_WEEK);
        int daysSinceMonday = (dayOfWeek + 5) % 7;

        start.add(Calendar.DAY_OF_MONTH, -daysSinceMonday);
        setStartOfDay(start);

        Calendar end = (Calendar) start.clone();
        end.add(Calendar.DAY_OF_MONTH, 6);
        setEndOfDay(end);

        loadReportForPeriod(txtWeekReport, start, end);
    }

    private void loadMonthReport() {
        Calendar start = getMonthStart();
        Calendar end = getMonthEnd(start);

        loadReportForPeriod(txtMonthReport, start, end);
    }

    private void loadYearReport() {
        Calendar start = Calendar.getInstance();
        start.set(Calendar.DAY_OF_YEAR, 1);
        setStartOfDay(start);

        Calendar end = (Calendar) start.clone();
        end.add(Calendar.YEAR, 1);
        end.add(Calendar.DAY_OF_YEAR, -1);
        setEndOfDay(end);

        loadReportForPeriod(txtYearReport, start, end);
    }

    private void loadMonthlyBudgetStatus() {
        Calendar start = getMonthStart();
        Calendar end = getMonthEnd(start);

        transactionRepository.loadPeriodSummary(
                formatDate(start),
                formatDate(end),
                (income, expense) -> updateBudgetStatus(expense)
        );
    }

    private void updateBudgetStatus(double expense) {
        String savedBudget = budgetPreferences.getString(
                "monthly_budget",
                ""
        );

        if (savedBudget.isEmpty()) {
            progressMonthlyBudget.setProgress(0);
            progressMonthlyBudget.setProgressTintList(
                    ColorStateList.valueOf(
                            Color.parseColor("#64748B")
                    )
            );

            txtBudgetStatus.setText(
                    "अभी Monthly Budget set नहीं है। ऊपर amount भरकर Save Budget दबाएँ।"
            );

            return;
        }

        double budget;

        try {
            budget = Double.parseDouble(savedBudget);
        } catch (NumberFormatException exception) {
            txtBudgetStatus.setText("Budget data सही नहीं है। नया budget save करें।");
            return;
        }

        double remaining = budget - expense;
        int percentage = (int) Math.round((expense / budget) * 100);

        progressMonthlyBudget.setProgress(
                Math.min(percentage, 100)
        );

        if (expense > budget) {
            progressMonthlyBudget.setProgressTintList(
                    ColorStateList.valueOf(
                            Color.parseColor("#DC2626")
                    )
            );

            txtBudgetStatus.setText(
                    "Budget: " + formatAmount(budget) +
                            "\nSpent: " + formatAmount(expense) +
                            "\nOver Budget: " + formatAmount(Math.abs(remaining))
            );
        } else {
            int color;

            if (percentage >= 80) {
                color = Color.parseColor("#EA580C");
            } else {
                color = Color.parseColor("#059669");
            }

            progressMonthlyBudget.setProgressTintList(
                    ColorStateList.valueOf(color)
            );

            txtBudgetStatus.setText(
                    "Budget: " + formatAmount(budget) +
                            "\nSpent: " + formatAmount(expense) +
                            "\nRemaining: " + formatAmount(remaining)
            );
        }
    }

    private void loadMonthlyCategoryChart() {
        Calendar start = getMonthStart();
        Calendar end = getMonthEnd(start);

        transactionRepository.loadCategoryTotals(
                "EXPENSE",
                formatDate(start),
                formatDate(end),
                this::showCategoryChart
        );
    }

    private void loadReportForPeriod(
            TextView reportView,
            Calendar start,
            Calendar end
    ) {
        transactionRepository.loadPeriodSummary(
                formatDate(start),
                formatDate(end),
                (income, expense) -> {
                    double balance = income - expense;

                    String reportText =
                            "Income: " + formatAmount(income) +
                                    "\nExpense: " + formatAmount(expense) +
                                    "\nNet Balance: " + formatAmount(balance);

                    reportView.setText(reportText);
                }
        );
    }

    private void showCategoryChart(List<CategoryTotal> categoryTotals) {
        categoryChartContainer.removeAllViews();

        if (categoryTotals.isEmpty()) {
            txtEmptyCategoryChart.setVisibility(View.VISIBLE);
            return;
        }

        txtEmptyCategoryChart.setVisibility(View.GONE);

        double highestTotal = 0;

        for (CategoryTotal item : categoryTotals) {
            if (item.total > highestTotal) {
                highestTotal = item.total;
            }
        }

        if (highestTotal <= 0) {
            highestTotal = 1;
        }

        for (CategoryTotal item : categoryTotals) {
            addCategoryBar(item, highestTotal);
        }
    }

    private void addCategoryBar(
            CategoryTotal categoryTotal,
            double highestTotal
    ) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(4), dp(10), dp(4), dp(10));

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );

        rowParams.setMargins(0, dp(6), 0, 0);
        row.setLayoutParams(rowParams);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        String category = categoryTotal.category;

        if (category == null || category.isEmpty()) {
            category = "Uncategorized";
        }

        TextView txtCategory = new TextView(this);
        txtCategory.setText(category);
        txtCategory.setTextSize(16);
        txtCategory.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);

        LinearLayout.LayoutParams categoryParams = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1
        );

        txtCategory.setLayoutParams(categoryParams);

        TextView txtAmount = new TextView(this);
        txtAmount.setText(formatAmount(categoryTotal.total));
        txtAmount.setTextColor(Color.parseColor("#D32F2F"));
        txtAmount.setTextSize(15);
        txtAmount.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);

        header.addView(txtCategory);
        header.addView(txtAmount);

        int progress = (int) Math.round(
                (categoryTotal.total / highestTotal) * 100
        );

        ProgressBar progressBar = new ProgressBar(
                this,
                null,
                android.R.attr.progressBarStyleHorizontal
        );

        progressBar.setMax(100);
        progressBar.setProgress(progress);
        progressBar.setProgressTintList(
                ColorStateList.valueOf(
                        Color.parseColor("#D32F2F")
                )
        );

        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(10)
        );

        progressParams.setMargins(0, dp(8), 0, 0);
        progressBar.setLayoutParams(progressParams);

        row.addView(header);
        row.addView(progressBar);

        categoryChartContainer.addView(row);
    }

    private Calendar getMonthStart() {
        Calendar start = Calendar.getInstance();
        start.set(Calendar.DAY_OF_MONTH, 1);
        setStartOfDay(start);

        return start;
    }

    private Calendar getMonthEnd(Calendar monthStart) {
        Calendar end = (Calendar) monthStart.clone();
        end.add(Calendar.MONTH, 1);
        end.add(Calendar.DAY_OF_MONTH, -1);
        setEndOfDay(end);

        return end;
    }

    private void setStartOfDay(Calendar calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
    }

    private void setEndOfDay(Calendar calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        calendar.set(Calendar.MILLISECOND, 999);
    }

    private String formatDate(Calendar calendar) {
        return new SimpleDateFormat(
                "yyyy-MM-dd HH:mm",
                Locale.getDefault()
        ).format(calendar.getTime());
    }

    private String formatAmount(double amount) {
        return "₹" + String.format(
                Locale.getDefault(),
                "%,.2f",
                amount
        );
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return (int) (value * density);
    }
}