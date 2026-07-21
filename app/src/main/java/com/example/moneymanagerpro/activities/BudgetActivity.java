package com.example.moneymanagerpro.activities;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.database.DatabaseClient;
import com.example.moneymanagerpro.model.Budget;
import com.example.moneymanagerpro.model.Category;
import com.example.moneymanagerpro.utils.BubbleTouchAnimator;
import com.example.moneymanagerpro.utils.BudgetAlertScheduler;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class BudgetActivity extends AppCompatActivity {

    private TextInputLayout inputBudgetLimit;
    private TextInputEditText etBudgetLimit;
    private MaterialAutoCompleteTextView dropdownBudgetCategory;
    private MaterialAutoCompleteTextView dropdownBudgetPeriod;
    private MaterialButton btnSaveBudget;
    private LinearLayout budgetContainer;
    private TextView txtEmptyBudgets;

    private final String[] budgetPeriods = {
            "Weekly", "Monthly", "Yearly"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_budget);

        bindViews();
        prepareScreen();

        requestNotificationPermission();
        BudgetAlertScheduler.schedule(getApplicationContext());
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadBudgets();
        BudgetAlertScheduler.schedule(getApplicationContext());
    }

    private void bindViews() {
        inputBudgetLimit = findViewById(R.id.inputBudgetLimit);
        etBudgetLimit = findViewById(R.id.etBudgetLimit);
        dropdownBudgetCategory = findViewById(
                R.id.dropdownBudgetCategory
        );
        dropdownBudgetPeriod = findViewById(
                R.id.dropdownBudgetPeriod
        );
        btnSaveBudget = findViewById(R.id.btnSaveBudget);
        budgetContainer = findViewById(R.id.budgetContainer);
        txtEmptyBudgets = findViewById(R.id.txtEmptyBudgets);

        TextView btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());
    }

    private void prepareScreen() {
        ArrayAdapter<String> periodAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                budgetPeriods
        );

        dropdownBudgetPeriod.setAdapter(periodAdapter);
        dropdownBudgetPeriod.setText("Monthly", false);

        BubbleTouchAnimator.apply(btnSaveBudget);
        btnSaveBudget.setOnClickListener(v -> saveBudget());

        loadExpenseCategories();
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(
                Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.POST_NOTIFICATIONS
                    },
                    401
            );
        }
    }

    private void loadExpenseCategories() {
        new Thread(() -> {
            List<Category> categories = DatabaseClient
                    .getInstance(getApplicationContext())
                    .getAppDatabase()
                    .categoryDao()
                    .getAllCategories();

            List<String> expenseCategories = new ArrayList<>();

            for (Category category : categories) {
                if (category.getType() != null
                        && category.getType()
                        .equalsIgnoreCase("expense")) {
                    expenseCategories.add(category.getName());
                }
            }

            if (expenseCategories.isEmpty()) {
                expenseCategories.add("Food");
                expenseCategories.add("Travel");
                expenseCategories.add("Shopping");
                expenseCategories.add("Bills");
                expenseCategories.add("Other Expense");
            }

            runOnUiThread(() -> {
                ArrayAdapter<String> categoryAdapter =
                        new ArrayAdapter<>(
                                BudgetActivity.this,
                                android.R.layout.simple_list_item_1,
                                expenseCategories
                        );

                dropdownBudgetCategory.setAdapter(categoryAdapter);
                dropdownBudgetCategory.setText(
                        expenseCategories.get(0),
                        false
                );
            });
        }).start();
    }

    private void saveBudget() {
        String amountText = etBudgetLimit.getText() == null
                ? ""
                : etBudgetLimit.getText().toString().trim();

        if (amountText.isEmpty()) {
            inputBudgetLimit.setError("Please enter budget limit");
            return;
        }

        double limitAmount;

        try {
            limitAmount = Double.parseDouble(amountText);
        } catch (Exception exception) {
            inputBudgetLimit.setError("Enter a valid amount");
            return;
        }

        if (limitAmount <= 0) {
            inputBudgetLimit.setError(
                    "Budget limit must be greater than zero"
            );
            return;
        }

        inputBudgetLimit.setError(null);

        String category = dropdownBudgetCategory.getText()
                .toString()
                .trim();

        String period = dropdownBudgetPeriod.getText()
                .toString()
                .trim();

        btnSaveBudget.setEnabled(false);
        btnSaveBudget.setText("Saving Budget...");

        double finalLimitAmount = limitAmount;

        new Thread(() -> {
            Budget existingBudget = DatabaseClient
                    .getInstance(getApplicationContext())
                    .getAppDatabase()
                    .budgetDao()
                    .getBudgetForCategory(category, period);

            if (existingBudget != null) {
                existingBudget.setLimitAmount(finalLimitAmount);

                DatabaseClient.getInstance(getApplicationContext())
                        .getAppDatabase()
                        .budgetDao()
                        .update(existingBudget);

            } else {
                Budget budget = new Budget();
                budget.setCategory(category);
                budget.setPeriod(period);
                budget.setLimitAmount(finalLimitAmount);

                DatabaseClient.getInstance(getApplicationContext())
                        .getAppDatabase()
                        .budgetDao()
                        .insert(budget);
            }

            runOnUiThread(() -> {
                etBudgetLimit.setText("");

                btnSaveBudget.setEnabled(true);
                btnSaveBudget.setText("Save Budget");

                BudgetAlertScheduler.schedule(
                        getApplicationContext()
                );

                Toast.makeText(
                        BudgetActivity.this,
                        "Budget saved successfully",
                        Toast.LENGTH_SHORT
                ).show();

                loadBudgets();
            });
        }).start();
    }

    private void loadBudgets() {
        new Thread(() -> {
            List<Budget> budgets = DatabaseClient
                    .getInstance(getApplicationContext())
                    .getAppDatabase()
                    .budgetDao()
                    .getAllBudgets();

            List<BudgetSummary> summaries = new ArrayList<>();

            for (Budget budget : budgets) {
                DateRange range = getDateRange(budget.getPeriod());

                double spentAmount = DatabaseClient
                        .getInstance(getApplicationContext())
                        .getAppDatabase()
                        .transactionDao()
                        .getExpenseTotalForCategoryPeriod(
                                budget.getCategory(),
                                range.startDate,
                                range.endDate
                        );

                summaries.add(
                        new BudgetSummary(
                                budget,
                                spentAmount,
                                getRemainingDays(budget.getPeriod())
                        )
                );
            }

            runOnUiThread(() -> showBudgets(summaries));
        }).start();
    }

    private void showBudgets(List<BudgetSummary> summaries) {
        budgetContainer.removeAllViews();

        txtEmptyBudgets.setVisibility(
                summaries.isEmpty() ? View.VISIBLE : View.GONE
        );

        for (BudgetSummary summary : summaries) {
            addBudgetCard(summary);
        }
    }

    private void addBudgetCard(BudgetSummary summary) {
        Budget budget = summary.budget;
        double spentAmount = summary.spentAmount;
        double limitAmount = budget.getLimitAmount();

        int progress = calculateProgress(
                spentAmount,
                limitAmount
        );

        double remainingAmount = limitAmount - spentAmount;

        String statusText;
        int statusColor;

        if (progress >= 100) {
            statusText = "Limit Crossed";
            statusColor = Color.parseColor("#B91C1C");

        } else if (progress >= 90) {
            statusText = "Critical Alert";
            statusColor = Color.parseColor("#DC2626");

        } else if (progress >= 80) {
            statusText = "80% Warning";
            statusColor = Color.parseColor("#EA580C");

        } else {
            statusText = "On Track";
            statusColor = Color.parseColor("#15803D");
        }

        MaterialCardView card = new MaterialCardView(this);
        card.setCardBackgroundColor(Color.WHITE);
        card.setRadius(dpToPx(22));
        card.setCardElevation(dpToPx(5));
        card.setStrokeWidth(dpToPx(1));
        card.setStrokeColor(Color.parseColor("#E2E8F0"));

        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        cardParams.setMargins(0, 0, 0, dpToPx(12));
        card.setLayoutParams(cardParams);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(
                dpToPx(16),
                dpToPx(16),
                dpToPx(16),
                dpToPx(16)
        );

        TextView txtCategory = createText(
                budget.getCategory(),
                20,
                Color.parseColor("#172033"),
                true
        );
        txtCategory.setGravity(Gravity.CENTER);

        TextView txtPeriod = createText(
                budget.getPeriod() + " Budget",
                13,
                Color.parseColor("#64748B"),
                false
        );
        txtPeriod.setGravity(Gravity.CENTER);

        TextView txtSpent = createText(
                formatAmount(spentAmount)
                        + " used out of "
                        + formatAmount(limitAmount),
                16,
                Color.parseColor("#172033"),
                true
        );
        txtSpent.setGravity(Gravity.CENTER);

        ProgressBar progressBar = new ProgressBar(
                this,
                null,
                android.R.attr.progressBarStyleHorizontal
        );

        progressBar.setMax(100);
        progressBar.setProgress(Math.min(progress, 100));
        progressBar.setProgressTintList(
                ColorStateList.valueOf(statusColor)
        );

        LinearLayout.LayoutParams progressParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dpToPx(10)
                );

        progressParams.setMargins(0, dpToPx(14), 0, 0);
        progressBar.setLayoutParams(progressParams);

        TextView txtProgress = createText(
                progress + "% Used | " + statusText,
                14,
                statusColor,
                true
        );
        txtProgress.setGravity(Gravity.CENTER);

        String remainingText;

        if (remainingAmount >= 0) {
            remainingText = "Remaining: "
                    + formatAmount(remainingAmount);
        } else {
            remainingText = "Exceeded by: "
                    + formatAmount(Math.abs(remainingAmount));
        }

        TextView txtRemaining = createText(
                remainingText,
                14,
                statusColor,
                true
        );
        txtRemaining.setGravity(Gravity.CENTER);

        TextView txtDailySafe = createText(
                getDailySafeText(
                        remainingAmount,
                        summary.remainingDays
                ),
                13,
                Color.parseColor("#475569"),
                false
        );
        txtDailySafe.setGravity(Gravity.CENTER);

        TextView txtDaysLeft = createText(
                summary.remainingDays + " day(s) left in this budget period",
                13,
                Color.parseColor("#64748B"),
                false
        );
        txtDaysLeft.setGravity(Gravity.CENTER);

        MaterialButton btnDelete = new MaterialButton(this);
        btnDelete.setText("Delete Budget");
        btnDelete.setTextSize(13);
        btnDelete.setTextColor(Color.WHITE);
        btnDelete.setAllCaps(false);
        btnDelete.setCornerRadius(dpToPx(22));
        btnDelete.setBackgroundTintList(
                ColorStateList.valueOf(
                        Color.parseColor("#64748B")
                )
        );

        LinearLayout.LayoutParams deleteParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dpToPx(46)
                );

        deleteParams.setMargins(0, dpToPx(14), 0, 0);
        btnDelete.setLayoutParams(deleteParams);

        BubbleTouchAnimator.apply(card);
        BubbleTouchAnimator.apply(btnDelete);

        btnDelete.setOnClickListener(v -> confirmDelete(budget));

        content.addView(txtCategory);
        content.addView(txtPeriod);
        content.addView(txtSpent);
        content.addView(progressBar);
        content.addView(txtProgress);
        content.addView(txtRemaining);
        content.addView(txtDailySafe);
        content.addView(txtDaysLeft);
        content.addView(btnDelete);

        card.addView(content);
        budgetContainer.addView(card);
    }

    private TextView createText(
            String text,
            float size,
            int color,
            boolean bold
    ) {
        TextView textView = new TextView(this);
        textView.setText(text);
        textView.setTextSize(size);
        textView.setTextColor(color);

        if (bold) {
            textView.setTypeface(
                    android.graphics.Typeface.DEFAULT_BOLD
            );
        }

        return textView;
    }

    private String getDailySafeText(
            double remainingAmount,
            int remainingDays
    ) {
        if (remainingAmount <= 0) {
            return "Avoid more spending in this category";
        }

        double dailyAmount = remainingAmount
                / Math.max(remainingDays, 1);

        return "Safe to spend around "
                + formatAmount(dailyAmount)
                + " per day";
    }

    private void confirmDelete(Budget budget) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Budget")
                .setMessage(
                        "Do you want to delete the "
                                + budget.getCategory()
                                + " "
                                + budget.getPeriod()
                                + " budget?"
                )
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    new Thread(() -> {
                        DatabaseClient.getInstance(
                                        getApplicationContext()
                                ).getAppDatabase()
                                .budgetDao()
                                .delete(budget);

                        runOnUiThread(() -> {
                            Toast.makeText(
                                    BudgetActivity.this,
                                    "Budget deleted",
                                    Toast.LENGTH_SHORT
                            ).show();

                            loadBudgets();
                        });
                    }).start();
                })
                .show();
    }

    private DateRange getDateRange(String period) {
        Calendar startCalendar = Calendar.getInstance();
        Calendar endCalendar = Calendar.getInstance();

        clearTime(startCalendar);
        clearTime(endCalendar);

        if (period.equalsIgnoreCase("Weekly")) {
            int day = startCalendar.get(Calendar.DAY_OF_WEEK);
            int difference = day - Calendar.MONDAY;

            if (difference < 0) {
                difference += 7;
            }

            startCalendar.add(Calendar.DAY_OF_MONTH, -difference);

            endCalendar = (Calendar) startCalendar.clone();
            endCalendar.add(Calendar.DAY_OF_MONTH, 6);

        } else if (period.equalsIgnoreCase("Yearly")) {
            startCalendar.set(Calendar.MONTH, Calendar.JANUARY);
            startCalendar.set(Calendar.DAY_OF_MONTH, 1);

            endCalendar.set(Calendar.MONTH, Calendar.DECEMBER);
            endCalendar.set(Calendar.DAY_OF_MONTH, 31);

        } else {
            startCalendar.set(Calendar.DAY_OF_MONTH, 1);

            endCalendar.set(
                    Calendar.DAY_OF_MONTH,
                    endCalendar.getActualMaximum(
                            Calendar.DAY_OF_MONTH
                    )
            );
        }

        SimpleDateFormat dateFormat = new SimpleDateFormat(
                "yyyy-MM-dd",
                Locale.US
        );

        return new DateRange(
                dateFormat.format(startCalendar.getTime()),
                dateFormat.format(endCalendar.getTime()) + " 23:59"
        );
    }

    private int getRemainingDays(String period) {
        Calendar today = Calendar.getInstance();
        clearTime(today);

        Calendar endCalendar = Calendar.getInstance();
        clearTime(endCalendar);

        if (period.equalsIgnoreCase("Weekly")) {
            int day = endCalendar.get(Calendar.DAY_OF_WEEK);
            int difference = Calendar.SUNDAY - day;

            if (difference < 0) {
                difference += 7;
            }

            endCalendar.add(Calendar.DAY_OF_MONTH, difference);

        } else if (period.equalsIgnoreCase("Yearly")) {
            endCalendar.set(Calendar.MONTH, Calendar.DECEMBER);
            endCalendar.set(Calendar.DAY_OF_MONTH, 31);

        } else {
            endCalendar.set(
                    Calendar.DAY_OF_MONTH,
                    endCalendar.getActualMaximum(
                            Calendar.DAY_OF_MONTH
                    )
            );
        }

        long difference = endCalendar.getTimeInMillis()
                - today.getTimeInMillis();

        return Math.max(
                (int) (difference / (24 * 60 * 60 * 1000)) + 1,
                1
        );
    }

    private int calculateProgress(
            double spentAmount,
            double limitAmount
    ) {
        if (limitAmount <= 0) {
            return 0;
        }

        return (int) Math.round(
                (spentAmount / limitAmount) * 100
        );
    }

    private void clearTime(Calendar calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
    }

    private String formatAmount(double amount) {
        NumberFormat formatter = NumberFormat.getNumberInstance(
                new Locale("en", "IN")
        );

        formatter.setMinimumFractionDigits(2);
        formatter.setMaximumFractionDigits(2);

        return "₹" + formatter.format(amount);
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return (int) (dp * density);
    }

    private static class DateRange {
        String startDate;
        String endDate;

        DateRange(String startDate, String endDate) {
            this.startDate = startDate;
            this.endDate = endDate;
        }
    }

    private static class BudgetSummary {
        Budget budget;
        double spentAmount;
        int remainingDays;

        BudgetSummary(
                Budget budget,
                double spentAmount,
                int remainingDays
        ) {
            this.budget = budget;
            this.spentAmount = spentAmount;
            this.remainingDays = remainingDays;
        }
    }
}