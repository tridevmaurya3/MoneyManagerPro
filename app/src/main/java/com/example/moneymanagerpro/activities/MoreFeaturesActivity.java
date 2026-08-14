package com.example.moneymanagerpro.activities;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.ColorRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.utils.BubbleTouchAnimator;
import com.google.android.material.card.MaterialCardView;

public class MoreFeaturesActivity extends AppCompatActivity {

    private static final int CARD_HEIGHT_DP = 90;
    private LinearLayout featureContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_more_features);
        featureContainer = findViewById(R.id.featureContainer);
        buildFeatureMenu();
    }

    private void buildFeatureMenu() {
        featureContainer.removeAllViews();

        addSectionHeader("Planning & Money", "Manage transfers, goals, budgets and repayments", true);
        addFeatureRow(item("Transfer", "Move money between accounts", "↔", TransferActivity.class, R.color.purple, R.color.purple_surface, R.color.purple_outline), item("Goals", "Track your saving targets", "◎", GoalActivity.class, R.color.success, R.color.success_surface, R.color.success_outline));
        addFeatureRow(item("Recurring", "Regular income and expenses", "↻", RecurringActivity.class, R.color.orange, R.color.warning_surface, R.color.warning_outline), item("Budgets", "Control category spending", "%", BudgetActivity.class, R.color.expense, R.color.error_surface, R.color.error_outline));
        addFeatureRow(item("Loans", "Track lending and EMI", "₹", LoanActivity.class, R.color.pink, R.color.pink_surface, R.color.pink_outline), item("Credit Cards", "Billing cycles and statements", "CC", CreditCardActivity.class, R.color.purple, R.color.purple_surface, R.color.purple_outline));
        addFeatureRow(item("Investments", "Manage saved investments", "↗", InvestmentActivity.class, R.color.purple, R.color.purple_surface, R.color.purple_outline), null);

        addSectionHeader("Insights & Tracking", "Understand financial activity and upcoming payments", false);
        addFeatureRow(item("Analytics", "View spending insights", "◔", AnalyticsActivity.class, R.color.teal, R.color.teal_surface, R.color.teal_outline), item("Charts & Trends", "Visual financial reports", "▥", ChartsActivity.class, R.color.teal, R.color.teal_surface, R.color.teal_outline));
        addFeatureRow(item("Smart Advisor", "Personal finance guidance", "✦", FinanceAdvisorActivity.class, R.color.purple, R.color.purple_surface, R.color.purple_outline), item("Calendar", "Daily cash-flow view", "▦", CalendarActivity.class, R.color.secondary, R.color.info_surface, R.color.info_outline));
        addFeatureRow(item("Receipt Vault", "Saved bill and receipt photos", "▣", ReceiptGalleryActivity.class, R.color.expense, R.color.error_surface, R.color.error_outline), item("Import CSV", "Bring old finance records", "⇪", CsvImportActivity.class, R.color.secondary, R.color.info_surface, R.color.info_outline));
        addFeatureRow(item("Export", "Create CSV and PDF files", "⇩", ExportActivity.class, R.color.app_text_secondary, R.color.app_surface_soft, R.color.app_outline), null);

        addSectionHeader("Data & Security", "Protect records, review integrations and manage preferences", false);
        addFeatureRow(item("Backup", "Create and restore data", "B", BackupActivity.class, R.color.secondary, R.color.info_surface, R.color.info_outline), item("Integration Review", "Map incoming accounts and categories", "↔", SmartSmsTransactionReviewActivity.class, R.color.teal, R.color.teal_surface, R.color.teal_outline));
        addFeatureRow(item("Reconciliation", "Resolve transfers, refunds and duplicates", "R", SpecialReconciliationActivity.class, R.color.orange, R.color.warning_surface, R.color.warning_outline), item("Settings", "Theme, PIN and security", "⚙", SettingsActivity.class, R.color.app_text_primary, R.color.app_surface_muted, R.color.app_outline));
        addFeatureRow(item("Help Guide", "Learn how to use the app", "?", HelpActivity.class, R.color.teal, R.color.teal_surface, R.color.teal_outline), null);
    }

    private FeatureItem item(String title, String subtitle, String icon, Class<?> activity,
                             @ColorRes int iconColor, @ColorRes int surface, @ColorRes int outline) {
        return new FeatureItem(title, subtitle, icon, activity, iconColor, surface, outline);
    }

    private void addSectionHeader(String title, String subtitle, boolean first) {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, first ? 0 : dp(13), 0, dp(6));
        section.setLayoutParams(params);

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextColor(color(R.color.app_text_primary));
        titleView.setTextSize(18);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);

        TextView subtitleView = new TextView(this);
        subtitleView.setText(subtitle);
        subtitleView.setTextColor(color(R.color.app_text_secondary));
        subtitleView.setTextSize(10.5f);
        subtitleView.setMaxLines(2);

        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        subtitleParams.setMargins(0, dp(2), 0, 0);
        subtitleView.setLayoutParams(subtitleParams);

        section.addView(titleView);
        section.addView(subtitleView);
        featureContainer.addView(section);
    }

    private void addFeatureRow(FeatureItem first, FeatureItem second) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.TOP);
        row.setBaselineAligned(false);

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        rowParams.setMargins(0, 0, 0, dp(7));
        row.setLayoutParams(rowParams);

        MaterialCardView firstCard = createFeatureCard(first);
        LinearLayout.LayoutParams firstParams = new LinearLayout.LayoutParams(0, dp(CARD_HEIGHT_DP), 1f);
        firstParams.setMargins(0, 0, dp(5), 0);
        row.addView(firstCard, firstParams);

        if (second != null) {
            MaterialCardView secondCard = createFeatureCard(second);
            LinearLayout.LayoutParams secondParams = new LinearLayout.LayoutParams(0, dp(CARD_HEIGHT_DP), 1f);
            secondParams.setMargins(dp(5), 0, 0, 0);
            row.addView(secondCard, secondParams);
        } else {
            Space space = new Space(this);
            LinearLayout.LayoutParams spaceParams = new LinearLayout.LayoutParams(0, dp(CARD_HEIGHT_DP), 1f);
            spaceParams.setMargins(dp(5), 0, 0, 0);
            row.addView(space, spaceParams);
        }

        featureContainer.addView(row);
    }

    private MaterialCardView createFeatureCard(FeatureItem item) {
        int iconColor = color(item.iconColor);

        MaterialCardView card = new MaterialCardView(this);
        card.setCardBackgroundColor(color(item.surfaceColor));
        card.setRadius(dp(15));
        card.setCardElevation(dp(1));
        card.setStrokeColor(color(item.outlineColor));
        card.setStrokeWidth(dp(1));
        card.setClickable(true);
        card.setFocusable(true);
        card.setRippleColor(ColorStateList.valueOf(Color.argb(32, Color.red(iconColor), Color.green(iconColor), Color.blue(iconColor))));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.TOP | Gravity.START);
        content.setPadding(dp(10), dp(8), dp(9), dp(7));

        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.HORIZONTAL);
        heading.setGravity(Gravity.CENTER_VERTICAL);

        TextView icon = new TextView(this);
        icon.setText(item.iconText);
        icon.setTextColor(iconColor);
        icon.setTextSize(item.iconText.length() > 2 ? 8.5f : 13.5f);
        icon.setTypeface(Typeface.DEFAULT_BOLD);
        icon.setGravity(Gravity.CENTER);

        GradientDrawable iconBackground = new GradientDrawable();
        iconBackground.setColor(Color.argb(24, Color.red(iconColor), Color.green(iconColor), Color.blue(iconColor)));
        iconBackground.setCornerRadius(dp(10));
        icon.setBackground(iconBackground);
        heading.addView(icon, new LinearLayout.LayoutParams(dp(38), dp(38)));

        TextView title = new TextView(this);
        title.setText(item.title);
        title.setTextColor(color(R.color.app_text_primary));
        title.setTextSize(13.5f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);

        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.setMargins(dp(9), 0, 0, 0);
        heading.addView(title, titleParams);

        TextView subtitle = new TextView(this);
        subtitle.setText(item.subtitle);
        subtitle.setTextColor(color(R.color.app_text_secondary));
        subtitle.setTextSize(9.5f);
        subtitle.setMaxLines(2);
        subtitle.setEllipsize(TextUtils.TruncateAt.END);
        subtitle.setLineSpacing(0f, 1f);

        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        subtitleParams.setMargins(0, dp(5), 0, 0);
        subtitle.setLayoutParams(subtitleParams);

        content.addView(heading);
        content.addView(subtitle);
        card.addView(content);

        BubbleTouchAnimator.apply(card);
        card.setOnClickListener(v -> openFeature(item.activityClass));
        return card;
    }

    private void openFeature(Class<?> activityClass) {
        try {
            startActivity(new Intent(this, activityClass));
        } catch (Exception exception) {
            Toast.makeText(this, "This feature could not be opened", Toast.LENGTH_SHORT).show();
        }
    }

    private int color(@ColorRes int resource) {
        return ContextCompat.getColor(this, resource);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class FeatureItem {
        final String title;
        final String subtitle;
        final String iconText;
        final Class<?> activityClass;
        @ColorRes final int iconColor;
        @ColorRes final int surfaceColor;
        @ColorRes final int outlineColor;

        FeatureItem(String title, String subtitle, String iconText, Class<?> activityClass,
                    int iconColor, int surfaceColor, int outlineColor) {
            this.title = title;
            this.subtitle = subtitle;
            this.iconText = iconText;
            this.activityClass = activityClass;
            this.iconColor = iconColor;
            this.surfaceColor = surfaceColor;
            this.outlineColor = outlineColor;
        }
    }
}
