package com.example.moneymanagerpro.utils;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

public class ThemeManager {

    public static final String THEME_SYSTEM = "system";
    public static final String THEME_LIGHT = "light";
    public static final String THEME_DARK = "dark";

    private static final String PREFS_NAME = "app_theme_preferences";
    private static final String KEY_SELECTED_THEME = "selected_theme";

    private ThemeManager() {
    }

    public static void applySavedTheme(Context context) {
        applyTheme(getSelectedTheme(context));
    }

    public static void setTheme(Context context, String theme) {
        context.getSharedPreferences(
                        PREFS_NAME,
                        Context.MODE_PRIVATE
                ).edit()
                .putString(KEY_SELECTED_THEME, theme)
                .apply();

        applyTheme(theme);
    }

    public static String getSelectedTheme(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
        );

        return preferences.getString(
                KEY_SELECTED_THEME,
                THEME_SYSTEM
        );
    }

    private static void applyTheme(String theme) {
        if (THEME_LIGHT.equals(theme)) {
            AppCompatDelegate.setDefaultNightMode(
                    AppCompatDelegate.MODE_NIGHT_NO
            );

        } else if (THEME_DARK.equals(theme)) {
            AppCompatDelegate.setDefaultNightMode(
                    AppCompatDelegate.MODE_NIGHT_YES
            );

        } else {
            AppCompatDelegate.setDefaultNightMode(
                    AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            );
        }
    }
}