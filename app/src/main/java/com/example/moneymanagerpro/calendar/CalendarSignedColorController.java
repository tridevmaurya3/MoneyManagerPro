package com.example.moneymanagerpro.calendar;

import android.app.Activity;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.activities.CalendarActivity;
import com.google.android.material.card.MaterialCardView;

/**
 * Applies clear signed styling to the unified calendar without changing stored
 * data: negative/expense/due groups use light red; positive/income groups use
 * light green. It also styles day-detail dialogs added after a date is tapped.
 */
public final class CalendarSignedColorController {

    private final Activity activity;
    private View decor;
    private ViewTreeObserver.OnGlobalLayoutListener listener;
    private boolean attached;

    public CalendarSignedColorController(@NonNull Activity activity) {
        this.activity = activity;
    }

    public void attach() {
        if (!(activity instanceof CalendarActivity)
                || activity.isFinishing()
                || activity.isDestroyed()
                || attached) {
            return;
        }

        decor = activity.getWindow().getDecorView();
        listener = this::applySignedColors;
        decor.getViewTreeObserver().addOnGlobalLayoutListener(listener);
        attached = true;
        decor.post(this::applySignedColors);
    }

    public void detach() {
        if (!attached || decor == null || listener == null) return;
        if (decor.getViewTreeObserver().isAlive()) {
            decor.getViewTreeObserver().removeOnGlobalLayoutListener(listener);
        }
        attached = false;
    }

    private void applySignedColors() {
        if (decor instanceof ViewGroup) styleTree((ViewGroup) decor);
    }

    private void styleTree(@NonNull ViewGroup group) {
        for (int index = 0; index < group.getChildCount(); index++) {
            View child = group.getChildAt(index);
            if (child instanceof MaterialCardView) styleCard((MaterialCardView) child);
            if (child instanceof ViewGroup) styleTree((ViewGroup) child);
        }
    }

    private void styleCard(@NonNull MaterialCardView card) {
        String text = collectText(card).toLowerCase();
        SignedState state = stateFor(text);
        if (state == SignedState.NONE) return;

        int surface = state == SignedState.POSITIVE
                ? R.color.success_surface
                : R.color.error_surface;
        int outline = state == SignedState.POSITIVE
                ? R.color.success_outline
                : R.color.error_outline;
        int accent = state == SignedState.POSITIVE
                ? R.color.success
                : R.color.expense;

        card.setCardBackgroundColor(ContextCompat.getColor(activity, surface));
        card.setStrokeColor(ContextCompat.getColor(activity, outline));
        card.setStrokeWidth(dp(1));
        styleText(card, ContextCompat.getColor(activity, accent));
    }

    private SignedState stateFor(@NonNull String text) {
        boolean positive = text.contains("income")
                || text.contains("received")
                || text.contains("+₹")
                || text.contains("+ ₹");

        boolean negative = text.contains("expense")
                || text.contains("bill")
                || text.contains("emi")
                || text.contains("due today")
                || text.contains("due tomorrow")
                || text.contains("overdue")
                || text.contains("-₹")
                || text.contains("- ₹");

        if (positive && !negative) return SignedState.POSITIVE;
        if (negative && !positive) return SignedState.NEGATIVE;
        if (negative) return SignedState.NEGATIVE;
        return SignedState.NONE;
    }

    private void styleText(@NonNull View view, int accent) {
        if (view instanceof TextView) {
            TextView textView = (TextView) view;
            String value = textView.getText() == null
                    ? ""
                    : textView.getText().toString().toLowerCase();
            boolean important = value.contains("income")
                    || value.contains("expense")
                    || value.contains("bill")
                    || value.contains("emi")
                    || value.contains("due")
                    || value.contains("received")
                    || value.contains("₹")
                    || value.contains("+")
                    || value.contains("-");
            if (important) {
                textView.setTextColor(accent);
                textView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                styleText(group.getChildAt(i), accent);
            }
        }
    }

    @NonNull
    private String collectText(@NonNull View view) {
        StringBuilder builder = new StringBuilder();
        collectText(view, builder);
        return builder.toString();
    }

    private void collectText(@NonNull View view, @NonNull StringBuilder builder) {
        if (view instanceof TextView) {
            CharSequence value = ((TextView) view).getText();
            if (value != null) builder.append(' ').append(value);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                collectText(group.getChildAt(i), builder);
            }
        }
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private enum SignedState {
        NONE,
        POSITIVE,
        NEGATIVE
    }
}
