package com.example.moneymanagerpro.dashboard;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.activities.BudgetActivity;
import com.example.moneymanagerpro.activities.DashboardActivity;
import com.example.moneymanagerpro.activities.FinanceAdvisorActivity;
import com.example.moneymanagerpro.database.DatabaseClient;
import com.example.moneymanagerpro.model.Budget;
import com.example.moneymanagerpro.model.CreditCardPayment;
import com.example.moneymanagerpro.model.Transaction;
import com.example.moneymanagerpro.utils.BubbleTouchAnimator;
import com.google.android.material.button.MaterialButton;
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

/**
 * Smart Dashboard 2.0 runtime panel.
 *
 * It analyses only the local Room database and displays cash flow, budget
 * health, saving-target progress, top spending category and month-end expense
 * projection. No finance data leaves the device.
 */
public final class SmartDashboard2Controller {

    private static final String PANEL_TAG =
            "money_manager_smart_dashboard_2_panel";

    private static final String[] DATE_PATTERNS = {
            "yyyy-MM-dd HH:mm",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd",
            "dd-MM-yyyy HH:mm",
            "dd-MM-yyyy",
            "dd/MM/yyyy HH:mm",
            "dd/MM/yyyy"
    };

    private final Activity activity;

    private LinearLayout panel;
    private TextView txtPeriod;
    private TextView txtCashFlow;
    private TextView txtBudgetHealth;
    private TextView txtSavingProgress;
    private TextView txtTopCategory;
    private TextView txtProjection;
    private TextView txtGuidance;
    private ProgressBar savingProgressBar;
    private ProgressBar budgetProgressBar;

    private int requestVersion;
    private String lastLoadedPeriod = "";

    public SmartDashboard2Controller(
            @NonNull Activity activity
    ) {
        this.activity = activity;
    }

    public void attach() {
        if (!(activity instanceof DashboardActivity)
                || activity.isFinishing()
                || activity.isDestroyed()) {
            return;
        }

        ensurePanel();
        loadSelectedPeriod();
    }

    private void ensurePanel() {
        View existing = activity.getWindow()
                .getDecorView()
                .findViewWithTag(PANEL_TAG);

        if (existing instanceof LinearLayout) {
            panel = (LinearLayout) existing;
            bindPanelViews(panel);
            return;
        }

        View monthCard = activity.findViewById(R.id.cardMonth1);
        if (monthCard == null
                || !(monthCard.getParent() instanceof ViewGroup)) {
            return;
        }

        ViewGroup monthRow = (ViewGroup) monthCard.getParent();
        if (!(monthRow.getParent() instanceof LinearLayout)) {
            return;
        }

        LinearLayout dashboardContent =
                (LinearLayout) monthRow.getParent();

        panel = buildPanel();
        panel.setTag(PANEL_TAG);

        int insertionIndex =
                dashboardContent.indexOfChild(monthRow) + 1;

        dashboardContent.addView(
                panel,
                insertionIndex
        );
    }

    @NonNull
    private LinearLayout buildPanel() {
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);

        LinearLayout.LayoutParams rootParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        rootParams.setMargins(0, dp(20), 0, 0);
        root.setLayoutParams(rootParams);

        TextView heading = createText(
                "Smart Dashboard 2.0",
                19,
                R.color.app_text_primary,
                true
        );
        root.addView(heading);

        txtPeriod = createText(
                "Private monthly intelligence from your own data",
                10,
                R.color.app_text_secondary,
                false
        );
        setTopMargin(txtPeriod, 3);
        root.addView(txtPeriod);

        MaterialCardView hero = createCard(
                R.color.info_surface,
                R.color.info_outline,
                18
        );
        setTopMargin(hero, 11);

        LinearLayout heroContent = verticalContent(15);

        txtCashFlow = createMetricText("Monthly Cash Flow", "₹0.00");
        heroContent.addView(txtCashFlow);

        txtGuidance = createText(
                "Loading income, expense and saving position...",
                11,
                R.color.app_text_secondary,
                false
        );
        txtGuidance.setLineSpacing(dp(2), 1f);
        setTopMargin(txtGuidance, 5);
        heroContent.addView(txtGuidance);

        hero.addView(heroContent);
        root.addView(hero);

        LinearLayout firstRow = createHorizontalRow();
        setTopMargin(firstRow, 10);

        MaterialCardView budgetCard = createCard(
                R.color.warning_surface,
                R.color.warning_outline,
                17
        );
        budgetCard.setLayoutParams(weightedCardParams(false));

        LinearLayout budgetContent = verticalContent(12);
        txtBudgetHealth = createSmallMetric(
                "Budget Health",
                "No budgets"
        );
        budgetContent.addView(txtBudgetHealth);

        budgetProgressBar = new ProgressBar(
                activity,
                null,
                android.R.attr.progressBarStyleHorizontal
        );
        budgetProgressBar.setMax(100);
        setTopMargin(budgetProgressBar, 9);
        budgetContent.addView(budgetProgressBar,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(7)
                ));
        budgetCard.addView(budgetContent);
        firstRow.addView(budgetCard);

        MaterialCardView savingCard = createCard(
                R.color.success_surface,
                R.color.success_outline,
                17
        );
        savingCard.setLayoutParams(weightedCardParams(true));

        LinearLayout savingContent = verticalContent(12);
        txtSavingProgress = createSmallMetric(
                "Saving Target",
                "₹0.00"
        );
        savingContent.addView(txtSavingProgress);

        savingProgressBar = new ProgressBar(
                activity,
                null,
                android.R.attr.progressBarStyleHorizontal
        );
        savingProgressBar.setMax(100);
        setTopMargin(savingProgressBar, 9);
        savingContent.addView(savingProgressBar,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(7)
                ));
        savingCard.addView(savingContent);
        firstRow.addView(savingCard);

        root.addView(firstRow);

        LinearLayout secondRow = createHorizontalRow();
        setTopMargin(secondRow, 10);

        MaterialCardView categoryCard = createCard(
                R.color.purple_surface,
                R.color.purple_outline,
                17
        );
        categoryCard.setLayoutParams(weightedCardParams(false));

        LinearLayout categoryContent = verticalContent(12);
        txtTopCategory = createSmallMetric(
                "Top Category",
                "No data"
        );
        categoryContent.addView(txtTopCategory);
        categoryCard.addView(categoryContent);
        secondRow.addView(categoryCard);

        MaterialCardView projectionCard = createCard(
                R.color.app_surface_soft,
                R.color.app_outline_soft,
                17
        );
        projectionCard.setLayoutParams(weightedCardParams(true));

        LinearLayout projectionContent = verticalContent(12);
        txtProjection = createSmallMetric(
                "Month-End Projection",
                "₹0.00"
        );
        projectionContent.addView(txtProjection);
        projectionCard.addView(projectionContent);
        secondRow.addView(projectionCard);

        root.addView(secondRow);

        LinearLayout actionRow = createHorizontalRow();
        setTopMargin(actionRow, 11);

        MaterialButton budgetButton = createActionButton(
                "Open AI Budget Planner",
                R.color.expense
        );
        budgetButton.setLayoutParams(weightedButtonParams(false));
        budgetButton.setOnClickListener(view ->
                activity.startActivity(
                        new Intent(
                                activity,
                                BudgetActivity.class
                        )
                )
        );
        BubbleTouchAnimator.apply(budgetButton);
        actionRow.addView(budgetButton);

        MaterialButton assistantButton = createActionButton(
                "Smart Assistant",
                R.color.purple
        );
        assistantButton.setLayoutParams(weightedButtonParams(true));
        assistantButton.setOnClickListener(view ->
                activity.startActivity(
                        new Intent(
                                activity,
                                FinanceAdvisorActivity.class
                        )
                )
        );
        BubbleTouchAnimator.apply(assistantButton);
        actionRow.addView(assistantButton);

        root.addView(actionRow);
        return root;
    }

    private void bindPanelViews(
            @NonNull LinearLayout ignored
    ) {
        // Views are retained by this controller instance. A recreated Activity
        // receives a fresh controller and builds a fresh panel.
    }

    private void loadSelectedPeriod() {
        if (panel == null) {
            return;
        }

        TextView selectedPeriodView =
                activity.findViewById(
                        R.id.txtSelectedPeriod
                );

        String selectedPeriod =
                selectedPeriodView == null
                        ? ""
                        : selectedPeriodView
                        .getText()
                        .toString()
                        .trim();

        if (selectedPeriod.isEmpty()) {
            selectedPeriod = new SimpleDateFormat(
                    "MMMM yyyy",
                    Locale.ENGLISH
            ).format(new Date());
        }

        if (selectedPeriod.equals(lastLoadedPeriod)
                && requestVersion > 0) {
            return;
        }

        lastLoadedPeriod = selectedPeriod;
        txtPeriod.setText(
                selectedPeriod
                        + " • Offline private analysis"
        );

        final Calendar requestedMonth =
                parseMonth(selectedPeriod);

        final int currentRequest = ++requestVersion;
        showLoading();

        new Thread(() -> {
            try {
                List<Transaction> transactions =
                        DatabaseClient
                                .getInstance(
                                        activity.getApplicationContext()
                                )
                                .getAppDatabase()
                                .transactionDao()
                                .getAllTransactions();

                List<Budget> budgets =
                        DatabaseClient
                                .getInstance(
                                        activity.getApplicationContext()
                                )
                                .getAppDatabase()
                                .budgetDao()
                                .getAllBudgets();

                List<CreditCardPayment> cardPayments =
                        DatabaseClient
                                .getInstance(
                                        activity.getApplicationContext()
                                )
                                .getAppDatabase()
                                .creditCardPaymentDao()
                                .getAllPayments();

                DashboardData data = analyse(
                        transactions,
                        budgets,
                        cardPayments,
                        requestedMonth
                );

                activity.runOnUiThread(() -> {
                    if (currentRequest != requestVersion
                            || activity.isFinishing()
                            || activity.isDestroyed()) {
                        return;
                    }
                    showData(data);
                });

            } catch (Exception exception) {
                activity.runOnUiThread(() -> {
                    if (currentRequest != requestVersion
                            || activity.isFinishing()
                            || activity.isDestroyed()) {
                        return;
                    }
                    showFailure();
                });
            }
        }).start();
    }

    @NonNull
    private DashboardData analyse(
            List<Transaction> transactions,
            List<Budget> budgets,
            List<CreditCardPayment> cardPayments,
            @NonNull Calendar requestedMonth
    ) {
        DashboardData data = new DashboardData();
        data.periodLabel = new SimpleDateFormat(
                "MMMM yyyy",
                Locale.ENGLISH
        ).format(requestedMonth.getTime());

        Calendar current = Calendar.getInstance();
        boolean currentMonth =
                current.get(Calendar.YEAR)
                        == requestedMonth.get(Calendar.YEAR)
                        && current.get(Calendar.MONTH)
                        == requestedMonth.get(Calendar.MONTH);

        Map<String, Double> categorySpend =
                new LinkedHashMap<>();

        if (transactions != null) {
            for (Transaction transaction : transactions) {
                if (transaction == null) {
                    continue;
                }

                Date date = parseDate(transaction.getDate());
                if (date == null) {
                    continue;
                }

                Calendar transactionMonth = Calendar.getInstance();
                transactionMonth.setTime(date);

                if (transactionMonth.get(Calendar.YEAR)
                        != requestedMonth.get(Calendar.YEAR)
                        || transactionMonth.get(Calendar.MONTH)
                        != requestedMonth.get(Calendar.MONTH)) {
                    continue;
                }

                double amount = Math.abs(transaction.getAmount());
                if (amount <= 0
                        || Double.isNaN(amount)
                        || Double.isInfinite(amount)) {
                    continue;
                }

                String type = safe(transaction.getType());
                if ("INCOME".equalsIgnoreCase(type)) {
                    data.income += amount;
                } else if ("EXPENSE".equalsIgnoreCase(type)) {
                    data.expense += amount;
                    String category = safe(transaction.getCategory());
                    if (category.isEmpty()) {
                        category = "Other Expense";
                    }
                    addCategory(categorySpend, category, amount);
                }
            }
        }

        data.cashFlow = data.income - data.expense;
        if (cardPayments != null) {
            for (CreditCardPayment payment : cardPayments) {
                if (payment == null) {
                    continue;
                }
                Date paymentDate = parseDate(payment.getPaymentDate());
                if (paymentDate == null) {
                    continue;
                }
                Calendar paymentMonth = Calendar.getInstance();
                paymentMonth.setTime(paymentDate);
                if (paymentMonth.get(Calendar.YEAR)
                        == requestedMonth.get(Calendar.YEAR)
                        && paymentMonth.get(Calendar.MONTH)
                        == requestedMonth.get(Calendar.MONTH)) {
                    data.cardPayments += Math.abs(payment.getAmount());
                }
            }
        }
        data.netAvailableCash =
                data.cashFlow - data.cardPayments;
        data.savingTarget = data.income * 0.20d;
        data.savingProgress = data.savingTarget > 0
                ? percentage(data.cashFlow, data.savingTarget)
                : 0;

        for (Map.Entry<String, Double> entry
                : categorySpend.entrySet()) {
            if (entry.getValue() > data.topCategoryAmount) {
                data.topCategory = entry.getKey();
                data.topCategoryAmount = entry.getValue();
            }
        }

        if (currentMonth) {
            int elapsedDays = Math.max(
                    1,
                    current.get(Calendar.DAY_OF_MONTH)
            );
            int totalDays = current.getActualMaximum(
                    Calendar.DAY_OF_MONTH
            );
            data.projectedExpense =
                    data.expense / elapsedDays * totalDays;
        } else {
            data.projectedExpense = data.expense;
        }

        double monthlyBudgetLimit = 0;
        double monthlyBudgetSpent = 0;
        int monthlyBudgetCount = 0;

        if (budgets != null) {
            for (Budget budget : budgets) {
                if (budget == null
                        || !"Monthly".equalsIgnoreCase(
                        safe(budget.getPeriod()))) {
                    continue;
                }

                double limit = Math.max(
                        0,
                        budget.getLimitAmount()
                );
                if (limit <= 0) {
                    continue;
                }

                monthlyBudgetCount++;
                monthlyBudgetLimit += limit;
                monthlyBudgetSpent += findCategoryAmount(
                        categorySpend,
                        safe(budget.getCategory())
                );
            }
        }

        data.budgetCount = monthlyBudgetCount;
        data.budgetLimit = monthlyBudgetLimit;
        data.budgetSpent = monthlyBudgetSpent;
        data.budgetUsage = monthlyBudgetLimit > 0
                ? percentage(monthlyBudgetSpent, monthlyBudgetLimit)
                : 0;

        return data;
    }

    private void showLoading() {
        txtCashFlow.setText("Monthly Cash Flow\nCalculating...");
        txtBudgetHealth.setText("Budget Health\nCalculating...");
        txtSavingProgress.setText("Saving Target\nCalculating...");
        txtTopCategory.setText("Top Category\nCalculating...");
        txtProjection.setText("Month-End Projection\nCalculating...");
        txtGuidance.setText(
                "Reviewing transactions and monthly budgets on this device."
        );
        savingProgressBar.setProgress(0);
        budgetProgressBar.setProgress(0);
    }

    private void showData(
            @NonNull DashboardData data
    ) {
        txtCashFlow.setText(buildCashFlowText(data));
        // Keep the heading neutral. Individual signed values receive their
        // own semantic colour inside buildCashFlowText(). Setting one colour
        // on the entire TextView made expense values appear green whenever
        // the net cash flow was positive.
        txtCashFlow.setTextColor(color(R.color.app_text_primary));

        if (data.budgetCount == 0) {
            txtBudgetHealth.setText(
                    "Budget Health\nNo monthly budgets"
            );
            budgetProgressBar.setProgress(0);
        } else {
            txtBudgetHealth.setText(
                    "Budget Health\n"
                            + Math.round(data.budgetUsage)
                            + "% used\n"
                            + money(data.budgetSpent)
                            + " / "
                            + money(data.budgetLimit)
            );
            budgetProgressBar.setProgress(
                    clampProgress(data.budgetUsage)
            );
        }

        txtSavingProgress.setText(
                "Saving Target\n"
                        + Math.round(data.savingProgress)
                        + "% achieved\nTarget "
                        + money(data.savingTarget)
        );
        savingProgressBar.setProgress(
                clampProgress(data.savingProgress)
        );

        txtTopCategory.setText(
                "Top Category\n"
                        + (data.topCategory.isEmpty()
                        ? "No expense data"
                        : data.topCategory
                        + "\n"
                        + money(data.topCategoryAmount))
        );

        txtProjection.setText(
                "Month-End Projection\n"
                        + money(data.projectedExpense)
                        + " expense"
        );

        txtGuidance.setText(
                buildGuidance(data)
        );
    }

    @NonNull
    private CharSequence buildCashFlowText(
            @NonNull DashboardData data
    ) {
        SpannableStringBuilder text = new SpannableStringBuilder();

        appendStyled(
                text,
                "Monthly Cash Flow",
                color(R.color.app_text_primary),
                Typeface.BOLD
        );
        text.append('\n');

        appendStyled(
                text,
                signedMoney(data.cashFlow),
                color(data.cashFlow >= 0
                        ? R.color.success
                        : R.color.expense),
                Typeface.BOLD
        );
        text.append("  •  Income ");
        appendStyled(
                text,
                "+" + money(data.income),
                color(R.color.success),
                Typeface.BOLD
        );
        text.append("  •  Expense ");
        appendStyled(
                text,
                "−" + money(data.expense),
                color(R.color.expense),
                Typeface.BOLD
        );
        text.append('\n');
        text.append("Card Payments ");
        appendStyled(
                text,
                "−" + money(data.cardPayments),
                color(R.color.expense),
                Typeface.BOLD
        );
        text.append("  •  Available Cash ");
        appendStyled(
                text,
                signedMoney(data.netAvailableCash),
                color(data.netAvailableCash >= 0
                        ? R.color.success
                        : R.color.expense),
                Typeface.BOLD
        );

        return text;
    }

    private void appendStyled(
            @NonNull SpannableStringBuilder target,
            @NonNull String value,
            int textColor,
            int typefaceStyle
    ) {
        int start = target.length();
        target.append(value);
        int end = target.length();

        target.setSpan(
                new ForegroundColorSpan(textColor),
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );
        target.setSpan(
                new StyleSpan(typefaceStyle),
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );
    }

    @NonNull
    private String buildGuidance(
            @NonNull DashboardData data
    ) {
        if (data.income <= 0 && data.expense <= 0) {
            return "Add income and expense entries to activate complete Smart Dashboard insights.";
        }

        if (data.cashFlow < 0) {
            return "Expense is above income by "
                    + money(Math.abs(data.cashFlow))
                    + ". Open AI Budget Planner to create safer category limits.";
        }

        if (data.budgetCount == 0) {
            return "Cash flow is positive, but no monthly category budget is active. Generate an AI budget plan for better control.";
        }

        if (data.budgetUsage >= 100) {
            return "Monthly category budgets have been crossed. Review the highest-spending category and reduce flexible expenses.";
        }

        if (data.budgetUsage >= 80) {
            return "Budget usage is in the warning zone. Keep the remaining spending focused on essential categories.";
        }

        if (data.savingTarget > 0
                && data.cashFlow < data.savingTarget) {
            return "Budgets are under control, but the 20% saving target is not yet complete.";
        }

        return "Cash flow, budget usage and saving progress are currently on track.";
    }

    private void showFailure() {
        txtCashFlow.setText("Monthly Cash Flow\nUnavailable");
        txtBudgetHealth.setText("Budget Health\nUnavailable");
        txtSavingProgress.setText("Saving Target\nUnavailable");
        txtTopCategory.setText("Top Category\nUnavailable");
        txtProjection.setText("Month-End Projection\nUnavailable");
        txtGuidance.setText(
                "Smart Dashboard data could not be calculated. Existing finance records were not changed."
        );
    }

    @NonNull
    private Calendar parseMonth(
            @NonNull String label
    ) {
        Calendar fallback = Calendar.getInstance();
        try {
            SimpleDateFormat formatter = new SimpleDateFormat(
                    "MMMM yyyy",
                    Locale.ENGLISH
            );
            formatter.setLenient(false);
            Date parsed = formatter.parse(label);
            if (parsed != null) {
                fallback.setTime(parsed);
            }
        } catch (Exception ignored) {
            // Current month remains the safe fallback.
        }
        fallback.set(Calendar.DAY_OF_MONTH, 1);
        fallback.set(Calendar.HOUR_OF_DAY, 0);
        fallback.set(Calendar.MINUTE, 0);
        fallback.set(Calendar.SECOND, 0);
        fallback.set(Calendar.MILLISECOND, 0);
        return fallback;
    }

    private Date parseDate(String value) {
        String clean = safe(value);
        if (clean.isEmpty()) {
            return null;
        }
        for (String pattern : DATE_PATTERNS) {
            SimpleDateFormat formatter =
                    new SimpleDateFormat(pattern, Locale.US);
            formatter.setLenient(false);
            ParsePosition position = new ParsePosition(0);
            Date date = formatter.parse(clean, position);
            if (date != null
                    && position.getIndex() == clean.length()) {
                return date;
            }
        }
        return null;
    }

    private void addCategory(
            @NonNull Map<String, Double> values,
            @NonNull String category,
            double amount
    ) {
        String matching = null;
        for (String existing : values.keySet()) {
            if (existing.equalsIgnoreCase(category)) {
                matching = existing;
                break;
            }
        }
        String key = matching == null ? category : matching;
        Double previous = values.get(key);
        values.put(key, (previous == null ? 0 : previous) + amount);
    }

    private double findCategoryAmount(
            @NonNull Map<String, Double> values,
            @NonNull String category
    ) {
        for (Map.Entry<String, Double> entry : values.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(category)) {
                return entry.getValue() == null ? 0 : entry.getValue();
            }
        }
        return 0;
    }

    private double percentage(double value, double total) {
        if (total <= 0) {
            return 0;
        }
        return Math.max(0, value / total * 100d);
    }

    private int clampProgress(double value) {
        return Math.max(0, Math.min(100, (int) Math.round(value)));
    }

    @NonNull
    private TextView createMetricText(
            @NonNull String label,
            @NonNull String value
    ) {
        return createText(
                label + "\n" + value,
                18,
                R.color.secondary,
                true
        );
    }

    @NonNull
    private TextView createSmallMetric(
            @NonNull String label,
            @NonNull String value
    ) {
        TextView textView = createText(
                label + "\n" + value,
                13,
                R.color.app_text_primary,
                true
        );
        textView.setLineSpacing(dp(3), 1f);
        return textView;
    }

    @NonNull
    private TextView createText(
            @NonNull String text,
            int size,
            @ColorRes int color,
            boolean bold
    ) {
        TextView textView = new TextView(activity);
        textView.setText(text);
        textView.setTextSize(size);
        textView.setTextColor(color(color));
        if (bold) {
            textView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        return textView;
    }

    @NonNull
    private MaterialCardView createCard(
            @ColorRes int surface,
            @ColorRes int outline,
            int radiusDp
    ) {
        MaterialCardView card = new MaterialCardView(activity);
        card.setCardBackgroundColor(color(surface));
        card.setStrokeColor(color(outline));
        card.setStrokeWidth(dp(1));
        card.setRadius(dp(radiusDp));
        card.setCardElevation(0f);
        return card;
    }

    @NonNull
    private LinearLayout verticalContent(int paddingDp) {
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(
                dp(paddingDp),
                dp(paddingDp),
                dp(paddingDp),
                dp(paddingDp)
        );
        return content;
    }

    @NonNull
    private LinearLayout createHorizontalRow() {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBaselineAligned(false);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        return row;
    }

    @NonNull
    private LinearLayout.LayoutParams weightedCardParams(boolean second) {
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                );
        if (second) {
            params.setMargins(dp(5), 0, 0, 0);
        } else {
            params.setMargins(0, 0, dp(5), 0);
        }
        return params;
    }

    @NonNull
    private LinearLayout.LayoutParams weightedButtonParams(boolean second) {
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        0,
                        dp(50),
                        1f
                );
        if (second) {
            params.setMargins(dp(5), 0, 0, 0);
        } else {
            params.setMargins(0, 0, dp(5), 0);
        }
        return params;
    }

    @NonNull
    private MaterialButton createActionButton(
            @NonNull String text,
            @ColorRes int tint
    ) {
        MaterialButton button = new MaterialButton(activity);
        button.setText(text);
        button.setTextSize(11);
        button.setTextColor(color(R.color.white));
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setCornerRadius(dp(16));
        button.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(
                        color(tint)
                )
        );
        button.setInsetTop(0);
        button.setInsetBottom(0);
        return button;
    }

    private void setTopMargin(
            @NonNull View view,
            int marginDp
    ) {
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        params.setMargins(0, dp(marginDp), 0, 0);
        view.setLayoutParams(params);
    }

    private int color(@ColorRes int colorResource) {
        return ContextCompat.getColor(activity, colorResource);
    }

    private int dp(int value) {
        return Math.round(
                value
                        * activity.getResources()
                        .getDisplayMetrics()
                        .density
        );
    }

    @NonNull
    private String money(double value) {
        NumberFormat format = NumberFormat.getCurrencyInstance(
                new Locale("en", "IN")
        );
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return format.format(Math.abs(value));
    }

    @NonNull
    private String signedMoney(double value) {
        if (value > 0.0001d) {
            return "+" + money(value);
        }
        if (value < -0.0001d) {
            return "-" + money(value);
        }
        return money(0);
    }

    @NonNull
    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class DashboardData {
        private String periodLabel = "";
        private double income;
        private double expense;
        private double cashFlow;
        private double cardPayments;
        private double netAvailableCash;
        private double savingTarget;
        private double savingProgress;
        private String topCategory = "";
        private double topCategoryAmount;
        private double projectedExpense;
        private int budgetCount;
        private double budgetLimit;
        private double budgetSpent;
        private double budgetUsage;
    }
}
