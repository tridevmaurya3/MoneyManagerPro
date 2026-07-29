package com.example.moneymanagerpro.activities;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.database.DatabaseClient;
import com.example.moneymanagerpro.model.Account;
import com.example.moneymanagerpro.model.Category;
import com.example.moneymanagerpro.model.ExpenseItem;
import com.example.moneymanagerpro.model.Transaction;
import com.example.moneymanagerpro.repository.AccountRepository;
import com.example.moneymanagerpro.repository.CategoryRepository;
import com.example.moneymanagerpro.repository.TransactionRepository;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class EditTransactionActivity extends AppCompatActivity {

    private static final int MAX_ITEM_ROWS = 50;

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
    private MaterialButton btnEditMoreItem;
    private LinearLayout editExpenseItemsSection;
    private LinearLayout editItemDetailsContainer;
    private TextView txtEditItemsTotal;

    private TransactionRepository transactionRepository;
    private CategoryRepository categoryRepository;
    private AccountRepository accountRepository;

    private Calendar selectedDate;

    private int transactionId;

    private String originalTransactionType = "EXPENSE";
    private String selectedCategoryName = "";
    private String selectedAccountName = "Cash";

    private boolean categoryReady = false;
    private boolean accountReady = false;

    private final List<Category> categories =
            new ArrayList<>();

    private final List<Account> accounts =
            new ArrayList<>();
    private final List<View> itemRows =
            new ArrayList<>();

    private ArrayAdapter<String> categoryAdapter;
    private ArrayAdapter<String> accountAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_transaction);

        bindViews();
        initializeRepositories();
        readTransactionData();

        setupCategorySpinner();
        setupAccountSpinner();
        setupTypeSpinner();
        loadExistingExpenseItems();

        btnSelectDate.setOnClickListener(
                view -> showDatePicker()
        );

        btnUpdate.setOnClickListener(
                view -> updateTransaction()
        );
    }

    private void bindViews() {
        etAmount =
                findViewById(R.id.etEditAmount);

        etNote =
                findViewById(R.id.etEditNote);

        spinnerType =
                findViewById(R.id.spinnerEditType);

        spinnerCategory =
                findViewById(R.id.spinnerEditCategory);

        spinnerAccount =
                findViewById(R.id.spinnerEditAccount);

        txtSelectedDate =
                findViewById(R.id.txtSelectedEditDate);

        txtCategoryMessage =
                findViewById(R.id.txtEditCategoryMessage);

        txtAccountMessage =
                findViewById(R.id.txtEditAccountMessage);

        btnSelectDate =
                findViewById(R.id.btnSelectEditDate);

        btnUpdate =
                findViewById(R.id.btnUpdateTransaction);

        btnEditMoreItem =
                findViewById(R.id.btnEditMoreItem);
        editExpenseItemsSection =
                findViewById(R.id.editExpenseItemsSection);
        editItemDetailsContainer =
                findViewById(R.id.editItemDetailsContainer);
        txtEditItemsTotal =
                findViewById(R.id.txtEditItemsTotal);

        btnEditMoreItem.setOnClickListener(
                view -> addItemRow(null)
        );
    }

    private void initializeRepositories() {
        transactionRepository =
                new TransactionRepository(this);

        categoryRepository =
                new CategoryRepository(this);

        accountRepository =
                new AccountRepository(this);
    }

    private void readTransactionData() {
        transactionId =
                getIntent().getIntExtra(
                        "transaction_id",
                        getIntent().getIntExtra(
                                "id",
                                0
                        )
                );

        String type =
                getStringExtra(
                        "transaction_type",
                        "type"
                );

        String category =
                getStringExtra(
                        "transaction_category",
                        "category"
                );

        String account =
                getStringExtra(
                        "transaction_account",
                        "account"
                );

        String note =
                getStringExtra(
                        "transaction_note",
                        "note"
                );

        String date =
                getStringExtra(
                        "transaction_date",
                        "date"
                );

        double amount;

        if (getIntent().hasExtra(
                "transaction_amount"
        )) {
            amount = getIntent().getDoubleExtra(
                    "transaction_amount",
                    0
            );
        } else {
            amount = getIntent().getDoubleExtra(
                    "amount",
                    0
            );
        }

        originalTransactionType =
                normalizeDatabaseType(type);

        selectedCategoryName =
                category == null
                        ? ""
                        : category.trim();

        selectedAccountName =
                account == null
                        || account.trim().isEmpty()
                        ? "Cash"
                        : account.trim();

        etAmount.setText(
                formatPlainAmount(amount)
        );

        etNote.setText(
                note == null
                        ? ""
                        : note
        );

        selectedDate =
                parseSavedDate(date);

        updateSelectedDateText();
    }

    private String getStringExtra(
            String primaryKey,
            String fallbackKey
    ) {
        String value =
                getIntent().getStringExtra(
                        primaryKey
                );

        if (value == null) {
            value = getIntent().getStringExtra(
                    fallbackKey
            );
        }

        return value;
    }

    private Calendar parseSavedDate(
            String dateText
    ) {
        Calendar calendar =
                Calendar.getInstance();

        if (dateText == null
                || dateText.trim().isEmpty()) {
            return calendar;
        }

        String[] formats = {
                "yyyy-MM-dd HH:mm",
                "yyyy-MM-dd"
        };

        for (String format : formats) {
            try {
                SimpleDateFormat dateFormat =
                        new SimpleDateFormat(
                                format,
                                Locale.US
                        );

                dateFormat.setLenient(false);

                Date parsedDate =
                        dateFormat.parse(
                                dateText.trim()
                        );

                if (parsedDate != null) {
                    calendar.setTime(parsedDate);
                    return calendar;
                }

            } catch (ParseException ignored) {
            }
        }

        return calendar;
    }

    private void setupCategorySpinner() {
        categoryAdapter =
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_spinner_item,
                        new ArrayList<>()
                );

        categoryAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        spinnerCategory.setAdapter(
                categoryAdapter
        );
    }

    private void setupAccountSpinner() {
        accountAdapter =
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_spinner_item,
                        new ArrayList<>()
                );

        accountAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        spinnerAccount.setAdapter(
                accountAdapter
        );

        loadAccounts();
    }

    private void setupTypeSpinner() {
        String[] types = {
                "Income",
                "Expense"
        };

        ArrayAdapter<String> typeAdapter =
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_spinner_item,
                        types
                );

        typeAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        spinnerType.setAdapter(
                typeAdapter
        );

        if (originalTransactionType.equals(
                "INCOME"
        )) {
            spinnerType.setSelection(
                    0,
                    false
            );
        } else {
            spinnerType.setSelection(
                    1,
                    false
            );
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
                        updateItemSectionVisibility();
                    }

                    @Override
                    public void onNothingSelected(
                            AdapterView<?> parent
                    ) {
                    }
                }
        );

        loadCategoriesForSelectedType();
        updateItemSectionVisibility();
    }

    private void loadExistingExpenseItems() {
        new Thread(() -> {
            List<ExpenseItem> items =
                    DatabaseClient
                            .getInstance(
                                    getApplicationContext()
                            )
                            .getAppDatabase()
                            .expenseItemDao()
                            .getItemsForTransaction(
                                    transactionId
                            );

            runOnUiThread(() -> {
                itemRows.clear();
                editItemDetailsContainer
                        .removeAllViews();

                if (items == null
                        || items.isEmpty()) {
                    addItemRow(null);
                } else {
                    for (ExpenseItem item : items) {
                        addItemRow(item);
                    }
                }

                updateItemsSummary();
            });
        }).start();
    }

    private void updateItemSectionVisibility() {
        if (editExpenseItemsSection == null) {
            return;
        }

        editExpenseItemsSection.setVisibility(
                "EXPENSE".equals(
                        getSelectedDatabaseType()
                )
                        ? View.VISIBLE
                        : View.GONE
        );
    }

    private void addItemRow(ExpenseItem existingItem) {
        if (itemRows.size() >= MAX_ITEM_ROWS) {
            Toast.makeText(
                    this,
                    "A maximum of 50 items can be added",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        View row = LayoutInflater.from(this).inflate(
                R.layout.item_expense_detail,
                editItemDetailsContainer,
                false
        );

        TextInputEditText name =
                row.findViewById(R.id.etItemName);
        TextInputEditText quantity =
                row.findViewById(R.id.etItemQuantity);
        TextInputEditText unit =
                row.findViewById(R.id.etItemUnit);
        TextInputEditText price =
                row.findViewById(R.id.etItemPrice);
        TextInputEditText total =
                row.findViewById(R.id.etItemTotal);
        MaterialButton remove =
                row.findViewById(R.id.btnRemoveItem);

        name.setSaveEnabled(false);
        quantity.setSaveEnabled(false);
        unit.setSaveEnabled(false);
        price.setSaveEnabled(false);
        total.setSaveEnabled(false);

        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(
                    CharSequence sequence,
                    int start,
                    int count,
                    int after
            ) {
            }

            @Override
            public void onTextChanged(
                    CharSequence sequence,
                    int start,
                    int before,
                    int count
            ) {
                updateItemTotal(row);
            }

            @Override
            public void afterTextChanged(
                    Editable editable
            ) {
            }
        };

        quantity.addTextChangedListener(watcher);
        price.addTextChangedListener(watcher);

        remove.setOnClickListener(view -> {
            itemRows.remove(row);
            editItemDetailsContainer.removeView(row);
            renumberItemRows();
            updateItemsSummary();
        });

        itemRows.add(row);
        editItemDetailsContainer.addView(row);

        if (existingItem != null) {
            name.setText(existingItem.getItemName());
            quantity.setText(
                    formatPlainAmount(
                            existingItem.getQuantity()
                    )
            );
            unit.setText(existingItem.getUnit());
            price.setText(
                    formatPlainAmount(
                            existingItem.getPrice()
                    )
            );
        }

        renumberItemRows();
        updateItemTotal(row);
    }

    private void renumberItemRows() {
        for (int index = 0;
             index < itemRows.size();
             index++) {
            TextView number =
                    itemRows.get(index)
                            .findViewById(
                                    R.id.txtItemNumber
                            );
            number.setText(
                    getString(
                            R.string.expense_item_number,
                            index + 1
                    )
            );
        }

        btnEditMoreItem.setEnabled(
                itemRows.size() < MAX_ITEM_ROWS
        );
    }

    private void updateItemTotal(View row) {
        BigDecimal quantity =
                parsePositiveDecimal(
                        itemText(
                                row,
                                R.id.etItemQuantity
                        )
                );
        BigDecimal price =
                parsePositiveDecimal(
                        itemText(
                                row,
                                R.id.etItemPrice
                        )
                );
        BigDecimal total =
                quantity != null
                        && price != null
                        ? quantity.multiply(price)
                        .setScale(
                                2,
                                RoundingMode.HALF_UP
                        )
                        : BigDecimal.ZERO;

        TextInputEditText totalField =
                row.findViewById(R.id.etItemTotal);
        totalField.setText(
                formatMoney(total)
        );
        updateItemsSummary();
    }

    private void updateItemsSummary() {
        BigDecimal total = BigDecimal.ZERO;

        for (View row : itemRows) {
            BigDecimal quantity =
                    parsePositiveDecimal(
                            itemText(
                                    row,
                                    R.id.etItemQuantity
                            )
                    );
            BigDecimal price =
                    parsePositiveDecimal(
                            itemText(
                                    row,
                                    R.id.etItemPrice
                            )
                    );

            if (quantity != null
                    && price != null) {
                total = total.add(
                        quantity.multiply(price)
                );
            }
        }

        txtEditItemsTotal.setText(
                getString(
                        R.string.expense_items_total,
                        formatMoney(
                                total.setScale(
                                        2,
                                        RoundingMode.HALF_UP
                                )
                        )
                )
        );
    }

    private List<ExpenseItem> collectExpenseItems() {
        List<ExpenseItem> items =
                new ArrayList<>();

        if (!"EXPENSE".equals(
                getSelectedDatabaseType()
        )) {
            return items;
        }

        for (View row : itemRows) {
            TextInputLayout nameInput =
                    row.findViewById(R.id.inputItemName);
            TextInputLayout quantityInput =
                    row.findViewById(
                            R.id.inputItemQuantity
                    );
            TextInputLayout unitInput =
                    row.findViewById(R.id.inputItemUnit);
            TextInputLayout priceInput =
                    row.findViewById(R.id.inputItemPrice);

            nameInput.setError(null);
            quantityInput.setError(null);
            unitInput.setError(null);
            priceInput.setError(null);

            String name =
                    itemText(row, R.id.etItemName);
            String quantityText =
                    itemText(row, R.id.etItemQuantity);
            String unit =
                    itemText(row, R.id.etItemUnit);
            String priceText =
                    itemText(row, R.id.etItemPrice);

            if (name.isEmpty()
                    && quantityText.isEmpty()
                    && unit.isEmpty()
                    && priceText.isEmpty()) {
                continue;
            }

            BigDecimal quantity =
                    parsePositiveDecimal(quantityText);
            BigDecimal price =
                    parsePositiveDecimal(priceText);

            if (name.isEmpty()) {
                nameInput.setError("Enter item name");
                return null;
            }
            if (quantity == null) {
                quantityInput.setError(
                        "Enter quantity greater than zero"
                );
                return null;
            }
            if (unit.isEmpty()) {
                unitInput.setError(
                        "Enter unit such as kg, litre or piece"
                );
                return null;
            }
            if (price == null) {
                priceInput.setError(
                        "Enter price per unit"
                );
                return null;
            }

            ExpenseItem item = new ExpenseItem();
            item.setTransactionId(transactionId);
            item.setItemName(name);
            item.setQuantity(quantity.doubleValue());
            item.setUnit(unit);
            item.setPrice(price.doubleValue());
            item.setTotal(
                    quantity.multiply(price)
                            .setScale(
                                    2,
                                    RoundingMode.HALF_UP
                            )
                            .doubleValue()
            );
            item.setSortOrder(items.size());
            items.add(item);
        }

        return items;
    }

    private String itemText(
            View row,
            int fieldId
    ) {
        TextInputEditText field =
                row.findViewById(fieldId);

        return field.getText() == null
                ? ""
                : field.getText()
                .toString()
                .trim();
    }

    private BigDecimal parsePositiveDecimal(
            String value
    ) {
        try {
            BigDecimal decimal =
                    new BigDecimal(value);

            return decimal.compareTo(
                    BigDecimal.ZERO
            ) > 0
                    ? decimal
                    : null;
        } catch (Exception exception) {
            return null;
        }
    }

    private String formatMoney(BigDecimal value) {
        return String.format(
                Locale.US,
                "%.2f",
                value.doubleValue()
        );
    }

    private void loadCategoriesForSelectedType() {
        if (categoryAdapter == null) {
            return;
        }

        categoryReady = false;
        updateSaveButtonState();

        String selectedDatabaseType =
                getSelectedDatabaseType();

        String repositoryType =
                getSelectedRepositoryType();

        categoryRepository.getCategoriesByType(
                repositoryType,
                result -> {
                    if (!selectedDatabaseType.equals(
                            getSelectedDatabaseType()
                    )) {
                        return;
                    }

                    categories.clear();
                    categoryAdapter.clear();

                    if (result != null) {
                        for (Category category : result) {
                            if (category == null
                                    || category.getName() == null
                                    || category.getName()
                                    .trim()
                                    .isEmpty()) {
                                continue;
                            }

                            categories.add(category);

                            categoryAdapter.add(
                                    category.getName().trim()
                            );
                        }
                    }

                    int matchingPosition =
                            findCategoryPosition(
                                    selectedCategoryName
                            );

                    boolean editingOriginalType =
                            selectedDatabaseType.equals(
                                    originalTransactionType
                            );

                    if (matchingPosition < 0
                            && editingOriginalType
                            && !selectedCategoryName.isEmpty()) {

                        Category oldCategory =
                                new Category();

                        oldCategory.setName(
                                selectedCategoryName
                        );

                        oldCategory.setType(
                                repositoryType
                        );

                        oldCategory.setColor(
                                selectedDatabaseType.equals(
                                        "INCOME"
                                )
                                        ? "#107C10"
                                        : "#C42B1C"
                        );

                        categories.add(
                                0,
                                oldCategory
                        );

                        categoryAdapter.insert(
                                selectedCategoryName,
                                0
                        );

                        matchingPosition = 0;
                    }

                    categoryAdapter.notifyDataSetChanged();

                    if (categories.isEmpty()) {
                        txtCategoryMessage.setText(
                                "इस type की कोई category उपलब्ध नहीं है। पहले Category बनाएँ।"
                        );

                        txtCategoryMessage.setVisibility(
                                View.VISIBLE
                        );

                        spinnerCategory.setEnabled(
                                false
                        );

                        categoryReady = false;

                    } else {
                        txtCategoryMessage.setVisibility(
                                View.GONE
                        );

                        spinnerCategory.setEnabled(
                                true
                        );

                        if (matchingPosition >= 0
                                && matchingPosition
                                < categories.size()) {

                            spinnerCategory.setSelection(
                                    matchingPosition
                            );

                        } else {
                            spinnerCategory.setSelection(
                                    0
                            );
                        }

                        categoryReady = true;
                    }

                    updateSaveButtonState();
                }
        );
    }

    private int findCategoryPosition(
            String categoryName
    ) {
        if (categoryName == null
                || categoryName.trim().isEmpty()) {
            return -1;
        }

        for (int index = 0;
             index < categories.size();
             index++) {

            Category category =
                    categories.get(index);

            if (category.getName() != null
                    && category.getName()
                    .trim()
                    .equalsIgnoreCase(
                            categoryName.trim()
                    )) {
                return index;
            }
        }

        return -1;
    }

    private void loadAccounts() {
        accountReady = false;
        updateSaveButtonState();

        accountRepository.getAllAccounts(
                result -> {
                    accounts.clear();
                    accountAdapter.clear();

                    if (result != null) {
                        for (Account account : result) {
                            if (account == null
                                    || account.getName() == null
                                    || account.getName()
                                    .trim()
                                    .isEmpty()) {
                                continue;
                            }

                            accounts.add(account);

                            accountAdapter.add(
                                    account.getName().trim()
                            );
                        }
                    }

                    int matchingPosition =
                            findAccountPosition(
                                    selectedAccountName
                            );

                    if (matchingPosition < 0
                            && !selectedAccountName.isEmpty()) {

                        Account oldAccount =
                                new Account();

                        oldAccount.setName(
                                selectedAccountName
                        );

                        oldAccount.setType(
                                "Other"
                        );

                        oldAccount.setOpeningBalance(
                                0
                        );

                        oldAccount.setColor(
                                "#0F6CBD"
                        );

                        accounts.add(
                                0,
                                oldAccount
                        );

                        accountAdapter.insert(
                                selectedAccountName,
                                0
                        );

                        matchingPosition = 0;
                    }

                    accountAdapter.notifyDataSetChanged();

                    if (accounts.isEmpty()) {
                        txtAccountMessage.setText(
                                "कोई account उपलब्ध नहीं है। पहले Account बनाएँ।"
                        );

                        txtAccountMessage.setVisibility(
                                View.VISIBLE
                        );

                        spinnerAccount.setEnabled(
                                false
                        );

                        accountReady = false;

                    } else {
                        txtAccountMessage.setVisibility(
                                View.GONE
                        );

                        spinnerAccount.setEnabled(
                                true
                        );

                        if (matchingPosition >= 0
                                && matchingPosition
                                < accounts.size()) {

                            spinnerAccount.setSelection(
                                    matchingPosition
                            );

                        } else {
                            int cashPosition =
                                    findAccountPosition(
                                            "Cash"
                                    );

                            spinnerAccount.setSelection(
                                    cashPosition >= 0
                                            ? cashPosition
                                            : 0
                            );
                        }

                        accountReady = true;
                    }

                    updateSaveButtonState();
                }
        );
    }

    private int findAccountPosition(
            String accountName
    ) {
        if (accountName == null
                || accountName.trim().isEmpty()) {
            return -1;
        }

        for (int index = 0;
             index < accounts.size();
             index++) {

            Account account =
                    accounts.get(index);

            if (account.getName() != null
                    && account.getName()
                    .trim()
                    .equalsIgnoreCase(
                            accountName.trim()
                    )) {
                return index;
            }
        }

        return -1;
    }

    private void updateSaveButtonState() {
        if (btnUpdate == null) {
            return;
        }

        btnUpdate.setEnabled(
                categoryReady
                        && accountReady
        );
    }

    private void showDatePicker() {
        int year =
                selectedDate.get(
                        Calendar.YEAR
                );

        int month =
                selectedDate.get(
                        Calendar.MONTH
                );

        int day =
                selectedDate.get(
                        Calendar.DAY_OF_MONTH
                );

        DatePickerDialog datePickerDialog =
                new DatePickerDialog(
                        this,
                        (view,
                         selectedYear,
                         selectedMonth,
                         selectedDay) -> {

                            selectedDate.set(
                                    Calendar.YEAR,
                                    selectedYear
                            );

                            selectedDate.set(
                                    Calendar.MONTH,
                                    selectedMonth
                            );

                            selectedDate.set(
                                    Calendar.DAY_OF_MONTH,
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
        String dateText =
                new SimpleDateFormat(
                        "dd MMMM yyyy",
                        Locale.ENGLISH
                ).format(
                        selectedDate.getTime()
                );

        txtSelectedDate.setText(
                dateText
        );
    }

    private void updateTransaction() {
        List<ExpenseItem> expenseItems =
                collectExpenseItems();

        if (expenseItems == null) {
            return;
        }

        String amountText =
                etAmount.getText() == null
                        ? ""
                        : etAmount.getText()
                        .toString()
                        .trim();

        String note =
                etNote.getText() == null
                        ? ""
                        : etNote.getText()
                        .toString()
                        .trim();

        if (transactionId <= 0) {
            Toast.makeText(
                    this,
                    "Transaction नहीं मिला",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        if (!categoryReady
                || categories.isEmpty()) {

            Toast.makeText(
                    this,
                    "पहले इस type की category बनाएँ",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        if (!accountReady
                || accounts.isEmpty()) {

            Toast.makeText(
                    this,
                    "पहले Account बनाएँ",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        if (TextUtils.isEmpty(
                amountText
        )) {
            etAmount.setError(
                    "Amount डालें"
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

        } catch (NumberFormatException exception) {
            etAmount.setError(
                    "सही amount डालें"
            );

            etAmount.requestFocus();
            return;
        }

        if (amount <= 0) {
            etAmount.setError(
                    "Amount 0 से बड़ा होना चाहिए"
            );

            etAmount.requestFocus();
            return;
        }

        int categoryPosition =
                spinnerCategory
                        .getSelectedItemPosition();

        int accountPosition =
                spinnerAccount
                        .getSelectedItemPosition();

        if (categoryPosition < 0
                || categoryPosition
                >= categories.size()) {

            Toast.makeText(
                    this,
                    "Category चुनें",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        if (accountPosition < 0
                || accountPosition
                >= accounts.size()) {

            Toast.makeText(
                    this,
                    "Account चुनें",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        Category selectedCategory =
                categories.get(
                        categoryPosition
                );

        Account selectedAccount =
                accounts.get(
                        accountPosition
                );

        btnUpdate.setEnabled(false);
        btnUpdate.setText(
                "Updating Transaction..."
        );

        Transaction transaction =
                new Transaction();

        transaction.setId(
                transactionId
        );

        transaction.setType(
                getSelectedDatabaseType()
        );

        transaction.setAmount(
                amount
        );

        transaction.setCategory(
                selectedCategory.getName()
        );

        transaction.setAccount(
                selectedAccount.getName()
        );

        transaction.setNote(note);

        String date =
                new SimpleDateFormat(
                        "yyyy-MM-dd HH:mm",
                        Locale.US
                ).format(
                        selectedDate.getTime()
                );

        transaction.setDate(date);

        transactionRepository.updateWithExpenseItems(
                transaction,
                expenseItems,
                () -> {
                    Toast.makeText(
                            EditTransactionActivity.this,
                            "Transaction updated",
                            Toast.LENGTH_SHORT
                    ).show();

                    finish();
                }
        );
    }

    private String getSelectedDatabaseType() {
        Object selectedItem =
                spinnerType.getSelectedItem();

        if (selectedItem != null
                && selectedItem.toString()
                .equalsIgnoreCase(
                        "Income"
                )) {
            return "INCOME";
        }

        return "EXPENSE";
    }

    private String getSelectedRepositoryType() {
        return getSelectedDatabaseType()
                .equals("INCOME")
                ? "Income"
                : "Expense";
    }

    private String normalizeDatabaseType(
            String type
    ) {
        if (type != null
                && type.trim()
                .equalsIgnoreCase(
                        "INCOME"
                )) {
            return "INCOME";
        }

        return "EXPENSE";
    }

    private String formatPlainAmount(
            double amount
    ) {
        if (amount == Math.rint(amount)) {
            return String.format(
                    Locale.US,
                    "%.0f",
                    amount
            );
        }

        return String.format(
                Locale.US,
                "%.2f",
                amount
        );
    }
}
