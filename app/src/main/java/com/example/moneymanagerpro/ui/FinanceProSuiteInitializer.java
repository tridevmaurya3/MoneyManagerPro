package com.example.moneymanagerpro.ui;

import android.app.Activity;
import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.TridevIntegrationContract;
import com.example.moneymanagerpro.TridevIntegrationHealthManager;
import com.example.moneymanagerpro.activities.DashboardActivity;
import com.example.moneymanagerpro.activities.FinanceIntelligenceHubActivity;
import com.example.moneymanagerpro.activities.SpecialReconciliationActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

/**
 * Applies lightweight UI polish to Finance Pro Suite and keeps the most useful
 * integration action visible on the main Dashboard.
 */
public final class FinanceProSuiteInitializer extends ContentProvider {

    private static final String COMPACT_HERO_TAG =
            "finance_pro_compact_smart_overview";
    private static final String RECON_CARD_TAG =
            "dashboard_reconciliation_center_v1";
    private static final String RECON_STATUS_TAG =
            "dashboard_reconciliation_status_v1";
    private static final String RECON_REVIEW_TAG =
            "dashboard_reconciliation_review_v1";
    private static final String RECON_MATCHED_TAG =
            "dashboard_reconciliation_matched_v1";
    private static final String RECON_PENDING_TAG =
            "dashboard_reconciliation_pending_v1";

    @Override
    public boolean onCreate() {
        if (getContext() == null) {
            return false;
        }

        Application application =
                (Application) getContext().getApplicationContext();

        application.registerActivityLifecycleCallbacks(
                new Application.ActivityLifecycleCallbacks() {
                    @Override
                    public void onActivityCreated(
                            @NonNull Activity activity,
                            @Nullable Bundle savedInstanceState
                    ) {
                    }

                    @Override
                    public void onActivityStarted(
                            @NonNull Activity activity
                    ) {
                    }

                    @Override
                    public void onActivityResumed(
                            @NonNull Activity activity
                    ) {
                        if (activity instanceof FinanceIntelligenceHubActivity) {
                            activity.getWindow()
                                    .getDecorView()
                                    .post(() -> polishFinanceProPage(activity));
                        }

                        if (activity instanceof DashboardActivity) {
                            activity.getWindow()
                                    .getDecorView()
                                    .post(() -> ensureDashboardReconciliationCard(activity));
                        }
                    }

                    @Override
                    public void onActivityPaused(
                            @NonNull Activity activity
                    ) {
                    }

                    @Override
                    public void onActivityStopped(
                            @NonNull Activity activity
                    ) {
                    }

                    @Override
                    public void onActivitySaveInstanceState(
                            @NonNull Activity activity,
                            @NonNull Bundle outState
                    ) {
                    }

                    @Override
                    public void onActivityDestroyed(
                            @NonNull Activity activity
                    ) {
                    }
                }
        );

        return true;
    }

    private void ensureDashboardReconciliationCard(@NonNull Activity activity) {
        if (activity.isFinishing() || activity.isDestroyed()) return;

        View existing = findTaggedView(
                activity.findViewById(android.R.id.content),
                RECON_CARD_TAG);
        if (existing instanceof MaterialCardView) {
            loadDashboardReconciliationSummary(activity, (MaterialCardView) existing);
            return;
        }

        View explore = activity.findViewById(R.id.btnMoreFeatures);
        if (explore == null || !(explore.getParent() instanceof ViewGroup)) return;
        ViewGroup moreContent = (ViewGroup) explore.getParent();
        if (!(moreContent.getParent() instanceof MaterialCardView)) return;
        MaterialCardView moreCard = (MaterialCardView) moreContent.getParent();
        if (!(moreCard.getParent() instanceof LinearLayout)) return;

        LinearLayout dashboardColumn = (LinearLayout) moreCard.getParent();
        int insertIndex = dashboardColumn.indexOfChild(moreCard);
        if (insertIndex < 0) return;

        MaterialCardView card = buildReconciliationCard(activity);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(activity, 14);
        dashboardColumn.addView(card, insertIndex, params);
        loadDashboardReconciliationSummary(activity, card);
    }

    @NonNull
    private MaterialCardView buildReconciliationCard(@NonNull Activity activity) {
        MaterialCardView card = new MaterialCardView(activity);
        card.setTag(RECON_CARD_TAG);
        card.setRadius(dp(activity, 16));
        card.setCardElevation(dp(activity, 1));
        card.setCardBackgroundColor(Color.parseColor("#EEF6FD"));
        card.setStrokeColor(Color.parseColor("#B8D8F2"));
        card.setStrokeWidth(dp(activity, 1));
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(v -> openReconciliation(activity));

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.HORIZONTAL);
        content.setGravity(Gravity.CENTER_VERTICAL);
        content.setPadding(
                dp(activity, 12),
                dp(activity, 10),
                dp(activity, 10),
                dp(activity, 10));

        MaterialCardView iconTile = new MaterialCardView(activity);
        iconTile.setRadius(dp(activity, 12));
        iconTile.setCardElevation(0f);
        iconTile.setCardBackgroundColor(Color.parseColor("#DCECF9"));

        TextView icon = new TextView(activity);
        icon.setText("↔");
        icon.setTextSize(19f);
        icon.setTextColor(Color.parseColor("#0F6CBD"));
        icon.setGravity(Gravity.CENTER);
        icon.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        iconTile.addView(icon, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                dp(activity, 42),
                dp(activity, 42));
        content.addView(iconTile, iconParams);

        LinearLayout textArea = new LinearLayout(activity);
        textArea.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(activity);
        title.setText("Reconciliation Center");
        title.setTextColor(Color.parseColor("#234F73"));
        title.setTextSize(13f);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        textArea.addView(title);

        TextView status = new TextView(activity);
        status.setTag(RECON_STATUS_TAG);
        status.setText("SmartSMS history is match-only • no duplicate auto-post");
        status.setTextColor(Color.parseColor("#60778A"));
        status.setTextSize(9.5f);
        status.setMaxLines(2);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = dp(activity, 2);
        textArea.addView(status, statusParams);

        LinearLayout chips = new LinearLayout(activity);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setGravity(Gravity.CENTER_VERTICAL);

        TextView review = compactChip(activity, "Review 0", "#FFF4E5", "#A15A00");
        review.setTag(RECON_REVIEW_TAG);
        chips.addView(review);

        TextView matched = compactChip(activity, "Matched 0", "#EFFAF3", "#107C10");
        matched.setTag(RECON_MATCHED_TAG);
        LinearLayout.LayoutParams matchedParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        matchedParams.leftMargin = dp(activity, 5);
        chips.addView(matched, matchedParams);

        TextView pending = compactChip(activity, "Pending 0", "#F3F0FF", "#6B3FA0");
        pending.setTag(RECON_PENDING_TAG);
        LinearLayout.LayoutParams pendingParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        pendingParams.leftMargin = dp(activity, 5);
        chips.addView(pending, pendingParams);

        LinearLayout.LayoutParams chipsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        chipsParams.topMargin = dp(activity, 6);
        textArea.addView(chips, chipsParams);

        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f);
        textParams.leftMargin = dp(activity, 10);
        textParams.rightMargin = dp(activity, 8);
        content.addView(textArea, textParams);

        MaterialButton reviewButton = new MaterialButton(activity);
        reviewButton.setText("Review");
        reviewButton.setTextSize(10.5f);
        reviewButton.setTextColor(Color.parseColor("#0F6CBD"));
        reviewButton.setAllCaps(false);
        reviewButton.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        reviewButton.setMinWidth(0);
        reviewButton.setMinHeight(0);
        reviewButton.setInsetTop(0);
        reviewButton.setInsetBottom(0);
        reviewButton.setPadding(dp(activity, 8), 0, dp(activity, 8), 0);
        reviewButton.setCornerRadius(dp(activity, 11));
        reviewButton.setBackgroundTintList(
                ColorStateList.valueOf(Color.parseColor("#F7FBFF")));
        reviewButton.setStrokeColor(
                ColorStateList.valueOf(Color.parseColor("#B8D8F2")));
        reviewButton.setStrokeWidth(dp(activity, 1));
        reviewButton.setOnClickListener(v -> openReconciliation(activity));
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                dp(activity, 72),
                dp(activity, 36));
        content.addView(reviewButton, buttonParams);

        card.addView(content);
        return card;
    }

    @NonNull
    private TextView compactChip(
            @NonNull Activity activity,
            @NonNull String text,
            @NonNull String background,
            @NonNull String foreground) {
        TextView chip = new TextView(activity);
        chip.setText(text);
        chip.setTextSize(8.5f);
        chip.setTextColor(Color.parseColor(foreground));
        chip.setGravity(Gravity.CENTER);
        chip.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        chip.setPadding(
                dp(activity, 6),
                dp(activity, 3),
                dp(activity, 6),
                dp(activity, 3));
        android.graphics.drawable.GradientDrawable shape =
                new android.graphics.drawable.GradientDrawable();
        shape.setColor(Color.parseColor(background));
        shape.setCornerRadius(dp(activity, 12));
        chip.setBackground(shape);
        return chip;
    }

    private void loadDashboardReconciliationSummary(
            @NonNull Activity activity,
            @NonNull MaterialCardView card) {
        new Thread(() -> {
            TridevIntegrationHealthManager.Snapshot snapshot = null;
            try {
                snapshot = new TridevIntegrationHealthManager(
                        activity.getApplicationContext()).loadSnapshot();
            } catch (RuntimeException ignored) {
            }

            final TridevIntegrationHealthManager.Snapshot result = snapshot;
            activity.runOnUiThread(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                TextView status = findTaggedText(card, RECON_STATUS_TAG);
                TextView review = findTaggedText(card, RECON_REVIEW_TAG);
                TextView matched = findTaggedText(card, RECON_MATCHED_TAG);
                TextView pending = findTaggedText(card, RECON_PENDING_TAG);
                if (status == null || review == null || matched == null || pending == null) return;

                if (result == null) {
                    status.setText("Reconciliation status is temporarily unavailable");
                    return;
                }

                TridevIntegrationHealthManager.AppHealth smartSms = null;
                for (TridevIntegrationHealthManager.AppHealth app : result.apps) {
                    if (app != null
                            && TridevIntegrationContract.APP_SMART_SMS.equals(app.appId)) {
                        smartSms = app;
                        break;
                    }
                }

                if (smartSms == null) {
                    status.setText("SmartSMS integration has no activity yet");
                    review.setText("Review 0");
                    matched.setText("Matched 0");
                    pending.setText("Pending 0");
                    return;
                }

                review.setText("Review " + smartSms.reviewCount);
                matched.setText("Matched " + smartSms.syncedCount);
                pending.setText("Pending " + (smartSms.pendingCount + smartSms.failedCount));

                if (smartSms.reviewCount > 0) {
                    status.setText(smartSms.reviewCount
                            + " historical/ambiguous match"
                            + (smartSms.reviewCount == 1 ? " needs" : "es need")
                            + " review • history stays duplicate-safe");
                } else if (smartSms.readiness
                        == TridevIntegrationHealthManager.Readiness.NOT_INSTALLED) {
                    status.setText("SmartSMSPro is not available on this device");
                } else if (smartSms.readiness
                        == TridevIntegrationHealthManager.Readiness.ACTION_REQUIRED) {
                    status.setText("SmartSMS needs attention before automatic finance sync");
                } else {
                    status.setText("SmartSMS history is match-only • future SMS can auto-sync");
                }
            });
        }, "DashboardReconciliationSummary").start();
    }

    private void openReconciliation(@NonNull Activity activity) {
        try {
            activity.startActivity(new Intent(activity, SpecialReconciliationActivity.class));
        } catch (RuntimeException ignored) {
        }
    }

    @Nullable
    private View findTaggedView(@Nullable View view, @NonNull String tag) {
        if (view == null) return null;
        if (tag.equals(view.getTag())) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                View found = findTaggedView(group.getChildAt(index), tag);
                if (found != null) return found;
            }
        }
        return null;
    }

    @Nullable
    private TextView findTaggedText(@Nullable View view, @NonNull String tag) {
        View found = findTaggedView(view, tag);
        return found instanceof TextView ? (TextView) found : null;
    }

    private void polishFinanceProPage(
            @NonNull Activity activity
    ) {
        if (activity.isFinishing() || activity.isDestroyed()) {
            return;
        }

        View root = activity.findViewById(android.R.id.content);
        if (!(root instanceof ViewGroup)) {
            return;
        }

        fixBackButton(
                activity,
                (ViewGroup) root
        );

        compactSmartOverview(
                activity,
                (ViewGroup) root
        );
    }

    private void fixBackButton(
            @NonNull Activity activity,
            @NonNull ViewGroup root
    ) {
        MaterialButton backButton =
                findBackButton(root);

        if (backButton == null) {
            return;
        }

        backButton.setText("←");
        backButton.setAllCaps(false);
        backButton.setTextSize(22);
        backButton.setTextColor(
                Color.parseColor("#17351F")
        );
        backButton.setGravity(Gravity.CENTER);
        backButton.setPadding(0, 0, 0, 0);
        backButton.setMinWidth(0);
        backButton.setMinHeight(0);
        backButton.setInsetTop(0);
        backButton.setInsetBottom(0);
        backButton.setBackgroundTintList(
                ColorStateList.valueOf(
                        Color.parseColor("#FFFFFF")
                )
        );
        backButton.setStrokeColor(
                ColorStateList.valueOf(
                        Color.parseColor("#C9D7CD")
                )
        );
        backButton.setStrokeWidth(dp(activity, 1));
        backButton.setCornerRadius(dp(activity, 13));

        ViewGroup.LayoutParams rawParams =
                backButton.getLayoutParams();

        if (rawParams instanceof LinearLayout.LayoutParams) {
            LinearLayout.LayoutParams params =
                    (LinearLayout.LayoutParams) rawParams;
            params.width = dp(activity, 42);
            params.height = dp(activity, 42);
            backButton.setLayoutParams(params);
        }
    }

    @Nullable
    private MaterialButton findBackButton(
            @NonNull View view
    ) {
        if (view instanceof MaterialButton) {
            CharSequence description =
                    view.getContentDescription();

            if (description != null
                    && "Back".equalsIgnoreCase(
                    description.toString().trim()
            )) {
                return (MaterialButton) view;
            }
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;

            for (int index = 0;
                 index < group.getChildCount();
                 index++) {

                MaterialButton found =
                        findBackButton(
                                group.getChildAt(index)
                        );

                if (found != null) {
                    return found;
                }
            }
        }

        return null;
    }

    private void compactSmartOverview(
            @NonNull Activity activity,
            @NonNull ViewGroup root
    ) {
        TextView heading =
                findTextView(
                        root,
                        "Smart Financial Dashboard 2.0"
                );

        if (heading == null
                || !(heading.getParent() instanceof ViewGroup)) {
            return;
        }

        ViewGroup parent =
                (ViewGroup) heading.getParent();

        int headingIndex =
                parent.indexOfChild(heading);

        MaterialCardView hero = null;

        for (int index = headingIndex + 1;
             index < parent.getChildCount();
             index++) {

            View candidate =
                    parent.getChildAt(index);

            if (candidate instanceof MaterialCardView) {
                hero = (MaterialCardView) candidate;
                break;
            }

            if (candidate instanceof TextView) {
                String text =
                        ((TextView) candidate)
                                .getText()
                                .toString();

                if (text.startsWith("AI Financial Insights")) {
                    break;
                }
            }
        }

        if (hero == null
                || COMPACT_HERO_TAG.equals(hero.getTag())
                || hero.getChildCount() == 0
                || !(hero.getChildAt(0) instanceof LinearLayout)) {
            return;
        }

        LinearLayout heroContent =
                (LinearLayout) hero.getChildAt(0);

        List<TextView> metrics =
                collectMetricViews(heroContent);

        if (metrics.size() != 6) {
            return;
        }

        hero.setTag(COMPACT_HERO_TAG);
        hero.setRadius(dp(activity, 16));
        heroContent.removeAllViews();
        heroContent.setPadding(
                dp(activity, 8),
                dp(activity, 8),
                dp(activity, 8),
                dp(activity, 8)
        );

        LinearLayout firstRow =
                compactMetricRow(activity);
        LinearLayout secondRow =
                compactMetricRow(activity);

        for (int index = 0;
             index < metrics.size();
             index++) {

            TextView metric =
                    metrics.get(index);

            metric.setMinHeight(0);
            metric.setGravity(Gravity.CENTER);
            metric.setTextSize(9.5f);
            metric.setPadding(
                    dp(activity, 2),
                    dp(activity, 3),
                    dp(activity, 2),
                    dp(activity, 3)
            );
            metric.setMaxLines(3);

            LinearLayout.LayoutParams metricParams =
                    new LinearLayout.LayoutParams(
                            0,
                            dp(activity, 54),
                            1f
                    );
            metricParams.setMargins(
                    dp(activity, 2),
                    0,
                    dp(activity, 2),
                    0
            );
            metric.setLayoutParams(metricParams);

            if (index < 3) {
                firstRow.addView(metric);
            } else {
                secondRow.addView(metric);
            }
        }

        heroContent.addView(firstRow);

        LinearLayout.LayoutParams secondRowParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        secondRowParams.topMargin =
                dp(activity, 4);
        secondRow.setLayoutParams(secondRowParams);
        heroContent.addView(secondRow);
    }

    @NonNull
    private List<TextView> collectMetricViews(
            @NonNull LinearLayout heroContent
    ) {
        List<TextView> metrics =
                new ArrayList<>();

        for (int rowIndex = 0;
             rowIndex < heroContent.getChildCount();
             rowIndex++) {

            View rowView =
                    heroContent.getChildAt(rowIndex);

            if (!(rowView instanceof LinearLayout)) {
                continue;
            }

            LinearLayout row =
                    (LinearLayout) rowView;

            for (int metricIndex = 0;
                 metricIndex < row.getChildCount();
                 metricIndex++) {

                View metricView =
                        row.getChildAt(metricIndex);

                if (metricView instanceof TextView) {
                    metrics.add(
                            (TextView) metricView
                    );
                }
            }
        }

        for (TextView metric : metrics) {
            if (metric.getParent() instanceof ViewGroup) {
                ((ViewGroup) metric.getParent())
                        .removeView(metric);
            }
        }

        return metrics;
    }

    @NonNull
    private LinearLayout compactMetricRow(
            @NonNull Activity activity
    ) {
        LinearLayout row =
                new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBaselineAligned(false);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setLayoutParams(
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );
        return row;
    }

    @Nullable
    private TextView findTextView(
            @NonNull View view,
            @NonNull String expectedText
    ) {
        if (view instanceof TextView) {
            CharSequence value =
                    ((TextView) view).getText();

            if (value != null
                    && expectedText.equals(
                    value.toString().trim()
            )) {
                return (TextView) view;
            }
        }

        if (view instanceof ViewGroup) {
            ViewGroup group =
                    (ViewGroup) view;

            for (int index = 0;
                 index < group.getChildCount();
                 index++) {

                TextView found =
                        findTextView(
                                group.getChildAt(index),
                                expectedText
                        );

                if (found != null) {
                    return found;
                }
            }
        }

        return null;
    }

    private int dp(
            @NonNull Activity activity,
            int value
    ) {
        return Math.round(
                value
                        * activity
                        .getResources()
                        .getDisplayMetrics()
                        .density
        );
    }

    @Nullable
    @Override
    public Cursor query(
            @NonNull Uri uri,
            @Nullable String[] projection,
            @Nullable String selection,
            @Nullable String[] selectionArgs,
            @Nullable String sortOrder
    ) {
        return null;
    }

    @Nullable
    @Override
    public String getType(
            @NonNull Uri uri
    ) {
        return null;
    }

    @Nullable
    @Override
    public Uri insert(
            @NonNull Uri uri,
            @Nullable ContentValues values
    ) {
        return null;
    }

    @Override
    public int delete(
            @NonNull Uri uri,
            @Nullable String selection,
            @Nullable String[] selectionArgs
    ) {
        return 0;
    }

    @Override
    public int update(
            @NonNull Uri uri,
            @Nullable ContentValues values,
            @Nullable String selection,
            @Nullable String[] selectionArgs
    ) {
        return 0;
    }
}
