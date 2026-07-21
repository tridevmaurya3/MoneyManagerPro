package com.example.moneymanagerpro.activities;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.model.Account;
import com.example.moneymanagerpro.model.Category;
import com.example.moneymanagerpro.model.Transaction;
import com.example.moneymanagerpro.repository.AccountRepository;
import com.example.moneymanagerpro.repository.CategoryRepository;
import com.example.moneymanagerpro.repository.TransactionRepository;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class EditTransactionActivity extends AppCompatActivity {

    private EditText etAmount;
    private EditText etNote;

    private Spinner spinnerType;
    private Spinner spinnerCategory;
    private Spinner spinnerAccount;

    private TextView txtSelectedDate;
    private TextView txtCategoryMessage;
    private TextView txtAccountMessage;

    private Button btnSelectDate;
    private Button btnUpdate;

    private TransactionRepository transactionRepository;
    private CategoryRepository categoryRepository;
    private AccountRepository accountRepository;

    private Calendar selectedDate;

    private int transactionId;
    private String selectedCategoryName;
    private String selectedAccountName;

    private boolean categoryReady = false;
    private boolean accountReady = false;

    private final List<Category> categories = new ArrayList<>();
    private final List<Account> accounts = new ArrayList<>();

    private ArrayAdapter<String> categoryAdapter;
    private ArrayAdapter<String> accountAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_transaction);

        etAmount = findViewById(R.id.etEditAmount);
        etNote = findViewById(R.id.etEditNote);

        spinnerType = findViewById(R.id.spinnerEditType);
        spinnerCategory = findViewById(R.id.spinnerEditCategory);
        spinnerAccount = findViewById(R.id.spinnerEditAccount);

        txtSelectedDate = findViewById(R.id.txtSelectedEditDate);
        txtCategoryMessage = findViewById(R.id.txtEditCategoryMessage);
        txtAccountMessage = findViewById(R.id.txtEditAccountMessage);

        btnSelectDate = findViewById(R.id.btnSelectEditDate);
        btnUpdate = findViewById(R.id.btnUpdateTransaction);

        transactionRepository = new TransactionRepository(this);
        categoryRepository = new CategoryRepository(this);
        accountRepository = new AccountRepository(this);

        readTransactionData();
        setupTypeSpinner();
        setupCategorySpinner();
        setupAccountSpinner();

        btnSelectDate.setOnClickListener(view -> showDatePicker());
        btnUpdate.setOnClickListener(view -> updateTransaction());
    }

    private void readTransactionData() {
        transactionId = getIntent().getIntExtra("transaction_id", 0);

        String type = getIntent().getStringExtra("transaction_type");
        String category = getIntent().getStringExtra("transaction_category");
        String account = getIntent().getStringExtra("transaction_account");
        String note = getIntent().getStringExtra("transaction_note");
        String date = getIntent().getStringExtra("transaction_date");

        double amount = getIntent().getDoubleExtra(
                "transaction_amount",
                0
        );

        selectedCategoryName = category == null ? "" : category;
        selectedAccountName = account == null ? "Cash" : account;

        etAmount.setText(String.valueOf(amount));
        etNote.setText(note == null ? "" : note);

        selectedDate = Calendar.getInstance();

        if (date != null && !date.isEmpty()) {
            try {
                selectedDate.setTime(
                        new SimpleDateFormat(
                                "yyyy-MM-dd HH:mm",
                                Locale.getDefault()
                        ).parse(date)
                );
            } catch (ParseException exception) {
                selectedDate = Calendar.getInstance();
            }
        }

        updateSelectedDateText();

        if (type == null || !type.equals("EXPENSE")) {
            spinnerType.setTag("INCOME");
        } else {
            spinnerType.setTag("EXPENSE");
        }
    }

    private void setupTypeSpinner() {
        String[] types = {"Income", "Expense"};

        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                types
        );

        typeAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        spinnerType.setAdapter(typeAdapter);

        String savedType = String.valueOf(spinnerType.getTag());

        if (savedType.equals("EXPENSE")) {
            spinnerType.setSelection(1);
        } else {
            spinnerType.setSelection(0);
        }

        spinnerType.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {

                    @Override
                    public void onItemSelected(
                            AdapterView<?> parent,
                            View view,
                            int position,
                            long id
                    ) {
                        loadCategoriesForSelectedType();
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                    }
                }
        );
    }

    private void setupCategorySpinner() {
        categoryAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                new ArrayList<>()
        );

        categoryAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        spinnerCategory.setAdapter(categoryAdapter);

        loadCategoriesForSelectedType();
    }

    private void setupAccountSpinner() {
        accountAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                new ArrayList<>()
        );

        accountAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        spinnerAccount.setAdapter(accountAdapter);

        loadAccounts();
    }

    private void loadCategoriesForSelectedType() {
        if (categoryAdapter == null) {
            return;
        }

        categoryReady = false;
        updateSaveButtonState();

        String requiredType = getSelectedType();

        categoryRepository.getCategoriesByType(requiredType, result -> {
            if (!requiredType.equals(getSelectedType())) {
                return;
            }

            categories.clear();
            categories.addAll(result);

            categoryAdapter.clear();

            int matchingPosition = 0;

            for (int index = 0; index < result.size(); index++) {
                Category category = result.get(index);

                categoryAdapter.add(category.getName());

                if (category.getName().equals(selectedCategoryName)) {
                    matchingPosition = index;
                }
            }

            categoryAdapter.notifyDataSetChanged();

            if (categories.isEmpty()) {
                txtCategoryMessage.setVisibility(View.VISIBLE);
                categoryReady = false;
            } else {
                txtCategoryMessage.setVisibility(View.GONE);
                spinnerCategory.setSelection(matchingPosition);
                categoryReady = true;
            }

            updateSaveButtonState();
        });
    }

    private void loadAccounts() {
        accountReady = false;
        updateSaveButtonState();

        accountRepository.getAllAccounts(result -> {
            accounts.clear();
            accounts.addAll(result);

            accountAdapter.clear();

            int matchingPosition = 0;
            int cashPosition = 0;
            boolean accountFound = false;

            for (int index = 0; index < result.size(); index++) {
                Account account = result.get(index);

                accountAdapter.add(account.getName());

                if (account.getName().equals(selectedAccountName)) {
                    matchingPosition = index;
                    accountFound = true;
                }

                if ("Cash".equalsIgnoreCase(account.getName())) {
                    cashPosition = index;
                }
            }

            accountAdapter.notifyDataSetChanged();

            if (accounts.isEmpty()) {
                txtAccountMessage.setVisibility(View.VISIBLE);
                accountReady = false;
            } else {
                txtAccountMessage.setVisibility(View.GONE);

                if (accountFound) {
                    spinnerAccount.setSelection(matchingPosition);
                } else {
                    spinnerAccount.setSelection(cashPosition);
                }

                accountReady = true;
            }

            updateSaveButtonState();
        });
    }

    private void updateSaveButtonState() {
        btnUpdate.setEnabled(categoryReady && accountReady);
    }

    private void showDatePicker() {
        int year = selectedDate.get(Calendar.YEAR);
        int month = selectedDate.get(Calendar.MONTH);
        int day = selectedDate.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                (view, selectedYear, selectedMonth, selectedDay) -> {
                    selectedDate.set(
                            selectedYear,
                            selectedMonth,
                            selectedDay
                    );

                    updateSelectedDateText();
                },
                year,
                month,
                day
        );

        datePickerDialog.show();
    }

    private void updateSelectedDateText() {
        String dateText = new SimpleDateFormat(
                "dd MMMM yyyy",
                Locale.getDefault()
        ).format(selectedDate.getTime());

        txtSelectedDate.setText(dateText);
    }

    private void updateTransaction() {
        String amountText = etAmount.getText().toString().trim();
        String note = etNote.getText().toString().trim();

        if (transactionId == 0) {
            Toast.makeText(
                    this,
                    "Transaction नहीं मिला",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        if (categories.isEmpty()) {
            Toast.makeText(
                    this,
                    "पहले इस type की category बनाएँ",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        if (accounts.isEmpty()) {
            Toast.makeText(
                    this,
                    "पहले Account बनाएँ",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        if (TextUtils.isEmpty(amountText)) {
            etAmount.setError("Amount डालें");
            etAmount.requestFocus();
            return;
        }

        double amount;

        try {
            amount = Double.parseDouble(amountText);
        } catch (NumberFormatException exception) {
            etAmount.setError("सही amount डालें");
            etAmount.requestFocus();
            return;
        }

        if (amount <= 0) {
            etAmount.setError("Amount 0 से बड़ा होना चाहिए");
            etAmount.requestFocus();
            return;
        }

        int categoryPosition = spinnerCategory.getSelectedItemPosition();
        int accountPosition = spinnerAccount.getSelectedItemPosition();

        if (categoryPosition < 0 || categoryPosition >= categories.size()) {
            Toast.makeText(
                    this,
                    "Category चुनें",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        if (accountPosition < 0 || accountPosition >= accounts.size()) {
            Toast.makeText(
                    this,
                    "Account चुनें",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        Category selectedCategory = categories.get(categoryPosition);
        Account selectedAccount = accounts.get(accountPosition);

        btnUpdate.setEnabled(false);

        Transaction transaction = new Transaction();
        transaction.setId(transactionId);
        transaction.setType(getSelectedType());
        transaction.setAmount(amount);
        transaction.setCategory(selectedCategory.getName());
        transaction.setAccount(selectedAccount.getName());
        transaction.setNote(note);

        String date = new SimpleDateFormat(
                "yyyy-MM-dd HH:mm",
                Locale.getDefault()
        ).format(selectedDate.getTime());

        transaction.setDate(date);

        transactionRepository.update(transaction, () -> {
            Toast.makeText(
                    EditTransactionActivity.this,
                    "Transaction updated",
                    Toast.LENGTH_SHORT
            ).show();

            finish();
        });
    }

    private String getSelectedType() {
        String type = spinnerType.getSelectedItem().toString();

        if (type.equals("Income")) {
            return "INCOME";
        }

        return "EXPENSE";
    }
}