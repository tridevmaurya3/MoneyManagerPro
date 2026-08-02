package com.example.moneymanagerpro.ui;

import android.app.Activity;
import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.method.PasswordTransformationMethod;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.activities.DashboardActivity;
import com.example.moneymanagerpro.activities.NotificationAssistantActivity;
import com.example.moneymanagerpro.activities.PinActivity;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;

import java.util.WeakHashMap;

/**
 * Runtime UI safety initializer.
 *
 * 1. Adds Financial Notifications to the actual Dashboard More Financial Tools
 *    bottom sheet even when that sheet is built dynamically inside DashboardActivity.
 * 2. Forces PIN fields to use password masking on every supported device.
 */
public final class NotificationAndPinUiInitializer extends ContentProvider {

    private static final int TAG_FINANCIAL_NOTIFICATION_CARD =
            0x4D4D504E;

    private static final WeakHashMap<Activity, Boolean> observedActivities =
            new WeakHashMap<>();

    @Override
    public boolean onCreate() {
        Context context = getContext();

        if (context == null) {
            return true;
        }

        Application application =
                (Application) context.getApplicationContext();

        application.registerActivityLifecycleCallbacks(
                new Application.ActivityLifecycleCallbacks() {
                    @Override
                    public void onActivityCreated(
                            @NonNull Activity activity,
                            @Nullable Bundle savedInstanceState
                    ) {
                        installForActivity(activity);
                    }

                    @Override
                    public void onActivityResumed(
                            @NonNull Activity activity
                    ) {
                        applyPinMasking(activity);
                        installForActivity(activity);
                    }

                    @Override public void onActivityStarted(@NonNull Activity activity) { }
                    @Override public void onActivityPaused(@NonNull Activity activity) { }
                    @Override public void onActivityStopped(@NonNull Activity activity) { }
                    @Override public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) { }
                    @Override public void onActivityDestroyed(@NonNull Activity activity) {
                        observedActivities.remove(activity);
                    }
                }
        );

        return true;
    }

    private static void installForActivity(
            @NonNull Activity activity
    ) {
        applyPinMasking(activity);

        if (!(activity instanceof DashboardActivity)
                || Boolean.TRUE.equals(observedActivities.get(activity))) {
            return;
        }

        View decorView = activity.getWindow().getDecorView();

        ViewTreeObserver.OnGlobalLayoutListener listener =
                () -> injectNotificationSection(activity, decorView);

        decorView.getViewTreeObserver().addOnGlobalLayoutListener(listener);
        observedActivities.put(activity, true);
        injectNotificationSection(activity, decorView);
    }

    private static void applyPinMasking(
            @NonNull Activity activity
    ) {
        if (!(activity instanceof PinActivity)) {
            return;
        }

        TextInputEditText pin = activity.findViewById(R.id.etPin);
        TextInputEditText confirmPin = activity.findViewById(R.id.etConfirmPin);

        mask(pin);
        mask(confirmPin);
    }

    private static void mask(
            @Nullable TextInputEditText input
    ) {
        if (input == null) {
            return;
        }

        int selection = input.getSelectionStart();
        input.setTransformationMethod(
                PasswordTransformationMethod.getInstance()
        );

        if (selection >= 0 && input.getText() != null) {
            input.setSelection(
                    Math.min(selection, input.getText().length())
            );
        }
    }

    private static void injectNotificationSection(
            @NonNull Activity activity,
            @NonNull View root
    ) {
        if (findViewWithTag(root, TAG_FINANCIAL_NOTIFICATION_CARD) != null) {
            return;
        }

        TextView dataAndAppHeading = findTextView(root, "Data & App");

        if (dataAndAppHeading == null) {
            return;
        }

        View headingContainer = dataAndAppHeading.getParent() instanceof View
                ? (View) dataAndAppHeading.getParent()
                : dataAndAppHeading;

        ViewParentResult parentResult = findDirectChildParent(headingContainer);

        if (parentResult == null
                || !(parentResult.parent instanceof LinearLayout)) {
            return;
        }

        LinearLayout mainLayout = (LinearLayout) parentResult.parent;
        int insertionIndex = mainLayout.indexOfChild(parentResult.directChild);

        if (insertionIndex < 0) {
            return;
        }

        LinearLayout section = new LinearLayout(activity);
        section.setTag(TAG_FINANCIAL_NOTIFICATION_CARD);
        section.setOrientation(LinearLayout.VERTICAL);

        LinearLayout.LayoutParams sectionParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        sectionParams.setMargins(0, dp(activity, 18), 0, dp(activity, 12));
        section.setLayoutParams(sectionParams);

        TextView heading = new TextView(activity);
        heading.setText("SMS & Notifications");
        heading.setTextSize(18);
        heading.setTypeface(Typeface.DEFAULT_BOLD);
        heading.setTextColor(
                ContextCompat.getColor(activity, R.color.app_text_primary)
        );
        section.addView(heading);

        TextView subtitle = new TextView(activity);
        subtitle.setText("Play-safe transaction detection without SMS permission");
        subtitle.setTextSize(10);
        subtitle.setTextColor(
                ContextCompat.getColor(activity, R.color.app_text_secondary)
        );

        LinearLayout.LayoutParams subtitleParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        subtitleParams.setMargins(0, dp(activity, 2), 0, dp(activity, 10));
        subtitle.setLayoutParams(subtitleParams);
        section.addView(subtitle);

        MaterialCardView card = new MaterialCardView(activity);
        card.setCardBackgroundColor(
                ContextCompat.getColor(activity, R.color.info_surface)
        );
        card.setStrokeColor(
                ContextCompat.getColor(activity, R.color.info_outline)
        );
        card.setStrokeWidth(dp(activity, 1));
        card.setRadius(dp(activity, 16));
        card.setCardElevation(0f);
        card.setClickable(true);
        card.setFocusable(true);
        card.setRippleColor(
                ColorStateList.valueOf(
                        ContextCompat.getColor(activity, R.color.info_outline)
                )
        );

        LinearLayout cardContent = new LinearLayout(activity);
        cardContent.setOrientation(LinearLayout.VERTICAL);
        cardContent.setGravity(Gravity.CENTER_VERTICAL);
        cardContent.setPadding(
                dp(activity, 16),
                dp(activity, 14),
                dp(activity, 16),
                dp(activity, 14)
        );

        TextView title = new TextView(activity);
        title.setText("Financial Notifications");
        title.setTextSize(15);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(
                ContextCompat.getColor(activity, R.color.app_text_primary)
        );
        cardContent.addView(title);

        TextView description = new TextView(activity);
        description.setText("Review bank, UPI, wallet and card alerts");
        description.setTextSize(10);
        description.setTextColor(
                ContextCompat.getColor(activity, R.color.app_text_secondary)
        );

        LinearLayout.LayoutParams descriptionParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        descriptionParams.setMargins(0, dp(activity, 3), 0, 0);
        description.setLayoutParams(descriptionParams);
        cardContent.addView(description);

        card.addView(cardContent);
        card.setOnClickListener(view -> {
            Intent intent = new Intent(
                    activity,
                    NotificationAssistantActivity.class
            );
            activity.startActivity(intent);
        });

        section.addView(
                card,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(activity, 88)
                )
        );

        mainLayout.addView(section, insertionIndex);
    }

    @Nullable
    private static View findViewWithTag(
            @NonNull View view,
            int tag
    ) {
        Object currentTag = view.getTag();

        if (currentTag instanceof Integer
                && ((Integer) currentTag) == tag) {
            return view;
        }

        if (!(view instanceof ViewGroup)) {
            return null;
        }

        ViewGroup group = (ViewGroup) view;

        for (int index = 0; index < group.getChildCount(); index++) {
            View result = findViewWithTag(group.getChildAt(index), tag);

            if (result != null) {
                return result;
            }
        }

        return null;
    }

    @Nullable
    private static TextView findTextView(
            @NonNull View view,
            @NonNull String text
    ) {
        if (view instanceof TextView
                && text.contentEquals(((TextView) view).getText())) {
            return (TextView) view;
        }

        if (!(view instanceof ViewGroup)) {
            return null;
        }

        ViewGroup group = (ViewGroup) view;

        for (int index = 0; index < group.getChildCount(); index++) {
            TextView result = findTextView(group.getChildAt(index), text);

            if (result != null) {
                return result;
            }
        }

        return null;
    }

    @Nullable
    private static ViewParentResult findDirectChildParent(
            @NonNull View view
    ) {
        View current = view;

        while (current.getParent() instanceof ViewGroup) {
            ViewGroup parent = (ViewGroup) current.getParent();

            if (parent instanceof LinearLayout
                    && parent.getChildCount() >= 3) {
                return new ViewParentResult(parent, current);
            }

            current = parent;
        }

        return null;
    }

    private static int dp(
            @NonNull Context context,
            int value
    ) {
        return Math.round(
                value * context.getResources().getDisplayMetrics().density
        );
    }

    private static final class ViewParentResult {
        private final ViewGroup parent;
        private final View directChild;

        private ViewParentResult(
                @NonNull ViewGroup parent,
                @NonNull View directChild
        ) {
            this.parent = parent;
            this.directChild = directChild;
        }
    }

    @Nullable @Override public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection, @Nullable String[] selectionArgs, @Nullable String sortOrder) { return null; }
    @Nullable @Override public String getType(@NonNull Uri uri) { return null; }
    @Nullable @Override public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) { return null; }
    @Override public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) { return 0; }
    @Override public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection, @Nullable String[] selectionArgs) { return 0; }
}
