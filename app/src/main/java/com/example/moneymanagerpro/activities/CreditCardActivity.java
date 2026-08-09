package com.example.moneymanagerpro.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.credit.CreditCardCycleCalculator;
import com.example.moneymanagerpro.credit.CreditCardTransactionMatcher;
import com.example.moneymanagerpro.database.AppDatabase;
import com.example.moneymanagerpro.database.DatabaseClient;
import com.example.moneymanagerpro.model.Account;
import com.example.moneymanagerpro.model.AccountBalance;
import com.example.moneymanagerpro.model.CreditCard;
import com.example.moneymanagerpro.model.CreditCardPayment;
import com.example.moneymanagerpro.model.Transaction;
import com.example.moneymanagerpro.utils.ReminderScheduler;
import com.example.moneymanagerpro.utils.ThemeManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CreditCardActivity extends AppCompatActivity {

    private TextInputLayout inputCardName;
    private TextInputLayout inputLastFour;
    private TextInputLayout inputCreditLimit;
    private TextInputEditText etCardName;
    private TextInputEditText etLastFour;
    private TextInputEditText etCreditLimit;
    private MaterialAutoCompleteTextView dropdownBillingDay;
    private MaterialAutoCompleteTextView dropdownDueDay;
    private MaterialAutoCompleteTextView dropdownPaymentAccount;
    private MaterialAutoCompleteTextView dropdownReminderDays;
    private MaterialButton btnSaveCard;
    private MaterialButton btnCancelEdit;
    private TextView txtFormTitle;
    private TextView txtEmptyCards;
    private LinearLayout cardContainer;
    private NestedScrollView creditCardScroll;

    private CreditCard editingCard;
    private List<String> paymentAccounts =
            new ArrayList<>();

    private final ActivityResultLauncher<String>
            notificationPermissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts
                            .RequestPermission(),
                    granted -> {
                        if (!granted) {
                            showMessage(
                                    "Due reminders remain off until notification permission is allowed"
                            );
                        }
                    }
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applySavedTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_credit_card);

        bindViews();
        setupStaticDropdowns();
        setupActions();
        findViewById(R.id.btnCreditCardDataCenter).setOnClickListener(view ->
                startActivity(new Intent(this, AdvancedFinanceDataActivity.class))
        );
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadData();
    }

    private void bindViews() {
        inputCardName = findViewById(R.id.inputCreditCardName);
        inputLastFour = findViewById(R.id.inputCreditCardLastFour);
        inputCreditLimit = findViewById(R.id.inputCreditLimit);
        etCardName = findViewById(R.id.etCreditCardName);
        etLastFour = findViewById(R.id.etCreditCardLastFour);
        etCreditLimit = findViewById(R.id.etCreditLimit);
        dropdownBillingDay = findViewById(R.id.dropdownBillingDay);
        dropdownDueDay = findViewById(R.id.dropdownDueDay);
        dropdownPaymentAccount =
                findViewById(R.id.dropdownCardPaymentAccount);
        dropdownReminderDays =
                findViewById(R.id.dropdownCardReminderDays);
        btnSaveCard = findViewById(R.id.btnSaveCreditCard);
        btnCancelEdit = findViewById(R.id.btnCancelCardEdit);
        txtFormTitle = findViewById(R.id.txtCardFormTitle);
        txtEmptyCards = findViewById(R.id.txtEmptyCreditCards);
        cardContainer = findViewById(R.id.creditCardContainer);
        creditCardScroll = findViewById(R.id.creditCardScroll);
    }

    private void setupStaticDropdowns() {
        List<String> days = new ArrayList<>();

        for (int day = 1; day <= 31; day++) {
            days.add(String.valueOf(day));
        }

        ArrayAdapter<String> dayAdapter =
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_list_item_1,
                        days
                );

        dropdownBillingDay.setAdapter(dayAdapter);
        dropdownDueDay.setAdapter(dayAdapter);
        dropdownBillingDay.setText("15", false);
        dropdownDueDay.setText("5", false);

        List<String> reminders = new ArrayList<>();
        reminders.add("On due date");
        reminders.add("1 day before");
        reminders.add("2 days before");
        reminders.add("3 days before");
        reminders.add("5 days before");
        reminders.add("7 days before");

        dropdownReminderDays.setAdapter(
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_list_item_1,
                        reminders
                )
        );
        dropdownReminderDays.setText(
                "3 days before",
                false
        );
    }

    private void setupActions() {
        findViewById(R.id.btnCreditCardBack)
                .setOnClickListener(view -> finish());
        btnSaveCard.setOnClickListener(
                view -> saveCard()
        );
        btnCancelEdit.setOnClickListener(
                view -> resetForm()
        );
    }

    private void loadData() {
        new Thread(() -> {
            AppDatabase database = getDatabase();

            List<Account> accounts =
                    database.accountDao()
                            .getAllAccounts();

            List<CreditCard> cards =
                    database.creditCardDao()
                            .getActiveCreditCards();

            List<AccountBalance> balances =
                    database.accountDao()
                            .getAccountBalances();

            Map<String, Double> balanceByAccount =
                    new HashMap<>();

            for (AccountBalance balance : balances) {
                balanceByAccount.put(
                        safe(balance.name),
                        balance.currentBalance
                );
            }

            List<String> availablePaymentAccounts =
                    new ArrayList<>();
            List<String> availableTransactionAccounts =
                    new ArrayList<>();

            for (Account account : accounts) {
                availableTransactionAccounts.add(
                        safe(account.getName())
                );

                if (!"Credit Card".equalsIgnoreCase(
                        safe(account.getType())
                )) {
                    availablePaymentAccounts.add(
                            safe(account.getName())
                    );
                }
            }

            if (availablePaymentAccounts.isEmpty()) {
                availablePaymentAccounts.add("Cash");
            }

            List<CardViewData> viewData =
                    new ArrayList<>();

            for (CreditCard card : cards) {
                List<String> transactionAccounts =
                        CreditCardTransactionMatcher
                                .findAccountAliases(
                                        card.getName(),
                                        card.getLastFour(),
                                        card.getAccountName(),
                                        availableTransactionAccounts
                                );

                CreditCardCycleCalculator.Cycle cycle =
                        CreditCardCycleCalculator.calculate(
                                card,
                                Calendar.getInstance()
                        );

                double statementAmount =
                        getCardSpend(
                                database,
                                card,
                                transactionAccounts,
                                cycle.closedStart,
                                cycle.closedEnd
                        );

                double paidAmount =
                        database.creditCardPaymentDao()
                                .getPaidForStatement(
                                        card.getId(),
                                        cycle.closedEnd
                                );

                double outstanding =
                        Math.max(
                                0,
                                statementAmount - paidAmount
                        );

                double currentSpend =
                        Math.max(
                                0,
                                getCardSpend(
                                        database,
                                        card,
                                        transactionAccounts,
                                        cycle.currentStart,
                                        cycle.currentEnd
                                )
                        );

                double accountBalance = 0;

                for (String accountName :
                        transactionAccounts) {
                    accountBalance +=
                            balanceByAccount.getOrDefault(
                                    accountName,
                                    0.0
                            );
                }

                double categorySpendOutsideAccounts =
                        database.transactionDao()
                                .getNetCardCategorySpendOutsideAccounts(
                                        transactionAccounts,
                                        card.getName()
                                );

                double totalUsed =
                        Math.max(
                                0,
                                -accountBalance
                                        + categorySpendOutsideAccounts
                        );

                int syncedTransactionCount =
                        database.transactionDao()
                                .getCardTransactionCountFromSources(
                                        transactionAccounts,
                                        card.getName()
                                );

                viewData.add(
                        new CardViewData(
                                card,
                                cycle,
                                statementAmount,
                                paidAmount,
                                outstanding,
                                currentSpend,
                                totalUsed,
                                syncedTransactionCount
                        )
                );
            }

            runOnUiThread(() -> {
                paymentAccounts =
                        availablePaymentAccounts;
                bindPaymentAccounts();
                renderCards(viewData);
            });
        }).start();
    }

    private void bindPaymentAccounts() {
        dropdownPaymentAccount.setAdapter(
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_list_item_1,
                        paymentAccounts
                )
        );

        if (editingCard == null
                && dropdownPaymentAccount
                .getText().toString().trim().isEmpty()) {

            dropdownPaymentAccount.setText(
                    preferredPaymentAccount(),
                    false
            );
        }
    }

    private String preferredPaymentAccount() {
        for (String account : paymentAccounts) {
            if ("Cash".equalsIgnoreCase(account)) {
                return account;
            }
        }

        return paymentAccounts.get(0);
    }

    private void saveCard() {
        clearErrors();

        String name = value(etCardName);
        String lastFour = value(etLastFour);
        String limitText = value(etCreditLimit);
        String paymentAccount =
                dropdownPaymentAccount
                        .getText().toString().trim();

        if (name.length() < 2) {
            inputCardName.setError("Enter card or bank name");
            return;
        }

        if (!lastFour.matches("\\d{4}")) {
            inputLastFour.setError(
                    "Enter exactly the last 4 digits"
            );
            return;
        }

        double creditLimit;

        try {
            creditLimit = Double.parseDouble(limitText);
        } catch (Exception exception) {
            inputCreditLimit.setError(
                    "Enter a valid credit limit"
            );
            return;
        }

        if (creditLimit <= 0) {
            inputCreditLimit.setError(
                    "Credit limit must be greater than zero"
            );
            return;
        }

        int billingDay =
                parseDay(dropdownBillingDay);
        int dueDay =
                parseDay(dropdownDueDay);
        int reminderDays =
                parseReminderDays();

        if (billingDay < 1 || dueDay < 1) {
            showMessage(
                    "Select valid billing and due days"
            );
            return;
        }

        if (paymentAccount.isEmpty()
                || !paymentAccounts.contains(
                paymentAccount
        )) {
            showMessage(
                    "Select a valid payment account"
            );
            return;
        }

        btnSaveCard.setEnabled(false);
        btnCancelEdit.setEnabled(false);

        CreditCard cardBeingEdited =
                editingCard;

        new Thread(() -> {
            try {
                AppDatabase database =
                        getDatabase();

                if (cardBeingEdited == null) {
                    CreditCard card =
                            new CreditCard();
                    String accountName =
                            buildCardAccountName(
                                    name,
                                    lastFour
                            );

                    if (database.creditCardDao()
                            .findByAccountName(
                                    accountName
                            ) != null) {
                        throw new IllegalStateException(
                                "This card already exists"
                        );
                    }

                    card.setName(name);
                    card.setLastFour(lastFour);
                    card.setAccountName(accountName);
                    card.setCreditLimit(creditLimit);
                    card.setBillingDay(billingDay);
                    card.setDueDay(dueDay);
                    card.setPaymentAccount(paymentAccount);
                    card.setReminderDays(reminderDays);
                    card.setActive(true);

                    database.runInTransaction(() -> {
                        Account existingAccount =
                                database.accountDao()
                                        .findByName(
                                                accountName
                                        );

                        if (existingAccount == null) {

                            Account account =
                                    new Account();
                            account.setName(accountName);
                            account.setType("Credit Card");
                            account.setOpeningBalance(0);
                            account.setColor("#6C63FF");

                            database.accountDao()
                                    .insert(account);

                        } else if (!"Credit Card"
                                .equalsIgnoreCase(
                                        safe(
                                                existingAccount
                                                        .getType()
                                        )
                                )) {

                            existingAccount.setType(
                                    "Credit Card"
                            );
                            existingAccount.setColor(
                                    "#6C63FF"
                            );
                            database.accountDao()
                                    .update(
                                            existingAccount
                                    );
                        }

                        database.creditCardDao()
                                .insert(card);
                    });

                } else {
                    cardBeingEdited.setCreditLimit(
                            creditLimit
                    );
                    cardBeingEdited.setBillingDay(
                            billingDay
                    );
                    cardBeingEdited.setDueDay(dueDay);
                    cardBeingEdited.setPaymentAccount(
                            paymentAccount
                    );
                    cardBeingEdited.setReminderDays(
                            reminderDays
                    );

                    database.creditCardDao()
                            .update(cardBeingEdited);
                }

                ReminderScheduler.scheduleDaily(
                        getApplicationContext()
                );

                runOnUiThread(() -> {
                    showMessage(
                            cardBeingEdited == null
                                    ? "Credit card added"
                                    : "Card settings updated"
                    );
                    requestNotificationPermissionIfNeeded();
                    resetForm();
                    loadData();
                });

            } catch (Exception exception) {
                runOnUiThread(() -> {
                    btnSaveCard.setEnabled(true);
                    btnCancelEdit.setEnabled(true);
                    showMessage(
                            safe(exception.getMessage())
                                    .isEmpty()
                                    ? "Unable to save credit card"
                                    : exception.getMessage()
                    );
                });
            }
        }).start();
    }

    private void renderCards(
            List<CardViewData> cards
    ) {
        cardContainer.removeAllViews();
        txtEmptyCards.setVisibility(
                cards.isEmpty()
                        ? View.VISIBLE
                        : View.GONE
        );

        for (CardViewData data : cards) {
            cardContainer.addView(
                    createCardView(data)
            );
        }
    }

    private View createCardView(
            CardViewData data
    ) {
        MaterialCardView cardView =
                new MaterialCardView(this);
        cardView.setRadius(dp(20));
        cardView.setCardElevation(dp(1));
        cardView.setCardBackgroundColor(
                color(R.color.app_surface)
        );
        cardView.setStrokeColor(
                color(R.color.app_outline)
        );
        cardView.setStrokeWidth(dp(1));
        cardView.setClipToOutline(false);

        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        cardParams.setMargins(0, 0, 0, dp(16));
        cardView.setLayoutParams(cardParams);

        LinearLayout content =
                new LinearLayout(this);
        content.setOrientation(
                LinearLayout.VERTICAL
        );
        content.setClipChildren(false);
        content.setClipToPadding(false);
        content.setPadding(
                dp(17),
                dp(16),
                dp(17),
                dp(22)
        );

        TextView title = text(
                data.card.getName(),
                18,
                R.color.app_text_primary,
                true
        );
        content.addView(title);

        content.addView(
                text(
                        "•••• "
                                + data.card.getLastFour()
                                + "  •  Limit "
                                + money(
                                data.card.getCreditLimit()
                        ),
                        12,
                        R.color.app_text_secondary,
                        false
                )
        );

        TextView syncStatus = text(
                data.syncedTransactionCount > 0
                        ? "Auto-synced "
                        + data.syncedTransactionCount
                        + (data.syncedTransactionCount == 1
                        ? " existing transaction"
                        : " existing transactions")
                        : "Auto-sync ready for matching card account or category",
                11,
                data.syncedTransactionCount > 0
                        ? R.color.success
                        : R.color.app_text_secondary,
                data.syncedTransactionCount > 0
        );
        LinearLayout.LayoutParams syncParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        syncParams.setMargins(0, dp(8), 0, 0);
        syncStatus.setLayoutParams(syncParams);
        content.addView(syncStatus);

        TextView status = text(
                statementStatus(data),
                13,
                data.outstanding <= 0.005
                        ? R.color.success
                        : R.color.expense,
                true
        );
        LinearLayout.LayoutParams statusParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        statusParams.setMargins(0, dp(12), 0, 0);
        status.setLayoutParams(statusParams);
        content.addView(status);

        content.addView(
                text(
                        "Statement "
                                + visibleDate(
                                data.cycle.closedStart
                        )
                                + " – "
                                + visibleDate(
                                data.cycle.closedEnd
                        )
                                + "\nDue "
                                + visibleDate(
                                data.cycle.dueDate
                        ),
                        12,
                        R.color.app_text_secondary,
                        false
                )
        );

        content.addView(
                text(
                        "Statement: "
                                + money(data.statementAmount)
                                + "   Paid: "
                                + money(data.paidAmount)
                                + "\nCurrent unbilled: "
                                + money(data.currentSpend)
                                + "   Available: "
                                + money(
                                Math.max(
                                        0,
                                        data.card.getCreditLimit()
                                                - data.totalUsed
                                )
                        ),
                        14,
                        R.color.app_text_primary,
                        true
                )
        );

        LinearLayout actions =
                new LinearLayout(this);
        actions.setOrientation(
                LinearLayout.HORIZONTAL
        );
        actions.setGravity(Gravity.CENTER);
        actions.setClipChildren(false);
        actions.setClipToPadding(false);
        actions.setPadding(0, 0, 0, dp(2));

        MaterialButton statementButton =
                actionButton("Statements");
        MaterialButton settingsButton =
                actionButton("Edit Settings");

        LinearLayout.LayoutParams firstParams =
                new LinearLayout.LayoutParams(
                        0,
                        dp(56),
                        1
                );
        firstParams.setMargins(0, dp(14), dp(4), 0);
        statementButton.setLayoutParams(firstParams);

        LinearLayout.LayoutParams secondParams =
                new LinearLayout.LayoutParams(
                        0,
                        dp(56),
                        1
                );
        secondParams.setMargins(dp(4), dp(14), 0, 0);
        settingsButton.setLayoutParams(secondParams);

        statementButton.setOnClickListener(
                view -> showStatementSummary(data)
        );
        settingsButton.setOnClickListener(
                view -> startEditing(data.card)
        );

        actions.addView(statementButton);
        actions.addView(settingsButton);
        content.addView(actions);
        cardView.addView(content);
        return cardView;
    }

    private void showStatementSummary(
            CardViewData data
    ) {
        String message =
                "Statement cycle\n"
                        + visibleDate(data.cycle.closedStart)
                        + " – "
                        + visibleDate(data.cycle.closedEnd)
                        + "\n\nPayment due: "
                        + visibleDate(data.cycle.dueDate)
                        + "\nStatement amount: "
                        + money(data.statementAmount)
                        + "\nPaid: "
                        + money(data.paidAmount)
                        + "\nOutstanding: "
                        + money(data.outstanding)
                        + "\n\nCurrent cycle\n"
                        + visibleDate(data.cycle.currentStart)
                        + " – "
                        + visibleDate(data.cycle.currentEnd)
                        + "\nUnbilled spending: "
                        + money(data.currentSpend);

        AlertDialog.Builder builder =
                new AlertDialog.Builder(this)
                        .setTitle(
                                data.card.getName()
                                        + " Statement"
                        )
                        .setMessage(message)
                        .setNegativeButton(
                                "Close",
                                null
                        )
                        .setNeutralButton(
                                "History",
                                (dialog, which) ->
                                        showStatementHistory(
                                                data.card
                                        )
                        );

        if (data.outstanding > 0.005) {
            builder.setPositiveButton(
                    "Record Payment",
                    (dialog, which) ->
                            showPaymentDialog(data)
            );
        }

        builder.show();
    }

    private void showStatementHistory(
            CreditCard card
    ) {
        new Thread(() -> {
            AppDatabase database = getDatabase();
            List<String> transactionAccounts =
                    new ArrayList<>();

            for (Account account :
                    database.accountDao()
                            .getAllAccounts()) {
                transactionAccounts.add(
                        safe(account.getName())
                );
            }

            transactionAccounts =
                    CreditCardTransactionMatcher
                            .findAccountAliases(
                                    card.getName(),
                                    card.getLastFour(),
                                    card.getAccountName(),
                                    transactionAccounts
                            );

            CreditCardCycleCalculator.Cycle current =
                    CreditCardCycleCalculator.calculate(
                            card,
                            Calendar.getInstance()
                    );

            Calendar statementEnd =
                    parseIsoCalendar(
                            current.closedEnd
                    );

            StringBuilder history =
                    new StringBuilder();

            for (int index = 0;
                 index < 6 && statementEnd != null;
                 index++) {

                CreditCardCycleCalculator.Statement statement =
                        CreditCardCycleCalculator
                                .calculateStatement(
                                        card,
                                        statementEnd
                                );

                double amount =
                        getCardSpend(
                                database,
                                card,
                                transactionAccounts,
                                statement.startDate,
                                statement.endDate
                        );

                double paid =
                        database.creditCardPaymentDao()
                                .getPaidForStatement(
                                        card.getId(),
                                        statement.endDate
                                );

                double due =
                        Math.max(0, amount - paid);

                if (index > 0) {
                    history.append("\n\n");
                }

                history.append(
                        visibleDate(statement.startDate)
                );
                history.append(" – ");
                history.append(
                        visibleDate(statement.endDate)
                );
                history.append("\nStatement: ");
                history.append(money(amount));
                history.append("  •  Paid: ");
                history.append(money(paid));
                history.append("\nDue: ");
                history.append(
                        visibleDate(statement.dueDate)
                );
                history.append("  •  ");
                history.append(
                        due <= 0.005
                                ? "Paid"
                                : "Outstanding "
                                + money(due)
                );

                statementEnd =
                        CreditCardCycleCalculator
                                .previousStatementEnd(
                                        card,
                                        statementEnd
                                );
            }

            String result = history.toString();

            runOnUiThread(() ->
                    new AlertDialog.Builder(this)
                            .setTitle("Last 6 Statements")
                            .setMessage(result)
                            .setPositiveButton("OK", null)
                            .show()
            );
        }).start();
    }

    private void showPaymentDialog(
            CardViewData data
    ) {
        LinearLayout form =
                new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(
                dp(22),
                dp(6),
                dp(22),
                0
        );

        EditText amountInput =
                new EditText(this);
        amountInput.setHint("Payment amount");
        amountInput.setInputType(
                InputType.TYPE_CLASS_NUMBER
                        | InputType.TYPE_NUMBER_FLAG_DECIMAL
        );
        amountInput.setText(
                String.format(
                        Locale.US,
                        "%.2f",
                        data.outstanding
                )
        );

        EditText noteInput =
                new EditText(this);
        noteInput.setHint("Optional note");
        noteInput.setInputType(
                InputType.TYPE_CLASS_TEXT
        );

        form.addView(amountInput);
        form.addView(noteInput);

        AlertDialog dialog =
                new AlertDialog.Builder(this)
                        .setTitle("Record Card Payment")
                        .setMessage(
                                "From: "
                                        + data.card
                                        .getPaymentAccount()
                                        + "\nStatement outstanding: "
                                        + money(data.outstanding)
                        )
                        .setView(form)
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton(
                                "Save Payment",
                                null
                        )
                        .create();

        dialog.setOnShowListener(ignored ->
                dialog.getButton(
                        AlertDialog.BUTTON_POSITIVE
                ).setOnClickListener(view -> {
                    double amount;

                    try {
                        amount = Double.parseDouble(
                                amountInput.getText()
                                        .toString().trim()
                        );
                    } catch (Exception exception) {
                        amountInput.setError(
                                "Enter a valid amount"
                        );
                        return;
                    }

                    if (amount <= 0
                            || amount
                            > data.outstanding + 0.005) {
                        amountInput.setError(
                                "Amount must be within outstanding balance"
                        );
                        return;
                    }

                    dialog.getButton(
                            AlertDialog.BUTTON_POSITIVE
                    ).setEnabled(false);

                    recordPayment(
                            data,
                            amount,
                            noteInput.getText()
                                    .toString().trim(),
                            dialog
                    );
                })
        );

        dialog.show();
    }

    private void recordPayment(
            CardViewData data,
            double amount,
            String note,
            AlertDialog dialog
    ) {
        new Thread(() -> {
            try {
                AppDatabase database =
                        getDatabase();

                String paymentDate =
                        new SimpleDateFormat(
                                "yyyy-MM-dd HH:mm",
                                Locale.US
                        ).format(
                                Calendar.getInstance()
                                        .getTime()
                        );

                CreditCardPayment payment =
                        new CreditCardPayment();
                payment.setCreditCardId(
                        data.card.getId()
                );
                payment.setStatementEndDate(
                        data.cycle.closedEnd
                );
                payment.setAmount(amount);
                payment.setPaymentDate(paymentDate);
                payment.setSourceAccount(
                        data.card.getPaymentAccount()
                );
                payment.setNote(note);

                Transaction transferOut =
                        createTransferTransaction(
                                "TRANSFER_OUT",
                                amount,
                                data.card.getPaymentAccount(),
                                "Credit card payment to "
                                        + data.card.getAccountName(),
                                paymentDate
                        );

                Transaction transferIn =
                        createTransferTransaction(
                                "TRANSFER_IN",
                                amount,
                                data.card.getAccountName(),
                                "Credit card payment from "
                                        + data.card.getPaymentAccount(),
                                paymentDate
                        );

                database.runInTransaction(() -> {
                    database.creditCardPaymentDao()
                            .insert(payment);
                    database.transactionDao()
                            .insert(transferOut);
                    database.transactionDao()
                            .insert(transferIn);
                });

                runOnUiThread(() -> {
                    dialog.dismiss();
                    showMessage(
                            "Credit card payment recorded"
                    );
                    loadData();
                });

            } catch (Exception exception) {
                runOnUiThread(() -> {
                    dialog.getButton(
                            AlertDialog.BUTTON_POSITIVE
                    ).setEnabled(true);
                    showMessage(
                            "Unable to record payment"
                    );
                });
            }
        }).start();
    }

    private Transaction createTransferTransaction(
            String type,
            double amount,
            String account,
            String note,
            String date
    ) {
        Transaction transaction =
                new Transaction();
        transaction.setType(type);
        transaction.setAmount(amount);
        transaction.setCategory(
                "Credit Card Payment"
        );
        transaction.setAccount(account);
        transaction.setNote(note);
        transaction.setDate(date);
        return transaction;
    }

    private void startEditing(
            CreditCard card
    ) {
        editingCard = card;
        txtFormTitle.setText("Edit Card Settings");
        etCardName.setText(card.getName());
        etLastFour.setText(card.getLastFour());
        etCardName.setEnabled(false);
        etLastFour.setEnabled(false);
        etCreditLimit.setText(
                String.format(
                        Locale.US,
                        "%.2f",
                        card.getCreditLimit()
                )
        );
        dropdownBillingDay.setText(
                String.valueOf(card.getBillingDay()),
                false
        );
        dropdownDueDay.setText(
                String.valueOf(card.getDueDay()),
                false
        );
        dropdownPaymentAccount.setText(
                card.getPaymentAccount(),
                false
        );
        dropdownReminderDays.setText(
                reminderLabel(
                        card.getReminderDays()
                ),
                false
        );
        btnSaveCard.setText("Update Card Settings");
        btnCancelEdit.setVisibility(View.VISIBLE);
        creditCardScroll.smoothScrollTo(0, 0);
    }

    private void resetForm() {
        editingCard = null;
        txtFormTitle.setText("Add Credit Card");
        etCardName.setEnabled(true);
        etLastFour.setEnabled(true);
        etCardName.setText("");
        etLastFour.setText("");
        etCreditLimit.setText("");
        dropdownBillingDay.setText("15", false);
        dropdownDueDay.setText("5", false);
        dropdownReminderDays.setText(
                "3 days before",
                false
        );

        if (!paymentAccounts.isEmpty()) {
            dropdownPaymentAccount.setText(
                    preferredPaymentAccount(),
                    false
            );
        }

        btnSaveCard.setEnabled(true);
        btnCancelEdit.setEnabled(true);
        btnSaveCard.setText("Save Credit Card");
        btnCancelEdit.setVisibility(View.GONE);
        clearErrors();
    }

    private double getCardSpend(
            AppDatabase database,
            CreditCard card,
            List<String> transactionAccounts,
            String startDate,
            String endDate
    ) {
        return database.transactionDao()
                .getNetCardSpendForPeriodFromSources(
                        transactionAccounts,
                        card.getName(),
                        startDate + " 00:00",
                        endDate + " 23:59"
                );
    }

    private String statementStatus(
            CardViewData data
    ) {
        if (data.outstanding <= 0.005) {
            return "Latest statement paid";
        }

        if (data.cycle.daysUntilDue < 0) {
            return "Payment overdue by "
                    + Math.abs(
                    data.cycle.daysUntilDue
            )
                    + " day(s) • "
                    + money(data.outstanding);
        }

        if (data.cycle.daysUntilDue == 0) {
            return "Payment due today • "
                    + money(data.outstanding);
        }

        return "Payment due in "
                + data.cycle.daysUntilDue
                + " day(s) • "
                + money(data.outstanding);
    }

    private int parseDay(
            MaterialAutoCompleteTextView dropdown
    ) {
        try {
            int day = Integer.parseInt(
                    dropdown.getText()
                            .toString().trim()
            );
            return day >= 1 && day <= 31
                    ? day
                    : -1;
        } catch (Exception exception) {
            return -1;
        }
    }

    private int parseReminderDays() {
        String text =
                dropdownReminderDays
                        .getText().toString().trim();

        if (text.equalsIgnoreCase(
                "On due date"
        )) {
            return 0;
        }

        try {
            return Integer.parseInt(
                    text.split(" ")[0]
            );
        } catch (Exception exception) {
            return 3;
        }
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT
                < Build.VERSION_CODES.TIRAMISU) {
            return;
        }

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED) {
            return;
        }

        notificationPermissionLauncher.launch(
                Manifest.permission.POST_NOTIFICATIONS
        );
    }

    private String reminderLabel(int days) {
        if (days <= 0) {
            return "On due date";
        }

        return days
                + (days == 1
                ? " day before"
                : " days before");
    }

    private Calendar parseIsoCalendar(
            String date
    ) {
        try {
            Calendar calendar =
                    Calendar.getInstance();
            calendar.setTime(
                    new SimpleDateFormat(
                            "yyyy-MM-dd",
                            Locale.US
                    ).parse(date)
            );
            return calendar;
        } catch (Exception exception) {
            return null;
        }
    }

    private String visibleDate(String date) {
        try {
            return new SimpleDateFormat(
                    "dd MMM yyyy",
                    Locale.ENGLISH
            ).format(
                    new SimpleDateFormat(
                            "yyyy-MM-dd",
                            Locale.US
                    ).parse(date)
            );
        } catch (Exception exception) {
            return date;
        }
    }

    private String money(double amount) {
        return NumberFormat
                .getCurrencyInstance(
                        new Locale("en", "IN")
                )
                .format(amount);
    }

    private String buildCardAccountName(
            String name,
            String lastFour
    ) {
        return name.trim()
                + " •••• "
                + lastFour;
    }

    private MaterialButton actionButton(
            String text
    ) {
        MaterialButton button =
                new MaterialButton(
                        this,
                        null,
                        com.google.android.material.R.attr
                                .materialButtonOutlinedStyle
                );
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(12);
        button.setCornerRadius(dp(12));
        return button;
    }

    private TextView text(
            String value,
            int size,
            int colorResource,
            boolean bold
    ) {
        TextView textView =
                new TextView(this);
        textView.setText(value);
        textView.setTextSize(size);
        textView.setTextColor(
                color(colorResource)
        );
        textView.setLineSpacing(dp(2), 1f);

        if (bold) {
            textView.setTypeface(
                    Typeface.DEFAULT_BOLD
            );
        }

        return textView;
    }

    private void clearErrors() {
        inputCardName.setError(null);
        inputLastFour.setError(null);
        inputCreditLimit.setError(null);
    }

    private String value(
            TextInputEditText editText
    ) {
        return editText.getText() == null
                ? ""
                : editText.getText()
                .toString().trim();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private int color(int resource) {
        return ContextCompat.getColor(
                this,
                resource
        );
    }

    private int dp(int value) {
        return Math.round(
                value
                        * getResources()
                        .getDisplayMetrics()
                        .density
        );
    }

    private AppDatabase getDatabase() {
        return DatabaseClient
                .getInstance(
                        getApplicationContext()
                )
                .getAppDatabase();
    }

    private void showMessage(String message) {
        Toast.makeText(
                this,
                message,
                Toast.LENGTH_LONG
        ).show();
    }

    private static final class CardViewData {
        final CreditCard card;
        final CreditCardCycleCalculator.Cycle cycle;
        final double statementAmount;
        final double paidAmount;
        final double outstanding;
        final double currentSpend;
        final double totalUsed;
        final int syncedTransactionCount;

        CardViewData(
                CreditCard card,
                CreditCardCycleCalculator.Cycle cycle,
                double statementAmount,
                double paidAmount,
                double outstanding,
                double currentSpend,
                double totalUsed,
                int syncedTransactionCount
        ) {
            this.card = card;
            this.cycle = cycle;
            this.statementAmount = statementAmount;
            this.paidAmount = paidAmount;
            this.outstanding = outstanding;
            this.currentSpend = currentSpend;
            this.totalUsed = totalUsed;
            this.syncedTransactionCount =
                    syncedTransactionCount;
        }
    }
}
