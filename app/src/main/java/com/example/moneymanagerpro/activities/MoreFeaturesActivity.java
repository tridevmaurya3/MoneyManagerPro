package com.example.moneymanagerpro.activities;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
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

        addSectionHeader(
                "Managing Your Money",
                "Manage transfers, goals, budgets and repayments",
                true
        );

        addFeatureRow(
                new FeatureItem(
                        "Transfer",
                        "Move money between accounts",
                        "↔",
                        TransferActivity.class,
                        R.color.purple,
                        R.color.purple_surface,
                        R.color.purple_outline
                ),
                new FeatureItem(
                        "Goals",
                        "Track your saving targets",
                        "◎",
                        GoalActivity.class,
                        R.color.success,
                        R.color.success_surface,
                        R.color.success_outline
                )
        );

        addFeatureRow(
                new FeatureItem(
                        "Recurring",
                        "Regular income and expenses",
                        "↻",
                        RecurringActivity.class,
                        R.color.orange,
                        R.color.warning_surface,
                        R.color.warning_outline
                ),
                new FeatureItem(
                        "Budgets",
                        "Control category spending",
                        "%",
                        BudgetActivity.class,
                        R.color.expense,
                        R.color.error_surface,
                        R.color.error_outline
                )
        );

        addFeatureRow(
                new FeatureItem(
                        "Loans",
                        "Track lending and EMI",
                        "₹",
                        LoanActivity.class,
                        R.color.pink,
                        R.color.pink_surface,
                        R.color.pink_outline
                ),
                new FeatureItem(
                        "Credit Cards",
                        "Billing cycles and statements",
                        "CC",
                        CreditCardActivity.class,
                        R.color.purple,
                        R.color.purple_surface,
                        R.color.purple_outline
                )
        );

        addFeatureRow(
                new FeatureItem(
                        "Investments",
                        "Manage saved investments",
                        "↗",
                        InvestmentActivity.class,
                        R.color.purple,
                        R.color.purple_surface,
                        R.color.purple_outline
                ),
                null
        );

        addSectionHeader(
                "Insights & Tracking",
                "Understand financial activity and upcoming payments",
                false
        );

        addFeatureRow(
                new FeatureItem(
                        "Analytics",
                        "View spending insights",
                        "◔",
                        AnalyticsActivity.class,
                        R.color.teal,
                        R.color.teal_surface,
                        R.color.teal_outline
                ),
                new FeatureItem(
                        "Charts & Trends",
                        "Visual financial reports",
                        "▥",
                        ChartsActivity.class,
                        R.color.teal,
                        R.color.teal_surface,
                        R.color.teal_outline
                )
        );

        addFeatureRow(
                new FeatureItem(
                        "Smart Advisor",
                        "Private finance suggestions",
                        "✦",
                        FinanceAdvisorActivity.class,
                        R.color.purple,
                        R.color.purple_surface,
                        R.color.purple_outline
                ),
                new FeatureItem(
                        "Calendar",
                        "Track money by date",
                        "▦",
                        CalendarActivity.class,
                        R.color.secondary,
                        R.color.info_surface,
                        R.color.info_outline
                )
        );

        addFeatureRow(
                new FeatureItem(
                        "Receipt Vault",
                        "Saved bill and receipt photos",
                        "▣",
                        ReceiptGalleryActivity.class,
                        R.color.expense,
                        R.color.error_surface,
                        R.color.error_outline
                ),
                new FeatureItem(
                        "Import CSV",
                        "Bring old finance records",
                        "⇪",
                        CsvImportActivity.class,
                        R.color.secondary,
                        R.color.info_surface,
                        R.color.info_outline
                )
        );

        addFeatureRow(
                new FeatureItem(
                        "Export",
                        "Create CSV and PDF files",
                        "⇩",
                        ExportActivity.class,
                        R.color.app_text_secondary,
                        R.color.app_surface_soft,
                        R.color.app_outline
                ),
                new FeatureItem(
                        "SMS Transaction Sync",
                        "Detect bank debit and credit alerts",
                        "SMS",
                        SmsTransactionActivity.class,
                        R.color.teal,
                        R.color.teal_surface,
                        R.color.teal_outline
                )
        );

        addSectionHeader(
                "Data & Security",
                "Protect records and manage app preferences",
                false
        );

        addFeatureRow(
                new FeatureItem(
                        "Backup",
                        "Create and restore data",
                        "B",
                        BackupActivity.class,
                        R.color.secondary,
                        R.color.info_surface,
                        R.color.info_outline
                ),
                new FeatureItem(
                        "Settings",
                        "Theme, PIN and security",
                        "⚙",
                        SettingsActivity.class,
                        R.color.app_text_primary,
                        R.color.app_surface_muted,
                        R.color.app_outline
                )
        );

        addFeatureRow(
                new FeatureItem(
                        "Help Guide",
                        "Learn how to use the app",
                        "?",
                        HelpActivity.class,
                        R.color.teal,
                        R.color.teal_surface,
                        R.color.teal_outline
                ),
                null
        );
    }

    private void addSectionHeader(
            String title,
            String subtitle,
            boolean firstSection
    ) {
        LinearLayout sectionLayout = new LinearLayout(this);
        sectionLayout.setOrientation(LinearLayout.VERTICAL);

        LinearLayout.LayoutParams sectionParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        sectionParams.setMargins(
                0,
                firstSection ? 0 : dp(14),
                0,
                dp(7)
        );

        sectionLayout.setLayoutParams(sectionParams);

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextColor(getColorValue(R.color.app_text_primary));
        titleView.setTextSize(16);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);

        TextView subtitleView = new TextView(this);
        subtitleView.setText(subtitle);
        subtitleView.setTextColor(getColorValue(R.color.app_text_secondary));
        subtitleView.setTextSize(9);
        subtitleView.setMaxLines(2);

        LinearLayout.LayoutParams subtitleParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        subtitleParams.setMargins(0, dp(2), 0, 0);
        subtitleView.setLayoutParams(subtitleParams);

        sectionLayout.addView(titleView);
        sectionLayout.addView(subtitleView);

        featureContainer.addView(sectionLayout);
    }

    private void addFeatureRow(
            FeatureItem firstFeature,
            FeatureItem secondFeature
    ) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.TOP);
        row.setBaselineAligned(false);

        LinearLayout.LayoutParams rowParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        rowParams.setMargins(0, 0, 0, dp(6));
        row.setLayoutParams(rowParams);

        MaterialCardView firstCard = createFeatureCard(firstFeature);
        LinearLayout.LayoutParams firstParams =
                new LinearLayout.LayoutParams(
                        0,
                        dp(90),
                        1f
                );
        firstParams.setMargins(0, 0, dp(4), 0);
        firstCard.setLayoutParams(firstParams);
        row.addView(firstCard);

        if (secondFeature != null) {
            MaterialCardView secondCard = createFeatureCard(secondFeature);
            LinearLayout.LayoutParams secondParams =
                    new LinearLayout.LayoutParams(
                            0,
                            dp(90),
                            1f
                    );
            secondParams.setMargins(dp(4), 0, 0, 0);
            secondCard.setLayoutParams(secondParams);
            row.addView(secondCard);
        } else {
            Space space = new Space(this);
            LinearLayout.LayoutParams spaceParams =
                    new LinearLayout.LayoutParams(
                            0,
                            dp(90),
                            1f
                    );
            spaceParams.setMargins(dp(4), 0, 0, 0);
            space.setLayoutParams(spaceParams);
            row.addView(space);
        }

        featureContainer.addView(row);
    }

    private MaterialCardView createFeatureCard(FeatureItem item) {
        int iconColor = getColorValue(item.iconColorResource);
        int surfaceColor = getColorValue(item.surfaceColorResource);
        int outlineColor = getColorValue(item.outlineColorResource);

        MaterialCardView card = new MaterialCardView(this);
        card.setCardBackgroundColor(surfaceColor);
        card.setRadius(dp(14));
        card.setCardElevation(0);
        card.setStrokeColor(outlineColor);
        card.setStrokeWidth(dp(1));
        card.setClickable(true);
        card.setFocusable(true);
        card.setRippleColor(
                ColorStateList.valueOf(createRippleColor(iconColor))
        );

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.TOP | Gravity.START);
        content.setPadding(dp(10), dp(8), dp(10), dp(8));

        TextView iconView = new TextView(this);
        iconView.setText(item.iconText);
        iconView.setTextColor(iconColor);
        iconView.setTextSize(13);
        iconView.setTypeface(Typeface.DEFAULT_BOLD);
        iconView.setGravity(Gravity.CENTER);

        GradientDrawable iconBackground = new GradientDrawable();
        iconBackground.setShape(GradientDrawable.RECTANGLE);
        iconBackground.setColor(createIconBackgroundColor(iconColor));
        iconBackground.setCornerRadius(dp(10));
        iconView.setBackground(iconBackground);

        LinearLayout.LayoutParams iconParams =
                new LinearLayout.LayoutParams(
                        dp(28),
                        dp(28)
                );
        iconView.setLayoutParams(iconParams);

        TextView titleView = new TextView(this);
        titleView.setText(item.title);
        titleView.setTextColor(getColorValue(R.color.app_text_primary));
        titleView.setTextSize(11.5f);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setMaxLines(1);
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);

        LinearLayout.LayoutParams titleParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        titleParams.setMargins(0, dp(6), 0, 0);
        titleView.setLayoutParams(titleParams);

        TextView subtitleView = new TextView(this);
        subtitleView.setText(item.subtitle);
        subtitleView.setTextColor(getColorValue(R.color.app_text_secondary));
        subtitleView.setTextSize(8.2f);
        subtitleView.setMaxLines(2);
        subtitleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        subtitleView.setLineSpacing(0f, 1f);

        LinearLayout.LayoutParams subtitleParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0,
                        1f
                );
        subtitleParams.setMargins(0, dp(1), 0, 0);
        subtitleView.setLayoutParams(subtitleParams);

        content.addView(iconView);
        content.addView(titleView);
        content.addView(subtitleView);

        card.addView(content);

        BubbleTouchAnimator.apply(card);

        card.setOnClickListener(v -> openFeature(item.activityClass));

        return card;
    }

    private void openFeature(Class<?> activityClass) {
        try {
            startActivity(new Intent(MoreFeaturesActivity.this, activityClass));
        } catch (Exception exception) {
            Toast.makeText(
                    this,
                    "This feature could not be opened",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    private int getColorValue(@ColorRes int colorResource) {
        return ContextCompat.getColor(this, colorResource);
    }

    private int createIconBackgroundColor(int baseColor) {
        return Color.argb(
                24,
                Color.red(baseColor),
                Color.green(baseColor),
                Color.blue(baseColor)
        );
    }

    private int createRippleColor(int baseColor) {
        return Color.argb(
                38,
                Color.red(baseColor),
                Color.green(baseColor),
                Color.blue(baseColor)
        );
    }

    private int dp(int value) {
        return Math.round(
                value * getResources().getDisplayMetrics().density
        );
    }

    private static class FeatureItem {
        private final String title;
        private final String subtitle;
        private final String iconText;
        private final Class<?> activityClass;

        @ColorRes
        private final int iconColorResource;

        @ColorRes
        private final int surfaceColorResource;

        @ColorRes
        private final int outlineColorResource;

        private FeatureItem(
                String title,
                String subtitle,
                String iconText,
                Class<?> activityClass,
                @ColorRes int iconColorResource,
                @ColorRes int surfaceColorResource,
                @ColorRes int outlineColorResource
        ) {
            this.title = title;
            this.subtitle = subtitle;
            this.iconText = iconText;
            this.activityClass = activityClass;
            this.iconColorResource = iconColorResource;
            this.surfaceColorResource = surfaceColorResource;
            this.outlineColorResource = outlineColorResource;
        }
    }
}
