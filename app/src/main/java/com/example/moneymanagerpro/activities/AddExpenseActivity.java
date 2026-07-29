package com.example.moneymanagerpro.activities;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.database.AppDatabase;
import com.example.moneymanagerpro.database.DatabaseClient;
import com.example.moneymanagerpro.model.Account;
import com.example.moneymanagerpro.model.Category;
import com.example.moneymanagerpro.model.ExpenseItem;
import com.example.moneymanagerpro.model.Transaction;
import com.example.moneymanagerpro.utils.ReceiptStore;
import com.example.moneymanagerpro.utils.TransactionScreenshotParser;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AddExpenseActivity extends AppCompatActivity {

    private static final int MAX_ITEM_ROWS = 50;
    private static final String STATE_ITEM_ROWS =
            "expense_item_rows";
    private static final String STATE_SELECTED_RECEIPT =
            "selected_receipt_uri";
    private static final String STATE_AUTO_SCREENSHOT =
            "auto_screenshot_uri";
    private static final String STATE_SCREENSHOT_RESULT =
            "screenshot_result";
    private static final String STATE_SCREENSHOT_STATUS =
            "screenshot_status";
    private static final String STATE_SCREENSHOT_CAN_FILL =
            "screenshot_can_fill";
    private static final String STATE_SCREENSHOT_NOTE =
            "screenshot_note";
    private static final String STATE_SELECTED_DATE =
            "selected_expense_date";

    private TextInputLayout inputAmount;
    private TextInputEditText etAmount;
    private TextInputEditText etDate;
    private TextInputEditText etNote;

    private MaterialAutoCompleteTextView dropdownCategory;
    private MaterialAutoCompleteTextView dropdownAccount;

    private MaterialButton btnAttachReceipt;
    private MaterialButton btnRemoveReceipt;
    private MaterialButton btnSaveExpense;
    private MaterialButton btnMoreItem;
    private MaterialButton btnReadTransactionScreenshot;
    private MaterialButton btnClearScreenshotResult;

    private ImageView imgReceiptPreview;
    private FrameLayout receiptPreviewContainer;
    private LinearLayout itemDetailsContainer;
    private TextView txtItemsTotal;
    private View screenshotReaderProgress;
    private View screenshotResultContainer;
    private TextView txtScreenshotStatus;
    private TextView txtDetectedAmount;
    private TextView txtDetectedMerchant;
    private TextView txtDetectedBank;
    private TextView txtDetectedReference;
    private TextView txtDetectedDate;
    private TextView txtDetectedPaymentApp;

    private Calendar selectedCalendar;
    private String selectedDate;
    private Uri selectedReceiptUri;
    private Uri autoAttachedScreenshotUri;
    private TextRecognizer screenshotTextRecognizer;
    private TransactionScreenshotParser.Result
            lastScreenshotResult;
    private String lastScreenshotNoteBlock = "";
    private String lastScreenshotStatusBase = "";
    private boolean lastScreenshotCanAutoFill;
    private boolean accountOptionsReady;

    private final List<View> itemRows = new ArrayList<>();
    private final List<String> availableAccountNames =
            new ArrayList<>();

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

                        autoAttachedScreenshotUri = null;
                        showReceiptPreview(uri);
                    }
            );

    private final ActivityResultLauncher<String[]>
            transactionScreenshotPicker =
            registerForActivityResult(
                    new ActivityResultContracts.OpenDocument(),
                    uri -> {
                        if (uri == null) {
                            return;
                        }

                        try {
                            getContentResolver()
                                    .takePersistableUriPermission(
                                            uri,
                                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                                    );
                        } catch (Exception ignored) {
                        }

                        readTransactionScreenshot(uri);
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
        btnMoreItem = findViewById(R.id.btnMoreItem);
        btnReadTransactionScreenshot =
                findViewById(
                        R.id.btnReadTransactionScreenshot
                );
        btnClearScreenshotResult =
                findViewById(
                        R.id.btnClearScreenshotResult
                );

        imgReceiptPreview = findViewById(R.id.imgReceiptPreview);
        receiptPreviewContainer = findViewById(R.id.receiptPreviewContainer);
        itemDetailsContainer = findViewById(R.id.itemDetailsContainer);
        txtItemsTotal = findViewById(R.id.txtItemsTotal);
        screenshotReaderProgress =
                findViewById(
                        R.id.screenshotReaderProgress
                );
        screenshotResultContainer =
                findViewById(
                        R.id.screenshotResultContainer
                );
        txtScreenshotStatus =
                findViewById(R.id.txtScreenshotStatus);
        txtDetectedAmount =
                findViewById(R.id.txtDetectedAmount);
        txtDetectedMerchant =
                findViewById(R.id.txtDetectedMerchant);
        txtDetectedBank =
                findViewById(R.id.txtDetectedBank);
        txtDetectedReference =
                findViewById(R.id.txtDetectedReference);
        txtDetectedDate =
                findViewById(R.id.txtDetectedDate);
        txtDetectedPaymentApp =
                findViewById(R.id.txtDetectedPaymentApp);

        TextView btnBack = findViewById(R.id.btnBack);

        selectedCalendar = Calendar.getInstance();

        if (savedInstanceState != null
                && savedInstanceState.containsKey(
                STATE_SELECTED_DATE
        )) {
            selectedCalendar.setTimeInMillis(
                    savedInstanceState.getLong(
                            STATE_SELECTED_DATE
                    )
            );
        }

        updateDateField();
        screenshotTextRecognizer =
                TextRecognition.getClient(
                        TextRecognizerOptions
                                .DEFAULT_OPTIONS
                );

        btnBack.setOnClickListener(v -> finish());

        etDate.setOnClickListener(v -> showDatePicker());

        btnAttachReceipt.setOnClickListener(v ->
                receiptPicker.launch(new String[]{"image/*"})
        );

        btnRemoveReceipt.setOnClickListener(v -> clearReceiptPreview());

        btnMoreItem.setOnClickListener(v -> addItemRow());

        btnReadTransactionScreenshot.setOnClickListener(
                view -> transactionScreenshotPicker.launch(
                        new String[]{"image/*"}
                )
        );

        btnClearScreenshotResult.setOnClickListener(
                view -> clearScreenshotResult()
        );

        btnSaveExpense.setOnClickListener(v -> saveExpense());

        if (!restoreItemRows(savedInstanceState)) {
            addItemRow();
        }
        restoreScreenshotState(savedInstanceState);
        loadFormOptions();
    }

    @Override
    protected void onDestroy() {
        if (screenshotTextRecognizer != null) {
            screenshotTextRecognizer.close();
        }

        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        ArrayList<String> savedRows = new ArrayList<>();

        for (View row : itemRows) {
            savedRows.add(
                    textOf(row.findViewById(R.id.etItemName))
            );
            savedRows.add(
                    textOf(row.findViewById(R.id.etItemQuantity))
            );
            savedRows.add(
                    textOf(row.findViewById(R.id.etItemUnit))
            );
            savedRows.add(
                    textOf(row.findViewById(R.id.etItemPrice))
            );
        }

        outState.putStringArrayList(
                STATE_ITEM_ROWS,
                savedRows
        );
        outState.putLong(
                STATE_SELECTED_DATE,
                selectedCalendar.getTimeInMillis()
        );

        if (selectedReceiptUri != null) {
            outState.putParcelable(
                    STATE_SELECTED_RECEIPT,
                    selectedReceiptUri
            );
        }

        if (autoAttachedScreenshotUri != null) {
            outState.putParcelable(
                    STATE_AUTO_SCREENSHOT,
                    autoAttachedScreenshotUri
            );
        }

        if (lastScreenshotResult != null) {
            outState.putSerializable(
                    STATE_SCREENSHOT_RESULT,
                    lastScreenshotResult
            );
        }

        outState.putString(
                STATE_SCREENSHOT_STATUS,
                lastScreenshotStatusBase
        );
        outState.putBoolean(
                STATE_SCREENSHOT_CAN_FILL,
                lastScreenshotCanAutoFill
        );
        outState.putString(
                STATE_SCREENSHOT_NOTE,
                lastScreenshotNoteBlock
        );

        super.onSaveInstanceState(outState);
    }

    @SuppressWarnings("deprecation")
    private void restoreScreenshotState(
            Bundle savedInstanceState
    ) {
        if (savedInstanceState == null) {
            return;
        }

        selectedReceiptUri =
                savedInstanceState.getParcelable(
                        STATE_SELECTED_RECEIPT
                );
        autoAttachedScreenshotUri =
                savedInstanceState.getParcelable(
                        STATE_AUTO_SCREENSHOT
                );

        if (selectedReceiptUri != null) {
            showReceiptPreview(selectedReceiptUri);
        }

        lastScreenshotResult =
                (TransactionScreenshotParser.Result)
                        savedInstanceState.getSerializable(
                                STATE_SCREENSHOT_RESULT
                        );
        lastScreenshotStatusBase =
                savedInstanceState.getString(
                        STATE_SCREENSHOT_STATUS,
                        ""
                );
        lastScreenshotCanAutoFill =
                savedInstanceState.getBoolean(
                        STATE_SCREENSHOT_CAN_FILL,
                        false
                );
        lastScreenshotNoteBlock =
                savedInstanceState.getString(
                        STATE_SCREENSHOT_NOTE,
                        ""
                );

        if (lastScreenshotResult != null) {
            showScreenshotResult(
                    lastScreenshotResult
            );
            screenshotResultContainer.setVisibility(
                    View.VISIBLE
            );

            if (lastScreenshotResult.getStatus()
                    == TransactionScreenshotParser
                    .Status.SUCCESS) {
                txtScreenshotStatus.setTextColor(
                        getColor(R.color.success)
                );
            } else if (lastScreenshotResult.getStatus()
                    == TransactionScreenshotParser
                    .Status.FAILED) {
                txtScreenshotStatus.setTextColor(
                        getColor(R.color.error)
                );
            } else {
                txtScreenshotStatus.setTextColor(
                        getColor(R.color.warning)
                );
            }

            updateScreenshotStatusForAccount();
        }
    }

    private boolean restoreItemRows(Bundle savedInstanceState) {
        if (savedInstanceState == null) {
            return false;
        }

        ArrayList<String> savedRows =
                savedInstanceState.getStringArrayList(
                        STATE_ITEM_ROWS
                );

        if (savedRows == null
                || savedRows.isEmpty()
                || savedRows.size() % 4 != 0) {
            return false;
        }

        int rowCount = Math.min(
                savedRows.size() / 4,
                MAX_ITEM_ROWS
        );

        for (int index = 0; index < rowCount; index++) {
            addItemRow();

            View row = itemRows.get(
                    itemRows.size() - 1
            );

            setText(
                    row.findViewById(R.id.etItemName),
                    savedRows.get(index * 4)
            );
            setText(
                    row.findViewById(R.id.etItemQuantity),
                    savedRows.get(index * 4 + 1)
            );
            setText(
                    row.findViewById(R.id.etItemUnit),
                    savedRows.get(index * 4 + 2)
            );
            setText(
                    row.findViewById(R.id.etItemPrice),
                    savedRows.get(index * 4 + 3)
            );
        }

        updateItemsSummary();
        return true;
    }

    private void setText(
            TextInputEditText editText,
            String value
    ) {
        editText.setText(value == null ? "" : value);
    }

    private void addItemRow() {
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
                itemDetailsContainer,
                false
        );

        TextInputEditText quantity =
                row.findViewById(R.id.etItemQuantity);
        TextInputEditText price =
                row.findViewById(R.id.etItemPrice);
        TextInputEditText name =
                row.findViewById(R.id.etItemName);
        TextInputEditText unit =
                row.findViewById(R.id.etItemUnit);
        TextInputEditText total =
                row.findViewById(R.id.etItemTotal);
        MaterialButton remove =
                row.findViewById(R.id.btnRemoveItem);

        name.setSaveEnabled(false);
        quantity.setSaveEnabled(false);
        unit.setSaveEnabled(false);
        price.setSaveEnabled(false);
        total.setSaveEnabled(false);

        TextWatcher totalWatcher = new TextWatcher() {
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
                updateItemTotal(row);
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        };

        quantity.addTextChangedListener(totalWatcher);
        price.addTextChangedListener(totalWatcher);

        remove.setOnClickListener(view -> {
            itemRows.remove(row);
            itemDetailsContainer.removeView(row);
            renumberItemRows();
            updateItemsSummary();
        });

        itemRows.add(row);
        itemDetailsContainer.addView(row);
        renumberItemRows();
        updateItemsSummary();
    }

    private void renumberItemRows() {
        for (int index = 0; index < itemRows.size(); index++) {
            TextView number =
                    itemRows.get(index)
                            .findViewById(R.id.txtItemNumber);

            number.setText(
                    getString(
                            R.string.expense_item_number,
                            index + 1
                    )
            );
        }

        btnMoreItem.setEnabled(itemRows.size() < MAX_ITEM_ROWS);
    }

    private void updateItemTotal(View row) {
        BigDecimal quantity = parsePositiveDecimal(
                textOf(row.findViewById(R.id.etItemQuantity))
        );
        BigDecimal price = parsePositiveDecimal(
                textOf(row.findViewById(R.id.etItemPrice))
        );

        BigDecimal total = BigDecimal.ZERO;

        if (quantity != null && price != null) {
            total = quantity
                    .multiply(price)
                    .setScale(2, RoundingMode.HALF_UP);
        }

        TextInputEditText totalField =
                row.findViewById(R.id.etItemTotal);

        totalField.setText(formatMoney(total));
        updateItemsSummary();
    }

    private void updateItemsSummary() {
        BigDecimal itemsTotal = BigDecimal.ZERO;

        for (View row : itemRows) {
            BigDecimal quantity = parsePositiveDecimal(
                    textOf(row.findViewById(R.id.etItemQuantity))
            );
            BigDecimal price = parsePositiveDecimal(
                    textOf(row.findViewById(R.id.etItemPrice))
            );

            if (quantity != null && price != null) {
                itemsTotal = itemsTotal.add(
                        quantity.multiply(price)
                );
            }
        }

        txtItemsTotal.setText(
                getString(
                        R.string.expense_items_total,
                        formatMoney(
                                itemsTotal.setScale(
                                        2,
                                        RoundingMode.HALF_UP
                                )
                        )
                )
        );
    }

    private List<ExpenseItem> collectExpenseItems() {
        List<ExpenseItem> items = new ArrayList<>();

        for (int index = 0; index < itemRows.size(); index++) {
            View row = itemRows.get(index);

            TextInputLayout nameInput =
                    row.findViewById(R.id.inputItemName);
            TextInputLayout quantityInput =
                    row.findViewById(R.id.inputItemQuantity);
            TextInputLayout unitInput =
                    row.findViewById(R.id.inputItemUnit);
            TextInputLayout priceInput =
                    row.findViewById(R.id.inputItemPrice);

            nameInput.setError(null);
            quantityInput.setError(null);
            unitInput.setError(null);
            priceInput.setError(null);

            String name = textOf(
                    row.findViewById(R.id.etItemName)
            );
            String quantityText = textOf(
                    row.findViewById(R.id.etItemQuantity)
            );
            String unit = textOf(
                    row.findViewById(R.id.etItemUnit)
            );
            String priceText = textOf(
                    row.findViewById(R.id.etItemPrice)
            );

            if (name.isEmpty()
                    && quantityText.isEmpty()
                    && unit.isEmpty()
                    && priceText.isEmpty()) {
                continue;
            }

            if (name.isEmpty()) {
                nameInput.setError("Enter item name");
                nameInput.requestFocus();
                return null;
            }

            BigDecimal quantity =
                    parsePositiveDecimal(quantityText);

            if (quantity == null) {
                quantityInput.setError(
                        "Enter a quantity greater than zero"
                );
                quantityInput.requestFocus();
                return null;
            }

            if (unit.isEmpty()) {
                unitInput.setError("Enter item unit");
                unitInput.requestFocus();
                return null;
            }

            BigDecimal price =
                    parsePositiveDecimal(priceText);

            if (price == null) {
                priceInput.setError(
                        "Enter a price greater than zero"
                );
                priceInput.requestFocus();
                return null;
            }

            BigDecimal total = quantity
                    .multiply(price)
                    .setScale(2, RoundingMode.HALF_UP);

            ExpenseItem item = new ExpenseItem();
            item.setItemName(name);
            item.setQuantity(quantity.doubleValue());
            item.setUnit(unit);
            item.setPrice(price.doubleValue());
            item.setTotal(total.doubleValue());
            item.setSortOrder(items.size());

            items.add(item);
        }

        return items;
    }

    private BigDecimal parsePositiveDecimal(String text) {
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

    private String textOf(TextInputEditText editText) {
        return editText.getText() == null
                ? ""
                : editText.getText().toString().trim();
    }

    private String formatMoney(BigDecimal amount) {
        return String.format(
                Locale.US,
                "%.2f",
                amount.doubleValue()
        );
    }

    private void readTransactionScreenshot(Uri uri) {
        setScreenshotReaderLoading(true);

        new Thread(() -> {
            try {
                InputImage image = InputImage.fromFilePath(
                        getApplicationContext(),
                        uri
                );

                runOnUiThread(() -> screenshotTextRecognizer
                        .process(image)
                        .addOnSuccessListener(text ->
                                handleRecognizedScreenshot(
                                        uri,
                                        text.getText()
                                )
                        )
                        .addOnFailureListener(exception ->
                                showScreenshotReaderError(
                                        "Screenshot text could not be read. "
                                                + "Try a clearer image."
                                )
                        )
                );
            } catch (IOException exception) {
                runOnUiThread(() ->
                        showScreenshotReaderError(
                                "Selected screenshot could not be opened."
                        )
                );
            }
        }).start();
    }

    private void handleRecognizedScreenshot(
            Uri screenshotUri,
            String recognizedText
    ) {
        setScreenshotReaderLoading(false);

        TransactionScreenshotParser.Result result =
                TransactionScreenshotParser.parse(
                        recognizedText
                );

        if (!result.hasUsefulData()) {
            showScreenshotReaderError(
                    "No transaction details were detected. "
                            + "Choose a clear payment screenshot."
            );
            return;
        }

        lastScreenshotResult = result;
        showScreenshotResult(result);

        boolean incoming =
                result.getDirection()
                        == TransactionScreenshotParser
                        .Direction.INCOMING;
        boolean failed =
                result.getStatus()
                        == TransactionScreenshotParser
                        .Status.FAILED;
        boolean pending =
                result.getStatus()
                        == TransactionScreenshotParser
                        .Status.PENDING;

        lastScreenshotCanAutoFill =
                !incoming && !failed && !pending;

        if (incoming) {
            lastScreenshotStatusBase =
                    "Incoming payment detected. "
                            + "Expense form was not auto-filled.";
            txtScreenshotStatus.setTextColor(
                    getColor(R.color.warning)
            );
        } else if (failed) {
            lastScreenshotStatusBase =
                    "Failed transaction detected. "
                            + "Expense form was not auto-filled.";
            txtScreenshotStatus.setTextColor(
                    getColor(R.color.error)
            );
        } else if (pending) {
            lastScreenshotStatusBase =
                    "Pending transaction detected. "
                            + "Wait for success before saving.";
            txtScreenshotStatus.setTextColor(
                    getColor(R.color.warning)
            );
        } else {
            applyScreenshotResult(
                    result,
                    screenshotUri
            );

            if (result.getStatus()
                    == TransactionScreenshotParser
                    .Status.SUCCESS) {
                lastScreenshotStatusBase =
                        "Successful transaction detected "
                                + "and available fields were filled.";
                txtScreenshotStatus.setTextColor(
                        getColor(R.color.success)
                );
            } else {
                lastScreenshotStatusBase =
                        "Transaction details detected. "
                                + "Status was unclear, so review carefully.";
                txtScreenshotStatus.setTextColor(
                        getColor(R.color.warning)
                );
            }
        }

        updateScreenshotStatusForAccount();
        screenshotResultContainer.setVisibility(
                View.VISIBLE
        );
    }

    private void showScreenshotResult(
            TransactionScreenshotParser.Result result
    ) {
        txtDetectedAmount.setText(
                "Amount: "
                        + (result.getAmount() == null
                        ? "Not detected"
                        : "₹"
                        + formatMoney(
                        BigDecimal.valueOf(
                                result.getAmount()
                        )
                ))
        );

        txtDetectedMerchant.setText(
                "Merchant: "
                        + valueOrNotDetected(
                        result.getMerchant()
                )
        );

        txtDetectedBank.setText(
                "Bank: "
                        + valueOrNotDetected(
                        result.getBank()
                )
        );

        txtDetectedReference.setText(
                "Reference: "
                        + valueOrNotDetected(
                        result.getReference()
                )
        );

        txtDetectedDate.setText(
                "Date: "
                        + valueOrNotDetected(
                        result.getDateText()
                )
        );

        txtDetectedPaymentApp.setText(
                "Payment app: "
                        + valueOrNotDetected(
                        result.getPaymentApp()
                )
        );
    }

    private void applyScreenshotResult(
            TransactionScreenshotParser.Result result,
            Uri screenshotUri
    ) {
        if (result.getAmount() != null) {
            etAmount.setText(
                    formatMoney(
                            BigDecimal.valueOf(
                                    result.getAmount()
                            )
                    )
            );
            inputAmount.setError(null);
        }

        Calendar detectedDate =
                parseDetectedDate(
                        result.getDateText()
                );

        if (detectedDate != null) {
            selectedCalendar = detectedDate;
            updateDateField();
        }

        appendScreenshotDetailsToNote(result);

        if (selectedReceiptUri == null) {
            showReceiptPreview(screenshotUri);
            autoAttachedScreenshotUri = screenshotUri;
        }
    }

    private Calendar parseDetectedDate(String dateText) {
        if (dateText == null
                || dateText.trim().isEmpty()) {
            return null;
        }

        String normalized = dateText
                .trim()
                .replace(" at ", ", ");

        String[] dateFormats = {
                "dd MMM yyyy, hh:mm a",
                "dd MMMM yyyy, hh:mm a",
                "dd MMM yyyy HH:mm",
                "dd MMMM yyyy HH:mm",
                "dd/MM/yyyy HH:mm",
                "dd-MM-yyyy HH:mm",
                "dd.MM.yyyy HH:mm",
                "dd/MM/yy HH:mm",
                "dd-MM-yy HH:mm",
                "dd.MM.yy HH:mm",
                "dd MMM yyyy",
                "dd MMMM yyyy",
                "dd/MM/yyyy",
                "dd-MM-yyyy",
                "dd.MM.yyyy",
                "dd/MM/yy",
                "dd-MM-yy",
                "dd.MM.yy"
        };

        for (String format : dateFormats) {
            try {
                SimpleDateFormat parser =
                        new SimpleDateFormat(
                                format,
                                Locale.ENGLISH
                        );

                parser.setLenient(false);

                Date parsedDate =
                        parser.parse(normalized);

                if (parsedDate != null) {
                    Calendar calendar =
                            Calendar.getInstance();

                    boolean containsTime =
                            format.contains("HH")
                                    || format.contains("hh");

                    int currentHour =
                            calendar.get(Calendar.HOUR_OF_DAY);
                    int currentMinute =
                            calendar.get(Calendar.MINUTE);

                    calendar.setTime(parsedDate);

                    if (!containsTime) {
                        calendar.set(
                                Calendar.HOUR_OF_DAY,
                                currentHour
                        );
                        calendar.set(
                                Calendar.MINUTE,
                                currentMinute
                        );
                    }

                    return calendar;
                }
            } catch (ParseException ignored) {
            }
        }

        return null;
    }

    private void appendScreenshotDetailsToNote(
            TransactionScreenshotParser.Result result
    ) {
        removeLastScreenshotNoteBlock();

        List<String> details = new ArrayList<>();

        if (!result.getMerchant().isEmpty()) {
            details.add(
                    "Merchant: " + result.getMerchant()
            );
        }

        if (!result.getBank().isEmpty()) {
            details.add("Bank: " + result.getBank());
        }

        if (!result.getReference().isEmpty()) {
            details.add(
                    "Ref: " + result.getReference()
            );
        }

        if (!result.getPaymentApp().isEmpty()) {
            details.add(
                    "App: " + result.getPaymentApp()
            );
        }

        if (details.isEmpty()) {
            return;
        }

        String block =
                "Screenshot • "
                        + joinDetails(details);
        String current = textOf(etNote);
        String separator =
                current.isEmpty() ? "" : "\n\n";
        int available =
                250 - current.length()
                        - separator.length();

        if (available <= 0) {
            return;
        }

        if (block.length() > available) {
            block = block.substring(0, available);
        }

        etNote.setText(
                current + separator + block
        );
        lastScreenshotNoteBlock = block;
    }

    private String joinDetails(List<String> details) {
        StringBuilder builder =
                new StringBuilder();

        for (String detail : details) {
            if (builder.length() > 0) {
                builder.append(" | ");
            }

            builder.append(detail);
        }

        return builder.toString();
    }

    private void removeLastScreenshotNoteBlock() {
        if (lastScreenshotNoteBlock.isEmpty()) {
            return;
        }

        String current = textOf(etNote);
        String withSeparator =
                "\n\n" + lastScreenshotNoteBlock;

        if (current.endsWith(withSeparator)) {
            current = current.substring(
                    0,
                    current.length()
                            - withSeparator.length()
            );
        } else if (current.equals(
                lastScreenshotNoteBlock
        )) {
            current = "";
        }

        etNote.setText(current);
        lastScreenshotNoteBlock = "";
    }

    private void updateScreenshotStatusForAccount() {
        if (lastScreenshotResult == null) {
            return;
        }

        String message = lastScreenshotStatusBase;

        if (lastScreenshotCanAutoFill
                && !lastScreenshotResult
                .getBank()
                .isEmpty()) {

            boolean matched =
                    matchDetectedBankToAccount(
                            lastScreenshotResult.getBank()
                    );

            if (matched) {
                message += " Matching account selected.";
            } else if (accountOptionsReady) {
                message += " Select the correct payment "
                        + "account manually.";
            } else {
                message += " Account list is still loading.";
            }
        }

        txtScreenshotStatus.setText(message);
    }

    private boolean matchDetectedBankToAccount(
            String detectedBank
    ) {
        if (detectedBank == null
                || detectedBank.trim().isEmpty()) {
            return false;
        }

        for (String accountName :
                availableAccountNames) {

            if (bankNamesMatch(
                    detectedBank,
                    accountName
            )) {
                dropdownAccount.setText(
                        accountName,
                        false
                );
                return true;
            }
        }

        return false;
    }

    private boolean bankNamesMatch(
            String bank,
            String account
    ) {
        String normalizedBank =
                normalizeBankName(bank);
        String normalizedAccount =
                normalizeBankName(account);

        if (normalizedBank.isEmpty()
                || normalizedAccount.isEmpty()) {
            return false;
        }

        if (normalizedBank.contains(
                normalizedAccount
        ) || normalizedAccount.contains(
                normalizedBank
        )) {
            return true;
        }

        String acronym =
                createBankAcronym(bank);

        return !acronym.isEmpty()
                && normalizedAccount.contains(acronym);
    }

    private String normalizeBankName(String value) {
        return value == null
                ? ""
                : value.toLowerCase(Locale.US)
                .replaceAll(
                        "[^a-z0-9]",
                        ""
                )
                .replace("bank", "");
    }

    private String createBankAcronym(String bank) {
        if (bank == null) {
            return "";
        }

        StringBuilder acronym =
                new StringBuilder();

        for (String word :
                bank.toLowerCase(Locale.US)
                        .split("\\s+")) {

            if (word.isEmpty()
                    || word.equals("of")
                    || word.equals("the")
                    || word.equals("bank")) {
                continue;
            }

            acronym.append(word.charAt(0));
        }

        return acronym.toString();
    }

    private void clearScreenshotResult() {
        removeLastScreenshotNoteBlock();

        lastScreenshotResult = null;
        lastScreenshotCanAutoFill = false;
        lastScreenshotStatusBase = "";

        screenshotResultContainer.setVisibility(
                View.GONE
        );

        if (autoAttachedScreenshotUri != null
                && autoAttachedScreenshotUri.equals(
                selectedReceiptUri
        )) {
            clearReceiptPreview();
        }

        Toast.makeText(
                this,
                "Reader result cleared. "
                        + "Review any filled form values.",
                Toast.LENGTH_SHORT
        ).show();
    }

    private void setScreenshotReaderLoading(
            boolean loading
    ) {
        btnReadTransactionScreenshot.setEnabled(
                !loading
        );
        btnReadTransactionScreenshot.setText(
                loading
                        ? "Reading Screenshot..."
                        : "Choose Transaction Screenshot"
        );
        screenshotReaderProgress.setVisibility(
                loading ? View.VISIBLE : View.GONE
        );
    }

    private void showScreenshotReaderError(
            String message
    ) {
        setScreenshotReaderLoading(false);

        Toast.makeText(
                this,
                message,
                Toast.LENGTH_LONG
        ).show();
    }

    private String valueOrNotDetected(
            String value
    ) {
        return value == null
                || value.trim().isEmpty()
                ? "Not detected"
                : value.trim();
    }

    private void showReceiptPreview(Uri uri) {
        selectedReceiptUri = uri;

        imgReceiptPreview.setImageURI(uri);
        receiptPreviewContainer.setVisibility(View.VISIBLE);

        btnAttachReceipt.setText("Change Bill Photo");
    }

    private void clearReceiptPreview() {
        selectedReceiptUri = null;
        autoAttachedScreenshotUri = null;

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

                availableAccountNames.clear();
                availableAccountNames.addAll(
                        accountNames
                );
                accountOptionsReady = true;

                if (lastScreenshotCanAutoFill) {
                    updateScreenshotStatusForAccount();
                }
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
        List<ExpenseItem> expenseItems =
                collectExpenseItems();

        if (expenseItems == null) {
            return;
        }

        String amountText = etAmount.getText() == null
                ? ""
                : etAmount.getText().toString().trim();

        if (amountText.isEmpty() && !expenseItems.isEmpty()) {
            BigDecimal itemTotal = BigDecimal.ZERO;

            for (ExpenseItem item : expenseItems) {
                itemTotal = itemTotal.add(
                        BigDecimal.valueOf(item.getTotal())
                );
            }

            amountText = formatMoney(itemTotal);
            etAmount.setText(amountText);
        }

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
                AppDatabase database = DatabaseClient
                        .getInstance(getApplicationContext())
                        .getAppDatabase();

                final long[] savedTransactionId = {0L};

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

                    for (ExpenseItem item : expenseItems) {
                        item.setTransactionId(
                                (int) transactionId
                        );
                    }

                    if (!expenseItems.isEmpty()) {
                        database.expenseItemDao()
                                .insertAll(expenseItems);
                    }

                    savedTransactionId[0] = transactionId;
                });

                if (!receiptUri.isEmpty()) {
                    ReceiptStore.saveReceiptUri(
                            getApplicationContext(),
                            savedTransactionId[0],
                            receiptUri
                    );
                }

                runOnUiThread(() -> {
                    String message;

                    if (!expenseItems.isEmpty()
                            && !receiptUri.isEmpty()) {
                        message =
                                "Expense, items and bill photo saved";
                    } else if (!expenseItems.isEmpty()) {
                        message =
                                "Expense and item details saved";
                    } else if (!receiptUri.isEmpty()) {
                        message =
                                "Expense and bill photo saved";
                    } else {
                        message =
                                "Expense saved successfully";
                    }

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
