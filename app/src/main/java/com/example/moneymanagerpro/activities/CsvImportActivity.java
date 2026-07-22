package com.example.moneymanagerpro.activities;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.database.AppDatabase;
import com.example.moneymanagerpro.database.DatabaseClient;
import com.example.moneymanagerpro.model.Transaction;
import com.example.moneymanagerpro.utils.BubbleTouchAnimator;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CsvImportActivity extends AppCompatActivity {

    private static final int MAX_PREVIEW_ROWS = 8;
    private static final int MAX_IMPORT_ROWS = 10000;

    private MaterialButton btnChooseCsv;
    private MaterialButton btnImportCsv;

    private TextView txtFileName;
    private TextView txtImportSummary;

    private LinearLayout previewContainer;

    private final List<CsvRow> validRows =
            new ArrayList<>();

    private String selectedFileName =
            "";

    private int readRequestVersion =
            0;

    private final ActivityResultLauncher<String[]> csvPicker =
            registerForActivityResult(
                    new ActivityResultContracts.OpenDocument(),
                    uri -> {
                        if (uri != null) {
                            preserveReadPermission(uri);
                            readCsvFile(uri);
                        }
                    }
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_csv_import);

        bindViews();
        prepareScreen();
    }

    private void bindViews() {
        TextView btnBack =
                findViewById(R.id.btnBack);

        btnChooseCsv =
                findViewById(R.id.btnChooseCsv);

        btnImportCsv =
                findViewById(R.id.btnImportCsv);

        txtFileName =
                findViewById(R.id.txtFileName);

        txtImportSummary =
                findViewById(R.id.txtImportSummary);

        previewContainer =
                findViewById(R.id.previewContainer);

        btnBack.setOnClickListener(
                view -> finish()
        );
    }

    private void prepareScreen() {
        btnChooseCsv.setOnClickListener(
                view -> openCsvPicker()
        );

        btnImportCsv.setOnClickListener(
                view -> confirmCsvImport()
        );

        BubbleTouchAnimator.apply(
                btnChooseCsv
        );

        BubbleTouchAnimator.apply(
                btnImportCsv
        );
    }

    private void openCsvPicker() {
        csvPicker.launch(
                new String[]{
                        "text/csv",
                        "text/comma-separated-values",
                        "text/plain",
                        "application/csv",
                        "application/vnd.ms-excel"
                }
        );
    }

    private void preserveReadPermission(
            Uri uri
    ) {
        try {
            getContentResolver()
                    .takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                    );

        } catch (Exception ignored) {
            // The selected document can still be read during this session.
        }
    }

    private void readCsvFile(
            Uri uri
    ) {
        int currentRequest =
                ++readRequestVersion;

        selectedFileName =
                getDisplayName(uri);

        showReadingState();

        new Thread(() -> {
            try {
                CsvReadResult result =
                        parseCsvDocument(uri);

                runOnUiThread(() -> {
                    if (currentRequest
                            != readRequestVersion) {

                        return;
                    }

                    validRows.clear();
                    validRows.addAll(
                            result.validRows
                    );

                    showPreview(
                            result.skippedRows
                    );
                });

            } catch (Exception exception) {
                String message =
                        safeText(
                                exception.getMessage(),
                                "Unable to read the selected CSV file."
                        );

                runOnUiThread(() -> {
                    if (currentRequest
                            != readRequestVersion) {

                        return;
                    }

                    showReadError(message);
                });
            }
        }).start();
    }

    private CsvReadResult parseCsvDocument(
            Uri uri
    ) throws Exception {
        List<CsvRow> parsedRows =
                new ArrayList<>();

        int skippedRows = 0;
        int processedRows = 0;

        InputStream inputStream =
                getContentResolver()
                        .openInputStream(uri);

        if (inputStream == null) {
            throw new Exception(
                    "The selected file could not be opened."
            );
        }

        try (
                BufferedReader reader =
                        new BufferedReader(
                                new InputStreamReader(
                                        inputStream,
                                        StandardCharsets.UTF_8
                                )
                        )
        ) {
            String line;
            boolean firstRecordChecked = false;
            char delimiter = ',';

            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }

                if (!firstRecordChecked) {
                    delimiter =
                            detectDelimiter(line);

                    List<String> firstValues =
                            parseCsvLine(
                                    line,
                                    delimiter
                            );

                    removeByteOrderMark(
                            firstValues
                    );

                    firstRecordChecked = true;

                    if (isHeaderRow(firstValues)) {
                        continue;
                    }

                    CsvRow firstRow =
                            createCsvRow(firstValues);

                    processedRows++;

                    if (firstRow != null) {
                        parsedRows.add(firstRow);
                    } else {
                        skippedRows++;
                    }

                    if (processedRows
                            >= MAX_IMPORT_ROWS) {

                        break;
                    }

                    continue;
                }

                List<String> values =
                        parseCsvLine(
                                line,
                                delimiter
                        );

                CsvRow row =
                        createCsvRow(values);

                processedRows++;

                if (row != null) {
                    parsedRows.add(row);
                } else {
                    skippedRows++;
                }

                if (processedRows
                        >= MAX_IMPORT_ROWS) {

                    break;
                }
            }

            if (!firstRecordChecked) {
                throw new Exception(
                        "The selected CSV file is empty."
                );
            }
        }

        return new CsvReadResult(
                parsedRows,
                skippedRows
        );
    }

    private char detectDelimiter(
            String line
    ) {
        int commaCount =
                countDelimiterOutsideQuotes(
                        line,
                        ','
                );

        int semicolonCount =
                countDelimiterOutsideQuotes(
                        line,
                        ';'
                );

        return semicolonCount > commaCount
                ? ';'
                : ',';
    }

    private int countDelimiterOutsideQuotes(
            String line,
            char delimiter
    ) {
        boolean insideQuotes = false;
        int count = 0;

        for (int index = 0;
             index < line.length();
             index++) {

            char currentCharacter =
                    line.charAt(index);

            if (currentCharacter == '"') {
                if (insideQuotes
                        && index + 1 < line.length()
                        && line.charAt(index + 1) == '"') {

                    index++;

                } else {
                    insideQuotes =
                            !insideQuotes;
                }

            } else if (currentCharacter == delimiter
                    && !insideQuotes) {

                count++;
            }
        }

        return count;
    }

    private List<String> parseCsvLine(
            String line,
            char delimiter
    ) {
        List<String> values =
                new ArrayList<>();

        StringBuilder currentValue =
                new StringBuilder();

        boolean insideQuotes = false;

        for (int index = 0;
             index < line.length();
             index++) {

            char currentCharacter =
                    line.charAt(index);

            if (currentCharacter == '"') {
                if (insideQuotes
                        && index + 1 < line.length()
                        && line.charAt(index + 1) == '"') {

                    currentValue.append('"');
                    index++;

                } else {
                    insideQuotes =
                            !insideQuotes;
                }

            } else if (currentCharacter == delimiter
                    && !insideQuotes) {

                values.add(
                        currentValue.toString()
                );

                currentValue.setLength(0);

            } else {
                currentValue.append(
                        currentCharacter
                );
            }
        }

        values.add(
                currentValue.toString()
        );

        return values;
    }

    private void removeByteOrderMark(
            List<String> values
    ) {
        if (values.isEmpty()) {
            return;
        }

        String firstValue =
                values.get(0);

        if (firstValue != null
                && firstValue.startsWith("\uFEFF")) {

            values.set(
                    0,
                    firstValue.substring(1)
            );
        }
    }

    private boolean isHeaderRow(
            List<String> values
    ) {
        if (values.size() < 3) {
            return false;
        }

        String firstColumn =
                normalizeHeaderText(
                        getValue(values, 0)
                );

        String secondColumn =
                normalizeHeaderText(
                        getValue(values, 1)
                );

        String thirdColumn =
                normalizeHeaderText(
                        getValue(values, 2)
                );

        boolean dateHeader =
                firstColumn.contains("date")
                        || firstColumn.contains("time");

        boolean typeHeader =
                secondColumn.contains("type")
                        || secondColumn.contains("transaction");

        boolean amountHeader =
                thirdColumn.contains("amount")
                        || thirdColumn.contains("value");

        return dateHeader
                && typeHeader
                && amountHeader;
    }

    private String normalizeHeaderText(
            String value
    ) {
        return safeText(
                value,
                ""
        )
                .trim()
                .toLowerCase(Locale.US)
                .replace("_", " ")
                .replace("-", " ");
    }

    private CsvRow createCsvRow(
            List<String> values
    ) {
        if (values == null
                || values.size() < 3) {

            return null;
        }

        String normalizedDate =
                normalizeDate(
                        getValue(values, 0)
                );

        if (normalizedDate == null) {
            return null;
        }

        String type =
                normalizeType(
                        getValue(values, 1)
                );

        if (type.isEmpty()) {
            return null;
        }

        Double amount =
                parseCsvAmount(
                        getValue(values, 2)
                );

        if (amount == null
                || amount <= 0
                || Double.isInfinite(amount)
                || Double.isNaN(amount)) {

            return null;
        }

        String category =
                getValue(values, 3);

        String account =
                getValue(values, 4);

        String note =
                getValue(values, 5);

        if (category.isEmpty()) {
            category = "Other";
        }

        if (account.isEmpty()) {
            account = "Cash";
        }

        return new CsvRow(
                normalizedDate,
                type,
                amount,
                category,
                account,
                note
        );
    }

    private String getValue(
            List<String> values,
            int index
    ) {
        if (values == null
                || index < 0
                || index >= values.size()) {

            return "";
        }

        String value =
                values.get(index);

        return value == null
                ? ""
                : value.trim();
    }

    private String normalizeType(
            String type
    ) {
        String normalizedType =
                safeText(
                        type,
                        ""
                )
                        .trim()
                        .toUpperCase(Locale.US)
                        .replace("-", "_")
                        .replace(" ", "_");

        while (normalizedType.contains("__")) {
            normalizedType =
                    normalizedType.replace(
                            "__",
                            "_"
                    );
        }

        if ("INCOME".equals(normalizedType)) {
            return "INCOME";
        }

        if ("EXPENSE".equals(normalizedType)) {
            return "EXPENSE";
        }

        if ("TRANSFER_IN".equals(normalizedType)
                || "TRANSFERIN".equals(normalizedType)) {

            return "TRANSFER_IN";
        }

        if ("TRANSFER_OUT".equals(normalizedType)
                || "TRANSFEROUT".equals(normalizedType)) {

            return "TRANSFER_OUT";
        }

        return "";
    }

    private Double parseCsvAmount(
            String amountText
    ) {
        if (amountText == null
                || amountText.trim().isEmpty()) {

            return null;
        }

        String cleanAmount =
                amountText
                        .trim()
                        .replace("\u00A0", "")
                        .replace(",", "")
                        .replace("₹", "")
                        .replaceAll(
                                "(?i)INR",
                                ""
                        )
                        .replaceAll(
                                "(?i)RS\\.?",
                                ""
                        )
                        .replace(" ", "");

        if (cleanAmount.startsWith("(")
                && cleanAmount.endsWith(")")) {

            cleanAmount =
                    "-"
                            + cleanAmount.substring(
                            1,
                            cleanAmount.length() - 1
                    );
        }

        try {
            return Double.parseDouble(
                    cleanAmount
            );

        } catch (Exception exception) {
            return null;
        }
    }

    private String normalizeDate(
            String dateText
    ) {
        if (dateText == null
                || dateText.trim().isEmpty()) {

            return null;
        }

        String cleanDate =
                dateText.trim();

        String[] patterns = {
                "yyyy-MM-dd HH:mm:ss.SSS",
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd HH:mm",
                "yyyy-MM-dd'T'HH:mm:ss.SSS",
                "yyyy-MM-dd'T'HH:mm:ss",
                "yyyy-MM-dd'T'HH:mm",
                "yyyy-MM-dd",
                "yyyy/MM/dd HH:mm:ss",
                "yyyy/MM/dd HH:mm",
                "yyyy/MM/dd",
                "dd/MM/yyyy HH:mm:ss",
                "dd/MM/yyyy HH:mm",
                "dd/MM/yyyy",
                "dd-MM-yyyy HH:mm:ss",
                "dd-MM-yyyy HH:mm",
                "dd-MM-yyyy",
                "dd MMM yyyy HH:mm",
                "dd MMM yyyy",
                "dd MMMM yyyy HH:mm",
                "dd MMMM yyyy"
        };

        for (String pattern : patterns) {
            Date parsedDate =
                    parseStrictDate(
                            cleanDate,
                            pattern
                    );

            if (parsedDate != null) {
                return new SimpleDateFormat(
                        "yyyy-MM-dd HH:mm",
                        Locale.US
                ).format(parsedDate);
            }
        }

        return null;
    }

    private Date parseStrictDate(
            String value,
            String pattern
    ) {
        try {
            SimpleDateFormat format =
                    new SimpleDateFormat(
                            pattern,
                            Locale.ENGLISH
                    );

            format.setLenient(false);

            ParsePosition parsePosition =
                    new ParsePosition(0);

            Date parsedDate =
                    format.parse(
                            value,
                            parsePosition
                    );

            if (parsedDate == null
                    || parsePosition.getIndex()
                    != value.length()) {

                return null;
            }

            return parsedDate;

        } catch (Exception exception) {
            return null;
        }
    }

    private void showReadingState() {
        validRows.clear();
        previewContainer.removeAllViews();

        txtFileName.setText(
                selectedFileName.isEmpty()
                        ? "Reading selected CSV file"
                        : selectedFileName
        );

        txtImportSummary.setText(
                "Checking dates, transaction types, amounts and column values..."
        );

        btnChooseCsv.setEnabled(false);
        btnChooseCsv.setText("Reading File...");

        btnImportCsv.setEnabled(false);
        btnImportCsv.setText("Import Entries");

        addPreviewMessage(
                "Reading CSV data...",
                "Valid transactions will appear here after the file is checked.",
                getColorValue(R.color.secondary),
                getColorValue(R.color.info_surface),
                getColorValue(R.color.info_outline),
                "CSV"
        );
    }

    private void showPreview(
            int skippedRows
    ) {
        previewContainer.removeAllViews();

        int validCount =
                validRows.size();

        txtFileName.setText(
                selectedFileName.isEmpty()
                        ? "CSV file selected"
                        : selectedFileName
        );

        StringBuilder summary =
                new StringBuilder();

        summary.append(validCount)
                .append(
                        validCount == 1
                                ? " valid entry"
                                : " valid entries"
                );

        if (skippedRows > 0) {
            summary.append(" • ")
                    .append(skippedRows)
                    .append(
                            skippedRows == 1
                                    ? " invalid row skipped"
                                    : " invalid rows skipped"
                    );
        }

        if (validCount + skippedRows
                >= MAX_IMPORT_ROWS) {

            summary.append(
                    " • Maximum 10,000 rows checked"
            );
        }

        txtImportSummary.setText(
                summary.toString()
        );

        btnChooseCsv.setEnabled(true);
        btnChooseCsv.setText(
                "Choose Another CSV File"
        );

        btnImportCsv.setEnabled(
                validCount > 0
        );

        btnImportCsv.setText(
                validCount > 0
                        ? "Import "
                          + validCount
                          + (
                        validCount == 1
                        ? " Entry"
                        : " Entries"
                )
                        : "No Valid Entries"
        );

        if (validCount == 0) {
            addPreviewMessage(
                    "No valid entries found",
                    "Check the date, type, amount and CSV column order, then select the corrected file.",
                    getColorValue(R.color.expense),
                    getColorValue(R.color.expense_surface),
                    getColorValue(R.color.expense_outline),
                    "!"
            );

            return;
        }

        int previewCount =
                Math.min(
                        validCount,
                        MAX_PREVIEW_ROWS
                );

        for (int index = 0;
             index < previewCount;
             index++) {

            previewContainer.addView(
                    createPreviewCard(
                            validRows.get(index),
                            index + 1
                    )
            );
        }

        if (validCount > previewCount) {
            int remainingCount =
                    validCount - previewCount;

            addPreviewMessage(
                    remainingCount
                            + (
                            remainingCount == 1
                                    ? " more entry"
                                    : " more entries"
                    ),
                    "These transactions are also validated and ready to import.",
                    getColorValue(R.color.purple),
                    getColorValue(R.color.purple_surface),
                    getColorValue(R.color.purple_outline),
                    "+"
            );
        }
    }

    private void showReadError(
            String errorMessage
    ) {
        validRows.clear();
        previewContainer.removeAllViews();

        txtFileName.setText(
                selectedFileName.isEmpty()
                        ? "Unable to read CSV file"
                        : selectedFileName
        );

        txtImportSummary.setText(
                errorMessage
        );

        btnChooseCsv.setEnabled(true);
        btnChooseCsv.setText(
                "Choose CSV File"
        );

        btnImportCsv.setEnabled(false);
        btnImportCsv.setText(
                "Import Entries"
        );

        addPreviewMessage(
                "CSV file could not be validated",
                "Use this order: Date, Type, Amount, Category, Account, Note.",
                getColorValue(R.color.expense),
                getColorValue(R.color.expense_surface),
                getColorValue(R.color.expense_outline),
                "!"
        );
    }

    private MaterialCardView createPreviewCard(
            CsvRow row,
            int position
    ) {
        TypeStyle typeStyle =
                getTypeStyle(row.type);

        MaterialCardView cardView =
                new MaterialCardView(this);

        cardView.setCardBackgroundColor(
                getColorValue(
                        R.color.app_surface
                )
        );

        cardView.setRadius(
                dp(16)
        );

        cardView.setCardElevation(0);

        cardView.setStrokeWidth(
                dp(1)
        );

        cardView.setStrokeColor(
                typeStyle.outlineColor
        );

        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        cardParams.setMargins(
                0,
                dp(4),
                0,
                dp(5)
        );

        cardView.setLayoutParams(
                cardParams
        );

        LinearLayout content =
                new LinearLayout(this);

        content.setOrientation(
                LinearLayout.VERTICAL
        );

        content.setPadding(
                dp(12),
                dp(11),
                dp(12),
                dp(11)
        );

        LinearLayout headerRow =
                new LinearLayout(this);

        headerRow.setOrientation(
                LinearLayout.HORIZONTAL
        );

        headerRow.setGravity(
                Gravity.CENTER_VERTICAL
        );

        TextView icon =
                createPreviewIcon(
                        typeStyle.symbol,
                        typeStyle.accentColor,
                        typeStyle.surfaceColor,
                        typeStyle.outlineColor
                );

        headerRow.addView(icon);

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
                dp(10),
                0,
                dp(8),
                0
        );

        titleContainer.setLayoutParams(
                titleParams
        );

        TextView category =
                createText(
                        row.category,
                        14,
                        getColorValue(
                                R.color.app_text_primary
                        ),
                        true
                );

        TextView type =
                createText(
                        position
                                + ". "
                                + typeStyle.label,
                        10,
                        typeStyle.accentColor,
                        true
                );

        LinearLayout.LayoutParams typeParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        typeParams.setMargins(
                0,
                dp(3),
                0,
                0
        );

        type.setLayoutParams(
                typeParams
        );

        titleContainer.addView(category);
        titleContainer.addView(type);

        headerRow.addView(titleContainer);

        TextView amount =
                createAmountBadge(
                        formatMoney(row.amount),
                        typeStyle.accentColor,
                        typeStyle.surfaceColor,
                        typeStyle.outlineColor
                );

        headerRow.addView(amount);

        content.addView(headerRow);

        LinearLayout detailBox =
                new LinearLayout(this);

        detailBox.setOrientation(
                LinearLayout.VERTICAL
        );

        detailBox.setPadding(
                dp(11),
                dp(9),
                dp(11),
                dp(9)
        );

        detailBox.setBackground(
                createRoundedDrawable(
                        getColorValue(
                                R.color.app_surface_soft
                        ),
                        getColorValue(
                                R.color.app_outline_soft
                        ),
                        12
                )
        );

        LinearLayout.LayoutParams detailParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        detailParams.setMargins(
                0,
                dp(10),
                0,
                0
        );

        detailBox.setLayoutParams(
                detailParams
        );

        addDetailRow(
                detailBox,
                "Date",
                formatVisibleDate(row.date)
        );

        addDetailRow(
                detailBox,
                "Account",
                row.account
        );

        if (!row.note.isEmpty()) {
            addDetailRow(
                    detailBox,
                    "Note",
                    row.note
            );
        }

        content.addView(detailBox);

        cardView.addView(content);

        return cardView;
    }

    private TextView createPreviewIcon(
            String symbol,
            int textColor,
            int backgroundColor,
            int outlineColor
    ) {
        TextView icon =
                createText(
                        symbol,
                        symbol.length() > 2
                                ? 9
                                : 16,
                        textColor,
                        true
                );

        icon.setGravity(
                Gravity.CENTER
        );

        icon.setBackground(
                createRoundedDrawable(
                        backgroundColor,
                        outlineColor,
                        13
                )
        );

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        dp(42),
                        dp(42)
                );

        icon.setLayoutParams(params);

        return icon;
    }

    private TextView createAmountBadge(
            String amount,
            int textColor,
            int backgroundColor,
            int outlineColor
    ) {
        TextView badge =
                createText(
                        amount,
                        11,
                        textColor,
                        true
                );

        badge.setGravity(
                Gravity.CENTER
        );

        badge.setMaxLines(1);

        badge.setPadding(
                dp(8),
                0,
                dp(8),
                0
        );

        badge.setBackground(
                createRoundedDrawable(
                        backgroundColor,
                        outlineColor,
                        11
                )
        );

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        dp(31)
                );

        badge.setLayoutParams(params);

        return badge;
    }

    private void addDetailRow(
            LinearLayout parent,
            String label,
            String value
    ) {
        LinearLayout row =
                new LinearLayout(this);

        row.setOrientation(
                LinearLayout.HORIZONTAL
        );

        row.setGravity(
                Gravity.TOP
        );

        LinearLayout.LayoutParams rowParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        if (parent.getChildCount() > 0) {
            rowParams.setMargins(
                    0,
                    dp(6),
                    0,
                    0
            );
        }

        row.setLayoutParams(rowParams);

        TextView labelView =
                createText(
                        label,
                        10,
                        getColorValue(
                                R.color.app_text_secondary
                        ),
                        false
                );

        labelView.setLayoutParams(
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        0.8f
                )
        );

        TextView valueView =
                createText(
                        safeText(
                                value,
                                "Not added"
                        ),
                        10,
                        getColorValue(
                                R.color.app_text_primary
                        ),
                        true
                );

        valueView.setGravity(
                Gravity.END
        );

        valueView.setLayoutParams(
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1.4f
                )
        );

        row.addView(labelView);
        row.addView(valueView);

        parent.addView(row);
    }

    private void addPreviewMessage(
            String title,
            String description,
            int accentColor,
            int surfaceColor,
            int outlineColor,
            String symbol
    ) {
        MaterialCardView card =
                new MaterialCardView(this);

        card.setCardBackgroundColor(
                surfaceColor
        );

        card.setRadius(
                dp(15)
        );

        card.setCardElevation(0);

        card.setStrokeWidth(
                dp(1)
        );

        card.setStrokeColor(
                outlineColor
        );

        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        cardParams.setMargins(
                0,
                dp(4),
                0,
                dp(4)
        );

        card.setLayoutParams(
                cardParams
        );

        LinearLayout content =
                new LinearLayout(this);

        content.setOrientation(
                LinearLayout.HORIZONTAL
        );

        content.setGravity(
                Gravity.CENTER_VERTICAL
        );

        content.setPadding(
                dp(12),
                dp(11),
                dp(12),
                dp(11)
        );

        TextView icon =
                createPreviewIcon(
                        symbol,
                        accentColor,
                        getColorValue(
                                R.color.app_surface
                        ),
                        outlineColor
                );

        content.addView(icon);

        LinearLayout textContainer =
                new LinearLayout(this);

        textContainer.setOrientation(
                LinearLayout.VERTICAL
        );

        LinearLayout.LayoutParams textParams =
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                );

        textParams.setMargins(
                dp(10),
                0,
                0,
                0
        );

        textContainer.setLayoutParams(
                textParams
        );

        TextView titleView =
                createText(
                        title,
                        12,
                        accentColor,
                        true
                );

        TextView descriptionView =
                createText(
                        description,
                        10,
                        getColorValue(
                                R.color.app_text_secondary
                        ),
                        false
                );

        descriptionView.setLineSpacing(
                dp(2),
                1f
        );

        LinearLayout.LayoutParams descriptionParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        descriptionParams.setMargins(
                0,
                dp(3),
                0,
                0
        );

        descriptionView.setLayoutParams(
                descriptionParams
        );

        textContainer.addView(titleView);
        textContainer.addView(descriptionView);

        content.addView(textContainer);

        card.addView(content);

        previewContainer.addView(card);
    }

    private void confirmCsvImport() {
        if (validRows.isEmpty()) {
            Toast.makeText(
                    this,
                    "Choose a valid CSV file first",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        String fileDescription =
                selectedFileName.isEmpty()
                        ? "the selected CSV file"
                        : "\"" + selectedFileName + "\"";

        new AlertDialog.Builder(this)
                .setTitle("Import CSV Entries")
                .setMessage(
                        validRows.size()
                                + (
                                validRows.size() == 1
                                        ? " transaction"
                                        : " transactions"
                        )
                                + " from "
                                + fileDescription
                                + " will be added to your existing finance data.\n\n"
                                + "Importing the same file again may create duplicate transactions."
                )
                .setNegativeButton(
                        "Cancel",
                        null
                )
                .setPositiveButton(
                        "Import",
                        (dialog, which) ->
                                performImport()
                )
                .show();
    }

    private void performImport() {
        if (validRows.isEmpty()) {
            return;
        }

        List<CsvRow> rowsToImport =
                new ArrayList<>(
                        validRows
                );

        btnChooseCsv.setEnabled(false);
        btnImportCsv.setEnabled(false);

        btnImportCsv.setText(
                "Importing..."
        );

        new Thread(() -> {
            try {
                AppDatabase database =
                        DatabaseClient
                                .getInstance(
                                        getApplicationContext()
                                )
                                .getAppDatabase();

                database.runInTransaction(() -> {
                    for (CsvRow row : rowsToImport) {
                        Transaction transaction =
                                new Transaction();

                        transaction.setType(
                                row.type
                        );

                        transaction.setAmount(
                                row.amount
                        );

                        transaction.setCategory(
                                row.category
                        );

                        transaction.setAccount(
                                row.account
                        );

                        transaction.setNote(
                                row.note
                        );

                        transaction.setDate(
                                row.date
                        );

                        database
                                .transactionDao()
                                .insert(transaction);
                    }
                });

                runOnUiThread(() ->
                        showImportSuccess(
                                rowsToImport.size()
                        )
                );

            } catch (Exception exception) {
                runOnUiThread(() ->
                        showImportFailure()
                );
            }
        }).start();
    }

    private void showImportSuccess(
            int importedCount
    ) {
        validRows.clear();
        previewContainer.removeAllViews();

        txtImportSummary.setText(
                importedCount
                        + (
                        importedCount == 1
                                ? " transaction imported successfully"
                                : " transactions imported successfully"
                )
        );

        btnChooseCsv.setEnabled(true);
        btnChooseCsv.setText(
                "Choose Another CSV File"
        );

        btnImportCsv.setEnabled(false);
        btnImportCsv.setText(
                "Import Complete"
        );

        addPreviewMessage(
                "Import completed",
                importedCount
                        + (
                        importedCount == 1
                                ? " transaction was added to your finance data."
                                : " transactions were added to your finance data."
                ),
                getColorValue(R.color.success),
                getColorValue(R.color.success_surface),
                getColorValue(R.color.success_outline),
                "✓"
        );

        Toast.makeText(
                this,
                importedCount
                        + (
                        importedCount == 1
                                ? " entry imported successfully"
                                : " entries imported successfully"
                ),
                Toast.LENGTH_LONG
        ).show();
    }

    private void showImportFailure() {
        btnChooseCsv.setEnabled(true);
        btnImportCsv.setEnabled(
                !validRows.isEmpty()
        );

        btnChooseCsv.setText(
                "Choose Another CSV File"
        );

        btnImportCsv.setText(
                validRows.isEmpty()
                        ? "Import Entries"
                        : "Import "
                          + validRows.size()
                          + (
                        validRows.size() == 1
                        ? " Entry"
                        : " Entries"
                )
        );

        Toast.makeText(
                this,
                "CSV import failed. No partial import was saved.",
                Toast.LENGTH_LONG
        ).show();
    }

    private TypeStyle getTypeStyle(
            String type
    ) {
        if ("INCOME".equalsIgnoreCase(type)) {
            return new TypeStyle(
                    "Income",
                    "↑",
                    getColorValue(R.color.success),
                    getColorValue(R.color.success_surface),
                    getColorValue(R.color.success_outline)
            );
        }

        if ("EXPENSE".equalsIgnoreCase(type)) {
            return new TypeStyle(
                    "Expense",
                    "↓",
                    getColorValue(R.color.expense),
                    getColorValue(R.color.expense_surface),
                    getColorValue(R.color.expense_outline)
            );
        }

        if ("TRANSFER_IN".equalsIgnoreCase(type)) {
            return new TypeStyle(
                    "Transfer In",
                    "IN",
                    getColorValue(R.color.purple),
                    getColorValue(R.color.purple_surface),
                    getColorValue(R.color.purple_outline)
            );
        }

        return new TypeStyle(
                "Transfer Out",
                "OUT",
                getColorValue(R.color.warning),
                getColorValue(R.color.warning_surface),
                getColorValue(R.color.warning_outline)
        );
    }

    private String getDisplayName(
            Uri uri
    ) {
        String displayName = "";

        Cursor cursor = null;

        try {
            cursor =
                    getContentResolver()
                            .query(
                                    uri,
                                    new String[]{
                                            OpenableColumns.DISPLAY_NAME
                                    },
                                    null,
                                    null,
                                    null
                            );

            if (cursor != null
                    && cursor.moveToFirst()) {

                int nameIndex =
                        cursor.getColumnIndex(
                                OpenableColumns.DISPLAY_NAME
                        );

                if (nameIndex >= 0) {
                    displayName =
                            safeText(
                                    cursor.getString(nameIndex),
                                    ""
                            );
                }
            }

        } catch (Exception ignored) {
            displayName = "";

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        if (displayName.isEmpty()) {
            String lastSegment =
                    uri.getLastPathSegment();

            displayName =
                    safeText(
                            lastSegment,
                            "Selected CSV file"
                    );
        }

        return displayName;
    }

    private String formatVisibleDate(
            String storedDate
    ) {
        Date date =
                parseStrictDate(
                        storedDate,
                        "yyyy-MM-dd HH:mm"
                );

        if (date == null) {
            return storedDate;
        }

        return new SimpleDateFormat(
                "dd MMM yyyy, hh:mm a",
                Locale.ENGLISH
        ).format(date);
    }

    private String formatMoney(
            double amount
    ) {
        NumberFormat formatter =
                NumberFormat.getNumberInstance(
                        new Locale(
                                "en",
                                "IN"
                        )
                );

        formatter.setMinimumFractionDigits(2);
        formatter.setMaximumFractionDigits(2);

        return "₹"
                + formatter.format(amount);
    }

    private TextView createText(
            String text,
            float textSize,
            int textColor,
            boolean bold
    ) {
        TextView textView =
                new TextView(this);

        textView.setText(text);
        textView.setTextSize(textSize);
        textView.setTextColor(textColor);

        if (bold) {
            textView.setTypeface(
                    Typeface.DEFAULT,
                    Typeface.BOLD
            );
        }

        return textView;
    }

    private GradientDrawable createRoundedDrawable(
            int backgroundColor,
            int outlineColor,
            int radiusDp
    ) {
        GradientDrawable drawable =
                new GradientDrawable();

        drawable.setColor(
                backgroundColor
        );

        drawable.setStroke(
                dp(1),
                outlineColor
        );

        drawable.setCornerRadius(
                dp(radiusDp)
        );

        return drawable;
    }

    private String safeText(
            String value,
            String fallback
    ) {
        if (value == null
                || value.trim().isEmpty()) {

            return fallback;
        }

        return value.trim();
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

    private static class CsvReadResult {

        private final List<CsvRow> validRows;
        private final int skippedRows;

        private CsvReadResult(
                List<CsvRow> validRows,
                int skippedRows
        ) {
            this.validRows =
                    validRows;

            this.skippedRows =
                    skippedRows;
        }
    }

    private static class TypeStyle {

        private final String label;
        private final String symbol;

        private final int accentColor;
        private final int surfaceColor;
        private final int outlineColor;

        private TypeStyle(
                String label,
                String symbol,
                int accentColor,
                int surfaceColor,
                int outlineColor
        ) {
            this.label =
                    label;

            this.symbol =
                    symbol;

            this.accentColor =
                    accentColor;

            this.surfaceColor =
                    surfaceColor;

            this.outlineColor =
                    outlineColor;
        }
    }

    private static class CsvRow {

        private final String date;
        private final String type;

        private final double amount;

        private final String category;
        private final String account;
        private final String note;

        private CsvRow(
                String date,
                String type,
                double amount,
                String category,
                String account,
                String note
        ) {
            this.date =
                    date;

            this.type =
                    type;

            this.amount =
                    amount;

            this.category =
                    category;

            this.account =
                    account;

            this.note =
                    note;
        }
    }
}