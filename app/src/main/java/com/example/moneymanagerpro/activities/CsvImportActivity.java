package com.example.moneymanagerpro.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.database.DatabaseClient;
import com.example.moneymanagerpro.model.Transaction;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CsvImportActivity extends AppCompatActivity {

    private MaterialButton btnChooseCsv;
    private MaterialButton btnImportCsv;

    private TextView txtFileName;
    private TextView txtImportSummary;
    private LinearLayout previewContainer;

    private final List<CsvRow> validRows = new ArrayList<>();

    private final ActivityResultLauncher<String[]> csvPicker =
            registerForActivityResult(
                    new ActivityResultContracts.OpenDocument(),
                    uri -> {
                        if (uri != null) {
                            readCsvFile(uri);
                        }
                    }
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_csv_import);

        TextView btnBack = findViewById(R.id.btnBack);
        btnChooseCsv = findViewById(R.id.btnChooseCsv);
        btnImportCsv = findViewById(R.id.btnImportCsv);

        txtFileName = findViewById(R.id.txtFileName);
        txtImportSummary = findViewById(R.id.txtImportSummary);
        previewContainer = findViewById(R.id.previewContainer);

        btnBack.setOnClickListener(v -> finish());

        btnChooseCsv.setOnClickListener(v ->
                csvPicker.launch(new String[]{
                        "text/*",
                        "application/csv",
                        "application/vnd.ms-excel"
                })
        );

        btnImportCsv.setOnClickListener(v -> importCsvRows());
    }

    private void readCsvFile(Uri uri) {
        btnChooseCsv.setEnabled(false);
        btnChooseCsv.setText("Reading File...");

        new Thread(() -> {
            List<CsvRow> parsedRows = new ArrayList<>();
            int skippedRows = 0;

            try {
                InputStream inputStream = getContentResolver().openInputStream(uri);

                if (inputStream == null) {
                    throw new Exception("Unable to open file");
                }

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(inputStream)
                );

                String firstLine = reader.readLine();

                if (firstLine == null) {
                    throw new Exception("The selected file is empty");
                }

                List<String> firstValues = parseCsvLine(firstLine);
                boolean hasHeader = isHeaderRow(firstValues);

                if (!hasHeader) {
                    CsvRow firstRow = createCsvRow(firstValues);

                    if (firstRow != null) {
                        parsedRows.add(firstRow);
                    } else {
                        skippedRows++;
                    }
                }

                String line;

                while ((line = reader.readLine()) != null) {
                    if (line.trim().isEmpty()) {
                        continue;
                    }

                    CsvRow row = createCsvRow(parseCsvLine(line));

                    if (row != null) {
                        parsedRows.add(row);
                    } else {
                        skippedRows++;
                    }
                }

                reader.close();

                int finalSkippedRows = skippedRows;

                runOnUiThread(() -> {
                    validRows.clear();
                    validRows.addAll(parsedRows);

                    showPreview(finalSkippedRows);

                    btnChooseCsv.setEnabled(true);
                    btnChooseCsv.setText("Choose Another CSV File");
                });

            } catch (Exception exception) {
                runOnUiThread(() -> {
                    validRows.clear();
                    previewContainer.removeAllViews();

                    txtFileName.setText("Unable to read the selected CSV file.");
                    txtImportSummary.setText(
                            "Use: Date, Type, Amount, Category, Account, Note"
                    );

                    btnChooseCsv.setEnabled(true);
                    btnChooseCsv.setText("Choose CSV File");
                    btnImportCsv.setEnabled(false);
                });
            }
        }).start();
    }

    private boolean isHeaderRow(List<String> values) {
        if (values.isEmpty()) {
            return false;
        }

        String firstValue = values.get(0).trim().toLowerCase(Locale.US);

        return firstValue.contains("date")
                || firstValue.contains("type")
                || firstValue.contains("amount");
    }

    private CsvRow createCsvRow(List<String> values) {
        if (values.size() < 3) {
            return null;
        }

        try {
            String date = getValue(values, 0);
            String type = normalizeType(getValue(values, 1));

            String amountText = getValue(values, 2)
                    .replace(",", "")
                    .replace("₹", "")
                    .trim();

            double amount = Double.parseDouble(amountText);

            if (amount <= 0 || type.isEmpty()) {
                return null;
            }

            String category = getValue(values, 3);
            String account = getValue(values, 4);
            String note = getValue(values, 5);

            if (category.isEmpty()) {
                category = "Other";
            }

            if (account.isEmpty()) {
                account = "Cash";
            }

            return new CsvRow(
                    normalizeDate(date),
                    type,
                    amount,
                    category,
                    account,
                    note
            );

        } catch (Exception exception) {
            return null;
        }
    }

    private String getValue(List<String> values, int index) {
        if (index >= values.size()) {
            return "";
        }

        return values.get(index).trim();
    }

    private String normalizeType(String type) {
        String value = type.trim().toUpperCase(Locale.US);

        if (value.equals("INCOME")) {
            return "INCOME";
        }

        if (value.equals("EXPENSE")) {
            return "EXPENSE";
        }

        if (value.equals("TRANSFER_IN")) {
            return "TRANSFER_IN";
        }

        if (value.equals("TRANSFER_OUT")) {
            return "TRANSFER_OUT";
        }

        return "";
    }

    private String normalizeDate(String dateText) {
        String cleanDate = dateText.trim();

        if (cleanDate.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return cleanDate + " 00:00";
        }

        String[] patterns = {
                "yyyy-MM-dd HH:mm",
                "dd/MM/yyyy",
                "dd-MM-yyyy",
                "dd MMM yyyy",
                "dd MMMM yyyy"
        };

        for (String pattern : patterns) {
            try {
                SimpleDateFormat inputFormat = new SimpleDateFormat(
                        pattern,
                        Locale.ENGLISH
                );

                Date parsedDate = inputFormat.parse(cleanDate);

                return new SimpleDateFormat(
                        "yyyy-MM-dd HH:mm",
                        Locale.US
                ).format(parsedDate);

            } catch (ParseException ignored) {
            }
        }

        return cleanDate;
    }

    private List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder currentValue = new StringBuilder();

        boolean insideQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char currentCharacter = line.charAt(i);

            if (currentCharacter == '"') {
                if (insideQuotes
                        && i + 1 < line.length()
                        && line.charAt(i + 1) == '"') {

                    currentValue.append('"');
                    i++;
                } else {
                    insideQuotes = !insideQuotes;
                }
            } else if (currentCharacter == ',' && !insideQuotes) {
                values.add(currentValue.toString());
                currentValue.setLength(0);
            } else {
                currentValue.append(currentCharacter);
            }
        }

        values.add(currentValue.toString());

        return values;
    }

    private void showPreview(int skippedRows) {
        previewContainer.removeAllViews();

        txtFileName.setText("CSV file selected successfully");

        txtImportSummary.setText(
                validRows.size() + " valid entries found"
                        + (skippedRows > 0
                        ? " • " + skippedRows + " invalid row(s) skipped"
                        : "")
        );

        btnImportCsv.setEnabled(!validRows.isEmpty());

        int previewCount = Math.min(validRows.size(), 8);

        for (int i = 0; i < previewCount; i++) {
            previewContainer.addView(createPreviewCard(validRows.get(i)));
        }

        if (validRows.size() > previewCount) {
            TextView moreRows = new TextView(this);
            moreRows.setText(
                    "Plus " + (validRows.size() - previewCount)
                            + " more entries ready to import"
            );

            moreRows.setTextColor(android.graphics.Color.parseColor("#475569"));
            moreRows.setTextSize(14);
            moreRows.setGravity(android.view.Gravity.CENTER);
            moreRows.setPadding(0, dp(12), 0, dp(6));

            previewContainer.addView(moreRows);
        }
    }

    private MaterialCardView createPreviewCard(CsvRow row) {
        MaterialCardView cardView = new MaterialCardView(this);
        cardView.setCardBackgroundColor(
                row.type.equals("INCOME")
                        ? android.graphics.Color.parseColor("#ECFDF5")
                        : android.graphics.Color.parseColor("#FEF2F2")
        );

        cardView.setRadius(dp(16));

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );

        cardParams.setMargins(0, 0, 0, dp(8));
        cardView.setLayoutParams(cardParams);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(12), dp(14), dp(12));

        TextView title = new TextView(this);
        title.setText(row.category + " • " + row.type);
        title.setTextSize(15);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setTextColor(
                row.type.equals("INCOME")
                        ? android.graphics.Color.parseColor("#166534")
                        : android.graphics.Color.parseColor("#991B1B")
        );

        TextView details = new TextView(this);
        details.setText(
                formatMoney(row.amount)
                        + " • "
                        + row.account
                        + " • "
                        + row.date
        );

        details.setTextSize(13);
        details.setTextColor(android.graphics.Color.parseColor("#475569"));
        details.setPadding(0, dp(5), 0, 0);

        content.addView(title);
        content.addView(details);

        cardView.addView(content);

        return cardView;
    }

    private void importCsvRows() {
        if (validRows.isEmpty()) {
            return;
        }

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Import entries?")
                .setMessage(
                        validRows.size()
                                + " transactions will be added to your current finance data."
                )
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Import", (dialog, which) -> performImport())
                .show();
    }

    private void performImport() {
        btnImportCsv.setEnabled(false);
        btnImportCsv.setText("Importing...");

        new Thread(() -> {
            int importedCount = 0;

            for (CsvRow row : validRows) {
                try {
                    Transaction transaction = new Transaction();
                    transaction.setType(row.type);
                    transaction.setAmount(row.amount);
                    transaction.setCategory(row.category);
                    transaction.setAccount(row.account);
                    transaction.setNote(row.note);
                    transaction.setDate(row.date);

                    DatabaseClient.getInstance(getApplicationContext())
                            .getAppDatabase()
                            .transactionDao()
                            .insert(transaction);

                    importedCount++;

                } catch (Exception ignored) {
                }
            }

            int finalImportedCount = importedCount;

            runOnUiThread(() -> {
                Toast.makeText(
                        CsvImportActivity.this,
                        finalImportedCount + " entries imported successfully",
                        Toast.LENGTH_LONG
                ).show();

                txtImportSummary.setText(
                        finalImportedCount + " entries imported into your finance data"
                );

                btnImportCsv.setText("Import Complete");
            });
        }).start();
    }

    private String formatMoney(double amount) {
        java.text.NumberFormat formatter = java.text.NumberFormat.getNumberInstance(
                new Locale("en", "IN")
        );

        formatter.setMinimumFractionDigits(2);
        formatter.setMaximumFractionDigits(2);

        return "₹" + formatter.format(amount);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private static class CsvRow {
        String date;
        String type;
        double amount;
        String category;
        String account;
        String note;

        CsvRow(
                String date,
                String type,
                double amount,
                String category,
                String account,
                String note
        ) {
            this.date = date;
            this.type = type;
            this.amount = amount;
            this.category = category;
            this.account = account;
            this.note = note;
        }
    }
}