package com.example.moneymanagerpro.ui;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.LinearLayout;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.utils.UnifiedPageHeader;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.IdentityHashMap;
import java.util.Map;

/** Page-specific list actions; screen rows and exports use the same visible order. */
public final class VisibleDataToolsController {
    private static final String TAG = "visible_data_tools_v3";
    // These screens expose a real list of rows. Other screens have charts or forms,
    // where a generic filter icon cannot safely filter the underlying data.
    private static final String[] LIST_PAGES = {
            "AccountActivity", "LoanActivity", "BudgetActivity", "GoalActivity",
            "InvestmentActivity", "SubscriptionActivity", "RecurringActivity",
            "CreditCardActivity", "ReceiptGalleryActivity"
    };

    private final Activity activity;
    private LinearLayout toolbar;
    private Calendar start;
    private Calendar end;
    private Sort sort = Sort.NEWEST;
    private String query = "";
    private final Map<View, Integer> originalOrder = new IdentityHashMap<>();
    private ViewTreeObserver.OnGlobalLayoutListener rowObserver;
    private View observedList;
    private View firstObservedRow;
    private int observedCount = -1;
    private boolean applying;

    public VisibleDataToolsController(Activity activity) { this.activity = activity; }

    public void attach() {
        if (!supported()) return;
        View decor = activity.getWindow().getDecorView();
        View existing = decor.findViewWithTag(TAG);
        if (existing instanceof LinearLayout) { toolbar = (LinearLayout) existing; observeRows(); applyFilter(); return; }
        decor.post(this::inject);
    }

    public void detach() {
        if (rowObserver != null && observedList != null) {
            observedList.getViewTreeObserver().removeOnGlobalLayoutListener(rowObserver);
        }
        rowObserver = null;
        observedList = null;
        originalOrder.clear();
        toolbar = null;
    }

    private void observeRows() {
        LinearLayout list = listContainer();
        if (list == null || observedList == list) return;
        observedList = list;
        rowObserver = () -> {
            if (applying) return;
            int count = list.getChildCount();
            View first = firstRow(list);
            if (count != observedCount || first != firstObservedRow) applyFilter();
        };
        list.getViewTreeObserver().addOnGlobalLayoutListener(rowObserver);
    }

    private View firstRow(LinearLayout list) {
        for (int i = 0; i < list.getChildCount(); i++) {
            View child = list.getChildAt(i);
            if (child instanceof MaterialCardView) return child;
        }
        return null;
    }

    /** Shares the currently visible, currently filtered page in A4 PDF form. */
    public void shareCurrentPdf() {
        if (toolbar == null) attach();
        activity.getWindow().getDecorView().post(() -> export(false, true));
    }

    /** Shares the Dashboard exactly for the month currently selected by the user. */
    public void shareDashboardMonth(@NonNull Calendar selectedMonth) {
        start = (Calendar) selectedMonth.clone();
        clearTime(start);
        start.set(Calendar.DAY_OF_MONTH, 1);
        end = (Calendar) start.clone();
        end.set(Calendar.DAY_OF_MONTH, end.getActualMaximum(Calendar.DAY_OF_MONTH));
        end.set(Calendar.HOUR_OF_DAY, 23);
        end.set(Calendar.MINUTE, 59);
        end.set(Calendar.SECOND, 59);
        activity.getWindow().getDecorView().post(() -> export(false, true));
    }

    private boolean supported() {
        String name = activity.getClass().getSimpleName();
        // Transactions has its own working filter panel and sort controls.
        for (String page : LIST_PAGES) if (name.equals(page)) return true;
        return false;
    }

    private LinearLayout listContainer() {
        int id;
        switch (activity.getClass().getSimpleName()) {
            case "AccountActivity": id = R.id.accountContainer; break;
            case "LoanActivity": id = R.id.loanContainer; break;
            case "BudgetActivity": id = R.id.budgetContainer; break;
            case "GoalActivity": id = R.id.goalContainer; break;
            case "InvestmentActivity": id = R.id.investmentContainer; break;
            case "SubscriptionActivity": id = R.id.subscriptionContainer; break;
            case "RecurringActivity": id = R.id.recurringContainer; break;
            case "CreditCardActivity": id = R.id.creditCardContainer; break;
            case "ReceiptGalleryActivity": id = R.id.receiptContainer; break;
            default: return null;
        }
        View view = activity.findViewById(id);
        return view instanceof LinearLayout ? (LinearLayout) view : null;
    }

    private void inject() {
        View content = activity.findViewById(android.R.id.content);
        if (!(content instanceof ViewGroup)) return;
        ViewGroup contentGroup = (ViewGroup) content;
        if (contentGroup.getChildCount() == 0) return;
        View original = contentGroup.getChildAt(0);
        if (attachActionsToHeader(original)) {
            observeRows();
            return;
        }
        contentGroup.removeView(original);
        hideOriginalBack(original);

        LinearLayout wrapper = new LinearLayout(activity);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setLayoutParams(new ViewGroup.LayoutParams(-1, -1));
        toolbar = buildToolbar();
        toolbar.setTag(TAG);
        wrapper.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(48)));
        wrapper.addView(original, new LinearLayout.LayoutParams(-1, 0, 1f));
        contentGroup.addView(wrapper);
        observeRows();
        View floatingDataCenter = contentGroup.findViewWithTag("credit_card_data_center_fab");
        if (floatingDataCenter != null) floatingDataCenter.bringToFront();
    }

    private LinearLayout buildToolbar() {
        LinearLayout outer = new LinearLayout(activity);
        outer.setOrientation(LinearLayout.HORIZONTAL);
        outer.setGravity(Gravity.CENTER_VERTICAL);
        outer.setPadding(dp(8), dp(6), dp(8), dp(4));
        outer.setBackgroundColor(activity.getColor(R.color.app_surface));

        MaterialButton back = button("", true, v -> activity.finish());
        back.setContentDescription("Back");
        UnifiedPageHeader.styleBack(activity, back);
        outer.addView(back);
        View spacer = new View(activity);
        outer.addView(spacer, new LinearLayout.LayoutParams(0, dp(1), 1f));
        outer.addView(iconButton(R.drawable.ic_filter_alt_24, "Filter list", false,
                v -> showFilterMenu()));
        outer.addView(iconButton(R.drawable.ic_sort_24, "Sort list", false,
                v -> showSortMenu()));
        outer.addView(iconButton(R.drawable.ic_share_24, "Share or export", true,
                v -> showExportMenu()));
        return outer;
    }

    private boolean attachActionsToHeader(View original) {
        View header = original.findViewWithTag(UnifiedPageHeader.HEADER_TAG);
        if (!(header instanceof MaterialCardView)) return false;
        MaterialCardView card = (MaterialCardView) header;
        if (card.getChildCount() == 0 || !(card.getChildAt(0) instanceof LinearLayout)) return false;
        LinearLayout headerRow = (LinearLayout) card.getChildAt(0);
        if (headerRow.getOrientation() != LinearLayout.HORIZONTAL) return false;
        LinearLayout actions = new LinearLayout(activity);
        actions.setTag(TAG);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.addView(iconButton(R.drawable.ic_filter_alt_24, "Filter list", false,
                v -> showFilterMenu()));
        actions.addView(iconButton(R.drawable.ic_sort_24, "Sort list", false,
                v -> showSortMenu()));
        actions.addView(iconButton(R.drawable.ic_share_24, "Share or export", true,
                v -> showExportMenu()));
        headerRow.addView(actions, new LinearLayout.LayoutParams(-2, -2));
        toolbar = actions;
        return true;
    }

    private MaterialButton iconButton(int iconRes, String description, boolean action,
                                      View.OnClickListener listener) {
        MaterialButton button = button("", action, listener);
        button.setContentDescription(description);
        button.setIconResource(iconRes);
        button.setIconTint(ColorStateList.valueOf(activity.getColor(
                action ? R.color.secondary : R.color.primary)));
        button.setIconPadding(0);
        button.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
        button.setPadding(0, 0, 0, 0);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(38), dp(38));
        params.setMargins(dp(4), 0, 0, 0);
        button.setLayoutParams(params);
        return button;
    }

    private void showFilterMenu() {
        EditText search = new EditText(activity);
        search.setSingleLine(true);
        search.setHint("Search visible items");
        search.setText(query);
        search.setSelectAllOnFocus(true);
        new AlertDialog.Builder(activity)
                .setTitle("Filter list")
                .setView(search)
                .setPositiveButton("Apply", (dialog, which) -> {
                    query = search.getText().toString().trim().toLowerCase(Locale.ROOT);
                    applyFilter();
                })
                .setNeutralButton("Clear", (dialog, which) -> {
                    query = "";
                    applyFilter();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showSortMenu() {
        String[] choices = {"Original order", "Name A–Z", "Name Z–A", "Amount high to low", "Amount low to high"};
        new AlertDialog.Builder(activity).setTitle("Sort list")
                .setSingleChoiceItems(choices, sort.ordinal(), (dialog, which) -> {
                    sort = Sort.values()[which];
                    applyFilter();
                    dialog.dismiss();
                }).setNegativeButton("Cancel", null).show();
    }

    private void showExportMenu() {
        String[] choices = {
                "▤  Save as PDF",
                "▦  Save as Excel",
                "↗  Share as PDF"
        };
        new AlertDialog.Builder(activity)
                .setTitle("Export current filtered data")
                .setItems(choices, (dialog, which) -> {
                    if (which == 0) export(false, false);
                    else if (which == 1) export(true, false);
                    else export(false, true);
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private boolean hideOriginalBack(View view) {
        if (view instanceof TextView) {
            CharSequence value = ((TextView) view).getText();
            String label = value == null ? "" : value.toString().trim();
            if (label.equals("‹") || label.equals("←") || label.toLowerCase(Locale.ENGLISH).contains("back")) {
                view.setVisibility(View.GONE);
                return true;
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                if (hideOriginalBack(group.getChildAt(index))) return true;
            }
        }
        return false;
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

    private void applyFilter() {
        LinearLayout list = listContainer();
        if (list == null || applying) return;
        applying = true;
        try {
        List<View> rows = new ArrayList<>();
        List<Integer> positions = new ArrayList<>();
        Map<View, String> texts = new IdentityHashMap<>();
        for (int i = 0; i < list.getChildCount(); i++) {
            View row = list.getChildAt(i);
            if (!(row instanceof MaterialCardView)) continue;
            if (!originalOrder.containsKey(row)) originalOrder.put(row, i);
            // Hidden rows must be made readable before applying a new query.
            row.setVisibility(View.VISIBLE);
            String text = visibleText(row);
            texts.put(row, text);
            row.setVisibility(text.toLowerCase(Locale.ROOT).contains(query)
                    ? View.VISIBLE : View.GONE);
            rows.add(row);
            positions.add(i);
        }
        originalOrder.keySet().retainAll(rows);
        if (rows.size() < 2) return;
        Comparator<View> comparator;
        switch (sort) {
            case NAME_ASC: comparator = Comparator.comparing(texts::get, String.CASE_INSENSITIVE_ORDER); break;
            case NAME_DESC: comparator = (a, b) -> String.CASE_INSENSITIVE_ORDER.compare(texts.get(b), texts.get(a)); break;
            case AMOUNT_HIGH: comparator = (a, b) -> Double.compare(firstAmount(texts.get(b)), firstAmount(texts.get(a))); break;
            case AMOUNT_LOW: comparator = Comparator.comparingDouble(v -> firstAmount(texts.get(v))); break;
            default: comparator = Comparator.comparingInt(v -> originalOrder.get(v));
        }
        rows.sort(comparator.thenComparingInt(v -> originalOrder.get(v)));
        // Reorder the actual card views; changing an export snapshot alone does not sort the page.
        for (View row : rows) list.removeView(row);
        for (int i = 0; i < rows.size(); i++) list.addView(rows.get(i),
                Math.min(positions.get(i), list.getChildCount()));
        } finally {
            observedCount = list.getChildCount();
            firstObservedRow = firstRow(list);
            applying = false;
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
        LinearLayout list = listContainer();
        if (list == null) collectCards(activity.findViewById(android.R.id.content), rows);
        else for (int i = 0; i < list.getChildCount(); i++) {
            View row = list.getChildAt(i);
            if (row instanceof MaterialCardView && row.getVisibility() == View.VISIBLE) {
                String value = visibleText(row).trim().replaceAll("\\s*\\n\\s*", " • ");
                if (!value.isEmpty()) rows.add(value);
            }
        }
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

    private double firstAmount(String value) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?:₹|Rs\\.?|INR)\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(value);
        boolean found = m.find();
        if (!found) {
            m = java.util.regex.Pattern.compile("([0-9][0-9,]*(?:\\.[0-9]+)?)").matcher(value);
            found = m.find();
        }
        if (!found) return 0; try { return Double.parseDouble(m.group(1).replace(",", "")); } catch (Exception e) { return 0; }
    }

    private void writeExcelXml(File file, List<String> rows) throws Exception {
        try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            w.write("<?xml version=\"1.0\"?><Workbook xmlns=\"urn:schemas-microsoft-com:office:spreadsheet\" xmlns:ss=\"urn:schemas-microsoft-com:office:spreadsheet\" xmlns:x=\"urn:schemas-microsoft-com:office:excel\"><Worksheet ss:Name=\"Visible Data\"><Table>");
            w.write(cell("Period", periodLabel()));
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
        paint.setTextSize(10); paint.setFakeBoldText(false); page.getCanvas().drawText(activity.getClass().getSimpleName() + " • " + periodLabel() + " • " + sort.label, 36, y, paint); y += 24;
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
    private String periodLabel() { return start == null || end == null ? "All dates" : format(start.getTime()) + " to " + format(end.getTime()); }
    private void clearTime(Calendar c) { c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0); }
    private int dp(int v) { return Math.round(v * activity.getResources().getDisplayMetrics().density); }

    private enum Sort { NEWEST("Original order"), NAME_ASC("Name A–Z"), NAME_DESC("Name Z–A"), AMOUNT_HIGH("Amount high to low"), AMOUNT_LOW("Amount low to high"); final String label; Sort(String l) { label = l; } }
}
