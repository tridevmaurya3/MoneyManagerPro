package com.example.moneymanagerpro.floating;

import android.content.Context;
import android.content.SharedPreferences;

final class FloatingQuickEntrySettings {

    private static final String PREFS =
            "floating_quick_entry_preferences";
    private static final String KEY_ENABLED =
            "floating_quick_entry_enabled";
    private static final String KEY_PERMISSION_PENDING =
            "floating_quick_entry_permission_pending";
    private static final String KEY_BUBBLE_ALPHA =
            "floating_quick_entry_bubble_alpha";

    private static final float DEFAULT_BUBBLE_ALPHA = 1.0f;
    private static final float MIN_BUBBLE_ALPHA = 0.28f;

    private FloatingQuickEntrySettings() {
    }

    static boolean isEnabled(Context context) {
        return preferences(context)
                .getBoolean(KEY_ENABLED, false);
    }

    static void setEnabled(
            Context context,
            boolean enabled
    ) {
        preferences(context)
                .edit()
                .putBoolean(KEY_ENABLED, enabled)
                .apply();
    }

    static boolean isPermissionPending(Context context) {
        return preferences(context)
                .getBoolean(KEY_PERMISSION_PENDING, false);
    }

    static void setPermissionPending(
            Context context,
            boolean pending
    ) {
        preferences(context)
                .edit()
                .putBoolean(KEY_PERMISSION_PENDING, pending)
                .apply();
    }

    static float getBubbleAlpha(Context context) {
        float value = preferences(context)
                .getFloat(
                        KEY_BUBBLE_ALPHA,
                        DEFAULT_BUBBLE_ALPHA
                );
        return Math.max(
                MIN_BUBBLE_ALPHA,
                Math.min(1.0f, value)
        );
    }

    static void setBubbleAlpha(
            Context context,
            float alpha
    ) {
        float safeAlpha = Math.max(
                MIN_BUBBLE_ALPHA,
                Math.min(1.0f, alpha)
        );
        preferences(context)
                .edit()
                .putFloat(KEY_BUBBLE_ALPHA, safeAlpha)
                .apply();
    }

    private static SharedPreferences preferences(
            Context context
    ) {
        return context.getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
        );
    }
}
