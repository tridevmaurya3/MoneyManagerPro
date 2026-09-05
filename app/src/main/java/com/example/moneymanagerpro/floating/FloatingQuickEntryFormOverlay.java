package com.example.moneymanagerpro.floating;

import android.app.DatePickerDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.Editable;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.example.moneymanagerpro.database.AppDatabase;
import com.example.moneymanagerpro.database.DatabaseClient;
import com.example.moneymanagerpro.model.Account;
import com.example.moneymanagerpro.model.Category;
import com.example.moneymanagerpro.model.ExpenseItem;
import com.example.moneymanagerpro.model.Transaction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Real TYPE_APPLICATION_OVERLAY entry form. It does not launch an Activity,
 * so using the floating button never opens the Money Manager app task.
 * Saving uses the same Room entities/DAOs as the in-app Income/Expense forms.
 */
final class FloatingQuickEntryFormOverlay {

    interface Listener {
        void onClosed(FloatingQuickEntryFormOverlay overlay);
    }

    private static final int MAX_ITEM_ROWS = 50;
    private static final String[] UNITS = {
            "Unit", "Piece", "Pack", "Packet", "Box", "Bottle",
            "kg", "g", "mg", "L", "mL", "Dozen", "Pair", "Set",
            "Bag", "Pouch", "Can", "Roll", "Sheet", "m", "cm",
            "mm", "ft", "in", "km", "sq ft", "sq m", "Tablet",
            "Capsule", "Tonne", "Pound"
    };

    private final Context context;
    private final WindowManager windowManager;
    private final boolean expense;
    private final Listener listener;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final Calendar selectedCalendar = Calendar.getInstance();
    private final List<ItemRow> itemRows = new ArrayList<>();

    private View rootView;
    private WindowManager.LayoutParams params;
    private EditText amountField;
    private EditText noteField;
    private Spinner categorySpinner;
    private Spinner accountSpinner;
    private TextView dateField;
    private TextView itemsTotalView;
    private Button addItemButton;
    private Button saveButton;
    private LinearLayout itemsContainer;

    private String selectedDate;
    private boolean dismissed;

    FloatingQuickEntryFormOverlay(
            Context context,
            boolean expense,
            Listener listener
    ) {
        this.context = context.getApplicationContext();
        this.expense = expense;
        this.listener = listener;
        this.windowManager = (WindowManager)
                this.context.getSystemService(Context.WINDOW_SERVICE);
    }

    void show() {
        if (windowManager == null || rootView != null) {
            return;
        }

        updateSelectedDate();
        rootView = buildRoot();

        int screenWidth = context.getResources()
                .getDisplayMetrics().widthPixels;
        int screenHeight = context.getResources()
                .getDisplayMetrics().heightPixels;
        int width = Math.min(
                screenWidth - dp(16),
                Math.max(dp(330), Math.round(screenWidth * 0.92f))
        );
        int height = Math.min(
                screenHeight - dp(70),
                Math.max(dp(440), Math.round(screenHeight * 0.78f))
        );

        params = new WindowManager.LayoutParams(
                width,
                height,
                overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = Math.max(0, (screenWidth - width) / 2);
        params.y = Math.max(dp(24), (screenHeight - height) / 4);
        params.softInputMode =
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;

        rootView.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_OUTSIDE) {
                dismiss();
                return true;
            }
            return false;
        });

        windowManager.addView(rootView, params);
        loadOptions();
    }

    void dismiss() {
        if (dismissed) {
            return;
        }
        dismissed = true;

        if (windowManager != null && rootView != null) {
            try {
                windowManager.removeView(rootView);
            } catch (Exception ignored) {
            }
        }

        rootView = null;
        params = null;
        if (listener != null) {
            listener.onClosed(this);
        }
    }

    private View buildRoot() {
        FrameLayout root = new FrameLayout(context);
        root.setPadding(dp(4), dp(4), dp(4), dp(4));

        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(false);
        scrollView.setClipToPadding(false);
        scrollView.setOverScrollMode(View.OVER_SCROLL_NEVER);

        GradientDrawable formBackground = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{
                        Color.parseColor("#EAF8EE"),
                        Color.parseColor("#FFF2F2"),
                        Color.parseColor("#EEF6FF")
                }
        );
        formBackground.setCornerRadius(dp(22));
        formBackground.setStroke(dp(1), Color.parseColor("#BFD1C8"));
        scrollView.setBackground(formBackground);
        scrollView.setElevation(dp(10));

        LinearLayout body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(14), dp(10), dp(14), dp(14));
        scrollView.addView(
                body,
                new ScrollView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        body.addView(buildHeader());

        amountField = createEditField(
                expense ? "Expense amount" : "Income amount",
                InputType.TYPE_CLASS_NUMBER
                        | InputType.TYPE_NUMBER_FLAG_DECIMAL
        );
        amountField.setTextSize(18);
        amountField.setTypeface(amountField.getTypeface(), Typeface.BOLD);
        addLabeledField(
                body,
                expense ? "Amount" : "Amount",
                amountField,
                dp(7)
        );

        if (expense) {
            buildItemsSection(body);
        }

        categorySpinner = createSpinner();
        addLabeledField(body, "Category", categorySpinner, dp(8));

        accountSpinner = createSpinner();
        addLabeledField(body, "Account", accountSpinner, dp(8));

        dateField = createDateField();
        addLabeledField(body, "Date", dateField, dp(8));

        noteField = createEditField(
                "Note (optional)",
                InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                        | InputType.TYPE_TEXT_FLAG_MULTI_LINE
        );
        noteField.setMinLines(2);
        noteField.setMaxLines(3);
        noteField.setGravity(Gravity.TOP | Gravity.START);
        noteField.setFilters(new InputFilter[]{
                new InputFilter.LengthFilter(250)
        });
        addLabeledField(body, "Note", noteField, dp(8));

        saveButton = new Button(context);
        saveButton.setAllCaps(false);
        saveButton.setText(expense ? "Save Expense" : "Save Income");
        saveButton.setTextColor(Color.WHITE);
        saveButton.setTextSize(15);
        saveButton.setTypeface(saveButton.getTypeface(), Typeface.BOLD);
        saveButton.setMinHeight(0);
        saveButton.setPadding(dp(12), dp(10), dp(12), dp(10));
        saveButton.setBackground(
                rounded(
                        expense ? "#C42B1C" : "#107C10",
                        expense ? "#C42B1C" : "#107C10",
                        15
                )
        );
        LinearLayout.LayoutParams saveParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(48)
                );
        saveParams.topMargin = dp(10);
        body.addView(saveButton, saveParams);
        saveButton.setOnClickListener(view -> save());

        FrameLayout.LayoutParams scrollParams =
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                );
        root.addView(scrollView, scrollParams);

        addResizeHandles(root);
        return root;
    }

    private View buildHeader() {
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(2), dp(1), dp(2), dp(5));

        TextView title = new TextView(context);
        title.setText(expense ? "−  Add Expense" : "+  Add Income");
        title.setTextColor(
                Color.parseColor(expense ? "#A92518" : "#0B6B28")
        );
        title.setTextSize(18);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        header.addView(
                title,
                new LinearLayout.LayoutParams(
                        0,
                        dp(40),
                        1f
                )
        );
        title.setGravity(Gravity.CENTER_VERTICAL);

        TextView close = new TextView(context);
        close.setText("×");
        close.setGravity(Gravity.CENTER);
        close.setTextSize(22);
        close.setTextColor(Color.parseColor("#52655B"));
        close.setBackground(rounded("#DFF2E8", "#B8D3C4", 14));
        header.addView(
                close,
                new LinearLayout.LayoutParams(dp(36), dp(36))
        );
        close.setOnClickListener(view -> dismiss());

        header.setOnTouchListener(new MoveTouchListener());
        return header;
    }

    private void buildItemsSection(LinearLayout body) {
        LinearLayout headingRow = new LinearLayout(context);
        headingRow.setOrientation(LinearLayout.HORIZONTAL);
        headingRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView heading = label("Optional Item Details");
        headingRow.addView(
                heading,
                new LinearLayout.LayoutParams(0, dp(32), 1f)
        );

        addItemButton = new Button(context);
        addItemButton.setAllCaps(false);
        addItemButton.setText("+ Item");
        addItemButton.setTextSize(11);
        addItemButton.setTextColor(Color.parseColor("#A92518"));
        addItemButton.setMinHeight(0);
        addItemButton.setPadding(dp(8), 0, dp(8), 0);
        addItemButton.setBackground(rounded("#FFF8F7", "#E6B9B3", 12));
        headingRow.addView(
                addItemButton,
                new LinearLayout.LayoutParams(dp(72), dp(34))
        );

        LinearLayout.LayoutParams headingParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        headingParams.topMargin = dp(8);
        body.addView(headingRow, headingParams);

        itemsContainer = new LinearLayout(context);
        itemsContainer.setOrientation(LinearLayout.VERTICAL);
        body.addView(
                itemsContainer,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        itemsTotalView = new TextView(context);
        itemsTotalView.setText("Items total: ₹0.00");
        itemsTotalView.setGravity(Gravity.END);
        itemsTotalView.setTextColor(Color.parseColor("#9A3025"));
        itemsTotalView.setTextSize(11);
        itemsTotalView.setTypeface(
                itemsTotalView.getTypeface(),
                Typeface.BOLD
        );
        LinearLayout.LayoutParams totalParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        totalParams.topMargin = dp(3);
        body.addView(itemsTotalView, totalParams);

        addItemButton.setOnClickListener(view -> addItemRow());
        addItemRow();
    }

    private void addItemRow() {
        if (itemRows.size() >= MAX_ITEM_ROWS) {
            Toast.makeText(
                    context,
                    "A maximum of 50 items can be added",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(8), dp(7), dp(8), dp(7));
        card.setBackground(rounded("#F9FFFFFF", "#E5C7C2", 13));

        LinearLayout top = new LinearLayout(context);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        EditText name = createEditField(
                "Item name",
                InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_FLAG_CAP_WORDS
        );
        top.addView(
                name,
                new LinearLayout.LayoutParams(0, dp(43), 1f)
        );

        TextView remove = new TextView(context);
        remove.setText("×");
        remove.setGravity(Gravity.CENTER);
        remove.setTextColor(Color.parseColor("#B3261E"));
        remove.setTextSize(18);
        LinearLayout.LayoutParams removeParams =
                new LinearLayout.LayoutParams(dp(32), dp(36));
        removeParams.leftMargin = dp(5);
        top.addView(remove, removeParams);
        card.addView(top);

        LinearLayout details = new LinearLayout(context);
        details.setOrientation(LinearLayout.HORIZONTAL);
        details.setGravity(Gravity.CENTER_VERTICAL);

        EditText quantity = createEditField(
                "Qty",
                InputType.TYPE_CLASS_NUMBER
                        | InputType.TYPE_NUMBER_FLAG_DECIMAL
        );
        Spinner unit = createSpinner();
        ArrayAdapter<String> unitAdapter = new ArrayAdapter<>(
                context,
                android.R.layout.simple_spinner_item,
                Arrays.asList(UNITS)
        );
        unitAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );
        unit.setAdapter(unitAdapter);

        EditText price = createEditField(
                "Price",
                InputType.TYPE_CLASS_NUMBER
                        | InputType.TYPE_NUMBER_FLAG_DECIMAL
        );

        LinearLayout.LayoutParams cell =
                new LinearLayout.LayoutParams(0, dp(43), 1f);
        cell.topMargin = dp(5);
        details.addView(quantity, cell);

        LinearLayout.LayoutParams unitCell =
                new LinearLayout.LayoutParams(0, dp(43), 1.15f);
        unitCell.topMargin = dp(5);
        unitCell.leftMargin = dp(5);
        details.addView(unit, unitCell);

        LinearLayout.LayoutParams priceCell =
                new LinearLayout.LayoutParams(0, dp(43), 1.15f);
        priceCell.topMargin = dp(5);
        priceCell.leftMargin = dp(5);
        details.addView(price, priceCell);
        card.addView(details);

        TextView total = new TextView(context);
        total.setText("₹0.00");
        total.setGravity(Gravity.END);
        total.setTextSize(10);
        total.setTextColor(Color.parseColor("#81514B"));
        card.addView(
                total,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(20)
                )
        );

        ItemRow row = new ItemRow(
                card,
                name,
                quantity,
                unit,
                price,
                total
        );
        itemRows.add(row);

        TextWatcher watcher = new TextWatcher() {
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
                updateItemTotal(row);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        };
        quantity.addTextChangedListener(watcher);
        price.addTextChangedListener(watcher);

        remove.setOnClickListener(view -> {
            itemRows.remove(row);
            itemsContainer.removeView(card);
            updateItemsTotal();
            if (addItemButton != null) {
                addItemButton.setEnabled(itemRows.size() < MAX_ITEM_ROWS);
            }
        });

        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        cardParams.topMargin = dp(4);
        itemsContainer.addView(card, cardParams);
        addItemButton.setEnabled(itemRows.size() < MAX_ITEM_ROWS);
        updateItemsTotal();
    }

    private void updateItemTotal(ItemRow row) {
        BigDecimal quantity = parsePositive(row.quantity.getText().toString());
        BigDecimal price = parsePositive(row.price.getText().toString());
        BigDecimal total = BigDecimal.ZERO;
        if (quantity != null && price != null) {
            total = quantity.multiply(price)
                    .setScale(2, RoundingMode.HALF_UP);
        }
        row.total.setText("₹" + money(total));
        updateItemsTotal();
    }

    private void updateItemsTotal() {
        if (itemsTotalView == null) {
            return;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (ItemRow row : itemRows) {
            BigDecimal quantity = parsePositive(
                    row.quantity.getText().toString()
            );
            BigDecimal price = parsePositive(
                    row.price.getText().toString()
            );
            if (quantity != null && price != null) {
                sum = sum.add(quantity.multiply(price));
            }
        }
        itemsTotalView.setText(
                "Items total: ₹"
                        + money(sum.setScale(2, RoundingMode.HALF_UP))
        );
    }

    private void loadOptions() {
        new Thread(() -> {
            List<Category> categories = DatabaseClient
                    .getInstance(context)
                    .getAppDatabase()
                    .categoryDao()
                    .getAllCategories();
            List<Account> accounts = DatabaseClient
                    .getInstance(context)
                    .getAppDatabase()
                    .accountDao()
                    .getAllAccounts();

            List<String> categoryNames = new ArrayList<>();
            List<String> accountNames = new ArrayList<>();
            String expectedType = expense ? "expense" : "income";

            for (Category category : categories) {
                if (category.getType() != null
                        && category.getType().equalsIgnoreCase(expectedType)
                        && category.getName() != null
                        && !category.getName().trim().isEmpty()) {
                    categoryNames.add(category.getName());
                }
            }

            if (categoryNames.isEmpty()) {
                if (expense) {
                    categoryNames.addAll(Arrays.asList(
                            "Food", "Travel", "Shopping", "Bills",
                            "Other Expense"
                    ));
                } else {
                    categoryNames.addAll(Arrays.asList(
                            "Salary", "Business", "Freelancing",
                            "Interest", "Other Income"
                    ));
                }
            }

            for (Account account : accounts) {
                if (account.getName() != null
                        && !account.getName().trim().isEmpty()) {
                    accountNames.add(account.getName());
                }
            }
            if (accountNames.isEmpty()) {
                accountNames.add("Cash");
            }

            uiHandler.post(() -> {
                if (dismissed || categorySpinner == null
                        || accountSpinner == null) {
                    return;
                }
                setSpinnerData(categorySpinner, categoryNames);
                setSpinnerData(accountSpinner, accountNames);
            });
        }).start();
    }

    private void setSpinnerData(
            Spinner spinner,
            List<String> values
    ) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                context,
                android.R.layout.simple_spinner_item,
                values
        );
        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );
        spinner.setAdapter(adapter);
    }

    private void save() {
        String amountText = clean(amountField);
        List<ExpenseItem> expenseItems = new ArrayList<>();

        if (expense) {
            expenseItems = collectExpenseItems();
            if (expenseItems == null) {
                return;
            }
            if (amountText.isEmpty() && !expenseItems.isEmpty()) {
                BigDecimal total = BigDecimal.ZERO;
                for (ExpenseItem item : expenseItems) {
                    total = total.add(BigDecimal.valueOf(item.getTotal()));
                }
                amountText = money(total);
                amountField.setText(amountText);
            }
        }

        BigDecimal amount = parsePositive(amountText);
        if (amount == null) {
            amountField.setError(
                    expense
                            ? "Enter a valid expense amount"
                            : "Enter a valid income amount"
            );
            amountField.requestFocus();
            return;
        }

        String category = selected(categorySpinner);
        String account = selected(accountSpinner);
        if (category.isEmpty() || account.isEmpty()) {
            Toast.makeText(
                    context,
                    "Select category and account",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        Transaction transaction = new Transaction();
        transaction.setType(expense ? "EXPENSE" : "INCOME");
        transaction.setAmount(amount.doubleValue());
        transaction.setCategory(category);
        transaction.setAccount(account);
        transaction.setNote(clean(noteField));
        transaction.setDate(selectedDate);

        saveButton.setEnabled(false);
        saveButton.setText(expense ? "Saving Expense…" : "Saving Income…");
        final List<ExpenseItem> itemsToSave = expenseItems;

        new Thread(() -> {
            try {
                AppDatabase database = DatabaseClient
                        .getInstance(context)
                        .getAppDatabase();

                if (expense) {
                    database.runInTransaction(() -> {
                        long transactionId = database
                                .transactionDao()
                                .insert(transaction);
                        if (transactionId <= 0
                                || transactionId > Integer.MAX_VALUE) {
                            throw new IllegalStateException(
                                    "Invalid transaction ID"
                            );
                        }
                        for (ExpenseItem item : itemsToSave) {
                            item.setTransactionId((int) transactionId);
                        }
                        if (!itemsToSave.isEmpty()) {
                            database.expenseItemDao()
                                    .insertAll(itemsToSave);
                        }
                    });
                } else {
                    database.transactionDao().insert(transaction);
                }

                uiHandler.post(() -> {
                    Toast.makeText(
                            context,
                            expense
                                    ? "Expense saved successfully"
                                    : "Income saved successfully",
                            Toast.LENGTH_SHORT
                    ).show();
                    dismiss();
                });
            } catch (Exception exception) {
                uiHandler.post(() -> {
                    if (saveButton != null) {
                        saveButton.setEnabled(true);
                        saveButton.setText(
                                expense ? "Save Expense" : "Save Income"
                        );
                    }
                    Toast.makeText(
                            context,
                            expense
                                    ? "Unable to save expense"
                                    : "Unable to save income",
                            Toast.LENGTH_SHORT
                    ).show();
                });
            }
        }).start();
    }

    private List<ExpenseItem> collectExpenseItems() {
        List<ExpenseItem> result = new ArrayList<>();

        for (ItemRow row : itemRows) {
            String name = clean(row.name);
            String quantityText = clean(row.quantity);
            String priceText = clean(row.price);

            if (name.isEmpty()
                    && quantityText.isEmpty()
                    && priceText.isEmpty()) {
                continue;
            }

            if (name.isEmpty()) {
                row.name.setError("Enter item name");
                row.name.requestFocus();
                return null;
            }

            BigDecimal quantity = parsePositive(quantityText);
            if (quantity == null) {
                row.quantity.setError("Enter quantity");
                row.quantity.requestFocus();
                return null;
            }

            BigDecimal price = parsePositive(priceText);
            if (price == null) {
                row.price.setError("Enter price");
                row.price.requestFocus();
                return null;
            }

            String unit = selected(row.unit);
            if (unit.isEmpty()) {
                unit = "Unit";
            }

            BigDecimal total = quantity.multiply(price)
                    .setScale(2, RoundingMode.HALF_UP);
            ExpenseItem item = new ExpenseItem();
            item.setItemName(name);
            item.setQuantity(quantity.doubleValue());
            item.setUnit(unit);
            item.setPrice(price.doubleValue());
            item.setTotal(total.doubleValue());
            item.setSortOrder(result.size());
            result.add(item);
        }

        return result;
    }

    private TextView createDateField() {
        TextView field = new TextView(context);
        field.setText(
                new SimpleDateFormat(
                        "dd MMM yyyy",
                        Locale.ENGLISH
                ).format(selectedCalendar.getTime())
        );
        field.setTextSize(14);
        field.setTextColor(Color.parseColor("#26332D"));
        field.setGravity(Gravity.CENTER_VERTICAL);
        field.setPadding(dp(12), 0, dp(12), 0);
        field.setBackground(rounded("#F8FFFFFF", "#C8D5CF", 12));
        field.setOnClickListener(view -> showDatePicker());
        return field;
    }

    private void showDatePicker() {
        ContextThemeWrapper themed = new ContextThemeWrapper(
                context,
                android.R.style.Theme_Material_Light_Dialog_Alert
        );
        DatePickerDialog dialog = new DatePickerDialog(
                themed,
                (view, year, month, dayOfMonth) -> {
                    selectedCalendar.set(Calendar.YEAR, year);
                    selectedCalendar.set(Calendar.MONTH, month);
                    selectedCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                    updateSelectedDate();
                    if (dateField != null) {
                        dateField.setText(
                                new SimpleDateFormat(
                                        "dd MMM yyyy",
                                        Locale.ENGLISH
                                ).format(selectedCalendar.getTime())
                        );
                    }
                },
                selectedCalendar.get(Calendar.YEAR),
                selectedCalendar.get(Calendar.MONTH),
                selectedCalendar.get(Calendar.DAY_OF_MONTH)
        );
        Window dialogWindow = dialog.getWindow();
        if (dialogWindow != null) {
            dialogWindow.setType(overlayType());
        }
        dialog.show();
    }

    private void updateSelectedDate() {
        selectedDate = new SimpleDateFormat(
                "yyyy-MM-dd HH:mm",
                Locale.US
        ).format(selectedCalendar.getTime());
    }

    private void addLabeledField(
            LinearLayout body,
            String labelText,
            View field,
            int topMargin
    ) {
        TextView label = label(labelText);
        LinearLayout.LayoutParams labelParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        labelParams.topMargin = topMargin;
        body.addView(label, labelParams);

        LinearLayout.LayoutParams fieldParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        field == noteField ? dp(66) : dp(46)
                );
        fieldParams.topMargin = dp(3);
        body.addView(field, fieldParams);
    }

    private TextView label(String text) {
        TextView label = new TextView(context);
        label.setText(text);
        label.setTextColor(Color.parseColor("#37463F"));
        label.setTextSize(11);
        label.setTypeface(label.getTypeface(), Typeface.BOLD);
        return label;
    }

    private EditText createEditField(
            String hint,
            int inputType
    ) {
        EditText field = new EditText(context);
        field.setHint(hint);
        field.setHintTextColor(Color.parseColor("#839088"));
        field.setTextColor(Color.parseColor("#26332D"));
        field.setTextSize(14);
        field.setSingleLine(
                (inputType & InputType.TYPE_TEXT_FLAG_MULTI_LINE) == 0
        );
        field.setInputType(inputType);
        field.setPadding(dp(12), dp(5), dp(12), dp(5));
        field.setBackground(rounded("#F8FFFFFF", "#C8D5CF", 12));
        return field;
    }

    private Spinner createSpinner() {
        Spinner spinner = new Spinner(context, Spinner.MODE_DROPDOWN);
        spinner.setPadding(dp(8), 0, dp(8), 0);
        spinner.setBackground(rounded("#F8FFFFFF", "#C8D5CF", 12));
        return spinner;
    }

    private GradientDrawable rounded(
            String fill,
            String stroke,
            int radiusDp
    ) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.parseColor(fill));
        background.setCornerRadius(dp(radiusDp));
        background.setStroke(dp(1), Color.parseColor(stroke));
        return background;
    }

    private void addResizeHandles(FrameLayout root) {
        addResizeHandle(root, Gravity.TOP | Gravity.START, -1, -1, "↖");
        addResizeHandle(root, Gravity.TOP | Gravity.END, 1, -1, "↗");
        addResizeHandle(root, Gravity.BOTTOM | Gravity.START, -1, 1, "↙");
        addResizeHandle(root, Gravity.BOTTOM | Gravity.END, 1, 1, "↘");
    }

    private void addResizeHandle(
            FrameLayout root,
            int gravity,
            int horizontalDirection,
            int verticalDirection,
            String symbol
    ) {
        TextView handle = new TextView(context);
        handle.setText(symbol);
        handle.setGravity(Gravity.CENTER);
        handle.setTextSize(13);
        handle.setTextColor(Color.parseColor("#4D6A5D"));
        handle.setBackground(rounded("#EAF5EF", "#B9CDC3", 8));
        FrameLayout.LayoutParams handleParams =
                new FrameLayout.LayoutParams(dp(26), dp(26), gravity);
        root.addView(handle, handleParams);
        handle.setOnTouchListener(
                new ResizeTouchListener(
                        horizontalDirection,
                        verticalDirection
                )
        );
    }

    private int overlayType() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
    }

    private String selected(Spinner spinner) {
        if (spinner == null || spinner.getSelectedItem() == null) {
            return "";
        }
        return spinner.getSelectedItem().toString().trim();
    }

    private String clean(EditText field) {
        return field == null || field.getText() == null
                ? ""
                : field.getText().toString().trim();
    }

    private BigDecimal parsePositive(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        try {
            BigDecimal value = new BigDecimal(text.trim());
            return value.compareTo(BigDecimal.ZERO) > 0
                    ? value
                    : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String money(BigDecimal amount) {
        return String.format(
                Locale.US,
                "%.2f",
                amount.doubleValue()
        );
    }

    private int dp(int value) {
        return Math.round(
                value
                        * context.getResources()
                        .getDisplayMetrics().density
        );
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private final class MoveTouchListener implements View.OnTouchListener {
        private int startX;
        private int startY;
        private float downX;
        private float downY;

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            if (params == null || windowManager == null) {
                return false;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                startX = params.x;
                startY = params.y;
                downX = event.getRawX();
                downY = event.getRawY();
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                params.x = startX + Math.round(event.getRawX() - downX);
                params.y = startY + Math.round(event.getRawY() - downY);
                int screenWidth = context.getResources()
                        .getDisplayMetrics().widthPixels;
                int screenHeight = context.getResources()
                        .getDisplayMetrics().heightPixels;
                params.x = clamp(
                        params.x,
                        -params.width / 2,
                        screenWidth - params.width / 2
                );
                params.y = clamp(
                        params.y,
                        dp(10),
                        screenHeight - dp(80)
                );
                windowManager.updateViewLayout(rootView, params);
                return true;
            }
            return event.getActionMasked() == MotionEvent.ACTION_UP
                    || event.getActionMasked() == MotionEvent.ACTION_CANCEL;
        }
    }

    private final class ResizeTouchListener implements View.OnTouchListener {
        private final int horizontalDirection;
        private final int verticalDirection;
        private float startRawX;
        private float startRawY;
        private int startWidth;
        private int startHeight;

        ResizeTouchListener(
                int horizontalDirection,
                int verticalDirection
        ) {
            this.horizontalDirection = horizontalDirection;
            this.verticalDirection = verticalDirection;
        }

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            if (params == null || windowManager == null) {
                return false;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                startRawX = event.getRawX();
                startRawY = event.getRawY();
                startWidth = params.width;
                startHeight = params.height;
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                int screenWidth = context.getResources()
                        .getDisplayMetrics().widthPixels;
                int screenHeight = context.getResources()
                        .getDisplayMetrics().heightPixels;
                int deltaX = Math.round(event.getRawX() - startRawX);
                int deltaY = Math.round(event.getRawY() - startRawY);
                params.width = clamp(
                        startWidth + horizontalDirection * deltaX,
                        Math.min(dp(300), screenWidth - dp(12)),
                        screenWidth - dp(8)
                );
                params.height = clamp(
                        startHeight + verticalDirection * deltaY,
                        Math.min(dp(390), screenHeight - dp(60)),
                        screenHeight - dp(34)
                );
                windowManager.updateViewLayout(rootView, params);
                return true;
            }
            return event.getActionMasked() == MotionEvent.ACTION_UP
                    || event.getActionMasked() == MotionEvent.ACTION_CANCEL;
        }
    }

    private static final class ItemRow {
        final View root;
        final EditText name;
        final EditText quantity;
        final Spinner unit;
        final EditText price;
        final TextView total;

        ItemRow(
                View root,
                EditText name,
                EditText quantity,
                Spinner unit,
                EditText price,
                TextView total
        ) {
            this.root = root;
            this.name = name;
            this.quantity = quantity;
            this.unit = unit;
            this.price = price;
            this.total = total;
        }
    }
}
