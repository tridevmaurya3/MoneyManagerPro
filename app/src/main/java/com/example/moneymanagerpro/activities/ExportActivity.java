package com.example.moneymanagerpro.activities;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import android.app.DatePickerDialog;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.database.DatabaseClient;
import com.example.moneymanagerpro.model.Transaction;
import com.example.moneymanagerpro.utils.BubbleTouchAnimator;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Calendar;
import java.util.Comparator;

public class ExportActivity extends AppCompatActivity {

    private static final int PDF_PAGE_WIDTH = 595;
    private static final int PDF_PAGE_HEIGHT = 842;

    private static final float PDF_LEFT_MARGIN = 36f;
    private static final float PDF_RIGHT_MARGIN = 559f;
    private static final float PDF_BOTTOM_LIMIT = 785f;

    private static final float PDF_ROW_HEIGHT = 57f;

    private TextView txtExportStatus;

    private MaterialButton btnExportCsv;
    private MaterialButton btnExportPdf;
    private MaterialButton btnShareExportPdf;
    private MaterialAutoCompleteTextView dropdownExportPeriod;
    private MaterialAutoCompleteTextView dropdownExportSort;
    private MaterialAutoCompleteTextView dropdownExportType;
    private Calendar exportStart;
    private Calendar exportEnd;
    private String exportSort = "Newest first";
    private String exportType = "All data";

    private boolean exportInProgress = false;

    private final ActivityResultLauncher<Intent> csvFileLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() != RESULT_OK
                                || result.getData() == null
                                || result.getData().getData() == null) {

                            showReadyStatus(
                                    "Excel export cancelled. Choose a format whenever you are ready."
                            );

                            return;
                        }

                        Uri fileUri =
                                result.getData().getData();

                        exportCsv(fileUri);
                    }
            );

    private final ActivityResultLauncher<Intent> pdfFileLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() != RESULT_OK
                                || result.getData() == null
                                || result.getData().getData() == null) {

                            showReadyStatus(
                                    "PDF export cancelled. Choose a format whenever you are ready."
                            );

                            return;
                        }

                        Uri fileUri =
                                result.getData().getData();

                        exportPdf(fileUri);
                    }
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_export);

        bindViews();
        prepareScreen();
    }

    private void bindViews() {
        TextView btnBack =
                findViewById(R.id.btnBack);

        txtExportStatus =
                findViewById(R.id.txtExportStatus);

        btnExportCsv =
                findViewById(R.id.btnExportCsv);

        btnExportPdf =
                findViewById(R.id.btnExportPdf);
        btnShareExportPdf = findViewById(R.id.btnShareExportPdf);
        dropdownExportPeriod = findViewById(R.id.dropdownExportPeriod);
        dropdownExportSort = findViewById(R.id.dropdownExportSort);
        dropdownExportType = findViewById(R.id.dropdownExportType);

        btnBack.setOnClickListener(
                view -> finish()
        );
    }

    private void prepareScreen() {
        setupExportFilters();
        btnExportCsv.setOnClickListener(
                view -> createCsvFile()
        );

        btnExportPdf.setOnClickListener(
                view -> createPdfFile()
        );
        btnShareExportPdf.setOnClickListener(view -> shareFilteredPdf());

        BubbleTouchAnimator.apply(
                btnExportCsv
        );

        BubbleTouchAnimator.apply(
                btnExportPdf
        );
        BubbleTouchAnimator.apply(btnShareExportPdf);

        showReadyStatus(
                "Choose Excel, PDF or Share PDF for the selected period and sort."
        );
    }

    private void setupExportFilters() {
        String[] periods = {"Today", "This Week", "This Month", "Last Month", "Last Two Month", "Last Three Month", "Last Six Month", "Custom"};
        String[] sorts = {"Newest first", "Oldest first", "Amount high to low", "Amount low to high"};
        String[] types = {"All data", "Income only", "Expense only", "Transfers only"};
        dropdownExportPeriod.setSimpleItems(periods);
        dropdownExportSort.setSimpleItems(sorts);
        dropdownExportType.setSimpleItems(types);
        dropdownExportPeriod.setText("This Month", false);
        dropdownExportSort.setText("Newest first", false);
        dropdownExportType.setText("All data", false);
        setExportRange("This Month");
        dropdownExportPeriod.setOnItemClickListener((p, v, position, id) -> setExportRange(periods[position]));
        dropdownExportSort.setOnItemClickListener((p, v, position, id) -> exportSort = sorts[position]);
        dropdownExportType.setOnItemClickListener((p, v, position, id) -> exportType = types[position]);
    }

    private void setExportRange(String label) {
        Calendar now = Calendar.getInstance();
        now.set(Calendar.HOUR_OF_DAY, 0); now.set(Calendar.MINUTE, 0); now.set(Calendar.SECOND, 0); now.set(Calendar.MILLISECOND, 0);
        exportStart = (Calendar) now.clone(); exportEnd = (Calendar) now.clone();
        if ("This Week".equals(label)) exportStart.set(Calendar.DAY_OF_WEEK, exportStart.getFirstDayOfWeek());
        else if ("This Month".equals(label)) exportStart.set(Calendar.DAY_OF_MONTH, 1);
        else if ("Last Month".equals(label)) { exportStart.add(Calendar.MONTH, -1); exportStart.set(Calendar.DAY_OF_MONTH, 1); exportEnd = (Calendar) exportStart.clone(); exportEnd.set(Calendar.DAY_OF_MONTH, exportEnd.getActualMaximum(Calendar.DAY_OF_MONTH)); }
        else if ("Last Two Month".equals(label)) { exportStart.add(Calendar.MONTH, -1); exportStart.set(Calendar.DAY_OF_MONTH, 1); }
        else if ("Last Three Month".equals(label)) { exportStart.add(Calendar.MONTH, -2); exportStart.set(Calendar.DAY_OF_MONTH, 1); }
        else if ("Last Six Month".equals(label)) { exportStart.add(Calendar.MONTH, -5); exportStart.set(Calendar.DAY_OF_MONTH, 1); }
        else if ("Custom".equals(label)) { pickCustomExportRange(); return; }
        exportEnd.set(Calendar.HOUR_OF_DAY, 23); exportEnd.set(Calendar.MINUTE, 59); exportEnd.set(Calendar.SECOND, 59);
    }

    private void pickCustomExportRange() {
        Calendar now = Calendar.getInstance();
        new DatePickerDialog(this, (d, y, m, day) -> {
            exportStart = Calendar.getInstance(); exportStart.set(y, m, day, 0, 0, 0);
            new DatePickerDialog(this, (d2, y2, m2, day2) -> {
                exportEnd = Calendar.getInstance(); exportEnd.set(y2, m2, day2, 23, 59, 59);
                if (exportEnd.before(exportStart)) { Calendar swap = exportStart; exportStart = exportEnd; exportEnd = swap; }
            }, y, m, day).show();
        }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void createCsvFile() {
        if (exportInProgress) {
            showBusyMessage();
            return;
        }

        Intent intent =
                new Intent(
                        Intent.ACTION_CREATE_DOCUMENT
                );

        intent.addCategory(
                Intent.CATEGORY_OPENABLE
        );

        intent.setType(
                "application/vnd.ms-excel"
        );

        intent.putExtra(
                Intent.EXTRA_TITLE,
                "MoneyManager_Transactions_"
                        + getFileDate()
                        + ".xls"
        );

        txtExportStatus.setText(
                "Choose a folder and file name for the Excel report."
        );

        txtExportStatus.setTextColor(
                getColorValue(
                        R.color.secondary
                )
        );

        try {
            csvFileLauncher.launch(intent);

        } catch (Exception exception) {
            showExportError(
                    "Unable to open the file picker."
            );
        }
    }

    private void createPdfFile() {
        if (exportInProgress) {
            showBusyMessage();
            return;
        }

        Intent intent =
                new Intent(
                        Intent.ACTION_CREATE_DOCUMENT
                );

        intent.addCategory(
                Intent.CATEGORY_OPENABLE
        );

        intent.setType(
                "application/pdf"
        );

        intent.putExtra(
                Intent.EXTRA_TITLE,
                "MoneyManager_Report_"
                        + getFileDate()
                        + ".pdf"
        );

        txtExportStatus.setText(
                "Choose a folder and file name for the PDF report."
        );

        txtExportStatus.setTextColor(
                getColorValue(
                        R.color.secondary
                )
        );

        try {
            pdfFileLauncher.launch(intent);

        } catch (Exception exception) {
            showExportError(
                    "Unable to open the file picker."
            );
        }
    }

    private void shareFilteredPdf() {
        if (exportInProgress) { showBusyMessage(); return; }
        setExportState(true, "Creating filtered PDF for sharing...");
        new Thread(() -> {
            try {
                File directory = new File(getCacheDir(), "shared_reports");
                if (!directory.exists() && !directory.mkdirs()) throw new IOException("Unable to prepare report folder");
                File file = new File(directory, "MoneyManager_Filtered_" + getFileDate() + ".pdf");
                Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".report_files", file);
                List<Transaction> transactions = loadTransactions();
                createPdfDocument(uri, transactions, calculateSummary(transactions));
                runOnUiThread(() -> {
                    setExportState(false, transactions.size() + " filtered transactions ready to share.");
                    Intent share = new Intent(Intent.ACTION_SEND);
                    share.setType("application/pdf");
                    share.putExtra(Intent.EXTRA_STREAM, uri);
                    share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(share, "Share filtered PDF"));
                });
            } catch (Exception exception) {
                runOnUiThread(() -> showExportError("Unable to create the shared PDF."));
            }
        }).start();
    }

    private void exportCsv(
            Uri fileUri
    ) {
        if (fileUri == null) {
            showExportError(
                    "The selected Excel location is unavailable."
            );

            return;
        }

        setExportState(
                true,
                "Creating Excel-compatible report..."
        );

        new Thread(() -> {
            try {
                List<Transaction> transactions =
                        loadTransactions();

                writeCsvDocument(
                        fileUri,
                        transactions
                );

                String fileName =
                        getDisplayName(
                                fileUri,
                                "Excel report"
                        );

                runOnUiThread(() ->
                        showExportSuccess(
                                fileName
                                        + " saved successfully. "
                                        + transactions.size()
                                        + (
                                        transactions.size() == 1
                                                ? " transaction was exported."
                                                : " transactions were exported."
                                )
                        )
                );

            } catch (Exception exception) {
                deleteIncompleteDocument(
                        fileUri
                );

                runOnUiThread(() ->
                        showExportError(
                                "Excel export failed. Please choose a location and try again."
                        )
                );
            }
        }).start();
    }

    private void writeCsvDocument(
            Uri fileUri,
            List<Transaction> transactions
    ) throws Exception {
        OutputStream outputStream =
                getContentResolver()
                        .openOutputStream(
                                fileUri,
                                "w"
                        );

        if (outputStream == null) {
            throw new IOException(
                    "Unable to create the CSV file."
            );
        }

        try (
                OutputStream safeOutputStream =
                        outputStream;

                BufferedWriter writer =
                        new BufferedWriter(
                                new OutputStreamWriter(
                                        safeOutputStream,
                                        StandardCharsets.UTF_8
                                )
                        )
        ) {
            /*
             * UTF-8 BOM helps Microsoft Excel display
             * Indian currency symbols and non-English text correctly.
             */
            writer.write("\uFEFF");

            /*
             * Date is kept as the first column so this exported file
             * can also be imported through the app's CSV Import screen.
             */
            writer.write(
                    "Date,Type,Amount,Category,Account,Note,ID"
            );

            writer.newLine();

            for (Transaction transaction : transactions) {
                if (transaction == null) {
                    continue;
                }

                writer.write(
                        escapeCsv(
                                safeText(
                                        transaction.getDate(),
                                        ""
                                )
                        )
                );

                writer.write(",");

                writer.write(
                        escapeCsv(
                                safeText(
                                        transaction.getType(),
                                        ""
                                )
                        )
                );

                writer.write(",");

                writer.write(
                        String.format(
                                Locale.US,
                                "%.2f",
                                transaction.getAmount()
                        )
                );

                writer.write(",");

                writer.write(
                        escapeCsv(
                                safeText(
                                        transaction.getCategory(),
                                        ""
                                )
                        )
                );

                writer.write(",");

                writer.write(
                        escapeCsv(
                                safeText(
                                        transaction.getAccount(),
                                        ""
                                )
                        )
                );

                writer.write(",");

                writer.write(
                        escapeCsv(
                                safeText(
                                        transaction.getNote(),
                                        ""
                                )
                        )
                );

                writer.write(",");

                writer.write(
                        String.valueOf(
                                transaction.getId()
                        )
                );

                writer.newLine();
            }

            writer.flush();
        }
    }

    private void exportPdf(
            Uri fileUri
    ) {
        if (fileUri == null) {
            showExportError(
                    "The selected PDF location is unavailable."
            );

            return;
        }

        setExportState(
                true,
                "Creating formatted PDF report..."
        );

        new Thread(() -> {
            try {
                List<Transaction> transactions =
                        loadTransactions();

                ReportSummary summary =
                        calculateSummary(
                                transactions
                        );

                createPdfDocument(
                        fileUri,
                        transactions,
                        summary
                );

                String fileName =
                        getDisplayName(
                                fileUri,
                                "PDF report"
                        );

                runOnUiThread(() ->
                        showExportSuccess(
                                fileName
                                        + " saved successfully with "
                                        + transactions.size()
                                        + (
                                        transactions.size() == 1
                                                ? " transaction."
                                                : " transactions."
                                )
                        )
                );

            } catch (Exception exception) {
                deleteIncompleteDocument(
                        fileUri
                );

                runOnUiThread(() ->
                        showExportError(
                                "PDF export failed. Please choose a location and try again."
                        )
                );
            }
        }).start();
    }

    private void createPdfDocument(
            Uri fileUri,
            List<Transaction> transactions,
            ReportSummary summary
    ) throws Exception {
        PdfDocument document =
                new PdfDocument();

        PdfDocument.Page currentPage =
                null;

        try {
            PdfPaints paints =
                    createPdfPaints();

            int pageNumber = 1;

            currentPage =
                    startPdfPage(
                            document,
                            pageNumber
                    );

            Canvas canvas =
                    currentPage.getCanvas();

            float currentY =
                    drawFirstPageHeader(
                            canvas,
                            paints,
                            summary
                    );

            if (transactions.isEmpty()) {
                drawEmptyPdfState(
                        canvas,
                        paints,
                        currentY
                );

            } else {
                for (Transaction transaction : transactions) {
                    if (transaction == null) {
                        continue;
                    }

                    if (currentY + PDF_ROW_HEIGHT
                            > PDF_BOTTOM_LIMIT) {

                        drawPdfFooter(
                                canvas,
                                paints,
                                pageNumber
                        );

                        document.finishPage(
                                currentPage
                        );

                        currentPage = null;

                        pageNumber++;

                        currentPage =
                                startPdfPage(
                                        document,
                                        pageNumber
                                );

                        canvas =
                                currentPage.getCanvas();

                        currentY =
                                drawContinuationHeader(
                                        canvas,
                                        paints,
                                        pageNumber
                                );
                    }

                    currentY =
                            drawPdfTransaction(
                                    canvas,
                                    paints,
                                    transaction,
                                    currentY
                            );
                }
            }

            drawPdfFooter(
                    canvas,
                    paints,
                    pageNumber
            );

            document.finishPage(
                    currentPage
            );

            currentPage = null;

            OutputStream outputStream =
                    getContentResolver()
                            .openOutputStream(
                                    fileUri,
                                    "w"
                            );

            if (outputStream == null) {
                throw new IOException(
                        "Unable to create the PDF file."
                );
            }

            try (
                    OutputStream safeOutputStream =
                            outputStream
            ) {
                document.writeTo(
                        safeOutputStream
                );

                safeOutputStream.flush();
            }

        } finally {
            if (currentPage != null) {
                try {
                    document.finishPage(
                            currentPage
                    );

                } catch (Exception ignored) {
                    // The page may already be invalid after an export error.
                }
            }

            try {
                document.close();

            } catch (Exception ignored) {
                // Closing failure does not require a second error.
            }
        }
    }

    private PdfDocument.Page startPdfPage(
            PdfDocument document,
            int pageNumber
    ) {
        PdfDocument.PageInfo pageInfo =
                new PdfDocument.PageInfo.Builder(
                        PDF_PAGE_WIDTH,
                        PDF_PAGE_HEIGHT,
                        pageNumber
                ).create();

        PdfDocument.Page page =
                document.startPage(
                        pageInfo
                );

        page.getCanvas()
                .drawColor(
                        Color.WHITE
                );

        return page;
    }

    private PdfPaints createPdfPaints() {
        PdfPaints paints =
                new PdfPaints();

        paints.titlePaint =
                createPaint(
                        Color.rgb(
                                30,
                                41,
                                59
                        ),
                        22f,
                        true
                );

        paints.subtitlePaint =
                createPaint(
                        Color.rgb(
                                100,
                                116,
                                139
                        ),
                        9.5f,
                        false
                );

        paints.sectionPaint =
                createPaint(
                        Color.rgb(
                                30,
                                64,
                                175
                        ),
                        11f,
                        true
                );

        paints.headerPaint =
                createPaint(
                        Color.rgb(
                                71,
                                85,
                                105
                        ),
                        8.5f,
                        true
                );

        paints.normalPaint =
                createPaint(
                        Color.rgb(
                                51,
                                65,
                                85
                        ),
                        8.5f,
                        false
                );

        paints.boldPaint =
                createPaint(
                        Color.rgb(
                                30,
                                41,
                                59
                        ),
                        9f,
                        true
                );

        paints.incomePaint =
                createPaint(
                        Color.rgb(
                                22,
                                101,
                                52
                        ),
                        9f,
                        true
                );

        paints.expensePaint =
                createPaint(
                        Color.rgb(
                                185,
                                28,
                                28
                        ),
                        9f,
                        true
                );

        paints.transferPaint =
                createPaint(
                        Color.rgb(
                                109,
                                40,
                                217
                        ),
                        9f,
                        true
                );

        paints.footerPaint =
                createPaint(
                        Color.rgb(
                                100,
                                116,
                                139
                        ),
                        8f,
                        false
                );

        paints.linePaint =
                new Paint(
                        Paint.ANTI_ALIAS_FLAG
                );

        paints.linePaint.setColor(
                Color.rgb(
                        226,
                        232,
                        240
                )
        );

        paints.linePaint.setStrokeWidth(
                1f
        );

        paints.cardPaint =
                new Paint(
                        Paint.ANTI_ALIAS_FLAG
                );

        paints.cardPaint.setStyle(
                Paint.Style.FILL
        );

        return paints;
    }

    private Paint createPaint(
            int color,
            float textSize,
            boolean bold
    ) {
        Paint paint =
                new Paint(
                        Paint.ANTI_ALIAS_FLAG
                );

        paint.setColor(color);
        paint.setTextSize(textSize);

        paint.setTypeface(
                bold
                        ? Typeface.create(
                        Typeface.DEFAULT,
                        Typeface.BOLD
                )
                        : Typeface.create(
                        Typeface.DEFAULT,
                        Typeface.NORMAL
                )
        );

        return paint;
    }

    private float drawFirstPageHeader(
            Canvas canvas,
            PdfPaints paints,
            ReportSummary summary
    ) {
        canvas.drawText(
                "Money Manager Pro",
                PDF_LEFT_MARGIN,
                42f,
                paints.titlePaint
        );

        canvas.drawText(
                "Complete Transaction Report",
                PDF_LEFT_MARGIN,
                59f,
                paints.sectionPaint
        );

        canvas.drawText(
                "Generated on "
                        + getCurrentDateTime(),
                PDF_LEFT_MARGIN,
                74f,
                paints.subtitlePaint
        );

        drawSummaryCards(
                canvas,
                paints,
                summary
        );

        canvas.drawText(
                "Transaction Details",
                PDF_LEFT_MARGIN,
                175f,
                paints.sectionPaint
        );

        drawPdfTableHeader(
                canvas,
                paints,
                188f
        );

        return 216f;
    }

    private float drawContinuationHeader(
            Canvas canvas,
            PdfPaints paints,
            int pageNumber
    ) {
        canvas.drawText(
                "Money Manager Pro",
                PDF_LEFT_MARGIN,
                39f,
                paints.titlePaint
        );

        canvas.drawText(
                "Transaction Report • Continued on page "
                        + pageNumber,
                PDF_LEFT_MARGIN,
                57f,
                paints.subtitlePaint
        );

        drawPdfTableHeader(
                canvas,
                paints,
                74f
        );

        return 102f;
    }

    private void drawSummaryCards(
            Canvas canvas,
            PdfPaints paints,
            ReportSummary summary
    ) {
        float top = 91f;
        float bottom = 153f;

        float gap = 8f;
        float availableWidth =
                PDF_RIGHT_MARGIN
                        - PDF_LEFT_MARGIN;

        float cardWidth =
                (
                        availableWidth
                                - gap * 2
                ) / 3f;

        drawPdfSummaryCard(
                canvas,
                paints,
                PDF_LEFT_MARGIN,
                top,
                PDF_LEFT_MARGIN + cardWidth,
                bottom,
                "Total Income",
                formatPdfMoney(
                        summary.income
                ),
                Color.rgb(
                        22,
                        101,
                        52
                ),
                Color.rgb(
                        240,
                        253,
                        244
                )
        );

        drawPdfSummaryCard(
                canvas,
                paints,
                PDF_LEFT_MARGIN
                        + cardWidth
                        + gap,
                top,
                PDF_LEFT_MARGIN
                        + cardWidth * 2
                        + gap,
                bottom,
                "Total Expense",
                formatPdfMoney(
                        summary.expense
                ),
                Color.rgb(
                        185,
                        28,
                        28
                ),
                Color.rgb(
                        254,
                        242,
                        242
                )
        );

        int netColor =
                summary.net >= 0
                        ? Color.rgb(
                        22,
                        101,
                        52
                )
                        : Color.rgb(
                        185,
                        28,
                        28
                );

        int netSurface =
                summary.net >= 0
                        ? Color.rgb(
                        240,
                        253,
                        244
                )
                        : Color.rgb(
                        254,
                        242,
                        242
                );

        drawPdfSummaryCard(
                canvas,
                paints,
                PDF_LEFT_MARGIN
                        + cardWidth * 2
                        + gap * 2,
                top,
                PDF_RIGHT_MARGIN,
                bottom,
                "Net Cash Flow",
                formatPdfSignedMoney(
                        summary.net
                ),
                netColor,
                netSurface
        );
    }

    private void drawPdfSummaryCard(
            Canvas canvas,
            PdfPaints paints,
            float left,
            float top,
            float right,
            float bottom,
            String label,
            String value,
            int accentColor,
            int surfaceColor
    ) {
        paints.cardPaint.setColor(
                surfaceColor
        );

        RectF cardRect =
                new RectF(
                        left,
                        top,
                        right,
                        bottom
                );

        canvas.drawRoundRect(
                cardRect,
                9f,
                9f,
                paints.cardPaint
        );

        Paint labelPaint =
                createPaint(
                        Color.rgb(
                                100,
                                116,
                                139
                        ),
                        8f,
                        true
                );

        Paint valuePaint =
                createPaint(
                        accentColor,
                        12f,
                        true
                );

        canvas.drawText(
                label,
                left + 10f,
                top + 20f,
                labelPaint
        );

        String fittedValue =
                fitTextToWidth(
                        valuePaint,
                        value,
                        right - left - 20f
                );

        canvas.drawText(
                fittedValue,
                left + 10f,
                top + 43f,
                valuePaint
        );
    }

    private void drawPdfTableHeader(
            Canvas canvas,
            PdfPaints paints,
            float top
    ) {
        paints.cardPaint.setColor(
                Color.rgb(
                        241,
                        245,
                        249
                )
        );

        RectF headerRect =
                new RectF(
                        PDF_LEFT_MARGIN,
                        top,
                        PDF_RIGHT_MARGIN,
                        top + 22f
                );

        canvas.drawRoundRect(
                headerRect,
                5f,
                5f,
                paints.cardPaint
        );

        float textY =
                top + 15f;

        canvas.drawText(
                "Date",
                42f,
                textY,
                paints.headerPaint
        );

        canvas.drawText(
                "Type",
                131f,
                textY,
                paints.headerPaint
        );

        canvas.drawText(
                "Category / Account",
                207f,
                textY,
                paints.headerPaint
        );

        canvas.drawText(
                "Amount",
                488f,
                textY,
                paints.headerPaint
        );
    }

    private float drawPdfTransaction(
            Canvas canvas,
            PdfPaints paints,
            Transaction transaction,
            float top
    ) {
        String type =
                safeText(
                        transaction.getType(),
                        "OTHER"
                )
                        .toUpperCase(
                                Locale.US
                        );

        if (((int) (
                (
                        top - 216f
                ) / PDF_ROW_HEIGHT
        )) % 2 != 0) {

            paints.cardPaint.setColor(
                    Color.rgb(
                            248,
                            250,
                            252
                    )
            );

            canvas.drawRect(
                    PDF_LEFT_MARGIN,
                    top - 4f,
                    PDF_RIGHT_MARGIN,
                    top + PDF_ROW_HEIGHT - 5f,
                    paints.cardPaint
            );
        }

        float firstLineY =
                top + 11f;

        float secondLineY =
                top + 28f;

        float thirdLineY =
                top + 44f;

        canvas.drawText(
                fitTextToWidth(
                        paints.normalPaint,
                        visiblePdfDate(
                                transaction.getDate()
                        ),
                        80f
                ),
                42f,
                firstLineY,
                paints.normalPaint
        );

        Paint typePaint =
                getPdfTypePaint(
                        paints,
                        type
                );

        canvas.drawText(
                fitTextToWidth(
                        typePaint,
                        getVisibleType(type),
                        69f
                ),
                131f,
                firstLineY,
                typePaint
        );

        String categoryAccount =
                safeText(
                        transaction.getCategory(),
                        "Other"
                )
                        + " / "
                        + safeText(
                        transaction.getAccount(),
                        "Cash"
                );

        canvas.drawText(
                fitTextToWidth(
                        paints.boldPaint,
                        categoryAccount,
                        257f
                ),
                207f,
                firstLineY,
                paints.boldPaint
        );

        String amountText =
                formatPdfMoney(
                        transaction.getAmount()
                );

        Paint amountPaint =
                getPdfTypePaint(
                        paints,
                        type
                );

        amountPaint.setTextAlign(
                Paint.Align.RIGHT
        );

        canvas.drawText(
                amountText,
                PDF_RIGHT_MARGIN - 4f,
                firstLineY,
                amountPaint
        );

        amountPaint.setTextAlign(
                Paint.Align.LEFT
        );

        canvas.drawText(
                "Stored date: "
                        + fitTextToWidth(
                        paints.normalPaint,
                        safeText(
                                transaction.getDate(),
                                "Not available"
                        ),
                        450f
                ),
                42f,
                secondLineY,
                paints.normalPaint
        );

        String note =
                safeText(
                        transaction.getNote(),
                        "No note"
                );

        canvas.drawText(
                "Note: "
                        + fitTextToWidth(
                        paints.normalPaint,
                        note,
                        475f
                ),
                42f,
                thirdLineY,
                paints.normalPaint
        );

        canvas.drawLine(
                PDF_LEFT_MARGIN,
                top + PDF_ROW_HEIGHT - 5f,
                PDF_RIGHT_MARGIN,
                top + PDF_ROW_HEIGHT - 5f,
                paints.linePaint
        );

        return top + PDF_ROW_HEIGHT;
    }

    private Paint getPdfTypePaint(
            PdfPaints paints,
            String type
    ) {
        if ("INCOME".equals(type)) {
            return paints.incomePaint;
        }

        if ("EXPENSE".equals(type)) {
            return paints.expensePaint;
        }

        return paints.transferPaint;
    }

    private String getVisibleType(
            String type
    ) {
        if ("TRANSFER_IN".equals(type)) {
            return "TRANSFER IN";
        }

        if ("TRANSFER_OUT".equals(type)) {
            return "TRANSFER OUT";
        }

        return type;
    }

    private void drawEmptyPdfState(
            Canvas canvas,
            PdfPaints paints,
            float currentY
    ) {
        paints.cardPaint.setColor(
                Color.rgb(
                        248,
                        250,
                        252
                )
        );

        RectF emptyRect =
                new RectF(
                        PDF_LEFT_MARGIN,
                        currentY,
                        PDF_RIGHT_MARGIN,
                        currentY + 92f
                );

        canvas.drawRoundRect(
                emptyRect,
                10f,
                10f,
                paints.cardPaint
        );

        Paint emptyTitle =
                createPaint(
                        Color.rgb(
                                51,
                                65,
                                85
                        ),
                        13f,
                        true
                );

        canvas.drawText(
                "No transactions available",
                PDF_LEFT_MARGIN + 18f,
                currentY + 35f,
                emptyTitle
        );

        canvas.drawText(
                "Add income or expense transactions and export the report again.",
                PDF_LEFT_MARGIN + 18f,
                currentY + 57f,
                paints.subtitlePaint
        );
    }

    private void drawPdfFooter(
            Canvas canvas,
            PdfPaints paints,
            int pageNumber
    ) {
        canvas.drawLine(
                PDF_LEFT_MARGIN,
                807f,
                PDF_RIGHT_MARGIN,
                807f,
                paints.linePaint
        );

        canvas.drawText(
                "Money Manager Pro • Private financial report",
                PDF_LEFT_MARGIN,
                824f,
                paints.footerPaint
        );

        paints.footerPaint.setTextAlign(
                Paint.Align.RIGHT
        );

        canvas.drawText(
                "Page "
                        + pageNumber,
                PDF_RIGHT_MARGIN,
                824f,
                paints.footerPaint
        );

        paints.footerPaint.setTextAlign(
                Paint.Align.LEFT
        );
    }

    private List<Transaction> loadTransactions() {
        List<Transaction> transactions =
                DatabaseClient
                        .getInstance(
                                getApplicationContext()
                        )
                        .getAppDatabase()
                        .transactionDao()
                        .getAllTransactions();

        if (transactions == null) {
            return new ArrayList<>();
        }

        List<Transaction> filtered = new ArrayList<>();
        for (Transaction transaction : transactions) {
            if (transaction == null) continue;
            Date date = parseStoredDate(transaction.getDate());
            if (date != null && exportStart != null && (date.before(exportStart.getTime()) || date.after(exportEnd.getTime()))) continue;
            String type = safeText(transaction.getType(), "");
            if ("Income only".equals(exportType) && !"INCOME".equalsIgnoreCase(type)) continue;
            if ("Expense only".equals(exportType) && !"EXPENSE".equalsIgnoreCase(type)) continue;
            if ("Transfers only".equals(exportType) && !type.toUpperCase(Locale.ROOT).startsWith("TRANSFER")) continue;
            filtered.add(transaction);
        }
        Comparator<Transaction> byDate = Comparator.comparing(t -> {
            Date d = parseStoredDate(t.getDate()); return d == null ? new Date(0) : d;
        });
        if ("Oldest first".equals(exportSort)) filtered.sort(byDate);
        else if ("Amount high to low".equals(exportSort)) filtered.sort(Comparator.comparingDouble(Transaction::getAmount).reversed());
        else if ("Amount low to high".equals(exportSort)) filtered.sort(Comparator.comparingDouble(Transaction::getAmount));
        else filtered.sort(byDate.reversed());
        return filtered;
    }

    private ReportSummary calculateSummary(
            List<Transaction> transactions
    ) {
        ReportSummary summary =
                new ReportSummary();

        if (transactions == null) {
            return summary;
        }

        for (Transaction transaction : transactions) {
            if (transaction == null) {
                continue;
            }

            double amount =
                    transaction.getAmount();

            if (Double.isNaN(amount)
                    || Double.isInfinite(amount)) {

                continue;
            }

            String type =
                    safeText(
                            transaction.getType(),
                            ""
                    );

            if ("INCOME".equalsIgnoreCase(type)) {
                summary.income += amount;

            } else if ("EXPENSE".equalsIgnoreCase(type)) {
                summary.expense += amount;

            } else if ("TRANSFER_IN".equalsIgnoreCase(type)) {
                summary.transferIn += amount;

            } else if ("TRANSFER_OUT".equalsIgnoreCase(type)) {
                summary.transferOut += amount;
            }
        }

        summary.net =
                summary.income
                        - summary.expense;

        return summary;
    }

    private void setExportState(
            boolean exporting,
            String statusMessage
    ) {
        exportInProgress =
                exporting;

        btnExportCsv.setEnabled(
                !exporting
        );

        btnExportPdf.setEnabled(
                !exporting
        );

        btnExportCsv.setAlpha(
                exporting
                        ? 0.55f
                        : 1f
        );

        btnExportPdf.setAlpha(
                exporting
                        ? 0.55f
                        : 1f
        );

        btnExportCsv.setText(
                exporting
                        ? "Export in Progress..."
                        : "Export Excel File"
        );

        btnExportPdf.setText(
                exporting
                        ? "Export in Progress..."
                        : "Export PDF Report"
        );

        txtExportStatus.setText(
                statusMessage
        );

        txtExportStatus.setTextColor(
                exporting
                        ? getColorValue(
                        R.color.secondary
                )
                        : getColorValue(
                        R.color.app_text_secondary
                )
        );
    }

    private void showExportSuccess(
            String message
    ) {
        setExportState(
                false,
                message
        );

        txtExportStatus.setTextColor(
                getColorValue(
                        R.color.success
                )
        );

        Toast.makeText(
                this,
                message,
                Toast.LENGTH_LONG
        ).show();
    }

    private void showExportError(
            String message
    ) {
        setExportState(
                false,
                message
        );

        txtExportStatus.setTextColor(
                getColorValue(
                        R.color.expense
                )
        );

        Toast.makeText(
                this,
                "Export failed",
                Toast.LENGTH_SHORT
        ).show();
    }

    private void showReadyStatus(
            String message
    ) {
        setExportState(
                false,
                message
        );
    }

    private void showBusyMessage() {
        Toast.makeText(
                this,
                "Please wait for the current export to finish",
                Toast.LENGTH_SHORT
        ).show();
    }

    private void deleteIncompleteDocument(
            Uri fileUri
    ) {
        if (fileUri == null) {
            return;
        }

        try {
            getContentResolver()
                    .delete(
                            fileUri,
                            null,
                            null
                    );

        } catch (Exception ignored) {
            // Some document providers do not permit deletion here.
        }
    }

    private String getDisplayName(
            Uri uri,
            String fallback
    ) {
        if (uri == null) {
            return fallback;
        }

        Cursor cursor =
                null;

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

                int nameColumn =
                        cursor.getColumnIndex(
                                OpenableColumns.DISPLAY_NAME
                        );

                if (nameColumn >= 0) {
                    return safeText(
                            cursor.getString(
                                    nameColumn
                            ),
                            fallback
                    );
                }
            }

        } catch (Exception ignored) {
            // Fallback text will be returned.

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return fallback;
    }

    private String escapeCsv(
            String value
    ) {
        String safeValue =
                safeText(
                        value,
                        ""
                );

        return "\""
                + safeValue.replace(
                "\"",
                "\"\""
        )
                + "\"";
    }

    private String visiblePdfDate(
            String storedDate
    ) {
        Date parsedDate =
                parseStoredDate(
                        storedDate
                );

        if (parsedDate == null) {
            String fallback =
                    safeText(
                            storedDate,
                            "No date"
                    );

            return fallback.length() > 10
                    ? fallback.substring(
                    0,
                    10
            )
                    : fallback;
        }

        return new SimpleDateFormat(
                "dd MMM yyyy",
                Locale.ENGLISH
        ).format(parsedDate);
    }

    private Date parseStoredDate(
            String value
    ) {
        if (value == null
                || value.trim().isEmpty()) {

            return null;
        }

        String cleanValue =
                value.trim();

        String[] patterns = {
                "yyyy-MM-dd HH:mm:ss.SSS",
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd HH:mm",
                "yyyy-MM-dd'T'HH:mm:ss.SSS",
                "yyyy-MM-dd'T'HH:mm:ss",
                "yyyy-MM-dd'T'HH:mm",
                "yyyy-MM-dd",
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
            try {
                SimpleDateFormat dateFormat =
                        new SimpleDateFormat(
                                pattern,
                                Locale.ENGLISH
                        );

                dateFormat.setLenient(false);

                ParsePosition parsePosition =
                        new ParsePosition(0);

                Date parsedDate =
                        dateFormat.parse(
                                cleanValue,
                                parsePosition
                        );

                if (parsedDate != null
                        && parsePosition.getIndex()
                        == cleanValue.length()) {

                    return parsedDate;
                }

            } catch (Exception ignored) {
                // Try the next supported format.
            }
        }

        return null;
    }

    private String fitTextToWidth(
            Paint paint,
            String value,
            float maximumWidth
    ) {
        String text =
                safeText(
                        value,
                        ""
                );

        if (paint.measureText(text)
                <= maximumWidth) {

            return text;
        }

        String ellipsis =
                "...";

        float ellipsisWidth =
                paint.measureText(
                        ellipsis
                );

        if (ellipsisWidth
                >= maximumWidth) {

            return "";
        }

        int endIndex =
                text.length();

        while (endIndex > 0) {
            String candidate =
                    text.substring(
                            0,
                            endIndex
                    )
                            + ellipsis;

            if (paint.measureText(candidate)
                    <= maximumWidth) {

                return candidate;
            }

            endIndex--;
        }

        return ellipsis;
    }

    private String formatPdfMoney(
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

        return "Rs. "
                + formatter.format(
                amount
        );
    }

    private String formatPdfSignedMoney(
            double amount
    ) {
        if (amount > 0) {
            return "+"
                    + formatPdfMoney(amount);
        }

        if (amount < 0) {
            return "-"
                    + formatPdfMoney(
                    Math.abs(amount)
            );
        }

        return formatPdfMoney(0);
    }

    private String getFileDate() {
        return new SimpleDateFormat(
                "yyyyMMdd_HHmm",
                Locale.US
        ).format(
                new Date()
        );
    }

    private String getCurrentDateTime() {
        return new SimpleDateFormat(
                "dd MMM yyyy, hh:mm a",
                Locale.ENGLISH
        ).format(
                new Date()
        );
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

    private static class ReportSummary {

        private double income;
        private double expense;

        private double transferIn;
        private double transferOut;

        private double net;
    }

    private static class PdfPaints {

        private Paint titlePaint;
        private Paint subtitlePaint;
        private Paint sectionPaint;

        private Paint headerPaint;
        private Paint normalPaint;
        private Paint boldPaint;

        private Paint incomePaint;
        private Paint expensePaint;
        private Paint transferPaint;

        private Paint footerPaint;

        private Paint linePaint;
        private Paint cardPaint;
    }
}
