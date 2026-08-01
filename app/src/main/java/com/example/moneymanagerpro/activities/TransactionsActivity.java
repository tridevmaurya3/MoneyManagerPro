package com.example.moneymanagerpro.activities;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.database.DatabaseClient;
import com.example.moneymanagerpro.model.ExpenseItem;
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
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class TransactionsActivity extends AppCompatActivity {

    private static final int EARLIEST_SELECTABLE_YEAR = 2000;

    private static final String SORT_NEWEST =
            "Newest first";

    private static final String SORT_OLDEST =
            "Oldest first";

    private static final String SORT_AMOUNT_HIGH =
            "Amount: High to low";

    private static final String SORT_AMOUNT_LOW =
            "Amount: Low to high";

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
    private MaterialButton btnShowFilters;
    private MaterialButton btnSortTransactions;

    private MaterialButton btnPreviousTransactionMonth;
    private MaterialButton btnNextTransactionMonth;

    private View btnChooseTransactionPeriod;
    private View transactionFilterPanel;

    private TextView txtEmptyTransactions;
    private TextView txtResultCount;
    private TextView txtActiveFilterSummary;
    private TextView txtSelectedTransactionPeriod;

    private LinearLayout transactionContainer;

    private TransactionRepository transactionRepository;

    private final List<Transaction> allTransactions =
            new ArrayList<>();

    private final Map<Integer, List<ExpenseItem>>
            expenseItemsByTransaction =
            new LinkedHashMap<>();

    private Calendar filterStartDate;
    private Calendar filterEndDate;

    private final Calendar selectedTransactionPeriod =
            Calendar.getInstance();

    private int lastObservedCurrentYear;
    private int lastObservedCurrentMonth;

    private String selectedSort =
            SORT_NEWEST;

    @Override
    protected void onCreate(
            Bundle savedInstanceState
    ) {
        super.onCreate(savedInstanceState);

        setContentView(
                R.layout.activity_transactions
        );

        initializeSelectedTransactionPeriod();
        bindViews();
        setupMonthYearSelector();
        setupFilters();

        transactionRepository =
                new TransactionRepository(this);

        loadTransactions();
    }

    @Override
    protected void onResume() {
        super.onResume();

        resetToCurrentMonthWhenCalendarMonthChanges();

        if (transactionRepository != null) {
            loadTransactions();
        }
    }

    private void bindViews() {
        TextView btnBack =
                findViewById(
                        R.id.btnBack
                );

        etSearchTransactions =
                findViewById(
                        R.id.etSearchTransactions
                );

        etMinAmount =
                findViewById(
                        R.id.etMinAmount
                );

        etMaxAmount =
                findViewById(
                        R.id.etMaxAmount
                );

        etFromDate =
                findViewById(
                        R.id.etFromDate
                );

        etToDate =
                findViewById(
                        R.id.etToDate
                );

        inputMinAmount =
                findViewById(
                        R.id.inputMinAmount
                );

        inputMaxAmount =
                findViewById(
                        R.id.inputMaxAmount
                );

        dropdownTransactionType =
                findViewById(
                        R.id.dropdownTransactionType
                );

        dropdownTransactionCategory =
                findViewById(
                        R.id.dropdownTransactionCategory
                );

        dropdownTransactionAccount =
                findViewById(
                        R.id.dropdownTransactionAccount
                );

        dropdownTransactionPeriod =
                findViewById(
                        R.id.dropdownTransactionPeriod
                );

        btnApplyFilters =
                findViewById(
                        R.id.btnApplyFilters
                );

        btnResetFilters =
                findViewById(
                        R.id.btnResetFilters
                );

        btnShowFilters =
                findViewById(
                        R.id.btnShowTransactionFilters
                );

        btnSortTransactions =
                findViewById(
                        R.id.btnSortTransactions
                );

        transactionFilterPanel =
                findViewById(
                        R.id.transactionFilterPanel
                );

        txtEmptyTransactions =
                findViewById(
                        R.id.txtEmptyTransactions
                );

        txtResultCount =
                findViewById(
                        R.id.txtResultCount
                );

        txtActiveFilterSummary =
                findViewById(
                        R.id.txtActiveFilterSummary
                );

        txtSelectedTransactionPeriod =
                findViewById(
                        R.id.txtSelectedTransactionPeriod
                );

        btnPreviousTransactionMonth =
                findViewById(
                        R.id.btnPreviousTransactionMonth
                );

        btnNextTransactionMonth =
                findViewById(
                        R.id.btnNextTransactionMonth
                );

        btnChooseTransactionPeriod =
                findViewById(
                        R.id.btnChooseTransactionPeriod
                );

        transactionContainer =
                findViewById(
                        R.id.transactionContainer
                );

        btnBack.setOnClickListener(
                view -> finish()
        );
    }

    private void initializeSelectedTransactionPeriod() {
        Calendar current =
                Calendar.getInstance();

        lastObservedCurrentYear =
                current.get(Calendar.YEAR);

        lastObservedCurrentMonth =
                current.get(Calendar.MONTH);

        setCalendarToMonth(
                selectedTransactionPeriod,
                lastObservedCurrentYear,
                lastObservedCurrentMonth
        );
    }

    private void setupMonthYearSelector() {
        updateSelectedPeriodUi();

        BubbleTouchAnimator.apply(
                btnPreviousTransactionMonth
        );

        BubbleTouchAnimator.apply(
                btnNextTransactionMonth
        );

        BubbleTouchAnimator.apply(
                btnChooseTransactionPeriod
        );

        btnPreviousTransactionMonth
                .setOnClickListener(
                        view -> changeSelectedMonth(-1)
                );

        btnNextTransactionMonth
                .setOnClickListener(
                        view -> changeSelectedMonth(1)
                );

        btnChooseTransactionPeriod
                .setOnClickListener(
                        view -> showMonthYearPicker()
                );
    }

    private void changeSelectedMonth(
            int monthChange
    ) {
        Calendar requestedPeriod =
                copyMonth(
                        selectedTransactionPeriod
                );

        requestedPeriod.add(
                Calendar.MONTH,
                monthChange
        );

        if (isAfterCurrentMonth(
                requestedPeriod
        )) {
            Toast.makeText(
                    this,
                    "Future month cannot be selected",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        if (requestedPeriod.get(
                Calendar.YEAR
        ) < EARLIEST_SELECTABLE_YEAR) {
            Toast.makeText(
                    this,
                    "No earlier period is available",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        setCalendarToMonth(
                selectedTransactionPeriod,
                requestedPeriod.get(
                        Calendar.YEAR
                ),
                requestedPeriod.get(
                        Calendar.MONTH
                )
        );

        applySelectedMonthScope();
    }

    private void showMonthYearPicker() {
        Calendar current =
                Calendar.getInstance();

        LinearLayout pickerContainer =
                new LinearLayout(this);

        pickerContainer.setOrientation(
                LinearLayout.HORIZONTAL
        );

        pickerContainer.setGravity(
                Gravity.CENTER
        );

        pickerContainer.setPadding(
                dp(18),
                dp(8),
                dp(18),
                dp(8)
        );

        NumberPicker monthPicker =
                new NumberPicker(this);

        monthPicker.setMinValue(0);
        monthPicker.setMaxValue(11);

        monthPicker.setDisplayedValues(
                createMonthNames(11)
        );

        monthPicker.setValue(
                selectedTransactionPeriod.get(
                        Calendar.MONTH
                )
        );

        monthPicker.setWrapSelectorWheel(
                false
        );

        NumberPicker yearPicker =
                new NumberPicker(this);

        yearPicker.setMinValue(
                EARLIEST_SELECTABLE_YEAR
        );

        yearPicker.setMaxValue(
                current.get(Calendar.YEAR)
        );

        yearPicker.setValue(
                selectedTransactionPeriod.get(
                        Calendar.YEAR
                )
        );

        yearPicker.setWrapSelectorWheel(
                false
        );

        LinearLayout.LayoutParams monthParams =
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1.35f
                );

        LinearLayout.LayoutParams yearParams =
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                );

        monthPicker.setLayoutParams(
                monthParams
        );

        yearPicker.setLayoutParams(
                yearParams
        );

        pickerContainer.addView(
                monthPicker
        );

        pickerContainer.addView(
                yearPicker
        );

        updateMonthPickerMaximum(
                monthPicker,
                yearPicker.getValue(),
                current
        );

        yearPicker.setOnValueChangedListener(
                (picker, oldValue, newValue) ->
                        updateMonthPickerMaximum(
                                monthPicker,
                                newValue,
                                current
                        )
        );

        new AlertDialog.Builder(this)
                .setTitle(
                        "Choose Month and Year"
                )
                .setView(
                        pickerContainer
                )
                .setNegativeButton(
                        "Cancel",
                        null
                )
                .setPositiveButton(
                        "Apply",
                        (dialog, which) -> {
                            setCalendarToMonth(
                                    selectedTransactionPeriod,
                                    yearPicker.getValue(),
                                    monthPicker.getValue()
                            );

                            applySelectedMonthScope();
                        }
                )
                .show();
    }

    private void updateMonthPickerMaximum(
            NumberPicker monthPicker,
            int selectedYear,
            Calendar current
    ) {
        int maximumMonth =
                selectedYear
                        == current.get(Calendar.YEAR)
                        ? current.get(Calendar.MONTH)
                        : Calendar.DECEMBER;

        int currentValue =
                monthPicker.getValue();

        monthPicker.setDisplayedValues(
                null
        );

        monthPicker.setMaxValue(
                maximumMonth
        );

        monthPicker.setDisplayedValues(
                createMonthNames(
                        maximumMonth
                )
        );

        if (currentValue > maximumMonth) {
            monthPicker.setValue(
                    maximumMonth
            );
        }
    }

    private String[] createMonthNames(
            int maximumMonth
    ) {
        String[] allMonths = {
                "January",
                "February",
                "March",
                "April",
                "May",
                "June",
                "July",
                "August",
                "September",
                "October",
                "November",
                "December"
        };

        String[] availableMonths =
                new String[
                        maximumMonth + 1
                        ];

        System.arraycopy(
                allMonths,
                0,
                availableMonths,
                0,
                availableMonths.length
        );

        return availableMonths;
    }

    private void applySelectedMonthScope() {
        filterStartDate = null;
        filterEndDate = null;

        updateDateFields();

        dropdownTransactionPeriod.setText(
                "Selected Month",
                false
        );

        updateSelectedPeriodUi();
        filterTransactions();
    }

    private void resetSelectedPeriodToCurrentMonth() {
        Calendar current =
                Calendar.getInstance();

        setCalendarToMonth(
                selectedTransactionPeriod,
                current.get(Calendar.YEAR),
                current.get(Calendar.MONTH)
        );

        lastObservedCurrentYear =
                current.get(Calendar.YEAR);

        lastObservedCurrentMonth =
                current.get(Calendar.MONTH);

        updateSelectedPeriodUi();
    }

    private void resetToCurrentMonthWhenCalendarMonthChanges() {
        Calendar current =
                Calendar.getInstance();

        int currentYear =
                current.get(Calendar.YEAR);

        int currentMonth =
                current.get(Calendar.MONTH);

        boolean monthChanged =
                currentYear
                        != lastObservedCurrentYear
                        || currentMonth
                        != lastObservedCurrentMonth;

        if (!monthChanged) {
            return;
        }

        lastObservedCurrentYear =
                currentYear;

        lastObservedCurrentMonth =
                currentMonth;

        setCalendarToMonth(
                selectedTransactionPeriod,
                currentYear,
                currentMonth
        );

        filterStartDate = null;
        filterEndDate = null;

        updateDateFields();

        if (dropdownTransactionPeriod != null) {
            dropdownTransactionPeriod.setText(
                    "Selected Month",
                    false
            );
        }

        updateSelectedPeriodUi();
    }

    private void updateSelectedPeriodUi() {
        if (txtSelectedTransactionPeriod == null) {
            return;
        }

        String selectedScope =
                dropdownTransactionPeriod == null
                        ? "Selected Month"
                        : getSelectedText(
                        dropdownTransactionPeriod,
                        "Selected Month"
                );

        String visiblePeriod;

        if ("All Time".equals(selectedScope)) {
            visiblePeriod =
                    "All Time";

        } else if ("Selected Year".equals(
                selectedScope
        )) {
            visiblePeriod =
                    String.valueOf(
                            selectedTransactionPeriod.get(
                                    Calendar.YEAR
                            )
                    );

        } else if ("Today".equals(
                selectedScope
        )) {
            visiblePeriod =
                    "Today";

        } else if ("This Week".equals(
                selectedScope
        )) {
            visiblePeriod =
                    "This Week";

        } else {
            visiblePeriod =
                    formatMonthYear(
                            selectedTransactionPeriod
                    );
        }

        txtSelectedTransactionPeriod.setText(
                visiblePeriod
        );

        boolean canMoveForward =
                !isCurrentMonth(
                        selectedTransactionPeriod
                );

        btnNextTransactionMonth.setEnabled(
                canMoveForward
        );

        btnNextTransactionMonth.setAlpha(
                canMoveForward
                        ? 1f
                        : 0.42f
        );

        boolean canMoveBackward =
                selectedTransactionPeriod.get(
                        Calendar.YEAR
                ) > EARLIEST_SELECTABLE_YEAR
                        || selectedTransactionPeriod.get(
                        Calendar.MONTH
                ) > Calendar.JANUARY;

        btnPreviousTransactionMonth.setEnabled(
                canMoveBackward
        );

        btnPreviousTransactionMonth.setAlpha(
                canMoveBackward
                        ? 1f
                        : 0.42f
        );
    }

    private String formatMonthYear(
            Calendar calendar
    ) {
        return new SimpleDateFormat(
                "MMMM yyyy",
                Locale.ENGLISH
        ).format(
                calendar.getTime()
        );
    }

    private boolean isCurrentMonth(
            Calendar calendar
    ) {
        Calendar current =
                Calendar.getInstance();

        return calendar.get(Calendar.YEAR)
                == current.get(Calendar.YEAR)
                && calendar.get(Calendar.MONTH)
                == current.get(Calendar.MONTH);
    }

    private boolean isAfterCurrentMonth(
            Calendar calendar
    ) {
        Calendar current =
                Calendar.getInstance();

        int requestedValue =
                calendar.get(Calendar.YEAR)
                        * 12
                        + calendar.get(
                        Calendar.MONTH
                );

        int currentValue =
                current.get(Calendar.YEAR)
                        * 12
                        + current.get(
                        Calendar.MONTH
                );

        return requestedValue
                > currentValue;
    }

    private Calendar copyMonth(
            Calendar source
    ) {
        Calendar copy =
                Calendar.getInstance();

        setCalendarToMonth(
                copy,
                source.get(Calendar.YEAR),
                source.get(Calendar.MONTH)
        );

        return copy;
    }

    private void setCalendarToMonth(
            Calendar calendar,
            int year,
            int month
    ) {
        calendar.clear();

        calendar.set(
                Calendar.YEAR,
                year
        );

        calendar.set(
                Calendar.MONTH,
                month
        );

        calendar.set(
                Calendar.DAY_OF_MONTH,
                1
        );

        calendar.set(
                Calendar.HOUR_OF_DAY,
                0
        );

        calendar.set(
                Calendar.MINUTE,
                0
        );

        calendar.set(
                Calendar.SECOND,
                0
        );

        calendar.set(
                Calendar.MILLISECOND,
                0
        );
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
                new String[]{
                        "All Categories"
                },
                "All Categories"
        );

        setDropdownItems(
                dropdownTransactionAccount,
                new String[]{
                        "All Accounts"
                },
                "All Accounts"
        );

        setDropdownItems(
                dropdownTransactionPeriod,
                new String[]{
                        "Selected Month",
                        "Selected Year",
                        "All Time",
                        "Today",
                        "This Week"
                },
                "Selected Month"
        );

        dropdownTransactionType
                .setOnItemClickListener(
                        (parent, view, position, id) ->
                                filterTransactions()
                );

        dropdownTransactionCategory
                .setOnItemClickListener(
                        (parent, view, position, id) ->
                                filterTransactions()
                );

        dropdownTransactionAccount
                .setOnItemClickListener(
                        (parent, view, position, id) ->
                                filterTransactions()
                );

        dropdownTransactionPeriod
                .setOnItemClickListener(
                        (parent, view, position, id) -> {
                            updateSelectedPeriodUi();
                            filterTransactions();
                        }
                );

        etSearchTransactions.addTextChangedListener(
                new TextWatcher() {
                    @Override
                    public void beforeTextChanged(
                            CharSequence text,
                            int start,
                            int count,
                            int after
                    ) {
                    }

                    @Override
                    public void onTextChanged(
                            CharSequence text,
                            int start,
                            int before,
                            int count
                    ) {
                        filterTransactions();
                    }

                    @Override
                    public void afterTextChanged(
                            Editable editable
                    ) {
                    }
                }
        );

        etFromDate.setOnClickListener(
                view -> showDatePicker(true)
        );

        etToDate.setOnClickListener(
                view -> showDatePicker(false)
        );

        btnApplyFilters.setOnClickListener(
                view -> {
                    filterTransactions();
                    setFilterPanelExpanded(false);
                }
        );

        btnResetFilters.setOnClickListener(
                view -> resetAllFilters()
        );

        BubbleTouchAnimator.apply(
                btnApplyFilters
        );

        BubbleTouchAnimator.apply(
                btnResetFilters
        );

        BubbleTouchAnimator.apply(
                btnShowFilters
        );

        BubbleTouchAnimator.apply(
                btnSortTransactions
        );

        btnShowFilters.setOnClickListener(
                view -> setFilterPanelExpanded(
                        transactionFilterPanel
                                .getVisibility()
                                != View.VISIBLE
                )
        );

        btnSortTransactions.setOnClickListener(
                this::showSortMenu
        );
    }

    private void setFilterPanelExpanded(
            boolean expanded
    ) {
        transactionFilterPanel.setVisibility(
                expanded
                        ? View.VISIBLE
                        : View.GONE
        );

        btnShowFilters.setContentDescription(
                expanded
                        ? "Close transaction filters"
                        : "Filter transactions"
        );
    }

    private int getActiveFilterCount() {
        int activeFilters = 0;

        if (!"All Transactions".equals(
                getSelectedText(
                        dropdownTransactionType,
                        "All Transactions"
                )
        )) {
            activeFilters++;
        }

        if (!"All Categories".equals(
                getSelectedText(
                        dropdownTransactionCategory,
                        "All Categories"
                )
        )) {
            activeFilters++;
        }

        if (!"All Accounts".equals(
                getSelectedText(
                        dropdownTransactionAccount,
                        "All Accounts"
                )
        )) {
            activeFilters++;
        }

        if (!"Selected Month".equals(
                getSelectedText(
                        dropdownTransactionPeriod,
                        "Selected Month"
                )
        )) {
            activeFilters++;
        }

        if (!getText(
                etMinAmount
        ).isEmpty()
                || !getText(
                etMaxAmount
        ).isEmpty()) {
            activeFilters++;
        }

        if (filterStartDate != null
                || filterEndDate != null) {
            activeFilters++;
        }

        return activeFilters;
    }

    private void updateFilterSummary() {
        int activeFilters =
                getActiveFilterCount();

        boolean hasCustomSort =
                !SORT_NEWEST.equals(
                        selectedSort
                );

        if (activeFilters == 0
                && !hasCustomSort) {
            txtActiveFilterSummary.setVisibility(
                    View.GONE
            );

            return;
        }

        StringBuilder summary =
                new StringBuilder();

        if (activeFilters > 0) {
            summary.append(
                    activeFilters
            ).append(
                    activeFilters == 1
                            ? " filter applied"
                            : " filters applied"
            );
        }

        if (hasCustomSort) {
            if (summary.length() > 0) {
                summary.append(
                        "  •  "
                );
            }

            summary.append(
                    selectedSort
            );
        }

        txtActiveFilterSummary.setText(
                summary.toString()
        );

        txtActiveFilterSummary.setVisibility(
                View.VISIBLE
        );
    }

    private void showSortMenu(
            View anchor
    ) {
        PopupMenu sortMenu =
                new PopupMenu(
                        this,
                        anchor
                );

        String[] options = {
                SORT_NEWEST,
                SORT_OLDEST,
                SORT_AMOUNT_HIGH,
                SORT_AMOUNT_LOW
        };

        for (int index = 0;
             index < options.length;
             index++) {
            sortMenu.getMenu().add(
                    0,
                    index,
                    index,
                    options[index]
            );
        }

        sortMenu.setOnMenuItemClickListener(
                item -> {
                    selectedSort =
                            item.getTitle()
                                    .toString();

                    filterTransactions();

                    return true;
                }
        );

        sortMenu.show();
    }

    private void setDropdownItems(
            MaterialAutoCompleteTextView dropdown,
            String[] items,
            String selectedItem
    ) {
        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_list_item_1,
                        items
                );

        dropdown.setAdapter(
                adapter
        );

        dropdown.setText(
                selectedItem,
                false
        );
    }

    private void loadTransactions() {
        new Thread(() -> {
            List<Transaction> transactions =
                    DatabaseClient
                            .getInstance(
                                    getApplicationContext()
                            )
                            .getAppDatabase()
                            .transactionDao()
                            .getAllTransactions();

            List<ExpenseItem> expenseItems =
                    DatabaseClient
                            .getInstance(
                                    getApplicationContext()
                            )
                            .getAppDatabase()
                            .expenseItemDao()
                            .getAllExpenseItems();

            Map<Integer, List<ExpenseItem>>
                    groupedItems =
                    new LinkedHashMap<>();

            if (expenseItems != null) {
                for (ExpenseItem item :
                        expenseItems) {
                    List<ExpenseItem> items =
                            groupedItems.get(
                                    item.getTransactionId()
                            );

                    if (items == null) {
                        items =
                                new ArrayList<>();

                        groupedItems.put(
                                item.getTransactionId(),
                                items
                        );
                    }

                    items.add(item);
                }
            }

            runOnUiThread(() -> {
                allTransactions.clear();
                expenseItemsByTransaction.clear();

                if (transactions != null) {
                    allTransactions.addAll(
                            transactions
                    );
                }

                expenseItemsByTransaction.putAll(
                        groupedItems
                );

                updateCategoryFilterOptions();
                updateAccountFilterOptions();
                filterTransactions();
            });
        }).start();
    }

    private void updateCategoryFilterOptions() {
        String selectedCategory =
                getSelectedText(
                        dropdownTransactionCategory,
                        "All Categories"
                );

        Set<String> categorySet =
                new LinkedHashSet<>();

        categorySet.add(
                "All Categories"
        );

        for (Transaction transaction :
                allTransactions) {
            String category =
                    safeText(
                            transaction.getCategory()
                    );

            if (!category.isEmpty()) {
                categorySet.add(
                        category
                );
            }
        }

        List<String> categoryList =
                new ArrayList<>(
                        categorySet
                );

        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_list_item_1,
                        categoryList
                );

        dropdownTransactionCategory.setAdapter(
                adapter
        );

        if (categoryList.contains(
                selectedCategory
        )) {
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
        String selectedAccount =
                getSelectedText(
                        dropdownTransactionAccount,
                        "All Accounts"
                );

        Set<String> accountSet =
                new LinkedHashSet<>();

        accountSet.add(
                "All Accounts"
        );

        for (Transaction transaction :
                allTransactions) {
            String account =
                    safeText(
                            transaction.getAccount()
                    );

            if (!account.isEmpty()) {
                accountSet.add(
                        account
                );
            }
        }

        List<String> accountList =
                new ArrayList<>(
                        accountSet
                );

        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_list_item_1,
                        accountList
                );

        dropdownTransactionAccount.setAdapter(
                adapter
        );

        if (accountList.contains(
                selectedAccount
        )) {
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

    private void showDatePicker(
            boolean isFromDate
    ) {
        Calendar calendar;

        if (isFromDate) {
            calendar =
                    filterStartDate == null
                            ? Calendar.getInstance()
                            : (Calendar)
                            filterStartDate.clone();

        } else {
            calendar =
                    filterEndDate == null
                            ? Calendar.getInstance()
                            : (Calendar)
                            filterEndDate.clone();
        }

        DatePickerDialog dialog =
                new DatePickerDialog(
                        this,
                        (view, year, month, dayOfMonth) -> {
                            Calendar selectedDate =
                                    Calendar.getInstance();

                            selectedDate.set(
                                    year,
                                    month,
                                    dayOfMonth
                            );

                            clearTime(
                                    selectedDate
                            );

                            if (isFromDate) {
                                filterStartDate =
                                        selectedDate;

                            } else {
                                filterEndDate =
                                        selectedDate;
                            }

                            updateDateFields();
                            filterTransactions();
                        },
                        calendar.get(
                                Calendar.YEAR
                        ),
                        calendar.get(
                                Calendar.MONTH
                        ),
                        calendar.get(
                                Calendar.DAY_OF_MONTH
                        )
                );

        dialog.show();
    }

    private void updateDateFields() {
        etFromDate.setText(
                filterStartDate == null
                        ? ""
                        : formatVisibleDate(
                        filterStartDate
                )
        );

        etToDate.setText(
                filterEndDate == null
                        ? ""
                        : formatVisibleDate(
                        filterEndDate
                )
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
                "Selected Month",
                false
        );

        resetSelectedPeriodToCurrentMonth();

        inputMinAmount.setError(null);
        inputMaxAmount.setError(null);

        selectedSort =
                SORT_NEWEST;

        filterTransactions();
    }

    private void filterTransactions() {
        if (transactionContainer == null) {
            return;
        }

        Double minAmount =
                getOptionalAmount(
                        etMinAmount,
                        inputMinAmount
                );

        Double maxAmount =
                getOptionalAmount(
                        etMaxAmount,
                        inputMaxAmount
                );

        if (minAmount == null
                || maxAmount == null) {
            return;
        }

        if (minAmount >= 0
                && maxAmount >= 0
                && minAmount > maxAmount) {
            inputMaxAmount.setError(
                    "Maximum amount must be greater than minimum amount"
            );

            return;
        }

        if (filterStartDate != null
                && filterEndDate != null
                && filterStartDate.after(
                filterEndDate
        )) {
            Toast.makeText(
                    this,
                    "From date cannot be after To date",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        inputMinAmount.setError(null);
        inputMaxAmount.setError(null);

        transactionContainer.removeAllViews();

        String searchText =
                getText(
                        etSearchTransactions
                ).toLowerCase(
                        Locale.getDefault()
                );

        String typeFilter =
                getSelectedText(
                        dropdownTransactionType,
                        "All Transactions"
                );

        String categoryFilter =
                getSelectedText(
                        dropdownTransactionCategory,
                        "All Categories"
                );

        String accountFilter =
                getSelectedText(
                        dropdownTransactionAccount,
                        "All Accounts"
                );

        String periodFilter =
                getSelectedText(
                        dropdownTransactionPeriod,
                        "Selected Month"
                );

        List<Transaction> visibleTransactions =
                new ArrayList<>();

        for (Transaction transaction :
                allTransactions) {
            boolean visible =
                    matchesTypeFilter(
                            transaction,
                            typeFilter
                    )
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
                            && matchesCustomDateRange(
                            transaction
                    )
                            && matchesAmountRange(
                            transaction,
                            minAmount,
                            maxAmount
                    )
                            && matchesSearch(
                            transaction,
                            searchText
                    );

            if (visible) {
                visibleTransactions.add(
                        transaction
                );
            }
        }

        sortTransactions(
                visibleTransactions
        );

        for (Transaction transaction :
                visibleTransactions) {
            addTransactionCard(
                    transaction
            );
        }

        int visibleCount =
                visibleTransactions.size();

        String resultText =
                visibleCount == 1
                        ? "1 transaction found"
                        : visibleCount
                          + " transactions found";

        txtResultCount.setText(
                resultText
        );

        updateFilterSummary();

        txtEmptyTransactions.setVisibility(
                visibleCount == 0
                        ? View.VISIBLE
                        : View.GONE
        );
    }

    private void sortTransactions(
            List<Transaction> transactions
    ) {
        Collections.sort(
                transactions,
                (first, second) -> {
                    if (SORT_AMOUNT_HIGH.equals(
                            selectedSort
                    )) {
                        return Double.compare(
                                second.getAmount(),
                                first.getAmount()
                        );
                    }

                    if (SORT_AMOUNT_LOW.equals(
                            selectedSort
                    )) {
                        return Double.compare(
                                first.getAmount(),
                                second.getAmount()
                        );
                    }

                    long firstTime =
                            getTransactionTime(
                                    first
                            );

                    long secondTime =
                            getTransactionTime(
                                    second
                            );

                    if (SORT_OLDEST.equals(
                            selectedSort
                    )) {
                        return Long.compare(
                                firstTime,
                                secondTime
                        );
                    }

                    return Long.compare(
                            secondTime,
                            firstTime
                    );
                }
        );
    }

    private long getTransactionTime(
            Transaction transaction
    ) {
        Calendar calendar =
                parseTransactionDate(
                        transaction.getDate()
                );

        return calendar == null
                ? 0L
                : calendar.getTimeInMillis();
    }

    private Double getOptionalAmount(
            EditText editText,
            TextInputLayout inputLayout
    ) {
        String amountText =
                getText(
                        editText
                );

        if (amountText.isEmpty()) {
            inputLayout.setError(null);

            return -1.0;
        }

        try {
            double amount =
                    Double.parseDouble(
                            amountText
                    );

            if (amount < 0) {
                inputLayout.setError(
                        "Amount cannot be negative"
                );

                return null;
            }

            inputLayout.setError(null);

            return amount;

        } catch (Exception exception) {
            inputLayout.setError(
                    "Enter a valid amount"
            );

            return null;
        }
    }

    private boolean matchesTypeFilter(
            Transaction transaction,
            String typeFilter
    ) {
        String type =
                safeText(
                        transaction.getType()
                ).toUpperCase(
                        Locale.getDefault()
                );

        if (typeFilter.equals(
                "Income Only"
        )) {
            return type.equals(
                    "INCOME"
            );
        }

        if (typeFilter.equals(
                "Expense Only"
        )) {
            return type.equals(
                    "EXPENSE"
            );
        }

        if (typeFilter.equals(
                "Transfers Only"
        )) {
            return type.equals(
                    "TRANSFER_IN"
            )
                    || type.equals(
                    "TRANSFER_OUT"
            );
        }

        return true;
    }

    private boolean matchesCategoryFilter(
            Transaction transaction,
            String categoryFilter
    ) {
        if (categoryFilter.equals(
                "All Categories"
        )) {
            return true;
        }

        return safeText(
                transaction.getCategory()
        ).equalsIgnoreCase(
                categoryFilter
        );
    }

    private boolean matchesAccountFilter(
            Transaction transaction,
            String accountFilter
    ) {
        if (accountFilter.equals(
                "All Accounts"
        )) {
            return true;
        }

        return safeText(
                transaction.getAccount()
        ).equalsIgnoreCase(
                accountFilter
        );
    }

    private boolean matchesPeriodFilter(
            Transaction transaction,
            String periodFilter
    ) {
        Calendar transactionDate =
                parseTransactionDate(
                        safeText(
                                transaction.getDate()
                        )
                );

        if (transactionDate == null) {
            return false;
        }

        clearTime(
                transactionDate
        );

        if (periodFilter.equals(
                "All Time"
        )) {
            return true;
        }

        if (periodFilter.equals(
                "Selected Month"
        )) {
            return transactionDate.get(
                    Calendar.YEAR
            ) == selectedTransactionPeriod.get(
                    Calendar.YEAR
            )
                    && transactionDate.get(
                    Calendar.MONTH
            ) == selectedTransactionPeriod.get(
                    Calendar.MONTH
            );
        }

        if (periodFilter.equals(
                "Selected Year"
        )) {
            return transactionDate.get(
                    Calendar.YEAR
            ) == selectedTransactionPeriod.get(
                    Calendar.YEAR
            );
        }

        Calendar today =
                Calendar.getInstance();

        clearTime(
                today
        );

        if (periodFilter.equals(
                "Today"
        )) {
            return transactionDate.get(
                    Calendar.YEAR
            ) == today.get(
                    Calendar.YEAR
            )
                    && transactionDate.get(
                    Calendar.DAY_OF_YEAR
            ) == today.get(
                    Calendar.DAY_OF_YEAR
            );
        }

        Calendar weekStart =
                Calendar.getInstance();

        clearTime(
                weekStart
        );

        weekStart.setFirstDayOfWeek(
                Calendar.MONDAY
        );

        int day =
                weekStart.get(
                        Calendar.DAY_OF_WEEK
                );

        int difference =
                day - Calendar.MONDAY;

        if (difference < 0) {
            difference += 7;
        }

        weekStart.add(
                Calendar.DAY_OF_MONTH,
                -difference
        );

        Calendar weekEnd =
                (Calendar)
                        weekStart.clone();

        weekEnd.add(
                Calendar.DAY_OF_MONTH,
                7
        );

        return !transactionDate.before(
                weekStart
        )
                && transactionDate.before(
                weekEnd
        );
    }

    private boolean matchesCustomDateRange(
            Transaction transaction
    ) {
        if (filterStartDate == null
                && filterEndDate == null) {
            return true;
        }

        Calendar transactionDate =
                parseTransactionDate(
                        safeText(
                                transaction.getDate()
                        )
                );

        if (transactionDate == null) {
            return false;
        }

        clearTime(
                transactionDate
        );

        if (filterStartDate != null
                && transactionDate.before(
                filterStartDate
        )) {
            return false;
        }

        if (filterEndDate != null
                && transactionDate.after(
                filterEndDate
        )) {
            return false;
        }

        return true;
    }

    private boolean matchesAmountRange(
            Transaction transaction,
            double minAmount,
            double maxAmount
    ) {
        double amount =
                transaction.getAmount();

        if (minAmount >= 0
                && amount < minAmount) {
            return false;
        }

        if (maxAmount >= 0
                && amount > maxAmount) {
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
                safeText(
                        transaction.getType()
                )
                        + " "
                        + transaction.getAmount()
                        + " "
                        + safeText(
                        transaction.getCategory()
                )
                        + " "
                        + safeText(
                        transaction.getAccount()
                )
                        + " "
                        + safeText(
                        transaction.getNote()
                )
                        + " "
                        + safeText(
                        transaction.getDate()
                );

        List<ExpenseItem> items =
                expenseItemsByTransaction.get(
                        transaction.getId()
                );

        if (items != null) {
            StringBuilder itemText =
                    new StringBuilder(
                            combinedText
                    );

            for (ExpenseItem item :
                    items) {
                itemText.append(' ')
                        .append(
                                safeText(
                                        item.getItemName()
                                )
                        )
                        .append(' ')
                        .append(
                                safeText(
                                        item.getUnit()
                                )
                        );
            }

            combinedText =
                    itemText.toString();
        }

        return combinedText
                .toLowerCase(
                        Locale.getDefault()
                )
                .contains(
                        searchText
                );
    }

    private Calendar parseTransactionDate(
            String dateText
    ) {
        String[] formats = {
                "yyyy-MM-dd HH:mm",
                "yyyy-MM-dd"
        };

        for (String format :
                formats) {
            try {
                Date parsedDate =
                        new SimpleDateFormat(
                                format,
                                Locale.US
                        ).parse(
                                dateText
                        );

                if (parsedDate != null) {
                    Calendar calendar =
                            Calendar.getInstance();

                    calendar.setTime(
                            parsedDate
                    );

                    return calendar;
                }

            } catch (ParseException exception) {
                // अगला date format try होगा।
            }
        }

        return null;
    }

    private void addTransactionCard(
            Transaction transaction
    ) {
        TransactionVisual visual =
                getTransactionVisual(
                        transaction
                );

        MaterialCardView card =
                new MaterialCardView(this);

        card.setCardBackgroundColor(
                getColorValue(
                        R.color.app_surface
                )
        );

        card.setRadius(
                dp(19)
        );

        card.setCardElevation(
                dp(1)
        );

        card.setStrokeColor(
                createTranslucentColor(
                        visual.accentColor,
                        80
                )
        );

        card.setStrokeWidth(
                dp(1)
        );

        card.setClickable(true);
        card.setFocusable(true);

        card.setRippleColor(
                ColorStateList.valueOf(
                        createTranslucentColor(
                                visual.accentColor,
                                35
                        )
                )
        );

        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        cardParams.setMargins(
                0,
                dp(6),
                0,
                dp(6)
        );

        card.setLayoutParams(
                cardParams
        );

        LinearLayout content =
                new LinearLayout(this);

        content.setOrientation(
                LinearLayout.VERTICAL
        );

        content.setPadding(
                dp(15),
                dp(15),
                dp(15),
                dp(13)
        );

        LinearLayout topRow =
                new LinearLayout(this);

        topRow.setOrientation(
                LinearLayout.HORIZONTAL
        );

        topRow.setGravity(
                Gravity.CENTER_VERTICAL
        );

        TextView transactionIcon =
                createTransactionIcon(
                        visual
                );

        topRow.addView(
                transactionIcon
        );

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
                dp(12),
                0,
                dp(8),
                0
        );

        titleContainer.setLayoutParams(
                titleParams
        );

        TextView txtTitle =
                createText(
                        visual.title,
                        16,
                        getColorValue(
                                R.color.app_text_primary
                        ),
                        true
                );

        txtTitle.setMaxLines(2);

        TextView txtAccount =
                createText(
                        getAccountDescription(
                                transaction
                        ),
                        11,
                        getColorValue(
                                R.color.app_text_secondary
                        ),
                        false
                );

        LinearLayout.LayoutParams accountParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        accountParams.setMargins(
                0,
                dp(3),
                0,
                0
        );

        txtAccount.setLayoutParams(
                accountParams
        );

        titleContainer.addView(
                txtTitle
        );

        titleContainer.addView(
                txtAccount
        );

        topRow.addView(
                titleContainer
        );

        LinearLayout amountContainer =
                new LinearLayout(this);

        amountContainer.setOrientation(
                LinearLayout.VERTICAL
        );

        amountContainer.setGravity(
                Gravity.END
        );

        TextView txtAmountLabel =
                createText(
                        visual.amountLabel,
                        9,
                        getColorValue(
                                R.color.app_text_secondary
                        ),
                        false
                );

        txtAmountLabel.setGravity(
                Gravity.END
        );

        TextView txtAmount =
                createText(
                        visual.amountPrefix
                                + formatAmount(
                                transaction.getAmount()
                        ),
                        16,
                        visual.accentColor,
                        true
                );

        txtAmount.setGravity(
                Gravity.END
        );

        txtAmount.setMaxLines(1);

        LinearLayout.LayoutParams amountParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        amountParams.setMargins(
                0,
                dp(3),
                0,
                0
        );

        txtAmount.setLayoutParams(
                amountParams
        );

        amountContainer.addView(
                txtAmountLabel
        );

        amountContainer.addView(
                txtAmount
        );

        topRow.addView(
                amountContainer
        );

        content.addView(
                topRow
        );

        View divider =
                createDivider();

        LinearLayout.LayoutParams dividerParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(1)
                );

        dividerParams.setMargins(
                0,
                dp(13),
                0,
                dp(11)
        );

        divider.setLayoutParams(
                dividerParams
        );

        content.addView(
                divider
        );

        LinearLayout informationRow =
                new LinearLayout(this);

        informationRow.setOrientation(
                LinearLayout.HORIZONTAL
        );

        informationRow.setGravity(
                Gravity.CENTER_VERTICAL
        );

        LinearLayout dateContainer =
                createInformationBlock(
                        "Date",
                        formatTransactionDate(
                                safeText(
                                        transaction.getDate()
                                )
                        )
                );

        LinearLayout.LayoutParams dateParams =
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                );

        dateContainer.setLayoutParams(
                dateParams
        );

        informationRow.addView(
                dateContainer
        );

        TextView typeBadge =
                createTypeBadge(
                        visual
                );

        informationRow.addView(
                typeBadge
        );

        content.addView(
                informationRow
        );

        String note =
                safeText(
                        transaction.getNote()
                );

        if (!note.isEmpty()) {
            content.addView(
                    createNoteView(
                            note
                    )
            );
        }

        List<ExpenseItem> expenseItems =
                expenseItemsByTransaction.get(
                        transaction.getId()
                );

        if (expenseItems != null
                && !expenseItems.isEmpty()) {
            content.addView(
                    createExpenseItemsView(
                            expenseItems
                    )
            );
        }

        if (visual.isTransfer) {
            content.addView(
                    createProtectedInfo()
            );

            card.setOnClickListener(
                    view -> Toast.makeText(
                            TransactionsActivity.this,
                            "Transfer entries are protected and cannot be edited or deleted.",
                            Toast.LENGTH_SHORT
                    ).show()
            );

        } else {
            content.addView(
                    createActionRow(
                            transaction
                    )
            );

            card.setOnClickListener(
                    view -> openEditScreen(
                            transaction
                    )
            );
        }

        card.addView(
                content
        );

        BubbleTouchAnimator.apply(
                card
        );

        transactionContainer.addView(
                card
        );
    }

    private TransactionVisual getTransactionVisual(
            Transaction transaction
    ) {
        String type =
                safeText(
                        transaction.getType()
                ).toUpperCase(
                        Locale.getDefault()
                );

        String category =
                safeText(
                        transaction.getCategory()
                );

        String account =
                safeText(
                        transaction.getAccount()
                );

        if (type.equals(
                "INCOME"
        )) {
            return new TransactionVisual(
                    category.isEmpty()
                            ? "Income"
                            : category,
                    "INCOME",
                    "Income",
                    "+ ",
                    "Received",
                    "+",
                    getColorValue(
                            R.color.success
                    ),
                    false
            );
        }

        if (type.equals(
                "EXPENSE"
        )) {
            return new TransactionVisual(
                    category.isEmpty()
                            ? "Expense"
                            : category,
                    "EXPENSE",
                    "Expense",
                    "− ",
                    "Spent",
                    "−",
                    getColorValue(
                            R.color.expense
                    ),
                    false
            );
        }

        if (type.equals(
                "TRANSFER_IN"
        )) {
            return new TransactionVisual(
                    account.isEmpty()
                            ? "Transfer In"
                            : "Transfer to "
                              + account,
                    "TRANSFER_IN",
                    "Transfer In",
                    "+ ",
                    "Received",
                    "↙",
                    getColorValue(
                            R.color.secondary
                    ),
                    true
            );
        }

        if (type.equals(
                "TRANSFER_OUT"
        )) {
            return new TransactionVisual(
                    account.isEmpty()
                            ? "Transfer Out"
                            : "Transfer from "
                              + account,
                    "TRANSFER_OUT",
                    "Transfer Out",
                    "− ",
                    "Transferred",
                    "↗",
                    getColorValue(
                            R.color.purple
                    ),
                    true
            );
        }

        return new TransactionVisual(
                type.isEmpty()
                        ? "Transaction"
                        : type,
                type,
                "Transaction",
                "",
                "Amount",
                "₹",
                getColorValue(
                        R.color.app_text_secondary
                ),
                false
        );
    }

    private TextView createTransactionIcon(
            TransactionVisual visual
    ) {
        TextView icon =
                new TextView(this);

        icon.setText(
                visual.iconText
        );

        icon.setTextColor(
                visual.accentColor
        );

        icon.setTextSize(20);

        icon.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        icon.setGravity(
                Gravity.CENTER
        );

        GradientDrawable background =
                new GradientDrawable();

        background.setColor(
                createTranslucentColor(
                        visual.accentColor,
                        24
                )
        );

        background.setStroke(
                dp(1),
                createTranslucentColor(
                        visual.accentColor,
                        75
                )
        );

        background.setCornerRadius(
                dp(14)
        );

        icon.setBackground(
                background
        );

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        dp(48),
                        dp(48)
                );

        icon.setLayoutParams(
                params
        );

        return icon;
    }

    private TextView createTypeBadge(
            TransactionVisual visual
    ) {
        TextView badge =
                new TextView(this);

        badge.setText(
                visual.badgeText
        );

        badge.setTextColor(
                visual.accentColor
        );

        badge.setTextSize(10);

        badge.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        badge.setGravity(
                Gravity.CENTER
        );

        badge.setPadding(
                dp(10),
                0,
                dp(10),
                0
        );

        GradientDrawable background =
                new GradientDrawable();

        background.setColor(
                createTranslucentColor(
                        visual.accentColor,
                        20
                )
        );

        background.setStroke(
                dp(1),
                createTranslucentColor(
                        visual.accentColor,
                        65
                )
        );

        background.setCornerRadius(
                dp(13)
        );

        badge.setBackground(
                background
        );

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        dp(30)
                );

        badge.setLayoutParams(
                params
        );

        return badge;
    }

    private LinearLayout createInformationBlock(
            String label,
            String value
    ) {
        LinearLayout container =
                new LinearLayout(this);

        container.setOrientation(
                LinearLayout.VERTICAL
        );

        TextView labelView =
                createText(
                        label,
                        9,
                        getColorValue(
                                R.color.app_text_secondary
                        ),
                        false
                );

        TextView valueView =
                createText(
                        value,
                        12,
                        getColorValue(
                                R.color.app_text_primary
                        ),
                        true
                );

        LinearLayout.LayoutParams valueParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        valueParams.setMargins(
                0,
                dp(3),
                0,
                0
        );

        valueView.setLayoutParams(
                valueParams
        );

        container.addView(
                labelView
        );

        container.addView(
                valueView
        );

        return container;
    }

    private TextView createNoteView(
            String note
    ) {
        TextView noteView =
                createText(
                        "Note: " + note,
                        11,
                        getColorValue(
                                R.color.app_text_secondary
                        ),
                        false
                );

        noteView.setLineSpacing(
                dp(2),
                1f
        );

        noteView.setPadding(
                dp(12),
                dp(10),
                dp(12),
                dp(10)
        );

        GradientDrawable background =
                new GradientDrawable();

        background.setColor(
                getColorValue(
                        R.color.app_surface_soft
                )
        );

        background.setStroke(
                dp(1),
                getColorValue(
                        R.color.app_outline_soft
                )
        );

        background.setCornerRadius(
                dp(12)
        );

        noteView.setBackground(
                background
        );

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        params.setMargins(
                0,
                dp(11),
                0,
                0
        );

        noteView.setLayoutParams(
                params
        );

        return noteView;
    }

    private TextView createExpenseItemsView(
            List<ExpenseItem> items
    ) {
        StringBuilder details =
                new StringBuilder(
                        "Items"
                );

        for (ExpenseItem item :
                items) {
            String quantity =
                    formatItemQuantity(
                            item.getQuantity()
                    );

            String unit =
                    safeText(
                            item.getUnit()
                    );

            details.append("\n• ")
                    .append(
                            safeText(
                                    item.getItemName()
                            )
                    )
                    .append(" — Qty ")
                    .append(
                            quantity
                    );

            if (!unit.isEmpty()
                    && !unit.equalsIgnoreCase(
                    quantity
            )) {
                details.append(' ')
                        .append(
                                unit
                        );
            }

            details.append(" • ")
                    .append(
                            formatAmount(
                                    item.getPrice()
                            )
                    )
                    .append(" each")
                    .append(" • Total ")
                    .append(
                            formatAmount(
                                    item.getTotal()
                            )
                    );
        }

        TextView itemView =
                createText(
                        details.toString(),
                        11,
                        getColorValue(
                                R.color.app_text_primary
                        ),
                        false
                );

        itemView.setLineSpacing(
                dp(2),
                1f
        );

        itemView.setPadding(
                dp(12),
                dp(10),
                dp(12),
                dp(10)
        );

        GradientDrawable background =
                new GradientDrawable();

        background.setColor(
                getColorValue(
                        R.color.expense_surface
                )
        );

        background.setStroke(
                dp(1),
                getColorValue(
                        R.color.expense_outline
                )
        );

        background.setCornerRadius(
                dp(12)
        );

        itemView.setBackground(
                background
        );

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        params.setMargins(
                0,
                dp(11),
                0,
                0
        );

        itemView.setLayoutParams(
                params
        );

        return itemView;
    }

    private String formatItemQuantity(
            double quantity
    ) {
        if (quantity
                == Math.rint(quantity)) {
            return String.format(
                    Locale.US,
                    "%.0f",
                    quantity
            );
        }

        return String.format(
                Locale.US,
                "%.2f",
                quantity
        ).replaceAll(
                "0+$",
                ""
        ).replaceAll(
                "\\.$",
                ""
        );
    }

    private TextView createProtectedInfo() {
        TextView info =
                createText(
                        "Protected transfer entry · Edit and delete are disabled",
                        10,
                        getColorValue(
                                R.color.purple
                        ),
                        true
                );

        info.setGravity(
                Gravity.CENTER_VERTICAL
        );

        info.setPadding(
                dp(12),
                dp(9),
                dp(12),
                dp(9)
        );

        GradientDrawable background =
                new GradientDrawable();

        background.setColor(
                getColorValue(
                        R.color.purple_surface
                )
        );

        background.setStroke(
                dp(1),
                getColorValue(
                        R.color.purple_outline
                )
        );

        background.setCornerRadius(
                dp(12)
        );

        info.setBackground(
                background
        );

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        params.setMargins(
                0,
                dp(11),
                0,
                0
        );

        info.setLayoutParams(
                params
        );

        return info;
    }

    private LinearLayout createActionRow(
            Transaction transaction
    ) {
        LinearLayout actionRow =
                new LinearLayout(this);

        actionRow.setOrientation(
                LinearLayout.HORIZONTAL
        );

        actionRow.setGravity(
                Gravity.END
        );

        LinearLayout.LayoutParams rowParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(42)
                );

        rowParams.setMargins(
                0,
                dp(12),
                0,
                0
        );

        actionRow.setLayoutParams(
                rowParams
        );

        MaterialButton btnEdit =
                createActionButton(
                        "Edit",
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

        MaterialButton btnDelete =
                createActionButton(
                        "Delete",
                        getColorValue(
                                R.color.expense
                        ),
                        getColorValue(
                                R.color.error_surface
                        ),
                        getColorValue(
                                R.color.error_outline
                        )
                );

        LinearLayout.LayoutParams editParams =
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        1f
                );

        editParams.setMargins(
                0,
                0,
                dp(5),
                0
        );

        btnEdit.setLayoutParams(
                editParams
        );

        LinearLayout.LayoutParams deleteParams =
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        1f
                );

        deleteParams.setMargins(
                dp(5),
                0,
                0,
                0
        );

        btnDelete.setLayoutParams(
                deleteParams
        );

        btnEdit.setOnClickListener(
                view -> openEditScreen(
                        transaction
                )
        );

        btnDelete.setOnClickListener(
                view -> confirmDelete(
                        transaction
                )
        );

        BubbleTouchAnimator.apply(
                btnEdit
        );

        BubbleTouchAnimator.apply(
                btnDelete
        );

        actionRow.addView(
                btnEdit
        );

        actionRow.addView(
                btnDelete
        );

        return actionRow;
    }

    private MaterialButton createActionButton(
            String text,
            int textColor,
            int backgroundColor,
            int strokeColor
    ) {
        MaterialButton button =
                new MaterialButton(this);

        button.setText(text);
        button.setTextSize(12);
        button.setTextColor(
                textColor
        );

        button.setAllCaps(false);

        button.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        button.setGravity(
                Gravity.CENTER
        );

        button.setCornerRadius(
                dp(13)
        );

        button.setBackgroundTintList(
                ColorStateList.valueOf(
                        backgroundColor
                )
        );

        button.setStrokeColor(
                ColorStateList.valueOf(
                        strokeColor
                )
        );

        button.setStrokeWidth(
                dp(1)
        );

        button.setInsetTop(0);
        button.setInsetBottom(0);

        return button;
    }

    private View createDivider() {
        View divider =
                new View(this);

        divider.setBackgroundColor(
                getColorValue(
                        R.color.app_divider
                )
        );

        return divider;
    }

    private String getAccountDescription(
            Transaction transaction
    ) {
        String account =
                safeText(
                        transaction.getAccount()
                );

        if (account.isEmpty()) {
            return "Account not specified";
        }

        return "Account: "
                + account;
    }

    private void openEditScreen(
            Transaction transaction
    ) {
        Intent intent =
                new Intent(
                        this,
                        EditTransactionActivity.class
                );

        intent.putExtra(
                "id",
                transaction.getId()
        );

        intent.putExtra(
                "type",
                transaction.getType()
        );

        intent.putExtra(
                "amount",
                transaction.getAmount()
        );

        intent.putExtra(
                "category",
                transaction.getCategory()
        );

        intent.putExtra(
                "account",
                transaction.getAccount()
        );

        intent.putExtra(
                "note",
                transaction.getNote()
        );

        intent.putExtra(
                "date",
                transaction.getDate()
        );

        intent.putExtra(
                "transaction_id",
                transaction.getId()
        );

        intent.putExtra(
                "transaction_type",
                transaction.getType()
        );

        intent.putExtra(
                "transaction_amount",
                transaction.getAmount()
        );

        intent.putExtra(
                "transaction_category",
                transaction.getCategory()
        );

        intent.putExtra(
                "transaction_account",
                transaction.getAccount()
        );

        intent.putExtra(
                "transaction_note",
                transaction.getNote()
        );

        intent.putExtra(
                "transaction_date",
                transaction.getDate()
        );

        startActivity(
                intent
        );
    }

    private void confirmDelete(
            Transaction transaction
    ) {
        String category =
                safeText(
                        transaction.getCategory()
                );

        String description =
                category.isEmpty()
                        ? "this transaction"
                        : "\""
                          + category
                          + "\" transaction";

        new AlertDialog.Builder(this)
                .setTitle(
                        "Delete Transaction"
                )
                .setMessage(
                        "Permanently delete "
                                + description
                                + "?\n\n"
                                + "This action cannot be undone."
                )
                .setNegativeButton(
                        "Cancel",
                        null
                )
                .setPositiveButton(
                        "Delete",
                        (dialog, which) ->
                                deleteTransaction(
                                        transaction
                                )
                )
                .show();
    }

    private void deleteTransaction(
            Transaction transaction
    ) {
        new Thread(() -> {
            DatabaseClient
                    .getInstance(
                            getApplicationContext()
                    )
                    .getAppDatabase()
                    .transactionDao()
                    .delete(
                            transaction
                    );

            runOnUiThread(() -> {
                Toast.makeText(
                        TransactionsActivity.this,
                        "Transaction deleted",
                        Toast.LENGTH_SHORT
                ).show();

                loadTransactions();
            });
        }).start();
    }

    private TextView createText(
            String text,
            float size,
            int color,
            boolean bold
    ) {
        TextView textView =
                new TextView(this);

        textView.setText(
                text
        );

        textView.setTextSize(
                size
        );

        textView.setTextColor(
                color
        );

        if (bold) {
            textView.setTypeface(
                    Typeface.DEFAULT,
                    Typeface.BOLD
            );
        }

        return textView;
    }

    private String getSelectedText(
            MaterialAutoCompleteTextView dropdown,
            String defaultText
    ) {
        String value =
                getText(
                        dropdown
                );

        return value.isEmpty()
                ? defaultText
                : value;
    }

    private String getText(
            TextView view
    ) {
        return view.getText() == null
                ? ""
                : view.getText()
                .toString()
                .trim();
    }

    private String safeText(
            String value
    ) {
        return value == null
                ? ""
                : value.trim();
    }

    private String formatAmount(
            double amount
    ) {
        NumberFormat numberFormat =
                NumberFormat.getNumberInstance(
                        new Locale(
                                "en",
                                "IN"
                        )
                );

        numberFormat.setMinimumFractionDigits(
                2
        );

        numberFormat.setMaximumFractionDigits(
                2
        );

        return "₹"
                + numberFormat.format(
                amount
        );
    }

    private String formatTransactionDate(
            String dateText
    ) {
        String[] formats = {
                "yyyy-MM-dd HH:mm",
                "yyyy-MM-dd"
        };

        for (String format :
                formats) {
            try {
                Date date =
                        new SimpleDateFormat(
                                format,
                                Locale.US
                        ).parse(
                                dateText
                        );

                if (date != null) {
                    return new SimpleDateFormat(
                            "dd MMM yyyy, hh:mm a",
                            Locale.ENGLISH
                    ).format(
                            date
                    );
                }

            } catch (Exception exception) {
                // अगला date format try होगा।
            }
        }

        return dateText.isEmpty()
                ? "Date unavailable"
                : dateText;
    }

    private String formatVisibleDate(
            Calendar calendar
    ) {
        return new SimpleDateFormat(
                "dd MMM yyyy",
                Locale.ENGLISH
        ).format(
                calendar.getTime()
        );
    }

    private void clearTime(
            Calendar calendar
    ) {
        calendar.set(
                Calendar.HOUR_OF_DAY,
                0
        );

        calendar.set(
                Calendar.MINUTE,
                0
        );

        calendar.set(
                Calendar.SECOND,
                0
        );

        calendar.set(
                Calendar.MILLISECOND,
                0
        );
    }

    private int createTranslucentColor(
            int baseColor,
            int alpha
    ) {
        return Color.argb(
                alpha,
                Color.red(
                        baseColor
                ),
                Color.green(
                        baseColor
                ),
                Color.blue(
                        baseColor
                )
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

    private int dp(
            int value
    ) {
        return Math.round(
                value
                        * getResources()
                        .getDisplayMetrics()
                        .density
        );
    }

    private static class TransactionVisual {

        private final String title;
        private final String rawType;
        private final String badgeText;
        private final String amountPrefix;
        private final String amountLabel;
        private final String iconText;
        private final int accentColor;
        private final boolean isTransfer;

        private TransactionVisual(
                String title,
                String rawType,
                String badgeText,
                String amountPrefix,
                String amountLabel,
                String iconText,
                int accentColor,
                boolean isTransfer
        ) {
            this.title =
                    title;

            this.rawType =
                    rawType;

            this.badgeText =
                    badgeText;

            this.amountPrefix =
                    amountPrefix;

            this.amountLabel =
                    amountLabel;

            this.iconText =
                    iconText;

            this.accentColor =
                    accentColor;

            this.isTransfer =
                    isTransfer;
        }
    }
}