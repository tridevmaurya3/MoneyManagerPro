package com.example.moneymanagerpro.activities;

import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
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
        int percentage = budget == 0
                ? 0
                : (int) Math.round((expense / budget) * 100);

        progressMonthlyBudget.setProgress(
                Math.min(Math.max(percentage, 0), 100)
        );

        if (expense > budget) {
            progressMonthlyBudget.setProgressTintList(
                    ColorStateList.valueOf(
                            Color.parseColor("#DC2626")
                    )
            );

            txtBudgetStatus.setText(
                    buildBudgetUsageText(
                            budget,
                            expense,
                            Math.abs(remaining),
                            percentage,
                            true
                    )
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
                    buildBudgetUsageText(
                            budget,
                            expense,
                            remaining,
                            percentage,
                            false
                    )
            );
        }
    }

    private CharSequence buildBudgetUsageText(
            double budget,
            double expense,
            double remainingOrOver,
            int usagePercent,
            boolean isOverBudget
    ) {
        SpannableStringBuilder builder = new SpannableStringBuilder();

        appendColoredLine(
                builder,
                "Budget",
                formatAmount(budget),
                Color.parseColor("#7C3AED")
        );

        appendColoredLine(
                builder,
                "Spent",
                formatAmount(expense),
                Color.parseColor("#DC2626")
        );

        if (isOverBudget) {
            appendColoredLine(
                    builder,
                    "Over Budget",
                    formatAmount(remainingOrOver),
                    Color.parseColor("#DC2626")
            );
        } else {
            appendColoredLine(
                    builder,
                    "Remaining",
                    formatAmount(remainingOrOver),
                    Color.parseColor("#16A34A")
            );
        }

        int usageColor;
        if (usagePercent >= 100) {
            usageColor = Color.parseColor("#DC2626");
        } else if (usagePercent >= 80) {
            usageColor = Color.parseColor("#EA580C");
        } else {
            usageColor = Color.parseColor("#2563EB");
        }

        appendColoredLine(
                builder,
                "Usage",
                usagePercent + "%",
                usageColor
        );

        return builder;
    }

    private void appendColoredLine(
            SpannableStringBuilder builder,
            String label,
            String value,
            int color
    ) {
        if (builder.length() > 0) {
            builder.append("\n");
        }

        String line = label + " " + value;
        int start = builder.length();
        builder.append(line);

        int end = builder.length();
        int valueStart = start + label.length() + 1;

        builder.setSpan(
                new ForegroundColorSpan(color),
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );

        builder.setSpan(
                new StyleSpan(Typeface.BOLD),
                start,
                start + label.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );

        builder.setSpan(
                new StyleSpan(Typeface.BOLD),
                valueStart,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );
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
                    reportView.setText(
                            buildPeriodSummaryText(income, expense, balance)
                    );
                }
        );
    }

    private CharSequence buildPeriodSummaryText(
            double income,
            double expense,
            double balance
    ) {
        SpannableStringBuilder builder = new SpannableStringBuilder();

        appendColoredLine(
                builder,
                "Income:",
                formatAmount(income),
                Color.parseColor("#16A34A")
        );

        appendColoredLine(
                builder,
                "Expense:",
                formatAmount(expense),
                Color.parseColor("#DC2626")
        );

        int balanceColor = balance >= 0
                ? Color.parseColor("#2563EB")
                : Color.parseColor("#DC2626");

        appendColoredLine(
                builder,
                "Net Balance:",
                formatAmount(balance),
                balanceColor
        );

        return builder;
    }

    private void showCategoryChart(List<CategoryTotal> categoryTotals) {
        categoryChartContainer.removeAllViews();

        if (categoryTotals == null || categoryTotals.isEmpty()) {
            txtEmptyCategoryChart.setVisibility(TextView.VISIBLE);
            return;
        }

        txtEmptyCategoryChart.setVisibility(TextView.GONE);

        double highestTotal = 0;

        for (CategoryTotal item : categoryTotals) {
            if (item != null && item.total > highestTotal) {
                highestTotal = item.total;
            }
        }

        if (highestTotal <= 0) {
            highestTotal = 1;
        }

        for (CategoryTotal item : categoryTotals) {
            if (item != null) {
                addCategoryBar(item, highestTotal);
            }
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
        txtCategory.setTypeface(Typeface.DEFAULT_BOLD);

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
        txtAmount.setTypeface(Typeface.DEFAULT_BOLD);

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