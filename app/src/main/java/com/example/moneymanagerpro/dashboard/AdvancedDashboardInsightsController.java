package com.example.moneymanagerpro.dashboard;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.example.moneymanagerpro.database.DatabaseClient;
import com.example.moneymanagerpro.model.Transaction;
import com.google.android.material.card.MaterialCardView;

import java.text.NumberFormat;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Adds the advanced visual section of Smart Dashboard 2.0.
 * All analysis is performed locally from the Room transaction table.
 */
public final class AdvancedDashboardInsightsController {

    private static final String PANEL_TAG = "advanced_dashboard_insights_panel";

    private static final String[] DATE_PATTERNS = {
            "yyyy-MM-dd HH:mm", "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd",
            "dd-MM-yyyy HH:mm", "dd-MM-yyyy", "dd/MM/yyyy HH:mm", "dd/MM/yyyy"
    };

    private final Activity activity;
    private LinearLayout panel;
    private InteractiveCashFlowTrendView chart;
    private LinearLayout categoryContainer;
    private TextView txtSelection;
    private int requestVersion;

    public AdvancedDashboardInsightsController(@NonNull Activity activity) {
        this.activity = activity;
    }

    public void attach() {
        ViewGroup content = activity.findViewById(android.R.id.content);
        LinearLayout root = findVerticalLinearLayout(content);
        if (root == null) {
            return;
        }

        View existing = root.findViewWithTag(PANEL_TAG);
        if (existing instanceof LinearLayout) {
            panel = (LinearLayout) existing;
        } else {
            panel = buildPanel();
            panel.setTag(PANEL_TAG);
            root.addView(panel);
        }

        loadData();
    }

    private LinearLayout buildPanel() {
        LinearLayout container = new LinearLayout(activity);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(0), dp(4), dp(0), dp(8));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, dp(22), 0, dp(8));
        container.setLayoutParams(params);

        TextView title = text("Interactive Trends", 20, Color.parseColor("#1D2939"), true);
        container.addView(title);

        TextView subtitle = text(
                "Touch the chart to compare income and expense. Category bars show where the selected month was spent.",
                11,
                Color.parseColor("#667085"),
                false
        );
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        subtitleParams.setMargins(0, dp(4), 0, dp(12));
        subtitle.setLayoutParams(subtitleParams);
        container.addView(subtitle);

        MaterialCardView chartCard = card("#F8FBFF", "#C9DDF2", 20);
        LinearLayout chartContent = new LinearLayout(activity);
        chartContent.setOrientation(LinearLayout.VERTICAL);
        chartContent.setPadding(dp(14), dp(13), dp(14), dp(12));

        TextView chartTitle = text("6-Month Cash Flow", 15, Color.parseColor("#0F6CBD"), true);
        chartContent.addView(chartTitle);

        txtSelection = text("Loading monthly trend...", 11, Color.parseColor("#667085"), false);
        LinearLayout.LayoutParams selectionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        selectionParams.setMargins(0, dp(3), 0, dp(8));
        txtSelection.setLayoutParams(selectionParams);
        chartContent.addView(txtSelection);

        chart = new InteractiveCashFlowTrendView(activity);
        chart.setPadding(dp(2), dp(2), dp(2), dp(2));
        chart.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(225)
        ));
        chart.setOnPointSelectedListener((point, position) -> showSelectedPoint(point));
        chartContent.addView(chart);
        chartCard.addView(chartContent);
        container.addView(chartCard);

        TextView categoryTitle = text("Category Spending Bars", 16, Color.parseColor("#1D2939"), true);
        LinearLayout.LayoutParams categoryTitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        categoryTitleParams.setMargins(0, dp(18), 0, dp(4));
        categoryTitle.setLayoutParams(categoryTitleParams);
        container.addView(categoryTitle);

        TextView categorySubtitle = text(
                "Top expense categories for the current month",
                11,
                Color.parseColor("#667085"),
                false
        );
        container.addView(categorySubtitle);

        categoryContainer = new LinearLayout(activity);
        categoryContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams categoryParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        categoryParams.setMargins(0, dp(10), 0, 0);
        categoryContainer.setLayoutParams(categoryParams);
        container.addView(categoryContainer);

        container.setAlpha(0f);
        container.setTranslationY(dp(24));
        container.animate().alpha(1f).translationY(0f).setDuration(420L).start();
        return container;
    }

    private void loadData() {
        final int version = ++requestVersion;
        new Thread(() -> {
            List<Transaction> transactions = DatabaseClient
                    .getInstance(activity.getApplicationContext())
                    .getAppDatabase()
                    .transactionDao()
                    .getAllTransactions();

            DashboardData data = analyse(transactions);
            activity.runOnUiThread(() -> {
                if (version != requestVersion || activity.isFinishing() || activity.isDestroyed()) {
                    return;
                }
                render(data);
            });
        }).start();
    }

    private DashboardData analyse(List<Transaction> transactions) {
        Calendar current = Calendar.getInstance();
        List<MonthBucket> months = new ArrayList<>();
        for (int back = 5; back >= 0; back--) {
            Calendar month = (Calendar) current.clone();
            month.add(Calendar.MONTH, -back);
            months.add(new MonthBucket(month));
        }

        Map<String, Double> categoryTotals = new LinkedHashMap<>();
        if (transactions != null) {
            for (Transaction transaction : transactions) {
                if (transaction == null) continue;
                Date date = parseDate(transaction.getDate());
                if (date == null) continue;
                Calendar transactionDate = Calendar.getInstance();
                transactionDate.setTime(date);
                double amount = Math.abs(transaction.getAmount());
                if (amount <= 0d || Double.isNaN(amount) || Double.isInfinite(amount)) continue;

                for (MonthBucket bucket : months) {
                    if (bucket.matches(transactionDate)) {
                        if ("INCOME".equalsIgnoreCase(safe(transaction.getType()))) {
                            bucket.income += amount;
                        } else if ("EXPENSE".equalsIgnoreCase(safe(transaction.getType()))) {
                            bucket.expense += amount;
                        }
                        break;
                    }
                }

                if (transactionDate.get(Calendar.YEAR) == current.get(Calendar.YEAR)
                        && transactionDate.get(Calendar.MONTH) == current.get(Calendar.MONTH)
                        && "EXPENSE".equalsIgnoreCase(safe(transaction.getType()))) {
                    String category = safe(transaction.getCategory());
                    if (category.isEmpty()) category = "Other Expense";
                    String key = findMatchingKey(categoryTotals, category);
                    categoryTotals.put(key == null ? category : key,
                            categoryTotals.getOrDefault(key == null ? category : key, 0d) + amount);
                }
            }
        }

        List<CategoryAmount> categories = new ArrayList<>();
        for (Map.Entry<String, Double> entry : categoryTotals.entrySet()) {
            categories.add(new CategoryAmount(entry.getKey(), entry.getValue()));
        }
        Collections.sort(categories, Comparator.comparingDouble(CategoryAmount::getAmount).reversed());
        if (categories.size() > 6) {
            categories = new ArrayList<>(categories.subList(0, 6));
        }
        return new DashboardData(months, categories);
    }

    private void render(DashboardData data) {
        List<InteractiveCashFlowTrendView.CashFlowPoint> points = new ArrayList<>();
        for (MonthBucket bucket : data.months) {
            points.add(new InteractiveCashFlowTrendView.CashFlowPoint(
                    bucket.label(), bucket.income, bucket.expense
            ));
        }
        chart.setData(points);
        if (!points.isEmpty()) {
            showSelectedPoint(points.get(points.size() - 1));
        }

        categoryContainer.removeAllViews();
        if (data.categories.isEmpty()) {
            categoryContainer.addView(infoCard(
                    "No category data",
                    "Add expense entries to see category spending bars.",
                    "#F7F9FC",
                    "#D9E2EC"
            ));
            return;
        }

        double maximum = data.categories.get(0).amount;
        int[] accents = {
                Color.parseColor("#0F6CBD"), Color.parseColor("#107C10"),
                Color.parseColor("#8764B8"), Color.parseColor("#D83B01"),
                Color.parseColor("#038387"), Color.parseColor("#C42B1C")
        };

        for (int index = 0; index < data.categories.size(); index++) {
            CategoryAmount item = data.categories.get(index);
            categoryContainer.addView(categoryBar(item, maximum, accents[index % accents.length], index));
        }
    }

    private View categoryBar(CategoryAmount item, double maximum, int accent, int index) {
        MaterialCardView card = card("#FFFFFF", "#E4EAF0", 16);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, 0, 0, dp(8));
        card.setLayoutParams(cardParams);

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(13), dp(11), dp(13), dp(12));

        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = text(item.category, 13, Color.parseColor("#1D2939"), true);
        row.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView amount = text(money(item.amount), 13, accent, true);
        row.addView(amount);
        content.addView(row);

        ProgressBar progress = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(1000);
        progress.setProgressTintList(android.content.res.ColorStateList.valueOf(accent));
        int target = maximum <= 0d ? 0 : (int) Math.round(item.amount / maximum * 1000d);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(8)
        );
        progressParams.setMargins(0, dp(9), 0, 0);
        progress.setLayoutParams(progressParams);
        progress.setProgress(0);
        content.addView(progress);
        card.addView(content);

        card.setAlpha(0f);
        card.setTranslationX(dp(24));
        card.animate().alpha(1f).translationX(0f).setStartDelay(index * 70L).setDuration(330L).start();
        progress.post(() -> android.animation.ObjectAnimator.ofInt(progress, "progress", 0, target)
                .setDuration(650L).start());
        return card;
    }

    private View infoCard(String title, String message, String background, String stroke) {
        MaterialCardView card = card(background, stroke, 16);
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(12), dp(14), dp(12));
        content.addView(text(title, 13, Color.parseColor("#1D2939"), true));
        TextView body = text(message, 11, Color.parseColor("#667085"), false);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        bodyParams.setMargins(0, dp(3), 0, 0);
        body.setLayoutParams(bodyParams);
        content.addView(body);
        card.addView(content);
        return card;
    }

    private void showSelectedPoint(InteractiveCashFlowTrendView.CashFlowPoint point) {
        String net = point.getNetCashFlow() >= 0d
                ? "+" + money(point.getNetCashFlow())
                : "-" + money(Math.abs(point.getNetCashFlow()));
        txtSelection.setText(point.getLabel() + " • Net cash flow " + net);
        txtSelection.setTextColor(point.getNetCashFlow() >= 0d
                ? Color.parseColor("#107C10") : Color.parseColor("#C42B1C"));
    }

    private MaterialCardView card(String background, String stroke, int radiusDp) {
        MaterialCardView card = new MaterialCardView(activity);
        card.setCardBackgroundColor(Color.parseColor(background));
        card.setStrokeColor(Color.parseColor(stroke));
        card.setStrokeWidth(dp(1));
        card.setRadius(dp(radiusDp));
        card.setCardElevation(0f);
        card.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        return card;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setLineSpacing(dp(2), 1f);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private LinearLayout findVerticalLinearLayout(View view) {
        if (view instanceof LinearLayout
                && ((LinearLayout) view).getOrientation() == LinearLayout.VERTICAL) {
            return (LinearLayout) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                LinearLayout found = findVerticalLinearLayout(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private Date parseDate(String value) {
        String clean = safe(value);
        for (String pattern : DATE_PATTERNS) {
            SimpleDateFormat formatter = new SimpleDateFormat(pattern, Locale.US);
            formatter.setLenient(false);
            ParsePosition position = new ParsePosition(0);
            Date parsed = formatter.parse(clean, position);
            if (parsed != null && position.getIndex() == clean.length()) return parsed;
        }
        return null;
    }

    private String findMatchingKey(Map<String, Double> map, String requested) {
        for (String key : map.keySet()) if (key.equalsIgnoreCase(requested)) return key;
        return null;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String money(double amount) {
        NumberFormat format = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));
        format.setMaximumFractionDigits(0);
        return format.format(Math.abs(amount));
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private static final class MonthBucket {
        final int year;
        final int month;
        double income;
        double expense;

        MonthBucket(Calendar calendar) {
            year = calendar.get(Calendar.YEAR);
            month = calendar.get(Calendar.MONTH);
        }

        boolean matches(Calendar calendar) {
            return year == calendar.get(Calendar.YEAR) && month == calendar.get(Calendar.MONTH);
        }

        String label() {
            Calendar calendar = Calendar.getInstance();
            calendar.clear();
            calendar.set(year, month, 1);
            return new SimpleDateFormat("MMM", Locale.ENGLISH).format(calendar.getTime());
        }
    }

    private static final class CategoryAmount {
        final String category;
        final double amount;
        CategoryAmount(String category, double amount) { this.category = category; this.amount = amount; }
        double getAmount() { return amount; }
    }

    private static final class DashboardData {
        final List<MonthBucket> months;
        final List<CategoryAmount> categories;
        DashboardData(List<MonthBucket> months, List<CategoryAmount> categories) {
            this.months = months;
            this.categories = categories;
        }
    }
}
