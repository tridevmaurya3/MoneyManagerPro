package com.example.moneymanagerpro.activities;

import android.app.DatePickerDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.model.InvestmentItem;
import com.example.moneymanagerpro.utils.BubbleTouchAnimator;
import com.example.moneymanagerpro.utils.InvestmentStore;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class InvestmentActivity extends AppCompatActivity {

    private TextInputLayout inputInvestmentName;
    private TextInputLayout inputInvestedAmount;
    private TextInputLayout inputCurrentValue;

    private TextInputEditText etInvestmentName;
    private TextInputEditText etInvestedAmount;
    private TextInputEditText etCurrentValue;
    private TextInputEditText etMonthlyContribution;
    private TextInputEditText etInvestmentDate;
    private TextInputEditText etInvestmentNote;

    private MaterialAutoCompleteTextView dropdownInvestmentType;
    private MaterialButton btnSaveInvestment;

    private TextView txtTotalInvested;
    private TextView txtCurrentPortfolioValue;
    private TextView txtProfitLoss;
    private TextView txtReturnPercentage;
    private TextView txtInvestmentEmpty;

    private LinearLayout investmentContainer;

    private Calendar selectedCalendar;
    private String selectedDate;

    private final String[] investmentTypes = {
            "SIP / Mutual Fund",
            "Fixed Deposit",
            "Stocks",
            "Gold",
            "PPF",
            "NPS",
            "Crypto",
            "Real Estate",
            "Other"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_investment);

        bindViews();
        prepareScreen();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadInvestments();
    }

    private void bindViews() {
        TextView btnBack =
                findViewById(R.id.btnBack);

        inputInvestmentName =
                findViewById(R.id.inputInvestmentName);

        inputInvestedAmount =
                findViewById(R.id.inputInvestedAmount);

        inputCurrentValue =
                findViewById(R.id.inputCurrentValue);

        etInvestmentName =
                findViewById(R.id.etInvestmentName);

        etInvestedAmount =
                findViewById(R.id.etInvestedAmount);

        etCurrentValue =
                findViewById(R.id.etCurrentValue);

        etMonthlyContribution =
                findViewById(R.id.etMonthlyContribution);

        etInvestmentDate =
                findViewById(R.id.etInvestmentDate);

        etInvestmentNote =
                findViewById(R.id.etInvestmentNote);

        dropdownInvestmentType =
                findViewById(R.id.dropdownInvestmentType);

        btnSaveInvestment =
                findViewById(R.id.btnSaveInvestment);

        txtTotalInvested =
                findViewById(R.id.txtTotalInvested);

        txtCurrentPortfolioValue =
                findViewById(R.id.txtCurrentPortfolioValue);

        txtProfitLoss =
                findViewById(R.id.txtProfitLoss);

        txtReturnPercentage =
                findViewById(R.id.txtReturnPercentage);

        txtInvestmentEmpty =
                findViewById(R.id.txtInvestmentEmpty);

        investmentContainer =
                findViewById(R.id.investmentContainer);

        btnBack.setOnClickListener(
                view -> finish()
        );
    }

    private void prepareScreen() {
        selectedCalendar =
                Calendar.getInstance();

        setupInvestmentTypes();
        updateDateField();

        etInvestmentDate.setOnClickListener(
                view -> showDatePicker()
        );

        btnSaveInvestment.setOnClickListener(
                view -> saveInvestment()
        );

        BubbleTouchAnimator.apply(
                btnSaveInvestment
        );
    }

    private void setupInvestmentTypes() {
        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_list_item_1,
                        investmentTypes
                );

        dropdownInvestmentType.setAdapter(adapter);

        dropdownInvestmentType.setText(
                investmentTypes[0],
                false
        );
    }

    private void showDatePicker() {
        DatePickerDialog dialog =
                new DatePickerDialog(
                        this,
                        (view, year, month, dayOfMonth) -> {
                            selectedCalendar.set(
                                    Calendar.YEAR,
                                    year
                            );

                            selectedCalendar.set(
                                    Calendar.MONTH,
                                    month
                            );

                            selectedCalendar.set(
                                    Calendar.DAY_OF_MONTH,
                                    dayOfMonth
                            );

                            updateDateField();
                        },
                        selectedCalendar.get(
                                Calendar.YEAR
                        ),
                        selectedCalendar.get(
                                Calendar.MONTH
                        ),
                        selectedCalendar.get(
                                Calendar.DAY_OF_MONTH
                        )
                );

        dialog.show();
    }

    private void updateDateField() {
        selectedDate =
                new SimpleDateFormat(
                        "yyyy-MM-dd",
                        Locale.US
                ).format(
                        selectedCalendar.getTime()
                );

        String visibleDate =
                new SimpleDateFormat(
                        "dd MMMM yyyy",
                        Locale.ENGLISH
                ).format(
                        selectedCalendar.getTime()
                );

        etInvestmentDate.setText(visibleDate);
    }

    private void saveInvestment() {
        String name =
                getText(etInvestmentName);

        String investedText =
                getText(etInvestedAmount);

        String currentValueText =
                getText(etCurrentValue);

        String monthlyText =
                getText(etMonthlyContribution);

        if (name.isEmpty()) {
            inputInvestmentName.setError(
                    "Enter investment name"
            );

            etInvestmentName.requestFocus();
            return;
        }

        double investedAmount;

        try {
            investedAmount =
                    Double.parseDouble(
                            investedText
                    );

        } catch (Exception exception) {
            inputInvestedAmount.setError(
                    "Enter a valid invested amount"
            );

            etInvestedAmount.requestFocus();
            return;
        }

        if (investedAmount <= 0) {
            inputInvestedAmount.setError(
                    "Invested amount must be greater than zero"
            );

            etInvestedAmount.requestFocus();
            return;
        }

        double currentValue;

        try {
            currentValue =
                    Double.parseDouble(
                            currentValueText
                    );

        } catch (Exception exception) {
            inputCurrentValue.setError(
                    "Enter a valid current value"
            );

            etCurrentValue.requestFocus();
            return;
        }

        if (currentValue < 0) {
            inputCurrentValue.setError(
                    "Current value cannot be negative"
            );

            etCurrentValue.requestFocus();
            return;
        }

        double monthlyContribution = 0;

        if (!monthlyText.isEmpty()) {
            try {
                monthlyContribution =
                        Double.parseDouble(
                                monthlyText
                        );

            } catch (Exception exception) {
                etMonthlyContribution.setError(
                        "Enter a valid monthly contribution"
                );

                etMonthlyContribution.requestFocus();
                return;
            }
        }

        if (monthlyContribution < 0) {
            etMonthlyContribution.setError(
                    "Monthly contribution cannot be negative"
            );

            etMonthlyContribution.requestFocus();
            return;
        }

        inputInvestmentName.setError(null);
        inputInvestedAmount.setError(null);
        inputCurrentValue.setError(null);
        etMonthlyContribution.setError(null);

        String investmentType =
                safeText(
                        dropdownInvestmentType
                                .getText()
                                .toString(),
                        investmentTypes[0]
                );

        InvestmentItem item =
                new InvestmentItem();

        item.setName(name);
        item.setType(investmentType);
        item.setInvestedAmount(investedAmount);
        item.setCurrentValue(currentValue);
        item.setMonthlyContribution(monthlyContribution);
        item.setStartDate(selectedDate);
        item.setNote(getText(etInvestmentNote));

        btnSaveInvestment.setEnabled(false);

        btnSaveInvestment.setText(
                "Saving Investment..."
        );

        try {
            InvestmentStore.add(
                    getApplicationContext(),
                    item
            );

            clearForm();
            loadInvestments();

            Toast.makeText(
                    this,
                    "Investment saved successfully",
                    Toast.LENGTH_SHORT
            ).show();

        } catch (Exception exception) {
            Toast.makeText(
                    this,
                    "Unable to save investment",
                    Toast.LENGTH_SHORT
            ).show();

        } finally {
            btnSaveInvestment.setEnabled(true);

            btnSaveInvestment.setText(
                    "Save Investment"
            );
        }
    }

    private void clearForm() {
        etInvestmentName.setText("");
        etInvestedAmount.setText("");
        etCurrentValue.setText("");
        etMonthlyContribution.setText("");
        etInvestmentNote.setText("");

        dropdownInvestmentType.setText(
                investmentTypes[0],
                false
        );

        selectedCalendar =
                Calendar.getInstance();

        updateDateField();
    }

    private void loadInvestments() {
        List<InvestmentItem> investments;

        try {
            investments =
                    InvestmentStore.getAll(
                            getApplicationContext()
                    );

        } catch (Exception exception) {
            investments =
                    new ArrayList<>();

            Toast.makeText(
                    this,
                    "Unable to load investments",
                    Toast.LENGTH_SHORT
            ).show();
        }

        if (investments == null) {
            investments =
                    new ArrayList<>();
        }

        investmentContainer.removeAllViews();

        double totalInvested = 0;
        double totalCurrentValue = 0;

        for (InvestmentItem item : investments) {
            if (item == null) {
                continue;
            }

            totalInvested +=
                    Math.max(
                            item.getInvestedAmount(),
                            0
                    );

            totalCurrentValue +=
                    Math.max(
                            item.getCurrentValue(),
                            0
                    );
        }

        double profitLoss =
                totalCurrentValue
                        - totalInvested;

        double returnPercentage =
                calculateReturnPercentage(
                        totalInvested,
                        profitLoss
                );

        updatePortfolioSummary(
                totalInvested,
                totalCurrentValue,
                profitLoss,
                returnPercentage
        );

        boolean isEmpty =
                investments.isEmpty();

        txtInvestmentEmpty.setVisibility(
                isEmpty
                        ? View.VISIBLE
                        : View.GONE
        );

        if (isEmpty) {
            return;
        }

        for (InvestmentItem item : investments) {
            if (item != null) {
                investmentContainer.addView(
                        createInvestmentCard(item)
                );
            }
        }
    }

    private void updatePortfolioSummary(
            double totalInvested,
            double totalCurrentValue,
            double profitLoss,
            double returnPercentage
    ) {
        txtTotalInvested.setText(
                formatMoney(totalInvested)
        );

        txtCurrentPortfolioValue.setText(
                formatMoney(totalCurrentValue)
        );

        txtProfitLoss.setText(
                formatSignedMoney(profitLoss)
        );

        txtReturnPercentage.setText(
                String.format(
                        Locale.US,
                        "Portfolio return: %+.2f%%",
                        returnPercentage
                )
        );

        int performanceColor;

        if (profitLoss > 0) {
            performanceColor =
                    getColorValue(
                            R.color.success
                    );

        } else if (profitLoss < 0) {
            performanceColor =
                    getColorValue(
                            R.color.expense
                    );

        } else {
            performanceColor =
                    getColorValue(
                            R.color.app_text_secondary
                    );
        }

        txtProfitLoss.setTextColor(
                performanceColor
        );

        txtReturnPercentage.setTextColor(
                performanceColor
        );
    }

    private MaterialCardView createInvestmentCard(
            InvestmentItem item
    ) {
        double investedAmount =
                Math.max(
                        item.getInvestedAmount(),
                        0
                );

        double currentValue =
                Math.max(
                        item.getCurrentValue(),
                        0
                );

        double profitLoss =
                currentValue
                        - investedAmount;

        double returnPercentage =
                calculateReturnPercentage(
                        investedAmount,
                        profitLoss
                );

        boolean isProfit =
                profitLoss >= 0;

        String investmentType =
                safeText(
                        item.getType(),
                        "Other"
                );

        int accentColor =
                getInvestmentAccentColor(
                        investmentType
                );

        int performanceColor =
                isProfit
                        ? getColorValue(
                        R.color.success
                )
                        : getColorValue(
                        R.color.expense
                );

        int performanceSurface =
                isProfit
                        ? getColorValue(
                        R.color.success_surface
                )
                        : getColorValue(
                        R.color.expense_surface
                );

        int performanceOutline =
                isProfit
                        ? getColorValue(
                        R.color.success_outline
                )
                        : getColorValue(
                        R.color.expense_outline
                );

        MaterialCardView cardView =
                new MaterialCardView(this);

        cardView.setCardBackgroundColor(
                getColorValue(
                        R.color.app_surface
                )
        );

        cardView.setRadius(
                dp(20)
        );

        cardView.setCardElevation(
                dp(1)
        );

        cardView.setStrokeWidth(
                dp(1)
        );

        cardView.setStrokeColor(
                createTranslucentColor(
                        accentColor,
                        65
                )
        );

        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        cardParams.setMargins(
                0,
                dp(6),
                0,
                dp(7)
        );

        cardView.setLayoutParams(
                cardParams
        );

        LinearLayout content =
                new LinearLayout(this);

        content.setOrientation(
                LinearLayout.VERTICAL
        );

        content.setPadding(
                dp(15),
                dp(15),
                dp(15),
                dp(14)
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

        TextView iconView =
                createInvestmentIcon(
                        investmentType,
                        accentColor
                );

        headerRow.addView(
                iconView
        );

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
                dp(11),
                0,
                dp(8),
                0
        );

        titleContainer.setLayoutParams(
                titleParams
        );

        TextView txtName =
                createText(
                        safeText(
                                item.getName(),
                                "Investment"
                        ),
                        16,
                        getColorValue(
                                R.color.app_text_primary
                        ),
                        true
                );

        TextView txtDate =
                createText(
                        "Started "
                                + formatVisibleDate(
                                item.getStartDate()
                        ),
                        10,
                        getColorValue(
                                R.color.app_text_secondary
                        ),
                        false
                );

        LinearLayout.LayoutParams dateParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        dateParams.setMargins(
                0,
                dp(3),
                0,
                0
        );

        txtDate.setLayoutParams(
                dateParams
        );

        titleContainer.addView(
                txtName
        );

        titleContainer.addView(
                txtDate
        );

        headerRow.addView(
                titleContainer
        );

        TextView typeBadge =
                createTypeBadge(
                        investmentType,
                        accentColor
                );

        headerRow.addView(
                typeBadge
        );

        content.addView(
                headerRow
        );

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
                        dp(1)
                );

        dividerParams.setMargins(
                0,
                dp(13),
                0,
                dp(12)
        );

        divider.setLayoutParams(
                dividerParams
        );

        content.addView(
                divider
        );

        /*
         * Invested and Current Value
         */

        LinearLayout amountRow =
                new LinearLayout(this);

        amountRow.setOrientation(
                LinearLayout.HORIZONTAL
        );

        amountRow.setBaselineAligned(false);

        LinearLayout investedBlock =
                createMetricBlock(
                        "Invested",
                        formatMoney(investedAmount),
                        getColorValue(
                                R.color.secondary
                        ),
                        getColorValue(
                                R.color.info_surface
                        ),
                        getColorValue(
                                R.color.info_outline
                        )
                );

        LinearLayout.LayoutParams investedParams =
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                );

        investedParams.setMargins(
                0,
                0,
                dp(5),
                0
        );

        investedBlock.setLayoutParams(
                investedParams
        );

        LinearLayout currentBlock =
                createMetricBlock(
                        "Current Value",
                        formatMoney(currentValue),
                        accentColor,
                        createTranslucentColor(
                                accentColor,
                                16
                        ),
                        createTranslucentColor(
                                accentColor,
                                60
                        )
                );

        LinearLayout.LayoutParams currentParams =
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                );

        currentParams.setMargins(
                dp(5),
                0,
                0,
                0
        );

        currentBlock.setLayoutParams(
                currentParams
        );

        amountRow.addView(
                investedBlock
        );

        amountRow.addView(
                currentBlock
        );

        content.addView(
                amountRow
        );

        /*
         * Performance
         */

        LinearLayout performanceBox =
                createPerformanceBox(
                        isProfit
                                ? "Portfolio Gain"
                                : "Portfolio Loss",
                        formatSignedMoney(
                                profitLoss
                        ),
                        String.format(
                                Locale.US,
                                "%+.2f%% return",
                                returnPercentage
                        ),
                        performanceColor,
                        performanceSurface,
                        performanceOutline,
                        isProfit
                                ? "↗"
                                : "↘"
                );

        LinearLayout.LayoutParams performanceParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        performanceParams.setMargins(
                0,
                dp(12),
                0,
                0
        );

        performanceBox.setLayoutParams(
                performanceParams
        );

        content.addView(
                performanceBox
        );

        /*
         * Monthly Contribution
         */

        String contributionTitle;
        String contributionValue;
        String contributionDescription;

        if (item.getMonthlyContribution() > 0) {
            contributionTitle =
                    "Monthly Contribution";

            contributionValue =
                    formatMoney(
                            item.getMonthlyContribution()
                    );

            contributionDescription =
                    "Regular contribution recorded for this investment.";

        } else {
            contributionTitle =
                    "Contribution Type";

            contributionValue =
                    "One-time Investment";

            contributionDescription =
                    "No monthly contribution has been entered.";
        }

        LinearLayout contributionBox =
                createInformationBox(
                        contributionTitle,
                        contributionValue,
                        contributionDescription,
                        accentColor
                );

        LinearLayout.LayoutParams contributionParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        contributionParams.setMargins(
                0,
                dp(10),
                0,
                0
        );

        contributionBox.setLayoutParams(
                contributionParams
        );

        content.addView(
                contributionBox
        );

        /*
         * Note
         */

        String noteText =
                safeText(
                        item.getNote(),
                        ""
                );

        if (!noteText.isEmpty()) {
            LinearLayout noteBox =
                    createNoteBox(
                            noteText
                    );

            LinearLayout.LayoutParams noteParams =
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    );

            noteParams.setMargins(
                    0,
                    dp(10),
                    0,
                    0
            );

            noteBox.setLayoutParams(
                    noteParams
            );

            content.addView(
                    noteBox
            );
        }

        /*
         * Actions
         */

        LinearLayout buttonRow =
                new LinearLayout(this);

        buttonRow.setOrientation(
                LinearLayout.HORIZONTAL
        );

        LinearLayout.LayoutParams buttonRowParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(46)
                );

        buttonRowParams.setMargins(
                0,
                dp(13),
                0,
                0
        );

        buttonRow.setLayoutParams(
                buttonRowParams
        );

        MaterialButton btnUpdateValue =
                createUpdateButton(
                        accentColor
                );

        LinearLayout.LayoutParams updateParams =
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        1f
                );

        updateParams.setMargins(
                0,
                0,
                dp(5),
                0
        );

        btnUpdateValue.setLayoutParams(
                updateParams
        );

        MaterialButton btnDelete =
                createDeleteButton();

        LinearLayout.LayoutParams deleteParams =
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        1f
                );

        deleteParams.setMargins(
                dp(5),
                0,
                0,
                0
        );

        btnDelete.setLayoutParams(
                deleteParams
        );

        btnUpdateValue.setOnClickListener(
                view -> showUpdateValueDialog(
                        item
                )
        );

        btnDelete.setOnClickListener(
                view -> showDeleteDialog(
                        item
                )
        );

        BubbleTouchAnimator.apply(
                btnUpdateValue
        );

        BubbleTouchAnimator.apply(
                btnDelete
        );

        buttonRow.addView(
                btnUpdateValue
        );

        buttonRow.addView(
                btnDelete
        );

        content.addView(
                buttonRow
        );

        cardView.addView(
                content
        );

        return cardView;
    }

    private TextView createInvestmentIcon(
            String type,
            int accentColor
    ) {
        TextView icon =
                new TextView(this);

        icon.setText(
                getInvestmentSymbol(type)
        );

        icon.setTextColor(
                accentColor
        );

        icon.setTextSize(
                getInvestmentSymbol(type).length() > 2
                        ? 10
                        : 15
        );

        icon.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        icon.setGravity(
                Gravity.CENTER
        );

        GradientDrawable background =
                createRoundedDrawable(
                        createTranslucentColor(
                                accentColor,
                                18
                        ),
                        createTranslucentColor(
                                accentColor,
                                65
                        ),
                        14
                );

        icon.setBackground(
                background
        );

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        dp(46),
                        dp(46)
                );

        icon.setLayoutParams(
                params
        );

        return icon;
    }

    private TextView createTypeBadge(
            String type,
            int accentColor
    ) {
        TextView badge =
                createText(
                        getShortTypeName(type),
                        9,
                        accentColor,
                        true
                );

        badge.setGravity(
                Gravity.CENTER
        );

        badge.setPadding(
                dp(9),
                0,
                dp(9),
                0
        );

        badge.setBackground(
                createRoundedDrawable(
                        createTranslucentColor(
                                accentColor,
                                16
                        ),
                        createTranslucentColor(
                                accentColor,
                                60
                        ),
                        12
                )
        );

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        dp(28)
                );

        badge.setLayoutParams(
                params
        );

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
                dp(12),
                dp(11),
                dp(12),
                dp(11)
        );

        container.setBackground(
                createRoundedDrawable(
                        backgroundColor,
                        outlineColor,
                        13
                )
        );

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

        valueView.setMaxLines(1);

        LinearLayout.LayoutParams valueParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        valueParams.setMargins(
                0,
                dp(4),
                0,
                0
        );

        valueView.setLayoutParams(
                valueParams
        );

        container.addView(
                labelView
        );

        container.addView(
                valueView
        );

        return container;
    }

    private LinearLayout createPerformanceBox(
            String title,
            String value,
            String percentage,
            int accentColor,
            int backgroundColor,
            int outlineColor,
            String iconText
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
                dp(12),
                dp(11),
                dp(12),
                dp(11)
        );

        container.setBackground(
                createRoundedDrawable(
                        backgroundColor,
                        outlineColor,
                        13
                )
        );

        TextView icon =
                createText(
                        iconText,
                        18,
                        accentColor,
                        true
                );

        icon.setGravity(
                Gravity.CENTER
        );

        LinearLayout.LayoutParams iconParams =
                new LinearLayout.LayoutParams(
                        dp(36),
                        dp(36)
                );

        icon.setLayoutParams(
                iconParams
        );

        container.addView(
                icon
        );

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
                dp(9),
                0,
                dp(8),
                0
        );

        titleContainer.setLayoutParams(
                titleParams
        );

        TextView titleView =
                createText(
                        title,
                        10,
                        getColorValue(
                                R.color.app_text_secondary
                        ),
                        false
                );

        TextView percentageView =
                createText(
                        percentage,
                        11,
                        accentColor,
                        true
                );

        LinearLayout.LayoutParams percentageParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        percentageParams.setMargins(
                0,
                dp(3),
                0,
                0
        );

        percentageView.setLayoutParams(
                percentageParams
        );

        titleContainer.addView(
                titleView
        );

        titleContainer.addView(
                percentageView
        );

        container.addView(
                titleContainer
        );

        TextView valueView =
                createText(
                        value,
                        15,
                        accentColor,
                        true
                );

        valueView.setGravity(
                Gravity.END
        );

        container.addView(
                valueView
        );

        return container;
    }

    private LinearLayout createInformationBox(
            String title,
            String value,
            String description,
            int accentColor
    ) {
        LinearLayout container =
                new LinearLayout(this);

        container.setOrientation(
                LinearLayout.VERTICAL
        );

        container.setPadding(
                dp(12),
                dp(10),
                dp(12),
                dp(10)
        );

        container.setBackground(
                createRoundedDrawable(
                        createTranslucentColor(
                                accentColor,
                                13
                        ),
                        createTranslucentColor(
                                accentColor,
                                48
                        ),
                        12
                )
        );

        TextView titleView =
                createText(
                        title,
                        10,
                        getColorValue(
                                R.color.app_text_secondary
                        ),
                        false
                );

        TextView valueView =
                createText(
                        value,
                        13,
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
                dp(3),
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
                dp(3),
                0,
                0
        );

        descriptionView.setLayoutParams(
                descriptionParams
        );

        container.addView(
                titleView
        );

        container.addView(
                valueView
        );

        container.addView(
                descriptionView
        );

        return container;
    }

    private LinearLayout createNoteBox(
            String note
    ) {
        LinearLayout container =
                new LinearLayout(this);

        container.setOrientation(
                LinearLayout.VERTICAL
        );

        container.setPadding(
                dp(12),
                dp(10),
                dp(12),
                dp(10)
        );

        container.setBackground(
                createRoundedDrawable(
                        getColorValue(
                                R.color.app_surface_soft
                        ),
                        getColorValue(
                                R.color.app_outline_soft
                        ),
                        12
                )
        );

        TextView title =
                createText(
                        "Investment Note",
                        10,
                        getColorValue(
                                R.color.app_text_secondary
                        ),
                        true
                );

        TextView message =
                createText(
                        note,
                        12,
                        getColorValue(
                                R.color.app_text_primary
                        ),
                        false
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

        message.setLayoutParams(
                messageParams
        );

        container.addView(
                title
        );

        container.addView(
                message
        );

        return container;
    }

    private MaterialButton createUpdateButton(
            int accentColor
    ) {
        MaterialButton button =
                new MaterialButton(this);

        button.setText(
                "Update Value"
        );

        button.setTextSize(11);
        button.setTextColor(accentColor);
        button.setAllCaps(false);

        button.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        button.setCornerRadius(
                dp(13)
        );

        button.setBackgroundTintList(
                ColorStateList.valueOf(
                        createTranslucentColor(
                                accentColor,
                                18
                        )
                )
        );

        button.setStrokeColor(
                ColorStateList.valueOf(
                        createTranslucentColor(
                                accentColor,
                                70
                        )
                )
        );

        button.setStrokeWidth(
                dp(1)
        );

        return button;
    }

    private MaterialButton createDeleteButton() {
        MaterialButton button =
                new MaterialButton(this);

        button.setText(
                "Delete"
        );

        button.setTextSize(11);

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
                dp(13)
        );

        button.setBackgroundTintList(
                ColorStateList.valueOf(
                        getColorValue(
                                R.color.expense_surface
                        )
                )
        );

        button.setStrokeColor(
                ColorStateList.valueOf(
                        getColorValue(
                                R.color.expense_outline
                        )
                )
        );

        button.setStrokeWidth(
                dp(1)
        );

        return button;
    }

    private void showUpdateValueDialog(
            InvestmentItem item
    ) {
        int accentColor =
                getInvestmentAccentColor(
                        item.getType()
                );

        double investedAmount =
                Math.max(
                        item.getInvestedAmount(),
                        0
                );

        double currentValue =
                Math.max(
                        item.getCurrentValue(),
                        0
                );

        double currentProfitLoss =
                currentValue
                        - investedAmount;

        LinearLayout dialogContent =
                new LinearLayout(this);

        dialogContent.setOrientation(
                LinearLayout.VERTICAL
        );

        dialogContent.setPadding(
                dp(22),
                dp(5),
                dp(22),
                dp(4)
        );

        LinearLayout summaryBox =
                createPerformanceBox(
                        currentProfitLoss >= 0
                                ? "Current Gain"
                                : "Current Loss",
                        formatSignedMoney(
                                currentProfitLoss
                        ),
                        "Invested "
                                + formatMoney(
                                investedAmount
                        ),
                        currentProfitLoss >= 0
                                ? getColorValue(
                                R.color.success
                        )
                                : getColorValue(
                                R.color.expense
                        ),
                        currentProfitLoss >= 0
                                ? getColorValue(
                                R.color.success_surface
                        )
                                : getColorValue(
                                R.color.expense_surface
                        ),
                        currentProfitLoss >= 0
                                ? getColorValue(
                                R.color.success_outline
                        )
                                : getColorValue(
                                R.color.expense_outline
                        ),
                        currentProfitLoss >= 0
                                ? "↗"
                                : "↘"
                );

        dialogContent.addView(
                summaryBox
        );

        TextInputLayout inputValue =
                new TextInputLayout(this);

        inputValue.setBoxBackgroundMode(
                TextInputLayout.BOX_BACKGROUND_OUTLINE
        );

        inputValue.setHint(
                "New current value"
        );

        inputValue.setPrefixText(
                "₹  "
        );

        inputValue.setBoxStrokeColor(
                accentColor
        );

        inputValue.setBoxCornerRadii(
                dp(14),
                dp(14),
                dp(14),
                dp(14)
        );

        LinearLayout.LayoutParams inputParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        inputParams.setMargins(
                0,
                dp(15),
                0,
                0
        );

        inputValue.setLayoutParams(
                inputParams
        );

        TextInputEditText input =
                new TextInputEditText(this);

        input.setInputType(
                InputType.TYPE_CLASS_NUMBER
                        | InputType.TYPE_NUMBER_FLAG_DECIMAL
        );

        input.setText(
                String.format(
                        Locale.US,
                        "%.2f",
                        currentValue
                )
        );

        input.setSelectAllOnFocus(true);
        input.setSingleLine(true);
        input.setTextSize(17);
        input.setMinHeight(dp(56));

        inputValue.addView(
                input
        );

        dialogContent.addView(
                inputValue
        );

        AlertDialog dialog =
                new AlertDialog.Builder(this)
                        .setTitle(
                                "Update Current Value"
                        )
                        .setMessage(
                                safeText(
                                        item.getName(),
                                        "Investment"
                                )
                        )
                        .setView(dialogContent)
                        .setNegativeButton(
                                "Cancel",
                                null
                        )
                        .setPositiveButton(
                                "Update Value",
                                null
                        )
                        .create();

        dialog.setOnShowListener(listener -> {
            android.widget.Button positiveButton =
                    dialog.getButton(
                            AlertDialog.BUTTON_POSITIVE
                    );

            positiveButton.setTextColor(
                    accentColor
            );

            positiveButton.setOnClickListener(view -> {
                String newValueText =
                        input.getText() == null
                                ? ""
                                : input
                                .getText()
                                .toString()
                                .trim();

                double newValue;

                try {
                    newValue =
                            Double.parseDouble(
                                    newValueText
                            );

                } catch (Exception exception) {
                    inputValue.setError(
                            "Enter a valid current value"
                    );

                    input.requestFocus();
                    return;
                }

                if (newValue < 0) {
                    inputValue.setError(
                            "Current value cannot be negative"
                    );

                    input.requestFocus();
                    return;
                }

                inputValue.setError(null);

                positiveButton.setEnabled(false);

                positiveButton.setText(
                        "Updating..."
                );

                double oldValue =
                        item.getCurrentValue();

                try {
                    item.setCurrentValue(
                            newValue
                    );

                    InvestmentStore.update(
                            getApplicationContext(),
                            item
                    );

                    dialog.dismiss();
                    loadInvestments();

                    Toast.makeText(
                            InvestmentActivity.this,
                            "Current value updated",
                            Toast.LENGTH_SHORT
                    ).show();

                } catch (Exception exception) {
                    item.setCurrentValue(
                            oldValue
                    );

                    positiveButton.setEnabled(true);

                    positiveButton.setText(
                            "Update Value"
                    );

                    Toast.makeText(
                            InvestmentActivity.this,
                            "Unable to update value",
                            Toast.LENGTH_SHORT
                    ).show();
                }
            });
        });

        dialog.show();
    }

    private void showDeleteDialog(
            InvestmentItem item
    ) {
        new AlertDialog.Builder(this)
                .setTitle(
                        "Delete Investment"
                )
                .setMessage(
                        "Delete \""
                                + safeText(
                                item.getName(),
                                "this investment"
                        )
                                + "\"?\n\n"
                                + "Its invested amount, current value and portfolio progress will be removed."
                )
                .setNegativeButton(
                        "Cancel",
                        null
                )
                .setPositiveButton(
                        "Delete",
                        (dialog, which) ->
                                deleteInvestment(item)
                )
                .show();
    }

    private void deleteInvestment(
            InvestmentItem item
    ) {
        try {
            InvestmentStore.delete(
                    getApplicationContext(),
                    item.getId()
            );

            loadInvestments();

            Toast.makeText(
                    this,
                    "Investment deleted",
                    Toast.LENGTH_SHORT
            ).show();

        } catch (Exception exception) {
            Toast.makeText(
                    this,
                    "Unable to delete investment",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    private double calculateReturnPercentage(
            double investedAmount,
            double profitLoss
    ) {
        if (investedAmount <= 0) {
            return 0;
        }

        return (
                profitLoss
                        / investedAmount
        ) * 100;
    }

    private int getInvestmentAccentColor(
            String investmentType
    ) {
        String type =
                safeText(
                        investmentType,
                        "Other"
                ).toLowerCase(
                        Locale.ENGLISH
                );

        if (type.contains("mutual")
                || type.contains("sip")) {

            return getColorValue(
                    R.color.purple
            );

        } else if (type.contains("fixed")
                || type.contains("deposit")) {

            return getColorValue(
                    R.color.secondary
            );

        } else if (type.contains("stock")) {
            return getColorValue(
                    R.color.success
            );

        } else if (type.contains("gold")) {
            return getColorValue(
                    R.color.warning
            );

        } else if (type.contains("ppf")) {
            return Color.parseColor(
                    "#00897B"
            );

        } else if (type.contains("nps")) {
            return Color.parseColor(
                    "#1565C0"
            );

        } else if (type.contains("crypto")) {
            return Color.parseColor(
                    "#EF6C00"
            );

        } else if (type.contains("real")) {
            return Color.parseColor(
                    "#795548"
            );

        } else {
            return getColorValue(
                    R.color.primary
            );
        }
    }

    private String getInvestmentSymbol(
            String investmentType
    ) {
        String type =
                safeText(
                        investmentType,
                        "Other"
                ).toLowerCase(
                        Locale.ENGLISH
                );

        if (type.contains("mutual")
                || type.contains("sip")) {

            return "MF";

        } else if (type.contains("fixed")
                || type.contains("deposit")) {

            return "FD";

        } else if (type.contains("stock")) {
            return "ST";

        } else if (type.contains("gold")) {
            return "Au";

        } else if (type.contains("ppf")) {
            return "PPF";

        } else if (type.contains("nps")) {
            return "NPS";

        } else if (type.contains("crypto")) {
            return "CR";

        } else if (type.contains("real")) {
            return "RE";

        } else {
            return "₹";
        }
    }

    private String getShortTypeName(
            String investmentType
    ) {
        String type =
                safeText(
                        investmentType,
                        "Other"
                );

        if (type.equalsIgnoreCase(
                "SIP / Mutual Fund"
        )) {
            return "Mutual Fund";

        } else if (type.equalsIgnoreCase(
                "Fixed Deposit"
        )) {
            return "FD";

        } else if (type.equalsIgnoreCase(
                "Real Estate"
        )) {
            return "Property";
        }

        return type;
    }

    private String formatVisibleDate(
            String storedDate
    ) {
        if (storedDate == null
                || storedDate.trim().isEmpty()) {

            return "Date not set";
        }

        try {
            SimpleDateFormat storedFormat =
                    new SimpleDateFormat(
                            "yyyy-MM-dd",
                            Locale.US
                    );

            storedFormat.setLenient(false);

            Date date =
                    storedFormat.parse(
                            storedDate.trim()
                    );

            if (date == null) {
                return storedDate;
            }

            return new SimpleDateFormat(
                    "dd MMM yyyy",
                    Locale.ENGLISH
            ).format(date);

        } catch (Exception exception) {
            return storedDate;
        }
    }

    private TextView createText(
            String text,
            float textSize,
            int textColor,
            boolean bold
    ) {
        TextView textView =
                new TextView(this);

        textView.setText(text);
        textView.setTextSize(textSize);
        textView.setTextColor(textColor);

        if (bold) {
            textView.setTypeface(
                    Typeface.DEFAULT,
                    Typeface.BOLD
            );
        }

        return textView;
    }

    private GradientDrawable createRoundedDrawable(
            int backgroundColor,
            int outlineColor,
            int cornerRadius
    ) {
        GradientDrawable drawable =
                new GradientDrawable();

        drawable.setColor(
                backgroundColor
        );

        drawable.setStroke(
                dp(1),
                outlineColor
        );

        drawable.setCornerRadius(
                dp(cornerRadius)
        );

        return drawable;
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

    private String getText(
            TextInputEditText editText
    ) {
        return editText.getText() == null
                ? ""
                : editText
                .getText()
                .toString()
                .trim();
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

    private String formatMoney(
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

    private String formatSignedMoney(
            double amount
    ) {
        if (amount > 0) {
            return "+"
                    + formatMoney(amount);

        } else if (amount < 0) {
            return "-"
                    + formatMoney(
                    Math.abs(amount)
            );
        }

        return formatMoney(0);
    }

    private int getColorValue(
            int colorResource
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
}