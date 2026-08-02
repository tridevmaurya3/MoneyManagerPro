package com.example.moneymanagerpro.budget;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.database.DatabaseClient;
import com.example.moneymanagerpro.model.Budget;
import com.example.moneymanagerpro.model.Transaction;
import com.example.moneymanagerpro.utils.BubbleTouchAnimator;
import com.example.moneymanagerpro.utils.BudgetAlertScheduler;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

/**
 * Adds an offline AI Budget Planner card to the existing BudgetActivity.
 * Existing manual budget controls remain unchanged.
 */
public final class AiBudgetPlannerUiController {

    private static final String ROOT_TAG =
            "money_manager_ai_budget_planner_root";

    private final Activity activity;
    private final AiBudgetPlannerEngine plannerEngine =
            new AiBudgetPlannerEngine();

    private LinearLayout plannerRoot;
    private LinearLayout suggestionContainer;
    private TextView txtPlannerSummary;
    private TextView txtPlannerStatus;
    private MaterialButton btnGeneratePlan;
    private MaterialButton btnApplyCompletePlan;

    private AiBudgetPlannerEngine.Plan currentPlan;
    private boolean operationInProgress;

    public AiBudgetPlannerUiController(
            @NonNull Activity activity
    ) {
        this.activity = activity;
    }

    public void attach() {
        if (activity.isFinishing() || activity.isDestroyed()) {
            return;
        }

        View existing = findViewWithTag(
                activity.findViewById(android.R.id.content),
                ROOT_TAG
        );

        if (existing instanceof LinearLayout) {
            plannerRoot = (LinearLayout) existing;
            return;
        }

        LinearLayout pageContainer = findPageContainer();
        if (pageContainer == null) {
            return;
        }

        plannerRoot = buildPlannerSection();
        plannerRoot.setTag(ROOT_TAG);

        int insertAt = Math.min(4, pageContainer.getChildCount());
        pageContainer.addView(plannerRoot, insertAt);
    }

    private LinearLayout findPageContainer() {
        View content = activity.findViewById(android.R.id.content);
        NestedScrollView scrollView = findNestedScrollView(content);

        if (scrollView == null || scrollView.getChildCount() == 0) {
            return null;
        }

        View child = scrollView.getChildAt(0);
        return child instanceof LinearLayout
                ? (LinearLayout) child
                : null;
    }

    private NestedScrollView findNestedScrollView(View view) {
        if (view instanceof NestedScrollView) {
            return (NestedScrollView) view;
        }

        if (!(view instanceof ViewGroup)) {
            return null;
        }

        ViewGroup group = (ViewGroup) view;
        for (int index = 0; index < group.getChildCount(); index++) {
            NestedScrollView result =
                    findNestedScrollView(group.getChildAt(index));
            if (result != null) {
                return result;
            }
        }

        return null;
    }

    private View findViewWithTag(View view, String tag) {
        if (view == null) {
            return null;
        }

        if (tag.equals(view.getTag())) {
            return view;
        }

        if (!(view instanceof ViewGroup)) {
            return null;
        }

        ViewGroup group = (ViewGroup) view;
        for (int index = 0; index < group.getChildCount(); index++) {
            View result = findViewWithTag(group.getChildAt(index), tag);
            if (result != null) {
                return result;
            }
        }

        return null;
    }

    private LinearLayout buildPlannerSection() {
        LinearLayout section = new LinearLayout(activity);
        section.setOrientation(LinearLayout.VERTICAL);

        LinearLayout.LayoutParams sectionParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        sectionParams.setMargins(0, dp(23), 0, 0);
        section.setLayoutParams(sectionParams);

        TextView heading = createText(
                "AI Suggested Monthly Plan",
                19,
                color(R.color.app_text_primary),
                true
        );
        section.addView(heading);

        TextView subtitle = createText(
                "Private offline recommendations from your last three months of transactions",
                11,
                color(R.color.app_text_secondary),
                false
        );
        LinearLayout.LayoutParams subtitleParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        subtitleParams.setMargins(0, dp(3), 0, dp(11));
        subtitle.setLayoutParams(subtitleParams);
        section.addView(subtitle);

        MaterialCardView card = new MaterialCardView(activity);
        card.setCardBackgroundColor(Color.parseColor("#F5F1FF"));
        card.setStrokeColor(Color.parseColor("#D8C9F2"));
        card.setStrokeWidth(dp(1));
        card.setRadius(dp(20));
        card.setCardElevation(dp(1));

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(16), dp(16), dp(16));

        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView icon = createText(
                "✦",
                23,
                color(R.color.purple),
                true
        );
        icon.setGravity(Gravity.CENTER);
        header.addView(
                icon,
                new LinearLayout.LayoutParams(dp(46), dp(46))
        );

        LinearLayout titleContainer = new LinearLayout(activity);
        titleContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams titleParams =
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                );
        titleParams.setMargins(dp(12), 0, 0, 0);
        titleContainer.setLayoutParams(titleParams);

        titleContainer.addView(createText(
                "Smart Budget Planner",
                17,
                color(R.color.purple),
                true
        ));

        txtPlannerStatus = createText(
                "Generate a plan to analyse your recent income and category spending.",
                10,
                color(R.color.app_text_secondary),
                false
        );
        LinearLayout.LayoutParams statusParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        statusParams.setMargins(0, dp(3), 0, 0);
        txtPlannerStatus.setLayoutParams(statusParams);
        titleContainer.addView(txtPlannerStatus);
        header.addView(titleContainer);
        content.addView(header);

        txtPlannerSummary = createText(
                "No plan generated yet.",
                11,
                color(R.color.app_text_secondary),
                false
        );
        txtPlannerSummary.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams summaryParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        summaryParams.setMargins(0, dp(14), 0, 0);
        txtPlannerSummary.setLayoutParams(summaryParams);
        content.addView(txtPlannerSummary);

        btnGeneratePlan = new MaterialButton(activity);
        btnGeneratePlan.setText("Generate AI Budget Plan");
        btnGeneratePlan.setAllCaps(false);
        btnGeneratePlan.setTextColor(Color.WHITE);
        btnGeneratePlan.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(
                        color(R.color.purple)
                )
        );
        btnGeneratePlan.setCornerRadius(dp(17));
        LinearLayout.LayoutParams generateParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(54)
                );
        generateParams.setMargins(0, dp(15), 0, 0);
        btnGeneratePlan.setLayoutParams(generateParams);
        content.addView(btnGeneratePlan);

        suggestionContainer = new LinearLayout(activity);
        suggestionContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams suggestionParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        suggestionParams.setMargins(0, dp(13), 0, 0);
        suggestionContainer.setLayoutParams(suggestionParams);
        content.addView(suggestionContainer);

        btnApplyCompletePlan = new MaterialButton(activity);
        btnApplyCompletePlan.setText("Apply Complete Monthly Plan");
        btnApplyCompletePlan.setAllCaps(false);
        btnApplyCompletePlan.setTextColor(color(R.color.purple));
        btnApplyCompletePlan.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(
                        color(R.color.app_surface)
                )
        );
        btnApplyCompletePlan.setStrokeColor(
                android.content.res.ColorStateList.valueOf(
                        color(R.color.purple)
                )
        );
        btnApplyCompletePlan.setStrokeWidth(dp(1));
        btnApplyCompletePlan.setCornerRadius(dp(17));
        btnApplyCompletePlan.setVisibility(View.GONE);
        LinearLayout.LayoutParams applyAllParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(52)
                );
        applyAllParams.setMargins(0, dp(8), 0, 0);
        btnApplyCompletePlan.setLayoutParams(applyAllParams);
        content.addView(btnApplyCompletePlan);

        card.addView(content);
        section.addView(card);

        btnGeneratePlan.setOnClickListener(view -> generatePlan());
        btnApplyCompletePlan.setOnClickListener(view -> applyCompletePlan());

        BubbleTouchAnimator.apply(btnGeneratePlan);
        BubbleTouchAnimator.apply(btnApplyCompletePlan);

        return section;
    }

    private void generatePlan() {
        if (operationInProgress) {
            return;
        }

        operationInProgress = true;
        setBusyState(true, "Analysing transactions...");
        suggestionContainer.removeAllViews();
        btnApplyCompletePlan.setVisibility(View.GONE);

        new Thread(() -> {
            try {
                List<Transaction> transactions =
                        DatabaseClient
                                .getInstance(activity.getApplicationContext())
                                .getAppDatabase()
                                .transactionDao()
                                .getAllTransactions();

                AiBudgetPlannerEngine.Plan plan =
                        plannerEngine.buildPlan(transactions);

                activity.runOnUiThread(() -> {
                    operationInProgress = false;
                    currentPlan = plan;
                    setBusyState(false, "Refresh AI Plan");
                    showPlan(plan);
                });
            } catch (Exception exception) {
                activity.runOnUiThread(() -> {
                    operationInProgress = false;
                    currentPlan = null;
                    setBusyState(false, "Generate AI Budget Plan");
                    txtPlannerStatus.setText("Plan generation failed");
                    txtPlannerSummary.setText(
                            "Transactions could not be analysed. Existing budgets were not changed."
                    );
                    Toast.makeText(
                            activity,
                            "Unable to generate AI budget plan",
                            Toast.LENGTH_SHORT
                    ).show();
                });
            }
        }).start();
    }

    private void showPlan(
            @NonNull AiBudgetPlannerEngine.Plan plan
    ) {
        txtPlannerStatus.setText(
                plan.getAnalysedTransactionCount()
                        + " recent transactions analysed privately on this device"
        );

        txtPlannerSummary.setText(
                "Average monthly income: "
                        + money(plan.getAverageMonthlyIncome())
                        + "\nAverage monthly expense: "
                        + money(plan.getAverageMonthlyExpense())
                        + "\nRecommended saving target: "
                        + money(plan.getTargetSaving())
                        + "\nSafe expense pool: "
                        + money(plan.getAvailableExpensePool())
        );

        suggestionContainer.removeAllViews();

        if (plan.getSuggestions().isEmpty()) {
            TextView empty = createText(
                    "Add more expense transactions to generate category-wise budget suggestions.",
                    11,
                    color(R.color.app_text_secondary),
                    false
            );
            suggestionContainer.addView(empty);
            btnApplyCompletePlan.setVisibility(View.GONE);
            return;
        }

        for (AiBudgetPlannerEngine.Suggestion suggestion
                : plan.getSuggestions()) {
            suggestionContainer.addView(
                    buildSuggestionCard(suggestion)
            );
        }

        btnApplyCompletePlan.setVisibility(View.VISIBLE);
    }

    private View buildSuggestionCard(
            @NonNull AiBudgetPlannerEngine.Suggestion suggestion
    ) {
        MaterialCardView card = new MaterialCardView(activity);
        card.setCardBackgroundColor(color(R.color.app_surface));
        card.setStrokeColor(color(R.color.purple_outline));
        card.setStrokeWidth(dp(1));
        card.setRadius(dp(16));
        card.setCardElevation(0f);

        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        cardParams.setMargins(0, 0, 0, dp(9));
        card.setLayoutParams(cardParams);

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(13), dp(12), dp(13), dp(12));

        LinearLayout topRow = new LinearLayout(activity);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView category = createText(
                suggestion.getCategory(),
                14,
                color(R.color.app_text_primary),
                true
        );
        topRow.addView(
                category,
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                )
        );

        TextView recommendation = createText(
                money(suggestion.getRecommendedLimit()),
                16,
                color(R.color.purple),
                true
        );
        topRow.addView(recommendation);
        content.addView(topRow);

        TextView details = createText(
                "Recent average "
                        + money(suggestion.getRecentMonthlyAverage())
                        + " • Confidence "
                        + suggestion.getConfidencePercent()
                        + "%\n"
                        + suggestion.getReason(),
                10,
                color(R.color.app_text_secondary),
                false
        );
        details.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams detailParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        detailParams.setMargins(0, dp(5), 0, 0);
        details.setLayoutParams(detailParams);
        content.addView(details);

        MaterialButton applyButton = new MaterialButton(activity);
        applyButton.setText("Apply This Suggestion");
        applyButton.setAllCaps(false);
        applyButton.setTextColor(color(R.color.purple));
        applyButton.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(
                        Color.TRANSPARENT
                )
        );
        applyButton.setStrokeColor(
                android.content.res.ColorStateList.valueOf(
                        color(R.color.purple_outline)
                )
        );
        applyButton.setStrokeWidth(dp(1));
        applyButton.setCornerRadius(dp(14));
        LinearLayout.LayoutParams buttonParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(46)
                );
        buttonParams.setMargins(0, dp(9), 0, 0);
        applyButton.setLayoutParams(buttonParams);
        content.addView(applyButton);

        applyButton.setOnClickListener(
                view -> applySuggestion(suggestion, applyButton)
        );
        BubbleTouchAnimator.apply(applyButton);

        card.addView(content);
        return card;
    }

    private void applySuggestion(
            @NonNull AiBudgetPlannerEngine.Suggestion suggestion,
            @NonNull MaterialButton button
    ) {
        if (operationInProgress) {
            return;
        }

        operationInProgress = true;
        button.setEnabled(false);
        button.setText("Applying...");

        new Thread(() -> {
            Exception failure = null;
            try {
                saveMonthlyBudget(suggestion);
            } catch (Exception exception) {
                failure = exception;
            }

            Exception finalFailure = failure;
            activity.runOnUiThread(() -> {
                operationInProgress = false;
                button.setEnabled(true);
                button.setText(
                        finalFailure == null
                                ? "Applied Successfully"
                                : "Apply This Suggestion"
                );

                if (finalFailure == null) {
                    BudgetAlertScheduler.schedule(
                            activity.getApplicationContext()
                    );
                    Toast.makeText(
                            activity,
                            suggestion.getCategory()
                                    + " monthly budget applied",
                            Toast.LENGTH_SHORT
                    ).show();
                } else {
                    Toast.makeText(
                            activity,
                            "Unable to apply suggestion",
                            Toast.LENGTH_SHORT
                    ).show();
                }
            });
        }).start();
    }

    private void applyCompletePlan() {
        if (operationInProgress
                || currentPlan == null
                || currentPlan.getSuggestions().isEmpty()) {
            return;
        }

        operationInProgress = true;
        btnApplyCompletePlan.setEnabled(false);
        btnApplyCompletePlan.setText("Applying Complete Plan...");

        new Thread(() -> {
            int applied = 0;
            Exception failure = null;

            try {
                for (AiBudgetPlannerEngine.Suggestion suggestion
                        : currentPlan.getSuggestions()) {
                    saveMonthlyBudget(suggestion);
                    applied++;
                }
            } catch (Exception exception) {
                failure = exception;
            }

            int finalApplied = applied;
            Exception finalFailure = failure;

            activity.runOnUiThread(() -> {
                operationInProgress = false;
                btnApplyCompletePlan.setEnabled(true);
                btnApplyCompletePlan.setText(
                        "Apply Complete Monthly Plan"
                );

                if (finalFailure == null) {
                    BudgetAlertScheduler.schedule(
                            activity.getApplicationContext()
                    );
                    Toast.makeText(
                            activity,
                            finalApplied
                                    + " monthly budgets applied successfully",
                            Toast.LENGTH_LONG
                    ).show();
                } else {
                    Toast.makeText(
                            activity,
                            "Applied "
                                    + finalApplied
                                    + " budgets before an error occurred",
                            Toast.LENGTH_LONG
                    ).show();
                }
            });
        }).start();
    }

    private void saveMonthlyBudget(
            @NonNull AiBudgetPlannerEngine.Suggestion suggestion
    ) {
        Budget existing =
                DatabaseClient
                        .getInstance(activity.getApplicationContext())
                        .getAppDatabase()
                        .budgetDao()
                        .getBudgetForCategory(
                                suggestion.getCategory(),
                                "Monthly"
                        );

        if (existing == null) {
            Budget budget = new Budget();
            budget.setCategory(suggestion.getCategory());
            budget.setPeriod("Monthly");
            budget.setLimitAmount(suggestion.getRecommendedLimit());

            DatabaseClient
                    .getInstance(activity.getApplicationContext())
                    .getAppDatabase()
                    .budgetDao()
                    .insert(budget);
        } else {
            existing.setLimitAmount(suggestion.getRecommendedLimit());

            DatabaseClient
                    .getInstance(activity.getApplicationContext())
                    .getAppDatabase()
                    .budgetDao()
                    .update(existing);
        }
    }

    private void setBusyState(
            boolean busy,
            @NonNull String buttonText
    ) {
        btnGeneratePlan.setEnabled(!busy);
        btnGeneratePlan.setText(buttonText);
        btnGeneratePlan.setAlpha(busy ? 0.62f : 1f);
    }

    private TextView createText(
            @NonNull String text,
            int size,
            int textColor,
            boolean bold
    ) {
        TextView textView = new TextView(activity);
        textView.setText(text);
        textView.setTextSize(size);
        textView.setTextColor(textColor);
        textView.setTypeface(
                Typeface.DEFAULT,
                bold ? Typeface.BOLD : Typeface.NORMAL
        );
        return textView;
    }

    private String money(double amount) {
        NumberFormat formatter =
                NumberFormat.getCurrencyInstance(
                        new Locale("en", "IN")
                );
        formatter.setMinimumFractionDigits(0);
        formatter.setMaximumFractionDigits(0);
        return formatter.format(Math.max(0.0d, amount));
    }

    private int color(int resource) {
        return ContextCompat.getColor(activity, resource);
    }

    private int dp(int value) {
        return Math.round(
                value
                        * activity
                        .getResources()
                        .getDisplayMetrics()
                        .density
        );
    }
}
