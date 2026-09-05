package com.example.moneymanagerpro.floating;

import android.app.DatePickerDialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
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
 * Compact TYPE_APPLICATION_OVERLAY quick-entry form.
 *
 * The overlay never launches an Activity. It saves through the same Room
 * entities and DAOs used by the in-app Income / Expense flows. The layout is
 * intentionally overlay-first: compact rows, independently scrollable option
 * popups, a fixed header and root-level corner resize gestures.
 */
final class FloatingQuickEntryFormOverlay {

    interface Listener {
        void onClosed(FloatingQuickEntryFormOverlay overlay);
    }

    private static final int MAX_ITEM_ROWS = 50;
    private static final int CORNER_TOUCH_DP = 28;
    private static final int CORNER_GUIDE_DP = 14;

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

    private ResizableRoot rootView;
    private WindowManager.LayoutParams params;
    private ScrollView bodyScroll;
    private LinearLayout itemsContainer;
    private EditText amountField;
    private EditText noteField;
    private OverlayDropdown categoryDropdown;
    private OverlayDropdown accountDropdown;
    private TextView dateField;
    private TextView itemsTotalView;
    private TextView addItemButton;
    private TextView saveButton;

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

        dismissed = false;
        updateSelectedDate();
        rootView = buildRoot();

        int screenWidth = context.getResources()
                .getDisplayMetrics().widthPixels;
        int screenHeight = context.getResources()
                .getDisplayMetrics().heightPixels;

        int width = Math.min(
                screenWidth - dp(18),
                Math.max(dp(318), Math.round(screenWidth * 0.88f))
        );
        int targetHeight = Math.round(
                screenHeight * (expense ? 0.68f : 0.55f)
        );
        int height = Math.min(
                screenHeight - dp(78),
                Math.max(dp(expense ? 410 : 350), targetHeight)
        );

        params = new WindowManager.LayoutParams(
                width,
                height,
                overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = Math.max(0, (screenWidth - width) / 2);
        params.y = Math.max(dp(22), (screenHeight - height) / 4);
        params.softInputMode =
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;

        rootView.setFocusableInTouchMode(true);
        rootView.setOnKeyListener((view, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_BACK
                    && event.getAction() == KeyEvent.ACTION_UP) {
                dismiss();
                return true;
            }
            return false;
        });

        windowManager.addView(rootView, params);
        rootView.requestFocus();
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
        bodyScroll = null;
        if (listener != null) {
            listener.onClosed(this);
        }
    }

    private ResizableRoot buildRoot() {
        ResizableRoot root = new ResizableRoot(context);
        root.setPadding(dp(4), dp(4), dp(4), dp(4));
        root.setClipToPadding(false);

        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(10), dp(7), dp(10), dp(9));
        panel.setBackground(formGradient());
        panel.setElevation(dp(10));

        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        root.addView(panel, panelParams);

        panel.addView(
                buildHeader(),
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(38)
                )
        );

        bodyScroll = new ScrollView(context);
        bodyScroll.setFillViewport(false);
        bodyScroll.setClipToPadding(false);
        bodyScroll.setVerticalScrollBarEnabled(true);
        bodyScroll.setScrollbarFadingEnabled(false);
        bodyScroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);

        LinearLayout body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(1), dp(2), dp(1), dp(2));
        bodyScroll.addView(
                body,
                new ScrollView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        LinearLayout.LayoutParams scrollParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0,
                        1f
                );
        panel.addView(bodyScroll, scrollParams);

        amountField = createEditField(
                expense ? "Expense amount" : "Income amount",
                InputType.TYPE_CLASS_NUMBER
                        | InputType.TYPE_NUMBER_FLAG_DECIMAL
        );
        amountField.setTextSize(16);
        amountField.setTypeface(amountField.getTypeface(), Typeface.BOLD);
        addLabeledField(body, "Amount", amountField, dp(2), dp(40));

        if (expense) {
            buildItemsSection(body);
        }

        categoryDropdown = new OverlayDropdown("Select category");
        accountDropdown = new OverlayDropdown("Select account");
        body.addView(buildCategoryAccountRow());

        dateField = createDateField();
        addLabeledField(body, "Date", dateField, dp(5), dp(38));

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
        addLabeledField(body, "Note", noteField, dp(5), dp(52));

        saveButton = actionButton(
                expense ? "Save Expense" : "Save Income",
                expense ? "#C42B1C" : "#107C10"
        );
        LinearLayout.LayoutParams saveParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(42)
                );
        saveParams.topMargin = dp(7);
        body.addView(saveButton, saveParams);
        saveButton.setOnClickListener(view -> save());

        return root;
    }

    private GradientDrawable formGradient() {
        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{
                        Color.parseColor("#EDF9F0"),
                        Color.parseColor("#FFF3F3"),
                        Color.parseColor("#EFF7FF")
                }
        );
        background.setCornerRadius(dp(20));
        background.setStroke(dp(1), Color.parseColor("#BDD0C7"));
        return background;
    }

    private View buildHeader() {
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(2), 0, dp(1), dp(2));

        TextView title = new TextView(context);
        title.setText(expense ? "−  Add Expense" : "+  Add Income");
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setTextColor(
                Color.parseColor(expense ? "#A92518" : "#0B6B28")
        );
        title.setTextSize(16);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        title.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(
                title,
                new LinearLayout.LayoutParams(0, dp(34), 1f)
        );

        TextView close = new TextView(context);
        close.setText("×");
        close.setContentDescription("Close floating form");
        close.setGravity(Gravity.CENTER);
        close.setTextSize(20);
        close.setTextColor(Color.parseColor("#52655B"));
        close.setBackground(rounded("#E4F4EB", "#B9D2C5", 12));
        header.addView(
                close,
                new LinearLayout.LayoutParams(dp(32), dp(32))
        );
        close.setOnClickListener(view -> dismiss());

        header.setOnTouchListener(new MoveTouchListener());
        return header;
    }

    private View buildCategoryAccountRow() {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.TOP);

        LinearLayout categoryColumn = compactColumn(
                "Category",
                categoryDropdown,
                dp(38)
        );
        LinearLayout accountColumn = compactColumn(
                "Account",
                accountDropdown,
                dp(38)
        );

        LinearLayout.LayoutParams leftParams =
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        leftParams.topMargin = dp(5);
        row.addView(categoryColumn, leftParams);

        LinearLayout.LayoutParams rightParams =
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.22f);
        rightParams.topMargin = dp(5);
        rightParams.leftMargin = dp(6);
        row.addView(accountColumn, rightParams);
        return row;
    }

    private LinearLayout compactColumn(
            String labelText,
            View field,
            int fieldHeight
    ) {
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        TextView label = label(labelText);
        column.addView(
                label,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(17)
                )
        );
        column.addView(
                field,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        fieldHeight
                )
        );
        return column;
    }

    private void buildItemsSection(LinearLayout body) {
        LinearLayout headingRow = new LinearLayout(context);
        headingRow.setOrientation(LinearLayout.HORIZONTAL);
        headingRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView heading = label("Optional Item Details");
        headingRow.addView(
                heading,
                new LinearLayout.LayoutParams(0, dp(28), 1f)
        );

        addItemButton = actionButton("+ Item", "#A92518");
        addItemButton.setTextSize(10.5f);
        addItemButton.setTextColor(Color.parseColor("#A92518"));
        addItemButton.setBackground(rounded("#FFF8F7", "#E3B8B1", 11));
        headingRow.addView(
                addItemButton,
                new LinearLayout.LayoutParams(dp(62), dp(30))
        );

        LinearLayout.LayoutParams headingParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        headingParams.topMargin = dp(4);
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
        itemsTotalView.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        itemsTotalView.setTextColor(Color.parseColor("#9A3025"));
        itemsTotalView.setTextSize(10);
        itemsTotalView.setTypeface(
                itemsTotalView.getTypeface(),
                Typeface.BOLD
        );
        body.addView(
                itemsTotalView,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(18)
                )
        );

        addItemButton.setOnClickListener(view -> {
            addItemRow();
            if (bodyScroll != null) {
                bodyScroll.post(() -> bodyScroll.smoothScrollTo(
                        0,
                        Math.max(0, itemsContainer.getBottom() - dp(80))
                ));
            }
        });
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
        card.setPadding(dp(6), dp(5), dp(6), dp(4));
        card.setBackground(rounded("#F8FFFFFF", "#E5C7C2", 11));

        LinearLayout nameRow = new LinearLayout(context);
        nameRow.setOrientation(LinearLayout.HORIZONTAL);
        nameRow.setGravity(Gravity.CENTER_VERTICAL);

        EditText name = createEditField(
                "Item name",
                InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_FLAG_CAP_WORDS
        );
        name.setTextSize(12.5f);
        nameRow.addView(
                name,
                new LinearLayout.LayoutParams(0, dp(36), 1f)
        );

        TextView remove = new TextView(context);
        remove.setText("×");
        remove.setGravity(Gravity.CENTER);
        remove.setTextColor(Color.parseColor("#B3261E"));
        remove.setTextSize(17);
        LinearLayout.LayoutParams removeParams =
                new LinearLayout.LayoutParams(dp(27), dp(32));
        removeParams.leftMargin = dp(4);
        nameRow.addView(remove, removeParams);
        card.addView(nameRow);

        LinearLayout detailRow = new LinearLayout(context);
        detailRow.setOrientation(LinearLayout.HORIZONTAL);
        detailRow.setGravity(Gravity.CENTER_VERTICAL);

        EditText quantity = createEditField(
                "Qty",
                InputType.TYPE_CLASS_NUMBER
                        | InputType.TYPE_NUMBER_FLAG_DECIMAL
        );
        quantity.setTextSize(12);

        OverlayDropdown unit = new OverlayDropdown("Unit");
        unit.setOptions(Arrays.asList(UNITS));

        EditText price = createEditField(
                "Price",
                InputType.TYPE_CLASS_NUMBER
                        | InputType.TYPE_NUMBER_FLAG_DECIMAL
        );
        price.setTextSize(12);

        LinearLayout.LayoutParams qtyParams =
                new LinearLayout.LayoutParams(0, dp(34), 0.8f);
        qtyParams.topMargin = dp(4);
        detailRow.addView(quantity, qtyParams);

        LinearLayout.LayoutParams unitParams =
                new LinearLayout.LayoutParams(0, dp(34), 1.0f);
        unitParams.topMargin = dp(4);
        unitParams.leftMargin = dp(4);
        detailRow.addView(unit, unitParams);

        LinearLayout.LayoutParams priceParams =
                new LinearLayout.LayoutParams(0, dp(34), 1.0f);
        priceParams.topMargin = dp(4);
        priceParams.leftMargin = dp(4);
        detailRow.addView(price, priceParams);

        card.addView(detailRow);

        TextView total = new TextView(context);
        total.setText("₹0.00");
        total.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        total.setTextSize(9.5f);
        total.setTextColor(Color.parseColor("#81514B"));
        card.addView(
                total,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(16)
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
                addItemButton.setAlpha(itemRows.size() < MAX_ITEM_ROWS ? 1f : 0.5f);
            }
        });

        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        cardParams.topMargin = dp(3);
        itemsContainer.addView(card, cardParams);
        if (addItemButton != null) {
            addItemButton.setEnabled(itemRows.size() < MAX_ITEM_ROWS);
            addItemButton.setAlpha(itemRows.size() < MAX_ITEM_ROWS ? 1f : 0.5f);
        }
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
                if (dismissed
                        || categoryDropdown == null
                        || accountDropdown == null) {
                    return;
                }
                categoryDropdown.setOptions(categoryNames);
                accountDropdown.setOptions(accountNames);
            });
        }, "FloatingEntryOptions").start();
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

        String category = categoryDropdown == null
                ? ""
                : categoryDropdown.selected();
        String account = accountDropdown == null
                ? ""
                : accountDropdown.selected();
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
        saveButton.setAlpha(0.65f);
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
                        saveButton.setAlpha(1f);
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
        }, "FloatingEntrySave").start();
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

            String unit = row.unit.selected();
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
        field.setTextSize(12.5f);
        field.setTextColor(Color.parseColor("#26332D"));
        field.setGravity(Gravity.CENTER_VERTICAL);
        field.setPadding(dp(10), 0, dp(10), 0);
        field.setBackground(rounded("#F8FFFFFF", "#C8D5CF", 10));
        field.setOnClickListener(view -> showDatePicker());
        return field;
    }

    private void showDatePicker() {
        try {
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
        } catch (Exception exception) {
            Toast.makeText(
                    context,
                    "Unable to open date picker",
                    Toast.LENGTH_SHORT
            ).show();
        }
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
            int topMargin,
            int fieldHeight
    ) {
        TextView label = label(labelText);
        LinearLayout.LayoutParams labelParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(17)
                );
        labelParams.topMargin = topMargin;
        body.addView(label, labelParams);

        body.addView(
                field,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        fieldHeight
                )
        );
    }

    private TextView label(String text) {
        TextView label = new TextView(context);
        label.setText(text);
        label.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        label.setTextColor(Color.parseColor("#37463F"));
        label.setTextSize(10.5f);
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
        field.setTextSize(12.5f);
        field.setSingleLine(
                (inputType & InputType.TYPE_TEXT_FLAG_MULTI_LINE) == 0
        );
        field.setInputType(inputType);
        field.setPadding(dp(10), dp(3), dp(10), dp(3));
        field.setBackground(rounded("#F8FFFFFF", "#C8D5CF", 10));
        return field;
    }

    private TextView actionButton(String text, String color) {
        TextView button = new TextView(context);
        button.setText(text);
        button.setGravity(Gravity.CENTER);
        button.setTextSize(12.5f);
        button.setTypeface(button.getTypeface(), Typeface.BOLD);
        button.setTextColor(Color.WHITE);
        button.setBackground(rounded(color, color, 12));
        button.setClickable(true);
        button.setFocusable(true);
        return button;
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

    private int overlayType() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
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

    private final class OverlayDropdown extends TextView {
        private final List<String> options = new ArrayList<>();
        private String selectedValue = "";
        private final String hint;
        private PopupWindow popup;

        OverlayDropdown(String hint) {
            super(context);
            this.hint = hint;
            setGravity(Gravity.CENTER_VERTICAL);
            setSingleLine(true);
            setEllipsize(TextUtils.TruncateAt.END);
            setTextSize(12.5f);
            setTextColor(Color.parseColor("#26332D"));
            setPadding(dp(10), 0, dp(8), 0);
            setBackground(rounded("#F8FFFFFF", "#C8D5CF", 10));
            setClickable(true);
            setFocusable(true);
            render();
            setOnClickListener(view -> showOptions());
        }

        void setOptions(List<String> values) {
            String previous = selectedValue;
            options.clear();
            if (values != null) {
                for (String value : values) {
                    if (value != null && !value.trim().isEmpty()) {
                        options.add(value.trim());
                    }
                }
            }

            if (!previous.isEmpty() && options.contains(previous)) {
                selectedValue = previous;
            } else if (!options.isEmpty()) {
                selectedValue = options.get(0);
            } else {
                selectedValue = "";
            }
            render();
        }

        String selected() {
            return selectedValue.trim();
        }

        private void render() {
            String value = selectedValue.isEmpty() ? hint : selectedValue;
            setText(value + "   ▾");
            setTextColor(Color.parseColor(
                    selectedValue.isEmpty() ? "#7D8B84" : "#26332D"
            ));
        }

        private void showOptions() {
            if (options.isEmpty()) {
                Toast.makeText(
                        context,
                        "No options available",
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }
            if (popup != null && popup.isShowing()) {
                popup.dismiss();
                return;
            }

            LinearLayout list = new LinearLayout(context);
            list.setOrientation(LinearLayout.VERTICAL);
            list.setPadding(dp(5), dp(5), dp(5), dp(5));

            ScrollView optionScroll = new ScrollView(context);
            optionScroll.setFillViewport(false);
            optionScroll.setVerticalScrollBarEnabled(true);
            optionScroll.setScrollbarFadingEnabled(false);
            optionScroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
            optionScroll.setBackground(
                    rounded("#FBFFFC", "#B9D1C4", 13)
            );
            optionScroll.addView(
                    list,
                    new ScrollView.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    )
            );

            for (String option : options) {
                TextView row = new TextView(context);
                row.setText(option);
                row.setSingleLine(true);
                row.setEllipsize(TextUtils.TruncateAt.END);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setTextSize(12.5f);
                row.setTextColor(Color.parseColor("#26332D"));
                row.setPadding(dp(11), 0, dp(9), 0);
                if (option.equals(selectedValue)) {
                    row.setBackground(rounded("#E8F5ED", "#C5DDCF", 9));
                }
                LinearLayout.LayoutParams rowParams =
                        new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                dp(38)
                        );
                rowParams.bottomMargin = dp(2);
                list.addView(row, rowParams);
                row.setOnClickListener(view -> {
                    selectedValue = option;
                    render();
                    if (popup != null) {
                        popup.dismiss();
                    }
                });
            }

            int screenWidth = context.getResources()
                    .getDisplayMetrics().widthPixels;
            int popupWidth = Math.min(
                    screenWidth - dp(24),
                    Math.max(getWidth(), measurePopupWidth(options))
            );
            int popupHeight = Math.min(
                    dp(260),
                    Math.max(dp(42), options.size() * dp(40) + dp(10))
            );

            popup = new PopupWindow(
                    optionScroll,
                    popupWidth,
                    popupHeight,
                    true
            );
            popup.setOutsideTouchable(true);
            popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            popup.setElevation(dp(9));
            popup.setClippingEnabled(true);
            popup.setOnDismissListener(() -> popup = null);
            popup.showAsDropDown(this, 0, dp(3));
        }

        private int measurePopupWidth(List<String> values) {
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setTextSize(getTextSize());
            float widest = 0f;
            for (String value : values) {
                widest = Math.max(widest, paint.measureText(value));
            }
            return Math.round(widest) + dp(42);
        }
    }

    /**
     * Family-Hub style root-level resize. The visible corner marks are only
     * thin guides; the actual 28dp corner touch zones handle the gesture.
     */
    private final class ResizableRoot extends FrameLayout {
        private final Paint cornerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private boolean resizing;
        private int horizontalDirection;
        private int verticalDirection;
        private float startRawX;
        private float startRawY;
        private int startX;
        private int startY;
        private int startWidth;
        private int startHeight;

        ResizableRoot(Context context) {
            super(context);
            cornerPaint.setColor(Color.argb(145, 64, 118, 145));
            cornerPaint.setStrokeWidth(Math.max(1f, dp(1)));
            cornerPaint.setStyle(Paint.Style.STROKE);
            setWillNotDraw(false);
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            super.dispatchDraw(canvas);
            drawCornerGuides(canvas);
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent event) {
            if (event == null) {
                return super.dispatchTouchEvent(null);
            }

            if (event.getActionMasked() == MotionEvent.ACTION_OUTSIDE) {
                dismiss();
                return true;
            }

            if (handleResizeGesture(event)) {
                return true;
            }
            return super.dispatchTouchEvent(event);
        }

        private void drawCornerGuides(Canvas canvas) {
            int inset = dp(3);
            int length = dp(CORNER_GUIDE_DP);
            int right = getWidth() - inset;
            int bottom = getHeight() - inset;

            canvas.drawLine(inset, inset, inset + length, inset, cornerPaint);
            canvas.drawLine(inset, inset, inset, inset + length, cornerPaint);

            canvas.drawLine(right - length, inset, right, inset, cornerPaint);
            canvas.drawLine(right, inset, right, inset + length, cornerPaint);

            canvas.drawLine(inset, bottom, inset + length, bottom, cornerPaint);
            canvas.drawLine(inset, bottom - length, inset, bottom, cornerPaint);

            canvas.drawLine(right - length, bottom, right, bottom, cornerPaint);
            canvas.drawLine(right, bottom - length, right, bottom, cornerPaint);
        }

        private boolean handleResizeGesture(MotionEvent event) {
            if (params == null || windowManager == null || rootView == null) {
                return false;
            }

            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                int touch = dp(CORNER_TOUCH_DP);
                float localX = event.getX();
                float localY = event.getY();

                boolean left = localX <= touch;
                boolean right = localX >= getWidth() - touch;
                boolean top = localY <= touch;
                boolean bottom = localY >= getHeight() - touch;

                if (!(left || right) || !(top || bottom)) {
                    return false;
                }

                horizontalDirection = left ? -1 : 1;
                verticalDirection = top ? -1 : 1;
                resizing = true;
                startRawX = event.getRawX();
                startRawY = event.getRawY();
                startX = params.x;
                startY = params.y;
                startWidth = params.width;
                startHeight = params.height;
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            }

            if (!resizing) {
                return false;
            }

            if (action == MotionEvent.ACTION_MOVE) {
                resizeTo(event.getRawX(), event.getRawY());
                return true;
            }

            if (action == MotionEvent.ACTION_UP
                    || action == MotionEvent.ACTION_CANCEL) {
                resizeTo(event.getRawX(), event.getRawY());
                resizing = false;
                return true;
            }

            return true;
        }

        private void resizeTo(float rawX, float rawY) {
            int screenWidth = context.getResources()
                    .getDisplayMetrics().widthPixels;
            int screenHeight = context.getResources()
                    .getDisplayMetrics().heightPixels;
            int minWidth = Math.min(dp(290), screenWidth - dp(12));
            int minHeight = Math.min(dp(330), screenHeight - dp(70));

            int dx = Math.round(rawX - startRawX);
            int dy = Math.round(rawY - startRawY);

            int newX = startX;
            int newY = startY;
            int newWidth = startWidth;
            int newHeight = startHeight;

            if (horizontalDirection < 0) {
                int fixedRight = startX + startWidth;
                int desiredLeft = startX + dx;
                newX = clamp(
                        desiredLeft,
                        0,
                        Math.max(0, fixedRight - minWidth)
                );
                newWidth = fixedRight - newX;
            } else {
                int desiredRight = startX + startWidth + dx;
                int right = clamp(
                        desiredRight,
                        startX + minWidth,
                        screenWidth
                );
                newWidth = right - startX;
            }

            if (verticalDirection < 0) {
                int fixedBottom = startY + startHeight;
                int desiredTop = startY + dy;
                newY = clamp(
                        desiredTop,
                        dp(4),
                        Math.max(dp(4), fixedBottom - minHeight)
                );
                newHeight = fixedBottom - newY;
            } else {
                int desiredBottom = startY + startHeight + dy;
                int bottom = clamp(
                        desiredBottom,
                        startY + minHeight,
                        screenHeight - dp(6)
                );
                newHeight = bottom - startY;
            }

            params.x = newX;
            params.y = newY;
            params.width = newWidth;
            params.height = newHeight;
            windowManager.updateViewLayout(rootView, params);
        }
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
                        0,
                        Math.max(0, screenWidth - params.width)
                );
                params.y = clamp(
                        params.y,
                        dp(4),
                        Math.max(dp(4), screenHeight - params.height - dp(6))
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
        final OverlayDropdown unit;
        final EditText price;
        final TextView total;

        ItemRow(
                View root,
                EditText name,
                EditText quantity,
                OverlayDropdown unit,
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
