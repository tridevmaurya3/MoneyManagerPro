package com.example.moneymanagerpro.activities;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.database.DatabaseClient;
import com.example.moneymanagerpro.model.Account;
import com.example.moneymanagerpro.model.Category;
import com.example.moneymanagerpro.model.Transaction;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class AddIncomeActivity extends AppCompatActivity {

    private TextInputLayout inputAmount;
    private TextInputEditText etAmount;
    private TextInputEditText etDate;
    private TextInputEditText etNote;
    private MaterialAutoCompleteTextView dropdownCategory;
    private MaterialAutoCompleteTextView dropdownAccount;
    private MaterialButton btnSaveIncome;

    private Calendar selectedCalendar;
    private String selectedDate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_income);

        inputAmount = findViewById(R.id.inputAmount);
        etAmount = findViewById(R.id.etAmount);
        etDate = findViewById(R.id.etDate);
        etNote = findViewById(R.id.etNote);
        dropdownCategory = findViewById(R.id.dropdownCategory);
        dropdownAccount = findViewById(R.id.dropdownAccount);
        btnSaveIncome = findViewById(R.id.btnSaveIncome);

        TextView btnBack = findViewById(R.id.btnBack);

        selectedCalendar = Calendar.getInstance();
        updateDateField();

        btnBack.setOnClickListener(v -> finish());

        etDate.setOnClickListener(v -> showDatePicker());

        btnSaveIncome.setOnClickListener(v -> saveIncome());

        loadFormOptions();
    }

    private void loadFormOptions() {
        new Thread(() -> {
            List<Category> categories = DatabaseClient
                    .getInstance(getApplicationContext())
                    .getAppDatabase()
                    .categoryDao()
                    .getAllCategories();

            List<Account> accounts = DatabaseClient
                    .getInstance(getApplicationContext())
                    .getAppDatabase()
                    .accountDao()
                    .getAllAccounts();

            List<String> incomeCategories = new ArrayList<>();
            List<String> accountNames = new ArrayList<>();

            for (Category category : categories) {
                String type = category.getType();

                if (type != null && type.equalsIgnoreCase("income")) {
                    incomeCategories.add(category.getName());
                }
            }

            if (incomeCategories.isEmpty()) {
                incomeCategories.add("Salary");
                incomeCategories.add("Business");
                incomeCategories.add("Freelancing");
                incomeCategories.add("Interest");
                incomeCategories.add("Other Income");
            }

            for (Account account : accounts) {
                accountNames.add(account.getName());
            }

            if (accountNames.isEmpty()) {
                accountNames.add("Cash");
            }

            runOnUiThread(() -> {
                setDropdownData(dropdownCategory, incomeCategories, "Salary");
                setDropdownData(dropdownAccount, accountNames, "Cash");
            });
        }).start();
    }

    private void setDropdownData(
            MaterialAutoCompleteTextView dropdown,
            List<String> values,
            String preferredValue
    ) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                values
        );

        dropdown.setAdapter(adapter);

        String selectedValue = values.get(0);

        for (String value : values) {
            if (value.equalsIgnoreCase(preferredValue)) {
                selectedValue = value;
                break;
            }
        }

        dropdown.setText(selectedValue, false);
    }

    private void showDatePicker() {
        DatePickerDialog dialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    selectedCalendar.set(Calendar.YEAR, year);
                    selectedCalendar.set(Calendar.MONTH, month);
                    selectedCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);

                    updateDateField();
                },
                selectedCalendar.get(Calendar.YEAR),
                selectedCalendar.get(Calendar.MONTH),
                selectedCalendar.get(Calendar.DAY_OF_MONTH)
        );

        dialog.show();
    }

    private void updateDateField() {
        selectedDate = new SimpleDateFormat(
                "yyyy-MM-dd HH:mm",
                Locale.US
        ).format(selectedCalendar.getTime());

        String visibleDate = new SimpleDateFormat(
                "dd MMMM yyyy",
                Locale.ENGLISH
        ).format(selectedCalendar.getTime());

        etDate.setText(visibleDate);
    }

    private void saveIncome() {
        String amountText = etAmount.getText() == null
                ? ""
                : etAmount.getText().toString().trim();

        if (amountText.isEmpty()) {
            inputAmount.setError("Please enter income amount");
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

        String category = dropdownCategory.getText().toString().trim();
        String account = dropdownAccount.getText().toString().trim();
        String note = etNote.getText() == null
                ? ""
                : etNote.getText().toString().trim();

        Transaction transaction = new Transaction();
        transaction.setType("INCOME");
        transaction.setAmount(amount);
        transaction.setCategory(category);
        transaction.setAccount(account);
        transaction.setNote(note);
        transaction.setDate(selectedDate);

        btnSaveIncome.setEnabled(false);
        btnSaveIncome.setText("Saving Income...");

        new Thread(() -> {
            try {
                DatabaseClient.getInstance(getApplicationContext())
                        .getAppDatabase()
                        .transactionDao()
                        .insert(transaction);

                runOnUiThread(() -> {
                    Toast.makeText(
                            AddIncomeActivity.this,
                            "Income saved successfully",
                            Toast.LENGTH_SHORT
                    ).show();

                    finish();
                });
            } catch (Exception exception) {
                runOnUiThread(() -> {
                    btnSaveIncome.setEnabled(true);
                    btnSaveIncome.setText("Save Income");

                    Toast.makeText(
                            AddIncomeActivity.this,
                            "Unable to save income",
                            Toast.LENGTH_SHORT
                    ).show();
                });
            }
        }).start();
    }
}