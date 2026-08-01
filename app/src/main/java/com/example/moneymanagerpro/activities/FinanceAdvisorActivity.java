package com.example.moneymanagerpro.activities;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.assistant.SmartTransactionAssistantEngine;
import com.example.moneymanagerpro.database.DatabaseClient;
import com.example.moneymanagerpro.model.Transaction;
import com.example.moneymanagerpro.utils.BubbleTouchAnimator;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

/**
 * Offline Smart Transaction Assistant.
 *
 * It provides monthly money-health analysis, duplicate detection, unusual
 * expense warnings, category suggestions and natural-language answers without
 * sending private finance data to a server.
 */
public class FinanceAdvisorActivity
        extends AppCompatActivity {

    private TextView txtAssistantPeriod;
    private TextView txtHealthScore;
    private TextView txtHealthLabel;
    private TextView txtIncome;
    private TextView txtExpense;
    private TextView txtSaving;
    private TextView txtDuplicateCount;
    private TextView txtUnusualCount;
    private TextView txtTopCategory;
    private TextView txtOverview;
    private TextView txtAnswerTitle;
    private TextView txtAnswer;

    private TextInputLayout inputAssistantQuery;
    private TextInputEditText etAssistantQuery;

    private MaterialCardView cardAssistantAnswer;

    private MaterialButton btnAskAssistant;
    private MaterialButton btnQuickExpense;
    private MaterialButton btnQuickCategory;
    private MaterialButton btnQuickDuplicates;
    private MaterialButton btnQuickUnusual;
    private MaterialButton btnRefreshAssistant;
    private MaterialButton btnOpenTransactions;

    private LinearLayout alertContainer;
    private LinearLayout recommendationContainer;

    private final SmartTransactionAssistantEngine assistantEngine =
            new SmartTransactionAssistantEngine();

    private SmartTransactionAssistantEngine.Analysis currentAnalysis;

    private int analysisRequestVersion = 0;
    private boolean analysisLoading = false;

    @Override
    protected void onCreate(
            Bundle savedInstanceState
    ) {
        super.onCreate(savedInstanceState);

        setContentView(
                R.layout.activity_finance_advisor
        );

        bindViews();
        setupActions();
        showLoadingState();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadAnalysis();
    }

    private void bindViews() {
        TextView btnBack =
                findViewById(
                        R.id.btnBack
                );

        txtAssistantPeriod =
                findViewById(
                        R.id.txtAssistantPeriod
                );

        txtHealthScore =
                findViewById(
                        R.id.txtHealthScore
                );

        txtHealthLabel =
                findViewById(
                        R.id.txtHealthLabel
                );

        txtIncome =
                findViewById(
                        R.id.txtAssistantIncome
                );

        txtExpense =
                findViewById(
                        R.id.txtAssistantExpense
                );

        txtSaving =
                findViewById(
                        R.id.txtAssistantSaving
                );

        txtDuplicateCount =
                findViewById(
                        R.id.txtDuplicateCount
                );

        txtUnusualCount =
                findViewById(
                        R.id.txtUnusualCount
                );

        txtTopCategory =
                findViewById(
                        R.id.txtTopCategory
                );

        txtOverview =
                findViewById(
                        R.id.txtAssistantOverview
                );

        txtAnswerTitle =
                findViewById(
                        R.id.txtAssistantAnswerTitle
                );

        txtAnswer =
                findViewById(
                        R.id.txtAssistantAnswer
                );

        inputAssistantQuery =
                findViewById(
                        R.id.inputAssistantQuery
                );

        etAssistantQuery =
                findViewById(
                        R.id.etAssistantQuery
                );

        cardAssistantAnswer =
                findViewById(
                        R.id.cardAssistantAnswer
                );

        btnAskAssistant =
                findViewById(
                        R.id.btnAskAssistant
                );

        btnQuickExpense =
                findViewById(
                        R.id.btnQuickExpense
                );

        btnQuickCategory =
                findViewById(
                        R.id.btnQuickCategory
                );

        btnQuickDuplicates =
                findViewById(
                        R.id.btnQuickDuplicates
                );

        btnQuickUnusual =
                findViewById(
                        R.id.btnQuickUnusual
                );

        btnRefreshAssistant =
                findViewById(
                        R.id.btnRefreshAssistant
                );

        btnOpenTransactions =
                findViewById(
                        R.id.btnOpenTransactions
                );

        alertContainer =
                findViewById(
                        R.id.assistantAlertContainer
                );

        recommendationContainer =
                findViewById(
                        R.id.recommendationContainer
                );

        btnBack.setOnClickListener(
                view -> finish()
        );

        BubbleTouchAnimator.apply(btnBack);
    }

    private void setupActions() {
        btnAskAssistant.setOnClickListener(
                view -> answerCurrentQuestion()
        );

        btnQuickExpense.setOnClickListener(
                view -> askQuickQuestion(
                        "How much did I spend this month?"
                )
        );

        btnQuickCategory.setOnClickListener(
                view -> askQuickQuestion(
                        "Which is my highest category this month?"
                )
        );

        btnQuickDuplicates.setOnClickListener(
                view -> askQuickQuestion(
                        "Show duplicate transactions"
                )
        );

        btnQuickUnusual.setOnClickListener(
                view -> askQuickQuestion(
                        "Show unusual high expenses"
                )
        );

        btnRefreshAssistant.setOnClickListener(
                view -> loadAnalysis()
        );

        btnOpenTransactions.setOnClickListener(
                view -> startActivity(
                        new Intent(
                                this,
                                TransactionsActivity.class
                        )
                )
        );

        etAssistantQuery.setOnEditorActionListener(
                (view, actionId, event) -> {
                    if (actionId == EditorInfo.IME_ACTION_DONE
                            || actionId == EditorInfo.IME_ACTION_GO
                            || actionId == EditorInfo.IME_ACTION_SEARCH) {

                        answerCurrentQuestion();
                        return true;
                    }

                    return false;
                }
        );

        BubbleTouchAnimator.apply(btnAskAssistant);
        BubbleTouchAnimator.apply(btnQuickExpense);
        BubbleTouchAnimator.apply(btnQuickCategory);
        BubbleTouchAnimator.apply(btnQuickDuplicates);
        BubbleTouchAnimator.apply(btnQuickUnusual);
        BubbleTouchAnimator.apply(btnRefreshAssistant);
        BubbleTouchAnimator.apply(btnOpenTransactions);
    }

    private void loadAnalysis() {
        if (analysisLoading) {
            return;
        }

        analysisLoading = true;

        int requestVersion =
                ++analysisRequestVersion;

        showLoadingState();

        new Thread(
                () -> {
                    try {
                        List<Transaction> transactions =
                                DatabaseClient
                                        .getInstance(
                                                getApplicationContext()
                                        )
                                        .getAppDatabase()
                                        .transactionDao()
                                        .getAllTransactions();

                        SmartTransactionAssistantEngine.Analysis analysis =
                                assistantEngine.analyse(
                                        transactions
                                );

                        runOnUiThread(
                                () -> {
                                    if (requestVersion
                                            != analysisRequestVersion
                                            || isFinishing()
                                            || isDestroyed()) {

                                        return;
                                    }

                                    analysisLoading = false;
                                    currentAnalysis = analysis;

                                    showAnalysis(
                                            analysis
                                    );
                                }
                        );

                    } catch (Exception exception) {
                        runOnUiThread(
                                () -> {
                                    if (requestVersion
                                            != analysisRequestVersion
                                            || isFinishing()
                                            || isDestroyed()) {

                                        return;
                                    }

                                    analysisLoading = false;
                                    currentAnalysis = null;

                                    showAnalysisFailure();
                                }
                        );
                    }
                }
        ).start();
    }

    private void showLoadingState() {
        txtAssistantPeriod.setText(
                "Analysing local transactions..."
        );

        txtHealthScore.setText("—");
        txtHealthLabel.setText(
                "Calculating money health"
        );

        txtIncome.setText("₹0.00");
        txtExpense.setText("₹0.00");
        txtSaving.setText("₹0.00");

        txtDuplicateCount.setText("—");
        txtUnusualCount.setText("—");
        txtTopCategory.setText("—");

        txtOverview.setText(
                "Income, expenses, categories, duplicate entries and unusual amounts are being reviewed privately on this device."
        );

        alertContainer.removeAllViews();
        recommendationContainer.removeAllViews();

        addDynamicCard(
                alertContainer,
                "Analysing transactions",
                "Smart alerts will appear after the local calculation completes.",
                R.color.purple,
                R.color.purple_surface,
                R.color.purple_outline
        );

        txtAnswerTitle.setText(
                "Assistant is getting ready"
        );

        txtAnswer.setText(
                "You can ask a question after the transaction analysis is complete."
        );

        styleAnswerCard(
                SmartTransactionAssistantEngine.AnswerTone.PURPLE
        );

        setAssistantControlsEnabled(
                false
        );
    }

    private void showAnalysis(
            @NonNull SmartTransactionAssistantEngine.Analysis analysis
    ) {
        SmartTransactionAssistantEngine.MonthMetrics current =
                analysis.getCurrentMonth();

        txtAssistantPeriod.setText(
                analysis.getCurrentMonthLabel()
                        + " • Offline private analysis"
        );

        txtHealthScore.setText(
                String.valueOf(
                        analysis.getHealthScore()
                )
        );

        txtHealthScore.setTextColor(
                getHealthColor(
                        analysis.getHealthScore()
                )
        );

        txtHealthLabel.setText(
                getHealthLabel(
                        analysis.getHealthScore(),
                        current.getTotalCount()
                )
        );

        txtIncome.setText(
                formatMoney(
                        current.getIncome()
                )
        );

        txtExpense.setText(
                formatMoney(
                        current.getExpense()
                )
        );

        txtSaving.setText(
                formatSignedMoney(
                        current.getSaving()
                )
        );

        txtSaving.setTextColor(
                getColorValue(
                        current.getSaving() > 0.0001d
                                ? R.color.success
                                : current.getSaving() < -0.0001d
                                ? R.color.expense
                                : R.color.app_text_secondary
                )
        );

        txtDuplicateCount.setText(
                String.valueOf(
                        analysis.getDuplicateExtraCount()
                )
        );

        txtUnusualCount.setText(
                String.valueOf(
                        analysis
                                .getUnusualTransactions()
                                .size()
                )
        );

        txtTopCategory.setText(
                current.getTopCategory().isEmpty()
                        ? "No data"
                        : current.getTopCategory()
        );

        txtOverview.setText(
                buildOverview(
                        analysis
                )
        );

        renderAlerts(
                analysis
        );

        renderRecommendations(
                analysis
        );

        showAnswer(
                assistantEngine.answer(
                        "summary",
                        analysis
                )
        );

        setAssistantControlsEnabled(
                true
        );
    }

    private void showAnalysisFailure() {
        txtAssistantPeriod.setText(
                "Analysis unavailable"
        );

        txtHealthScore.setText("!");
        txtHealthScore.setTextColor(
                getColorValue(
                        R.color.expense
                )
        );

        txtHealthLabel.setText(
                "Transactions could not be read"
        );

        txtOverview.setText(
                "The local database could not be analysed. Close the screen and try again. Existing transaction data has not been changed."
        );

        alertContainer.removeAllViews();
        recommendationContainer.removeAllViews();

        addDynamicCard(
                alertContainer,
                "Analysis failed",
                "No transaction was modified. Tap Refresh Analysis to try again.",
                R.color.expense,
                R.color.error_surface,
                R.color.error_outline
        );

        txtAnswerTitle.setText(
                "Assistant unavailable"
        );

        txtAnswer.setText(
                "Refresh the analysis before asking a finance question."
        );

        styleAnswerCard(
                SmartTransactionAssistantEngine.AnswerTone.EXPENSE
        );

        setAssistantControlsEnabled(
                false
        );

        btnRefreshAssistant.setEnabled(true);
        btnRefreshAssistant.setAlpha(1f);
    }

    private void renderAlerts(
            @NonNull SmartTransactionAssistantEngine.Analysis analysis
    ) {
        alertContainer.removeAllViews();

        if (analysis.getDuplicateExtraCount() == 0
                && analysis
                .getUnusualTransactions()
                .isEmpty()) {

            addDynamicCard(
                    alertContainer,
                    "No urgent smart alert",
                    "No likely duplicate or unusual high-value expense was found in the current local analysis.",
                    R.color.success,
                    R.color.success_surface,
                    R.color.success_outline
            );

            return;
        }

        int duplicateLimit =
                Math.min(
                        3,
                        analysis
                                .getDuplicateGroups()
                                .size()
                );

        for (int index = 0;
             index < duplicateLimit;
             index++) {

            SmartTransactionAssistantEngine.DuplicateGroup group =
                    analysis
                            .getDuplicateGroups()
                            .get(index);

            addDynamicCard(
                    alertContainer,
                    "Possible duplicate • "
                            + group.getCategory(),
                    group.getCount()
                            + " matching entries • "
                            + formatMoney(
                            group.getAmount()
                    )
                            + " • "
                            + group.getAccount()
                            + "\n"
                            + group.getFirstDate()
                            + " to "
                            + group.getLatestDate(),
                    R.color.orange,
                    R.color.warning_surface,
                    R.color.warning_outline
            );
        }

        int unusualLimit =
                Math.min(
                        3,
                        analysis
                                .getUnusualTransactions()
                                .size()
                );

        for (int index = 0;
             index < unusualLimit;
             index++) {

            SmartTransactionAssistantEngine.UnusualTransaction item =
                    analysis
                            .getUnusualTransactions()
                            .get(index);

            String note =
                    item.getNote().trim().isEmpty()
                            ? ""
                            : " • " + item.getNote().trim();

            addDynamicCard(
                    alertContainer,
                    "Unusual expense • "
                            + item.getCategory(),
                    formatMoney(
                            item.getAmount()
                    )
                            + " • "
                            + item.getAccount()
                            + note
                            + "\n"
                            + item.getDisplayDate()
                            + "\n"
                            + item.getReason(),
                    R.color.expense,
                    R.color.error_surface,
                    R.color.error_outline
            );
        }
    }

    private void renderRecommendations(
            @NonNull SmartTransactionAssistantEngine.Analysis analysis
    ) {
        recommendationContainer.removeAllViews();

        List<SmartTransactionAssistantEngine.Insight> insights =
                analysis.getInsights();

        if (insights.isEmpty()) {
            addDynamicCard(
                    recommendationContainer,
                    "No recommendation available",
                    "Add more transactions to build a useful monthly pattern.",
                    R.color.app_text_secondary,
                    R.color.app_surface_soft,
                    R.color.app_outline
            );

            return;
        }

        for (SmartTransactionAssistantEngine.Insight insight
                : insights) {

            ToneColors colors =
                    colorsForInsight(
                            insight.getTone()
                    );

            addDynamicCard(
                    recommendationContainer,
                    insight.getTitle(),
                    insight.getMessage(),
                    colors.accent,
                    colors.surface,
                    colors.outline
            );
        }
    }

    private void answerCurrentQuestion() {
        hideKeyboard();
        inputAssistantQuery.setError(null);

        if (analysisLoading) {
            Toast.makeText(
                    this,
                    "Transaction analysis is still running",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        if (currentAnalysis == null) {
            inputAssistantQuery.setError(
                    "Refresh analysis before asking"
            );

            return;
        }

        String question =
                etAssistantQuery.getText() == null
                        ? ""
                        : etAssistantQuery
                        .getText()
                        .toString()
                        .trim();

        if (question.isEmpty()) {
            inputAssistantQuery.setError(
                    "Enter a finance question"
            );

            etAssistantQuery.requestFocus();
            return;
        }

        showAnswer(
                assistantEngine.answer(
                        question,
                        currentAnalysis
                )
        );
    }

    private void askQuickQuestion(
            @NonNull String question
    ) {
        etAssistantQuery.setText(question);
        etAssistantQuery.setSelection(
                question.length()
        );

        answerCurrentQuestion();
    }

    private void showAnswer(
            @NonNull SmartTransactionAssistantEngine.Answer answer
    ) {
        txtAnswerTitle.setText(
                answer.getTitle()
        );

        txtAnswer.setText(
                answer.getMessage()
        );

        styleAnswerCard(
                answer.getTone()
        );
    }

    private void styleAnswerCard(
            @NonNull SmartTransactionAssistantEngine.AnswerTone tone
    ) {
        ToneColors colors =
                colorsForAnswer(tone);

        cardAssistantAnswer.setCardBackgroundColor(
                getColorValue(
                        colors.surface
                )
        );

        cardAssistantAnswer.setStrokeColor(
                getColorValue(
                        colors.outline
                )
        );

        txtAnswerTitle.setTextColor(
                getColorValue(
                        colors.accent
                )
        );
    }

    private void setAssistantControlsEnabled(
            boolean enabled
    ) {
        btnAskAssistant.setEnabled(enabled);
        btnQuickExpense.setEnabled(enabled);
        btnQuickCategory.setEnabled(enabled);
        btnQuickDuplicates.setEnabled(enabled);
        btnQuickUnusual.setEnabled(enabled);
        etAssistantQuery.setEnabled(enabled);

        float alpha =
                enabled
                        ? 1f
                        : 0.55f;

        btnAskAssistant.setAlpha(alpha);
        btnQuickExpense.setAlpha(alpha);
        btnQuickCategory.setAlpha(alpha);
        btnQuickDuplicates.setAlpha(alpha);
        btnQuickUnusual.setAlpha(alpha);
        etAssistantQuery.setAlpha(alpha);
    }

    private String buildOverview(
            @NonNull SmartTransactionAssistantEngine.Analysis analysis
    ) {
        SmartTransactionAssistantEngine.MonthMetrics current =
                analysis.getCurrentMonth();

        if (current.getTotalCount() == 0) {
            return "No income or expense entry is available for "
                    + current.getLabel()
                    + ". Add transactions to activate complete assistant insights.";
        }

        String savingText;

        if (current.getSaving() > 0.0001d) {
            savingText =
                    "A positive saving of "
                            + formatMoney(
                            current.getSaving()
                    )
                            + " remains.";

        } else if (current.getSaving() < -0.0001d) {
            savingText =
                    "Expense is above income by "
                            + formatMoney(
                            Math.abs(
                                    current.getSaving()
                            )
                    )
                            + ".";

        } else {
            savingText =
                    "Income and expense are currently equal.";
        }

        String categoryText =
                current.getTopCategory().isEmpty()
                        ? "No expense category is available yet."
                        : current.getTopCategory()
                        + " is the largest expense category at "
                        + formatMoney(
                        current.getTopCategoryAmount()
                )
                        + ".";

        return current.getTotalCount()
                + " transactions were analysed for "
                + current.getLabel()
                + ". Income is "
                + formatMoney(
                current.getIncome()
        )
                + " and expense is "
                + formatMoney(
                current.getExpense()
        )
                + ". "
                + savingText
                + " "
                + categoryText;
    }

    private void addDynamicCard(
            @NonNull LinearLayout container,
            @NonNull String titleText,
            @NonNull String messageText,
            @ColorRes int accentColor,
            @ColorRes int surfaceColor,
            @ColorRes int outlineColor
    ) {
        MaterialCardView card =
                new MaterialCardView(this);

        card.setCardBackgroundColor(
                getColorValue(
                        surfaceColor
                )
        );

        card.setRadius(
                dp(17)
        );

        card.setCardElevation(0f);

        card.setStrokeColor(
                getColorValue(
                        outlineColor
                )
        );

        card.setStrokeWidth(
                dp(1)
        );

        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        cardParams.setMargins(
                0,
                0,
                0,
                dp(9)
        );

        card.setLayoutParams(cardParams);

        LinearLayout content =
                new LinearLayout(this);

        content.setOrientation(
                LinearLayout.HORIZONTAL
        );

        content.setGravity(
                Gravity.TOP
        );

        content.setPadding(
                dp(14),
                dp(13),
                dp(14),
                dp(13)
        );

        TextView badge =
                new TextView(this);

        badge.setText("✦");
        badge.setGravity(Gravity.CENTER);
        badge.setTextSize(17);
        badge.setTextColor(
                getColorValue(
                        accentColor
                )
        );

        badge.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        LinearLayout.LayoutParams badgeParams =
                new LinearLayout.LayoutParams(
                        dp(38),
                        dp(38)
                );

        badge.setLayoutParams(badgeParams);

        content.addView(badge);

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
                dp(11),
                0,
                0,
                0
        );

        textContainer.setLayoutParams(textParams);

        TextView title =
                new TextView(this);

        title.setText(titleText);
        title.setTextSize(14);
        title.setTextColor(
                getColorValue(
                        accentColor
                )
        );

        title.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        textContainer.addView(title);

        TextView message =
                new TextView(this);

        message.setText(messageText);
        message.setTextSize(11);
        message.setTextColor(
                getColorValue(
                        R.color.app_text_secondary
                )
        );

        message.setLineSpacing(
                dp(2),
                1f
        );

        LinearLayout.LayoutParams messageParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        messageParams.setMargins(
                0,
                dp(4),
                0,
                0
        );

        message.setLayoutParams(messageParams);

        textContainer.addView(message);
        content.addView(textContainer);
        card.addView(content);
        container.addView(card);
    }

    private ToneColors colorsForAnswer(
            @NonNull SmartTransactionAssistantEngine.AnswerTone tone
    ) {
        switch (tone) {
            case SUCCESS:
                return new ToneColors(
                        R.color.success,
                        R.color.success_surface,
                        R.color.success_outline
                );

            case EXPENSE:
                return new ToneColors(
                        R.color.expense,
                        R.color.error_surface,
                        R.color.error_outline
                );

            case WARNING:
                return new ToneColors(
                        R.color.orange,
                        R.color.warning_surface,
                        R.color.warning_outline
                );

            case INFO:
                return new ToneColors(
                        R.color.secondary,
                        R.color.info_surface,
                        R.color.info_outline
                );

            case PURPLE:
                return new ToneColors(
                        R.color.purple,
                        R.color.purple_surface,
                        R.color.purple_outline
                );

            case NEUTRAL:
            default:
                return new ToneColors(
                        R.color.app_text_secondary,
                        R.color.app_surface_soft,
                        R.color.app_outline
                );
        }
    }

    private ToneColors colorsForInsight(
            @NonNull SmartTransactionAssistantEngine.InsightTone tone
    ) {
        switch (tone) {
            case SUCCESS:
                return new ToneColors(
                        R.color.success,
                        R.color.success_surface,
                        R.color.success_outline
                );

            case EXPENSE:
                return new ToneColors(
                        R.color.expense,
                        R.color.error_surface,
                        R.color.error_outline
                );

            case WARNING:
                return new ToneColors(
                        R.color.orange,
                        R.color.warning_surface,
                        R.color.warning_outline
                );

            case INFO:
            default:
                return new ToneColors(
                        R.color.secondary,
                        R.color.info_surface,
                        R.color.info_outline
                );
        }
    }

    private int getHealthColor(
            int score
    ) {
        if (score >= 75) {
            return getColorValue(
                    R.color.success
            );
        }

        if (score >= 50) {
            return getColorValue(
                    R.color.orange
            );
        }

        return getColorValue(
                R.color.expense
        );
    }

    private String getHealthLabel(
            int score,
            int transactionCount
    ) {
        if (transactionCount == 0) {
            return "Add transactions to calculate score";
        }

        if (score >= 85) {
            return "Strong and well controlled";
        }

        if (score >= 70) {
            return "Healthy with room to improve";
        }

        if (score >= 50) {
            return "Needs closer monthly attention";
        }

        return "Important spending review needed";
    }

    private String formatMoney(
            double amount
    ) {
        NumberFormat formatter =
                NumberFormat.getCurrencyInstance(
                        new Locale(
                                "en",
                                "IN"
                        )
                );

        formatter.setMinimumFractionDigits(2);
        formatter.setMaximumFractionDigits(2);

        return formatter.format(
                Math.abs(amount)
        );
    }

    private String formatSignedMoney(
            double amount
    ) {
        if (amount > 0.0001d) {
            return "+" + formatMoney(amount);
        }

        if (amount < -0.0001d) {
            return "-" + formatMoney(amount);
        }

        return formatMoney(0);
    }

    private void hideKeyboard() {
        View focusedView =
                getCurrentFocus();

        if (focusedView == null) {
            return;
        }

        InputMethodManager inputMethodManager =
                (InputMethodManager) getSystemService(
                        INPUT_METHOD_SERVICE
                );

        if (inputMethodManager != null) {
            inputMethodManager.hideSoftInputFromWindow(
                    focusedView.getWindowToken(),
                    0
            );
        }
    }

    private int getColorValue(
            @ColorRes int colorResource
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

    private static final class ToneColors {

        private final int accent;
        private final int surface;
        private final int outline;

        private ToneColors(
                int accent,
                int surface,
                int outline
        ) {
            this.accent = accent;
            this.surface = surface;
            this.outline = outline;
        }
    }
}
