package com.example.moneymanagerpro.notification;

import android.app.Activity;
import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.activities.MoreFeaturesActivity;
import com.example.moneymanagerpro.activities.NotificationAssistantActivity;
import com.google.android.material.card.MaterialCardView;

/** Adds a discoverable launcher without changing the existing More Tools layout contract. */
public final class NotificationAssistantInitializer extends ContentProvider {

    private static final String CARD_TAG = "play_safe_notification_assistant_card";

    @Override
    public boolean onCreate() {
        if (getContext() == null) return false;
        Application application = (Application) getContext().getApplicationContext();
        application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle state) { }
            @Override public void onActivityStarted(@NonNull Activity activity) { }
            @Override public void onActivityResumed(@NonNull Activity activity) {
                if (activity instanceof MoreFeaturesActivity) {
                    activity.getWindow().getDecorView().post(() -> addLauncher(activity));
                }
            }
            @Override public void onActivityPaused(@NonNull Activity activity) { }
            @Override public void onActivityStopped(@NonNull Activity activity) { }
            @Override public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) { }
            @Override public void onActivityDestroyed(@NonNull Activity activity) { }
        });
        return true;
    }

    private void addLauncher(@NonNull Activity activity) {
        LinearLayout container = activity.findViewById(R.id.featureContainer);
        if (container == null || container.findViewWithTag(CARD_TAG) != null) return;

        TextView heading = new TextView(activity);
        heading.setText("SMS & Notifications");
        heading.setTextSize(16);
        heading.setTypeface(Typeface.DEFAULT_BOLD);
        heading.setTextColor(ContextCompat.getColor(activity, R.color.app_text_primary));
        LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        headingParams.setMargins(0, dp(activity, 14), 0, dp(activity, 7));
        container.addView(heading, headingParams);

        MaterialCardView card = new MaterialCardView(activity);
        card.setTag(CARD_TAG);
        card.setCardBackgroundColor(ContextCompat.getColor(activity, R.color.info_surface));
        card.setStrokeColor(ContextCompat.getColor(activity, R.color.info_outline));
        card.setStrokeWidth(dp(activity, 1));
        card.setRadius(dp(activity, 14));
        card.setCardElevation(0f);
        card.setClickable(true);
        card.setFocusable(true);

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.HORIZONTAL);
        content.setGravity(Gravity.CENTER_VERTICAL);
        content.setPadding(dp(activity, 14), dp(activity, 12), dp(activity, 14), dp(activity, 12));

        TextView icon = new TextView(activity);
        icon.setText("N");
        icon.setTextSize(14);
        icon.setTypeface(Typeface.DEFAULT_BOLD);
        icon.setGravity(Gravity.CENTER);
        icon.setTextColor(ContextCompat.getColor(activity, R.color.secondary));
        content.addView(icon, new LinearLayout.LayoutParams(dp(activity, 38), dp(activity, 38)));

        LinearLayout labels = new LinearLayout(activity);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(activity);
        title.setText("Financial Notifications");
        title.setTextSize(13);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(ContextCompat.getColor(activity, R.color.app_text_primary));
        TextView subtitle = new TextView(activity);
        subtitle.setText("Play-safe transaction detection — no SMS permission");
        subtitle.setTextSize(9);
        subtitle.setTextColor(ContextCompat.getColor(activity, R.color.app_text_secondary));
        labels.addView(title);
        labels.addView(subtitle);
        LinearLayout.LayoutParams labelsParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        labelsParams.setMargins(dp(activity, 10), 0, 0, 0);
        content.addView(labels, labelsParams);

        card.addView(content);
        card.setOnClickListener(v -> activity.startActivity(
                new Intent(activity, NotificationAssistantActivity.class)
        ));

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(activity, 76)
        );
        cardParams.setMargins(0, 0, 0, dp(activity, 8));
        container.addView(card, cardParams);
    }

    private int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    @Nullable @Override public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection, @Nullable String[] args, @Nullable String order) { return null; }
    @Nullable @Override public String getType(@NonNull Uri uri) { return null; }
    @Nullable @Override public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) { return null; }
    @Override public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] args) { return 0; }
    @Override public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection, @Nullable String[] args) { return 0; }
}
