package com.example.moneymanagerpro.ui;

import android.app.Activity;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.example.moneymanagerpro.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.Locale;

public final class GlobalSignedFinanceColorController {

    private final Activity activity;
    private View decor;
    private ViewTreeObserver.OnGlobalLayoutListener listener;
    private boolean attached;
    private boolean applying;

    public GlobalSignedFinanceColorController(@NonNull Activity activity) {
        this.activity = activity;
    }

    public void attach() {
        if (attached || activity.isFinishing() || activity.isDestroyed()) return;
        decor = activity.getWindow().getDecorView();
        listener = this::apply;
        decor.getViewTreeObserver().addOnGlobalLayoutListener(listener);
        attached = true;
        decor.post(this::apply);
    }

    public void detach() {
        if (!attached || decor == null || listener == null) return;
        if (decor.getViewTreeObserver().isAlive()) {
            decor.getViewTreeObserver().removeOnGlobalLayoutListener(listener);
        }
        attached = false;
    }

    private void apply() {
        if (applying || !(decor instanceof ViewGroup)) return;
        applying = true;
        try {
            styleTree((ViewGroup) decor);
        } finally {
            applying = false;
        }
    }

    private void styleTree(ViewGroup group) {
        for (int index = 0; index < group.getChildCount(); index++) {
            View child = group.getChildAt(index);
            if (child instanceof MaterialCardView) {
                styleFinanceCard((MaterialCardView) child);
            }
            if (child instanceof ViewGroup) styleTree((ViewGroup) child);
        }
    }

    private void styleFinanceCard(MaterialCardView card) {
        if (containsNestedCard(card) || containsActionButton(card)) return;

        String text = collectText(card).toLowerCase(Locale.ROOT);
        if (!looksLikeMoneyCard(text)) return;

        SignedState state = classify(text);
        if (state == SignedState.NONE) return;

        int surface = state == SignedState.POSITIVE
                ? R.color.income_surface : R.color.expense_surface;
        int outline = state == SignedState.POSITIVE
                ? R.color.income_outline : R.color.expense_outline;
        int accent = state == SignedState.POSITIVE
                ? R.color.income : R.color.expense;

        card.setCardBackgroundColor(ContextCompat.getColor(activity, surface));
        card.setStrokeColor(ContextCompat.getColor(activity, outline));
        card.setStrokeWidth(dp(1));
        colorText(card, ContextCompat.getColor(activity, accent));
    }

    private SignedState classify(String text) {
        boolean explicitNegative = containsAny(
                text,
                "−₹", "-₹", "− ₹", "- ₹",
                "saving -", "balance -", "net -"
        );
        boolean explicitPositive = containsAny(
                text,
                "+₹", "+ ₹", "saving +", "balance +", "net +"
        );
        if (explicitNegative) return SignedState.NEGATIVE;
        if (explicitPositive) return SignedState.POSITIVE;

        boolean negative = containsAny(
                text,
                "expense", "spent", "debited", "debit",
                "bill", "emi", "overdue", "outstanding",
                "due today", "due tomorrow", "transfer out"
        );
        boolean positive = containsAny(
                text,
                "income", "received", "credited", "credit",
                "refund", "reversal", "transfer in"
        );

        if (negative && !positive) return SignedState.NEGATIVE;
        if (positive && !negative) return SignedState.POSITIVE;
        return SignedState.NONE;
    }

    private boolean looksLikeMoneyCard(String text) {
        return text.contains("₹")
                || text.contains(" inr ")
                || text.matches("(?s).*\\brs\\.?\\s*[0-9].*");
    }

    private boolean containsNestedCard(ViewGroup group) {
        for (int index = 0; index < group.getChildCount(); index++) {
            View child = group.getChildAt(index);
            if (child instanceof MaterialCardView) return true;
            if (child instanceof ViewGroup
                    && containsNestedCard((ViewGroup) child)) return true;
        }
        return false;
    }

    private boolean containsActionButton(ViewGroup group) {
        for (int index = 0; index < group.getChildCount(); index++) {
            View child = group.getChildAt(index);
            if (child instanceof MaterialButton) return true;
            if (child instanceof ViewGroup
                    && containsActionButton((ViewGroup) child)) return true;
        }
        return false;
    }

    private void colorText(View view, int accent) {
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            text.setTextColor(accent);
            if (isImportant(text.getText())) {
                text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                colorText(group.getChildAt(index), accent);
            }
        }
    }

    private boolean isImportant(CharSequence value) {
        String text = value == null
                ? "" : value.toString().toLowerCase(Locale.ROOT);
        return text.contains("₹")
                || containsAny(
                text,
                "expense", "income", "bill", "emi", "due",
                "refund", "received", "credited", "debited",
                "saving", "balance", "outstanding"
        );
    }

    private String collectText(View view) {
        StringBuilder text = new StringBuilder();
        collectText(view, text);
        return text.toString();
    }

    private void collectText(View view, StringBuilder output) {
        if (view instanceof TextView) {
            CharSequence value = ((TextView) view).getText();
            if (value != null) output.append(' ').append(value);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                collectText(group.getChildAt(index), output);
            }
        }
    }

    private boolean containsAny(String text, String... values) {
        for (String value : values) if (text.contains(value)) return true;
        return false;
    }

    private int dp(int value) {
        return Math.round(
                value * activity.getResources().getDisplayMetrics().density
        );
    }

    private enum SignedState {
        NONE,
        POSITIVE,
        NEGATIVE
    }
}
