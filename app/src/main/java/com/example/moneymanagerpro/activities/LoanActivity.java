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
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.database.AppDatabase;
import com.example.moneymanagerpro.database.DatabaseClient;
import com.example.moneymanagerpro.model.Account;
import com.example.moneymanagerpro.model.Loan;
import com.example.moneymanagerpro.model.LoanPayment;
import com.example.moneymanagerpro.model.Transaction;
import com.example.moneymanagerpro.utils.BubbleTouchAnimator;
import com.example.moneymanagerpro.utils.LoanReminderScheduler;
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

public class LoanActivity extends AppCompatActivity {

    private TextInputLayout inputPersonName;
    private TextInputLayout inputTotalAmount;
    private TextInputLayout inputHistoricalPaid;
    private TextInputLayout inputHistoricalInstallments;
    private TextInputLayout inputTenureMonths;
    private TextInputLayout inputInterestRate;
    private TextInputLayout inputEmiAmount;

    private TextInputEditText etPersonName;
    private TextInputEditText etTotalAmount;
    private TextInputEditText etHistoricalPaid;
    private TextInputEditText etHistoricalInstallments;
    private TextInputEditText etTenureMonths;
    private TextInputEditText etInterestRate;
    private TextInputEditText etEmiAmount;
    private TextInputEditText etStartDate;
    private TextInputEditText etDueDate;
    private TextInputEditText etLoanNote;

    private MaterialAutoCompleteTextView dropdownLoanType;
    private MaterialButton btnSaveLoan;

    private LinearLayout loanContainer;
    private TextView txtEmptyLoans;

    private Calendar startCalendar;
    private Calendar dueCalendar;

    private String selectedStartDate;
    private String selectedDueDate;

    private final String[] loanTypes = {
            "Loan Taken",
            "Loan Given"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_loan);

        bindViews();
        prepareForm();

        LoanReminderScheduler.schedule(
                getApplicationContext()
        );
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadLoans();
    }

    private void bindViews() {
        inputPersonName =
                findViewById(R.id.inputPersonName);

        inputTotalAmount =
                findViewById(R.id.inputTotalAmount);

        inputHistoricalPaid =
                findViewById(R.id.inputHistoricalPaid);

        inputHistoricalInstallments =
                findViewById(R.id.inputHistoricalInstallments);

        inputTenureMonths =
                findViewById(R.id.inputTenureMonths);

        inputInterestRate =
                findViewById(R.id.inputInterestRate);

        inputEmiAmount =
                findViewById(R.id.inputEmiAmount);

        etPersonName =
                findViewById(R.id.etPersonName);

        etTotalAmount =
                findViewById(R.id.etTotalAmount);

        etHistoricalPaid =
                findViewById(R.id.etHistoricalPaid);

        etHistoricalInstallments =
                findViewById(R.id.etHistoricalInstallments);

        etTenureMonths =
                findViewById(R.id.etTenureMonths);

        etInterestRate =
                findViewById(R.id.etInterestRate);

        etEmiAmount =
                findViewById(R.id.etEmiAmount);

        etStartDate =
                findViewById(R.id.etStartDate);

        etDueDate =
                findViewById(R.id.etDueDate);

        etLoanNote =
                findViewById(R.id.etLoanNote);

        dropdownLoanType =
                findViewById(R.id.dropdownLoanType);

        btnSaveLoan =
                findViewById(R.id.btnSaveLoan);

        loanContainer =
                findViewById(R.id.loanContainer);

        txtEmptyLoans =
                findViewById(R.id.txtEmptyLoans);

        TextView btnBack =
                findViewById(R.id.btnBack);

        btnBack.setOnClickListener(
                view -> finish()
        );
    }

    private void prepareForm() {
        startCalendar =
                Calendar.getInstance();

        dueCalendar =
                Calendar.getInstance();

        dueCalendar.add(
                Calendar.MONTH,
                1
        );

        updateDateFields();

        ArrayAdapter<String> typeAdapter =
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_list_item_1,
                        loanTypes
                );

        dropdownLoanType.setAdapter(
                typeAdapter
        );

        dropdownLoanType.setText(
                loanTypes[0],
                false
        );

        etStartDate.setOnClickListener(
                view -> showDatePicker(true)
        );

        etDueDate.setOnClickListener(
                view -> showDatePicker(false)
        );

        btnSaveLoan.setOnClickListener(
                view -> saveLoan()
        );

        BubbleTouchAnimator.apply(
                btnSaveLoan
        );
    }

    private void showDatePicker(
            boolean isStartDate
    ) {
        Calendar dateCalendar =
                isStartDate
                        ? startCalendar
                        : dueCalendar;

        DatePickerDialog dialog =
                new DatePickerDialog(
                        this,
                        (view, year, month, dayOfMonth) -> {
                            dateCalendar.set(
                                    Calendar.YEAR,
                                    year
                            );

                            dateCalendar.set(
                                    Calendar.MONTH,
                                    month
                            );

                            dateCalendar.set(
                                    Calendar.DAY_OF_MONTH,
                                    dayOfMonth
                            );

                            updateDateFields();
                        },
                        dateCalendar.get(
                                Calendar.YEAR
                        ),
                        dateCalendar.get(
                                Calendar.MONTH
                        ),
                        dateCalendar.get(
                                Calendar.DAY_OF_MONTH
                        )
                );

        dialog.show();
    }

    private void updateDateFields() {
        selectedStartDate =
                formatStorageDate(
                        startCalendar
                );

        selectedDueDate =
                formatStorageDate(
                        dueCalendar
                );

        etStartDate.setText(
                formatVisibleDate(
                        startCalendar
                )
        );

        etDueDate.setText(
                formatVisibleDate(
                        dueCalendar
                )
        );
    }

    private void saveLoan() {
        clearFormErrors();

        String personName =
                getText(etPersonName);

        String totalText =
                getText(etTotalAmount);

        String historicalText =
                getText(etHistoricalPaid);

        String installmentText =
                getText(etHistoricalInstallments);

        String tenureText =
                getText(etTenureMonths);

        String interestText =
                getText(etInterestRate);

        String emiText =
                getText(etEmiAmount);

        if (personName.isEmpty()) {
            inputPersonName.setError(
                    "Enter bank or person name"
            );

            etPersonName.requestFocus();
            return;
        }

        double totalAmount;

        try {
            totalAmount =
                    Double.parseDouble(
                            totalText
                    );

        } catch (Exception exception) {
            inputTotalAmount.setError(
                    "Enter a valid total repayable amount"
            );

            etTotalAmount.requestFocus();
            return;
        }

        if (totalAmount <= 0) {
            inputTotalAmount.setError(
                    "Total amount must be greater than zero"
            );

            etTotalAmount.requestFocus();
            return;
        }

        double historicalPaid = 0;

        if (!historicalText.isEmpty()) {
            try {
                historicalPaid =
                        Double.parseDouble(
                                historicalText
                        );

            } catch (Exception exception) {
                inputHistoricalPaid.setError(
                        "Enter a valid previous payment"
                );

                etHistoricalPaid.requestFocus();
                return;
            }
        }

        if (historicalPaid < 0
                || historicalPaid > totalAmount) {

            inputHistoricalPaid.setError(
                    "Amount must be between 0 and total amount"
            );

            etHistoricalPaid.requestFocus();
            return;
        }

        int historicalInstallments = 0;

        if (!installmentText.isEmpty()) {
            try {
                historicalInstallments =
                        Integer.parseInt(
                                installmentText
                        );

            } catch (Exception exception) {
                inputHistoricalInstallments.setError(
                        "Enter a valid installment count"
                );

                etHistoricalInstallments.requestFocus();
                return;
            }
        }

        int tenureMonths = 0;

        if (!tenureText.isEmpty()) {
            try {
                tenureMonths =
                        Integer.parseInt(
                                tenureText
                        );

            } catch (Exception exception) {
                inputTenureMonths.setError(
                        "Enter a valid tenure"
                );

                etTenureMonths.requestFocus();
                return;
            }
        }

        if (historicalInstallments < 0) {
            inputHistoricalInstallments.setError(
                    "Installment count cannot be negative"
            );

            etHistoricalInstallments.requestFocus();
            return;
        }

        if (tenureMonths < 0) {
            inputTenureMonths.setError(
                    "Tenure cannot be negative"
            );

            etTenureMonths.requestFocus();
            return;
        }

        if (tenureMonths > 0
                && historicalInstallments > tenureMonths) {

            inputHistoricalInstallments.setError(
                    "Previous EMIs cannot exceed total tenure"
            );

            etHistoricalInstallments.requestFocus();
            return;
        }

        double interestRate = 0;

        if (!interestText.isEmpty()) {
            try {
                interestRate =
                        Double.parseDouble(
                                interestText
                        );

            } catch (Exception exception) {
                inputInterestRate.setError(
                        "Enter a valid interest rate"
                );

                etInterestRate.requestFocus();
                return;
            }
        }

        double emiAmount = 0;

        if (!emiText.isEmpty()) {
            try {
                emiAmount =
                        Double.parseDouble(
                                emiText
                        );

            } catch (Exception exception) {
                inputEmiAmount.setError(
                        "Enter a valid EMI amount"
                );

                etEmiAmount.requestFocus();
                return;
            }
        }

        if (interestRate < 0) {
            inputInterestRate.setError(
                    "Interest rate cannot be negative"
            );

            etInterestRate.requestFocus();
            return;
        }

        if (emiAmount < 0) {
            inputEmiAmount.setError(
                    "EMI amount cannot be negative"
            );

            etEmiAmount.requestFocus();
            return;
        }

        double outstandingAmount =
                Math.max(
                        totalAmount - historicalPaid,
                        0
                );

        String selectedLoanType =
                safeText(
                        dropdownLoanType
                                .getText()
                                .toString(),
                        loanTypes[0]
                );

        Loan loan =
                new Loan();

        loan.setPersonName(personName);
        loan.setLoanType(selectedLoanType);
        loan.setTotalAmount(totalAmount);
        loan.setHistoricalPaidAmount(historicalPaid);
        loan.setHistoricalInstallments(historicalInstallments);
        loan.setOutstandingAmount(outstandingAmount);
        loan.setInterestRate(interestRate);
        loan.setEmiAmount(emiAmount);
        loan.setStartDate(selectedStartDate);
        loan.setTenureMonths(tenureMonths);
        loan.setDueDate(selectedDueDate);
        loan.setNote(getText(etLoanNote));
        loan.setActive(outstandingAmount > 0);

        btnSaveLoan.setEnabled(false);

        btnSaveLoan.setText(
                "Saving Loan..."
        );

        new Thread(() -> {
            try {
                DatabaseClient
                        .getInstance(
                                getApplicationContext()
                        )
                        .getAppDatabase()
                        .loanDao()
                        .insert(loan);

                runOnUiThread(() -> {
                    resetForm();

                    btnSaveLoan.setEnabled(true);

                    btnSaveLoan.setText(
                            "Save Smart Loan"
                    );

                    LoanReminderScheduler.schedule(
                            getApplicationContext()
                    );

                    Toast.makeText(
                            LoanActivity.this,
                            "Smart loan tracker created",
                            Toast.LENGTH_SHORT
                    ).show();

                    loadLoans();
                });

            } catch (Exception exception) {
                runOnUiThread(() -> {
                    btnSaveLoan.setEnabled(true);

                    btnSaveLoan.setText(
                            "Save Smart Loan"
                    );

                    Toast.makeText(
                            LoanActivity.this,
                            "Unable to create loan tracker",
                            Toast.LENGTH_SHORT
                    ).show();
                });
            }
        }).start();
    }

    private void clearFormErrors() {
        inputPersonName.setError(null);
        inputTotalAmount.setError(null);
        inputHistoricalPaid.setError(null);
        inputHistoricalInstallments.setError(null);
        inputTenureMonths.setError(null);
        inputInterestRate.setError(null);
        inputEmiAmount.setError(null);
    }

    private void resetForm() {
        etPersonName.setText("");
        etTotalAmount.setText("");
        etHistoricalPaid.setText("");
        etHistoricalInstallments.setText("");
        etTenureMonths.setText("");
        etInterestRate.setText("");
        etEmiAmount.setText("");
        etLoanNote.setText("");

        dropdownLoanType.setText(
                loanTypes[0],
                false
        );

        startCalendar =
                Calendar.getInstance();

        dueCalendar =
                Calendar.getInstance();

        dueCalendar.add(
                Calendar.MONTH,
                1
        );

        updateDateFields();
    }

    private void loadLoans() {
        new Thread(() -> {
            try {
                List<Loan> loans =
                        DatabaseClient
                                .getInstance(
                                        getApplicationContext()
                                )
                                .getAppDatabase()
                                .loanDao()
                                .getAllLoans();

                runOnUiThread(
                        () -> showLoans(loans)
                );

            } catch (Exception exception) {
                runOnUiThread(() -> {
                    loanContainer.removeAllViews();

                    txtEmptyLoans.setVisibility(
                            View.VISIBLE
                    );

                    Toast.makeText(
                            LoanActivity.this,
                            "Unable to load loans",
                            Toast.LENGTH_SHORT
                    ).show();
                });
            }
        }).start();
    }

    private void showLoans(
            List<Loan> loans
    ) {
        loanContainer.removeAllViews();

        boolean isEmpty =
                loans == null
                        || loans.isEmpty();

        txtEmptyLoans.setVisibility(
                isEmpty
                        ? View.VISIBLE
                        : View.GONE
        );

        if (isEmpty) {
            return;
        }

        for (Loan loan : loans) {
            if (loan != null) {
                addLoanCard(loan);
            }
        }
    }

    private void addLoanCard(
            Loan loan
    ) {
        boolean loanGiven =
                "Loan Given".equalsIgnoreCase(
                        safeText(
                                loan.getLoanType(),
                                "Loan Taken"
                        )
                );

        boolean isCompleted =
                loan.getOutstandingAmount() <= 0;

        boolean isActive =
                loan.isActive()
                        && !isCompleted;

        int accentColor =
                loanGiven
                        ? getColorValue(
                        R.color.success
                )
                        : getColorValue(
                        R.color.expense
                );

        int accentSurface =
                loanGiven
                        ? getColorValue(
                        R.color.success_surface
                )
                        : getColorValue(
                        R.color.expense_surface
                );

        int accentOutline =
                loanGiven
                        ? getColorValue(
                        R.color.success_outline
                )
                        : getColorValue(
                        R.color.expense_outline
                );

        String remainingLabel =
                loanGiven
                        ? "Amount To Receive"
                        : "Outstanding Amount";

        String completedLabel =
                loanGiven
                        ? "Recovered"
                        : "Paid";

        double totalAmount =
                Math.max(
                        loan.getTotalAmount(),
                        0
                );

        double outstandingAmount =
                Math.max(
                        loan.getOutstandingAmount(),
                        0
                );

        double completedAmount =
                Math.max(
                        totalAmount - outstandingAmount,
                        0
                );

        int progress =
                calculateProgress(
                        completedAmount,
                        totalAmount
                );

        MaterialCardView card =
                new MaterialCardView(this);

        card.setCardBackgroundColor(
                getColorValue(
                        R.color.app_surface
                )
        );

        card.setRadius(
                dpToPx(20)
        );

        card.setCardElevation(
                dpToPx(1)
        );

        card.setStrokeWidth(
                dpToPx(1)
        );

        card.setStrokeColor(
                isActive
                        ? accentOutline
                        : getColorValue(
                        R.color.app_outline_soft
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
                dpToPx(7)
        );

        card.setLayoutParams(
                cardParams
        );

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

        TextView loanIcon =
                createLoanIcon(
                        loanGiven,
                        accentColor,
                        accentSurface,
                        accentOutline
                );

        headerRow.addView(
                loanIcon
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
                dpToPx(11),
                0,
                dpToPx(8),
                0
        );

        titleContainer.setLayoutParams(
                titleParams
        );

        TextView title =
                createText(
                        safeText(
                                loan.getPersonName(),
                                "Loan"
                        ),
                        16,
                        getColorValue(
                                R.color.app_text_primary
                        ),
                        true
                );

        TextView type =
                createText(
                        safeText(
                                loan.getLoanType(),
                                "Loan"
                        ),
                        10,
                        getColorValue(
                                R.color.app_text_secondary
                        ),
                        false
                );

        LinearLayout.LayoutParams typeParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        typeParams.setMargins(
                0,
                dpToPx(3),
                0,
                0
        );

        type.setLayoutParams(
                typeParams
        );

        titleContainer.addView(title);
        titleContainer.addView(type);

        headerRow.addView(
                titleContainer
        );

        TextView statusBadge =
                createStatusBadge(
                        isCompleted
                                ? "Completed"
                                : isActive
                                  ? "Active"
                                  : "Archived",
                        isCompleted
                                ? getColorValue(
                                R.color.success
                        )
                                : isActive
                                  ? accentColor
                                  : getColorValue(
                                R.color.app_text_secondary
                        ),
                        isCompleted
                                ? getColorValue(
                                R.color.success_surface
                        )
                                : isActive
                                  ? accentSurface
                                  : getColorValue(
                                R.color.app_surface_muted
                        ),
                        isCompleted
                                ? getColorValue(
                                R.color.success_outline
                        )
                                : isActive
                                  ? accentOutline
                                  : getColorValue(
                                R.color.app_outline
                        )
                );

        headerRow.addView(
                statusBadge
        );

        content.addView(
                headerRow
        );

        /*
         * Outstanding amount
         */

        LinearLayout outstandingBox =
                createOutstandingBox(
                        remainingLabel,
                        formatAmount(
                                outstandingAmount
                        ),
                        isCompleted
                                ? completedLabel
                                  + " in full"
                                : getDueText(
                                loan.getDueDate()
                        ),
                        isCompleted
                                ? getColorValue(
                                R.color.success
                        )
                                : accentColor,
                        isCompleted
                                ? getColorValue(
                                R.color.success_surface
                        )
                                : accentSurface,
                        isCompleted
                                ? getColorValue(
                                R.color.success_outline
                        )
                                : accentOutline,
                        isCompleted
                                ? "✓"
                                : loanGiven
                                  ? "↑"
                                  : "↓"
                );

        LinearLayout.LayoutParams outstandingParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        outstandingParams.setMargins(
                0,
                dpToPx(13),
                0,
                0
        );

        outstandingBox.setLayoutParams(
                outstandingParams
        );

        content.addView(
                outstandingBox
        );

        /*
         * Progress
         */

        LinearLayout progressHeader =
                new LinearLayout(this);

        progressHeader.setOrientation(
                LinearLayout.HORIZONTAL
        );

        progressHeader.setGravity(
                Gravity.CENTER_VERTICAL
        );

        LinearLayout.LayoutParams progressHeaderParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        progressHeaderParams.setMargins(
                0,
                dpToPx(13),
                0,
                0
        );

        progressHeader.setLayoutParams(
                progressHeaderParams
        );

        TextView progressTitle =
                createText(
                        "Payment progress",
                        10,
                        getColorValue(
                                R.color.app_text_secondary
                        ),
                        false
                );

        LinearLayout.LayoutParams progressTitleParams =
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                );

        progressTitle.setLayoutParams(
                progressTitleParams
        );

        TextView progressValue =
                createText(
                        progress
                                + "% "
                                + completedLabel,
                        11,
                        isCompleted
                                ? getColorValue(
                                R.color.success
                        )
                                : accentColor,
                        true
                );

        progressHeader.addView(progressTitle);
        progressHeader.addView(progressValue);

        content.addView(
                progressHeader
        );

        ProgressBar progressBar =
                new ProgressBar(
                        this,
                        null,
                        android.R.attr
                                .progressBarStyleHorizontal
                );

        progressBar.setMax(100);
        progressBar.setProgress(progress);

        progressBar.setProgressTintList(
                ColorStateList.valueOf(
                        isCompleted
                                ? getColorValue(
                                R.color.success
                        )
                                : accentColor
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

        content.addView(
                progressBar
        );

        /*
         * Main metrics
         */

        LinearLayout metricsRow =
                new LinearLayout(this);

        metricsRow.setOrientation(
                LinearLayout.HORIZONTAL
        );

        metricsRow.setBaselineAligned(false);

        LinearLayout.LayoutParams metricsParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        metricsParams.setMargins(
                0,
                dpToPx(13),
                0,
                0
        );

        metricsRow.setLayoutParams(
                metricsParams
        );

        LinearLayout totalBlock =
                createMetricBlock(
                        "Total Amount",
                        formatAmount(totalAmount),
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

        LinearLayout.LayoutParams totalParams =
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                );

        totalParams.setMargins(
                0,
                0,
                dpToPx(5),
                0
        );

        totalBlock.setLayoutParams(
                totalParams
        );

        LinearLayout emiBlock =
                createMetricBlock(
                        "Monthly EMI",
                        loan.getEmiAmount() > 0
                                ? formatAmount(
                                loan.getEmiAmount()
                        )
                                : "Not added",
                        accentColor,
                        accentSurface,
                        accentOutline
                );

        LinearLayout.LayoutParams emiParams =
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                );

        emiParams.setMargins(
                dpToPx(5),
                0,
                0,
                0
        );

        emiBlock.setLayoutParams(
                emiParams
        );

        metricsRow.addView(totalBlock);
        metricsRow.addView(emiBlock);

        content.addView(
                metricsRow
        );

        /*
         * Schedule details
         */

        LinearLayout scheduleBox =
                createScheduleBox(
                        loan,
                        accentColor
                );

        LinearLayout.LayoutParams scheduleParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        scheduleParams.setMargins(
                0,
                dpToPx(10),
                0,
                0
        );

        scheduleBox.setLayoutParams(
                scheduleParams
        );

        content.addView(
                scheduleBox
        );

        /*
         * Historical payments
         */

        LinearLayout historySummary =
                createInformationBox(
                        "Previous Payment Baseline",
                        formatAmount(
                                loan.getHistoricalPaidAmount()
                        ),
                        loan.getHistoricalInstallments()
                                + " previous installment(s)",
                        getColorValue(
                                R.color.purple
                        ),
                        getColorValue(
                                R.color.purple_surface
                        ),
                        getColorValue(
                                R.color.purple_outline
                        ),
                        "↶"
                );

        LinearLayout.LayoutParams historySummaryParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        historySummaryParams.setMargins(
                0,
                dpToPx(10),
                0,
                0
        );

        historySummary.setLayoutParams(
                historySummaryParams
        );

        content.addView(
                historySummary
        );

        /*
         * Note
         */

        String note =
                safeText(
                        loan.getNote(),
                        ""
                );

        if (!note.isEmpty()) {
            LinearLayout noteBox =
                    createNoteBox(note);

            LinearLayout.LayoutParams noteParams =
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    );

            noteParams.setMargins(
                    0,
                    dpToPx(10),
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
         * Action buttons
         */

        LinearLayout firstActionRow =
                createActionRow();

        LinearLayout secondActionRow =
                createActionRow();

        MaterialButton btnEmi =
                createActionButton(
                        loanGiven
                                ? "Record Receipt"
                                : "Pay EMI",
                        accentColor,
                        accentSurface,
                        accentOutline
                );

        MaterialButton btnExtra =
                createActionButton(
                        loanGiven
                                ? "Extra Receipt"
                                : "Extra Payment",
                        getColorValue(
                                R.color.purple
                        ),
                        getColorValue(
                                R.color.purple_surface
                        ),
                        getColorValue(
                                R.color.purple_outline
                        )
                );

        MaterialButton btnHistory =
                createActionButton(
                        "History",
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

        MaterialButton btnArchive =
                createActionButton(
                        loan.isActive()
                                ? "Archive"
                                : "Archived",
                        getColorValue(
                                R.color.app_text_secondary
                        ),
                        getColorValue(
                                R.color.app_surface_muted
                        ),
                        getColorValue(
                                R.color.app_outline
                        )
                );

        btnEmi.setEnabled(isActive);
        btnExtra.setEnabled(isActive);

        btnEmi.setAlpha(
                isActive
                        ? 1f
                        : 0.5f
        );

        btnExtra.setAlpha(
                isActive
                        ? 1f
                        : 0.5f
        );

        btnEmi.setOnClickListener(
                view -> showPaymentDialog(
                        loan,
                        "EMI"
                )
        );

        btnExtra.setOnClickListener(
                view -> showPaymentDialog(
                        loan,
                        "EXTRA"
                )
        );

        btnHistory.setOnClickListener(
                view -> showPaymentHistory(
                        loan
                )
        );

        btnArchive.setOnClickListener(
                view -> archiveLoan(
                        loan
                )
        );

        addButtonToRow(
                firstActionRow,
                btnEmi
        );

        addButtonToRow(
                firstActionRow,
                btnExtra
        );

        addButtonToRow(
                secondActionRow,
                btnHistory
        );

        addButtonToRow(
                secondActionRow,
                btnArchive
        );

        BubbleTouchAnimator.apply(card);
        BubbleTouchAnimator.apply(btnEmi);
        BubbleTouchAnimator.apply(btnExtra);
        BubbleTouchAnimator.apply(btnHistory);
        BubbleTouchAnimator.apply(btnArchive);

        content.addView(firstActionRow);
        content.addView(secondActionRow);

        card.addView(content);

        loanContainer.addView(card);
    }

    private TextView createLoanIcon(
            boolean loanGiven,
            int accentColor,
            int backgroundColor,
            int outlineColor
    ) {
        TextView icon =
                createText(
                        loanGiven
                                ? "↑"
                                : "↓",
                        20,
                        accentColor,
                        true
                );

        icon.setGravity(
                Gravity.CENTER
        );

        icon.setBackground(
                createRoundedDrawable(
                        backgroundColor,
                        outlineColor,
                        14
                )
        );

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        dpToPx(46),
                        dpToPx(46)
                );

        icon.setLayoutParams(params);

        return icon;
    }

    private TextView createStatusBadge(
            String text,
            int textColor,
            int backgroundColor,
            int outlineColor
    ) {
        TextView badge =
                createText(
                        text,
                        9,
                        textColor,
                        true
                );

        badge.setGravity(
                Gravity.CENTER
        );

        badge.setPadding(
                dpToPx(10),
                0,
                dpToPx(10),
                0
        );

        badge.setBackground(
                createRoundedDrawable(
                        backgroundColor,
                        outlineColor,
                        13
                )
        );

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        dpToPx(29)
                );

        badge.setLayoutParams(params);

        return badge;
    }

    private LinearLayout createOutstandingBox(
            String label,
            String value,
            String description,
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
                dpToPx(12),
                dpToPx(11),
                dpToPx(12),
                dpToPx(11)
        );

        container.setBackground(
                createRoundedDrawable(
                        backgroundColor,
                        outlineColor,
                        14
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
                        dpToPx(38),
                        dpToPx(38)
                );

        icon.setLayoutParams(
                iconParams
        );

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
                        18,
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

        textContainer.addView(labelView);
        textContainer.addView(valueView);
        textContainer.addView(descriptionView);

        container.addView(
                textContainer
        );

        return container;
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
                        13,
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

    private LinearLayout createScheduleBox(
            Loan loan,
            int accentColor
    ) {
        LinearLayout container =
                new LinearLayout(this);

        container.setOrientation(
                LinearLayout.VERTICAL
        );

        container.setPadding(
                dpToPx(12),
                dpToPx(10),
                dpToPx(12),
                dpToPx(10)
        );

        container.setBackground(
                createRoundedDrawable(
                        getColorValue(
                                R.color.app_surface_soft
                        ),
                        getColorValue(
                                R.color.app_outline_soft
                        ),
                        13
                )
        );

        TextView title =
                createText(
                        "Loan Schedule",
                        11,
                        accentColor,
                        true
                );

        container.addView(title);

        addInfoLine(
                container,
                "Remaining EMIs",
                getRemainingEmiText(loan)
        );

        addInfoLine(
                container,
                "Next Due",
                getDueText(
                        loan.getDueDate()
                )
        );

        addInfoLine(
                container,
                "Expected Closure",
                getExpectedClosureText(loan)
        );

        addInfoLine(
                container,
                "Interest Rate",
                loan.getInterestRate() > 0
                        ? String.format(
                        Locale.US,
                        "%.2f%%",
                        loan.getInterestRate()
                )
                        : "Not added"
        );

        return container;
    }

    private LinearLayout createInformationBox(
            String title,
            String value,
            String description,
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
                dpToPx(12),
                dpToPx(10),
                dpToPx(12),
                dpToPx(10)
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
                        17,
                        accentColor,
                        true
                );

        icon.setGravity(
                Gravity.CENTER
        );

        LinearLayout.LayoutParams iconParams =
                new LinearLayout.LayoutParams(
                        dpToPx(36),
                        dpToPx(36)
                );

        icon.setLayoutParams(
                iconParams
        );

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

        container.addView(
                textContainer
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
                dpToPx(12),
                dpToPx(10),
                dpToPx(12),
                dpToPx(10)
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
                        "Loan Note",
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
                dpToPx(2),
                1f
        );

        LinearLayout.LayoutParams messageParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        messageParams.setMargins(
                0,
                dpToPx(4),
                0,
                0
        );

        message.setLayoutParams(
                messageParams
        );

        container.addView(title);
        container.addView(message);

        return container;
    }

    private void addInfoLine(
            LinearLayout parent,
            String label,
            String value
    ) {
        LinearLayout row =
                new LinearLayout(this);

        row.setOrientation(
                LinearLayout.HORIZONTAL
        );

        row.setGravity(
                Gravity.CENTER_VERTICAL
        );

        LinearLayout.LayoutParams rowParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        rowParams.setMargins(
                0,
                dpToPx(7),
                0,
                0
        );

        row.setLayoutParams(
                rowParams
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

        labelView.setLayoutParams(
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                )
        );

        TextView valueView =
                createText(
                        value,
                        10,
                        getColorValue(
                                R.color.app_text_primary
                        ),
                        true
                );

        valueView.setGravity(
                Gravity.END
        );

        valueView.setLayoutParams(
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1.25f
                )
        );

        row.addView(labelView);
        row.addView(valueView);

        parent.addView(row);
    }

    private LinearLayout createActionRow() {
        LinearLayout row =
                new LinearLayout(this);

        row.setOrientation(
                LinearLayout.HORIZONTAL
        );

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dpToPx(46)
                );

        params.setMargins(
                0,
                dpToPx(10),
                0,
                0
        );

        row.setLayoutParams(params);

        return row;
    }

    private void addButtonToRow(
            LinearLayout row,
            MaterialButton button
    ) {
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        1f
                );

        params.setMargins(
                dpToPx(4),
                0,
                dpToPx(4),
                0
        );

        button.setLayoutParams(params);

        row.addView(button);
    }

    private MaterialButton createActionButton(
            String text,
            int textColor,
            int backgroundColor,
            int outlineColor
    ) {
        MaterialButton button =
                new MaterialButton(this);

        button.setText(text);
        button.setTextColor(textColor);
        button.setTextSize(10);
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
                        backgroundColor
                )
        );

        button.setStrokeColor(
                ColorStateList.valueOf(
                        outlineColor
                )
        );

        button.setStrokeWidth(
                dpToPx(1)
        );

        button.setPadding(
                dpToPx(2),
                0,
                dpToPx(2),
                0
        );

        return button;
    }

    private void showPaymentDialog(
            Loan loan,
            String paymentType
    ) {
        new Thread(() -> {
            try {
                List<Account> accounts =
                        DatabaseClient
                                .getInstance(
                                        getApplicationContext()
                                )
                                .getAppDatabase()
                                .accountDao()
                                .getAllAccounts();

                List<String> accountNames =
                        new ArrayList<>();

                if (accounts != null) {
                    for (Account account : accounts) {
                        if (account != null
                                && account.getName() != null
                                && !account.getName()
                                .trim()
                                .isEmpty()) {

                            accountNames.add(
                                    account.getName()
                                            .trim()
                            );
                        }
                    }
                }

                if (accountNames.isEmpty()) {
                    accountNames.add("Cash");
                }

                runOnUiThread(() ->
                        createPaymentDialog(
                                loan,
                                paymentType,
                                accountNames
                        )
                );

            } catch (Exception exception) {
                runOnUiThread(() -> {
                    List<String> fallbackAccounts =
                            new ArrayList<>();

                    fallbackAccounts.add("Cash");

                    createPaymentDialog(
                            loan,
                            paymentType,
                            fallbackAccounts
                    );
                });
            }
        }).start();
    }

    private void createPaymentDialog(
            Loan loan,
            String paymentType,
            List<String> accountNames
    ) {
        boolean extraPayment =
                "EXTRA".equalsIgnoreCase(
                        paymentType
                );

        boolean loanGiven =
                "Loan Given".equalsIgnoreCase(
                        safeText(
                                loan.getLoanType(),
                                "Loan Taken"
                        )
                );

        int accentColor =
                loanGiven
                        ? getColorValue(
                        R.color.success
                )
                        : getColorValue(
                        R.color.expense
                );

        int accentSurface =
                loanGiven
                        ? getColorValue(
                        R.color.success_surface
                )
                        : getColorValue(
                        R.color.expense_surface
                );

        int accentOutline =
                loanGiven
                        ? getColorValue(
                        R.color.success_outline
                )
                        : getColorValue(
                        R.color.expense_outline
                );

        String actionName;

        if (loanGiven) {
            actionName =
                    extraPayment
                            ? "Record Extra Receipt"
                            : "Record EMI Receipt";

        } else {
            actionName =
                    extraPayment
                            ? "Record Extra Payment"
                            : "Pay EMI";
        }

        LinearLayout dialogLayout =
                new LinearLayout(this);

        dialogLayout.setOrientation(
                LinearLayout.VERTICAL
        );

        dialogLayout.setPadding(
                dpToPx(22),
                dpToPx(6),
                dpToPx(22),
                dpToPx(4)
        );

        LinearLayout balanceBox =
                createInformationBox(
                        loanGiven
                                ? "Amount To Receive"
                                : "Outstanding Amount",
                        formatAmount(
                                loan.getOutstandingAmount()
                        ),
                        safeText(
                                loan.getPersonName(),
                                "Loan"
                        ),
                        accentColor,
                        accentSurface,
                        accentOutline,
                        loanGiven
                                ? "↑"
                                : "↓"
                );

        dialogLayout.addView(
                balanceBox
        );

        TextInputLayout amountInput =
                createDialogInputLayout(
                        "Amount",
                        accentColor
                );

        amountInput.setPrefixText("₹  ");

        TextInputEditText etAmount =
                new TextInputEditText(this);

        etAmount.setInputType(
                InputType.TYPE_CLASS_NUMBER
                        | InputType.TYPE_NUMBER_FLAG_DECIMAL
        );

        etAmount.setSingleLine(true);
        etAmount.setTextSize(17);
        etAmount.setMinHeight(dpToPx(56));

        if (!extraPayment
                && loan.getEmiAmount() > 0) {

            etAmount.setText(
                    String.format(
                            Locale.US,
                            "%.2f",
                            Math.min(
                                    loan.getEmiAmount(),
                                    loan.getOutstandingAmount()
                            )
                    )
            );
        }

        amountInput.addView(etAmount);

        LinearLayout.LayoutParams amountParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        amountParams.setMargins(
                0,
                dpToPx(14),
                0,
                0
        );

        amountInput.setLayoutParams(
                amountParams
        );

        dialogLayout.addView(
                amountInput
        );

        TextInputLayout accountInput =
                createDialogInputLayout(
                        loanGiven
                                ? "Received In Account"
                                : "Paid From Account",
                        accentColor
                );

        accountInput.setEndIconMode(
                TextInputLayout.END_ICON_DROPDOWN_MENU
        );

        MaterialAutoCompleteTextView dropdownAccount =
                new MaterialAutoCompleteTextView(this);

        dropdownAccount.setFocusable(false);
        dropdownAccount.setInputType(0);
        dropdownAccount.setMinHeight(dpToPx(56));
        dropdownAccount.setPadding(
                dpToPx(14),
                0,
                dpToPx(14),
                0
        );

        ArrayAdapter<String> accountAdapter =
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_list_item_1,
                        accountNames
                );

        dropdownAccount.setAdapter(
                accountAdapter
        );

        dropdownAccount.setText(
                accountNames.get(0),
                false
        );

        accountInput.addView(
                dropdownAccount
        );

        LinearLayout.LayoutParams accountParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        accountParams.setMargins(
                0,
                dpToPx(12),
                0,
                0
        );

        accountInput.setLayoutParams(
                accountParams
        );

        dialogLayout.addView(
                accountInput
        );

        TextInputLayout noteInput =
                createDialogInputLayout(
                        "Optional note",
                        accentColor
                );

        TextInputEditText etNote =
                new TextInputEditText(this);

        etNote.setInputType(
                InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        );

        etNote.setMinHeight(dpToPx(56));

        noteInput.addView(etNote);

        LinearLayout.LayoutParams noteParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        noteParams.setMargins(
                0,
                dpToPx(12),
                0,
                0
        );

        noteInput.setLayoutParams(
                noteParams
        );

        dialogLayout.addView(
                noteInput
        );

        AlertDialog dialog =
                new AlertDialog.Builder(this)
                        .setTitle(actionName)
                        .setView(dialogLayout)
                        .setNegativeButton(
                                "Cancel",
                                null
                        )
                        .setPositiveButton(
                                "Save",
                                null
                        )
                        .create();

        dialog.setOnShowListener(listener -> {
            Button saveButton =
                    dialog.getButton(
                            AlertDialog.BUTTON_POSITIVE
                    );

            saveButton.setTextColor(
                    accentColor
            );

            saveButton.setOnClickListener(view -> {
                String amountText =
                        getText(etAmount);

                double amount;

                try {
                    amount =
                            Double.parseDouble(
                                    amountText
                            );

                } catch (Exception exception) {
                    amountInput.setError(
                            "Enter a valid amount"
                    );

                    etAmount.requestFocus();
                    return;
                }

                if (amount <= 0) {
                    amountInput.setError(
                            "Amount must be greater than zero"
                    );

                    etAmount.requestFocus();
                    return;
                }

                if (amount
                        > loan.getOutstandingAmount()) {

                    amountInput.setError(
                            "Amount cannot exceed the outstanding balance"
                    );

                    etAmount.requestFocus();
                    return;
                }

                String accountName =
                        safeText(
                                dropdownAccount
                                        .getText()
                                        .toString(),
                                "Cash"
                        );

                amountInput.setError(null);
                saveButton.setEnabled(false);
                saveButton.setText("Saving...");

                saveLoanPayment(
                        loan,
                        paymentType,
                        amount,
                        accountName,
                        getText(etNote),
                        dialog,
                        saveButton
                );
            });
        });

        dialog.show();
    }

    private TextInputLayout createDialogInputLayout(
            String hint,
            int accentColor
    ) {
        TextInputLayout inputLayout =
                new TextInputLayout(this);

        inputLayout.setHint(hint);

        inputLayout.setBoxBackgroundMode(
                TextInputLayout.BOX_BACKGROUND_OUTLINE
        );

        inputLayout.setBoxStrokeColor(
                accentColor
        );

        inputLayout.setBoxCornerRadii(
                dpToPx(14),
                dpToPx(14),
                dpToPx(14),
                dpToPx(14)
        );

        return inputLayout;
    }

    private void saveLoanPayment(
            Loan loan,
            String paymentType,
            double paymentAmount,
            String accountName,
            String paymentNote,
            AlertDialog dialog,
            Button saveButton
    ) {
        boolean loanGiven =
                "Loan Given".equalsIgnoreCase(
                        safeText(
                                loan.getLoanType(),
                                "Loan Taken"
                        )
                );

        String transactionDate =
                new SimpleDateFormat(
                        "yyyy-MM-dd HH:mm",
                        Locale.US
                ).format(
                        Calendar
                                .getInstance()
                                .getTime()
                );

        Transaction transaction =
                new Transaction();

        transaction.setType(
                loanGiven
                        ? "INCOME"
                        : "EXPENSE"
        );

        transaction.setAmount(
                paymentAmount
        );

        transaction.setCategory(
                "EXTRA".equalsIgnoreCase(
                        paymentType
                )
                        ? loanGiven
                          ? "Loan Extra Receipt"
                          : "Loan Extra Payment"
                        : loanGiven
                          ? "Loan EMI Receipt"
                          : "Loan EMI"
        );

        transaction.setAccount(
                accountName
        );

        transaction.setNote(
                (
                        "EXTRA".equalsIgnoreCase(
                                paymentType
                        )
                                ? loanGiven
                                  ? "Extra loan receipt"
                                  : "Extra loan payment"
                                : loanGiven
                                  ? "Loan EMI receipt"
                                  : "Loan EMI payment"
                )
                        + " - "
                        + safeText(
                        loan.getPersonName(),
                        "Loan"
                )
                        + (
                        paymentNote.isEmpty()
                                ? ""
                                : " - "
                                  + paymentNote
                )
        );

        transaction.setDate(
                transactionDate
        );

        LoanPayment loanPayment =
                new LoanPayment();

        loanPayment.setLoanId(
                loan.getId()
        );

        loanPayment.setAmount(
                paymentAmount
        );

        loanPayment.setPaymentType(
                paymentType
        );

        loanPayment.setAccount(
                accountName
        );

        loanPayment.setPaymentDate(
                transactionDate
        );

        loanPayment.setNote(
                paymentNote
        );

        double oldOutstanding =
                loan.getOutstandingAmount();

        String oldDueDate =
                loan.getDueDate();

        boolean oldActiveState =
                loan.isActive();

        double remainingAmount =
                oldOutstanding
                        - paymentAmount;

        loan.setOutstandingAmount(
                Math.max(
                        remainingAmount,
                        0
                )
        );

        if ("EMI".equalsIgnoreCase(
                paymentType
        )
                && remainingAmount > 0) {

            loan.setDueDate(
                    addOneMonth(
                            loan.getDueDate()
                    )
            );
        }

        if (remainingAmount <= 0) {
            loan.setActive(false);
        }

        new Thread(() -> {
            try {
                AppDatabase database =
                        DatabaseClient
                                .getInstance(
                                        getApplicationContext()
                                )
                                .getAppDatabase();

                database.runInTransaction(() -> {
                    database.transactionDao()
                            .insert(transaction);

                    database.loanPaymentDao()
                            .insert(loanPayment);

                    database.loanDao()
                            .update(loan);
                });

                runOnUiThread(() -> {
                    dialog.dismiss();

                    LoanReminderScheduler.schedule(
                            getApplicationContext()
                    );

                    Toast.makeText(
                            LoanActivity.this,
                            "EXTRA".equalsIgnoreCase(
                                    paymentType
                            )
                                    ? loanGiven
                                      ? "Extra receipt recorded"
                                      : "Extra payment recorded"
                                    : loanGiven
                                      ? "EMI receipt recorded"
                                      : "EMI recorded successfully",
                            Toast.LENGTH_SHORT
                    ).show();

                    loadLoans();
                });

            } catch (Exception exception) {
                loan.setOutstandingAmount(
                        oldOutstanding
                );

                loan.setDueDate(
                        oldDueDate
                );

                loan.setActive(
                        oldActiveState
                );

                runOnUiThread(() -> {
                    saveButton.setEnabled(true);
                    saveButton.setText("Save");

                    Toast.makeText(
                            LoanActivity.this,
                            "Unable to record payment",
                            Toast.LENGTH_SHORT
                    ).show();
                });
            }
        }).start();
    }

    private void showPaymentHistory(
            Loan loan
    ) {
        new Thread(() -> {
            try {
                List<LoanPayment> payments =
                        DatabaseClient
                                .getInstance(
                                        getApplicationContext()
                                )
                                .getAppDatabase()
                                .loanPaymentDao()
                                .getPaymentsForLoan(
                                        loan.getId()
                                );

                runOnUiThread(() ->
                        createHistoryDialog(
                                loan,
                                payments
                        )
                );

            } catch (Exception exception) {
                runOnUiThread(() ->
                        Toast.makeText(
                                LoanActivity.this,
                                "Unable to load payment history",
                                Toast.LENGTH_SHORT
                        ).show()
                );
            }
        }).start();
    }

    private void createHistoryDialog(
            Loan loan,
            List<LoanPayment> payments
    ) {
        boolean loanGiven =
                "Loan Given".equalsIgnoreCase(
                        safeText(
                                loan.getLoanType(),
                                "Loan Taken"
                        )
                );

        int accentColor =
                loanGiven
                        ? getColorValue(
                        R.color.success
                )
                        : getColorValue(
                        R.color.expense
                );

        int accentSurface =
                loanGiven
                        ? getColorValue(
                        R.color.success_surface
                )
                        : getColorValue(
                        R.color.expense_surface
                );

        int accentOutline =
                loanGiven
                        ? getColorValue(
                        R.color.success_outline
                )
                        : getColorValue(
                        R.color.expense_outline
                );

        LinearLayout historyLayout =
                new LinearLayout(this);

        historyLayout.setOrientation(
                LinearLayout.VERTICAL
        );

        historyLayout.setPadding(
                dpToPx(22),
                dpToPx(6),
                dpToPx(22),
                dpToPx(8)
        );

        LinearLayout baseline =
                createInformationBox(
                        "Paid Before App",
                        formatAmount(
                                loan.getHistoricalPaidAmount()
                        ),
                        loan.getHistoricalInstallments()
                                + " previous installment(s)",
                        getColorValue(
                                R.color.purple
                        ),
                        getColorValue(
                                R.color.purple_surface
                        ),
                        getColorValue(
                                R.color.purple_outline
                        ),
                        "↶"
                );

        historyLayout.addView(
                baseline
        );

        if (payments == null
                || payments.isEmpty()) {

            TextView empty =
                    createText(
                            "No EMI or extra payment has been recorded in the app yet.",
                            13,
                            getColorValue(
                                    R.color.app_text_secondary
                            ),
                            false
                    );

            empty.setGravity(
                    Gravity.CENTER
            );

            empty.setPadding(
                    dpToPx(12),
                    dpToPx(24),
                    dpToPx(12),
                    dpToPx(24)
            );

            historyLayout.addView(
                    empty
            );

        } else {
            for (int index = 0;
                 index < payments.size();
                 index++) {

                LoanPayment payment =
                        payments.get(index);

                if (payment == null) {
                    continue;
                }

                MaterialCardView historyCard =
                        createHistoryCard(
                                payment,
                                index + 1,
                                accentColor,
                                accentSurface,
                                accentOutline
                        );

                historyLayout.addView(
                        historyCard
                );
            }
        }

        new AlertDialog.Builder(this)
                .setTitle(
                        safeText(
                                loan.getPersonName(),
                                "Loan"
                        )
                                + " History"
                )
                .setView(historyLayout)
                .setPositiveButton(
                        "Close",
                        null
                )
                .show();
    }

    private MaterialCardView createHistoryCard(
            LoanPayment payment,
            int number,
            int accentColor,
            int backgroundColor,
            int outlineColor
    ) {
        MaterialCardView card =
                new MaterialCardView(this);

        card.setCardBackgroundColor(
                getColorValue(
                        R.color.app_surface_soft
                )
        );

        card.setRadius(
                dpToPx(14)
        );

        card.setCardElevation(0);
        card.setStrokeWidth(dpToPx(1));
        card.setStrokeColor(outlineColor);

        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        cardParams.setMargins(
                0,
                dpToPx(10),
                0,
                0
        );

        card.setLayoutParams(
                cardParams
        );

        LinearLayout content =
                new LinearLayout(this);

        content.setOrientation(
                LinearLayout.VERTICAL
        );

        content.setPadding(
                dpToPx(12),
                dpToPx(11),
                dpToPx(12),
                dpToPx(11)
        );

        LinearLayout header =
                new LinearLayout(this);

        header.setOrientation(
                LinearLayout.HORIZONTAL
        );

        header.setGravity(
                Gravity.CENTER_VERTICAL
        );

        TextView numberView =
                createText(
                        String.valueOf(number),
                        11,
                        accentColor,
                        true
                );

        numberView.setGravity(
                Gravity.CENTER
        );

        numberView.setBackground(
                createRoundedDrawable(
                        backgroundColor,
                        outlineColor,
                        11
                )
        );

        LinearLayout.LayoutParams numberParams =
                new LinearLayout.LayoutParams(
                        dpToPx(34),
                        dpToPx(34)
                );

        numberView.setLayoutParams(
                numberParams
        );

        header.addView(numberView);

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
                dpToPx(9),
                0,
                dpToPx(8),
                0
        );

        titleContainer.setLayoutParams(
                titleParams
        );

        TextView paymentType =
                createText(
                        "EXTRA".equalsIgnoreCase(
                                payment.getPaymentType()
                        )
                                ? "Extra Payment"
                                : "EMI Payment",
                        12,
                        getColorValue(
                                R.color.app_text_primary
                        ),
                        true
                );

        TextView paymentDate =
                createText(
                        formatPaymentDate(
                                payment.getPaymentDate()
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
                dpToPx(2),
                0,
                0
        );

        paymentDate.setLayoutParams(
                dateParams
        );

        titleContainer.addView(paymentType);
        titleContainer.addView(paymentDate);

        header.addView(titleContainer);

        TextView amount =
                createText(
                        formatAmount(
                                payment.getAmount()
                        ),
                        13,
                        accentColor,
                        true
                );

        amount.setGravity(
                Gravity.END
        );

        header.addView(amount);

        content.addView(header);

        TextView account =
                createText(
                        "Account: "
                                + safeText(
                                payment.getAccount(),
                                "Cash"
                        ),
                        10,
                        getColorValue(
                                R.color.app_text_secondary
                        ),
                        false
                );

        LinearLayout.LayoutParams accountParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        accountParams.setMargins(
                dpToPx(43),
                dpToPx(6),
                0,
                0
        );

        account.setLayoutParams(
                accountParams
        );

        content.addView(account);

        String note =
                safeText(
                        payment.getNote(),
                        ""
                );

        if (!note.isEmpty()) {
            TextView noteView =
                    createText(
                            note,
                            10,
                            getColorValue(
                                    R.color.app_text_primary
                            ),
                            false
                    );

            LinearLayout.LayoutParams noteParams =
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    );

            noteParams.setMargins(
                    dpToPx(43),
                    dpToPx(4),
                    0,
                    0
            );

            noteView.setLayoutParams(
                    noteParams
            );

            content.addView(noteView);
        }

        card.addView(content);

        return card;
    }

    private void archiveLoan(
            Loan loan
    ) {
        String message =
                loan.isActive()
                        ? "Archive this loan? No transaction or payment history will be deleted."
                        : "This loan is already archived. Its saved history remains available.";

        String positiveText =
                loan.isActive()
                        ? "Archive"
                        : "Close";

        AlertDialog.Builder builder =
                new AlertDialog.Builder(this)
                        .setTitle(
                                loan.isActive()
                                        ? "Archive Loan"
                                        : "Loan Archived"
                        )
                        .setMessage(message)
                        .setNegativeButton(
                                "Cancel",
                                null
                        );

        if (loan.isActive()) {
            builder.setPositiveButton(
                    positiveText,
                    (dialog, which) -> {
                        boolean previousState =
                                loan.isActive();

                        loan.setActive(false);

                        new Thread(() -> {
                            try {
                                DatabaseClient
                                        .getInstance(
                                                getApplicationContext()
                                        )
                                        .getAppDatabase()
                                        .loanDao()
                                        .update(loan);

                                runOnUiThread(() -> {
                                    LoanReminderScheduler.schedule(
                                            getApplicationContext()
                                    );

                                    Toast.makeText(
                                            LoanActivity.this,
                                            "Loan archived safely",
                                            Toast.LENGTH_SHORT
                                    ).show();

                                    loadLoans();
                                });

                            } catch (Exception exception) {
                                loan.setActive(
                                        previousState
                                );

                                runOnUiThread(() ->
                                        Toast.makeText(
                                                LoanActivity.this,
                                                "Unable to archive loan",
                                                Toast.LENGTH_SHORT
                                        ).show()
                                );
                            }
                        }).start();
                    }
            );

        } else {
            builder.setPositiveButton(
                    positiveText,
                    null
            );
        }

        builder.show();
    }

    private int calculateProgress(
            double paidAmount,
            double totalAmount
    ) {
        if (totalAmount <= 0) {
            return 0;
        }

        int progress =
                (int) Math.round(
                        (
                                paidAmount
                                        / totalAmount
                        ) * 100
                );

        return Math.min(
                Math.max(progress, 0),
                100
        );
    }

    private String getRemainingEmiText(
            Loan loan
    ) {
        if (loan.getOutstandingAmount() <= 0) {
            return "Completed";
        }

        if (loan.getEmiAmount() <= 0) {
            return "EMI not added";
        }

        int remaining =
                (int) Math.ceil(
                        loan.getOutstandingAmount()
                                / loan.getEmiAmount()
                );

        return remaining
                + " month(s)";
    }

    private String getExpectedClosureText(
            Loan loan
    ) {
        if (loan.getOutstandingAmount() <= 0) {
            return "Completed";
        }

        if (loan.getEmiAmount() <= 0) {
            return "EMI not added";
        }

        int remainingEmis =
                (int) Math.ceil(
                        loan.getOutstandingAmount()
                                / loan.getEmiAmount()
                );

        Calendar calendar =
                parseStorageDate(
                        loan.getDueDate()
                );

        if (calendar == null) {
            return "Not available";
        }

        calendar.add(
                Calendar.MONTH,
                Math.max(
                        remainingEmis - 1,
                        0
                )
        );

        return formatVisibleDate(
                calendar
        );
    }

    private String getDueText(
            String dueDate
    ) {
        Calendar parsedDueDate =
                parseStorageDate(
                        dueDate
                );

        if (parsedDueDate == null) {
            return "Due date not added";
        }

        Calendar today =
                Calendar.getInstance();

        clearTime(today);
        clearTime(parsedDueDate);

        long difference =
                parsedDueDate.getTimeInMillis()
                        - today.getTimeInMillis();

        long days =
                difference
                        / (
                        24L
                                * 60L
                                * 60L
                                * 1000L
                );

        if (days < 0) {
            return "Overdue by "
                    + Math.abs(days)
                    + " day(s)";
        }

        if (days == 0) {
            return "Due today";
        }

        return formatVisibleDate(
                parsedDueDate
        )
                + " • "
                + days
                + " day(s) left";
    }

    private String addOneMonth(
            String dueDate
    ) {
        Calendar calendar =
                parseStorageDate(
                        dueDate
                );

        if (calendar == null) {
            calendar =
                    Calendar.getInstance();
        }

        calendar.add(
                Calendar.MONTH,
                1
        );

        return formatStorageDate(
                calendar
        );
    }

    private Calendar parseStorageDate(
            String value
    ) {
        if (value == null
                || value.trim().isEmpty()) {

            return null;
        }

        try {
            SimpleDateFormat format =
                    new SimpleDateFormat(
                            "yyyy-MM-dd",
                            Locale.US
                    );

            format.setLenient(false);

            Date parsedDate =
                    format.parse(
                            value.trim()
                    );

            if (parsedDate == null) {
                return null;
            }

            Calendar calendar =
                    Calendar.getInstance();

            calendar.setTime(
                    parsedDate
            );

            return calendar;

        } catch (Exception exception) {
            return null;
        }
    }

    private String formatPaymentDate(
            String value
    ) {
        if (value == null
                || value.trim().isEmpty()) {

            return "Date not available";
        }

        String[] patterns = {
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd HH:mm",
                "yyyy-MM-dd"
        };

        for (String pattern : patterns) {
            try {
                SimpleDateFormat inputFormat =
                        new SimpleDateFormat(
                                pattern,
                                Locale.US
                        );

                inputFormat.setLenient(false);

                Date date =
                        inputFormat.parse(
                                value.trim()
                        );

                if (date != null) {
                    return new SimpleDateFormat(
                            "dd MMM yyyy, hh:mm a",
                            Locale.ENGLISH
                    ).format(date);
                }

            } catch (Exception ignored) {
                // Try the next supported format.
            }
        }

        return value;
    }

    private String formatStorageDate(
            Calendar calendar
    ) {
        return new SimpleDateFormat(
                "yyyy-MM-dd",
                Locale.US
        ).format(
                calendar.getTime()
        );
    }

    private String formatVisibleDate(
            Calendar calendar
    ) {
        return new SimpleDateFormat(
                "dd MMM yyyy",
                Locale.ENGLISH
        ).format(
                calendar.getTime()
        );
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

    private GradientDrawable createRoundedDrawable(
            int backgroundColor,
            int outlineColor,
            int radiusDp
    ) {
        GradientDrawable drawable =
                new GradientDrawable();

        drawable.setColor(
                backgroundColor
        );

        drawable.setStroke(
                dpToPx(1),
                outlineColor
        );

        drawable.setCornerRadius(
                dpToPx(radiusDp)
        );

        return drawable;
    }

    private String getText(
            TextView view
    ) {
        return view.getText() == null
                ? ""
                : view
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

    private String formatAmount(
            double amount
    ) {
        NumberFormat numberFormat =
                NumberFormat.getNumberInstance(
                        new Locale(
                                "en",
                                "IN"
                        )
                );

        numberFormat.setMinimumFractionDigits(2);
        numberFormat.setMaximumFractionDigits(2);

        return "₹"
                + numberFormat.format(
                amount
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
}