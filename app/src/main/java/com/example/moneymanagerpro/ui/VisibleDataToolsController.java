package com.example.moneymanagerpro.ui;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;

import com.example.moneymanagerpro.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * One visible-data contract: the chosen range controls visible date-bearing rows and the exact
 * remaining text is the source for PDF, Excel-compatible XML and Share-PDF.
 */
public final class VisibleDataToolsController {
    private static final String TAG = "visible_data_tools_v3";
    private static final String ORIGINAL_VISIBILITY = "visible_data_original_visibility";
    private static final String[] SUPPORTED = {"Transactions", "Report", "Analytics", "Charts", "Calendar", "CreditCard", "Loan", "Budget", "Goal", "Investment", "Subscription", "Recurring", "Account", "AdvancedFinanceData", "FinanceAdvisor", "ReceiptGallery"};
    private static final String[] DATE_PATTERNS = {"yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy-MM-dd", "dd-MM-yyyy HH:mm", "dd-MM-yyyy", "dd/MM/yyyy HH:mm", "dd/MM/yyyy", "dd MMM yyyy", "MMM dd, yyyy"};

    private final Activity activity;
    private LinearLayout toolbar;
    private Calendar start;
    private Calendar end;
    private Sort sort = Sort.NEWEST;

    public VisibleDataToolsController(Activity activity) { this.activity = activity; }

    public void attach() {
        if (!supported()) return;
        View decor = activity.getWindow().getDecorView();
        View existing = decor.findViewWithTag(TAG);
        if (existing instanceof LinearLayout) { toolbar = (LinearLayout) existing; applyFilter(); return; }
        decor.post(this::inject);
    }

    public void detach() { toolbar = null; }

    /** Shares the currently visible, currently filtered page in A4 PDF form. */
    public void shareCurrentPdf() {
        if (start == null || end == null) selectRange(Range.THIS_MONTH);
        if (toolbar == null) attach();
        activity.getWindow().getDecorView().post(() -> export(false, true));
    }

    private boolean supported() {
        String name = activity.getClass().getSimpleName();
        if (name.equals("ExportActivity") || name.startsWith("Add") || name.startsWith("Edit") || name.contains("Settings") || name.contains("Backup") || name.contains("Authentication") || name.contains("Pin")) return false;
        for (String value : SUPPORTED) if (name.contains(value)) return true;
        return false;
    }

    private void inject() {
        View content = activity.findViewById(android.R.id.content);
        if (!(content instanceof ViewGroup)) return;
        ViewGroup contentGroup = (ViewGroup) content;
        if (contentGroup.getChildCount() == 0) return;
        View original = contentGroup.getChildAt(0);
        contentGroup.removeView(original);

        LinearLayout wrapper = new LinearLayout(activity);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setLayoutParams(new ViewGroup.LayoutParams(-1, -1));
        toolbar = buildToolbar();
        toolbar.setTag(TAG);
        wrapper.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(96)));
        wrapper.addView(original, new LinearLayout.LayoutParams(-1, 0, 1f));
        contentGroup.addView(wrapper);
        selectRange(Range.THIS_MONTH);
    }

    private LinearLayout buildToolbar() {
        LinearLayout outer = new LinearLayout(activity);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setPadding(dp(8), dp(6), dp(8), dp(4));
        outer.setBackgroundColor(activity.getColor(R.color.app_surface));

        LinearLayout ranges = actionStrip();
        for (Range range : Range.values()) ranges.addView(button(range.symbol + "  " + range.label, false, v -> selectRange(range)));
        outer.addView(scroller(ranges), new LinearLayout.LayoutParams(-1, dp(41)));

        LinearLayout exports = actionStrip();
        exports.addView(button("⇅  Sort", true, v -> cycleSort()));
        exports.addView(button("▤  PDF", true, v -> export(false, false)));
        exports.addView(button("▦  Excel", true, v -> export(true, false)));
        exports.addView(button("↗  Share PDF", true, v -> export(false, true)));
        outer.addView(scroller(exports), new LinearLayout.LayoutParams(-1, dp(41)));
        return outer;
    }

    private HorizontalScrollView scroller(LinearLayout content) {
        HorizontalScrollView scroll = new HorizontalScrollView(activity);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setFillViewport(false);
        scroll.addView(content);
        return scroll;
    }

    private LinearLayout actionStrip() {
        LinearLayout strip = new LinearLayout(activity);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        strip.setGravity(Gravity.CENTER_VERTICAL);
        return strip;
    }

    private MaterialButton button(String label, boolean action, View.OnClickListener listener) {
        MaterialButton button = new MaterialButton(activity);
        button.setText(label);
        button.setTextSize(11);
        button.setAllCaps(false);
        button.setTextColor(activity.getColor(action ? R.color.secondary : R.color.primary));
        button.setBackgroundTintList(ColorStateList.valueOf(activity.getColor(action ? R.color.info_surface : R.color.app_surface_soft)));
        button.setStrokeColor(ColorStateList.valueOf(activity.getColor(action ? R.color.info_outline : R.color.app_outline)));
        button.setStrokeWidth(dp(1));
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setPadding(dp(11), 0, dp(11), 0);
        button.setCornerRadius(dp(14));
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, dp(34));
        p.setMargins(0, dp(2), dp(6), dp(2));
        button.setLayoutParams(p);
        return button;
    }

    private void selectRange(Range range) {
        Calendar now = Calendar.getInstance(); clearTime(now);
        start = (Calendar) now.clone(); end = (Calendar) now.clone();
        switch (range) {
            case TODAY: break;
            case THIS_WEEK: start.set(Calendar.DAY_OF_WEEK, start.getFirstDayOfWeek()); break;
            case THIS_MONTH: start.set(Calendar.DAY_OF_MONTH, 1); end.set(Calendar.DAY_OF_MONTH, end.getActualMaximum(Calendar.DAY_OF_MONTH)); break;
            case LAST_MONTH: start.add(Calendar.MONTH, -1); start.set(Calendar.DAY_OF_MONTH, 1); end = (Calendar) start.clone(); end.set(Calendar.DAY_OF_MONTH, end.getActualMaximum(Calendar.DAY_OF_MONTH)); break;
            case LAST_TWO_MONTHS: start.add(Calendar.MONTH, -1); start.set(Calendar.DAY_OF_MONTH, 1); break;
            case LAST_THREE_MONTHS: start.add(Calendar.MONTH, -2); start.set(Calendar.DAY_OF_MONTH, 1); break;
            case LAST_SIX_MONTHS: start.add(Calendar.MONTH, -5); start.set(Calendar.DAY_OF_MONTH, 1); break;
            case CUSTOM: pickCustomStart(); return;
        }
        end.set(Calendar.HOUR_OF_DAY, 23); end.set(Calendar.MINUTE, 59); end.set(Calendar.SECOND, 59);
        applyFilter();
        Toast.makeText(activity, range.label + " • " + sort.label, Toast.LENGTH_SHORT).show();
    }

    private void pickCustomStart() {
        Calendar now = Calendar.getInstance();
        new DatePickerDialog(activity, (v, y, m, d) -> {
            start = Calendar.getInstance(); start.set(y, m, d, 0, 0, 0);
            new DatePickerDialog(activity, (v2, y2, m2, d2) -> {
                end = Calendar.getInstance(); end.set(y2, m2, d2, 23, 59, 59);
                if (end.before(start)) { Calendar swap = start; start = end; end = swap; }
                applyFilter();
            }, y, m, d).show();
        }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void cycleSort() {
        sort = Sort.values()[(sort.ordinal() + 1) % Sort.values().length];
        applyFilter();
        Toast.makeText(activity, "Sort: " + sort.label, Toast.LENGTH_SHORT).show();
    }

    private void applyFilter() {
        if (toolbar == null || start == null || end == null) return;
        View root = activity.findViewById(android.R.id.content);
        filterChildren(root);
    }

    private void filterChildren(View view) {
        if (view == toolbar) return;
        if (view instanceof MaterialCardView) {
            Object original = view.getTag(R.id.visible_data_original_visibility);
            if (original == null) view.setTag(R.id.visible_data_original_visibility, view.getVisibility());
            Date date = findDate(visibleText(view));
            if (date != null) view.setVisibility(!date.before(start.getTime()) && !date.after(end.getTime()) ? View.VISIBLE : View.GONE);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) filterChildren(group.getChildAt(i));
        }
    }

    private void export(boolean excel, boolean share) {
        List<String> rows = snapshot();
        if (rows.isEmpty()) { Toast.makeText(activity, "No visible data to export", Toast.LENGTH_SHORT).show(); return; }
        new Thread(() -> {
            try {
                File dir = new File(activity.getCacheDir(), "shared_reports");
                if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Report folder unavailable");
                String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
                File file = new File(dir, "MoneyManager_" + activity.getClass().getSimpleName() + "_" + stamp + (excel ? ".xls" : ".pdf"));
                if (excel) writeExcelXml(file, rows); else writePdf(file, rows);
                activity.runOnUiThread(() -> {
                    if (share) sharePdf(file); else shareOrOpen(file, excel);
                });
            } catch (Exception e) { activity.runOnUiThread(() -> Toast.makeText(activity, "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show()); }
        }).start();
    }

    private List<String> snapshot() {
        List<String> rows = new ArrayList<>();
        collectCards(activity.findViewById(android.R.id.content), rows);
        if (sort == Sort.OLDEST || sort == Sort.AMOUNT_LOW) Collections.reverse(rows);
        if (sort == Sort.AMOUNT_HIGH || sort == Sort.AMOUNT_LOW) rows.sort(Comparator.comparingDouble(this::firstAmount).reversed());
        return rows;
    }

    private void collectCards(View view, List<String> rows) {
        if (view == toolbar || view.getVisibility() != View.VISIBLE) return;
        if (view instanceof MaterialCardView) {
            String text = visibleText(view).trim().replaceAll("\\s*\\n\\s*", " • ").replaceAll("\\s{2,}", " ");
            if (!text.isEmpty() && !text.contains("PDF • Excel • Share PDF")) rows.add(text);
            return;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) collectCards(group.getChildAt(i), rows);
        }
    }

    private String visibleText(View view) {
        StringBuilder out = new StringBuilder(); collectText(view, out); return out.toString();
    }

    private void collectText(View view, StringBuilder out) {
        if (view.getVisibility() != View.VISIBLE || view == toolbar) return;
        if (view instanceof TextView) { CharSequence text = ((TextView) view).getText(); if (text != null && text.length() > 0) out.append(text).append('\n'); }
        if (view instanceof ViewGroup) { ViewGroup g = (ViewGroup) view; for (int i = 0; i < g.getChildCount(); i++) collectText(g.getChildAt(i), out); }
    }

    private Date findDate(String text) {
        for (String token : text.split("[\\n•|]")) for (String pattern : DATE_PATTERNS) {
            SimpleDateFormat f = new SimpleDateFormat(pattern, Locale.ENGLISH); f.setLenient(false);
            ParsePosition p = new ParsePosition(0); Date d = f.parse(token.trim(), p);
            if (d != null && p.getIndex() == token.trim().length()) return d;
        }
        return null;
    }

    private double firstAmount(String value) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("[₹]?\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)").matcher(value);
        if (!m.find()) return 0; try { return Double.parseDouble(m.group(1).replace(",", "")); } catch (Exception e) { return 0; }
    }

    private void writeExcelXml(File file, List<String> rows) throws Exception {
        try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            w.write("<?xml version=\"1.0\"?><Workbook xmlns=\"urn:schemas-microsoft-com:office:spreadsheet\" xmlns:ss=\"urn:schemas-microsoft-com:office:spreadsheet\" xmlns:x=\"urn:schemas-microsoft-com:office:excel\"><Worksheet ss:Name=\"Visible Data\"><Table>");
            w.write(cell("Period", format(start.getTime()) + " to " + format(end.getTime())));
            w.write(cell("Sort", sort.label));
            int i = 1; for (String row : rows) w.write(cell(String.valueOf(i++), row));
            w.write("</Table><WorksheetOptions xmlns=\"urn:schemas-microsoft-com:office:excel\"><PageSetup><Layout x:Orientation=\"Landscape\"/><PageMargins x:Bottom=\"0.5\" x:Left=\"0.5\" x:Right=\"0.5\" x:Top=\"0.5\"/></PageSetup></WorksheetOptions></Worksheet></Workbook>");
        }
    }

    private String cell(String a, String b) { return "<Row><Cell><Data ss:Type=\"String\">" + xml(a) + "</Data></Cell><Cell><Data ss:Type=\"String\">" + xml(b) + "</Data></Cell></Row>"; }
    private String xml(String s) { return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }

    private void writePdf(File file, List<String> rows) throws Exception {
        PdfDocument document = new PdfDocument(); Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG); int pageNo = 1; float y = 42;
        PdfDocument.Page page = document.startPage(new PdfDocument.PageInfo.Builder(595, 842, pageNo).create());
        paint.setColor(Color.rgb(25, 55, 70)); paint.setTextSize(18); paint.setFakeBoldText(true); page.getCanvas().drawText("Money Manager Pro", 36, y, paint); y += 24;
        paint.setTextSize(10); paint.setFakeBoldText(false); page.getCanvas().drawText(activity.getClass().getSimpleName() + " • " + format(start.getTime()) + " to " + format(end.getTime()) + " • " + sort.label, 36, y, paint); y += 24;
        for (String row : rows) {
            List<String> lines = wrap(row, 86); float needed = lines.size() * 14f + 12;
            if (y + needed > 805) { document.finishPage(page); page = document.startPage(new PdfDocument.PageInfo.Builder(595, 842, ++pageNo).create()); y = 42; }
            paint.setColor(Color.rgb(245, 248, 250)); page.getCanvas().drawRect(32, y - 11, 563, y + needed - 12, paint);
            paint.setColor(Color.rgb(30, 45, 55)); paint.setTextSize(9);
            for (String line : lines) { page.getCanvas().drawText(line, 40, y, paint); y += 14; }
            y += 10;
        }
        document.finishPage(page); try (FileOutputStream out = new FileOutputStream(file)) { document.writeTo(out); } document.close();
    }

    private List<String> wrap(String text, int max) { List<String> out = new ArrayList<>(); String remaining = text; while (remaining.length() > max) { int cut = remaining.lastIndexOf(' ', max); if (cut < 1) cut = max; out.add(remaining.substring(0, cut)); remaining = remaining.substring(cut).trim(); } if (!remaining.isEmpty()) out.add(remaining); return out; }

    private void sharePdf(File file) { shareOrOpen(file, false); }
    private void shareOrOpen(File file, boolean excel) {
        Uri uri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".report_files", file);
        Intent intent = new Intent(Intent.ACTION_SEND); intent.setType(excel ? "application/vnd.ms-excel" : "application/pdf"); intent.putExtra(Intent.EXTRA_STREAM, uri); intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); activity.startActivity(Intent.createChooser(intent, excel ? "Open or share Excel" : "Share PDF"));
    }

    private String format(Date date) { return new SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH).format(date); }
    private void clearTime(Calendar c) { c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0); }
    private int dp(int v) { return Math.round(v * activity.getResources().getDisplayMetrics().density); }

    private enum Range {
        TODAY("Today", "●"), THIS_WEEK("This Week", "▥"), THIS_MONTH("This Month", "▣"),
        LAST_MONTH("Last Month", "‹"), LAST_TWO_MONTHS("Last Two Month", "«"),
        LAST_THREE_MONTHS("Last Three Month", "≪"), LAST_SIX_MONTHS("Last Six Month", "◫"), CUSTOM("Custom", "⌗");
        final String label; final String symbol;
        Range(String label, String symbol) { this.label = label; this.symbol = symbol; }
    }
    private enum Sort { NEWEST("Newest first"), OLDEST("Oldest first"), AMOUNT_HIGH("Amount high to low"), AMOUNT_LOW("Amount low to high"); final String label; Sort(String l) { label = l; } }
}
