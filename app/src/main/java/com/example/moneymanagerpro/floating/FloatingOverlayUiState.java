package com.example.moneymanagerpro.floating;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;

/**
 * Floating-only UI state helper.
 *
 * Keeps Income and Expense overlay geometry independent and restores the last
 * user-resized size/position on the next open. It also fixes the compact
 * Receiver Name / Choose UPI App row without touching expense/payment logic.
 */
final class FloatingOverlayUiState {

    private static final String PREFS = "floating_overlay_geometry";
    private static WeakReference<Object> currentExpenseOverlay =
            new WeakReference<>(null);

    private FloatingOverlayUiState() {
    }

    static void apply(
            Context context,
            Object overlay,
            boolean expense
    ) {
        if (context == null || overlay == null) {
            return;
        }

        View root = asView(readField(overlay, "rootView"));
        WindowManager.LayoutParams params =
                asLayoutParams(readField(overlay, "params"));
        WindowManager windowManager =
                asWindowManager(readField(overlay, "windowManager"));

        if (root == null || params == null || windowManager == null) {
            return;
        }

        if (expense) {
            currentExpenseOverlay = new WeakReference<>(overlay);
        }

        restoreGeometry(
                context.getApplicationContext(),
                root,
                params,
                windowManager,
                expense
        );

        if (expense) {
            fixExpenseUpiSecondRow(context, overlay);
        }
    }

    static void hideExpenseForExternalAction() {
        Object overlay = currentExpenseOverlay.get();
        if (overlay == null) {
            return;
        }

        View root = asView(readField(overlay, "rootView"));
        WindowManager windowManager =
                asWindowManager(readField(overlay, "windowManager"));
        if (root == null || windowManager == null || !root.isAttachedToWindow()) {
            return;
        }

        try {
            windowManager.removeViewImmediate(root);
        } catch (Exception ignored) {
        }
    }

    static void restoreExpenseAfterExternalAction() {
        Object overlay = currentExpenseOverlay.get();
        if (overlay == null) {
            return;
        }

        View root = asView(readField(overlay, "rootView"));
        WindowManager.LayoutParams params =
                asLayoutParams(readField(overlay, "params"));
        WindowManager windowManager =
                asWindowManager(readField(overlay, "windowManager"));

        if (root == null
                || params == null
                || windowManager == null
                || root.isAttachedToWindow()) {
            return;
        }

        try {
            windowManager.addView(root, params);
            root.requestFocus();
        } catch (Exception ignored) {
        }
    }

    private static void restoreGeometry(
            Context context,
            View root,
            WindowManager.LayoutParams params,
            WindowManager windowManager,
            boolean expense
    ) {
        SharedPreferences preferences = context.getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
        );
        String prefix = expense ? "expense_" : "income_";

        int screenWidth = context.getResources()
                .getDisplayMetrics().widthPixels;
        int screenHeight = context.getResources()
                .getDisplayMetrics().heightPixels;
        int minWidth = Math.min(dp(context, 290), screenWidth - dp(context, 12));
        int minHeight = Math.min(
                dp(context, expense ? 360 : 330),
                screenHeight - dp(context, 70)
        );
        int maxWidth = Math.max(minWidth, screenWidth - dp(context, 8));
        int maxHeight = Math.max(minHeight, screenHeight - dp(context, 10));

        int storedWidth = preferences.getInt(prefix + "width", 0);
        int storedHeight = preferences.getInt(prefix + "height", 0);
        int storedX = preferences.getInt(prefix + "x", Integer.MIN_VALUE);
        int storedY = preferences.getInt(prefix + "y", Integer.MIN_VALUE);

        if (storedWidth > 0) {
            params.width = clamp(storedWidth, minWidth, maxWidth);
        }
        if (storedHeight > 0) {
            params.height = clamp(storedHeight, minHeight, maxHeight);
        }

        if (storedX != Integer.MIN_VALUE) {
            params.x = clamp(
                    storedX,
                    0,
                    Math.max(0, screenWidth - params.width)
            );
        } else {
            params.x = clamp(
                    params.x,
                    0,
                    Math.max(0, screenWidth - params.width)
            );
        }

        if (storedY != Integer.MIN_VALUE) {
            params.y = clamp(
                    storedY,
                    dp(context, 4),
                    Math.max(dp(context, 4), screenHeight - params.height - dp(context, 6))
            );
        } else {
            params.y = clamp(
                    params.y,
                    dp(context, 4),
                    Math.max(dp(context, 4), screenHeight - params.height - dp(context, 6))
            );
        }

        try {
            windowManager.updateViewLayout(root, params);
        } catch (Exception ignored) {
        }

        root.addOnLayoutChangeListener(
                (view, left, top, right, bottom,
                 oldLeft, oldTop, oldRight, oldBottom) -> {
                    if (params.width <= 0 || params.height <= 0) {
                        return;
                    }
                    preferences.edit()
                            .putInt(prefix + "width", params.width)
                            .putInt(prefix + "height", params.height)
                            .putInt(prefix + "x", params.x)
                            .putInt(prefix + "y", params.y)
                            .apply();
                }
        );
    }

    private static void fixExpenseUpiSecondRow(
            Context context,
            Object overlay
    ) {
        View receiver = asView(readField(overlay, "upiNameField"));
        View chooseUpi = asView(readField(overlay, "payUpiButton"));
        if (receiver == null || chooseUpi == null) {
            return;
        }

        ViewGroup parent = receiver.getParent() instanceof ViewGroup
                ? (ViewGroup) receiver.getParent()
                : null;
        if (parent == null || chooseUpi.getParent() != parent) {
            return;
        }

        parent.setClipChildren(false);
        parent.setClipToPadding(false);
        parent.setMinimumHeight(dp(context, 42));

        ViewGroup.LayoutParams parentParams = parent.getLayoutParams();
        if (parentParams != null) {
            parentParams.height = dp(context, 42);
            parent.setLayoutParams(parentParams);
        }

        makeSecondRowControlSafe(context, receiver);
        makeSecondRowControlSafe(context, chooseUpi);
    }

    private static void makeSecondRowControlSafe(
            Context context,
            View view
    ) {
        view.setMinimumHeight(0);
        if (view instanceof EditText) {
            ((EditText) view).setMinHeight(0);
        }
        if (view instanceof TextView) {
            ((TextView) view).setIncludeFontPadding(false);
        }

        ViewGroup.LayoutParams raw = view.getLayoutParams();
        if (raw == null) {
            return;
        }
        raw.height = dp(context, 38);
        if (raw instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams margins =
                    (ViewGroup.MarginLayoutParams) raw;
            margins.topMargin = dp(context, 2);
            margins.bottomMargin = dp(context, 2);
        }
        view.setLayoutParams(raw);
    }

    private static Object readField(Object target, String name) {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (Exception ignored) {
                type = type.getSuperclass();
            }
        }
        return null;
    }

    private static View asView(Object value) {
        return value instanceof View ? (View) value : null;
    }

    private static WindowManager.LayoutParams asLayoutParams(Object value) {
        return value instanceof WindowManager.LayoutParams
                ? (WindowManager.LayoutParams) value
                : null;
    }

    private static WindowManager asWindowManager(Object value) {
        return value instanceof WindowManager
                ? (WindowManager) value
                : null;
    }

    private static int dp(Context context, int value) {
        return Math.round(
                value * context.getResources().getDisplayMetrics().density
        );
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
