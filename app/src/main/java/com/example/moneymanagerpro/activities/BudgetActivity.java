package com.example.moneymanagerpro.activities;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

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
            "Weekly",
            "Monthly",
            "Yearly"
    };

    private final int[] categoryAccentColors = {
            Color.parseColor("#C42B1C"),
            Color.parseColor("#D83B01"),
            Color.parseColor("#8764B8"),
            Color.parseColor("#0F6CBD"),
            Color.parseColor("#008272"),
            Color.parseColor("#CA5010"),
            Color.parseColor("#5C2D91"),
            Color.parseColor("#038387")
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_budget);

        bindViews();
        prepareScreen();

        requestNotificationPermission();

        BudgetAlertScheduler.schedule(
                getApplicationContext()
        );
    }

    @Override
    protected void onResume() {
        super.onResume();

        loadBudgets();

        BudgetAlertScheduler.schedule(
                getApplicationContext()
        );
    }

    private void bindViews() {
        inputBudgetLimit =
                findViewById(
                        R.id.inputBudgetLimit
                );

        etBudgetLimit =
                findViewById(
                        R.id.etBudgetLimit
                );

        dropdownBudgetCategory =
                findViewById(
                        R.id.dropdownBudgetCategory
                );

        dropdownBudgetPeriod =
                findViewById(
                        R.id.dropdownBudgetPeriod
                );

        btnSaveBudget =
                findViewById(
                        R.id.btnSaveBudget
                );

        budgetContainer =
                findViewById(
                        R.id.budgetContainer
                );

        txtEmptyBudgets =
                findViewById(
                        R.id.txtEmptyBudgets
                );

        TextView btnBack =
                findViewById(R.id.btnBack);

        btnBack.setOnClickListener(
                view -> finish()
        );
    }

    private void prepareScreen() {
        ArrayAdapter<String> periodAdapter =
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_list_item_1,
                        budgetPeriods
                );

        dropdownBudgetPeriod.setAdapter(
                periodAdapter
        );

        dropdownBudgetPeriod.setText(
                "Monthly",
                false
        );

        btnSaveBudget.setOnClickListener(
                view -> saveBudget()
        );

        BubbleTouchAnimator.apply(
                btnSaveBudget
        );

        loadExpenseCategories();
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT
                >= Build.VERSION_CODES.TIRAMISU
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
            List<Category> categories =
                    DatabaseClient
                            .getInstance(
                                    getApplicationContext()
                            )
                            .getAppDatabase()
                            .categoryDao()
                            .getAllCategories();

            List<String> expenseCategories =
                    new ArrayList<>();

            if (categories != null) {
                for (Category category : categories) {
                    if (category == null
                            || category.getName() == null
                            || category.getName()
                            .trim()
                            .isEmpty()) {

                        continue;
                    }

                    if (category.getType() != null
                            && category.getType()
                            .equalsIgnoreCase(
                                    "expense"
                            )) {

                        expenseCategories.add(
                                category.getName().trim()
                        );
                    }
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

                dropdownBudgetCategory.setAdapter(
                        categoryAdapter
                );

                String currentSelection =
                        dropdownBudgetCategory
                                .getText()
                                .toString()
                                .trim();

                if (currentSelection.isEmpty()
                        || !expenseCategories.contains(
                        currentSelection
                )) {
                    dropdownBudgetCategory.setText(
                            expenseCategories.get(0),
                            false
                    );
                }
            });
        }).start();
    }

    private void saveBudget() {
        String amountText =
                etBudgetLimit.getText() == null
                        ? ""
                        : etBudgetLimit
                        .getText()
                        .toString()
                        .trim();

        if (amountText.isEmpty()) {
            inputBudgetLimit.setError(
                    "Please enter budget limit"
            );

            etBudgetLimit.requestFocus();
            return;
        }

        double limitAmount;

        try {
            limitAmount =
                    Double.parseDouble(
                            amountText
                    );

        } catch (Exception exception) {
            inputBudgetLimit.setError(
                    "Enter a valid amount"
            );

            etBudgetLimit.requestFocus();
            return;
        }

        if (limitAmount <= 0) {
            inputBudgetLimit.setError(
                    "Budget limit must be greater than zero"
            );

            etBudgetLimit.requestFocus();
            return;
        }

        inputBudgetLimit.setError(null);

        String category =
                dropdownBudgetCategory
                        .getText()
                        .toString()
                        .trim();

        String period =
                dropdownBudgetPeriod
                        .getText()
                        .toString()
                        .trim();

        if (category.isEmpty()) {
            Toast.makeText(
                    this,
                    "Please select an expense category",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        if (period.isEmpty()) {
            Toast.makeText(
                    this,
                    "Please select a budget period",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        btnSaveBudget.setEnabled(false);
        btnSaveBudget.setText(
                "Saving Budget..."
        );

        double finalLimitAmount =
                limitAmount;

        new Thread(() -> {
            try {
                Budget existingBudget =
                        DatabaseClient
                                .getInstance(
                                        getApplicationContext()
                                )
                                .getAppDatabase()
                                .budgetDao()
                                .getBudgetForCategory(
                                        category,
                                        period
                                );

                if (existingBudget != null) {
                    existingBudget.setLimitAmount(
                            finalLimitAmount
                    );

                    DatabaseClient
                            .getInstance(
                                    getApplicationContext()
                            )
                            .getAppDatabase()
                            .budgetDao()
                            .update(existingBudget);

                } else {
                    Budget budget =
                            new Budget();

                    budget.setCategory(category);
                    budget.setPeriod(period);

                    budget.setLimitAmount(
                            finalLimitAmount
                    );

                    DatabaseClient
                            .getInstance(
                                    getApplicationContext()
                            )
                            .getAppDatabase()
                            .budgetDao()
                            .insert(budget);
                }

                runOnUiThread(() -> {
                    etBudgetLimit.setText("");

                    btnSaveBudget.setEnabled(true);
                    btnSaveBudget.setText(
                            "Save Budget"
                    );

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

            } catch (Exception exception) {
                runOnUiThread(() -> {
                    btnSaveBudget.setEnabled(true);
                    btnSaveBudget.setText(
                            "Save Budget"
                    );

                    Toast.makeText(
                            BudgetActivity.this,
                            "Unable to save budget",
                            Toast.LENGTH_SHORT
                    ).show();
                });
            }
        }).start();
    }

    private void loadBudgets() {
        new Thread(() -> {
            List<Budget> budgets =
                    DatabaseClient
                            .getInstance(
                                    getApplicationContext()
                            )
                            .getAppDatabase()
                            .budgetDao()
                            .getAllBudgets();

            List<BudgetSummary> summaries =
                    new ArrayList<>();

            if (budgets != null) {
                for (Budget budget : budgets) {
                    if (budget == null) {
                        continue;
                    }

                    DateRange range =
                            getDateRange(
                                    budget.getPeriod()
                            );

                    double spentAmount =
                            DatabaseClient
                                    .getInstance(
                                            getApplicationContext()
                                    )
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
                                    getRemainingDays(
                                            budget.getPeriod()
                                    )
                            )
                    );
                }
            }

            runOnUiThread(
                    () -> showBudgets(
                            summaries
                    )
            );
        }).start();
    }

    private void showBudgets(
            List<BudgetSummary> summaries
    ) {
        budgetContainer.removeAllViews();

        boolean isEmpty =
                summaries == null
                        || summaries.isEmpty();

        txtEmptyBudgets.setVisibility(
                isEmpty
                        ? View.VISIBLE
                        : View.GONE
        );

        if (isEmpty) {
            return;
        }

        for (BudgetSummary summary :
                summaries) {

            addBudgetCard(summary);
        }
    }

    private void addBudgetCard(
            BudgetSummary summary
    ) {
        Budget budget =
                summary.budget;

        double spentAmount =
                summary.spentAmount;

        double limitAmount =
                budget.getLimitAmount();

        int progress =
                calculateProgress(
                        spentAmount,
                        limitAmount
                );

        double remainingAmount =
                limitAmount - spentAmount;

        String statusText;
        int statusColor;

        if (progress >= 100) {
            statusText = "Limit Crossed";

            statusColor =
                    getColorValue(
                            R.color.expense
                    );

        } else if (progress >= 90) {
            statusText = "Critical Alert";

            statusColor =
                    Color.parseColor(
                            "#DC2626"
                    );

        } else if (progress >= 80) {
            statusText = "Warning";

            statusColor =
                    getColorValue(
                            R.color.warning
                    );

        } else {
            statusText = "On Track";

            statusColor =
                    getColorValue(
                            R.color.success
                    );
        }

        int categoryAccent =
                getCategoryAccent(
                        budget.getCategory()
                );

        MaterialCardView card =
                new MaterialCardView(this);

        card.setCardBackgroundColor(
                getColorValue(
                        R.color.app_surface
                )
        );

        card.setRadius(dpToPx(19));
        card.setCardElevation(dpToPx(1));
        card.setStrokeWidth(dpToPx(1));

        card.setStrokeColor(
                createTranslucentColor(
                        statusColor,
                        75
                )
        );

        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        cardParams.setMargins(
                0,
                dpToPx(6),
                0,
                dpToPx(6)
        );

        card.setLayoutParams(cardParams);

        LinearLayout content =
                new LinearLayout(this);

        content.setOrientation(
                LinearLayout.VERTICAL
        );

        content.setPadding(
                dpToPx(15),
                dpToPx(15),
                dpToPx(15),
                dpToPx(14)
        );

        /*
         * Header
         */

        LinearLayout headerRow =
                new LinearLayout(this);

        headerRow.setOrientation(
                LinearLayout.HORIZONTAL
        );

        headerRow.setGravity(
                Gravity.CENTER_VERTICAL
        );

        TextView categoryIcon =
                createCategoryIcon(
                        budget.getCategory(),
                        categoryAccent
                );

        headerRow.addView(categoryIcon);

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
                dpToPx(11),
                0,
                dpToPx(8),
                0
        );

        titleContainer.setLayoutParams(
                titleParams
        );

        TextView txtCategory =
                createText(
                        safeText(
                                budget.getCategory(),
                                "Expense Category"
                        ),
                        16,
                        getColorValue(
                                R.color.app_text_primary
                        ),
                        true
                );

        TextView txtPeriod =
                createText(
                        safeText(
                                budget.getPeriod(),
                                "Monthly"
                        )
                                + " Budget",
                        11,
                        getColorValue(
                                R.color.app_text_secondary
                        ),
                        false
                );

        LinearLayout.LayoutParams periodParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        periodParams.setMargins(
                0,
                dpToPx(3),
                0,
                0
        );

        txtPeriod.setLayoutParams(
                periodParams
        );

        titleContainer.addView(txtCategory);
        titleContainer.addView(txtPeriod);

        headerRow.addView(titleContainer);

        TextView statusBadge =
                createStatusBadge(
                        statusText,
                        statusColor
                );

        headerRow.addView(statusBadge);

        content.addView(headerRow);

        /*
         * Divider
         */

        View divider =
                new View(this);

        divider.setBackgroundColor(
                getColorValue(
                        R.color.app_divider
                )
        );

        LinearLayout.LayoutParams dividerParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dpToPx(1)
                );

        dividerParams.setMargins(
                0,
                dpToPx(13),
                0,
                dpToPx(12)
        );

        divider.setLayoutParams(
                dividerParams
        );

        content.addView(divider);

        /*
         * Spent and Limit metrics
         */

        LinearLayout metricRow =
                new LinearLayout(this);

        metricRow.setOrientation(
                LinearLayout.HORIZONTAL
        );

        metricRow.setBaselineAligned(false);

        LinearLayout spentBlock =
                createMetricBlock(
                        "Spent",
                        formatAmount(spentAmount),
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

        LinearLayout.LayoutParams spentParams =
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                );

        spentParams.setMargins(
                0,
                0,
                dpToPx(5),
                0
        );

        spentBlock.setLayoutParams(
                spentParams
        );

        LinearLayout limitBlock =
                createMetricBlock(
                        "Budget Limit",
                        formatAmount(limitAmount),
                        categoryAccent,
                        createTranslucentColor(
                                categoryAccent,
                                18
                        ),
                        createTranslucentColor(
                                categoryAccent,
                                60
                        )
                );

        LinearLayout.LayoutParams limitParams =
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                );

        limitParams.setMargins(
                dpToPx(5),
                0,
                0,
                0
        );

        limitBlock.setLayoutParams(
                limitParams
        );

        metricRow.addView(spentBlock);
        metricRow.addView(limitBlock);

        content.addView(metricRow);

        /*
         * Progress
         */

        TextView usageTitle =
                createText(
                        "Budget usage",
                        11,
                        getColorValue(
                                R.color.app_text_secondary
                        ),
                        false
                );

        LinearLayout.LayoutParams usageTitleParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        usageTitleParams.setMargins(
                0,
                dpToPx(14),
                0,
                0
        );

        usageTitle.setLayoutParams(
                usageTitleParams
        );

        content.addView(usageTitle);

        ProgressBar progressBar =
                new ProgressBar(
                        this,
                        null,
                        android.R.attr
                                .progressBarStyleHorizontal
                );

        progressBar.setMax(100);

        progressBar.setProgress(
                Math.min(
                        Math.max(progress, 0),
                        100
                )
        );

        progressBar.setProgressTintList(
                ColorStateList.valueOf(
                        statusColor
                )
        );

        progressBar.setProgressBackgroundTintList(
                ColorStateList.valueOf(
                        getColorValue(
                                R.color.app_outline_soft
                        )
                )
        );

        LinearLayout.LayoutParams progressParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dpToPx(9)
                );

        progressParams.setMargins(
                0,
                dpToPx(7),
                0,
                0
        );

        progressBar.setLayoutParams(
                progressParams
        );

        content.addView(progressBar);

        TextView txtProgress =
                createText(
                        progress
                                + "% used · "
                                + statusText,
                        12,
                        statusColor,
                        true
                );

        LinearLayout.LayoutParams progressTextParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        progressTextParams.setMargins(
                0,
                dpToPx(7),
                0,
                0
        );

        txtProgress.setLayoutParams(
                progressTextParams
        );

        content.addView(txtProgress);

        /*
         * Remaining / exceeded
         */

        String remainingTitle;
        String remainingValue;
        String remainingDescription;
        int remainingColor;
        int remainingBackground;
        int remainingOutline;

        if (remainingAmount >= 0) {
            remainingTitle =
                    "Remaining Budget";

            remainingValue =
                    formatAmount(
                            remainingAmount
                    );

            remainingDescription =
                    getDailySafeText(
                            remainingAmount,
                            summary.remainingDays
                    );

            remainingColor =
                    getColorValue(
                            R.color.success
                    );

            remainingBackground =
                    getColorValue(
                            R.color.success_surface
                    );

            remainingOutline =
                    getColorValue(
                            R.color.success_outline
                    );

        } else {
            remainingTitle =
                    "Budget Exceeded";

            remainingValue =
                    formatAmount(
                            Math.abs(
                                    remainingAmount
                            )
                    );

            remainingDescription =
                    "Avoid additional spending in this category";

            remainingColor =
                    getColorValue(
                            R.color.expense
                    );

            remainingBackground =
                    getColorValue(
                            R.color.expense_surface
                    );

            remainingOutline =
                    getColorValue(
                            R.color.expense_outline
                    );
        }

        LinearLayout remainingBox =
                createInformationBox(
                        remainingTitle,
                        remainingValue,
                        remainingDescription,
                        remainingColor,
                        remainingBackground,
                        remainingOutline
                );

        LinearLayout.LayoutParams remainingParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        remainingParams.setMargins(
                0,
                dpToPx(13),
                0,
                0
        );

        remainingBox.setLayoutParams(
                remainingParams
        );

        content.addView(remainingBox);

        /*
         * Days left
         */

        TextView txtDaysLeft =
                createText(
                        summary.remainingDays
                                + " day(s) remaining in this budget period",
                        11,
                        getColorValue(
                                R.color.app_text_secondary
                        ),
                        false
                );

        txtDaysLeft.setGravity(
                Gravity.CENTER_VERTICAL
        );

        LinearLayout.LayoutParams daysParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        daysParams.setMargins(
                0,
                dpToPx(10),
                0,
                0
        );

        txtDaysLeft.setLayoutParams(
                daysParams
        );

        content.addView(txtDaysLeft);

        /*
         * Delete action
         */

        MaterialButton btnDelete =
                createDeleteButton();

        LinearLayout.LayoutParams deleteParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dpToPx(44)
                );

        deleteParams.setMargins(
                0,
                dpToPx(13),
                0,
                0
        );

        btnDelete.setLayoutParams(
                deleteParams
        );

        btnDelete.setOnClickListener(
                view -> confirmDelete(
                        budget
                )
        );

        BubbleTouchAnimator.apply(
                btnDelete
        );

        content.addView(btnDelete);

        card.addView(content);

        budgetContainer.addView(card);
    }

    private TextView createCategoryIcon(
            String categoryName,
            int accentColor
    ) {
        TextView icon =
                new TextView(this);

        String visibleText = "₹";

        if (categoryName != null
                && !categoryName.trim()
                .isEmpty()) {

            visibleText =
                    categoryName
                            .trim()
                            .substring(0, 1)
                            .toUpperCase(
                                    Locale.getDefault()
                            );
        }

        icon.setText(visibleText);
        icon.setTextColor(accentColor);
        icon.setTextSize(17);

        icon.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        icon.setGravity(Gravity.CENTER);

        GradientDrawable background =
                new GradientDrawable();

        background.setColor(
                createTranslucentColor(
                        accentColor,
                        20
                )
        );

        background.setStroke(
                dpToPx(1),
                createTranslucentColor(
                        accentColor,
                        65
                )
        );

        background.setCornerRadius(
                dpToPx(14)
        );

        icon.setBackground(background);

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        dpToPx(46),
                        dpToPx(46)
                );

        icon.setLayoutParams(params);

        return icon;
    }

    private TextView createStatusBadge(
            String statusText,
            int statusColor
    ) {
        TextView badge =
                new TextView(this);

        badge.setText(statusText);
        badge.setTextColor(statusColor);
        badge.setTextSize(10);

        badge.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        badge.setGravity(Gravity.CENTER);

        badge.setPadding(
                dpToPx(10),
                0,
                dpToPx(10),
                0
        );

        GradientDrawable background =
                new GradientDrawable();

        background.setColor(
                createTranslucentColor(
                        statusColor,
                        18
                )
        );

        background.setStroke(
                dpToPx(1),
                createTranslucentColor(
                        statusColor,
                        65
                )
        );

        background.setCornerRadius(
                dpToPx(13)
        );

        badge.setBackground(background);

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        dpToPx(30)
                );

        badge.setLayoutParams(params);

        return badge;
    }

    private LinearLayout createMetricBlock(
            String label,
            String value,
            int valueColor,
            int backgroundColor,
            int outlineColor
    ) {
        LinearLayout container =
                new LinearLayout(this);

        container.setOrientation(
                LinearLayout.VERTICAL
        );

        container.setPadding(
                dpToPx(12),
                dpToPx(11),
                dpToPx(12),
                dpToPx(11)
        );

        GradientDrawable background =
                new GradientDrawable();

        background.setColor(backgroundColor);

        background.setStroke(
                dpToPx(1),
                outlineColor
        );

        background.setCornerRadius(
                dpToPx(13)
        );

        container.setBackground(background);

        TextView labelView =
                createText(
                        label,
                        10,
                        getColorValue(
                                R.color.app_text_secondary
                        ),
                        false
                );

        TextView valueView =
                createText(
                        value,
                        14,
                        valueColor,
                        true
                );

        LinearLayout.LayoutParams valueParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        valueParams.setMargins(
                0,
                dpToPx(4),
                0,
                0
        );

        valueView.setLayoutParams(
                valueParams
        );

        container.addView(labelView);
        container.addView(valueView);

        return container;
    }

    private LinearLayout createInformationBox(
            String title,
            String value,
            String description,
            int accentColor,
            int backgroundColor,
            int outlineColor
    ) {
        LinearLayout container =
                new LinearLayout(this);

        container.setOrientation(
                LinearLayout.HORIZONTAL
        );

        container.setGravity(
                Gravity.CENTER_VERTICAL
        );

        container.setPadding(
                dpToPx(12),
                dpToPx(11),
                dpToPx(12),
                dpToPx(11)
        );

        GradientDrawable background =
                new GradientDrawable();

        background.setColor(backgroundColor);

        background.setStroke(
                dpToPx(1),
                outlineColor
        );

        background.setCornerRadius(
                dpToPx(13)
        );

        container.setBackground(background);

        TextView icon =
                new TextView(this);

        icon.setText(
                accentColor
                        == getColorValue(
                        R.color.expense
                )
                        ? "!"
                        : "✓"
        );

        icon.setTextColor(accentColor);
        icon.setTextSize(16);

        icon.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        icon.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams iconParams =
                new LinearLayout.LayoutParams(
                        dpToPx(34),
                        dpToPx(34)
                );

        icon.setLayoutParams(iconParams);

        container.addView(icon);

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
                dpToPx(9),
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
                        11,
                        getColorValue(
                                R.color.app_text_secondary
                        ),
                        false
                );

        TextView valueView =
                createText(
                        value,
                        15,
                        accentColor,
                        true
                );

        LinearLayout.LayoutParams valueParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        valueParams.setMargins(
                0,
                dpToPx(2),
                0,
                0
        );

        valueView.setLayoutParams(
                valueParams
        );

        TextView descriptionView =
                createText(
                        description,
                        10,
                        getColorValue(
                                R.color.app_text_secondary
                        ),
                        false
                );

        LinearLayout.LayoutParams descriptionParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        descriptionParams.setMargins(
                0,
                dpToPx(3),
                0,
                0
        );

        descriptionView.setLayoutParams(
                descriptionParams
        );

        textContainer.addView(titleView);
        textContainer.addView(valueView);
        textContainer.addView(descriptionView);

        container.addView(textContainer);

        return container;
    }

    private MaterialButton createDeleteButton() {
        MaterialButton button =
                new MaterialButton(this);

        button.setText("Delete Budget");
        button.setTextSize(12);
        button.setTextColor(
                getColorValue(
                        R.color.expense
                )
        );

        button.setAllCaps(false);

        button.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        button.setCornerRadius(
                dpToPx(13)
        );

        button.setBackgroundTintList(
                ColorStateList.valueOf(
                        getColorValue(
                                R.color.error_surface
                        )
                )
        );

        button.setStrokeColor(
                ColorStateList.valueOf(
                        getColorValue(
                                R.color.error_outline
                        )
                )
        );

        button.setStrokeWidth(
                dpToPx(1)
        );

        button.setInsetTop(0);
        button.setInsetBottom(0);

        return button;
    }

    private TextView createText(
            String text,
            float size,
            int color,
            boolean bold
    ) {
        TextView textView =
                new TextView(this);

        textView.setText(text);
        textView.setTextSize(size);
        textView.setTextColor(color);

        if (bold) {
            textView.setTypeface(
                    Typeface.DEFAULT,
                    Typeface.BOLD
            );
        }

        return textView;
    }

    private String getDailySafeText(
            double remainingAmount,
            int remainingDays
    ) {
        if (remainingAmount <= 0) {
            return "Avoid additional spending in this category";
        }

        double dailyAmount =
                remainingAmount
                        / Math.max(
                        remainingDays,
                        1
                );

        return "Safe to spend around "
                + formatAmount(dailyAmount)
                + " per day";
    }

    private void confirmDelete(
            Budget budget
    ) {
        String categoryName =
                safeText(
                        budget.getCategory(),
                        "this category"
                );

        String periodName =
                safeText(
                        budget.getPeriod(),
                        ""
                );

        new AlertDialog.Builder(this)
                .setTitle("Delete Budget")
                .setMessage(
                        "Delete the "
                                + categoryName
                                + " "
                                + periodName
                                + " budget?\n\n"
                                + "This will remove only the budget limit. "
                                + "Your transactions will remain safe."
                )
                .setNegativeButton(
                        "Cancel",
                        null
                )
                .setPositiveButton(
                        "Delete",
                        (dialog, which) ->
                                deleteBudget(
                                        budget
                                )
                )
                .show();
    }

    private void deleteBudget(
            Budget budget
    ) {
        new Thread(() -> {
            try {
                DatabaseClient
                        .getInstance(
                                getApplicationContext()
                        )
                        .getAppDatabase()
                        .budgetDao()
                        .delete(budget);

                runOnUiThread(() -> {
                    Toast.makeText(
                            BudgetActivity.this,
                            "Budget deleted",
                            Toast.LENGTH_SHORT
                    ).show();

                    BudgetAlertScheduler.schedule(
                            getApplicationContext()
                    );

                    loadBudgets();
                });

            } catch (Exception exception) {
                runOnUiThread(() ->
                        Toast.makeText(
                                BudgetActivity.this,
                                "Unable to delete budget",
                                Toast.LENGTH_SHORT
                        ).show()
                );
            }
        }).start();
    }

    private DateRange getDateRange(
            String period
    ) {
        Calendar startCalendar =
                Calendar.getInstance();

        Calendar endCalendar =
                Calendar.getInstance();

        clearTime(startCalendar);
        clearTime(endCalendar);

        if ("Weekly".equalsIgnoreCase(
                period
        )) {
            int day =
                    startCalendar.get(
                            Calendar.DAY_OF_WEEK
                    );

            int difference =
                    day - Calendar.MONDAY;

            if (difference < 0) {
                difference += 7;
            }

            startCalendar.add(
                    Calendar.DAY_OF_MONTH,
                    -difference
            );

            endCalendar =
                    (Calendar) startCalendar.clone();

            endCalendar.add(
                    Calendar.DAY_OF_MONTH,
                    6
            );

        } else if ("Yearly".equalsIgnoreCase(
                period
        )) {
            startCalendar.set(
                    Calendar.MONTH,
                    Calendar.JANUARY
            );

            startCalendar.set(
                    Calendar.DAY_OF_MONTH,
                    1
            );

            endCalendar.set(
                    Calendar.MONTH,
                    Calendar.DECEMBER
            );

            endCalendar.set(
                    Calendar.DAY_OF_MONTH,
                    31
            );

        } else {
            startCalendar.set(
                    Calendar.DAY_OF_MONTH,
                    1
            );

            endCalendar.set(
                    Calendar.DAY_OF_MONTH,
                    endCalendar.getActualMaximum(
                            Calendar.DAY_OF_MONTH
                    )
            );
        }

        SimpleDateFormat dateFormat =
                new SimpleDateFormat(
                        "yyyy-MM-dd",
                        Locale.US
                );

        return new DateRange(
                dateFormat.format(
                        startCalendar.getTime()
                ),
                dateFormat.format(
                        endCalendar.getTime()
                ) + " 23:59"
        );
    }

    private int getRemainingDays(
            String period
    ) {
        Calendar today =
                Calendar.getInstance();

        clearTime(today);

        Calendar endCalendar =
                Calendar.getInstance();

        clearTime(endCalendar);

        if ("Weekly".equalsIgnoreCase(
                period
        )) {
            int day =
                    endCalendar.get(
                            Calendar.DAY_OF_WEEK
                    );

            int difference =
                    Calendar.SUNDAY - day;

            if (difference < 0) {
                difference += 7;
            }

            endCalendar.add(
                    Calendar.DAY_OF_MONTH,
                    difference
            );

        } else if ("Yearly".equalsIgnoreCase(
                period
        )) {
            endCalendar.set(
                    Calendar.MONTH,
                    Calendar.DECEMBER
            );

            endCalendar.set(
                    Calendar.DAY_OF_MONTH,
                    31
            );

        } else {
            endCalendar.set(
                    Calendar.DAY_OF_MONTH,
                    endCalendar.getActualMaximum(
                            Calendar.DAY_OF_MONTH
                    )
            );
        }

        long difference =
                endCalendar.getTimeInMillis()
                        - today.getTimeInMillis();

        return Math.max(
                (int) (
                        difference
                                / (
                                24L
                                        * 60L
                                        * 60L
                                        * 1000L
                        )
                ) + 1,
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
                (
                        spentAmount
                                / limitAmount
                ) * 100
        );
    }

    private int getCategoryAccent(
            String category
    ) {
        String safeCategory =
                category == null
                        ? ""
                        : category.trim();

        int index =
                safeCategory.hashCode()
                        & 0x7FFFFFFF;

        index =
                index
                        % categoryAccentColors.length;

        return categoryAccentColors[index];
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

    private int dpToPx(
            int dp
    ) {
        return Math.round(
                dp
                        * getResources()
                        .getDisplayMetrics()
                        .density
        );
    }

    private static class DateRange {

        private final String startDate;
        private final String endDate;

        private DateRange(
                String startDate,
                String endDate
        ) {
            this.startDate = startDate;
            this.endDate = endDate;
        }
    }

    private static class BudgetSummary {

        private final Budget budget;
        private final double spentAmount;
        private final int remainingDays;

        private BudgetSummary(
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