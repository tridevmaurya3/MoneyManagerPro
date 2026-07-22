package com.example.moneymanagerpro.activities;

import android.app.DatePickerDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
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
import com.example.moneymanagerpro.database.DatabaseClient;
import com.example.moneymanagerpro.model.Account;
import com.example.moneymanagerpro.model.Category;
import com.example.moneymanagerpro.model.RecurringTransaction;
import com.example.moneymanagerpro.model.Transaction;
import com.example.moneymanagerpro.utils.BubbleTouchAnimator;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class RecurringActivity extends AppCompatActivity {

    private TextInputLayout inputAmount;

    private TextInputEditText etAmount;
    private TextInputEditText etStartDate;
    private TextInputEditText etNote;

    private MaterialAutoCompleteTextView dropdownType;
    private MaterialAutoCompleteTextView dropdownCategory;
    private MaterialAutoCompleteTextView dropdownAccount;
    private MaterialAutoCompleteTextView dropdownFrequency;

    private MaterialButton btnSaveRecurring;
    private MaterialButton btnRunDueEntries;

    private LinearLayout recurringContainer;
    private TextView txtEmptyRecurring;

    private Calendar selectedCalendar;
    private String selectedStartDate;

    private final String[] transactionTypes = {
            "Expense",
            "Income"
    };

    private final String[] frequencies = {
            "Daily",
            "Weekly",
            "Monthly",
            "Yearly"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recurring);

        bindViews();
        prepareScreen();
    }

    @Override
    protected void onResume() {
        super.onResume();

        loadAccounts();
        loadRecurringEntries();
    }

    private void bindViews() {
        inputAmount =
                findViewById(R.id.inputAmount);

        etAmount =
                findViewById(R.id.etAmount);

        etStartDate =
                findViewById(R.id.etStartDate);

        etNote =
                findViewById(R.id.etNote);

        dropdownType =
                findViewById(R.id.dropdownType);

        dropdownCategory =
                findViewById(R.id.dropdownCategory);

        dropdownAccount =
                findViewById(R.id.dropdownAccount);

        dropdownFrequency =
                findViewById(R.id.dropdownFrequency);

        btnSaveRecurring =
                findViewById(R.id.btnSaveRecurring);

        btnRunDueEntries =
                findViewById(R.id.btnRunDueEntries);

        recurringContainer =
                findViewById(R.id.recurringContainer);

        txtEmptyRecurring =
                findViewById(R.id.txtEmptyRecurring);

        TextView btnBack =
                findViewById(R.id.btnBack);

        btnBack.setOnClickListener(
                view -> finish()
        );
    }

    private void prepareScreen() {
        selectedCalendar =
                Calendar.getInstance();

        updateStartDateField();
        setupDropdowns();

        etStartDate.setOnClickListener(
                view -> showDatePicker()
        );

        btnSaveRecurring.setOnClickListener(
                view -> saveRecurringEntry()
        );

        btnRunDueEntries.setOnClickListener(
                view -> runDueEntriesNow()
        );

        BubbleTouchAnimator.apply(
                btnSaveRecurring
        );

        BubbleTouchAnimator.apply(
                btnRunDueEntries
        );
    }

    private void setupDropdowns() {
        ArrayAdapter<String> typeAdapter =
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_list_item_1,
                        transactionTypes
                );

        ArrayAdapter<String> frequencyAdapter =
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_list_item_1,
                        frequencies
                );

        dropdownType.setAdapter(typeAdapter);
        dropdownFrequency.setAdapter(frequencyAdapter);

        dropdownType.setText(
                "Expense",
                false
        );

        dropdownFrequency.setText(
                "Monthly",
                false
        );

        dropdownType.setOnItemClickListener(
                (parent, view, position, id) ->
                        loadCategoriesForSelectedType()
        );

        loadCategoriesForSelectedType();
    }

    private void loadAccounts() {
        new Thread(() -> {
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
                    if (account == null
                            || account.getName() == null
                            || account.getName()
                            .trim()
                            .isEmpty()) {

                        continue;
                    }

                    accountNames.add(
                            account.getName().trim()
                    );
                }
            }

            if (accountNames.isEmpty()) {
                accountNames.add("Cash");
            }

            runOnUiThread(() -> {
                ArrayAdapter<String> accountAdapter =
                        new ArrayAdapter<>(
                                RecurringActivity.this,
                                android.R.layout.simple_list_item_1,
                                accountNames
                        );

                dropdownAccount.setAdapter(
                        accountAdapter
                );

                String currentAccount =
                        dropdownAccount
                                .getText()
                                .toString()
                                .trim();

                if (!currentAccount.isEmpty()
                        && accountNames.contains(
                        currentAccount
                )) {
                    return;
                }

                String selectedAccount =
                        accountNames.get(0);

                for (String accountName :
                        accountNames) {

                    if (accountName.equalsIgnoreCase(
                            "Cash"
                    )) {
                        selectedAccount =
                                accountName;

                        break;
                    }
                }

                dropdownAccount.setText(
                        selectedAccount,
                        false
                );
            });
        }).start();
    }

    private void loadCategoriesForSelectedType() {
        String selectedType =
                dropdownType
                        .getText()
                        .toString()
                        .trim();

        boolean isIncome =
                selectedType.equalsIgnoreCase(
                        "Income"
                );

        new Thread(() -> {
            List<Category> categories =
                    DatabaseClient
                            .getInstance(
                                    getApplicationContext()
                            )
                            .getAppDatabase()
                            .categoryDao()
                            .getAllCategories();

            List<String> categoryNames =
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

                    String categoryType =
                            category.getType();

                    if (categoryType != null
                            && categoryType
                            .equalsIgnoreCase(
                                    isIncome
                                            ? "Income"
                                            : "Expense"
                            )) {

                        categoryNames.add(
                                category.getName()
                                        .trim()
                        );
                    }
                }
            }

            if (categoryNames.isEmpty()) {
                if (isIncome) {
                    categoryNames.add("Salary");
                    categoryNames.add("Business");
                    categoryNames.add("Other Income");

                } else {
                    categoryNames.add("Food");
                    categoryNames.add("Bills");
                    categoryNames.add("Other Expense");
                }
            }

            runOnUiThread(() -> {
                ArrayAdapter<String> categoryAdapter =
                        new ArrayAdapter<>(
                                RecurringActivity.this,
                                android.R.layout.simple_list_item_1,
                                categoryNames
                        );

                dropdownCategory.setAdapter(
                        categoryAdapter
                );

                dropdownCategory.setText(
                        categoryNames.get(0),
                        false
                );
            });
        }).start();
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

                            updateStartDateField();
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

    private void updateStartDateField() {
        selectedStartDate =
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

        etStartDate.setText(visibleDate);
    }

    private void saveRecurringEntry() {
        String amountText =
                etAmount.getText() == null
                        ? ""
                        : etAmount
                        .getText()
                        .toString()
                        .trim();

        if (amountText.isEmpty()) {
            inputAmount.setError(
                    "Please enter amount"
            );

            etAmount.requestFocus();
            return;
        }

        double amount;

        try {
            amount =
                    Double.parseDouble(
                            amountText
                    );

        } catch (Exception exception) {
            inputAmount.setError(
                    "Enter a valid amount"
            );

            etAmount.requestFocus();
            return;
        }

        if (amount <= 0) {
            inputAmount.setError(
                    "Amount must be greater than zero"
            );

            etAmount.requestFocus();
            return;
        }

        inputAmount.setError(null);

        String selectedType =
                dropdownType
                        .getText()
                        .toString()
                        .trim();

        String category =
                dropdownCategory
                        .getText()
                        .toString()
                        .trim();

        String account =
                dropdownAccount
                        .getText()
                        .toString()
                        .trim();

        String frequency =
                dropdownFrequency
                        .getText()
                        .toString()
                        .trim();

        if (selectedType.isEmpty()) {
            showMessage(
                    "Please select entry type"
            );
            return;
        }

        if (category.isEmpty()) {
            showMessage(
                    "Please select category"
            );
            return;
        }

        if (account.isEmpty()) {
            showMessage(
                    "Please select account"
            );
            return;
        }

        if (frequency.isEmpty()) {
            showMessage(
                    "Please select frequency"
            );
            return;
        }

        if (selectedStartDate == null
                || selectedStartDate
                .trim()
                .isEmpty()) {

            showMessage(
                    "Please select start date"
            );
            return;
        }

        String type =
                selectedType.equalsIgnoreCase(
                        "Income"
                )
                        ? "INCOME"
                        : "EXPENSE";

        String note =
                etNote.getText() == null
                        ? ""
                        : etNote
                        .getText()
                        .toString()
                        .trim();

        RecurringTransaction recurringTransaction =
                new RecurringTransaction();

        recurringTransaction.setType(type);
        recurringTransaction.setAmount(amount);
        recurringTransaction.setCategory(category);
        recurringTransaction.setAccount(account);
        recurringTransaction.setNote(note);
        recurringTransaction.setFrequency(frequency);

        recurringTransaction.setStartDate(
                selectedStartDate
        );

        recurringTransaction.setNextRunDate(
                selectedStartDate
        );

        recurringTransaction.setActive(true);

        btnSaveRecurring.setEnabled(false);

        btnSaveRecurring.setText(
                "Saving Schedule..."
        );

        new Thread(() -> {
            try {
                DatabaseClient
                        .getInstance(
                                getApplicationContext()
                        )
                        .getAppDatabase()
                        .recurringTransactionDao()
                        .insert(recurringTransaction);

                runOnUiThread(() -> {
                    etAmount.setText("");
                    etNote.setText("");

                    dropdownType.setText(
                            "Expense",
                            false
                    );

                    dropdownFrequency.setText(
                            "Monthly",
                            false
                    );

                    btnSaveRecurring.setEnabled(true);

                    btnSaveRecurring.setText(
                            "Save Recurring Entry"
                    );

                    Toast.makeText(
                            RecurringActivity.this,
                            "Recurring entry saved",
                            Toast.LENGTH_SHORT
                    ).show();

                    loadCategoriesForSelectedType();
                    loadRecurringEntries();
                });

            } catch (Exception exception) {
                runOnUiThread(() -> {
                    btnSaveRecurring.setEnabled(true);

                    btnSaveRecurring.setText(
                            "Save Recurring Entry"
                    );

                    Toast.makeText(
                            RecurringActivity.this,
                            "Unable to save recurring entry",
                            Toast.LENGTH_SHORT
                    ).show();
                });
            }
        }).start();
    }

    private void loadRecurringEntries() {
        new Thread(() -> {
            List<RecurringTransaction> entries =
                    DatabaseClient
                            .getInstance(
                                    getApplicationContext()
                            )
                            .getAppDatabase()
                            .recurringTransactionDao()
                            .getAllRecurringTransactions();

            runOnUiThread(
                    () -> showRecurringEntries(
                            entries
                    )
            );
        }).start();
    }

    private void showRecurringEntries(
            List<RecurringTransaction> entries
    ) {
        recurringContainer.removeAllViews();

        boolean isEmpty =
                entries == null
                        || entries.isEmpty();

        txtEmptyRecurring.setVisibility(
                isEmpty
                        ? View.VISIBLE
                        : View.GONE
        );

        if (isEmpty) {
            return;
        }

        for (RecurringTransaction entry :
                entries) {

            if (entry != null) {
                addRecurringCard(entry);
            }
        }
    }

    private void addRecurringCard(
            RecurringTransaction entry
    ) {
        boolean isIncome =
                "INCOME".equalsIgnoreCase(
                        entry.getType()
                );

        int primaryColor =
                getColorValue(
                        isIncome
                                ? R.color.success
                                : R.color.expense
                );

        int primarySurface =
                getColorValue(
                        isIncome
                                ? R.color.success_surface
                                : R.color.expense_surface
                );

        int primaryOutline =
                getColorValue(
                        isIncome
                                ? R.color.success_outline
                                : R.color.expense_outline
                );

        int scheduleStatusColor =
                getColorValue(
                        entry.isActive()
                                ? R.color.success
                                : R.color.warning
                );

        int scheduleStatusSurface =
                getColorValue(
                        entry.isActive()
                                ? R.color.success_surface
                                : R.color.warning_surface
                );

        int scheduleStatusOutline =
                getColorValue(
                        entry.isActive()
                                ? R.color.success_outline
                                : R.color.warning_outline
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
                entry.isActive()
                        ? primaryOutline
                        : scheduleStatusOutline
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

        TextView entryIcon =
                createEntryIcon(
                        isIncome,
                        primaryColor,
                        primarySurface,
                        primaryOutline
                );

        headerRow.addView(entryIcon);

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

        TextView txtTitle =
                createText(
                        safeText(
                                entry.getCategory(),
                                "Recurring Entry"
                        ),
                        16,
                        getColorValue(
                                R.color.app_text_primary
                        ),
                        true
                );

        TextView txtType =
                createText(
                        isIncome
                                ? "Recurring Income"
                                : "Recurring Expense",
                        11,
                        primaryColor,
                        true
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

        txtType.setLayoutParams(typeParams);

        titleContainer.addView(txtTitle);
        titleContainer.addView(txtType);

        headerRow.addView(titleContainer);

        TextView statusBadge =
                createStatusBadge(
                        entry.isActive()
                                ? "Active"
                                : "Paused",
                        scheduleStatusColor,
                        scheduleStatusSurface,
                        scheduleStatusOutline
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
         * Amount
         */

        LinearLayout amountBox =
                createAmountBox(
                        formatAmount(
                                entry.getAmount()
                        ),
                        isIncome
                                ? "Scheduled income amount"
                                : "Scheduled expense amount",
                        primaryColor,
                        primarySurface,
                        primaryOutline
                );

        content.addView(amountBox);

        /*
         * Frequency and Account
         */

        LinearLayout detailRow =
                new LinearLayout(this);

        detailRow.setOrientation(
                LinearLayout.HORIZONTAL
        );

        detailRow.setBaselineAligned(false);

        LinearLayout frequencyBlock =
                createDetailBlock(
                        "Frequency",
                        safeText(
                                entry.getFrequency(),
                                "Monthly"
                        ),
                        getColorValue(
                                R.color.warning
                        ),
                        getColorValue(
                                R.color.warning_surface
                        ),
                        getColorValue(
                                R.color.warning_outline
                        )
                );

        LinearLayout.LayoutParams frequencyParams =
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                );

        frequencyParams.setMargins(
                0,
                dpToPx(11),
                dpToPx(5),
                0
        );

        frequencyBlock.setLayoutParams(
                frequencyParams
        );

        LinearLayout accountBlock =
                createDetailBlock(
                        "Account",
                        safeText(
                                entry.getAccount(),
                                "Cash"
                        ),
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

        LinearLayout.LayoutParams accountParams =
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                );

        accountParams.setMargins(
                dpToPx(5),
                dpToPx(11),
                0,
                0
        );

        accountBlock.setLayoutParams(
                accountParams
        );

        detailRow.addView(frequencyBlock);
        detailRow.addView(accountBlock);

        content.addView(detailRow);

        /*
         * Next run date
         */

        LinearLayout nextRunBox =
                createNextRunBox(
                        safeText(
                                entry.getNextRunDate(),
                                "Not available"
                        ),
                        entry.isActive()
                );

        LinearLayout.LayoutParams nextRunParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        nextRunParams.setMargins(
                0,
                dpToPx(11),
                0,
                0
        );

        nextRunBox.setLayoutParams(
                nextRunParams
        );

        content.addView(nextRunBox);

        /*
         * Optional note
         */

        String note =
                safeText(
                        entry.getNote(),
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

            content.addView(noteBox);
        }

        /*
         * Action buttons
         */

        LinearLayout actionRow =
                new LinearLayout(this);

        actionRow.setOrientation(
                LinearLayout.HORIZONTAL
        );

        LinearLayout.LayoutParams actionRowParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dpToPx(46)
                );

        actionRowParams.setMargins(
                0,
                dpToPx(13),
                0,
                0
        );

        actionRow.setLayoutParams(
                actionRowParams
        );

        MaterialButton btnPauseResume =
                createPauseResumeButton(
                        entry.isActive()
                );

        MaterialButton btnDelete =
                createDeleteButton();

        LinearLayout.LayoutParams pauseParams =
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        1f
                );

        pauseParams.setMargins(
                0,
                0,
                dpToPx(5),
                0
        );

        btnPauseResume.setLayoutParams(
                pauseParams
        );

        LinearLayout.LayoutParams deleteParams =
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        1f
                );

        deleteParams.setMargins(
                dpToPx(5),
                0,
                0,
                0
        );

        btnDelete.setLayoutParams(
                deleteParams
        );

        btnPauseResume.setOnClickListener(
                view -> toggleSchedule(entry)
        );

        btnDelete.setOnClickListener(
                view -> confirmDelete(entry)
        );

        BubbleTouchAnimator.apply(
                btnPauseResume
        );

        BubbleTouchAnimator.apply(
                btnDelete
        );

        actionRow.addView(btnPauseResume);
        actionRow.addView(btnDelete);

        content.addView(actionRow);

        card.addView(content);

        recurringContainer.addView(card);
    }

    private TextView createEntryIcon(
            boolean isIncome,
            int primaryColor,
            int surfaceColor,
            int outlineColor
    ) {
        TextView icon =
                new TextView(this);

        icon.setText(
                isIncome
                        ? "↓"
                        : "↑"
        );

        icon.setTextColor(primaryColor);
        icon.setTextSize(20);

        icon.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        icon.setGravity(Gravity.CENTER);

        GradientDrawable background =
                new GradientDrawable();

        background.setColor(surfaceColor);

        background.setStroke(
                dpToPx(1),
                outlineColor
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
            String text,
            int textColor,
            int backgroundColor,
            int outlineColor
    ) {
        TextView badge =
                new TextView(this);

        badge.setText(text);
        badge.setTextColor(textColor);
        badge.setTextSize(10);

        badge.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        badge.setGravity(Gravity.CENTER);

        badge.setPadding(
                dpToPx(11),
                0,
                dpToPx(11),
                0
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

        badge.setBackground(background);

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        dpToPx(30)
                );

        badge.setLayoutParams(params);

        return badge;
    }

    private LinearLayout createAmountBox(
            String amount,
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
                dpToPx(13),
                dpToPx(12),
                dpToPx(13),
                dpToPx(12)
        );

        GradientDrawable background =
                new GradientDrawable();

        background.setColor(backgroundColor);

        background.setStroke(
                dpToPx(1),
                outlineColor
        );

        background.setCornerRadius(
                dpToPx(14)
        );

        container.setBackground(background);

        TextView symbol =
                new TextView(this);

        symbol.setText("₹");
        symbol.setTextColor(accentColor);
        symbol.setTextSize(18);

        symbol.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        symbol.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams symbolParams =
                new LinearLayout.LayoutParams(
                        dpToPx(36),
                        dpToPx(36)
                );

        symbol.setLayoutParams(symbolParams);

        container.addView(symbol);

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

        TextView amountView =
                createText(
                        amount,
                        19,
                        accentColor,
                        true
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
                dpToPx(2),
                0,
                0
        );

        descriptionView.setLayoutParams(
                descriptionParams
        );

        textContainer.addView(amountView);
        textContainer.addView(descriptionView);

        container.addView(textContainer);

        return container;
    }

    private LinearLayout createDetailBlock(
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
                dpToPx(10),
                dpToPx(12),
                dpToPx(10)
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
                dpToPx(3),
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

    private LinearLayout createNextRunBox(
            String nextRunDate,
            boolean isActive
    ) {
        int accentColor =
                getColorValue(
                        isActive
                                ? R.color.secondary
                                : R.color.warning
                );

        int backgroundColor =
                getColorValue(
                        isActive
                                ? R.color.info_surface
                                : R.color.warning_surface
                );

        int outlineColor =
                getColorValue(
                        isActive
                                ? R.color.info_outline
                                : R.color.warning_outline
                );

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

        icon.setText("◷");
        icon.setTextColor(accentColor);
        icon.setTextSize(18);
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

        TextView title =
                createText(
                        isActive
                                ? "Next Entry Date"
                                : "Schedule Paused",
                        10,
                        getColorValue(
                                R.color.app_text_secondary
                        ),
                        false
                );

        TextView value =
                createText(
                        nextRunDate,
                        14,
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

        value.setLayoutParams(valueParams);

        textContainer.addView(title);
        textContainer.addView(value);

        container.addView(textContainer);

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

        GradientDrawable background =
                new GradientDrawable();

        background.setColor(
                getColorValue(
                        R.color.app_surface_soft
                )
        );

        background.setStroke(
                dpToPx(1),
                getColorValue(
                        R.color.app_outline_soft
                )
        );

        background.setCornerRadius(
                dpToPx(12)
        );

        container.setBackground(background);

        TextView label =
                createText(
                        "Note",
                        10,
                        getColorValue(
                                R.color.app_text_secondary
                        ),
                        false
                );

        TextView value =
                createText(
                        note,
                        12,
                        getColorValue(
                                R.color.app_text_primary
                        ),
                        false
                );

        LinearLayout.LayoutParams valueParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        valueParams.setMargins(
                0,
                dpToPx(3),
                0,
                0
        );

        value.setLayoutParams(valueParams);

        container.addView(label);
        container.addView(value);

        return container;
    }

    private MaterialButton createPauseResumeButton(
            boolean isActive
    ) {
        MaterialButton button =
                new MaterialButton(this);

        button.setText(
                isActive
                        ? "Pause Schedule"
                        : "Resume Schedule"
        );

        button.setTextSize(11);
        button.setAllCaps(false);

        button.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        button.setCornerRadius(
                dpToPx(13)
        );

        button.setInsetTop(0);
        button.setInsetBottom(0);

        int accentColor =
                getColorValue(
                        isActive
                                ? R.color.warning
                                : R.color.success
                );

        int surfaceColor =
                getColorValue(
                        isActive
                                ? R.color.warning_surface
                                : R.color.success_surface
                );

        int outlineColor =
                getColorValue(
                        isActive
                                ? R.color.warning_outline
                                : R.color.success_outline
                );

        button.setTextColor(accentColor);

        button.setBackgroundTintList(
                ColorStateList.valueOf(
                        surfaceColor
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

        return button;
    }

    private MaterialButton createDeleteButton() {
        MaterialButton button =
                new MaterialButton(this);

        button.setText("Delete");
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
                dpToPx(13)
        );

        button.setInsetTop(0);
        button.setInsetBottom(0);

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

        return button;
    }

    private void toggleSchedule(
            RecurringTransaction entry
    ) {
        boolean newStatus =
                !entry.isActive();

        entry.setActive(newStatus);

        new Thread(() -> {
            try {
                DatabaseClient
                        .getInstance(
                                getApplicationContext()
                        )
                        .getAppDatabase()
                        .recurringTransactionDao()
                        .update(entry);

                runOnUiThread(() -> {
                    Toast.makeText(
                            RecurringActivity.this,
                            newStatus
                                    ? "Schedule resumed"
                                    : "Schedule paused",
                            Toast.LENGTH_SHORT
                    ).show();

                    loadRecurringEntries();
                });

            } catch (Exception exception) {
                entry.setActive(!newStatus);

                runOnUiThread(() ->
                        Toast.makeText(
                                RecurringActivity.this,
                                "Unable to update schedule",
                                Toast.LENGTH_SHORT
                        ).show()
                );
            }
        }).start();
    }

    private void confirmDelete(
            RecurringTransaction entry
    ) {
        String categoryName =
                safeText(
                        entry.getCategory(),
                        "this"
                );

        new AlertDialog.Builder(this)
                .setTitle(
                        "Delete Recurring Entry"
                )
                .setMessage(
                        "Delete the \""
                                + categoryName
                                + "\" recurring schedule?\n\n"
                                + "Transactions already created from this schedule will remain safe."
                )
                .setNegativeButton(
                        "Cancel",
                        null
                )
                .setPositiveButton(
                        "Delete",
                        (dialog, which) ->
                                deleteRecurringEntry(
                                        entry
                                )
                )
                .show();
    }

    private void deleteRecurringEntry(
            RecurringTransaction entry
    ) {
        new Thread(() -> {
            try {
                DatabaseClient
                        .getInstance(
                                getApplicationContext()
                        )
                        .getAppDatabase()
                        .recurringTransactionDao()
                        .delete(entry);

                runOnUiThread(() -> {
                    Toast.makeText(
                            RecurringActivity.this,
                            "Recurring entry deleted",
                            Toast.LENGTH_SHORT
                    ).show();

                    loadRecurringEntries();
                });

            } catch (Exception exception) {
                runOnUiThread(() ->
                        Toast.makeText(
                                RecurringActivity.this,
                                "Unable to delete recurring entry",
                                Toast.LENGTH_SHORT
                        ).show()
                );
            }
        }).start();
    }

    private void runDueEntriesNow() {
        btnRunDueEntries.setEnabled(false);

        btnRunDueEntries.setText(
                "Running Due Entries..."
        );

        new Thread(() -> {
            try {
                String today =
                        new SimpleDateFormat(
                                "yyyy-MM-dd",
                                Locale.US
                        ).format(
                                Calendar.getInstance()
                                        .getTime()
                        );

                List<RecurringTransaction> dueEntries =
                        DatabaseClient
                                .getInstance(
                                        getApplicationContext()
                                )
                                .getAppDatabase()
                                .recurringTransactionDao()
                                .getDueRecurringTransactions(
                                        today
                                );

                int createdEntries = 0;

                if (dueEntries != null) {
                    for (RecurringTransaction entry :
                            dueEntries) {

                        if (entry == null
                                || !entry.isActive()) {

                            continue;
                        }

                        String nextRunDate =
                                safeText(
                                        entry.getNextRunDate(),
                                        ""
                                );

                        if (nextRunDate.isEmpty()) {
                            continue;
                        }

                        int safetyCounter = 0;

                        while (entry.isActive()
                                && nextRunDate.compareTo(
                                today
                        ) <= 0
                                && safetyCounter < 366) {

                            Transaction transaction =
                                    new Transaction();

                            transaction.setType(
                                    safeText(
                                            entry.getType(),
                                            "EXPENSE"
                                    )
                            );

                            transaction.setAmount(
                                    entry.getAmount()
                            );

                            transaction.setCategory(
                                    safeText(
                                            entry.getCategory(),
                                            "Recurring Entry"
                                    )
                            );

                            transaction.setAccount(
                                    safeText(
                                            entry.getAccount(),
                                            "Cash"
                                    )
                            );

                            String entryNote =
                                    safeText(
                                            entry.getNote(),
                                            ""
                                    );

                            String transactionNote =
                                    "Recurring "
                                            + safeText(
                                            entry.getFrequency(),
                                            "Monthly"
                                    )
                                            + " entry";

                            if (!entryNote.isEmpty()) {
                                transactionNote +=
                                        " - " + entryNote;
                            }

                            transaction.setNote(
                                    transactionNote
                            );

                            transaction.setDate(
                                    new SimpleDateFormat(
                                            "yyyy-MM-dd HH:mm",
                                            Locale.US
                                    ).format(
                                            Calendar.getInstance()
                                                    .getTime()
                                    )
                            );

                            DatabaseClient
                                    .getInstance(
                                            getApplicationContext()
                                    )
                                    .getAppDatabase()
                                    .transactionDao()
                                    .insert(transaction);

                            nextRunDate =
                                    getNextRunDate(
                                            nextRunDate,
                                            safeText(
                                                    entry.getFrequency(),
                                                    "Monthly"
                                            )
                                    );

                            entry.setNextRunDate(
                                    nextRunDate
                            );

                            DatabaseClient
                                    .getInstance(
                                            getApplicationContext()
                                    )
                                    .getAppDatabase()
                                    .recurringTransactionDao()
                                    .update(entry);

                            createdEntries++;
                            safetyCounter++;
                        }
                    }
                }

                int finalCreatedEntries =
                        createdEntries;

                runOnUiThread(() -> {
                    restoreRunButton();

                    Toast.makeText(
                            RecurringActivity.this,
                            finalCreatedEntries == 0
                                    ? "No recurring entries are due today"
                                    : finalCreatedEntries
                                      + " recurring entries added",
                            Toast.LENGTH_LONG
                    ).show();

                    loadRecurringEntries();
                });

            } catch (Exception exception) {
                runOnUiThread(() -> {
                    restoreRunButton();

                    Toast.makeText(
                            RecurringActivity.this,
                            "Unable to process due entries",
                            Toast.LENGTH_SHORT
                    ).show();
                });
            }
        }).start();
    }

    private void restoreRunButton() {
        btnRunDueEntries.setEnabled(true);

        btnRunDueEntries.setText(
                "Run Due Entries Now"
        );
    }

    private String getNextRunDate(
            String currentDate,
            String frequency
    ) {
        try {
            Calendar calendar =
                    Calendar.getInstance();

            SimpleDateFormat dateFormat =
                    new SimpleDateFormat(
                            "yyyy-MM-dd",
                            Locale.US
                    );

            dateFormat.setLenient(false);

            calendar.setTime(
                    dateFormat.parse(
                            currentDate
                    )
            );

            if (frequency.equalsIgnoreCase(
                    "Daily"
            )) {
                calendar.add(
                        Calendar.DAY_OF_MONTH,
                        1
                );

            } else if (frequency.equalsIgnoreCase(
                    "Weekly"
            )) {
                calendar.add(
                        Calendar.DAY_OF_MONTH,
                        7
                );

            } else if (frequency.equalsIgnoreCase(
                    "Yearly"
            )) {
                calendar.add(
                        Calendar.YEAR,
                        1
                );

            } else {
                calendar.add(
                        Calendar.MONTH,
                        1
                );
            }

            return dateFormat.format(
                    calendar.getTime()
            );

        } catch (Exception exception) {
            return currentDate;
        }
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
        return String.format(
                new Locale("en", "IN"),
                "₹%,.2f",
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

    private void showMessage(
            String message
    ) {
        Toast.makeText(
                this,
                message,
                Toast.LENGTH_SHORT
        ).show();
    }
}