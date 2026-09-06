package com.example.moneymanagerpro.floating;

import android.view.View;
import android.view.WindowManager;

import java.lang.reflect.Field;

/**
 * Temporarily detaches the expense overlay while an Android-owned external
 * screen (QR scanner, UPI chooser/app, or image picker) is in the foreground.
 * The same View and LayoutParams are reattached afterwards, so entered data,
 * scroll state, position and resized geometry are preserved.
 */
final class FloatingExpenseOverlayVisibility {

    private FloatingExpenseOverlayVisibility() {
    }

    static void hide(FloatingExpenseQuickEntryOverlay overlay) {
        if (overlay == null) {
            return;
        }
        try {
            View root = rootView(overlay);
            WindowManager manager = windowManager(overlay);
            if (root != null && manager != null && root.isAttachedToWindow()) {
                manager.removeViewImmediate(root);
            }
        } catch (Exception ignored) {
        }
    }

    static void restore(FloatingExpenseQuickEntryOverlay overlay) {
        if (overlay == null) {
            return;
        }
        try {
            View root = rootView(overlay);
            WindowManager manager = windowManager(overlay);
            WindowManager.LayoutParams params = layoutParams(overlay);
            if (root != null
                    && manager != null
                    && params != null
                    && !root.isAttachedToWindow()) {
                manager.addView(root, params);
                root.requestFocus();
            }
        } catch (Exception ignored) {
        }
    }

    private static View rootView(
            FloatingExpenseQuickEntryOverlay overlay
    ) throws Exception {
        Field field = FloatingExpenseQuickEntryOverlay.class
                .getDeclaredField("rootView");
        field.setAccessible(true);
        return (View) field.get(overlay);
    }

    private static WindowManager windowManager(
            FloatingExpenseQuickEntryOverlay overlay
    ) throws Exception {
        Field field = FloatingExpenseQuickEntryOverlay.class
                .getDeclaredField("windowManager");
        field.setAccessible(true);
        return (WindowManager) field.get(overlay);
    }

    private static WindowManager.LayoutParams layoutParams(
            FloatingExpenseQuickEntryOverlay overlay
    ) throws Exception {
        Field field = FloatingExpenseQuickEntryOverlay.class
                .getDeclaredField("params");
        field.setAccessible(true);
        return (WindowManager.LayoutParams) field.get(overlay);
    }
}
