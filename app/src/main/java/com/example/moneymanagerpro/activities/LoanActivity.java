package com.example.moneymanagerpro.activities;

import android.app.DatePickerDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import com.example.moneymanagerpro.utils.LoanReminderScheduler;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.database.AppDatabase;
import com.example.moneymanagerpro.database.DatabaseClient;
import com.example.moneymanagerpro.model.Account;
import com.example.moneymanagerpro.model.Loan;
import com.example.moneymanagerpro.model.LoanPayment;
import com.example.moneymanagerpro.model.Transaction;
import com.example.moneymanagerpro.utils.BubbleTouchAnimator;
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
        LoanReminderScheduler.schedule(getApplicationContext());
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadLoans();
    }

    private void bindViews() {
        inputPersonName = findViewById(R.id.inputPersonName);
        inputTotalAmount = findViewById(R.id.inputTotalAmount);
        inputHistoricalPaid = findViewById(R.id.inputHistoricalPaid);
        inputHistoricalInstallments = findViewById(R.id.inputHistoricalInstallments);
        inputTenureMonths = findViewById(R.id.inputTenureMonths);
        inputInterestRate = findViewById(R.id.inputInterestRate);
        inputEmiAmount = findViewById(R.id.inputEmiAmount);

        etPersonName = findViewById(R.id.etPersonName);
        etTotalAmount = findViewById(R.id.etTotalAmount);
        etHistoricalPaid = findViewById(R.id.etHistoricalPaid);
        etHistoricalInstallments = findViewById(R.id.etHistoricalInstallments);
        etTenureMonths = findViewById(R.id.etTenureMonths);
        etInterestRate = findViewById(R.id.etInterestRate);
        etEmiAmount = findViewById(R.id.etEmiAmount);
        etStartDate = findViewById(R.id.etStartDate);
        etDueDate = findViewById(R.id.etDueDate);
        etLoanNote = findViewById(R.id.etLoanNote);

        dropdownLoanType = findViewById(R.id.dropdownLoanType);
        btnSaveLoan = findViewById(R.id.btnSaveLoan);
        loanContainer = findViewById(R.id.loanContainer);
        txtEmptyLoans = findViewById(R.id.txtEmptyLoans);

        TextView btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());
    }

    private void prepareForm() {
        startCalendar = Calendar.getInstance();
        dueCalendar = Calendar.getInstance();
        dueCalendar.add(Calendar.MONTH, 1);

        updateDateFields();

        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                loanTypes
        );

        dropdownLoanType.setAdapter(typeAdapter);
        dropdownLoanType.setText("Loan Taken", false);

        etStartDate.setOnClickListener(v -> showDatePicker(true));
        etDueDate.setOnClickListener(v -> showDatePicker(false));

        BubbleTouchAnimator.apply(btnSaveLoan);
        btnSaveLoan.setOnClickListener(v -> saveLoan());
    }

    private void showDatePicker(boolean isStartDate) {
        Calendar dateCalendar = isStartDate ? startCalendar : dueCalendar;

        DatePickerDialog dialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    dateCalendar.set(Calendar.YEAR, year);
                    dateCalendar.set(Calendar.MONTH, month);
                    dateCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                    updateDateFields();
                },
                dateCalendar.get(Calendar.YEAR),
                dateCalendar.get(Calendar.MONTH),
                dateCalendar.get(Calendar.DAY_OF_MONTH)
        );

        dialog.show();
    }

    private void updateDateFields() {
        selectedStartDate = formatStorageDate(startCalendar);
        selectedDueDate = formatStorageDate(dueCalendar);

        etStartDate.setText(formatVisibleDate(startCalendar));
        etDueDate.setText(formatVisibleDate(dueCalendar));
    }

    private void saveLoan() {
        clearFormErrors();

        String personName = getText(etPersonName);
        String totalText = getText(etTotalAmount);
        String historicalText = getText(etHistoricalPaid);
        String installmentText = getText(etHistoricalInstallments);
        String tenureText = getText(etTenureMonths);
        String interestText = getText(etInterestRate);
        String emiText = getText(etEmiAmount);

        if (personName.isEmpty()) {
            inputPersonName.setError("Enter bank or person name");
            return;
        }

        double totalAmount = parseAmount(totalText);
        if (totalAmount <= 0) {
            inputTotalAmount.setError("Enter a valid total repayable amount");
            return;
        }

        double historicalPaid = historicalText.isEmpty()
                ? 0
                : parseAmount(historicalText);

        if (historicalPaid < 0 || historicalPaid > totalAmount) {
            inputHistoricalPaid.setError("Amount must be between 0 and total amount");
            return;
        }

        int historicalInstallments = parseInteger(installmentText);
        int tenureMonths = parseInteger(tenureText);

        if (historicalInstallments < 0) {
            inputHistoricalInstallments.setError("Enter a valid installment count");
            return;
        }

        if (tenureMonths < 0) {
            inputTenureMonths.setError("Enter a valid tenure");
            return;
        }

        if (tenureMonths > 0 && historicalInstallments > tenureMonths) {
            inputHistoricalInstallments.setError("Cannot be more than total tenure");
            return;
        }

        double interestRate = interestText.isEmpty() ? 0 : parseAmount(interestText);
        double emiAmount = emiText.isEmpty() ? 0 : parseAmount(emiText);

        if (interestRate < 0) {
            inputInterestRate.setError("Enter a valid interest rate");
            return;
        }

        if (emiAmount < 0) {
            inputEmiAmount.setError("Enter a valid EMI amount");
            return;
        }

        Loan loan = new Loan();
        loan.setPersonName(personName);
        loan.setLoanType(getText(dropdownLoanType));
        loan.setTotalAmount(totalAmount);
        loan.setHistoricalPaidAmount(historicalPaid);
        loan.setHistoricalInstallments(historicalInstallments);
        loan.setOutstandingAmount(totalAmount - historicalPaid);
        loan.setInterestRate(interestRate);
        loan.setEmiAmount(emiAmount);
        loan.setStartDate(selectedStartDate);
        loan.setTenureMonths(tenureMonths);
        loan.setDueDate(selectedDueDate);
        loan.setNote(getText(etLoanNote));
        loan.setActive(totalAmount - historicalPaid > 0);

        btnSaveLoan.setEnabled(false);
        btnSaveLoan.setText("Saving Loan...");

        new Thread(() -> {
            DatabaseClient.getInstance(getApplicationContext())
                    .getAppDatabase()
                    .loanDao()
                    .insert(loan);

            runOnUiThread(() -> {
                resetForm();
                btnSaveLoan.setEnabled(true);
                btnSaveLoan.setText("Save Smart Loan");
                LoanReminderScheduler.schedule(getApplicationContext());

                Toast.makeText(
                        LoanActivity.this,
                        "Smart loan tracker created",
                        Toast.LENGTH_SHORT
                ).show();

                loadLoans();
            });
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

        dropdownLoanType.setText("Loan Taken", false);

        startCalendar = Calendar.getInstance();
        dueCalendar = Calendar.getInstance();
        dueCalendar.add(Calendar.MONTH, 1);
        updateDateFields();
    }

    private void loadLoans() {
        new Thread(() -> {
            List<Loan> loans = DatabaseClient
                    .getInstance(getApplicationContext())
                    .getAppDatabase()
                    .loanDao()
                    .getAllLoans();

            runOnUiThread(() -> showLoans(loans));
        }).start();
    }

    private void showLoans(List<Loan> loans) {
        loanContainer.removeAllViews();

        txtEmptyLoans.setVisibility(
                loans.isEmpty() ? View.VISIBLE : View.GONE
        );

        for (Loan loan : loans) {
            addLoanCard(loan);
        }
    }

    private void addLoanCard(Loan loan) {
        boolean loanGiven = loan.getLoanType().equalsIgnoreCase("Loan Given");

        int primaryColor = loanGiven
                ? Color.parseColor("#2E7D32")
                : Color.parseColor("#B91C1C");

        String remainingLabel = loanGiven ? "Amount To Receive" : "Outstanding Amount";
        String completedLabel = loanGiven ? "Recovered" : "Paid";

        MaterialCardView card = new MaterialCardView(this);
        card.setCardBackgroundColor(Color.WHITE);
        card.setRadius(dpToPx(22));
        card.setCardElevation(dpToPx(5));
        card.setStrokeWidth(dpToPx(1));
        card.setStrokeColor(Color.parseColor("#E2E8F0"));

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, 0, 0, dpToPx(14));
        card.setLayoutParams(cardParams);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dpToPx(16), dpToPx(18), dpToPx(16), dpToPx(16));

        TextView title = createText(
                loan.getPersonName(),
                20,
                Color.parseColor("#172033"),
                true
        );
        title.setGravity(Gravity.CENTER);

        TextView type = createText(
                loan.getLoanType() + (loan.isActive() ? " | Active" : " | Closed"),
                13,
                primaryColor,
                true
        );
        type.setGravity(Gravity.CENTER);

        TextView outstanding = createText(
                remainingLabel + "\n" + formatAmount(loan.getOutstandingAmount()),
                21,
                primaryColor,
                true
        );
        outstanding.setGravity(Gravity.CENTER);

        int progress = calculateProgress(
                loan.getTotalAmount() - loan.getOutstandingAmount(),
                loan.getTotalAmount()
        );

        ProgressBar progressBar = new ProgressBar(
                this,
                null,
                android.R.attr.progressBarStyleHorizontal
        );
        progressBar.setMax(100);
        progressBar.setProgress(progress);
        progressBar.setProgressTintList(
                ColorStateList.valueOf(primaryColor)
        );

        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(10)
        );
        progressParams.setMargins(0, dpToPx(14), 0, 0);
        progressBar.setLayoutParams(progressParams);

        TextView progressText = createText(
                progress + "% " + completedLabel,
                14,
                primaryColor,
                true
        );
        progressText.setGravity(Gravity.CENTER);

        String historyInfo = "Previously paid: "
                + formatAmount(loan.getHistoricalPaidAmount())
                + " | Previous EMIs: "
                + loan.getHistoricalInstallments();

        TextView previousInfo = createText(
                historyInfo,
                13,
                Color.parseColor("#64748B"),
                false
        );
        previousInfo.setGravity(Gravity.CENTER);

        addInfoLine(
                content,
                "Total repayable",
                formatAmount(loan.getTotalAmount())
        );

        addInfoLine(
                content,
                "Monthly EMI",
                loan.getEmiAmount() > 0
                        ? formatAmount(loan.getEmiAmount())
                        : "Not added"
        );

        addInfoLine(
                content,
                "Remaining EMIs",
                getRemainingEmiText(loan)
        );

        addInfoLine(
                content,
                "Next due",
                getDueText(loan.getDueDate())
        );

        addInfoLine(
                content,
                "Expected closure",
                getExpectedClosureText(loan)
        );

        TextView noteText = createText(
                loan.getNote().isEmpty() ? "" : "Note: " + loan.getNote(),
                13,
                Color.parseColor("#64748B"),
                false
        );
        noteText.setGravity(Gravity.CENTER);

        LinearLayout firstActionRow = createActionRow();
        LinearLayout secondActionRow = createActionRow();

        MaterialButton btnEmi = createActionButton(
                loanGiven ? "Record Receipt" : "Pay EMI",
                primaryColor
        );

        MaterialButton btnExtra = createActionButton(
                loanGiven ? "Extra Receipt" : "Extra Payment",
                Color.parseColor("#7C3AED")
        );

        MaterialButton btnHistory = createActionButton(
                "History",
                Color.parseColor("#1565C0")
        );

        MaterialButton btnArchive = createActionButton(
                loan.isActive() ? "Archive" : "Delete View",
                Color.parseColor("#475569")
        );

        btnEmi.setEnabled(loan.isActive());
        btnExtra.setEnabled(loan.isActive());

        btnEmi.setOnClickListener(v -> showPaymentDialog(loan, "EMI"));
        btnExtra.setOnClickListener(v -> showPaymentDialog(loan, "EXTRA"));
        btnHistory.setOnClickListener(v -> showPaymentHistory(loan));
        btnArchive.setOnClickListener(v -> archiveLoan(loan));

        addButtonToRow(firstActionRow, btnEmi);
        addButtonToRow(firstActionRow, btnExtra);
        addButtonToRow(secondActionRow, btnHistory);
        addButtonToRow(secondActionRow, btnArchive);

        BubbleTouchAnimator.apply(card);
        BubbleTouchAnimator.apply(btnEmi);
        BubbleTouchAnimator.apply(btnExtra);
        BubbleTouchAnimator.apply(btnHistory);
        BubbleTouchAnimator.apply(btnArchive);

        content.addView(title);
        content.addView(type);
        content.addView(outstanding);
        content.addView(progressBar);
        content.addView(progressText);
        content.addView(previousInfo);
        content.addView(createDivider());
        content.addView(noteText);
        content.addView(firstActionRow);
        content.addView(secondActionRow);

        card.addView(content);
        loanContainer.addView(card);
    }

    private void addInfoLine(
            LinearLayout parent,
            String label,
            String value
    ) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        rowParams.setMargins(0, dpToPx(5), 0, 0);
        row.setLayoutParams(rowParams);

        TextView labelView = createText(
                label,
                13,
                Color.parseColor("#64748B"),
                false
        );

        TextView valueView = createText(
                value,
                13,
                Color.parseColor("#172033"),
                true
        );
        valueView.setGravity(Gravity.END);

        labelView.setLayoutParams(new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1
        ));

        valueView.setLayoutParams(new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1
        ));

        row.addView(labelView);
        row.addView(valueView);
        parent.addView(row);
    }

    private LinearLayout createActionRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(46)
        );
        params.setMargins(0, dpToPx(12), 0, 0);
        row.setLayoutParams(params);

        return row;
    }

    private void addButtonToRow(
            LinearLayout row,
            MaterialButton button
    ) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1
        );
        params.setMargins(dpToPx(4), 0, dpToPx(4), 0);
        button.setLayoutParams(params);

        row.addView(button);
    }

    private MaterialButton createActionButton(
            String text,
            int color
    ) {
        MaterialButton button = new MaterialButton(this);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(12);
        button.setAllCaps(false);
        button.setCornerRadius(dpToPx(20));
        button.setPadding(dpToPx(2), 0, dpToPx(2), 0);
        button.setBackgroundTintList(ColorStateList.valueOf(color));

        return button;
    }

    private View createDivider() {
        View divider = new View(this);
        divider.setBackgroundColor(Color.parseColor("#E2E8F0"));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(1)
        );
        params.setMargins(0, dpToPx(12), 0, dpToPx(6));
        divider.setLayoutParams(params);

        return divider;
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
            textView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        }

        return textView;
    }

    private void showPaymentDialog(
            Loan loan,
            String paymentType
    ) {
        new Thread(() -> {
            List<Account> accounts = DatabaseClient
                    .getInstance(getApplicationContext())
                    .getAppDatabase()
                    .accountDao()
                    .getAllAccounts();

            List<String> accountNames = new ArrayList<>();

            for (Account account : accounts) {
                accountNames.add(account.getName());
            }

            if (accountNames.isEmpty()) {
                accountNames.add("Cash");
            }

            runOnUiThread(() ->
                    createPaymentDialog(loan, paymentType, accountNames)
            );
        }).start();
    }

    private void createPaymentDialog(
            Loan loan,
            String paymentType,
            List<String> accountNames
    ) {
        boolean extraPayment = paymentType.equals("EXTRA");
        boolean loanGiven = loan.getLoanType().equalsIgnoreCase("Loan Given");

        String actionName;

        if (loanGiven) {
            actionName = extraPayment ? "Record Extra Receipt" : "Record EMI Receipt";
        } else {
            actionName = extraPayment ? "Record Extra Payment" : "Pay EMI";
        }

        LinearLayout dialogLayout = new LinearLayout(this);
        dialogLayout.setOrientation(LinearLayout.VERTICAL);
        dialogLayout.setPadding(dpToPx(22), dpToPx(8), dpToPx(22), dpToPx(4));

        TextView title = createText(
                "Remaining: " + formatAmount(loan.getOutstandingAmount()),
                16,
                Color.parseColor("#172033"),
                true
        );
        title.setGravity(Gravity.CENTER);

        TextInputLayout amountInput = new TextInputLayout(this);
        amountInput.setHint("Amount");
        amountInput.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);

        TextInputEditText etAmount = new TextInputEditText(this);
        etAmount.setInputType(
                InputType.TYPE_CLASS_NUMBER
                        | InputType.TYPE_NUMBER_FLAG_DECIMAL
        );
        etAmount.setGravity(Gravity.CENTER);
        etAmount.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);

        if (!extraPayment && loan.getEmiAmount() > 0) {
            etAmount.setText(String.valueOf(loan.getEmiAmount()));
        }

        amountInput.addView(etAmount);

        TextInputLayout accountInput = new TextInputLayout(this);
        accountInput.setHint("Paid From Account");
        accountInput.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        accountInput.setEndIconMode(TextInputLayout.END_ICON_DROPDOWN_MENU);

        MaterialAutoCompleteTextView dropdownAccount =
                new MaterialAutoCompleteTextView(this);

        dropdownAccount.setFocusable(false);
        dropdownAccount.setInputType(0);
        dropdownAccount.setGravity(Gravity.CENTER);
        dropdownAccount.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);

        ArrayAdapter<String> accountAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                accountNames
        );

        dropdownAccount.setAdapter(accountAdapter);
        dropdownAccount.setText(accountNames.get(0), false);
        accountInput.addView(dropdownAccount);

        TextInputLayout noteInput = new TextInputLayout(this);
        noteInput.setHint("Optional note");
        noteInput.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);

        TextInputEditText etNote = new TextInputEditText(this);
        etNote.setGravity(Gravity.CENTER);
        etNote.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        noteInput.addView(etNote);

        dialogLayout.addView(title);
        dialogLayout.addView(amountInput);
        dialogLayout.addView(accountInput);
        dialogLayout.addView(noteInput);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(actionName)
                .setView(dialogLayout)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create();

        dialog.setOnShowListener(listener -> {
            Button saveButton = dialog.getButton(
                    AlertDialog.BUTTON_POSITIVE
            );

            saveButton.setOnClickListener(v -> {
                double amount = parseAmount(getText(etAmount));

                if (amount <= 0) {
                    amountInput.setError("Enter a valid amount");
                    return;
                }

                if (amount > loan.getOutstandingAmount()) {
                    amountInput.setError("Amount cannot be more than remaining loan");
                    return;
                }

                amountInput.setError(null);

                saveLoanPayment(
                        loan,
                        paymentType,
                        amount,
                        getText(dropdownAccount),
                        getText(etNote),
                        dialog
                );
            });
        });

        dialog.show();
    }

    private void saveLoanPayment(
            Loan loan,
            String paymentType,
            double paymentAmount,
            String accountName,
            String paymentNote,
            AlertDialog dialog
    ) {
        boolean loanGiven = loan.getLoanType().equalsIgnoreCase("Loan Given");

        Transaction transaction = new Transaction();
        transaction.setType(loanGiven ? "INCOME" : "EXPENSE");
        transaction.setAmount(paymentAmount);
        transaction.setCategory(
                paymentType.equals("EXTRA")
                        ? "Loan Extra Payment"
                        : "Loan EMI"
        );
        transaction.setAccount(accountName);
        transaction.setNote(
                (paymentType.equals("EXTRA")
                        ? "Extra payment"
                        : "EMI payment")
                        + " - " + loan.getPersonName()
                        + (paymentNote.isEmpty() ? "" : " - " + paymentNote)
        );
        transaction.setDate(
                new SimpleDateFormat(
                        "yyyy-MM-dd HH:mm",
                        Locale.US
                ).format(Calendar.getInstance().getTime())
        );

        LoanPayment loanPayment = new LoanPayment();
        loanPayment.setLoanId(loan.getId());
        loanPayment.setAmount(paymentAmount);
        loanPayment.setPaymentType(paymentType);
        loanPayment.setAccount(accountName);
        loanPayment.setPaymentDate(transaction.getDate());
        loanPayment.setNote(paymentNote);

        double remainingAmount = loan.getOutstandingAmount() - paymentAmount;
        loan.setOutstandingAmount(Math.max(remainingAmount, 0));

        if (paymentType.equals("EMI") && remainingAmount > 0) {
            loan.setDueDate(addOneMonth(loan.getDueDate()));
        }

        if (remainingAmount <= 0) {
            loan.setActive(false);
        }

        new Thread(() -> {
            AppDatabase database = DatabaseClient
                    .getInstance(getApplicationContext())
                    .getAppDatabase();

            database.runInTransaction(() -> {
                database.transactionDao().insert(transaction);
                database.loanPaymentDao().insert(loanPayment);
                database.loanDao().update(loan);
            });

            runOnUiThread(() -> {
                dialog.dismiss();
                LoanReminderScheduler.schedule(getApplicationContext());

                Toast.makeText(
                        LoanActivity.this,
                        paymentType.equals("EXTRA")
                                ? "Extra payment recorded"
                                : "EMI recorded successfully",
                        Toast.LENGTH_SHORT
                ).show();

                loadLoans();
            });
        }).start();
    }

    private void showPaymentHistory(Loan loan) {
        new Thread(() -> {
            List<LoanPayment> payments = DatabaseClient
                    .getInstance(getApplicationContext())
                    .getAppDatabase()
                    .loanPaymentDao()
                    .getPaymentsForLoan(loan.getId());

            runOnUiThread(() -> createHistoryDialog(loan, payments));
        }).start();
    }

    private void createHistoryDialog(
            Loan loan,
            List<LoanPayment> payments
    ) {
        LinearLayout historyLayout = new LinearLayout(this);
        historyLayout.setOrientation(LinearLayout.VERTICAL);
        historyLayout.setPadding(dpToPx(22), dpToPx(8), dpToPx(22), dpToPx(8));

        TextView baseline = createText(
                "Paid before app: "
                        + formatAmount(loan.getHistoricalPaidAmount())
                        + "\nPrevious installments: "
                        + loan.getHistoricalInstallments(),
                14,
                Color.parseColor("#475569"),
                false
        );
        baseline.setGravity(Gravity.CENTER);
        historyLayout.addView(baseline);

        if (payments.isEmpty()) {
            TextView empty = createText(
                    "\nNo EMI or extra payment has been recorded in the app yet.",
                    14,
                    Color.parseColor("#64748B"),
                    false
            );
            empty.setGravity(Gravity.CENTER);
            historyLayout.addView(empty);
        } else {
            for (LoanPayment payment : payments) {
                TextView row = createText(
                        "\n"
                                + payment.getPaymentType()
                                + " | "
                                + formatAmount(payment.getAmount())
                                + "\n"
                                + formatPaymentDate(payment.getPaymentDate())
                                + " | "
                                + payment.getAccount()
                                + (payment.getNote().isEmpty()
                                ? ""
                                : "\n" + payment.getNote()),
                        14,
                        Color.parseColor("#172033"),
                        false
                );

                historyLayout.addView(row);
            }
        }

        new AlertDialog.Builder(this)
                .setTitle("Loan Payment History")
                .setView(historyLayout)
                .setPositiveButton("Close", null)
                .show();
    }

    private void archiveLoan(Loan loan) {
        String message = loan.isActive()
                ? "Archive this loan? No transaction or payment history will be deleted."
                : "Hide this closed loan? Its saved history will remain safe.";

        new AlertDialog.Builder(this)
                .setTitle("Archive Loan")
                .setMessage(message)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Archive", (dialog, which) -> {
                    loan.setActive(false);

                    new Thread(() -> {
                        DatabaseClient.getInstance(getApplicationContext())
                                .getAppDatabase()
                                .loanDao()
                                .update(loan);

                        runOnUiThread(() -> {
                            Toast.makeText(
                                    LoanActivity.this,
                                    "Loan archived safely",
                                    Toast.LENGTH_SHORT
                            ).show();

                            loadLoans();
                        });
                    }).start();
                })
                .show();
    }

    private int calculateProgress(
            double paidAmount,
            double totalAmount
    ) {
        if (totalAmount <= 0) {
            return 0;
        }

        int progress = (int) Math.round(
                (paidAmount / totalAmount) * 100
        );

        return Math.min(Math.max(progress, 0), 100);
    }

    private String getRemainingEmiText(Loan loan) {
        if (loan.getOutstandingAmount() <= 0) {
            return "Completed";
        }

        if (loan.getEmiAmount() <= 0) {
            return "Add EMI amount";
        }

        int remaining = (int) Math.ceil(
                loan.getOutstandingAmount() / loan.getEmiAmount()
        );

        return remaining + " months";
    }

    private String getExpectedClosureText(Loan loan) {
        if (loan.getOutstandingAmount() <= 0) {
            return "Completed";
        }

        if (loan.getEmiAmount() <= 0) {
            return "Add EMI amount";
        }

        int remainingEmis = (int) Math.ceil(
                loan.getOutstandingAmount() / loan.getEmiAmount()
        );

        Calendar calendar = parseStorageDate(loan.getDueDate());

        if (calendar == null) {
            return "Not available";
        }

        calendar.add(Calendar.MONTH, Math.max(remainingEmis - 1, 0));

        return formatVisibleDate(calendar);
    }

    private String getDueText(String dueDate) {
        Calendar dueCalendar = parseStorageDate(dueDate);

        if (dueCalendar == null) {
            return "Not added";
        }

        Calendar today = Calendar.getInstance();
        clearTime(today);
        clearTime(dueCalendar);

        long difference = dueCalendar.getTimeInMillis()
                - today.getTimeInMillis();

        long days = difference / (24 * 60 * 60 * 1000);

        if (days < 0) {
            return "Overdue by " + Math.abs(days) + " day(s)";
        }

        if (days == 0) {
            return "Due today";
        }

        return formatVisibleDate(dueCalendar)
                + " | " + days + " day(s) left";
    }

    private String addOneMonth(String dueDate) {
        Calendar calendar = parseStorageDate(dueDate);

        if (calendar == null) {
            calendar = Calendar.getInstance();
        }

        calendar.add(Calendar.MONTH, 1);

        return formatStorageDate(calendar);
    }

    private Calendar parseStorageDate(String value) {
        try {
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(
                    new SimpleDateFormat(
                            "yyyy-MM-dd",
                            Locale.US
                    ).parse(value)
            );
            return calendar;
        } catch (Exception exception) {
            return null;
        }
    }

    private String formatPaymentDate(String value) {
        try {
            return new SimpleDateFormat(
                    "dd MMM yyyy, hh:mm a",
                    Locale.ENGLISH
            ).format(
                    new SimpleDateFormat(
                            "yyyy-MM-dd HH:mm",
                            Locale.US
                    ).parse(value)
            );
        } catch (Exception exception) {
            return value;
        }
    }

    private String formatStorageDate(Calendar calendar) {
        return new SimpleDateFormat(
                "yyyy-MM-dd",
                Locale.US
        ).format(calendar.getTime());
    }

    private String formatVisibleDate(Calendar calendar) {
        return new SimpleDateFormat(
                "dd MMM yyyy",
                Locale.ENGLISH
        ).format(calendar.getTime());
    }

    private void clearTime(Calendar calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
    }

    private String getText(TextView view) {
        return view.getText() == null
                ? ""
                : view.getText().toString().trim();
    }

    private double parseAmount(String value) {
        try {
            return Double.parseDouble(value);
        } catch (Exception exception) {
            return 0;
        }
    }

    private int parseInteger(String value) {
        try {
            return Integer.parseInt(value);
        } catch (Exception exception) {
            return 0;
        }
    }

    private String formatAmount(double amount) {
        NumberFormat numberFormat = NumberFormat.getCurrencyInstance(
                new Locale("en", "IN")
        );

        return numberFormat.format(amount);
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return (int) (dp * density);
    }
}