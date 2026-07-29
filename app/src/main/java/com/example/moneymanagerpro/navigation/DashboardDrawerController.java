package com.example.moneymanagerpro.navigation;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.activities.AnalyticsActivity;
import com.example.moneymanagerpro.activities.BackupActivity;
import com.example.moneymanagerpro.activities.BudgetActivity;
import com.example.moneymanagerpro.activities.CalendarActivity;
import com.example.moneymanagerpro.activities.ChartsActivity;
import com.example.moneymanagerpro.activities.CsvImportActivity;
import com.example.moneymanagerpro.activities.CreditCardActivity;
import com.example.moneymanagerpro.activities.ExportActivity;
import com.example.moneymanagerpro.activities.FinanceAdvisorActivity;
import com.example.moneymanagerpro.activities.GoalActivity;
import com.example.moneymanagerpro.activities.HelpActivity;
import com.example.moneymanagerpro.activities.InvestmentActivity;
import com.example.moneymanagerpro.activities.LoanActivity;
import com.example.moneymanagerpro.activities.ReceiptGalleryActivity;
import com.example.moneymanagerpro.activities.RecurringActivity;
import com.example.moneymanagerpro.activities.SettingsActivity;
import com.example.moneymanagerpro.activities.SubscriptionActivity;
import com.example.moneymanagerpro.activities.TransferActivity;
import com.example.moneymanagerpro.utils.BubbleTouchAnimator;

public final class DashboardDrawerController {

    private final Context context;
    private final DrawerLayout drawerLayout;
    private final LinearLayout menuContainer;

    public DashboardDrawerController(
            @NonNull Context context,
            @NonNull DrawerLayout drawerLayout,
            @NonNull LinearLayout menuContainer
    ) {
        this.context = context;
        this.drawerLayout = drawerLayout;
        this.menuContainer = menuContainer;
    }

    public void buildMenu() {
        menuContainer.removeAllViews();

        addSection("Planning & Money");
        addMenuItem("Transfer", "Move money between accounts", "↔",
                TransferActivity.class, R.color.purple);
        addMenuItem("Goals", "Track your savings targets", "◎",
                GoalActivity.class, R.color.success);
        addMenuItem("Recurring", "Regular income and expenses", "↻",
                RecurringActivity.class, R.color.orange);
        addMenuItem("Budgets", "Control category spending", "%",
                BudgetActivity.class, R.color.expense);
        addMenuItem("Loans", "Track lending and EMI", "₹",
                LoanActivity.class, R.color.pink);
        addMenuItem("Credit Cards", "Billing cycles and statements", "CC",
                CreditCardActivity.class, R.color.purple);
        addMenuItem("Investments", "Manage saved investments", "↗",
                InvestmentActivity.class, R.color.purple);

        addSection("Insights & Tracking");
        addMenuItem("Analytics", "View spending insights", "◔",
                AnalyticsActivity.class, R.color.teal);
        addMenuItem("Charts & Trends", "Visual financial reports", "▥",
                ChartsActivity.class, R.color.teal);
        addMenuItem("Smart Advisor", "Personal finance guidance", "✦",
                FinanceAdvisorActivity.class, R.color.purple);
        addMenuItem("Calendar", "Daily cash-flow view", "▦",
                CalendarActivity.class, R.color.secondary);
        addMenuItem("Bills & Plans", "Subscriptions and bills", "□",
                SubscriptionActivity.class, R.color.purple);
        addMenuItem("Bill Photos", "Saved receipt images", "▣",
                ReceiptGalleryActivity.class, R.color.expense);

        addSection("Data & App");
        addMenuItem("Export", "Create reports and files", "⇩",
                ExportActivity.class, R.color.app_text_secondary);
        addMenuItem("Backup", "Protect and restore data", "B",
                BackupActivity.class, R.color.secondary);
        addMenuItem("Import CSV", "Import transaction records", "CSV",
                CsvImportActivity.class, R.color.secondary);
        addMenuItem("Settings", "Privacy and preferences", "⚙",
                SettingsActivity.class, R.color.app_text_primary);
        addMenuItem("Help Guide", "Learn how features work", "?",
                HelpActivity.class, R.color.teal);
    }

    private void addSection(@NonNull String titleText) {
        TextView title = new TextView(context);
        title.setText(titleText);
        title.setTextColor(color(R.color.app_text_secondary));
        title.setTextSize(12);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setAllCaps(true);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(dp(8), dp(14), dp(8), dp(7));
        title.setLayoutParams(params);
        menuContainer.addView(title);
    }

    private void addMenuItem(
            @NonNull String titleText,
            @NonNull String subtitleText,
            @NonNull String iconText,
            @NonNull Class<?> activityClass,
            @ColorRes int accentColorResource
    ) {
        int accentColor = color(accentColorResource);

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(9), dp(10), dp(9));
        row.setClickable(true);
        row.setFocusable(true);
        row.setContentDescription(titleText + ". " + subtitleText);

        GradientDrawable rowBackground = new GradientDrawable();
        rowBackground.setColor(color(R.color.app_surface));
        rowBackground.setCornerRadius(dp(14));
        rowBackground.setStroke(dp(1), color(R.color.app_outline));
        row.setBackground(rowBackground);

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        rowParams.setMargins(0, 0, 0, dp(7));
        row.setLayoutParams(rowParams);

        TextView icon = new TextView(context);
        icon.setText(iconText);
        icon.setTextColor(accentColor);
        icon.setTextSize("CSV".equals(iconText) ? 9 : 17);
        icon.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        icon.setGravity(Gravity.CENTER);

        GradientDrawable iconBackground = new GradientDrawable();
        iconBackground.setColor(withAlpha(accentColor, 24));
        iconBackground.setCornerRadius(dp(11));
        icon.setBackground(iconBackground);
        row.addView(icon, new LinearLayout.LayoutParams(dp(40), dp(40)));

        LinearLayout labels = new LinearLayout(context);
        labels.setOrientation(LinearLayout.VERTICAL);

        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
        );
        labelParams.setMargins(dp(12), 0, dp(8), 0);
        labels.setLayoutParams(labelParams);

        TextView title = new TextView(context);
        title.setText(titleText);
        title.setTextColor(color(R.color.app_text_primary));
        title.setTextSize(15);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        labels.addView(title);

        TextView subtitle = new TextView(context);
        subtitle.setText(subtitleText);
        subtitle.setTextColor(color(R.color.app_text_secondary));
        subtitle.setTextSize(11);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        subtitleParams.setMargins(0, dp(2), 0, 0);
        subtitle.setLayoutParams(subtitleParams);
        labels.addView(subtitle);
        row.addView(labels);

        TextView arrow = new TextView(context);
        arrow.setText("›");
        arrow.setTextColor(color(R.color.app_text_secondary));
        arrow.setTextSize(24);
        arrow.setGravity(Gravity.CENTER);
        row.addView(arrow, new LinearLayout.LayoutParams(dp(24), dp(40)));

        BubbleTouchAnimator.apply(row);
        row.setOnClickListener(view -> {
            drawerLayout.closeDrawers();
            Intent intent = new Intent(context, activityClass);
            context.startActivity(intent);
        });

        menuContainer.addView(row);
    }

    private int color(@ColorRes int colorResource) {
        return ContextCompat.getColor(context, colorResource);
    }

    private int withAlpha(int color, int alpha) {
        return Color.argb(
                alpha,
                Color.red(color),
                Color.green(color),
                Color.blue(color)
        );
    }

    private int dp(int value) {
        return Math.round(
                value * context.getResources().getDisplayMetrics().density
        );
    }
}
