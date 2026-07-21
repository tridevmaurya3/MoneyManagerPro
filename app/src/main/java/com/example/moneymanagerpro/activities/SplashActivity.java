package com.example.moneymanagerpro.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.utils.ThemeManager;

public class SplashActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "MoneyManagerSecurity";
    private static final String KEY_PIN_ENABLED = "pin_enabled";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applySavedTheme(this);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            SharedPreferences preferences = getSharedPreferences(
                    PREFS_NAME,
                    MODE_PRIVATE
            );

            boolean pinLockEnabled = preferences.getBoolean(
                    KEY_PIN_ENABLED,
                    false
            );

            Class<?> nextScreen = pinLockEnabled
                    ? PinActivity.class
                    : DashboardActivity.class;

            startActivity(new Intent(
                    SplashActivity.this,
                    nextScreen
            ));

            finish();

        }, 1200);
    }
}