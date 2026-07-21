package com.example.moneymanagerpro.activities;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.database.DatabaseClient;
import com.example.moneymanagerpro.model.Account;
import com.example.moneymanagerpro.model.Category;
import com.example.moneymanagerpro.model.Transaction;
import com.example.moneymanagerpro.utils.ReceiptStore;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class AddExpenseActivity extends AppCompatActivity {

    private TextInputLayout inputAmount;
    private TextInputEditText etAmount;
    private TextInputEditText etDate;
    private TextInputEditText etNote;

    private MaterialAutoCompleteTextView dropdownCategory;
    private MaterialAutoCompleteTextView dropdownAccount;

    private MaterialButton btnAttachReceipt;
    private MaterialButton btnRemoveReceipt;
    private MaterialButton btnSaveExpense;

    private ImageView imgReceiptPreview;
    private FrameLayout receiptPreviewContainer;

    private Calendar selectedCalendar;
    private String selectedDate;
    private Uri selectedReceiptUri;

    private final ActivityResultLauncher<String[]> receiptPicker =
            registerForActivityResult(
                    new ActivityResultContracts.OpenDocument(),
                    uri -> {
                        if (uri == null) {
                            return;
                        }

                        try {
                            getContentResolver().takePersistableUriPermission(
                                    uri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                            );
                        } catch (Exception ignored) {
                        }

                        showReceiptPreview(uri);
                    }
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_expense);

        inputAmount = findViewById(R.id.inputAmount);
        etAmount = findViewById(R.id.etAmount);
        etDate = findViewById(R.id.etDate);
        etNote = findViewById(R.id.etNote);

        dropdownCategory = findViewById(R.id.dropdownCategory);
        dropdownAccount = findViewById(R.id.dropdownAccount);

        btnAttachReceipt = findViewById(R.id.btnAttachReceipt);
        btnRemoveReceipt = findViewById(R.id.btnRemoveReceipt);
        btnSaveExpense = findViewById(R.id.btnSaveExpense);

        imgReceiptPreview = findViewById(R.id.imgReceiptPreview);
        receiptPreviewContainer = findViewById(R.id.receiptPreviewContainer);

        TextView btnBack = findViewById(R.id.btnBack);

        selectedCalendar = Calendar.getInstance();
        updateDateField();

        btnBack.setOnClickListener(v -> finish());

        etDate.setOnClickListener(v -> showDatePicker());

        btnAttachReceipt.setOnClickListener(v ->
                receiptPicker.launch(new String[]{"image/*"})
        );

        btnRemoveReceipt.setOnClickListener(v -> clearReceiptPreview());

        btnSaveExpense.setOnClickListener(v -> saveExpense());

        loadFormOptions();
    }

    private void showReceiptPreview(Uri uri) {
        selectedReceiptUri = uri;

        imgReceiptPreview.setImageURI(uri);
        receiptPreviewContainer.setVisibility(View.VISIBLE);

        btnAttachReceipt.setText("Change Bill Photo");
    }

    private void clearReceiptPreview() {
        selectedReceiptUri = null;

        imgReceiptPreview.setImageDrawable(null);
        receiptPreviewContainer.setVisibility(View.GONE);

        btnAttachReceipt.setText("Choose Bill Photo");
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

            List<String> expenseCategories = new ArrayList<>();
            List<String> accountNames = new ArrayList<>();

            for (Category category : categories) {
                String type = category.getType();

                if (type != null && type.equalsIgnoreCase("expense")) {
                    expenseCategories.add(category.getName());
                }
            }

            if (expenseCategories.isEmpty()) {
                expenseCategories.add("Food");
                expenseCategories.add("Travel");
                expenseCategories.add("Shopping");
                expenseCategories.add("Bills");
                expenseCategories.add("Other Expense");
            }

            for (Account account : accounts) {
                accountNames.add(account.getName());
            }

            if (accountNames.isEmpty()) {
                accountNames.add("Cash");
            }

            runOnUiThread(() -> {
                setDropdownData(dropdownCategory, expenseCategories, "Food");
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

    private void saveExpense() {
        String amountText = etAmount.getText() == null
                ? ""
                : etAmount.getText().toString().trim();

        if (amountText.isEmpty()) {
            inputAmount.setError("Please enter expense amount");
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

        String receiptUri = selectedReceiptUri == null
                ? ""
                : selectedReceiptUri.toString();

        Transaction transaction = new Transaction();
        transaction.setType("EXPENSE");
        transaction.setAmount(amount);
        transaction.setCategory(category);
        transaction.setAccount(account);
        transaction.setNote(note);
        transaction.setDate(selectedDate);

        btnSaveExpense.setEnabled(false);
        btnSaveExpense.setText("Saving Expense...");

        new Thread(() -> {
            try {
                long transactionId = DatabaseClient
                        .getInstance(getApplicationContext())
                        .getAppDatabase()
                        .transactionDao()
                        .insert(transaction);

                if (!receiptUri.isEmpty()) {
                    ReceiptStore.saveReceiptUri(
                            getApplicationContext(),
                            transactionId,
                            receiptUri
                    );
                }

                runOnUiThread(() -> {
                    String message = receiptUri.isEmpty()
                            ? "Expense saved successfully"
                            : "Expense and bill photo saved";

                    Toast.makeText(
                            AddExpenseActivity.this,
                            message,
                            Toast.LENGTH_SHORT
                    ).show();

                    finish();
                });
            } catch (Exception exception) {
                runOnUiThread(() -> {
                    btnSaveExpense.setEnabled(true);
                    btnSaveExpense.setText("Save Expense");

                    Toast.makeText(
                            AddExpenseActivity.this,
                            "Unable to save expense",
                            Toast.LENGTH_SHORT
                    ).show();
                });
            }
        }).start();
    }
}