package com.example.moneymanagerpro.auth;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import com.example.moneymanagerpro.activities.AuthenticationActivity;
import com.example.moneymanagerpro.activities.DashboardActivity;
import com.example.moneymanagerpro.activities.PinActivity;
import com.google.firebase.auth.FirebaseAuth;

public final class AuthNavigator {

    private static final String SECURITY_PREFERENCES =
            "MoneyManagerSecurity";

    private static final String KEY_PIN = "app_pin";
    private static final String KEY_PIN_ENABLED = "pin_enabled";

    private AuthNavigator() {
    }

    public static void openAfterAuthentication(Activity activity) {
        SharedPreferences preferences =
                activity.getSharedPreferences(
                        SECURITY_PREFERENCES,
                        Context.MODE_PRIVATE
                );

        String pin = preferences.getString(KEY_PIN, "");
        boolean pinEnabled = preferences.getBoolean(
                KEY_PIN_ENABLED,
                false
        );

        Class<?> destination =
                pinEnabled
                        && pin != null
                        && pin.matches("\\d{4}")
                        ? PinActivity.class
                        : DashboardActivity.class;

        Intent intent = new Intent(activity, destination);
        intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK
        );
        activity.startActivity(intent);
        activity.finish();
    }

    public static void logout(Activity activity) {
        FirebaseAuth.getInstance().signOut();

        Intent intent = AuthenticationActivity.createLoginIntent(activity);
        intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK
        );
        activity.startActivity(intent);
        activity.finish();
    }
}
