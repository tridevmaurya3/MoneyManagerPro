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

    private static SharedPreferences preferences(
            Context context
    ) {
        return context.getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
        );
    }
}
