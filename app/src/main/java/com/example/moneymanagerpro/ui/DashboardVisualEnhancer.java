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

/** Dashboard visual polish without SMS or external-notification launchers. */
public final class DashboardVisualEnhancer {
    private static final Map<Activity, Boolean> observers = new WeakHashMap<>();
    private DashboardVisualEnhancer() {}

    public static void apply(@NonNull Activity activity) {
        if (!(activity instanceof DashboardActivity)) return;
        styleCard(activity, R.id.cardPeriodSelector, "#EEF6FF", "#B8D8F2");
        styleCard(activity, R.id.cardMonth1, "#F2FAF4", "#B9DEC3");
        styleCard(activity, R.id.cardMonth2, "#F7F2FF", "#D8C7F2");
        styleCard(activity, R.id.cardMonth3, "#FFF9E8", "#E7D59B");
        setTextColor(activity, R.id.txtSelectedPeriod, "#315F83");
        setTextColor(activity, R.id.txtMonth1Title, "#315E3D");
        setTextColor(activity, R.id.txtMonth2Title, "#654A8E");
        setTextColor(activity, R.id.txtMonth3Title, "#755D18");
        installLabelObserver(activity);
    }

    public static void remove(@NonNull Activity activity) { observers.remove(activity); }

    private static void installLabelObserver(Activity activity) {
        if (Boolean.TRUE.equals(observers.get(activity))) return;
        View root = activity.getWindow().getDecorView();
        ViewTreeObserver.OnGlobalLayoutListener listener = () -> updateLabels(root);
        root.getViewTreeObserver().addOnGlobalLayoutListener(listener);
        observers.put(activity, true);
        updateLabels(root);
    }

    private static void updateLabels(View view) {
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            if ("Smart Advisor".contentEquals(text.getText())) text.setText("Smart Transaction Assistant");
            else if ("Personal finance guidance".contentEquals(text.getText())) text.setText("Offline transaction intelligence");
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) updateLabels(group.getChildAt(i));
        }
    }

    private static void styleCard(Activity activity, int id, String background, String stroke) {
        View view = activity.findViewById(id);
        if (!(view instanceof MaterialCardView)) return;
        MaterialCardView card = (MaterialCardView) view;
        card.setCardBackgroundColor(Color.parseColor(background));
        card.setStrokeColor(Color.parseColor(stroke));
        card.setStrokeWidth(dp(activity, 1));
        card.setCardElevation(0f);
    }

    private static void setTextColor(Activity activity, int id, String color) {
        View view = activity.findViewById(id);
        if (view instanceof TextView) ((TextView) view).setTextColor(Color.parseColor(color));
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
