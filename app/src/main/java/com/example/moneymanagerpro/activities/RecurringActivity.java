package com.example.moneymanagerpro.activities;

import android.app.DatePickerDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

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
            "Expense", "Income"
    };

    private final String[] frequencies = {
            "Daily", "Weekly", "Monthly", "Yearly"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recurring);

        inputAmount = findViewById(R.id.inputAmount);
        etAmount = findViewById(R.id.etAmount);
        etStartDate = findViewById(R.id.etStartDate);
        etNote = findViewById(R.id.etNote);

        dropdownType = findViewById(R.id.dropdownType);
        dropdownCategory = findViewById(R.id.dropdownCategory);
        dropdownAccount = findViewById(R.id.dropdownAccount);
        dropdownFrequency = findViewById(R.id.dropdownFrequency);

        btnSaveRecurring = findViewById(R.id.btnSaveRecurring);
        btnRunDueEntries = findViewById(R.id.btnRunDueEntries);

        recurringContainer = findViewById(R.id.recurringContainer);
        txtEmptyRecurring = findViewById(R.id.txtEmptyRecurring);

        TextView btnBack = findViewById(R.id.btnBack);

        btnBack.setOnClickListener(v -> finish());

        selectedCalendar = Calendar.getInstance();
        updateStartDateField();

        setupDropdowns();

        etStartDate.setOnClickListener(v -> showDatePicker());

        BubbleTouchAnimator.apply(btnSaveRecurring);
        BubbleTouchAnimator.apply(btnRunDueEntries);

        btnSaveRecurring.setOnClickListener(v -> saveRecurringEntry());
        btnRunDueEntries.setOnClickListener(v -> runDueEntriesNow());
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadAccounts();
        loadRecurringEntries();
    }

    private void setupDropdowns() {
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                transactionTypes
        );

        ArrayAdapter<String> frequencyAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                frequencies
        );

        dropdownType.setAdapter(typeAdapter);
        dropdownFrequency.setAdapter(frequencyAdapter);

        dropdownType.setText("Expense", false);
        dropdownFrequency.setText("Monthly", false);

        dropdownType.setOnItemClickListener((parent, view, position, id) ->
                loadCategoriesForSelectedType()
        );

        loadCategoriesForSelectedType();
    }

    private void loadAccounts() {
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

            runOnUiThread(() -> {
                ArrayAdapter<String> accountAdapter = new ArrayAdapter<>(
                        RecurringActivity.this,
                        android.R.layout.simple_list_item_1,
                        accountNames
                );

                dropdownAccount.setAdapter(accountAdapter);

                String selectedAccount = accountNames.get(0);

                for (String accountName : accountNames) {
                    if (accountName.equalsIgnoreCase("Cash")) {
                        selectedAccount = accountName;
                        break;
                    }
                }

                dropdownAccount.setText(selectedAccount, false);
            });
        }).start();
    }

    private void loadCategoriesForSelectedType() {
        String selectedType = dropdownType.getText().toString().trim();

        boolean isIncome = selectedType.equalsIgnoreCase("Income");

        new Thread(() -> {
            List<Category> categories = DatabaseClient
                    .getInstance(getApplicationContext())
                    .getAppDatabase()
                    .categoryDao()
                    .getAllCategories();

            List<String> categoryNames = new ArrayList<>();

            for (Category category : categories) {
                if (category.getType() != null
                        && category.getType().equalsIgnoreCase(
                        isIncome ? "Income" : "Expense"
                )) {
                    categoryNames.add(category.getName());
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
                ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(
                        RecurringActivity.this,
                        android.R.layout.simple_list_item_1,
                        categoryNames
                );

                dropdownCategory.setAdapter(categoryAdapter);
                dropdownCategory.setText(categoryNames.get(0), false);
            });
        }).start();
    }

    private void showDatePicker() {
        DatePickerDialog dialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    selectedCalendar.set(Calendar.YEAR, year);
                    selectedCalendar.set(Calendar.MONTH, month);
                    selectedCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);

                    updateStartDateField();
                },
                selectedCalendar.get(Calendar.YEAR),
                selectedCalendar.get(Calendar.MONTH),
                selectedCalendar.get(Calendar.DAY_OF_MONTH)
        );

        dialog.show();
    }

    private void updateStartDateField() {
        selectedStartDate = new SimpleDateFormat(
                "yyyy-MM-dd",
                Locale.US
        ).format(selectedCalendar.getTime());

        String visibleDate = new SimpleDateFormat(
                "dd MMMM yyyy",
                Locale.ENGLISH
        ).format(selectedCalendar.getTime());

        etStartDate.setText(visibleDate);
    }

    private void saveRecurringEntry() {
        String amountText = etAmount.getText() == null
                ? ""
                : etAmount.getText().toString().trim();

        if (amountText.isEmpty()) {
            inputAmount.setError("Please enter amount");
            return;
        }

        double amount;

        try {
            amount = Double.parseDouble(amountText);
        } catch (Exception exception) {
            inputAmount.setError("Enter a valid amount");
            return;
        }

        if (amount <= 0) {
            inputAmount.setError("Amount must be greater than zero");
            return;
        }

        inputAmount.setError(null);

        String selectedType = dropdownType.getText().toString().trim();

        String type = selectedType.equalsIgnoreCase("Income")
                ? "INCOME"
                : "EXPENSE";

        String category = dropdownCategory.getText().toString().trim();
        String account = dropdownAccount.getText().toString().trim();
        String frequency = dropdownFrequency.getText().toString().trim();

        String note = etNote.getText() == null
                ? ""
                : etNote.getText().toString().trim();

        RecurringTransaction recurringTransaction = new RecurringTransaction();
        recurringTransaction.setType(type);
        recurringTransaction.setAmount(amount);
        recurringTransaction.setCategory(category);
        recurringTransaction.setAccount(account);
        recurringTransaction.setNote(note);
        recurringTransaction.setFrequency(frequency);
        recurringTransaction.setStartDate(selectedStartDate);
        recurringTransaction.setNextRunDate(selectedStartDate);
        recurringTransaction.setActive(true);

        btnSaveRecurring.setEnabled(false);
        btnSaveRecurring.setText("Saving Schedule...");

        new Thread(() -> {
            DatabaseClient.getInstance(getApplicationContext())
                    .getAppDatabase()
                    .recurringTransactionDao()
                    .insert(recurringTransaction);

            runOnUiThread(() -> {
                etAmount.setText("");
                etNote.setText("");
                dropdownType.setText("Expense", false);
                dropdownFrequency.setText("Monthly", false);

                btnSaveRecurring.setEnabled(true);
                btnSaveRecurring.setText("Save Recurring Entry");

                Toast.makeText(
                        RecurringActivity.this,
                        "Recurring entry saved",
                        Toast.LENGTH_SHORT
                ).show();

                loadCategoriesForSelectedType();
                loadRecurringEntries();
            });
        }).start();
    }

    private void loadRecurringEntries() {
        new Thread(() -> {
            List<RecurringTransaction> entries = DatabaseClient
                    .getInstance(getApplicationContext())
                    .getAppDatabase()
                    .recurringTransactionDao()
                    .getAllRecurringTransactions();

            runOnUiThread(() -> showRecurringEntries(entries));
        }).start();
    }

    private void showRecurringEntries(List<RecurringTransaction> entries) {
        recurringContainer.removeAllViews();

        txtEmptyRecurring.setVisibility(
                entries.isEmpty() ? View.VISIBLE : View.GONE
        );

        for (RecurringTransaction entry : entries) {
            addRecurringCard(entry);
        }
    }

    private void addRecurringCard(RecurringTransaction entry) {
        boolean isIncome = entry.getType().equalsIgnoreCase("INCOME");

        int primaryColor = isIncome
                ? Color.parseColor("#2E7D32")
                : Color.parseColor("#D32F2F");

        MaterialCardView card = new MaterialCardView(this);
        card.setCardBackgroundColor(Color.WHITE);
        card.setRadius(dpToPx(22));
        card.setCardElevation(dpToPx(5));

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, 0, 0, dpToPx(12));
        card.setLayoutParams(cardParams);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));

        TextView txtTitle = new TextView(this);
        txtTitle.setText(entry.getCategory());
        txtTitle.setTextSize(20);
        txtTitle.setTextColor(Color.parseColor("#172033"));
        txtTitle.setGravity(Gravity.CENTER);
        txtTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);

        TextView txtType = new TextView(this);
        txtType.setText(
                (isIncome ? "Income" : "Expense")
                        + " • "
                        + entry.getFrequency()
                        + " • "
                        + entry.getAccount()
        );
        txtType.setTextSize(13);
        txtType.setTextColor(primaryColor);
        txtType.setGravity(Gravity.CENTER);
        txtType.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);

        TextView txtAmount = new TextView(this);
        txtAmount.setText(formatAmount(entry.getAmount()));
        txtAmount.setTextSize(21);
        txtAmount.setTextColor(primaryColor);
        txtAmount.setGravity(Gravity.CENTER);
        txtAmount.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);

        TextView txtNextRun = new TextView(this);
        txtNextRun.setText("Next Entry: " + entry.getNextRunDate());
        txtNextRun.setTextSize(13);
        txtNextRun.setTextColor(Color.parseColor("#64748B"));
        txtNextRun.setGravity(Gravity.CENTER);

        TextView txtStatus = new TextView(this);
        txtStatus.setText(entry.isActive() ? "Active Schedule" : "Paused Schedule");
        txtStatus.setTextSize(13);
        txtStatus.setGravity(Gravity.CENTER);
        txtStatus.setTextColor(
                entry.isActive()
                        ? Color.parseColor("#2E7D32")
                        : Color.parseColor("#EF6C00")
        );

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout.LayoutParams actionRowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(50)
        );
        actionRowParams.setMargins(0, dpToPx(14), 0, 0);
        actionRow.setLayoutParams(actionRowParams);

        MaterialButton btnPauseResume = new MaterialButton(this);
        btnPauseResume.setText(entry.isActive() ? "Pause" : "Resume");
        btnPauseResume.setTextColor(Color.WHITE);
        btnPauseResume.setTextSize(13);
        btnPauseResume.setAllCaps(false);
        btnPauseResume.setCornerRadius(dpToPx(22));
        btnPauseResume.setBackgroundTintList(
                ColorStateList.valueOf(
                        entry.isActive()
                                ? Color.parseColor("#EF6C00")
                                : Color.parseColor("#2E7D32")
                )
        );

        MaterialButton btnDelete = new MaterialButton(this);
        btnDelete.setText("Delete");
        btnDelete.setTextColor(Color.WHITE);
        btnDelete.setTextSize(13);
        btnDelete.setAllCaps(false);
        btnDelete.setCornerRadius(dpToPx(22));
        btnDelete.setBackgroundTintList(
                ColorStateList.valueOf(Color.parseColor("#455A64"))
        );

        LinearLayout.LayoutParams pauseParams = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1
        );
        pauseParams.setMargins(0, 0, dpToPx(6), 0);

        LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1
        );
        deleteParams.setMargins(dpToPx(6), 0, 0, 0);

        btnPauseResume.setLayoutParams(pauseParams);
        btnDelete.setLayoutParams(deleteParams);

        BubbleTouchAnimator.apply(card);
        BubbleTouchAnimator.apply(btnPauseResume);
        BubbleTouchAnimator.apply(btnDelete);

        btnPauseResume.setOnClickListener(v -> toggleSchedule(entry));
        btnDelete.setOnClickListener(v -> confirmDelete(entry));

        actionRow.addView(btnPauseResume);
        actionRow.addView(btnDelete);

        content.addView(txtTitle);
        content.addView(txtType);
        content.addView(txtAmount);
        content.addView(txtNextRun);
        content.addView(txtStatus);
        content.addView(actionRow);

        card.addView(content);
        recurringContainer.addView(card);
    }

    private void toggleSchedule(RecurringTransaction entry) {
        entry.setActive(!entry.isActive());

        new Thread(() -> {
            DatabaseClient.getInstance(getApplicationContext())
                    .getAppDatabase()
                    .recurringTransactionDao()
                    .update(entry);

            runOnUiThread(() -> {
                Toast.makeText(
                        RecurringActivity.this,
                        entry.isActive()
                                ? "Schedule resumed"
                                : "Schedule paused",
                        Toast.LENGTH_SHORT
                ).show();

                loadRecurringEntries();
            });
        }).start();
    }

    private void confirmDelete(RecurringTransaction entry) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Recurring Entry")
                .setMessage(
                        "Do you want to delete the "
                                + entry.getCategory()
                                + " recurring schedule?"
                )
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    new Thread(() -> {
                        DatabaseClient.getInstance(getApplicationContext())
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
                    }).start();
                })
                .show();
    }

    private void runDueEntriesNow() {
        btnRunDueEntries.setEnabled(false);
        btnRunDueEntries.setText("Running Due Entries...");

        new Thread(() -> {
            String today = new SimpleDateFormat(
                    "yyyy-MM-dd",
                    Locale.US
            ).format(Calendar.getInstance().getTime());

            List<RecurringTransaction> dueEntries = DatabaseClient
                    .getInstance(getApplicationContext())
                    .getAppDatabase()
                    .recurringTransactionDao()
                    .getDueRecurringTransactions(today);

            int createdEntries = 0;

            for (RecurringTransaction entry : dueEntries) {
                int safetyCounter = 0;

                while (entry.isActive()
                        && entry.getNextRunDate().compareTo(today) <= 0
                        && safetyCounter < 366) {

                    Transaction transaction = new Transaction();
                    transaction.setType(entry.getType());
                    transaction.setAmount(entry.getAmount());
                    transaction.setCategory(entry.getCategory());
                    transaction.setAccount(entry.getAccount());
                    transaction.setNote(
                            "Recurring " + entry.getFrequency()
                                    + " entry"
                                    + (entry.getNote().isEmpty()
                                    ? ""
                                    : " - " + entry.getNote())
                    );
                    transaction.setDate(
                            new SimpleDateFormat(
                                    "yyyy-MM-dd HH:mm",
                                    Locale.US
                            ).format(Calendar.getInstance().getTime())
                    );

                    DatabaseClient.getInstance(getApplicationContext())
                            .getAppDatabase()
                            .transactionDao()
                            .insert(transaction);

                    entry.setNextRunDate(
                            getNextRunDate(
                                    entry.getNextRunDate(),
                                    entry.getFrequency()
                            )
                    );

                    DatabaseClient.getInstance(getApplicationContext())
                            .getAppDatabase()
                            .recurringTransactionDao()
                            .update(entry);

                    createdEntries++;
                    safetyCounter++;
                }
            }

            int finalCreatedEntries = createdEntries;

            runOnUiThread(() -> {
                btnRunDueEntries.setEnabled(true);
                btnRunDueEntries.setText("Run Due Entries Now");

                Toast.makeText(
                        RecurringActivity.this,
                        finalCreatedEntries == 0
                                ? "No recurring entries are due today"
                                : finalCreatedEntries + " recurring entries added",
                        Toast.LENGTH_LONG
                ).show();

                loadRecurringEntries();
            });
        }).start();
    }

    private String getNextRunDate(String currentDate, String frequency) {
        try {
            Calendar calendar = Calendar.getInstance();

            calendar.setTime(
                    new SimpleDateFormat(
                            "yyyy-MM-dd",
                            Locale.US
                    ).parse(currentDate)
            );

            if (frequency.equalsIgnoreCase("Daily")) {
                calendar.add(Calendar.DAY_OF_MONTH, 1);
            } else if (frequency.equalsIgnoreCase("Weekly")) {
                calendar.add(Calendar.DAY_OF_MONTH, 7);
            } else if (frequency.equalsIgnoreCase("Yearly")) {
                calendar.add(Calendar.YEAR, 1);
            } else {
                calendar.add(Calendar.MONTH, 1);
            }

            return new SimpleDateFormat(
                    "yyyy-MM-dd",
                    Locale.US
            ).format(calendar.getTime());

        } catch (Exception exception) {
            return currentDate;
        }
    }

    private String formatAmount(double amount) {
        return String.format(
                new Locale("en", "IN"),
                "₹%,.2f",
                amount
        );
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return (int) (dp * density);
    }
}