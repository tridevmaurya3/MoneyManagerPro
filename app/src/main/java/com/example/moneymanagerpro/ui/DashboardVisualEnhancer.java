package com.example.moneymanagerpro.ui;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.activities.DashboardActivity;
import com.example.moneymanagerpro.activities.NotificationAssistantActivity;
import com.google.android.material.card.MaterialCardView;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Applies dashboard visual polish and augments the runtime More Financial Tools
 * bottom sheet. The Financial Notifications launcher is injected directly into
 * the visible sheet so it remains available alongside the full tools page.
 */
public final class DashboardVisualEnhancer {

    private static final String NOTIFICATION_SECTION_TAG =
            "runtime_financial_notifications_section";

    private static final Map<Activity, Boolean> labelObservers =
            new WeakHashMap<>();

    private DashboardVisualEnhancer() {
    }

    public static void apply(@NonNull Activity activity) {
        if (!(activity instanceof DashboardActivity)) {
            return;
        }

        styleCard(activity, R.id.cardPeriodSelector, "#EEF6FF", "#B8D8F2");
        styleCard(activity, R.id.cardMonth1, "#F2FAF4", "#B9DEC3");
        styleCard(activity, R.id.cardMonth2, "#F7F2FF", "#D8C7F2");
        styleCard(activity, R.id.cardMonth3, "#FFF9E8", "#E7D59B");

        setTextColor(activity, R.id.txtSelectedPeriod, "#315F83");
        setTextColor(activity, R.id.txtMonth1Title, "#315E3D");
        setTextColor(activity, R.id.txtMonth2Title, "#654A8E");
        setTextColor(activity, R.id.txtMonth3Title, "#755D18");

        installDynamicObserver(activity);
    }

    public static void remove(@NonNull Activity activity) {
        labelObservers.remove(activity);
    }

    private static void styleCard(
            @NonNull Activity activity,
            int viewId,
            @NonNull String backgroundColor,
            @NonNull String strokeColor
    ) {
        View view = activity.findViewById(viewId);
        if (!(view instanceof MaterialCardView)) {
            return;
        }

        MaterialCardView card = (MaterialCardView) view;
        card.setCardBackgroundColor(Color.parseColor(backgroundColor));
        card.setStrokeColor(Color.parseColor(strokeColor));
        card.setStrokeWidth(dp(activity, 1));
        card.setCardElevation(0f);
    }

    private static void setTextColor(
            @NonNull Activity activity,
            int viewId,
            @NonNull String color
    ) {
        View view = activity.findViewById(viewId);
        if (view instanceof TextView) {
            ((TextView) view).setTextColor(Color.parseColor(color));
        }
    }

    private static void installDynamicObserver(@NonNull Activity activity) {
        if (Boolean.TRUE.equals(labelObservers.get(activity))) {
            return;
        }

        View decorView = activity.getWindow().getDecorView();
        ViewTreeObserver.OnGlobalLayoutListener listener = () -> {
            updateDynamicToolLabels(decorView);
            ensureNotificationLauncher(activity, decorView);
        };

        decorView.getViewTreeObserver().addOnGlobalLayoutListener(listener);
        labelObservers.put(activity, true);

        updateDynamicToolLabels(decorView);
        ensureNotificationLauncher(activity, decorView);
    }

    private static void updateDynamicToolLabels(@NonNull View view) {
        if (view instanceof TextView) {
            TextView textView = (TextView) view;
            CharSequence currentText = textView.getText();

            if (currentText != null) {
                if ("Smart Advisor".contentEquals(currentText)) {
                    textView.setText("Smart Transaction Assistant");
                } else if ("Personal finance guidance".contentEquals(currentText)) {
                    textView.setText("Offline transaction intelligence");
                }
            }
        }

        if (!(view instanceof ViewGroup)) {
            return;
        }

        ViewGroup group = (ViewGroup) view;
        for (int index = 0; index < group.getChildCount(); index++) {
            updateDynamicToolLabels(group.getChildAt(index));
        }
    }

    private static void ensureNotificationLauncher(
            @NonNull Activity activity,
            @NonNull View root
    ) {
        if (!(root instanceof ViewGroup)) {
            return;
        }

        ViewGroup rootGroup = (ViewGroup) root;
        if (rootGroup.findViewWithTag(NOTIFICATION_SECTION_TAG) != null) {
            return;
        }

        TextView dataAndAppHeading = findTextView(rootGroup, "Data & App");
        if (dataAndAppHeading == null) {
            return;
        }

        View parent = (View) dataAndAppHeading.getParent();
        if (!(parent instanceof LinearLayout)) {
            return;
        }

        View grandParent = (View) parent.getParent();
        if (!(grandParent instanceof LinearLayout)) {
            return;
        }

        LinearLayout mainLayout = (LinearLayout) grandParent;
        int insertionIndex = mainLayout.indexOfChild(parent);
        if (insertionIndex < 0) {
            return;
        }

        LinearLayout section = new LinearLayout(activity);
        section.setTag(NOTIFICATION_SECTION_TAG);
        section.setOrientation(LinearLayout.VERTICAL);

        LinearLayout.LayoutParams sectionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        sectionParams.setMargins(0, dp(activity, 16), 0, dp(activity, 8));
        section.setLayoutParams(sectionParams);

        TextView heading = new TextView(activity);
        heading.setText("SMS & Notifications");
        heading.setTextSize(16);
        heading.setTypeface(Typeface.DEFAULT_BOLD);
        heading.setTextColor(ContextCompat.getColor(activity, R.color.app_text_primary));
        section.addView(heading);

        TextView subtitle = new TextView(activity);
        subtitle.setText("Play-safe transaction detection without SMS permission");
        subtitle.setTextSize(9);
        subtitle.setTextColor(ContextCompat.getColor(activity, R.color.app_text_secondary));
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        subtitleParams.setMargins(0, dp(activity, 2), 0, dp(activity, 8));
        section.addView(subtitle, subtitleParams);

        MaterialCardView card = new MaterialCardView(activity);
        card.setCardBackgroundColor(ContextCompat.getColor(activity, R.color.info_surface));
        card.setStrokeColor(ContextCompat.getColor(activity, R.color.info_outline));
        card.setStrokeWidth(dp(activity, 1));
        card.setRadius(dp(activity, 16));
        card.setCardElevation(0f);
        card.setClickable(true);
        card.setFocusable(true);

        LinearLayout cardContent = new LinearLayout(activity);
        cardContent.setOrientation(LinearLayout.HORIZONTAL);
        cardContent.setGravity(Gravity.CENTER_VERTICAL);
        cardContent.setPadding(
                dp(activity, 14),
                dp(activity, 12),
                dp(activity, 14),
                dp(activity, 12)
        );

        TextView icon = new TextView(activity);
        icon.setText("N");
        icon.setTextSize(14);
        icon.setTypeface(Typeface.DEFAULT_BOLD);
        icon.setGravity(Gravity.CENTER);
        icon.setTextColor(ContextCompat.getColor(activity, R.color.secondary));
        cardContent.addView(icon, new LinearLayout.LayoutParams(
                dp(activity, 42),
                dp(activity, 42)
        ));

        LinearLayout labels = new LinearLayout(activity);
        labels.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(activity);
        title.setText("Financial Notifications");
        title.setTextSize(13);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(ContextCompat.getColor(activity, R.color.app_text_primary));
        labels.addView(title);

        TextView description = new TextView(activity);
        description.setText("Review bank, UPI, wallet and card alerts");
        description.setTextSize(9);
        description.setTextColor(ContextCompat.getColor(activity, R.color.app_text_secondary));
        labels.addView(description);

        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
        );
        labelParams.setMargins(dp(activity, 10), 0, 0, 0);
        cardContent.addView(labels, labelParams);

        TextView arrow = new TextView(activity);
        arrow.setText("›");
        arrow.setTextSize(22);
        arrow.setTextColor(ContextCompat.getColor(activity, R.color.app_text_secondary));
        cardContent.addView(arrow);

        card.addView(cardContent);
        card.setOnClickListener(view -> activity.startActivity(
                new Intent(activity, NotificationAssistantActivity.class)
        ));

        section.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(activity, 78)
        ));

        mainLayout.addView(section, insertionIndex);
    }

    private static TextView findTextView(
            @NonNull ViewGroup root,
            @NonNull String expectedText
    ) {
        for (int index = 0; index < root.getChildCount(); index++) {
            View child = root.getChildAt(index);

            if (child instanceof TextView
                    && expectedText.contentEquals(((TextView) child).getText())) {
                return (TextView) child;
            }

            if (child instanceof ViewGroup) {
                TextView nested = findTextView((ViewGroup) child, expectedText);
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }

    private static int dp(@NonNull Activity activity, int value) {
        return Math.round(
                value * activity.getResources().getDisplayMetrics().density
        );
    }
}
