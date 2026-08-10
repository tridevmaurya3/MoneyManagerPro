package com.example.moneymanagerpro.ui;

import android.app.Activity;
import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
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
import com.example.moneymanagerpro.activities.DashboardActivity;
import com.example.moneymanagerpro.activities.FinanceIntelligenceHubActivity;
import com.example.moneymanagerpro.utils.BubbleTouchAnimator;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

/**
 * Adds a permanent Finance Pro Suite entry directly below the Dashboard period
 * selector. It does not replace or duplicate existing dashboard controllers.
 */
public final class FinanceProSuiteInitializer extends ContentProvider {

    private static final String CARD_TAG = "finance_pro_suite_dashboard_entry";

    @Override
    public boolean onCreate() {
        if (getContext() == null) return false;

        Application application = (Application) getContext().getApplicationContext();
        application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
            }

            @Override
            public void onActivityStarted(@NonNull Activity activity) {
            }

            @Override
            public void onActivityResumed(@NonNull Activity activity) {
                if (activity instanceof DashboardActivity) {
                    activity.getWindow().getDecorView().post(() -> inject(activity));
                }
            }

            @Override
            public void onActivityPaused(@NonNull Activity activity) {
            }

            @Override
            public void onActivityStopped(@NonNull Activity activity) {
            }

            @Override
            public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
            }

            @Override
            public void onActivityDestroyed(@NonNull Activity activity) {
            }
        });
        return true;
    }

    private void inject(@NonNull Activity activity) {
        if (activity.isFinishing() || activity.isDestroyed()) return;

        View existing = activity.getWindow().getDecorView().findViewWithTag(CARD_TAG);
        if (existing != null) return;

        View periodCard = activity.findViewById(R.id.cardPeriodSelector);
        if (periodCard == null || !(periodCard.getParent() instanceof ViewGroup)) return;

        ViewGroup parent = (ViewGroup) periodCard.getParent();
        MaterialCardView card = new MaterialCardView(activity);
        card.setTag(CARD_TAG);
        card.setCardBackgroundColor(Color.parseColor("#F1F7FF"));
        card.setStrokeColor(Color.parseColor("#B9D4EF"));
        card.setStrokeWidth(dp(activity, 1));
        card.setRadius(dp(activity, 18));
        card.setCardElevation(0f);
        card.setClickable(true);
        card.setFocusable(true);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, dp(activity, 10), 0, 0);
        card.setLayoutParams(cardParams);

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(activity, 14), dp(activity, 12), dp(activity, 14), dp(activity, 12));

        LinearLayout headingRow = new LinearLayout(activity);
        headingRow.setOrientation(LinearLayout.HORIZONTAL);
        headingRow.setGravity(Gravity.CENTER_VERTICAL);
        headingRow.setBaselineAligned(false);

        LinearLayout labels = new LinearLayout(activity);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setLayoutParams(new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
        ));

        TextView title = text(activity, "Finance Pro Suite", 15, "#17351F", true);
        labels.addView(title);
        TextView subtitle = text(
                activity,
                "Dashboard 2.0 • AI Insights • Analytics • Smart Budget • Accounts & Cards",
                9,
                "#617067",
                false
        );
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        subtitleParams.topMargin = dp(activity, 2);
        subtitle.setLayoutParams(subtitleParams);
        labels.addView(subtitle);
        headingRow.addView(labels);

        TextView badge = text(activity, "PRO", 9, "#0F6CBD", true);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(activity, 9), dp(activity, 5), dp(activity, 9), dp(activity, 5));
        headingRow.addView(badge, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        content.addView(headingRow);

        MaterialButton open = new MaterialButton(activity);
        open.setText("Open Smart Financial Dashboard 2.0");
        open.setTextAllCaps(false);
        open.setTextSize(10);
        open.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        open.setTextColor(Color.WHITE);
        open.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#0F6CBD")));
        open.setCornerRadius(dp(activity, 13));
        open.setInsetTop(0);
        open.setInsetBottom(0);
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(activity, 42)
        );
        buttonParams.topMargin = dp(activity, 9);
        open.setLayoutParams(buttonParams);
        BubbleTouchAnimator.apply(open);

        View.OnClickListener listener = view -> activity.startActivity(
                new Intent(activity, FinanceIntelligenceHubActivity.class)
        );
        open.setOnClickListener(listener);
        card.setOnClickListener(listener);

        content.addView(open);
        card.addView(content);

        int index = parent.indexOfChild(periodCard);
        parent.addView(card, Math.min(parent.getChildCount(), index + 1));

        card.setAlpha(0f);
        card.setTranslationY(dp(activity, 10));
        card.animate().alpha(1f).translationY(0f).setDuration(260L).start();
    }

    private TextView text(Activity activity, String value, int size, String color, boolean bold) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(Color.parseColor(color));
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection,
                        @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        return null;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        return null;
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        return null;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values,
                      @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }
}
