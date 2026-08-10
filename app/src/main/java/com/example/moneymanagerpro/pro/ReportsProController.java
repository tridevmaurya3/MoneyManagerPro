package com.example.moneymanagerpro.pro;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;

import com.example.moneymanagerpro.database.DatabaseClient;
import com.example.moneymanagerpro.model.Transaction;
import com.example.moneymanagerpro.utils.BubbleTouchAnimator;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Adds A4 PDF, real XLSX, sharing and custom-range statements to ReportActivity. */
public final class ReportsProController {

    private static final String PANEL_TAG = "reports_pro_panel";
    private static final int PDF_WIDTH = 595;
    private static final int PDF_HEIGHT = 842;
    private static final float PDF_LEFT = 34f;
    private static final float PDF_RIGHT = 561f;
    private static final float PDF_BOTTOM = 805f;

    private static final String[] DATE_PATTERNS = {
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "yyyy-MM-dd",
            "dd-MM-yyyy HH:mm:ss",
            "dd-MM-yyyy HH:mm",
            "dd-MM-yyyy",
            "dd/MM/yyyy HH:mm:ss",
            "dd/MM/yyyy HH:mm",
            "dd/MM/yyyy",
            "MMM dd, yyyy HH:mm",
            "MMM dd, yyyy"
    };

    private final Activity activity;
    private final Calendar start = Calendar.getInstance();
    private final Calendar end = Calendar.getInstance();

    private LinearLayout panel;
    private MaterialButton btnStart;
    private MaterialButton btnEnd;
    private TextView txtPreview;
    private TextView txtStatus;
    private TextView chipRange;

    private List<Transaction> currentTransactions = new ArrayList<>();
    private ReportSummary currentSummary = new ReportSummary();
    private File lastGeneratedFile;
    private String lastGeneratedMime = "application/pdf";
    private int requestVersion;

    public ReportsProController(@NonNull Activity activity) {
        this.activity = activity;
        setCurrentMonth();
    }

    public void attach() {
        if (activity.isFinishing() || activity.isDestroyed()) return;

        ViewGroup content = activity.findViewById(android.R.id.content);
        LinearLayout root = findMainVertical(content);
        if (root == null) return;

        View existing = root.findViewWithTag(PANEL_TAG);
        if (existing instanceof LinearLayout) {
            panel = (LinearLayout) existing;
            refreshPreview();
            return;
        }

        panel = new LinearLayout(activity);
        panel.setTag(PANEL_TAG);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(0, 0, 0, dp(8));
        LinearLayout.LayoutParams panelParams = new LinearLayout.LayoutParams(-1, -2);
        panelParams.setMargins(0, dp(22), 0, dp(8));
        panel.setLayoutParams(panelParams);

        panel.addView(text("Reports Pro", 19, "#17351F", true));
        TextView subtitle = text(
                "Custom date range • monthly statement • A4 PDF • Excel • Share",
                10,
                "#667085",
                false
        );
        setMargins(subtitle, 0, 3, 0, 10);
        panel.addView(subtitle);

        LinearLayout chips = horizontal();
        chipRange = chip("Current month", "#EEF5FF", "#0F6CBD");
        chips.addView(chipRange);
        chips.addView(chip("A4 PDF", "#FFF2F0", "#C42B1C"));
        chips.addView(chip("XLSX Excel", "#EFF9F1", "#107C41"));
        panel.addView(chips);

        MaterialCardView rangeCard = card("#F7F9FC", "#D8E0E8");
        LinearLayout rangeContent = verticalPadding(13);
        rangeContent.addView(text("Custom Date Range", 14, "#17351F", true));

        LinearLayout dateRow = horizontal();
        setMargins(dateRow, 0, 9, 0, 0);
        btnStart = button("Start", false);
        btnEnd = button("End", false);
        btnStart.setOnClickListener(v -> pickDate(true));
        btnEnd.setOnClickListener(v -> pickDate(false));
        BubbleTouchAnimator.apply(btnStart);
        BubbleTouchAnimator.apply(btnEnd);
        dateRow.addView(btnStart);
        dateRow.addView(btnEnd);
        rangeContent.addView(dateRow);

        MaterialButton month = button("Use Current Month Statement", true);
        setMargins(month, 0, 8, 0, 0);
        month.setOnClickListener(v -> {
            setCurrentMonth();
            updateDateButtons();
            chipRange.setText("Current month");
            refreshPreview();
        });
        BubbleTouchAnimator.apply(month);
        rangeContent.addView(month);
        rangeCard.addView(rangeContent);
        panel.addView(rangeCard);

        MaterialCardView previewCard = card("#FFF9EC", "#E9D7A8");
        LinearLayout previewContent = verticalPadding(13);
        previewContent.addView(text("Statement Preview", 14, "#17351F", true));
        txtPreview = text("Preparing financial statement…", 11, "#475467", false);
        txtPreview.setLineSpacing(dp(3), 1f);
        setMargins(txtPreview, 0, 7, 0, 0);
        previewContent.addView(txtPreview);
        previewCard.addView(previewContent);
        setMargins(previewCard, 0, 9, 0, 0);
        panel.addView(previewCard);

        txtStatus = text(
                "Choose PDF, Excel or Share after the preview is ready.",
                10,
                "#667085",
                false
        );
        setMargins(txtStatus, 0, 8, 0, 7);
        panel.addView(txtStatus);

        LinearLayout exportActions = horizontalOrVertical();
        MaterialButton pdf = button("PDF", true);
        MaterialButton excel = button("Excel", false);
        MaterialButton share = button("Share", false);
        pdf.setOnClickListener(v -> generatePdfAndShare());
        excel.setOnClickListener(v -> generateExcelAndShare());
        share.setOnClickListener(v -> shareLatestOrCreatePdf());
        BubbleTouchAnimator.apply(pdf);
        BubbleTouchAnimator.apply(excel);
        BubbleTouchAnimator.apply(share);
        exportActions.addView(pdf);
        exportActions.addView(excel);
        exportActions.addView(share);
        panel.addView(exportActions);

        TextView privacy = text(
                "Reports are generated locally from the selected range. Files are placed in app cache only for sharing and can be recreated at any time.",
                9,
                "#667085",
                false
        );
        privacy.setLineSpacing(dp(2), 1f);
        setMargins(privacy, 0, 9, 0, 0);
        panel.addView(privacy);

        root.addView(panel);
        updateDateButtons();
        refreshPreview();
    }

    private void pickDate(boolean startDate) {
        Calendar target = startDate ? start : end;
        DatePickerDialog dialog = new DatePickerDialog(
                activity,
                (view, year, month, dayOfMonth) -> {
                    target.set(Calendar.YEAR, year);
                    target.set(Calendar.MONTH, month);
                    target.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                    if (startDate) {
                        setStartOfDay(start);
                        if (start.after(end)) {
                            end.setTimeInMillis(start.getTimeInMillis());
                            setEndOfDay(end);
                        }
                    } else {
                        setEndOfDay(end);
                        if (end.before(start)) {
                            start.setTimeInMillis(end.getTimeInMillis());
                            setStartOfDay(start);
                        }
                    }
                    updateDateButtons();
                    chipRange.setText("Custom range");
                    refreshPreview();
                },
                target.get(Calendar.YEAR),
                target.get(Calendar.MONTH),
                target.get(Calendar.DAY_OF_MONTH)
        );
        dialog.show();
    }

    private void refreshPreview() {
        final int version = ++requestVersion;
        if (txtPreview != null) txtPreview.setText("Calculating selected range…");

        long startMillis = start.getTimeInMillis();
        long endMillis = end.getTimeInMillis();

        new Thread(() -> {
            List<Transaction> all = DatabaseClient
                    .getInstance(activity.getApplicationContext())
                    .getAppDatabase()
                    .transactionDao()
                    .getAllTransactions();

            List<Transaction> filtered = new ArrayList<>();
            ReportSummary summary = new ReportSummary();

            if (all != null) {
                for (Transaction transaction : all) {
                    if (transaction == null) continue;
                    Date date = parseDate(transaction.getDate());
                    if (date == null) continue;
                    long time = date.getTime();
                    if (time < startMillis || time > endMillis) continue;

                    filtered.add(transaction);
                    double amount = Math.abs(transaction.getAmount());
                    if ("INCOME".equalsIgnoreCase(safe(transaction.getType()))) {
                        summary.income += amount;
                        summary.incomeCount++;
                    } else if ("EXPENSE".equalsIgnoreCase(safe(transaction.getType()))) {
                        summary.expense += amount;
                        summary.expenseCount++;
                    }
                }
            }

            filtered.sort((first, second) -> Long.compare(
                    timeOf(second.getDate()),
                    timeOf(first.getDate())
            ));
            summary.net = summary.income - summary.expense;
            summary.totalCount = filtered.size();

            activity.runOnUiThread(() -> {
                if (version != requestVersion || activity.isFinishing() || activity.isDestroyed()) return;
                currentTransactions = filtered;
                currentSummary = summary;
                renderPreview();
            });
        }).start();
    }

    private void renderPreview() {
        String period = dateLabel(start) + " → " + dateLabel(end);
        String text = "Period  " + period
                + "\nIncome  " + money(currentSummary.income)
                + "\nExpense  " + money(currentSummary.expense)
                + "\nNet Balance  " + signedMoney(currentSummary.net)
                + "\nTransactions  " + currentSummary.totalCount
                + "  (" + currentSummary.incomeCount + " income • "
                + currentSummary.expenseCount + " expense)";
        txtPreview.setText(text);
        txtPreview.setTextColor(Color.parseColor(currentSummary.net >= 0d ? "#107C41" : "#C42B1C"));
        txtStatus.setText("Statement ready • " + currentSummary.totalCount + " matching transaction(s)");
        txtStatus.setTextColor(Color.parseColor("#0F6CBD"));
    }

    private void generatePdfAndShare() {
        snapshotAndGenerate(true);
    }

    private void generateExcelAndShare() {
        snapshotAndGenerate(false);
    }

    private void snapshotAndGenerate(boolean pdf) {
        List<Transaction> snapshot = new ArrayList<>(currentTransactions);
        ReportSummary summary = currentSummary.copy();
        long startMillis = start.getTimeInMillis();
        long endMillis = end.getTimeInMillis();
        txtStatus.setText(pdf ? "Creating A4 PDF…" : "Creating Excel workbook…");

        new Thread(() -> {
            try {
                File file = pdf
                        ? createPdf(snapshot, summary, startMillis, endMillis)
                        : createXlsx(snapshot, summary, startMillis, endMillis);
                String mime = pdf
                        ? "application/pdf"
                        : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

                activity.runOnUiThread(() -> {
                    if (activity.isFinishing() || activity.isDestroyed()) return;
                    lastGeneratedFile = file;
                    lastGeneratedMime = mime;
                    txtStatus.setText((pdf ? "PDF" : "Excel") + " ready • opening Share");
                    txtStatus.setTextColor(Color.parseColor("#107C41"));
                    shareFile(file, mime);
                });
            } catch (Exception exception) {
                activity.runOnUiThread(() -> {
                    if (activity.isFinishing() || activity.isDestroyed()) return;
                    txtStatus.setText(useful(exception, "Report file could not be created."));
                    txtStatus.setTextColor(Color.parseColor("#C42B1C"));
                });
            }
        }).start();
    }

    private void shareLatestOrCreatePdf() {
        if (lastGeneratedFile != null && lastGeneratedFile.exists()) {
            shareFile(lastGeneratedFile, lastGeneratedMime);
        } else {
            generatePdfAndShare();
        }
    }

    @NonNull
    private File createPdf(
            @NonNull List<Transaction> transactions,
            @NonNull ReportSummary summary,
            long startMillis,
            long endMillis
    ) throws Exception {
        File directory = reportDirectory();
        File file = new File(directory, "MoneyManager_Statement_" + fileStamp() + ".pdf");

        PdfDocument document = new PdfDocument();
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        int pageNumber = 1;
        PdfDocument.Page page = document.startPage(
                new PdfDocument.PageInfo.Builder(PDF_WIDTH, PDF_HEIGHT, pageNumber).create()
        );
        Canvas canvas = page.getCanvas();
        float y = drawPdfHeader(canvas, paint, summary, startMillis, endMillis, pageNumber);

        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setTextSize(9f);
        paint.setColor(Color.rgb(52, 64, 84));
        drawPdfColumns(canvas, paint, y);
        y += 18f;

        paint.setTypeface(Typeface.DEFAULT);
        paint.setTextSize(8.2f);

        SimpleDateFormat display = new SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH);

        for (Transaction transaction : transactions) {
            if (y > PDF_BOTTOM - 30f) {
                document.finishPage(page);
                pageNumber++;
                page = document.startPage(
                        new PdfDocument.PageInfo.Builder(PDF_WIDTH, PDF_HEIGHT, pageNumber).create()
                );
                canvas = page.getCanvas();
                y = drawPdfContinuationHeader(canvas, paint, pageNumber);
                paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
                paint.setTextSize(9f);
                drawPdfColumns(canvas, paint, y);
                y += 18f;
                paint.setTypeface(Typeface.DEFAULT);
                paint.setTextSize(8.2f);
            }

            Date parsed = parseDate(transaction.getDate());
            String date = parsed == null ? safe(transaction.getDate()) : display.format(parsed);
            String type = safe(transaction.getType());
            String category = trimTo(safe(transaction.getCategory()), 22);
            String account = trimTo(safe(transaction.getAccount()), 18);
            String amount = ("EXPENSE".equalsIgnoreCase(type) ? "-" : "+")
                    + plainMoney(transaction.getAmount());

            paint.setColor(Color.rgb(71, 84, 103));
            canvas.drawText(date, 34f, y, paint);
            canvas.drawText(trimTo(type, 9), 106f, y, paint);
            canvas.drawText(category, 158f, y, paint);
            canvas.drawText(account, 315f, y, paint);
            paint.setColor("EXPENSE".equalsIgnoreCase(type)
                    ? Color.rgb(196, 43, 28)
                    : Color.rgb(16, 124, 65));
            canvas.drawText(amount, 455f, y, paint);
            paint.setColor(Color.rgb(228, 234, 240));
            canvas.drawLine(PDF_LEFT, y + 6f, PDF_RIGHT, y + 6f, paint);
            y += 22f;
        }

        if (transactions.isEmpty()) {
            paint.setColor(Color.rgb(102, 112, 133));
            paint.setTextSize(10f);
            canvas.drawText("No transactions found for the selected date range.", PDF_LEFT, y + 15f, paint);
        }

        document.finishPage(page);
        try (FileOutputStream output = new FileOutputStream(file)) {
            document.writeTo(output);
        } finally {
            document.close();
        }
        return file;
    }

    private float drawPdfHeader(
            Canvas canvas,
            Paint paint,
            ReportSummary summary,
            long startMillis,
            long endMillis,
            int pageNumber
    ) {
        paint.setColor(Color.rgb(23, 53, 31));
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setTextSize(20f);
        canvas.drawText("Money Manager Pro", PDF_LEFT, 48f, paint);

        paint.setTextSize(12f);
        paint.setColor(Color.rgb(15, 108, 189));
        canvas.drawText("Monthly / Custom Financial Statement", PDF_LEFT, 69f, paint);

        paint.setTypeface(Typeface.DEFAULT);
        paint.setTextSize(9f);
        paint.setColor(Color.rgb(102, 112, 133));
        canvas.drawText(
                formatDateMillis(startMillis) + " to " + formatDateMillis(endMillis)
                        + "   |   Page " + pageNumber,
                PDF_LEFT,
                87f,
                paint
        );

        float top = 108f;
        drawSummaryBox(canvas, paint, "Income", plainMoney(summary.income), 34f, top, Color.rgb(16, 124, 65));
        drawSummaryBox(canvas, paint, "Expense", plainMoney(summary.expense), 177f, top, Color.rgb(196, 43, 28));
        drawSummaryBox(canvas, paint, "Net", signedPlainMoney(summary.net), 320f, top,
                summary.net >= 0d ? Color.rgb(16, 124, 65) : Color.rgb(196, 43, 28));
        drawSummaryBox(canvas, paint, "Entries", String.valueOf(summary.totalCount), 463f, top, Color.rgb(135, 100, 184));

        return 171f;
    }

    private float drawPdfContinuationHeader(Canvas canvas, Paint paint, int pageNumber) {
        paint.setColor(Color.rgb(23, 53, 31));
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setTextSize(14f);
        canvas.drawText("Money Manager Pro • Statement continued", PDF_LEFT, 42f, paint);
        paint.setTextSize(8f);
        paint.setColor(Color.rgb(102, 112, 133));
        canvas.drawText("Page " + pageNumber, PDF_RIGHT - 45f, 42f, paint);
        return 64f;
    }

    private void drawSummaryBox(Canvas canvas, Paint paint, String label, String value, float x, float y, int accent) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(247, 249, 252));
        canvas.drawRoundRect(x, y, x + 126f, y + 47f, 8f, 8f, paint);
        paint.setColor(accent);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setTextSize(8f);
        canvas.drawText(label, x + 9f, y + 16f, paint);
        paint.setTextSize(10f);
        canvas.drawText(trimTo(value, 18), x + 9f, y + 34f, paint);
    }

    private void drawPdfColumns(Canvas canvas, Paint paint, float y) {
        canvas.drawText("DATE", 34f, y, paint);
        canvas.drawText("TYPE", 106f, y, paint);
        canvas.drawText("CATEGORY", 158f, y, paint);
        canvas.drawText("ACCOUNT", 315f, y, paint);
        canvas.drawText("AMOUNT", 455f, y, paint);
    }

    @NonNull
    private File createXlsx(
            @NonNull List<Transaction> transactions,
            @NonNull ReportSummary summary,
            long startMillis,
            long endMillis
    ) throws Exception {
        File directory = reportDirectory();
        File file = new File(directory, "MoneyManager_Statement_" + fileStamp() + ".xlsx");

        StringBuilder sheet = new StringBuilder();
        sheet.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                .append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");

        int row = 1;
        sheet.append(rowInline(row++, "A", "Money Manager Pro Financial Statement"));
        sheet.append(rowInline(row++, "A", formatDateMillis(startMillis) + " to " + formatDateMillis(endMillis)));
        sheet.append(rowCells(row++,
                inlineCell("A", row - 1, "Income"), numberCell("B", row - 1, summary.income),
                inlineCell("C", row - 1, "Expense"), numberCell("D", row - 1, summary.expense),
                inlineCell("E", row - 1, "Net"), numberCell("F", row - 1, summary.net)));
        row++;
        sheet.append(rowCells(row++,
                inlineCell("A", row - 1, "Date"),
                inlineCell("B", row - 1, "Type"),
                inlineCell("C", row - 1, "Category"),
                inlineCell("D", row - 1, "Account"),
                inlineCell("E", row - 1, "Note"),
                inlineCell("F", row - 1, "Amount")));

        SimpleDateFormat display = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ENGLISH);
        for (Transaction transaction : transactions) {
            int currentRow = row++;
            Date parsed = parseDate(transaction.getDate());
            String date = parsed == null ? safe(transaction.getDate()) : display.format(parsed);
            double signedAmount = "EXPENSE".equalsIgnoreCase(safe(transaction.getType()))
                    ? -Math.abs(transaction.getAmount())
                    : Math.abs(transaction.getAmount());
            sheet.append(rowCells(currentRow,
                    inlineCell("A", currentRow, date),
                    inlineCell("B", currentRow, safe(transaction.getType())),
                    inlineCell("C", currentRow, safe(transaction.getCategory())),
                    inlineCell("D", currentRow, safe(transaction.getAccount())),
                    inlineCell("E", currentRow, safe(transaction.getNote())),
                    numberCell("F", currentRow, signedAmount)));
        }

        sheet.append("</sheetData></worksheet>");

        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(file))) {
            putZip(zip, "[Content_Types].xml",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                            + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                            + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                            + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                            + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
                            + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
                            + "</Types>");
            putZip(zip, "_rels/.rels",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                            + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
                            + "</Relationships>");
            putZip(zip, "xl/workbook.xml",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                            + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
                            + "<sheets><sheet name=\"Financial Statement\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
            putZip(zip, "xl/_rels/workbook.xml.rels",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                            + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
                            + "</Relationships>");
            putZip(zip, "xl/worksheets/sheet1.xml", sheet.toString());
        }
        return file;
    }

    private String rowInline(int row, String column, String value) {
        return "<row r=\"" + row + "\">" + inlineCell(column, row, value) + "</row>";
    }

    private String rowCells(int row, String... cells) {
        StringBuilder builder = new StringBuilder("<row r=\"").append(row).append("\">");
        for (String cell : cells) builder.append(cell);
        return builder.append("</row>").toString();
    }

    private String inlineCell(String column, int row, String value) {
        return "<c r=\"" + column + row + "\" t=\"inlineStr\"><is><t>"
                + xml(value) + "</t></is></c>";
    }

    private String numberCell(String column, int row, double value) {
        return "<c r=\"" + column + row + "\"><v>"
                + String.format(Locale.US, "%.2f", value) + "</v></c>";
    }

    private void putZip(ZipOutputStream zip, String name, String content) throws Exception {
        ZipEntry entry = new ZipEntry(name);
        zip.putNextEntry(entry);
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private void shareFile(@NonNull File file, @NonNull String mime) {
        try {
            Uri uri = FileProvider.getUriForFile(
                    activity,
                    activity.getPackageName() + ".report_files",
                    file
            );
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType(mime);
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.putExtra(Intent.EXTRA_SUBJECT, "Money Manager Pro Financial Statement");
            share.putExtra(Intent.EXTRA_TEXT,
                    "Financial statement for " + dateLabel(start) + " to " + dateLabel(end));
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(Intent.createChooser(share, "Share financial report"));
        } catch (Exception exception) {
            txtStatus.setText(useful(exception, "Unable to open Share."));
            txtStatus.setTextColor(Color.parseColor("#C42B1C"));
        }
    }

    private File reportDirectory() {
        File directory = new File(activity.getCacheDir(), "shared_reports");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IllegalStateException("Shared report cache could not be created.");
        }
        return directory;
    }

    private void setCurrentMonth() {
        Calendar now = Calendar.getInstance();
        start.setTimeInMillis(now.getTimeInMillis());
        start.set(Calendar.DAY_OF_MONTH, 1);
        setStartOfDay(start);
        end.setTimeInMillis(now.getTimeInMillis());
        end.set(Calendar.DAY_OF_MONTH, end.getActualMaximum(Calendar.DAY_OF_MONTH));
        setEndOfDay(end);
    }

    private void updateDateButtons() {
        if (btnStart != null) btnStart.setText("From  " + dateLabel(start));
        if (btnEnd != null) btnEnd.setText("To  " + dateLabel(end));
    }

    private Date parseDate(String value) {
        String clean = safe(value);
        if (clean.isEmpty()) return null;
        for (String pattern : DATE_PATTERNS) {
            SimpleDateFormat formatter = new SimpleDateFormat(pattern, Locale.US);
            formatter.setLenient(false);
            ParsePosition position = new ParsePosition(0);
            Date parsed = formatter.parse(clean, position);
            if (parsed != null && position.getIndex() == clean.length()) return parsed;
        }
        return null;
    }

    private long timeOf(String value) {
        Date parsed = parseDate(value);
        return parsed == null ? 0L : parsed.getTime();
    }

    private void setStartOfDay(Calendar calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
    }

    private void setEndOfDay(Calendar calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        calendar.set(Calendar.MILLISECOND, 999);
    }

    private String dateLabel(Calendar calendar) {
        return new SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH).format(calendar.getTime());
    }

    private String formatDateMillis(long millis) {
        return new SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH).format(new Date(millis));
    }

    private String fileStamp() {
        return new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
    }

    private String money(double value) {
        NumberFormat format = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));
        format.setMaximumFractionDigits(2);
        return format.format(Math.abs(value));
    }

    private String signedMoney(double value) {
        return (value >= 0d ? "+" : "−") + money(value);
    }

    private String plainMoney(double value) {
        return "Rs. " + String.format(Locale.ENGLISH, "%,.2f", Math.abs(value));
    }

    private String signedPlainMoney(double value) {
        return (value >= 0d ? "+" : "-") + plainMoney(value);
    }

    private String trimTo(String value, int max) {
        String safe = value == null ? "" : value;
        return safe.length() <= max ? safe : safe.substring(0, Math.max(1, max - 1)) + "…";
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String xml(String value) {
        return safe(value)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private String useful(Exception exception, String fallback) {
        String message = exception == null ? null : exception.getMessage();
        return message == null || message.trim().isEmpty() ? fallback : message.trim();
    }

    private LinearLayout findMainVertical(ViewGroup root) {
        if (root == null) return null;
        LinearLayout best = null;
        int bestChildren = -1;
        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);
            if (child instanceof LinearLayout) {
                LinearLayout layout = (LinearLayout) child;
                if (layout.getOrientation() == LinearLayout.VERTICAL && layout.getChildCount() > bestChildren) {
                    best = layout;
                    bestChildren = layout.getChildCount();
                }
            }
            if (child instanceof ViewGroup) {
                LinearLayout nested = findMainVertical((ViewGroup) child);
                if (nested != null && nested.getChildCount() > bestChildren) {
                    best = nested;
                    bestChildren = nested.getChildCount();
                }
            }
        }
        return best;
    }

    private MaterialCardView card(String background, String outline) {
        MaterialCardView card = new MaterialCardView(activity);
        card.setCardBackgroundColor(Color.parseColor(background));
        card.setStrokeColor(Color.parseColor(outline));
        card.setStrokeWidth(dp(1));
        card.setRadius(dp(17));
        card.setCardElevation(0f);
        card.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        return card;
    }

    private TextView chip(String value, String background, String foreground) {
        TextView chip = text(value, 9, foreground, true);
        chip.setGravity(Gravity.CENTER);
        chip.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(background)));
        chip.setPadding(dp(7), dp(5), dp(7), dp(5));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(32), 1f);
        params.setMargins(dp(2), 0, dp(2), 0);
        chip.setLayoutParams(params);
        return chip;
    }

    private MaterialButton button(String label, boolean strong) {
        MaterialButton button = new MaterialButton(activity);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(10);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setCornerRadius(dp(13));
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setTextColor(Color.parseColor(strong ? "#FFFFFF" : "#17351F"));
        button.setBackgroundTintList(ColorStateList.valueOf(
                Color.parseColor(strong ? "#0F6CBD" : "#FFFFFF")
        ));
        button.setStrokeColor(ColorStateList.valueOf(Color.parseColor(strong ? "#0F6CBD" : "#C9D7CD")));
        button.setStrokeWidth(dp(1));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(43), 1f);
        params.setMargins(dp(3), 0, dp(3), 0);
        button.setLayoutParams(params);
        return button;
    }

    private TextView text(String value, float size, String color, boolean bold) {
        TextView text = new TextView(activity);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(Color.parseColor(color));
        text.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        text.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        return text;
    }

    private LinearLayout verticalPadding(int padding) {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(padding), dp(padding), dp(padding), dp(padding));
        return layout;
    }

    private LinearLayout horizontal() {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        layout.setBaselineAligned(false);
        layout.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        return layout;
    }

    private LinearLayout horizontalOrVertical() {
        int widthDp = (int) (activity.getResources().getDisplayMetrics().widthPixels
                / activity.getResources().getDisplayMetrics().density);
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(widthDp < 360 ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        layout.setBaselineAligned(false);
        layout.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        return layout;
    }

    private void setMargins(View view, int left, int top, int right, int bottom) {
        ViewGroup.LayoutParams raw = view.getLayoutParams();
        LinearLayout.LayoutParams params = raw instanceof LinearLayout.LayoutParams
                ? (LinearLayout.LayoutParams) raw
                : new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        view.setLayoutParams(params);
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private static final class ReportSummary {
        double income;
        double expense;
        double net;
        int incomeCount;
        int expenseCount;
        int totalCount;

        ReportSummary copy() {
            ReportSummary copy = new ReportSummary();
            copy.income = income;
            copy.expense = expense;
            copy.net = net;
            copy.incomeCount = incomeCount;
            copy.expenseCount = expenseCount;
            copy.totalCount = totalCount;
            return copy;
        }
    }
}
