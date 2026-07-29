package com.example.moneymanagerpro.activities;

import android.app.DatePickerDialog;
import android.content.ActivityNotFoundException;
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
import com.example.moneymanagerpro.utils.UpiPaymentResultParser;
import com.example.moneymanagerpro.utils.UpiQrPayloadParser;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanner;
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class AddExpenseActivity extends AppCompatActivity {

    private static final int MAX_ITEM_ROWS = 50;
    private static final String STATE_ITEM_ROWS =
            "expense_item_rows";
    private static final String STATE_UPI_RESULT =
            "upi_payment_result";
    private static final String STATE_UPI_NOTE =
            "upi_payment_note";
    private static final String STATE_UPI_REQUEST_REF =
            "upi_request_reference";

    private TextInputLayout inputAmount;
    private TextInputEditText etAmount;
    private TextInputEditText etDate;
    private TextInputEditText etNote;
    private TextInputEditText etUpiPayeeId;
    private TextInputEditText etUpiPayeeName;
    private TextInputLayout inputUpiPayeeId;
    private TextInputLayout inputUpiPayeeName;

    private MaterialAutoCompleteTextView dropdownCategory;
    private MaterialAutoCompleteTextView dropdownAccount;
    private MaterialAutoCompleteTextView dropdownUpiEntryMode;

    private MaterialButton btnAttachReceipt;
    private MaterialButton btnRemoveReceipt;
    private MaterialButton btnSaveExpense;
    private MaterialButton btnMoreItem;
    private MaterialButton btnPayWithUpi;
    private MaterialButton btnClearUpiPaymentResult;

    private ImageView imgReceiptPreview;
    private FrameLayout receiptPreviewContainer;
    private LinearLayout itemDetailsContainer;
    private TextView txtItemsTotal;
    private View upiPaymentResultCard;
    private TextView txtUpiPaymentStatus;

    private Calendar selectedCalendar;
    private String selectedDate;
    private Uri selectedReceiptUri;
    private UpiPaymentResultParser.Result
            lastUpiPaymentResult;
    private String lastUpiNoteBlock = "";
    private String currentUpiRequestReference = "";
    private GmsBarcodeScanner upiQrScanner;

    private final List<View> itemRows = new ArrayList<>();

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

    private final ActivityResultLauncher<Intent>
            upiPaymentLauncher =
            registerForActivityResult(
                    new ActivityResultContracts
                            .StartActivityForResult(),
                    result -> handleUpiPaymentResult(
                            result.getResultCode(),
                            result.getData()
                    )
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_expense);

        inputAmount = findViewById(R.id.inputAmount);
        etAmount = findViewById(R.id.etAmount);
        etDate = findViewById(R.id.etDate);
        etNote = findViewById(R.id.etNote);
        etUpiPayeeId =
                findViewById(R.id.etUpiPayeeId);
        etUpiPayeeName =
                findViewById(R.id.etUpiPayeeName);
        inputUpiPayeeId =
                findViewById(R.id.inputUpiPayeeId);
        inputUpiPayeeName =
                findViewById(R.id.inputUpiPayeeName);

        dropdownCategory = findViewById(R.id.dropdownCategory);
        dropdownAccount = findViewById(R.id.dropdownAccount);
        dropdownUpiEntryMode =
                findViewById(
                        R.id.dropdownUpiEntryMode
                );

        btnAttachReceipt = findViewById(R.id.btnAttachReceipt);
        btnRemoveReceipt = findViewById(R.id.btnRemoveReceipt);
        btnSaveExpense = findViewById(R.id.btnSaveExpense);
        btnMoreItem = findViewById(R.id.btnMoreItem);
        btnPayWithUpi =
                findViewById(R.id.btnPayWithUpi);
        btnClearUpiPaymentResult =
                findViewById(
                        R.id.btnClearUpiPaymentResult
                );

        imgReceiptPreview = findViewById(R.id.imgReceiptPreview);
        receiptPreviewContainer = findViewById(R.id.receiptPreviewContainer);
        itemDetailsContainer = findViewById(R.id.itemDetailsContainer);
        txtItemsTotal = findViewById(R.id.txtItemsTotal);
        upiPaymentResultCard =
                findViewById(
                        R.id.upiPaymentResultCard
                );
        txtUpiPaymentStatus =
                findViewById(
                        R.id.txtUpiPaymentStatus
                );

        TextView btnBack = findViewById(R.id.btnBack);

        selectedCalendar = Calendar.getInstance();
        updateDateField();

        btnBack.setOnClickListener(v -> finish());

        etDate.setOnClickListener(v -> showDatePicker());
        setupUpiEntryMode();

        btnAttachReceipt.setOnClickListener(v ->
                receiptPicker.launch(new String[]{"image/*"})
        );

        btnRemoveReceipt.setOnClickListener(v -> clearReceiptPreview());

        btnMoreItem.setOnClickListener(v -> addItemRow());

        btnPayWithUpi.setOnClickListener(
                view -> launchUpiPayment()
        );

        btnClearUpiPaymentResult
                .setOnClickListener(
                        view -> clearUpiPaymentResult()
                );

        btnSaveExpense.setOnClickListener(v -> saveExpense());

        if (!restoreItemRows(savedInstanceState)) {
            addItemRow();
        }
        restoreUpiState(savedInstanceState);
        loadFormOptions();
    }

    private void setupUpiEntryMode() {
        String[] modes = {
                "Enter UPI ID",
                "Scan UPI QR Code"
        };

        dropdownUpiEntryMode.setAdapter(
                new ArrayAdapter<>(
                        this,
                        android.R.layout
                                .simple_dropdown_item_1line,
                        modes
                )
        );
        dropdownUpiEntryMode.setText(
                modes[0],
                false
        );

        GmsBarcodeScannerOptions options =
                new GmsBarcodeScannerOptions.Builder()
                        .setBarcodeFormats(
                                Barcode.FORMAT_QR_CODE
                        )
                        .enableAutoZoom()
                        .build();
        upiQrScanner =
                GmsBarcodeScanning.getClient(
                        this,
                        options
                );

        dropdownUpiEntryMode
                .setOnItemClickListener(
                        (parent, view, position, id) -> {
                            if (position == 1) {
                                startUpiQrScan();
                            }
                        }
                );
    }

    private void startUpiQrScan() {
        if (upiQrScanner == null) {
            Toast.makeText(
                    this,
                    "QR scanner is not available",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        upiQrScanner.startScan()
                .addOnSuccessListener(
                        barcode -> applyUpiQrPayload(
                                barcode.getRawValue()
                        )
                )
                .addOnCanceledListener(
                        () -> dropdownUpiEntryMode
                                .setText(
                                        "Enter UPI ID",
                                        false
                                )
                )
                .addOnFailureListener(
                        exception -> {
                            dropdownUpiEntryMode.setText(
                                    "Enter UPI ID",
                                    false
                            );
                            Toast.makeText(
                                    this,
                                    "QR scanner could not start. "
                                            + "Check Google Play services and try again.",
                                    Toast.LENGTH_LONG
                            ).show();
                        }
                );
    }

    private void applyUpiQrPayload(
            String rawValue
    ) {
        UpiQrPayloadParser.Result result =
                UpiQrPayloadParser.parse(rawValue);

        if (!result.isValid()) {
            dropdownUpiEntryMode.setText(
                    "Enter UPI ID",
                    false
            );
            Toast.makeText(
                    this,
                    "This QR code does not contain a valid UPI payment ID",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        etUpiPayeeId.setText(
                result.getPayeeId()
        );
        etUpiPayeeName.setText(
                result.getPayeeName()
        );
        inputUpiPayeeId.setError(null);
        inputUpiPayeeName.setError(null);

        if (textOf(etAmount).isEmpty()
                && parsePositiveDecimal(
                result.getAmount()
        ) != null) {
            etAmount.setText(result.getAmount());
            inputAmount.setError(null);
        }

        dropdownUpiEntryMode.setText(
                "UPI QR Scanned",
                false
        );

        Toast.makeText(
                this,
                "UPI ID and receiver name filled from QR code",
                Toast.LENGTH_SHORT
        ).show();
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

        if (lastUpiPaymentResult != null) {
            outState.putSerializable(
                    STATE_UPI_RESULT,
                    lastUpiPaymentResult
            );
        }
        outState.putString(
                STATE_UPI_NOTE,
                lastUpiNoteBlock
        );
        outState.putString(
                STATE_UPI_REQUEST_REF,
                currentUpiRequestReference
        );

        super.onSaveInstanceState(outState);
    }

    @SuppressWarnings("deprecation")
    private void restoreUpiState(
            Bundle savedInstanceState
    ) {
        if (savedInstanceState == null) {
            return;
        }

        lastUpiPaymentResult =
                (UpiPaymentResultParser.Result)
                        savedInstanceState
                                .getSerializable(
                                        STATE_UPI_RESULT
                                );
        lastUpiNoteBlock =
                savedInstanceState.getString(
                        STATE_UPI_NOTE,
                        ""
                );
        currentUpiRequestReference =
                savedInstanceState.getString(
                        STATE_UPI_REQUEST_REF,
                        ""
                );

        if (lastUpiPaymentResult != null) {
            showUpiPaymentResult(
                    lastUpiPaymentResult
            );
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

     private void launchUpiPayment() {
        inputUpiPayeeId.setError(null);
        inputUpiPayeeName.setError(null);

        String payeeId =
                textOf(etUpiPayeeId);
        String payeeName =
                textOf(etUpiPayeeName);
        String amountText =
                textOf(etAmount);

        if (!payeeId.matches(
                "^[A-Za-z0-9._-]{2,}@[A-Za-z0-9.-]{2,}$"
        )) {
            inputUpiPayeeId.setError(
                    "Enter a valid UPI ID"
            );
            etUpiPayeeId.requestFocus();
            return;
        }

        if (payeeName.length() < 2) {
            inputUpiPayeeName.setError(
                    "Enter receiver name"
            );
            etUpiPayeeName.requestFocus();
            return;
        }

        BigDecimal amount =
                parsePositiveDecimal(amountText);

        if (amount == null) {
            inputAmount.setError(
                    "Enter expense amount before payment"
            );
            etAmount.requestFocus();
            return;
        }

        currentUpiRequestReference =
                "MMP" + System.currentTimeMillis();

        String transactionNote =
                textOf(etNote);

        if (transactionNote.isEmpty()) {
            transactionNote = "Money Manager Pro expense";
        }

        Uri paymentUri =
                new Uri.Builder()
                        .scheme("upi")
                        .authority("pay")
                        .appendQueryParameter(
                                "pa",
                                payeeId
                        )
                        .appendQueryParameter(
                                "pn",
                                payeeName
                        )
                        .appendQueryParameter(
                                "tr",
                                currentUpiRequestReference
                        )
                        .appendQueryParameter(
                                "tn",
                                transactionNote
                        )
                        .appendQueryParameter(
                                "am",
                                formatMoney(amount)
                        )
                        .appendQueryParameter(
                                "cu",
                                "INR"
                        )
                        .build();

        Intent paymentIntent =
                new Intent(
                        Intent.ACTION_VIEW,
                        paymentUri
                );
        Intent chooser = Intent.createChooser(
                paymentIntent,
                "Choose UPI / Payment App"
        );

        try {
            upiPaymentLauncher.launch(chooser);
        } catch (ActivityNotFoundException exception) {
            Toast.makeText(
                    this,
                    "No UPI payment app is available",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void handleUpiPaymentResult(
            int resultCode,
            Intent data
    ) {
        String response =
                collectUpiResponse(data);
        lastUpiPaymentResult =
                UpiPaymentResultParser.parse(response);
        showUpiPaymentResult(
                lastUpiPaymentResult
        );

        if (lastUpiPaymentResult.getStatus()
                == UpiPaymentResultParser
                .Status.SUCCESS) {
            appendUpiResultToNote(
                    lastUpiPaymentResult
            );
        } else {
            removeLastUpiNoteBlock();
        }
    }

    private String collectUpiResponse(Intent data) {
        if (data == null) {
            return "";
        }

        StringBuilder response =
                new StringBuilder();

        if (data.getDataString() != null) {
            response.append(
                    data.getDataString()
            );
        }

        if (data.getExtras() != null) {
            for (String key :
                    data.getExtras().keySet()) {
                Object value =
                        data.getExtras().get(key);

                if (value == null) {
                    continue;
                }

                if (response.length() > 0) {
                    response.append('&');
                }

                response.append(key)
                        .append('=')
                        .append(value);
            }
        }

        return response.toString();
    }

    private void showUpiPaymentResult(
            UpiPaymentResultParser.Result result
    ) {
        String reference =
                result.getTransactionReference()
                        .isEmpty()
                        ? currentUpiRequestReference
                        : result.getTransactionReference();
        String message;
        int color;

        switch (result.getStatus()) {
            case SUCCESS:
                message =
                        "Payment app reported SUCCESS."
                                + "\nReference: "
                                + valueOrNotDetected(
                                reference
                        )
                                + "\nReview the amount and account, then tap Save Expense.";
                color = getColor(R.color.success);
                break;
            case FAILED:
                message =
                        "Payment app reported FAILED."
                                + "\nNo successful UPI payment will be recorded.";
                color = getColor(R.color.error);
                break;
            case PENDING:
                message =
                        "Payment is PENDING or SUBMITTED."
                                + "\nWait for final confirmation before saving.";
                color = getColor(R.color.warning);
                break;
            case UNKNOWN:
            default:
                message =
                        "The payment app did not return a verifiable status."
                                + "\nCheck the payment app or bank before saving.";
                color = getColor(R.color.warning);
                break;
        }

        txtUpiPaymentStatus.setText(message);
        txtUpiPaymentStatus.setTextColor(color);
        upiPaymentResultCard.setVisibility(
                View.VISIBLE
        );
    }

    private void appendUpiResultToNote(
            UpiPaymentResultParser.Result result
    ) {
        removeLastUpiNoteBlock();

        String reference =
                result.getTransactionReference()
                        .isEmpty()
                        ? currentUpiRequestReference
                        : result.getTransactionReference();
        String block =
                "UPI • App-reported success"
                        + (reference.isEmpty()
                        ? ""
                        : " • Ref: " + reference);
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
        lastUpiNoteBlock = block;
    }

    private void removeLastUpiNoteBlock() {
        if (lastUpiNoteBlock.isEmpty()) {
            return;
        }

        String current = textOf(etNote);
        String withSeparator =
                "\n\n" + lastUpiNoteBlock;

        if (current.endsWith(withSeparator)) {
            current = current.substring(
                    0,
                    current.length()
                            - withSeparator.length()
            );
        } else if (current.equals(
                lastUpiNoteBlock
        )) {
            current = "";
        }

        etNote.setText(current);
        lastUpiNoteBlock = "";
    }

    private void clearUpiPaymentResult() {
        removeLastUpiNoteBlock();
        lastUpiPaymentResult = null;
        currentUpiRequestReference = "";
        upiPaymentResultCard.setVisibility(
                View.GONE
        );
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
        if (lastUpiPaymentResult != null
                && lastUpiPaymentResult.getStatus()
                != UpiPaymentResultParser
                .Status.SUCCESS) {
            Toast.makeText(
                    this,
                    "UPI payment is not successful. "
                            + "Verify it or clear the payment result.",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

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
