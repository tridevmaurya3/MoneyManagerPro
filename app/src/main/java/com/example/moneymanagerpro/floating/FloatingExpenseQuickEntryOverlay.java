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
import android.net.Uri;
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
import android.widget.ImageView;
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
import com.example.moneymanagerpro.utils.ReceiptStore;
import com.example.moneymanagerpro.utils.UpiPaymentResultParser;
import com.example.moneymanagerpro.utils.UpiQrPayloadParser;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Add-Expense counterpart of the compact Add-Income WindowManager overlay.
 * It never opens the Money Manager dashboard. UPI chooser/QR/gallery screens
 * are delegated to a transparent helper activity and return to this overlay.
 */
final class FloatingExpenseQuickEntryOverlay {

    interface Listener {
        void onClosed(FloatingExpenseQuickEntryOverlay overlay);
    }

    interface ExternalHost {
        void launchQrScanner();
        void launchUpiChooser(String paymentUri);
        void launchReceiptPicker();
    }

    private interface SelectionListener {
        void onSelected(String value);
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
    private final Listener listener;
    private final ExternalHost externalHost;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final Calendar selectedCalendar = Calendar.getInstance();
    private final List<ItemRow> itemRows = new ArrayList<>();

    private ResizableRoot rootView;
    private WindowManager.LayoutParams params;
    private ScrollView bodyScroll;
    private LinearLayout itemsContainer;
    private EditText amountField;
    private EditText noteField;
    private EditText upiIdField;
    private EditText upiNameField;
    private OverlayDropdown categoryDropdown;
    private OverlayDropdown accountDropdown;
    private OverlayDropdown upiModeDropdown;
    private TextView dateField;
    private TextView itemsTotalView;
    private TextView addItemButton;
    private TextView payUpiButton;
    private TextView billButton;
    private TextView saveButton;
    private LinearLayout upiResultPanel;
    private TextView upiStatusView;
    private FrameLayout receiptPreview;
    private ImageView receiptImage;

    private String selectedDate;
    private String selectedReceiptUri = "";
    private String currentUpiRequestReference = "";
    private String lastUpiNoteBlock = "";
    private UpiPaymentResultParser.Result lastUpiPaymentResult;
    private boolean dismissed;

    FloatingExpenseQuickEntryOverlay(
            Context context,
            Listener listener,
            ExternalHost externalHost
    ) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        this.externalHost = externalHost;
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

        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        int screenHeight = context.getResources().getDisplayMetrics().heightPixels;
        int width = Math.min(
                screenWidth - dp(18),
                Math.max(dp(318), Math.round(screenWidth * 0.88f))
        );
        int height = Math.min(
                screenHeight - dp(76),
                Math.max(dp(470), Math.round(screenHeight * 0.76f))
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
        params.y = Math.max(dp(18), (screenHeight - height) / 4);
        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;

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

    void handleExternalQrResult(String rawValue) {
        if (dismissed || rootView == null) {
            return;
        }

        String raw = rawValue == null ? "" : rawValue.trim();
        if (raw.isEmpty()) {
            upiModeDropdown.setSelectedValue("Enter UPI ID", false);
            return;
        }

        UpiQrPayloadParser.Result result = UpiQrPayloadParser.parse(raw);
        if (!result.isValid()) {
            upiModeDropdown.setSelectedValue("Enter UPI ID", false);
            Toast.makeText(
                    context,
                    "This QR code does not contain a valid UPI payment ID",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        upiIdField.setText(result.getPayeeId());
        upiNameField.setText(result.getPayeeName());
        if (clean(amountField).isEmpty()
                && parsePositive(result.getAmount()) != null) {
            amountField.setText(result.getAmount());
        }
        upiModeDropdown.setSelectedValue("UPI QR Scanned", false);
        Toast.makeText(
                context,
                "UPI ID and receiver name filled from QR code",
                Toast.LENGTH_SHORT
        ).show();
    }

    void handleExternalUpiResult(String response) {
        if (dismissed || rootView == null) {
            return;
        }

        lastUpiPaymentResult = UpiPaymentResultParser.parse(
                response == null ? "" : response
        );
        showUpiPaymentResult(lastUpiPaymentResult);
        if (lastUpiPaymentResult.getStatus()
                == UpiPaymentResultParser.Status.SUCCESS) {
            appendUpiResultToNote(lastUpiPaymentResult);
        } else {
            removeLastUpiNoteBlock();
        }
    }

    void handleExternalReceiptResult(String uriText) {
        if (dismissed || rootView == null) {
            return;
        }

        String value = uriText == null ? "" : uriText.trim();
        if (value.isEmpty()) {
            return;
        }

        selectedReceiptUri = value;
        try {
            receiptImage.setImageURI(Uri.parse(value));
        } catch (Exception ignored) {
            receiptImage.setImageDrawable(null);
        }
        receiptPreview.setVisibility(View.VISIBLE);
        billButton.setText("Change Bill");
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
        root.addView(
                panel,
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                )
        );

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
        body.setPadding(dp(1), dp(2), dp(1), dp(5));
        bodyScroll.addView(
                body,
                new ScrollView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );
        panel.addView(
                bodyScroll,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0,
                        1f
                )
        );

        amountField = createEditField(
                "Expense amount",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
        );
        amountField.setTextSize(15.5f);
        amountField.setTypeface(amountField.getTypeface(), Typeface.BOLD);
        addLabeledField(body, "Amount", amountField, dp(1), dp(38));

        buildItemsSection(body);

        categoryDropdown = new OverlayDropdown("Select category", null);
        accountDropdown = new OverlayDropdown("Select account", null);
        body.addView(buildCategoryAccountRow());

        buildUpiSection(body);
        buildDateBillRow(body);
        buildReceiptPreview(body);

        noteField = createEditField(
                "Note (optional)",
                InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                        | InputType.TYPE_TEXT_FLAG_MULTI_LINE
        );
        noteField.setMinLines(1);
        noteField.setMaxLines(2);
        noteField.setGravity(Gravity.TOP | Gravity.START);
        noteField.setFilters(new InputFilter[]{new InputFilter.LengthFilter(250)});
        addLabeledField(body, "Note", noteField, dp(4), dp(44));

        saveButton = actionButton("Save Expense", "#C42B1C");
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(40)
        );
        saveParams.topMargin = dp(6);
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
        title.setText("−  Add Expense");
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setTextColor(Color.parseColor("#A92518"));
        title.setTextSize(16);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        title.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(34), 1f));

        TextView close = new TextView(context);
        close.setText("×");
        close.setContentDescription("Close floating expense form");
        close.setGravity(Gravity.CENTER);
        close.setTextSize(20);
        close.setTextColor(Color.parseColor("#52655B"));
        close.setBackground(rounded("#E4F4EB", "#B9D2C5", 12));
        header.addView(close, new LinearLayout.LayoutParams(dp(32), dp(32)));
        close.setOnClickListener(view -> dismiss());
        header.setOnTouchListener(new MoveTouchListener());
        return header;
    }

    private View buildCategoryAccountRow() {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.TOP);

        LinearLayout categoryColumn = compactColumn(
                "Category", categoryDropdown, dp(36)
        );
        LinearLayout accountColumn = compactColumn(
                "Account", accountDropdown, dp(36)
        );

        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        );
        left.topMargin = dp(4);
        row.addView(categoryColumn, left);

        LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f
        );
        right.topMargin = dp(4);
        right.leftMargin = dp(5);
        row.addView(accountColumn, right);
        return row;
    }

    private void buildUpiSection(LinearLayout body) {
        TextView section = label("UPI / Payment (optional)");
        LinearLayout.LayoutParams sectionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(18)
        );
        sectionParams.topMargin = dp(4);
        body.addView(section, sectionParams);

        upiModeDropdown = new OverlayDropdown(
                "UPI method",
                value -> {
                    if ("Scan UPI QR Code".equals(value)
                            && externalHost != null) {
                        upiModeDropdown.setSelectedValue("Scanning QR…", false);
                        externalHost.launchQrScanner();
                    }
                }
        );
        upiModeDropdown.setOptions(Arrays.asList(
                "Enter UPI ID",
                "Scan UPI QR Code"
        ));

        upiIdField = createEditField(
                "Receiver UPI ID",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        );
        upiIdField.setSingleLine(true);
        upiNameField = createEditField(
                "Receiver name",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS
        );
        upiNameField.setSingleLine(true);

        LinearLayout first = new LinearLayout(context);
        first.setOrientation(LinearLayout.HORIZONTAL);
        first.setGravity(Gravity.CENTER_VERTICAL);
        first.addView(
                upiModeDropdown,
                new LinearLayout.LayoutParams(0, dp(36), 0.9f)
        );
        LinearLayout.LayoutParams idParams = new LinearLayout.LayoutParams(
                0, dp(36), 1.2f
        );
        idParams.leftMargin = dp(5);
        first.addView(upiIdField, idParams);
        body.addView(first);

        LinearLayout second = new LinearLayout(context);
        second.setOrientation(LinearLayout.HORIZONTAL);
        second.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                0, dp(36), 1.18f
        );
        nameParams.topMargin = dp(4);
        second.addView(upiNameField, nameParams);

        payUpiButton = outlineButton("Choose UPI App", "#A92518");
        LinearLayout.LayoutParams payParams = new LinearLayout.LayoutParams(
                0, dp(36), 0.82f
        );
        payParams.topMargin = dp(4);
        payParams.leftMargin = dp(5);
        second.addView(payUpiButton, payParams);
        body.addView(second);
        payUpiButton.setOnClickListener(view -> launchUpiPayment());

        upiResultPanel = new LinearLayout(context);
        upiResultPanel.setOrientation(LinearLayout.HORIZONTAL);
        upiResultPanel.setGravity(Gravity.CENTER_VERTICAL);
        upiResultPanel.setPadding(dp(7), dp(4), dp(5), dp(4));
        upiResultPanel.setBackground(rounded("#F4F8FF", "#C9D8EF", 9));
        upiResultPanel.setVisibility(View.GONE);

        upiStatusView = new TextView(context);
        upiStatusView.setTextSize(9.5f);
        upiStatusView.setTextColor(Color.parseColor("#3A4B57"));
        upiStatusView.setMaxLines(2);
        upiStatusView.setEllipsize(TextUtils.TruncateAt.END);
        upiResultPanel.addView(
                upiStatusView,
                new LinearLayout.LayoutParams(0, dp(34), 1f)
        );

        TextView clear = outlineButton("Clear", "#315F92");
        LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(
                dp(54), dp(30)
        );
        clearParams.leftMargin = dp(4);
        upiResultPanel.addView(clear, clearParams);
        clear.setOnClickListener(view -> clearUpiPaymentResult());

        LinearLayout.LayoutParams resultParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        resultParams.topMargin = dp(3);
        body.addView(upiResultPanel, resultParams);
    }

    private void buildDateBillRow(LinearLayout body) {
        dateField = createDateField();
        billButton = outlineButton("Bill Photo", "#A92518");
        billButton.setOnClickListener(view -> {
            if (externalHost != null) {
                externalHost.launchReceiptPicker();
            }
        });

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.TOP);

        LinearLayout dateColumn = compactColumn("Date", dateField, dp(36));
        LinearLayout billColumn = compactColumn("Bill", billButton, dp(36));

        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.05f
        );
        left.topMargin = dp(4);
        row.addView(dateColumn, left);

        LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.95f
        );
        right.topMargin = dp(4);
        right.leftMargin = dp(5);
        row.addView(billColumn, right);
        body.addView(row);
    }

    private void buildReceiptPreview(LinearLayout body) {
        receiptPreview = new FrameLayout(context);
        receiptPreview.setVisibility(View.GONE);
        receiptPreview.setBackground(rounded("#F8FFFFFF", "#D8C2BD", 10));
        receiptPreview.setPadding(dp(3), dp(3), dp(3), dp(3));

        receiptImage = new ImageView(context);
        receiptImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        receiptPreview.addView(
                receiptImage,
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                )
        );

        TextView remove = outlineButton("×", "#A92518");
        remove.setTextSize(16f);
        FrameLayout.LayoutParams removeParams = new FrameLayout.LayoutParams(
                dp(30), dp(30), Gravity.TOP | Gravity.END
        );
        removeParams.setMargins(0, dp(4), dp(4), 0);
        receiptPreview.addView(remove, removeParams);
        remove.setOnClickListener(view -> clearReceiptPreview());

        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(72)
        );
        previewParams.topMargin = dp(3);
        body.addView(receiptPreview, previewParams);
    }

    private LinearLayout compactColumn(
            String labelText,
            View field,
            int fieldHeight
    ) {
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.addView(
                label(labelText),
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(16)
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
        TextView heading = label("Optional Item Details");
        LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(20)
        );
        headingParams.topMargin = dp(3);
        body.addView(heading, headingParams);

        itemsContainer = new LinearLayout(context);
        itemsContainer.setOrientation(LinearLayout.VERTICAL);
        body.addView(
                itemsContainer,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        LinearLayout footer = new LinearLayout(context);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.CENTER_VERTICAL);

        itemsTotalView = new TextView(context);
        itemsTotalView.setText("Items total: ₹0.00");
        itemsTotalView.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        itemsTotalView.setTextColor(Color.parseColor("#9A3025"));
        itemsTotalView.setTextSize(9.5f);
        itemsTotalView.setTypeface(itemsTotalView.getTypeface(), Typeface.BOLD);
        footer.addView(
                itemsTotalView,
                new LinearLayout.LayoutParams(0, dp(30), 1f)
        );

        addItemButton = outlineButton("+ Item", "#A92518");
        footer.addView(
                addItemButton,
                new LinearLayout.LayoutParams(dp(62), dp(30))
        );
        body.addView(footer);

        addItemButton.setOnClickListener(view -> {
            addItemRow();
            if (bodyScroll != null) {
                bodyScroll.post(() -> bodyScroll.smoothScrollTo(
                        0,
                        Math.max(0, itemsContainer.getBottom() - dp(90))
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
        card.setPadding(dp(5), dp(4), dp(5), dp(3));
        card.setBackground(rounded("#F8FFFFFF", "#E5C7C2", 10));

        LinearLayout nameRow = new LinearLayout(context);
        nameRow.setOrientation(LinearLayout.HORIZONTAL);
        nameRow.setGravity(Gravity.CENTER_VERTICAL);

        EditText name = createEditField(
                "Item name",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS
        );
        name.setTextSize(11.5f);
        nameRow.addView(name, new LinearLayout.LayoutParams(0, dp(34), 1f));

        TextView remove = outlineButton("×", "#A92518");
        remove.setTextSize(15f);
        LinearLayout.LayoutParams removeParams = new LinearLayout.LayoutParams(
                dp(28), dp(30)
        );
        removeParams.leftMargin = dp(3);
        nameRow.addView(remove, removeParams);
        card.addView(nameRow);

        LinearLayout detailRow = new LinearLayout(context);
        detailRow.setOrientation(LinearLayout.HORIZONTAL);
        detailRow.setGravity(Gravity.CENTER_VERTICAL);

        EditText quantity = createEditField(
                "Qty",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
        );
        quantity.setTextSize(11f);
        OverlayDropdown unit = new OverlayDropdown("Unit", null);
        unit.setOptions(Arrays.asList(UNITS));
        EditText price = createEditField(
                "Price",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
        );
        price.setTextSize(11f);

        detailRow.addView(
                quantity,
                new LinearLayout.LayoutParams(0, dp(34), 0.8f)
        );
        LinearLayout.LayoutParams unitParams = new LinearLayout.LayoutParams(
                0, dp(34), 1f
        );
        unitParams.leftMargin = dp(4);
        detailRow.addView(unit, unitParams);
        LinearLayout.LayoutParams priceParams = new LinearLayout.LayoutParams(
                0, dp(34), 1f
        );
        priceParams.leftMargin = dp(4);
        detailRow.addView(price, priceParams);
        card.addView(detailRow);

        TextView total = new TextView(context);
        total.setText("₹0.00");
        total.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        total.setTextSize(9f);
        total.setTextColor(Color.parseColor("#81514B"));
        card.addView(
                total,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(14)
                )
        );

        ItemRow row = new ItemRow(card, name, quantity, unit, price, total);
        itemRows.add(row);

        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateItemTotal(row);
            }
        };
        quantity.addTextChangedListener(watcher);
        price.addTextChangedListener(watcher);

        remove.setOnClickListener(view -> {
            itemRows.remove(row);
            itemsContainer.removeView(card);
            updateItemsTotal();
            updateAddItemState();
        });

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        cardParams.topMargin = dp(3);
        itemsContainer.addView(card, cardParams);
        updateItemsTotal();
        updateAddItemState();
    }

    private void updateAddItemState() {
        if (addItemButton == null) {
            return;
        }
        boolean enabled = itemRows.size() < MAX_ITEM_ROWS;
        addItemButton.setEnabled(enabled);
        addItemButton.setAlpha(enabled ? 1f : 0.5f);
    }

    private void updateItemTotal(ItemRow row) {
        BigDecimal quantity = parsePositive(clean(row.quantity));
        BigDecimal price = parsePositive(clean(row.price));
        BigDecimal total = BigDecimal.ZERO;
        if (quantity != null && price != null) {
            total = quantity.multiply(price).setScale(2, RoundingMode.HALF_UP);
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
            BigDecimal quantity = parsePositive(clean(row.quantity));
            BigDecimal price = parsePositive(clean(row.price));
            if (quantity != null && price != null) {
                sum = sum.add(quantity.multiply(price));
            }
        }
        itemsTotalView.setText(
                "Items total: ₹" + money(sum.setScale(2, RoundingMode.HALF_UP))
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
            for (Category category : categories) {
                if (category.getType() != null
                        && category.getType().equalsIgnoreCase("expense")
                        && category.getName() != null
                        && !category.getName().trim().isEmpty()) {
                    categoryNames.add(category.getName().trim());
                }
            }
            if (categoryNames.isEmpty()) {
                categoryNames.addAll(Arrays.asList(
                        "Food", "Travel", "Shopping", "Bills", "Other Expense"
                ));
            }
            for (Account account : accounts) {
                if (account.getName() != null
                        && !account.getName().trim().isEmpty()) {
                    accountNames.add(account.getName().trim());
                }
            }
            if (accountNames.isEmpty()) {
                accountNames.add("Cash");
            }

            uiHandler.post(() -> {
                if (dismissed || categoryDropdown == null || accountDropdown == null) {
                    return;
                }
                categoryDropdown.setOptions(categoryNames);
                accountDropdown.setOptions(accountNames);
            });
        }, "FloatingExpenseOptions").start();
    }

    private void launchUpiPayment() {
        String payeeId = clean(upiIdField);
        String payeeName = clean(upiNameField);
        String amountText = clean(amountField);

        if (!payeeId.matches("^[A-Za-z0-9._-]{2,}@[A-Za-z0-9.-]{2,}$")) {
            upiIdField.setError("Enter a valid UPI ID");
            upiIdField.requestFocus();
            return;
        }
        if (payeeName.length() < 2) {
            upiNameField.setError("Enter receiver name");
            upiNameField.requestFocus();
            return;
        }

        BigDecimal amount = parsePositive(amountText);
        if (amount == null) {
            amountField.setError("Enter expense amount before payment");
            amountField.requestFocus();
            return;
        }

        currentUpiRequestReference = "MMP" + System.currentTimeMillis();
        String transactionNote = clean(noteField);
        if (transactionNote.isEmpty()) {
            transactionNote = "Money Manager Pro expense";
        }

        Uri paymentUri = new Uri.Builder()
                .scheme("upi")
                .authority("pay")
                .appendQueryParameter("pa", payeeId)
                .appendQueryParameter("pn", payeeName)
                .appendQueryParameter("tr", currentUpiRequestReference)
                .appendQueryParameter("tn", transactionNote)
                .appendQueryParameter("am", money(amount))
                .appendQueryParameter("cu", "INR")
                .build();

        if (externalHost != null) {
            externalHost.launchUpiChooser(paymentUri.toString());
        }
    }

    private void showUpiPaymentResult(UpiPaymentResultParser.Result result) {
        String reference = result.getTransactionReference().isEmpty()
                ? currentUpiRequestReference
                : result.getTransactionReference();
        String message;
        int color;

        switch (result.getStatus()) {
            case SUCCESS:
                message = "SUCCESS • Ref: " + valueOrNotProvided(reference);
                color = Color.parseColor("#107C10");
                break;
            case FAILED:
                message = "FAILED • Payment not confirmed";
                color = Color.parseColor("#B3261E");
                break;
            case PENDING:
                message = "PENDING • Wait for final confirmation";
                color = Color.parseColor("#9A6700");
                break;
            case UNKNOWN:
            default:
                message = "Status not verified • Check UPI app/bank";
                color = Color.parseColor("#9A6700");
                break;
        }
        upiStatusView.setText(message);
        upiStatusView.setTextColor(color);
        upiResultPanel.setVisibility(View.VISIBLE);
    }

    private void appendUpiResultToNote(UpiPaymentResultParser.Result result) {
        removeLastUpiNoteBlock();
        String reference = result.getTransactionReference().isEmpty()
                ? currentUpiRequestReference
                : result.getTransactionReference();
        String block = "UPI • App-reported success"
                + (reference.isEmpty() ? "" : " • Ref: " + reference);
        String current = clean(noteField);
        String separator = current.isEmpty() ? "" : "\n\n";
        int available = 250 - current.length() - separator.length();
        if (available <= 0) {
            return;
        }
        if (block.length() > available) {
            block = block.substring(0, available);
        }
        noteField.setText(current + separator + block);
        lastUpiNoteBlock = block;
    }

    private void removeLastUpiNoteBlock() {
        if (lastUpiNoteBlock.isEmpty()) {
            return;
        }
        String current = clean(noteField);
        String withSeparator = "\n\n" + lastUpiNoteBlock;
        if (current.endsWith(withSeparator)) {
            current = current.substring(0, current.length() - withSeparator.length());
        } else if (current.equals(lastUpiNoteBlock)) {
            current = "";
        }
        noteField.setText(current);
        lastUpiNoteBlock = "";
    }

    private void clearUpiPaymentResult() {
        removeLastUpiNoteBlock();
        lastUpiPaymentResult = null;
        currentUpiRequestReference = "";
        upiResultPanel.setVisibility(View.GONE);
    }

    private void clearReceiptPreview() {
        selectedReceiptUri = "";
        receiptImage.setImageDrawable(null);
        receiptPreview.setVisibility(View.GONE);
        billButton.setText("Bill Photo");
    }

    private void save() {
        if (lastUpiPaymentResult != null
                && lastUpiPaymentResult.getStatus()
                != UpiPaymentResultParser.Status.SUCCESS) {
            Toast.makeText(
                    context,
                    "UPI payment is not successful. Verify it or clear the payment result.",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        List<ExpenseItem> expenseItems = collectExpenseItems();
        if (expenseItems == null) {
            return;
        }

        String amountText = clean(amountField);
        if (amountText.isEmpty() && !expenseItems.isEmpty()) {
            BigDecimal total = BigDecimal.ZERO;
            for (ExpenseItem item : expenseItems) {
                total = total.add(BigDecimal.valueOf(item.getTotal()));
            }
            amountText = money(total);
            amountField.setText(amountText);
        }

        BigDecimal amount = parsePositive(amountText);
        if (amount == null) {
            amountField.setError("Enter a valid expense amount");
            amountField.requestFocus();
            return;
        }

        String category = categoryDropdown == null ? "" : categoryDropdown.selected();
        String account = accountDropdown == null ? "" : accountDropdown.selected();
        if (category.isEmpty() || account.isEmpty()) {
            Toast.makeText(context, "Select category and account", Toast.LENGTH_SHORT).show();
            return;
        }

        Transaction transaction = new Transaction();
        transaction.setType("EXPENSE");
        transaction.setAmount(amount.doubleValue());
        transaction.setCategory(category);
        transaction.setAccount(account);
        transaction.setNote(clean(noteField));
        transaction.setDate(selectedDate);

        saveButton.setEnabled(false);
        saveButton.setAlpha(0.65f);
        saveButton.setText("Saving Expense…");
        final List<ExpenseItem> itemsToSave = expenseItems;
        final String receiptUri = selectedReceiptUri;

        new Thread(() -> {
            try {
                AppDatabase database = DatabaseClient
                        .getInstance(context)
                        .getAppDatabase();
                final long[] savedTransactionId = {0L};

                database.runInTransaction(() -> {
                    long transactionId = database.transactionDao().insert(transaction);
                    if (transactionId <= 0 || transactionId > Integer.MAX_VALUE) {
                        throw new IllegalStateException("Invalid transaction ID");
                    }
                    for (ExpenseItem item : itemsToSave) {
                        item.setTransactionId((int) transactionId);
                    }
                    if (!itemsToSave.isEmpty()) {
                        database.expenseItemDao().insertAll(itemsToSave);
                    }
                    savedTransactionId[0] = transactionId;
                });

                if (!receiptUri.isEmpty()) {
                    ReceiptStore.saveReceiptUri(
                            context,
                            savedTransactionId[0],
                            receiptUri
                    );
                }

                uiHandler.post(() -> {
                    String message;
                    if (!itemsToSave.isEmpty() && !receiptUri.isEmpty()) {
                        message = "Expense, items and bill photo saved";
                    } else if (!itemsToSave.isEmpty()) {
                        message = "Expense and item details saved";
                    } else if (!receiptUri.isEmpty()) {
                        message = "Expense and bill photo saved";
                    } else {
                        message = "Expense saved successfully";
                    }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
                    dismiss();
                });
            } catch (Exception exception) {
                uiHandler.post(() -> {
                    if (saveButton != null) {
                        saveButton.setEnabled(true);
                        saveButton.setAlpha(1f);
                        saveButton.setText("Save Expense");
                    }
                    Toast.makeText(
                            context,
                            "Unable to save expense",
                            Toast.LENGTH_SHORT
                    ).show();
                });
            }
        }, "FloatingExpenseSave").start();
    }

    private List<ExpenseItem> collectExpenseItems() {
        List<ExpenseItem> result = new ArrayList<>();
        for (ItemRow row : itemRows) {
            String name = clean(row.name);
            String quantityText = clean(row.quantity);
            String priceText = clean(row.price);
            String unit = row.unit.selected();

            if (name.isEmpty() && quantityText.isEmpty() && priceText.isEmpty()) {
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
                new SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)
                        .format(selectedCalendar.getTime())
        );
        field.setTextSize(11.5f);
        field.setTextColor(Color.parseColor("#26332D"));
        field.setGravity(Gravity.CENTER_VERTICAL);
        field.setPadding(dp(9), 0, dp(8), 0);
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
                        dateField.setText(
                                new SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)
                                        .format(selectedCalendar.getTime())
                        );
                    },
                    selectedCalendar.get(Calendar.YEAR),
                    selectedCalendar.get(Calendar.MONTH),
                    selectedCalendar.get(Calendar.DAY_OF_MONTH)
            );
            Window window = dialog.getWindow();
            if (window != null) {
                window.setType(overlayType());
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
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(16)
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
        label.setIncludeFontPadding(false);
        return label;
    }

    private EditText createEditField(String hint, int inputType) {
        EditText field = new EditText(context);
        field.setHint(hint);
        field.setHintTextColor(Color.parseColor("#839088"));
        field.setTextColor(Color.parseColor("#26332D"));
        field.setTextSize(11.5f);
        field.setInputType(inputType);
        field.setSingleLine(
                (inputType & InputType.TYPE_TEXT_FLAG_MULTI_LINE) == 0
        );
        field.setMinimumHeight(0);
        field.setMinHeight(0);
        field.setIncludeFontPadding(false);
        field.setPadding(dp(9), dp(2), dp(9), dp(2));
        field.setBackground(rounded("#F8FFFFFF", "#C8D5CF", 10));
        return field;
    }

    private TextView actionButton(String text, String color) {
        TextView button = new TextView(context);
        button.setText(text);
        button.setGravity(Gravity.CENTER);
        button.setTextSize(11.5f);
        button.setTypeface(button.getTypeface(), Typeface.BOLD);
        button.setTextColor(Color.WHITE);
        button.setBackground(rounded(color, color, 11));
        button.setClickable(true);
        button.setFocusable(true);
        button.setIncludeFontPadding(false);
        return button;
    }

    private TextView outlineButton(String text, String color) {
        TextView button = new TextView(context);
        button.setText(text);
        button.setGravity(Gravity.CENTER);
        button.setTextSize(10.5f);
        button.setTypeface(button.getTypeface(), Typeface.BOLD);
        button.setTextColor(Color.parseColor(color));
        button.setBackground(rounded("#F9FFFFFF", "#D8BEB9", 10));
        button.setClickable(true);
        button.setFocusable(true);
        button.setIncludeFontPadding(false);
        return button;
    }

    private GradientDrawable rounded(String fill, String stroke, int radiusDp) {
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
            return value.compareTo(BigDecimal.ZERO) > 0 ? value : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String money(BigDecimal amount) {
        return String.format(Locale.US, "%.2f", amount.doubleValue());
    }

    private String valueOrNotProvided(String value) {
        return value == null || value.trim().isEmpty()
                ? "Not provided"
                : value.trim();
    }

    private int dp(int value) {
        return Math.round(
                value * context.getResources().getDisplayMetrics().density
        );
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private final class OverlayDropdown extends TextView {
        private final List<String> options = new ArrayList<>();
        private final String hint;
        private final SelectionListener selectionListener;
        private String selectedValue = "";
        private PopupWindow popup;

        OverlayDropdown(String hint, SelectionListener selectionListener) {
            super(context);
            this.hint = hint;
            this.selectionListener = selectionListener;
            setGravity(Gravity.CENTER_VERTICAL);
            setSingleLine(true);
            setEllipsize(TextUtils.TruncateAt.END);
            setTextSize(11.5f);
            setTextColor(Color.parseColor("#26332D"));
            setPadding(dp(9), 0, dp(7), 0);
            setMinimumHeight(0);
            setIncludeFontPadding(false);
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

        void setSelectedValue(String value, boolean notify) {
            selectedValue = value == null ? "" : value.trim();
            render();
            if (notify && selectionListener != null) {
                selectionListener.onSelected(selectedValue);
            }
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
                Toast.makeText(context, "No options available", Toast.LENGTH_SHORT).show();
                return;
            }
            if (popup != null && popup.isShowing()) {
                popup.dismiss();
                return;
            }

            LinearLayout list = new LinearLayout(context);
            list.setOrientation(LinearLayout.VERTICAL);
            list.setPadding(dp(4), dp(4), dp(4), dp(4));

            ScrollView scroll = new ScrollView(context);
            scroll.setVerticalScrollBarEnabled(true);
            scroll.setScrollbarFadingEnabled(false);
            scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
            scroll.setBackground(rounded("#FBFFFC", "#B9D1C4", 12));
            scroll.addView(
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
                row.setTextSize(11.5f);
                row.setTextColor(Color.parseColor("#26332D"));
                row.setPadding(dp(10), 0, dp(8), 0);
                if (option.equals(selectedValue)) {
                    row.setBackground(rounded("#E8F5ED", "#C5DDCF", 9));
                }
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(36)
                );
                rowParams.bottomMargin = dp(2);
                list.addView(row, rowParams);
                row.setOnClickListener(view -> {
                    setSelectedValue(option, true);
                    if (popup != null) {
                        popup.dismiss();
                    }
                });
            }

            int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
            int popupWidth = Math.min(
                    screenWidth - dp(24),
                    Math.max(getWidth(), measurePopupWidth(options))
            );
            int popupHeight = Math.min(
                    dp(250),
                    Math.max(dp(40), options.size() * dp(38) + dp(8))
            );
            popup = new PopupWindow(scroll, popupWidth, popupHeight, true);
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
            return Math.round(widest) + dp(40);
        }
    }

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
                return true;
            }
            if (!resizing) {
                return false;
            }
            if (action == MotionEvent.ACTION_MOVE) {
                resizeTo(event.getRawX(), event.getRawY());
                return true;
            }
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                resizeTo(event.getRawX(), event.getRawY());
                resizing = false;
                return true;
            }
            return true;
        }

        private void resizeTo(float rawX, float rawY) {
            int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
            int screenHeight = context.getResources().getDisplayMetrics().heightPixels;
            int minWidth = Math.min(dp(290), screenWidth - dp(12));
            int minHeight = Math.min(dp(360), screenHeight - dp(70));
            int dx = Math.round(rawX - startRawX);
            int dy = Math.round(rawY - startRawY);

            int newX = startX;
            int newY = startY;
            int newWidth = startWidth;
            int newHeight = startHeight;

            if (horizontalDirection < 0) {
                int fixedRight = startX + startWidth;
                newX = clamp(startX + dx, 0, Math.max(0, fixedRight - minWidth));
                newWidth = fixedRight - newX;
            } else {
                int right = clamp(
                        startX + startWidth + dx,
                        startX + minWidth,
                        screenWidth
                );
                newWidth = right - startX;
            }

            if (verticalDirection < 0) {
                int fixedBottom = startY + startHeight;
                newY = clamp(
                        startY + dy,
                        dp(4),
                        Math.max(dp(4), fixedBottom - minHeight)
                );
                newHeight = fixedBottom - newY;
            } else {
                int bottom = clamp(
                        startY + startHeight + dy,
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
                int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
                int screenHeight = context.getResources().getDisplayMetrics().heightPixels;
                params.x = clamp(params.x, 0, Math.max(0, screenWidth - params.width));
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
