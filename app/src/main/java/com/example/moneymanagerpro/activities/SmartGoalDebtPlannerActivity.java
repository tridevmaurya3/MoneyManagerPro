package com.example.moneymanagerpro.activities;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.database.AppDatabase;
import com.example.moneymanagerpro.database.DatabaseClient;
import com.example.moneymanagerpro.planner.SmartGoalDebtPlannerEngine;
import com.example.moneymanagerpro.utils.BubbleTouchAnimator;
import com.example.moneymanagerpro.utils.ThemeManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputLayout;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SmartGoalDebtPlannerActivity extends AppCompatActivity {

    private LinearLayout content;
    private LinearLayout resultContainer;
    private MaterialAutoCompleteTextView strategyDropdown;
    private EditText extraPaymentInput;
    private MaterialButton generateButton;
    private final SmartGoalDebtPlannerEngine engine = new SmartGoalDebtPlannerEngine();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applySavedTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(buildScreen());
        generatePlan();
    }

    private View buildScreen() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(color(R.color.app_background));

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(18), dp(18), dp(28));
        scrollView.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = text("‹", 32, R.color.app_text_primary, false);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(view -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(42), dp(42)));

        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        headingParams.setMargins(dp(6), 0, 0, 0);
        heading.setLayoutParams(headingParams);
        heading.addView(text("Smart Goal & Debt Planner", 21, R.color.app_text_primary, true));
        heading.addView(text("Offline payoff strategy and savings forecast", 11, R.color.app_text_secondary, false));
        header.addView(heading);
        content.addView(header);

        MaterialCardView controls = card(R.color.info_surface, R.color.info_outline);
        LinearLayout controlContent = new LinearLayout(this);
        controlContent.setOrientation(LinearLayout.VERTICAL);
        controlContent.setPadding(dp(15), dp(15), dp(15), dp(15));
        controlContent.addView(text("Planning Method", 15, R.color.secondary, true));
        controlContent.addView(text(
                "Choose Snowball, Avalanche, EMI Relief, Highest Balance or Quick Utilization Win. The payoff order and forecast are recalculated for the selected method.",
                11, R.color.app_text_secondary, false
        ));

        HorizontalScrollView strategyScroll = new HorizontalScrollView(this);
        strategyScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout strategyChips = new LinearLayout(this);
        strategyChips.setOrientation(LinearLayout.HORIZONTAL);
        String[] shortStrategies = {"Snowball", "Avalanche", "EMI Relief", "Highest Balance", "Quick Win"};
        for (String strategy : shortStrategies) {
            MaterialButton chip = button(strategy);
            chip.setTextSize(10);
            chip.setTextColor(color(R.color.secondary));
            chip.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color(R.color.info_surface)));
            chip.setStrokeColor(android.content.res.ColorStateList.valueOf(color(R.color.info_outline)));
            chip.setStrokeWidth(dp(1));
            LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(38));
            chipParams.setMargins(0, dp(10), dp(6), 0);
            chip.setLayoutParams(chipParams);
            chip.setOnClickListener(view -> selectStrategy(strategy));
            strategyChips.addView(chip);
        }
        strategyScroll.addView(strategyChips);
        controlContent.addView(strategyScroll);

        TextInputLayout strategyLayout = new TextInputLayout(this);
        strategyLayout.setHint("Debt payoff strategy");
        strategyLayout.setEndIconMode(TextInputLayout.END_ICON_DROPDOWN_MENU);
        LinearLayout.LayoutParams fieldParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        fieldParams.setMargins(0, dp(12), 0, 0);
        strategyLayout.setLayoutParams(fieldParams);
        strategyDropdown = new MaterialAutoCompleteTextView(this);
        strategyDropdown.setInputType(0);
        List<String> strategies = new ArrayList<>();
        strategies.add("Snowball — smallest debt first");
        strategies.add("Avalanche — highest interest first");
        strategies.add("EMI Relief — release highest EMI first");
        strategies.add("Highest Balance — largest debt first");
        strategies.add("Quick Win — lowest balance-to-EMI ratio");
        strategyDropdown.setSimpleItems(strategies.toArray(new String[0]));
        strategyDropdown.setText(strategies.get(0), false);
        strategyLayout.addView(strategyDropdown);
        controlContent.addView(strategyLayout);

        TextInputLayout extraLayout = new TextInputLayout(this);
        extraLayout.setHint("Extra monthly debt payment (optional)");
        extraLayout.setPrefixText("₹ ");
        extraLayout.setLayoutParams(fieldParams);
        extraPaymentInput = new EditText(this);
        extraPaymentInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        extraLayout.addView(extraPaymentInput);
        controlContent.addView(extraLayout);

        generateButton = button("Generate Smart Plan");
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50)
        );
        buttonParams.setMargins(0, dp(12), 0, 0);
        generateButton.setLayoutParams(buttonParams);
        generateButton.setOnClickListener(view -> generatePlan());
        BubbleTouchAnimator.apply(generateButton);
        controlContent.addView(generateButton);
        controls.addView(controlContent);
        LinearLayout.LayoutParams controlsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        controlsParams.setMargins(0, dp(16), 0, dp(14));
        controls.setLayoutParams(controlsParams);
        content.addView(controls);

        resultContainer = new LinearLayout(this);
        resultContainer.setOrientation(LinearLayout.VERTICAL);
        content.addView(resultContainer);
        return scrollView;
    }

    private void selectStrategy(String strategy) {
        if (strategy.startsWith("Avalanche")) {
            strategyDropdown.setText("Avalanche — highest interest first", false);
        } else if (strategy.startsWith("EMI Relief")) {
            strategyDropdown.setText("EMI Relief — release highest EMI first", false);
        } else if (strategy.startsWith("Highest Balance")) {
            strategyDropdown.setText("Highest Balance — largest debt first", false);
        } else if (strategy.startsWith("Quick Win")) {
            strategyDropdown.setText("Quick Win — lowest balance-to-EMI ratio", false);
        } else {
            strategyDropdown.setText("Snowball — smallest debt first", false);
        }
        generatePlan();
    }

    private void generatePlan() {
        generateButton.setEnabled(false);
        generateButton.setText("Calculating...");
        resultContainer.removeAllViews();
        resultContainer.addView(text("Reading local finance data...", 12, R.color.app_text_secondary, false));

        double extra = 0d;
        try {
            String value = extraPaymentInput.getText() == null ? "" : extraPaymentInput.getText().toString().trim();
            if (!value.isEmpty()) extra = Double.parseDouble(value);
        } catch (Exception ignored) {
            extra = 0d;
        }
        final double requestedExtra = Math.max(0d, extra);
        String selectedStrategy = strategyDropdown.getText().toString();
        final SmartGoalDebtPlannerEngine.Strategy strategy;
        if (selectedStrategy.startsWith("Avalanche")) {
            strategy = SmartGoalDebtPlannerEngine.Strategy.AVALANCHE;
        } else if (selectedStrategy.startsWith("EMI Relief")) {
            strategy = SmartGoalDebtPlannerEngine.Strategy.HIGHEST_EMI_RELIEF;
        } else if (selectedStrategy.startsWith("Highest Balance")) {
            strategy = SmartGoalDebtPlannerEngine.Strategy.HIGHEST_BALANCE_FIRST;
        } else if (selectedStrategy.startsWith("Quick Win")) {
            strategy = SmartGoalDebtPlannerEngine.Strategy.LOWEST_UTILIZATION_WIN;
        } else {
            strategy = SmartGoalDebtPlannerEngine.Strategy.SNOWBALL;
        }

        new Thread(() -> {
            AppDatabase database = DatabaseClient.getInstance(getApplicationContext()).getAppDatabase();
            SmartGoalDebtPlannerEngine.Plan plan = engine.buildPlan(
                    database.loanDao().getActiveLoans(),
                    database.goalDao().getAllGoals(),
                    database.transactionDao().getAllTransactions(),
                    requestedExtra,
                    strategy
            );
            runOnUiThread(() -> renderPlan(plan));
        }).start();
    }

    private void renderPlan(@NonNull SmartGoalDebtPlannerEngine.Plan plan) {
        resultContainer.removeAllViews();
        generateButton.setEnabled(true);
        generateButton.setText("Refresh Smart Plan");

        resultContainer.addView(summaryCard(plan));
        resultContainer.addView(sectionHeading("Debt Payoff Order"));

        if (plan.getDebts().isEmpty()) {
            resultContainer.addView(emptyCard("No active Loan Taken balance found. Add or update loans to create a payoff plan."));
        } else {
            int rank = 1;
            for (SmartGoalDebtPlannerEngine.DebtItem debt : plan.getDebts()) {
                resultContainer.addView(debtCard(rank++, debt));
            }
        }

        resultContainer.addView(sectionHeading("Goal Completion Forecast"));
        if (plan.getGoals().isEmpty()) {
            resultContainer.addView(emptyCard("No incomplete savings goal found. Create a goal to receive a monthly contribution forecast."));
        } else {
            for (SmartGoalDebtPlannerEngine.GoalItem goal : plan.getGoals()) {
                resultContainer.addView(goalCard(goal));
            }
        }

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        MaterialButton loans = button("Manage Loans");
        MaterialButton goals = button("Manage Goals");
        loans.setOnClickListener(view -> startActivity(new Intent(this, LoanActivity.class)));
        goals.setOnClickListener(view -> startActivity(new Intent(this, GoalActivity.class)));
        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, dp(48), 1f);
        left.setMargins(0, dp(10), dp(5), 0);
        LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, dp(48), 1f);
        right.setMargins(dp(5), dp(10), 0, 0);
        loans.setLayoutParams(left);
        goals.setLayoutParams(right);
        actions.addView(loans);
        actions.addView(goals);
        resultContainer.addView(actions);
    }

    private View summaryCard(SmartGoalDebtPlannerEngine.Plan plan) {
        MaterialCardView card = card(R.color.success_surface, R.color.success_outline);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(15), dp(15), dp(15), dp(15));
        box.addView(text("Recommended Monthly Action", 16, R.color.success, true));
        box.addView(text(
                "Average surplus: " + money(plan.getObservedSurplus())
                        + "  •  Extra debt payment: " + money(plan.getSuggestedExtraPayment()),
                11, R.color.app_text_secondary, false
        ));

        SmartGoalDebtPlannerEngine.Simulation normal = plan.getNormalPlan();
        SmartGoalDebtPlannerEngine.Simulation accelerated = plan.getAcceleratedPlan();
        box.addView(metricRow("Estimated debt-free date", accelerated.getPayoffDate(), R.color.success));
        box.addView(metricRow("Time with recommended plan", accelerated.getMonths() + " months", R.color.secondary));
        box.addView(metricRow("Estimated interest remaining", money(accelerated.getInterest()), R.color.expense));
        int monthsSaved = Math.max(0, normal.getMonths() - accelerated.getMonths());
        double interestSaved = Math.max(0d, normal.getInterest() - accelerated.getInterest());
        box.addView(text(
                "Estimated benefit: " + monthsSaved + " months sooner and " + money(interestSaved) + " less interest.",
                11, R.color.app_text_primary, true
        ));
        box.addView(text(
                "Estimates use recorded balances, rates and recent three-month cash flow. Actual lender statements remain authoritative.",
                10, R.color.app_text_secondary, false
        ));
        card.addView(box);
        return card;
    }

    private View debtCard(int rank, SmartGoalDebtPlannerEngine.DebtItem debt) {
        MaterialCardView card = card(R.color.expense_surface, R.color.expense_outline);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(13), dp(14), dp(13));
        box.addView(text(rank + ". " + debt.getName(), 15, R.color.app_text_primary, true));
        box.addView(text(
                "Outstanding " + money(debt.getBalance())
                        + "  •  Rate " + String.format(Locale.US, "%.2f%%", debt.getAnnualRate())
                        + "  •  Minimum " + money(debt.getMinimumPayment()),
                11, R.color.app_text_secondary, false
        ));
        card.addView(box);
        return card;
    }

    private View goalCard(SmartGoalDebtPlannerEngine.GoalItem goal) {
        MaterialCardView card = card(
                goal.isOnTrack() ? R.color.success_surface : R.color.warning_surface,
                goal.isOnTrack() ? R.color.success_outline : R.color.warning_outline
        );
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(13), dp(14), dp(13));
        box.addView(text(goal.getName(), 15, R.color.app_text_primary, true));
        box.addView(text(
                "Remaining " + money(goal.getRemaining()) + "  •  Target " + goal.getTargetDate(),
                11, R.color.app_text_secondary, false
        ));
        box.addView(text(
                "Required monthly: " + money(goal.getRequiredMonthly())
                        + "  •  Recommended now: " + money(goal.getRecommendedMonthly()),
                11, goal.isOnTrack() ? R.color.success : R.color.warning, true
        ));
        String estimate = goal.getEstimatedMonths() == Integer.MAX_VALUE
                ? "No completion estimate until monthly surplus is available."
                : "Estimated completion in about " + goal.getEstimatedMonths() + " months.";
        box.addView(text(estimate, 10, R.color.app_text_secondary, false));
        card.addView(box);
        return card;
    }

    private View emptyCard(String message) {
        MaterialCardView card = card(R.color.info_surface, R.color.info_outline);
        TextView view = text(message, 11, R.color.app_text_secondary, false);
        view.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.addView(view);
        return card;
    }

    private TextView sectionHeading(String title) {
        TextView view = text(title, 17, R.color.app_text_primary, true);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, dp(16), 0, dp(8));
        view.setLayoutParams(params);
        return view;
    }

    private View metricRow(String label, String value, @ColorRes int valueColor) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, dp(8), 0, 0);
        row.setLayoutParams(params);
        row.addView(text(label, 11, R.color.app_text_secondary, false), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(text(value, 13, valueColor, true));
        return row;
    }

    private MaterialCardView card(@ColorRes int background, @ColorRes int stroke) {
        MaterialCardView card = new MaterialCardView(this);
        card.setCardBackgroundColor(color(background));
        card.setStrokeColor(color(stroke));
        card.setStrokeWidth(dp(1));
        card.setRadius(dp(18));
        card.setCardElevation(dp(1));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, dp(10));
        card.setLayoutParams(params);
        return card;
    }

    private MaterialButton button(String label) {
        MaterialButton button = new MaterialButton(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(12);
        button.setCornerRadius(dp(14));
        return button;
    }

    private TextView text(String value, int size, @ColorRes int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color(color));
        view.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        return view;
    }

    private String money(double amount) {
        NumberFormat format = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));
        format.setMaximumFractionDigits(0);
        return format.format(Math.max(0d, amount));
    }

    private int color(@ColorRes int resource) {
        return ContextCompat.getColor(this, resource);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
