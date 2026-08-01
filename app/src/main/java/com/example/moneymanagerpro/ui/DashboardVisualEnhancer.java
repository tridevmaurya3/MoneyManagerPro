package com.example.moneymanagerpro.ui;

import android.app.Activity;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.activities.DashboardActivity;
import com.google.android.material.card.MaterialCardView;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Applies four distinct light Fluent-style colors to the dashboard period
 * selector and the three previous-month cards. It also updates the More Tools
 * label from Smart Advisor to Smart Transaction Assistant when the dynamic
 * bottom sheet is displayed.
 */
public final class DashboardVisualEnhancer {

    private static final Map<Activity, Boolean>
            labelObservers =
            new WeakHashMap<>();

    private DashboardVisualEnhancer() {
    }

    public static void apply(
            @NonNull Activity activity
    ) {
        if (!(activity instanceof DashboardActivity)) {
            return;
        }

        styleCard(
                activity,
                R.id.cardPeriodSelector,
                "#EEF6FF",
                "#B8D8F2"
        );

        styleCard(
                activity,
                R.id.cardMonth1,
                "#F2FAF4",
                "#B9DEC3"
        );

        styleCard(
                activity,
                R.id.cardMonth2,
                "#F7F2FF",
                "#D8C7F2"
        );

        styleCard(
                activity,
                R.id.cardMonth3,
                "#FFF9E8",
                "#E7D59B"
        );

        setTextColor(
                activity,
                R.id.txtSelectedPeriod,
                "#315F83"
        );

        setTextColor(
                activity,
                R.id.txtMonth1Title,
                "#315E3D"
        );

        setTextColor(
                activity,
                R.id.txtMonth2Title,
                "#654A8E"
        );

        setTextColor(
                activity,
                R.id.txtMonth3Title,
                "#755D18"
        );

        installDynamicLabelObserver(
                activity
        );
    }

    public static void remove(
            @NonNull Activity activity
    ) {
        labelObservers.remove(
                activity
        );
    }

    private static void styleCard(
            @NonNull Activity activity,
            int viewId,
            @NonNull String backgroundColor,
            @NonNull String strokeColor
    ) {
        View view =
                activity.findViewById(
                        viewId
                );

        if (!(view instanceof MaterialCardView)) {
            return;
        }

        MaterialCardView card =
                (MaterialCardView) view;

        card.setCardBackgroundColor(
                Color.parseColor(
                        backgroundColor
                )
        );

        card.setStrokeColor(
                Color.parseColor(
                        strokeColor
                )
        );

        card.setStrokeWidth(
                dp(
                        activity,
                        1
                )
        );

        card.setCardElevation(0f);
    }

    private static void setTextColor(
            @NonNull Activity activity,
            int viewId,
            @NonNull String color
    ) {
        View view =
                activity.findViewById(
                        viewId
                );

        if (view instanceof TextView) {
            ((TextView) view).setTextColor(
                    Color.parseColor(
                            color
                    )
            );
        }
    }

    private static void installDynamicLabelObserver(
            @NonNull Activity activity
    ) {
        if (Boolean.TRUE.equals(
                labelObservers.get(
                        activity
                )
        )) {
            return;
        }

        View decorView =
                activity
                        .getWindow()
                        .getDecorView();

        ViewTreeObserver.OnGlobalLayoutListener listener =
                () -> updateDynamicToolLabels(
                        decorView
                );

        decorView
                .getViewTreeObserver()
                .addOnGlobalLayoutListener(
                        listener
                );

        labelObservers.put(
                activity,
                true
        );

        updateDynamicToolLabels(
                decorView
        );
    }

    private static void updateDynamicToolLabels(
            @NonNull View view
    ) {
        if (view instanceof TextView) {
            TextView textView =
                    (TextView) view;

            CharSequence currentText =
                    textView.getText();

            if (currentText != null) {
                if ("Smart Advisor"
                        .contentEquals(
                                currentText
                        )) {

                    textView.setText(
                            "Smart Transaction Assistant"
                    );

                } else if ("Personal finance guidance"
                        .contentEquals(
                                currentText
                        )) {

                    textView.setText(
                            "Offline transaction intelligence"
                    );
                }
            }
        }

        if (!(view instanceof ViewGroup)) {
            return;
        }

        ViewGroup group =
                (ViewGroup) view;

        for (int index = 0;
             index < group.getChildCount();
             index++) {

            updateDynamicToolLabels(
                    group.getChildAt(index)
            );
        }
    }

    private static int dp(
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
}
