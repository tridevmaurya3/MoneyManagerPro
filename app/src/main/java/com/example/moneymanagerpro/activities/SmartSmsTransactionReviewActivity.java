package com.example.moneymanagerpro.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.bridge.SmartSmsBridgeContract;
import com.example.moneymanagerpro.database.AppDatabase;
import com.example.moneymanagerpro.database.DatabaseClient;
import com.example.moneymanagerpro.model.Account;
import com.example.moneymanagerpro.model.Category;
import com.example.moneymanagerpro.model.Transaction;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SmartSmsTransactionReviewActivity extends AppCompatActivity {

    private static final String IMPORT_PREFS = "smart_sms_bridge_imports_v1";

    private TextInputLayout inputAmount;
    private TextInputEditText etAmount;
    private TextInputEditText etDate;
    private TextInputEditText etNote;
    private MaterialAutoCompleteTextView dropdownType;
    private MaterialAutoCompleteTextView dropdownCategory;
    private MaterialAutoCompleteTextView dropdownAccount;
    private TextView txtSource;
    private TextView txtDuplicate;
    private MaterialButton btnSave;

    private final List<String> expenseCategories = new ArrayList<>();
    private final List<String> incomeCategories = new ArrayList<>();
    private final List<String> accountNames = new ArrayList<>();

    private String fingerprint;
    private String sender;
    private String method;
    private String sourceBody;
    private String reference;
    private String suggestedCategory;
    private long sourceTimestamp;
    private boolean duplicate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!isValidBridgeIntent(getIntent())) {
            Toast.makeText(this, R.string.bridge_invalid_request, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        setContentView(R.layout.activity_smart_sms_transaction_review);
        bindViews();
        readPayload();
        populatePayload();
        setupActions();
        loadOptions();
        checkDuplicate();
    }

    private boolean isValidBridgeIntent(Intent intent) {
        if (intent == null || !SmartSmsBridgeContract.ACTION_REVIEW_TRANSACTION.equals(intent.getAction())) {
            return false;
        }
        String source = intent.getStringExtra(SmartSmsBridgeContract.EXTRA_SOURCE_PACKAGE);
        String direction = intent.getStringExtra(SmartSmsBridgeContract.EXTRA_DIRECTION);
        double amount = intent.getDoubleExtra(SmartSmsBridgeContract.EXTRA_AMOUNT, 0d);
        String bridgeFingerprint = intent.getStringExtra(SmartSmsBridgeContract.EXTRA_FINGERPRINT);
        return SmartSmsBridgeContract.TRUSTED_SOURCE_PACKAGE.equals(source)
                && amount > 0d
                && bridgeFingerprint != null
                && !bridgeFingerprint.trim().isEmpty()
                && (SmartSmsBridgeContract.DIRECTION_DEBIT.equals(direction)
                || SmartSmsBridgeContract.DIRECTION_CREDIT.equals(direction));
    }

    private void bindViews() {
        inputAmount = findViewById(R.id.bridgeInputAmount);
        etAmount = findViewById(R.id.bridgeAmount);
        etDate = findViewById(R.id.bridgeDate);
        etNote = findViewById(R.id.bridgeNote);
        dropdownType = findViewById(R.id.bridgeType);
        dropdownCategory = findViewById(R.id.bridgeCategory);
        dropdownAccount = findViewById(R.id.bridgeAccount);
        txtSource = findViewById(R.id.bridgeSourceMessage);
        txtDuplicate = findViewById(R.id.bridgeDuplicateWarning);
        btnSave = findViewById(R.id.bridgeSave);
    }

    private void readPayload() {
        Intent intent = getIntent();
        fingerprint = safe(intent.getStringExtra(SmartSmsBridgeContract.EXTRA_FINGERPRINT));
        sender = safe(intent.getStringExtra(SmartSmsBridgeContract.EXTRA_SENDER));
        method = safe(intent.getStringExtra(SmartSmsBridgeContract.EXTRA_METHOD));
        sourceBody = safe(intent.getStringExtra(SmartSmsBridgeContract.EXTRA_BODY));
        reference = safe(intent.getStringExtra(SmartSmsBridgeContract.EXTRA_REFERENCE));
        suggestedCategory = safe(intent.getStringExtra(SmartSmsBridgeContract.EXTRA_CATEGORY));
        sourceTimestamp = intent.getLongExtra(
                SmartSmsBridgeContract.EXTRA_TIMESTAMP,
                System.currentTimeMillis());
    }

    private void populatePayload() {
        Intent intent = getIntent();
        String direction = intent.getStringExtra(SmartSmsBridgeContract.EXTRA_DIRECTION);
        String type = SmartSmsBridgeContract.DIRECTION_CREDIT.equals(direction) ? "INCOME" : "EXPENSE";
        double amount = intent.getDoubleExtra(SmartSmsBridgeContract.EXTRA_AMOUNT, 0d);

        dropdownType.setAdapter(new ArrayAdapter<>(
                this,
                android.R.layout.simple_dropdown_item_1line,
                new String[]{"EXPENSE", "INCOME"}));
        dropdownType.setText(type, false);
        dropdownType.setOnItemClickListener((parent, view, position, id) -> applyCategoryOptions());

        etAmount.setText(formatAmount(amount));
        etDate.setText(new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                .format(new Date(sourceTimestamp)));

        StringBuilder note = new StringBuilder();
        note.append("Smart SMS");
        if (!method.isEmpty()) note.append(" • ").append(method);
        if (!sender.isEmpty()) note.append(" • ").append(sender);
        if (!reference.isEmpty()) note.append(" • Ref: ").append(reference);
        etNote.setText(note.toString());

        String sourceLabel = sourceBody.isEmpty()
                ? getString(R.string.bridge_source_unavailable)
                : sourceBody;
        txtSource.setText(sourceLabel);
    }

    private void setupActions() {
        findViewById(R.id.bridgeBack).setOnClickListener(v -> finish());
        btnSave.setOnClickListener(v -> saveReviewedTransaction());
    }

    private void loadOptions() {
        btnSave.setEnabled(false);
        new Thread(() -> {
            AppDatabase database = DatabaseClient
                    .getInstance(getApplicationContext())
                    .getAppDatabase();
            List<Category> categories = database.categoryDao().getAllCategories();
            List<Account> accounts = database.accountDao().getAllAccounts();

            expenseCategories.clear();
            incomeCategories.clear();
            accountNames.clear();

            for (Category category : categories) {
                if (category == null || category.getName() == null) continue;
                String type = safe(category.getType());
                if (type.equalsIgnoreCase("expense")) expenseCategories.add(category.getName());
                if (type.equalsIgnoreCase("income")) incomeCategories.add(category.getName());
            }
            if (expenseCategories.isEmpty()) {
                expenseCategories.add("Bills");
                expenseCategories.add("Shopping");
                expenseCategories.add("Travel");
                expenseCategories.add("Other Expense");
            }
            if (incomeCategories.isEmpty()) {
                incomeCategories.add("Salary");
                incomeCategories.add("Refund");
                incomeCategories.add("Other Income");
            }

            for (Account account : accounts) {
                if (account != null && account.getName() != null && !account.getName().trim().isEmpty()) {
                    accountNames.add(account.getName().trim());
                }
            }
            if (accountNames.isEmpty()) accountNames.add("Cash");

            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                dropdownAccount.setAdapter(new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_dropdown_item_1line,
                        accountNames));
                dropdownAccount.setText(bestAccount(), false);
                applyCategoryOptions();
                btnSave.setEnabled(!duplicate);
            });
        }, "SmartSmsBridgeOptions").start();
    }

    private void applyCategoryOptions() {
        boolean income = "INCOME".equalsIgnoreCase(textOf(dropdownType));
        List<String> values = income ? incomeCategories : expenseCategories;
        dropdownCategory.setAdapter(new ArrayAdapter<>(
                this,
                android.R.layout.simple_dropdown_item_1line,
                values));

        String selected = bestCategory(values, income);
        dropdownCategory.setText(selected, false);
    }

    private String bestCategory(List<String> values, boolean income) {
        if (!suggestedCategory.isEmpty()) {
            for (String value : values) {
                if (value.equalsIgnoreCase(suggestedCategory)) return value;
            }
        }
        String preferred = income ? "Other Income" : "Other Expense";
        for (String value : values) {
            if (value.equalsIgnoreCase(preferred)) return value;
        }
        return values.isEmpty() ? preferred : values.get(0);
    }

    private String bestAccount() {
        String senderLower = sender.toLowerCase(Locale.ROOT);
        for (String account : accountNames) {
            String normalized = account.toLowerCase(Locale.ROOT);
            if ((!senderLower.isEmpty() && (normalized.contains(senderLower) || senderLower.contains(normalized)))
                    || (!method.isEmpty() && normalized.contains(method.toLowerCase(Locale.ROOT)))) {
                return account;
            }
        }
        for (String account : accountNames) {
            if (account.equalsIgnoreCase("Cash")) return account;
        }
        return accountNames.get(0);
    }

    private void checkDuplicate() {
        SharedPreferences preferences = getSharedPreferences(IMPORT_PREFS, MODE_PRIVATE);
        duplicate = preferences.getBoolean(fingerprint, false);
        txtDuplicate.setVisibility(duplicate ? View.VISIBLE : View.GONE);
        btnSave.setEnabled(!duplicate && !accountNames.isEmpty());
    }

    private void saveReviewedTransaction() {
        if (duplicate) {
            Toast.makeText(this, R.string.bridge_duplicate_blocked, Toast.LENGTH_LONG).show();
            return;
        }

        String type = textOf(dropdownType).toUpperCase(Locale.ROOT);
        String amountText = textOf(etAmount).replace(",", "");
        String category = textOf(dropdownCategory);
        String account = textOf(dropdownAccount);
        String date = textOf(etDate);
        String note = textOf(etNote);

        if (!type.equals("EXPENSE") && !type.equals("INCOME")) {
            Toast.makeText(this, R.string.bridge_invalid_type, Toast.LENGTH_SHORT).show();
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(amountText);
        } catch (Exception exception) {
            amount = 0d;
        }
        if (amount <= 0d) {
            inputAmount.setError(getString(R.string.bridge_invalid_amount));
            return;
        }
        inputAmount.setError(null);
        if (category.isEmpty() || account.isEmpty() || date.isEmpty()) {
            Toast.makeText(this, R.string.bridge_complete_fields, Toast.LENGTH_SHORT).show();
            return;
        }

        Transaction transaction = new Transaction();
        transaction.setType(type);
        transaction.setAmount(amount);
        transaction.setCategory(category);
        transaction.setAccount(account);
        transaction.setDate(date);
        transaction.setNote(note);

        btnSave.setEnabled(false);
        btnSave.setText(R.string.bridge_saving);

        new Thread(() -> {
            try {
                long id = DatabaseClient
                        .getInstance(getApplicationContext())
                        .getAppDatabase()
                        .transactionDao()
                        .insert(transaction);
                if (id <= 0L) throw new IllegalStateException("Insert failed");

                getSharedPreferences(IMPORT_PREFS, MODE_PRIVATE)
                        .edit()
                        .putBoolean(fingerprint, true)
                        .apply();

                runOnUiThread(() -> {
                    Toast.makeText(this, R.string.bridge_saved, Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(this, TransactionsActivity.class));
                    finish();
                });
            } catch (Exception exception) {
                runOnUiThread(() -> {
                    btnSave.setEnabled(true);
                    btnSave.setText(R.string.bridge_review_save);
                    Toast.makeText(this, R.string.bridge_save_failed, Toast.LENGTH_LONG).show();
                });
            }
        }, "SmartSmsBridgeSave").start();
    }

    private String formatAmount(double amount) {
        return String.format(Locale.US, amount % 1d == 0d ? "%.0f" : "%.2f", amount);
    }

    private String textOf(TextInputEditText field) {
        return field.getText() == null ? "" : field.getText().toString().trim();
    }

    private String textOf(MaterialAutoCompleteTextView field) {
        return field.getText() == null ? "" : field.getText().toString().trim();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
