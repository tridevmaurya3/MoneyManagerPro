package com.example.moneymanagerpro.ui;

import android.app.Activity;
import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
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

import com.example.moneymanagerpro.activities.FinanceIntelligenceHubActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

/**
 * Applies lightweight UI polish to Finance Pro Suite without changing its
 * database, calculations or navigation. Dashboard placement is handled by
 * DashboardAccordionController so Finance Pro appears with Dashboard Tools.
 */
public final class FinanceProSuiteInitializer extends ContentProvider {

    private static final String COMPACT_HERO_TAG =
            "finance_pro_compact_smart_overview";

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

        // Use the standard left arrow instead of the narrow chevron glyph.
        // It renders reliably across Android fonts and screen densities.
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
