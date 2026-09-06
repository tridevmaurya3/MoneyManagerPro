package com.example.moneymanagerpro.floating;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;

/**
 * Floating-only UI state helper.
 *
 * Keeps Income and Expense overlay geometry independent and restores the last
 * user-resized size/position on the next open. Expense UPI presentation is
 * reduced to one native Scan & Pay action; Money Manager no longer needs its
 * own UPI-ID, receiver or QR-mode controls in the floating form.
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
            simplifyExpenseNativeUpi(context, overlay);
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
                dp(context, expense ? 330 : 330),
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

    private static void simplifyExpenseNativeUpi(
            Context context,
            Object overlay
    ) {
        View mode = asView(readField(overlay, "upiModeDropdown"));
        View receiverId = asView(readField(overlay, "upiIdField"));
        View receiverName = asView(readField(overlay, "upiNameField"));
        View payButton = asView(readField(overlay, "payUpiButton"));
        View resultPanel = asView(readField(overlay, "upiResultPanel"));

        if (payButton == null) {
            return;
        }

        ViewGroup firstRow = commonParent(mode, receiverId);
        ViewGroup secondRow = commonParent(receiverName, payButton);
        ViewGroup body = firstRow != null && firstRow.getParent() instanceof ViewGroup
                ? (ViewGroup) firstRow.getParent()
                : null;

        if (body != null && firstRow != null) {
            int firstIndex = body.indexOfChild(firstRow);
            if (firstIndex > 0) {
                View possibleSection = body.getChildAt(firstIndex - 1);
                if (possibleSection instanceof TextView) {
                    TextView section = (TextView) possibleSection;
                    section.setText("UPI Payment");
                }
            }
            body.removeView(firstRow);
        } else {
            hide(mode);
            hide(receiverId);
        }

        if (secondRow != null) {
            if (receiverName != null && receiverName.getParent() == secondRow) {
                secondRow.removeView(receiverName);
            } else {
                hide(receiverName);
            }

            secondRow.setClipChildren(false);
            secondRow.setClipToPadding(false);
            secondRow.setMinimumHeight(0);

            ViewGroup.LayoutParams rowParams = secondRow.getLayoutParams();
            if (rowParams != null) {
                rowParams.height = dp(context, 42);
                secondRow.setLayoutParams(rowParams);
            }

            ViewGroup.LayoutParams raw = payButton.getLayoutParams();
            if (raw instanceof LinearLayout.LayoutParams) {
                LinearLayout.LayoutParams params =
                        (LinearLayout.LayoutParams) raw;
                params.width = 0;
                params.height = dp(context, 38);
                params.weight = 1f;
                params.leftMargin = 0;
                params.topMargin = dp(context, 2);
                params.bottomMargin = dp(context, 2);
                payButton.setLayoutParams(params);
            } else if (raw != null) {
                raw.width = ViewGroup.LayoutParams.MATCH_PARENT;
                raw.height = dp(context, 38);
                payButton.setLayoutParams(raw);
            }
        }

        if (payButton instanceof TextView) {
            TextView button = (TextView) payButton;
            button.setText("Scan & Pay");
            button.setTextSize(11f);
            button.setContentDescription(
                    "Choose a UPI app and use its native QR scanner"
            );
        }

        hide(resultPanel);

        payButton.setOnClickListener(view -> {
            Object host = readField(overlay, "externalHost");
            invokeLaunchNativeUpi(host);
        });
    }

    private static void invokeLaunchNativeUpi(Object host) {
        if (host == null) {
            return;
        }
        try {
            java.lang.reflect.Method method =
                    host.getClass().getMethod("launchQrScanner");
            method.setAccessible(true);
            method.invoke(host);
        } catch (Exception ignored) {
        }
    }

    private static ViewGroup commonParent(View first, View second) {
        if (first == null || second == null) {
            return null;
        }
        if (first.getParent() instanceof ViewGroup
                && first.getParent() == second.getParent()) {
            return (ViewGroup) first.getParent();
        }
        return null;
    }

    private static void hide(View view) {
        if (view != null) {
            view.setVisibility(View.GONE);
        }
    }

    private static Object readField(Object target, String name) {
        if (target == null) {
            return null;
        }
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
