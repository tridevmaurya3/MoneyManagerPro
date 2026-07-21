package com.example.moneymanagerpro.activities;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.database.DatabaseClient;
import com.example.moneymanagerpro.model.Transaction;
import com.example.moneymanagerpro.repository.TransactionRepository;
import com.example.moneymanagerpro.utils.BubbleTouchAnimator;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputLayout;

import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class TransactionsActivity extends AppCompatActivity {

    private EditText etSearchTransactions;
    private EditText etMinAmount;
    private EditText etMaxAmount;
    private EditText etFromDate;
    private EditText etToDate;

    private TextInputLayout inputMinAmount;
    private TextInputLayout inputMaxAmount;

    private MaterialAutoCompleteTextView dropdownTransactionType;
    private MaterialAutoCompleteTextView dropdownTransactionCategory;
    private MaterialAutoCompleteTextView dropdownTransactionAccount;
    private MaterialAutoCompleteTextView dropdownTransactionPeriod;

    private MaterialButton btnApplyFilters;
    private MaterialButton btnResetFilters;

    private TextView txtEmptyTransactions;
    private TextView txtResultCount;
    private LinearLayout transactionContainer;

    private TransactionRepository transactionRepository;

    private final List<Transaction> allTransactions = new ArrayList<>();

    private Calendar filterStartDate;
    private Calendar filterEndDate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transactions);

        bindViews();
        setupFilters();

        transactionRepository = new TransactionRepository(this);

        loadTransactions();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (transactionRepository != null) {
            loadTransactions();
        }
    }

    private void bindViews() {
        TextView btnBack = findViewById(R.id.btnBack);

        etSearchTransactions = findViewById(R.id.etSearchTransactions);
        etMinAmount = findViewById(R.id.etMinAmount);
        etMaxAmount = findViewById(R.id.etMaxAmount);
        etFromDate = findViewById(R.id.etFromDate);
        etToDate = findViewById(R.id.etToDate);

        inputMinAmount = findViewById(R.id.inputMinAmount);
        inputMaxAmount = findViewById(R.id.inputMaxAmount);

        dropdownTransactionType = findViewById(
                R.id.dropdownTransactionType
        );

        dropdownTransactionCategory = findViewById(
                R.id.dropdownTransactionCategory
        );

        dropdownTransactionAccount = findViewById(
                R.id.dropdownTransactionAccount
        );

        dropdownTransactionPeriod = findViewById(
                R.id.dropdownTransactionPeriod
        );

        btnApplyFilters = findViewById(R.id.btnApplyFilters);
        btnResetFilters = findViewById(R.id.btnResetFilters);

        txtEmptyTransactions = findViewById(
                R.id.txtEmptyTransactions
        );

        txtResultCount = findViewById(R.id.txtResultCount);

        transactionContainer = findViewById(
                R.id.transactionContainer
        );

        btnBack.setOnClickListener(v -> finish());
    }

    private void setupFilters() {
        setDropdownItems(
                dropdownTransactionType,
                new String[]{
                        "All Transactions",
                        "Income Only",
                        "Expense Only",
                        "Transfers Only"
                },
                "All Transactions"
        );

        setDropdownItems(
                dropdownTransactionCategory,
                new String[]{"All Categories"},
                "All Categories"
        );

        setDropdownItems(
                dropdownTransactionAccount,
                new String[]{"All Accounts"},
                "All Accounts"
        );

        setDropdownItems(
                dropdownTransactionPeriod,
                new String[]{
                        "All Time",
                        "Today",
                        "This Week",
                        "This Month",
                        "This Year"
                },
                "All Time"
        );

        dropdownTransactionType.setOnItemClickListener(
                (parent, view, position, id) -> filterTransactions()
        );

        dropdownTransactionCategory.setOnItemClickListener(
                (parent, view, position, id) -> filterTransactions()
        );

        dropdownTransactionAccount.setOnItemClickListener(
                (parent, view, position, id) -> filterTransactions()
        );

        dropdownTransactionPeriod.setOnItemClickListener(
                (parent, view, position, id) -> filterTransactions()
        );

        etSearchTransactions.addTextChangedListener(
                new TextWatcher() {
                    @Override
                    public void beforeTextChanged(
                            CharSequence s,
                            int start,
                            int count,
                            int after
                    ) {
                    }

                    @Override
                    public void onTextChanged(
                            CharSequence s,
                            int start,
                            int before,
                            int count
                    ) {
                        filterTransactions();
                    }

                    @Override
                    public void afterTextChanged(Editable s) {
                    }
                }
        );

        etFromDate.setOnClickListener(v -> showDatePicker(true));
        etToDate.setOnClickListener(v -> showDatePicker(false));

        btnApplyFilters.setOnClickListener(v -> filterTransactions());

        btnResetFilters.setOnClickListener(v -> resetAllFilters());

        BubbleTouchAnimator.apply(btnApplyFilters);
        BubbleTouchAnimator.apply(btnResetFilters);
    }

    private void setDropdownItems(
            MaterialAutoCompleteTextView dropdown,
            String[] items,
            String selectedItem
    ) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                items
        );

        dropdown.setAdapter(adapter);
        dropdown.setText(selectedItem, false);
    }

    private void loadTransactions() {
        transactionRepository.getAllTransactions(transactions -> {
            allTransactions.clear();
            allTransactions.addAll(transactions);

            updateCategoryFilterOptions();
            updateAccountFilterOptions();
            filterTransactions();
        });
    }

    private void updateCategoryFilterOptions() {
        String selectedCategory = getSelectedText(
                dropdownTransactionCategory,
                "All Categories"
        );

        Set<String> categorySet = new LinkedHashSet<>();
        categorySet.add("All Categories");

        for (Transaction transaction : allTransactions) {
            String category = safeText(transaction.getCategory());

            if (!category.isEmpty()) {
                categorySet.add(category);
            }
        }

        List<String> categoryList = new ArrayList<>(categorySet);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                categoryList
        );

        dropdownTransactionCategory.setAdapter(adapter);

        if (categoryList.contains(selectedCategory)) {
            dropdownTransactionCategory.setText(
                    selectedCategory,
                    false
            );
        } else {
            dropdownTransactionCategory.setText(
                    "All Categories",
                    false
            );
        }
    }

    private void updateAccountFilterOptions() {
        String selectedAccount = getSelectedText(
                dropdownTransactionAccount,
                "All Accounts"
        );

        Set<String> accountSet = new LinkedHashSet<>();
        accountSet.add("All Accounts");

        for (Transaction transaction : allTransactions) {
            String account = safeText(transaction.getAccount());

            if (!account.isEmpty()) {
                accountSet.add(account);
            }
        }

        List<String> accountList = new ArrayList<>(accountSet);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                accountList
        );

        dropdownTransactionAccount.setAdapter(adapter);

        if (accountList.contains(selectedAccount)) {
            dropdownTransactionAccount.setText(
                    selectedAccount,
                    false
            );
        } else {
            dropdownTransactionAccount.setText(
                    "All Accounts",
                    false
            );
        }
    }

    private void showDatePicker(boolean isFromDate) {
        Calendar calendar = isFromDate
                ? (filterStartDate == null
                   ? Calendar.getInstance()
                   : filterStartDate)
                : (filterEndDate == null
                   ? Calendar.getInstance()
                   : filterEndDate);

        DatePickerDialog dialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    Calendar selectedDate = Calendar.getInstance();
                    selectedDate.set(year, month, dayOfMonth);
                    clearTime(selectedDate);

                    if (isFromDate) {
                        filterStartDate = selectedDate;
                    } else {
                        filterEndDate = selectedDate;
                    }

                    updateDateFields();
                    filterTransactions();
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );

        dialog.show();
    }

    private void updateDateFields() {
        etFromDate.setText(
                filterStartDate == null
                        ? ""
                        : formatVisibleDate(filterStartDate)
        );

        etToDate.setText(
                filterEndDate == null
                        ? ""
                        : formatVisibleDate(filterEndDate)
        );
    }

    private void resetAllFilters() {
        etSearchTransactions.setText("");
        etMinAmount.setText("");
        etMaxAmount.setText("");

        filterStartDate = null;
        filterEndDate = null;
        updateDateFields();

        dropdownTransactionType.setText(
                "All Transactions",
                false
        );

        dropdownTransactionCategory.setText(
                "All Categories",
                false
        );

        dropdownTransactionAccount.setText(
                "All Accounts",
                false
        );

        dropdownTransactionPeriod.setText(
                "All Time",
                false
        );

        inputMinAmount.setError(null);
        inputMaxAmount.setError(null);

        filterTransactions();
    }

    private void filterTransactions() {
        if (transactionContainer == null) {
            return;
        }

        Double minAmount = getOptionalAmount(
                etMinAmount,
                inputMinAmount
        );

        Double maxAmount = getOptionalAmount(
                etMaxAmount,
                inputMaxAmount
        );

        if (minAmount == null || maxAmount == null) {
            return;
        }

        if (minAmount >= 0 && maxAmount >= 0
                && minAmount > maxAmount) {
            inputMaxAmount.setError(
                    "Maximum amount must be greater than minimum amount"
            );
            return;
        }

        inputMinAmount.setError(null);
        inputMaxAmount.setError(null);

        transactionContainer.removeAllViews();

        String searchText = getText(etSearchTransactions)
                .toLowerCase(Locale.getDefault());

        String typeFilter = getSelectedText(
                dropdownTransactionType,
                "All Transactions"
        );

        String categoryFilter = getSelectedText(
                dropdownTransactionCategory,
                "All Categories"
        );

        String accountFilter = getSelectedText(
                dropdownTransactionAccount,
                "All Accounts"
        );

        String periodFilter = getSelectedText(
                dropdownTransactionPeriod,
                "All Time"
        );

        int visibleCount = 0;

        for (Transaction transaction : allTransactions) {
            if (matchesTypeFilter(transaction, typeFilter)
                    && matchesCategoryFilter(
                    transaction,
                    categoryFilter
            )
                    && matchesAccountFilter(
                    transaction,
                    accountFilter
            )
                    && matchesPeriodFilter(
                    transaction,
                    periodFilter
            )
                    && matchesCustomDateRange(transaction)
                    && matchesAmountRange(
                    transaction,
                    minAmount,
                    maxAmount
            )
                    && matchesSearch(
                    transaction,
                    searchText
            )) {

                addTransactionRow(transaction);
                visibleCount++;
            }
        }

        txtResultCount.setText(
                visibleCount + " transaction(s) found"
        );

        txtEmptyTransactions.setVisibility(
                visibleCount == 0 ? View.VISIBLE : View.GONE
        );
    }

    private Double getOptionalAmount(
            EditText editText,
            TextInputLayout inputLayout
    ) {
        String amountText = getText(editText);

        if (amountText.isEmpty()) {
            inputLayout.setError(null);
            return -1.0;
        }

        try {
            double amount = Double.parseDouble(amountText);

            if (amount < 0) {
                inputLayout.setError("Amount cannot be negative");
                return null;
            }

            inputLayout.setError(null);
            return amount;

        } catch (Exception exception) {
            inputLayout.setError("Enter a valid amount");
            return null;
        }
    }

    private boolean matchesTypeFilter(
            Transaction transaction,
            String typeFilter
    ) {
        String type = safeText(transaction.getType());

        if (typeFilter.equals("Income Only")) {
            return type.equals("INCOME");
        }

        if (typeFilter.equals("Expense Only")) {
            return type.equals("EXPENSE");
        }

        if (typeFilter.equals("Transfers Only")) {
            return type.equals("TRANSFER_IN")
                    || type.equals("TRANSFER_OUT");
        }

        return true;
    }

    private boolean matchesCategoryFilter(
            Transaction transaction,
            String categoryFilter
    ) {
        if (categoryFilter.equals("All Categories")) {
            return true;
        }

        return safeText(transaction.getCategory())
                .equalsIgnoreCase(categoryFilter);
    }

    private boolean matchesAccountFilter(
            Transaction transaction,
            String accountFilter
    ) {
        if (accountFilter.equals("All Accounts")) {
            return true;
        }

        return safeText(transaction.getAccount())
                .equalsIgnoreCase(accountFilter);
    }

    private boolean matchesPeriodFilter(
            Transaction transaction,
            String periodFilter
    ) {
        if (periodFilter.equals("All Time")) {
            return true;
        }

        Calendar transactionDate = parseTransactionDate(
                safeText(transaction.getDate())
        );

        if (transactionDate == null) {
            return false;
        }

        Calendar today = Calendar.getInstance();
        clearTime(today);
        clearTime(transactionDate);

        if (periodFilter.equals("Today")) {
            return transactionDate.get(Calendar.YEAR)
                    == today.get(Calendar.YEAR)
                    && transactionDate.get(Calendar.DAY_OF_YEAR)
                    == today.get(Calendar.DAY_OF_YEAR);
        }

        if (periodFilter.equals("This Month")) {
            return transactionDate.get(Calendar.YEAR)
                    == today.get(Calendar.YEAR)
                    && transactionDate.get(Calendar.MONTH)
                    == today.get(Calendar.MONTH);
        }

        if (periodFilter.equals("This Year")) {
            return transactionDate.get(Calendar.YEAR)
                    == today.get(Calendar.YEAR);
        }

        Calendar weekStart = Calendar.getInstance();
        clearTime(weekStart);
        weekStart.setFirstDayOfWeek(Calendar.MONDAY);

        int day = weekStart.get(Calendar.DAY_OF_WEEK);
        int difference = day - Calendar.MONDAY;

        if (difference < 0) {
            difference += 7;
        }

        weekStart.add(Calendar.DAY_OF_MONTH, -difference);

        Calendar weekEnd = (Calendar) weekStart.clone();
        weekEnd.add(Calendar.DAY_OF_MONTH, 7);

        return !transactionDate.before(weekStart)
                && transactionDate.before(weekEnd);
    }

    private boolean matchesCustomDateRange(
            Transaction transaction
    ) {
        if (filterStartDate == null && filterEndDate == null) {
            return true;
        }

        Calendar transactionDate = parseTransactionDate(
                safeText(transaction.getDate())
        );

        if (transactionDate == null) {
            return false;
        }

        clearTime(transactionDate);

        if (filterStartDate != null
                && transactionDate.before(filterStartDate)) {
            return false;
        }

        if (filterEndDate != null
                && transactionDate.after(filterEndDate)) {
            return false;
        }

        return true;
    }

    private boolean matchesAmountRange(
            Transaction transaction,
            double minAmount,
            double maxAmount
    ) {
        double amount = transaction.getAmount();

        if (minAmount >= 0 && amount < minAmount) {
            return false;
        }

        if (maxAmount >= 0 && amount > maxAmount) {
            return false;
        }

        return true;
    }

    private boolean matchesSearch(
            Transaction transaction,
            String searchText
    ) {
        if (searchText.isEmpty()) {
            return true;
        }

        String combinedText =
                safeText(transaction.getType()) + " "
                        + transaction.getAmount() + " "
                        + safeText(transaction.getCategory()) + " "
                        + safeText(transaction.getAccount()) + " "
                        + safeText(transaction.getNote()) + " "
                        + safeText(transaction.getDate());

        return combinedText
                .toLowerCase(Locale.getDefault())
                .contains(searchText);
    }

    private Calendar parseTransactionDate(String dateText) {
        String[] formats = {
                "yyyy-MM-dd HH:mm",
                "yyyy-MM-dd"
        };

        for (String format : formats) {
            try {
                Date parsedDate = new SimpleDateFormat(
                        format,
                        Locale.US
                ).parse(dateText);

                if (parsedDate != null) {
                    Calendar calendar = Calendar.getInstance();
                    calendar.setTime(parsedDate);
                    return calendar;
                }
            } catch (ParseException exception) {
                // Try the next date format.
            }
        }

        return null;
    }

    private void addTransactionRow(Transaction transaction) {
        String type = safeText(transaction.getType());

        boolean isIncome = type.equals("INCOME");
        boolean isExpense = type.equals("EXPENSE");
        boolean isTransferIn = type.equals("TRANSFER_IN");
        boolean isTransferOut = type.equals("TRANSFER_OUT");
        boolean isTransfer = isTransferIn || isTransferOut;

        String title;
        String amountPrefix;
        int amountColor;

        if (isIncome) {
            title = "Income | " + safeText(transaction.getCategory());
            amountPrefix = "+ ";
            amountColor = Color.parseColor("#188038");

        } else if (isExpense) {
            title = "Expense | " + safeText(transaction.getCategory());
            amountPrefix = "- ";
            amountColor = Color.parseColor("#D93025");

        } else if (isTransferIn) {
            title = "Transfer In | " + safeText(transaction.getAccount());
            amountPrefix = "+ ";
            amountColor = Color.parseColor("#1565C0");

        } else if (isTransferOut) {
            title = "Transfer Out | " + safeText(transaction.getAccount());
            amountPrefix = "- ";
            amountColor = Color.parseColor("#7B1FA2");

        } else {
            title = type;
            amountPrefix = "";
            amountColor = Color.parseColor("#344054");
        }

        MaterialCardView card = new MaterialCardView(this);
        card.setCardBackgroundColor(Color.WHITE);
        card.setRadius(dp(18));
        card.setCardElevation(dp(3));
        card.setStrokeWidth(dp(1));
        card.setStrokeColor(Color.parseColor("#DCE3EE"));

        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        cardParams.setMargins(0, dp(7), 0, dp(7));
        card.setLayoutParams(cardParams);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(14), dp(16), dp(14));

        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView txtTitle = createText(
                title,
                16,
                Color.parseColor("#1D2939"),
                true
        );

        txtTitle.setLayoutParams(new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1
        ));

        TextView txtAmount = createText(
                amountPrefix + formatAmount(transaction.getAmount()),
                17,
                amountColor,
                true
        );

        txtAmount.setGravity(Gravity.END);

        topRow.addView(txtTitle);
        topRow.addView(txtAmount);

        TextView txtDetails = createText(
                "Account: " + safeText(transaction.getAccount())
                        + "\nDate: "
                        + formatTransactionDate(
                        safeText(transaction.getDate())
                )
                        + (safeText(transaction.getNote()).isEmpty()
                        ? ""
                        : "\nNote: "
                          + safeText(transaction.getNote())),
                13,
                Color.parseColor("#667085"),
                false
        );

        LinearLayout.LayoutParams detailsParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        detailsParams.setMargins(0, dp(8), 0, 0);
        txtDetails.setLayoutParams(detailsParams);

        content.addView(topRow);
        content.addView(txtDetails);

        if (isTransfer) {
            TextView txtTransferInfo = createText(
                    "Transfer entries are protected and cannot be edited or deleted here.",
                    12,
                    Color.parseColor("#5B4B8A"),
                    false
            );

            LinearLayout.LayoutParams infoParams =
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    );

            infoParams.setMargins(0, dp(10), 0, 0);
            txtTransferInfo.setLayoutParams(infoParams);

            content.addView(txtTransferInfo);

            card.setOnClickListener(v ->
                    Toast.makeText(
                            TransactionsActivity.this,
                            "Transfer entries cannot be edited or deleted.",
                            Toast.LENGTH_SHORT
                    ).show()
            );

        } else {
            LinearLayout actionRow = new LinearLayout(this);
            actionRow.setOrientation(LinearLayout.HORIZONTAL);

            LinearLayout.LayoutParams rowParams =
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            dp(42)
                    );

            rowParams.setMargins(0, dp(12), 0, 0);
            actionRow.setLayoutParams(rowParams);

            MaterialButton btnEdit = createActionButton(
                    "Edit",
                    Color.parseColor("#1565C0")
            );

            MaterialButton btnDelete = createActionButton(
                    "Delete",
                    Color.parseColor("#64748B")
            );

            addButtonToRow(actionRow, btnEdit);
            addButtonToRow(actionRow, btnDelete);

            btnEdit.setOnClickListener(v ->
                    openEditScreen(transaction)
            );

            btnDelete.setOnClickListener(v ->
                    confirmDelete(transaction)
            );

            BubbleTouchAnimator.apply(btnEdit);
            BubbleTouchAnimator.apply(btnDelete);

            content.addView(actionRow);

            card.setOnClickListener(v ->
                    openEditScreen(transaction)
            );
        }

        card.addView(content);

        BubbleTouchAnimator.apply(card);
        transactionContainer.addView(card);
    }

    private MaterialButton createActionButton(
            String text,
            int color
    ) {
        MaterialButton button = new MaterialButton(this);
        button.setText(text);
        button.setTextSize(13);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setCornerRadius(dp(18));
        button.setBackgroundTintList(
                ColorStateList.valueOf(color)
        );

        return button;
    }

    private void addButtonToRow(
            LinearLayout row,
            MaterialButton button
    ) {
        LinearLayout.LayoutParams buttonParams =
                new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        1
                );

        buttonParams.setMargins(dp(4), 0, dp(4), 0);
        button.setLayoutParams(buttonParams);

        row.addView(button);
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
            textView.setTypeface(Typeface.DEFAULT_BOLD);
        }

        return textView;
    }

    private void openEditScreen(Transaction transaction) {
        Intent intent = new Intent(
                this,
                EditTransactionActivity.class
        );

        intent.putExtra("id", transaction.getId());
        intent.putExtra("type", transaction.getType());
        intent.putExtra("amount", transaction.getAmount());
        intent.putExtra("category", transaction.getCategory());
        intent.putExtra("account", transaction.getAccount());
        intent.putExtra("note", transaction.getNote());
        intent.putExtra("date", transaction.getDate());

        intent.putExtra("transaction_id", transaction.getId());
        intent.putExtra("transaction_type", transaction.getType());
        intent.putExtra("transaction_amount", transaction.getAmount());
        intent.putExtra("transaction_category", transaction.getCategory());
        intent.putExtra("transaction_account", transaction.getAccount());
        intent.putExtra("transaction_note", transaction.getNote());
        intent.putExtra("transaction_date", transaction.getDate());

        startActivity(intent);
    }

    private void confirmDelete(Transaction transaction) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Transaction")
                .setMessage(
                        "Do you want to permanently delete this transaction?"
                )
                .setPositiveButton("Delete", (dialog, which) -> {
                    new Thread(() -> {
                        DatabaseClient.getInstance(
                                        getApplicationContext()
                                ).getAppDatabase()
                                .transactionDao()
                                .delete(transaction);

                        runOnUiThread(() -> {
                            Toast.makeText(
                                    TransactionsActivity.this,
                                    "Transaction deleted",
                                    Toast.LENGTH_SHORT
                            ).show();

                            loadTransactions();
                        });
                    }).start();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private String getSelectedText(
            MaterialAutoCompleteTextView dropdown,
            String defaultText
    ) {
        String value = getText(dropdown);

        return value.isEmpty() ? defaultText : value;
    }

    private String getText(TextView view) {
        return view.getText() == null
                ? ""
                : view.getText().toString().trim();
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private String formatAmount(double amount) {
        NumberFormat numberFormat = NumberFormat.getNumberInstance(
                new Locale("en", "IN")
        );

        numberFormat.setMinimumFractionDigits(2);
        numberFormat.setMaximumFractionDigits(2);

        return "₹" + numberFormat.format(amount);
    }

    private String formatTransactionDate(String dateText) {
        String[] formats = {
                "yyyy-MM-dd HH:mm",
                "yyyy-MM-dd"
        };

        for (String format : formats) {
            try {
                Date date = new SimpleDateFormat(
                        format,
                        Locale.US
                ).parse(dateText);

                if (date != null) {
                    return new SimpleDateFormat(
                            "dd MMM yyyy, hh:mm a",
                            Locale.ENGLISH
                    ).format(date);
                }
            } catch (Exception exception) {
                // Try the next date format.
            }
        }

        return dateText;
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

    private int dp(int value) {
        return (int) (
                value * getResources().getDisplayMetrics().density
        );
    }
}