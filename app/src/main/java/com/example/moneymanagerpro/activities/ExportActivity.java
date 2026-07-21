package com.example.moneymanagerpro.activities;

import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.database.DatabaseClient;
import com.example.moneymanagerpro.model.Transaction;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ExportActivity extends AppCompatActivity {

    private static final int REQUEST_EXPORT_CSV = 101;
    private static final int REQUEST_EXPORT_PDF = 102;

    private TextView txtExportStatus;
    private Button btnExportCsv;
    private Button btnExportPdf;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_export);

        txtExportStatus = findViewById(R.id.txtExportStatus);
        btnExportCsv = findViewById(R.id.btnExportCsv);
        btnExportPdf = findViewById(R.id.btnExportPdf);

        btnExportCsv.setOnClickListener(v -> createCsvFile());

        btnExportPdf.setOnClickListener(v -> createPdfFile());
    }

    private void createCsvFile() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/csv");

        intent.putExtra(
                Intent.EXTRA_TITLE,
                "MoneyManager_Transactions_" +
                        getFileDate() +
                        ".csv"
        );

        startActivityForResult(intent, REQUEST_EXPORT_CSV);
    }

    private void createPdfFile() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/pdf");

        intent.putExtra(
                Intent.EXTRA_TITLE,
                "MoneyManager_Report_" +
                        getFileDate() +
                        ".pdf"
        );

        startActivityForResult(intent, REQUEST_EXPORT_PDF);
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            @Nullable Intent data
    ) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode != RESULT_OK ||
                data == null ||
                data.getData() == null) {
            return;
        }

        Uri fileUri = data.getData();

        if (requestCode == REQUEST_EXPORT_CSV) {
            exportCsv(fileUri);
        } else if (requestCode == REQUEST_EXPORT_PDF) {
            exportPdf(fileUri);
        }
    }

    private void exportCsv(Uri fileUri) {
        setExportButtonsEnabled(false);
        txtExportStatus.setText("CSV report बन रहा है...");

        new Thread(() -> {
            try {
                List<Transaction> transactions = DatabaseClient
                        .getInstance(getApplicationContext())
                        .getAppDatabase()
                        .transactionDao()
                        .getAllTransactions();

                OutputStream outputStream = getContentResolver()
                        .openOutputStream(fileUri);

                if (outputStream == null) {
                    throw new IOException("File cannot be created");
                }

                BufferedWriter writer = new BufferedWriter(
                        new OutputStreamWriter(outputStream, "UTF-8")
                );

                writer.write("\uFEFF");
                writer.write(
                        "ID,Type,Amount,Category,Account,Note,Date"
                );
                writer.newLine();

                for (Transaction transaction : transactions) {
                    writer.write(
                            transaction.getId() + "," +
                                    escapeCsv(transaction.getType()) + "," +
                                    transaction.getAmount() + "," +
                                    escapeCsv(transaction.getCategory()) + "," +
                                    escapeCsv(transaction.getAccount()) + "," +
                                    escapeCsv(transaction.getNote()) + "," +
                                    escapeCsv(transaction.getDate())
                    );

                    writer.newLine();
                }

                writer.flush();
                writer.close();

                runOnUiThread(() -> showExportSuccess(
                        "CSV export सफल हुआ। इसे Excel में खोल सकते हैं।"
                ));

            } catch (Exception exception) {
                runOnUiThread(() -> showExportError());
            }
        }).start();
    }

    private void exportPdf(Uri fileUri) {
        setExportButtonsEnabled(false);
        txtExportStatus.setText("PDF report बन रही है...");

        new Thread(() -> {
            PdfDocument document = new PdfDocument();

            try {
                List<Transaction> transactions = DatabaseClient
                        .getInstance(getApplicationContext())
                        .getAppDatabase()
                        .transactionDao()
                        .getAllTransactions();

                Paint titlePaint = new Paint();
                titlePaint.setColor(Color.rgb(27, 94, 32));
                titlePaint.setTextSize(21);
                titlePaint.setFakeBoldText(true);

                Paint normalPaint = new Paint();
                normalPaint.setColor(Color.DKGRAY);
                normalPaint.setTextSize(10);

                Paint linePaint = new Paint();
                linePaint.setColor(Color.LTGRAY);
                linePaint.setStrokeWidth(1);

                int pageNumber = 1;
                PdfDocument.Page page = document.startPage(
                        new PdfDocument.PageInfo.Builder(
                                595,
                                842,
                                pageNumber
                        ).create()
                );

                Canvas canvas = page.getCanvas();

                float currentY = drawPdfHeader(
                        canvas,
                        titlePaint,
                        normalPaint
                );

                if (transactions.isEmpty()) {
                    canvas.drawText(
                            "No transactions available.",
                            40,
                            currentY + 30,
                            normalPaint
                    );
                }

                for (Transaction transaction : transactions) {
                    if (currentY > 770) {
                        document.finishPage(page);

                        pageNumber++;

                        page = document.startPage(
                                new PdfDocument.PageInfo.Builder(
                                        595,
                                        842,
                                        pageNumber
                                ).create()
                        );

                        canvas = page.getCanvas();

                        currentY = drawPdfHeader(
                                canvas,
                                titlePaint,
                                normalPaint
                        );
                    }

                    drawPdfTransaction(
                            canvas,
                            linePaint,
                            normalPaint,
                            transaction,
                            currentY
                    );

                    currentY += 46;
                }

                document.finishPage(page);

                OutputStream outputStream = getContentResolver()
                        .openOutputStream(fileUri);

                if (outputStream == null) {
                    throw new IOException("File cannot be created");
                }

                document.writeTo(outputStream);
                outputStream.close();
                document.close();

                runOnUiThread(() -> showExportSuccess(
                        "PDF report सफलतापूर्वक save हो गई।"
                ));

            } catch (Exception exception) {
                document.close();

                runOnUiThread(() -> showExportError());
            }
        }).start();
    }

    private float drawPdfHeader(
            Canvas canvas,
            Paint titlePaint,
            Paint normalPaint
    ) {
        canvas.drawText(
                "Money Manager Pro",
                40,
                45,
                titlePaint
        );

        canvas.drawText(
                "Transaction Report • Generated: " +
                        getCurrentDateTime(),
                40,
                66,
                normalPaint
        );

        canvas.drawText(
                "Date",
                40,
                95,
                normalPaint
        );

        canvas.drawText(
                "Type",
                155,
                95,
                normalPaint
        );

        canvas.drawText(
                "Category / Account",
                235,
                95,
                normalPaint
        );

        canvas.drawText(
                "Amount",
                470,
                95,
                normalPaint
        );

        return 118;
    }

    private void drawPdfTransaction(
            Canvas canvas,
            Paint linePaint,
            Paint normalPaint,
            Transaction transaction,
            float y
    ) {
        canvas.drawLine(40, y - 13, 555, y - 13, linePaint);

        canvas.drawText(
                shortText(transaction.getDate(), 16),
                40,
                y,
                normalPaint
        );

        canvas.drawText(
                shortText(transaction.getType(), 12),
                155,
                y,
                normalPaint
        );

        canvas.drawText(
                shortText(
                        transaction.getCategory() +
                                " / " +
                                transaction.getAccount(),
                        28
                ),
                235,
                y,
                normalPaint
        );

        canvas.drawText(
                String.format(
                        Locale.getDefault(),
                        "₹%.2f",
                        transaction.getAmount()
                ),
                470,
                y,
                normalPaint
        );

        canvas.drawText(
                "Note: " + shortText(transaction.getNote(), 58),
                40,
                y + 15,
                normalPaint
        );
    }

    private void showExportSuccess(String message) {
        txtExportStatus.setText(message);
        setExportButtonsEnabled(true);

        Toast.makeText(
                this,
                message,
                Toast.LENGTH_LONG
        ).show();
    }

    private void showExportError() {
        txtExportStatus.setText(
                "Export नहीं हो सका। फिर से कोशिश करें।"
        );

        setExportButtonsEnabled(true);

        Toast.makeText(
                this,
                "Export failed",
                Toast.LENGTH_SHORT
        ).show();
    }

    private void setExportButtonsEnabled(boolean enabled) {
        btnExportCsv.setEnabled(enabled);
        btnExportPdf.setEnabled(enabled);
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "\"\"";
        }

        return "\"" +
                value.replace("\"", "\"\"") +
                "\"";
    }

    private String shortText(String value, int length) {
        if (value == null) {
            return "";
        }

        if (value.length() <= length) {
            return value;
        }

        return value.substring(0, length - 3) + "...";
    }

    private String getFileDate() {
        return new SimpleDateFormat(
                "yyyyMMdd_HHmm",
                Locale.getDefault()
        ).format(new Date());
    }

    private String getCurrentDateTime() {
        return new SimpleDateFormat(
                "dd MMM yyyy, hh:mm a",
                Locale.getDefault()
        ).format(new Date());
    }
}