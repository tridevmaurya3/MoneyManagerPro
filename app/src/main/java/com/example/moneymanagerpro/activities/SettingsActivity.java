package com.example.moneymanagerpro.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Button;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.utils.ThemeManager;
import com.google.android.material.button.MaterialButton;

public class SettingsActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "MoneyManagerSecurity";
    private static final String KEY_PIN = "app_pin";
    private static final String KEY_SETUP_COMPLETE = "pin_setup_complete";
    private static final String KEY_PIN_ENABLED = "pin_enabled";

    private TextView txtPinStatus;

    private MaterialButton btnThemeSystem;
    private MaterialButton btnThemeLight;
    private MaterialButton btnThemeDark;

    private MaterialButton btnSetChangePin;
    private MaterialButton btnDisablePin;
    private MaterialButton btnOpenBackup;

    private SharedPreferences preferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        txtPinStatus = findViewById(R.id.txtPinStatus);

        btnThemeSystem = findViewById(R.id.btnThemeSystem);
        btnThemeLight = findViewById(R.id.btnThemeLight);
        btnThemeDark = findViewById(R.id.btnThemeDark);

        btnSetChangePin = findViewById(R.id.btnSetChangePin);
        btnDisablePin = findViewById(R.id.btnDisablePin);
        btnOpenBackup = findViewById(R.id.btnOpenBackup);

        preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        btnThemeSystem.setOnClickListener(v ->
                chooseTheme(ThemeManager.THEME_SYSTEM)
        );

        btnThemeLight.setOnClickListener(v ->
                chooseTheme(ThemeManager.THEME_LIGHT)
        );

        btnThemeDark.setOnClickListener(v ->
                chooseTheme(ThemeManager.THEME_DARK)
        );

        btnSetChangePin.setOnClickListener(v -> showPinDialog());

        btnDisablePin.setOnClickListener(v -> confirmDisablePin());

        btnOpenBackup.setOnClickListener(v ->
                startActivity(new Intent(
                        SettingsActivity.this,
                        BackupActivity.class
                ))
        );

        refreshPinStatus();
        refreshThemeButtons();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshThemeButtons();
    }

    private void chooseTheme(String theme) {
        ThemeManager.setTheme(this, theme);
        refreshThemeButtons();
    }

    private void refreshThemeButtons() {
        String selectedTheme = ThemeManager.getSelectedTheme(this);

        styleThemeButton(
                btnThemeSystem,
                ThemeManager.THEME_SYSTEM.equals(selectedTheme)
        );

        styleThemeButton(
                btnThemeLight,
                ThemeManager.THEME_LIGHT.equals(selectedTheme)
        );

        styleThemeButton(
                btnThemeDark,
                ThemeManager.THEME_DARK.equals(selectedTheme)
        );
    }

    private void styleThemeButton(
            MaterialButton button,
            boolean selected
    ) {
        int selectedBackground = getColor(R.color.primary);
        int selectedText = getColor(R.color.white);

        int unselectedBackground = getColor(R.color.app_surface);
        int unselectedText = getColor(R.color.app_text_primary);

        button.setBackgroundTintList(
                ColorStateList.valueOf(
                        selected ? selectedBackground : unselectedBackground
                )
        );

        button.setTextColor(
                selected ? selectedText : unselectedText
        );

        button.setStrokeColor(
                ColorStateList.valueOf(getColor(R.color.app_outline))
        );

        button.setStrokeWidth(dp(1));
        button.setAllCaps(false);
    }

    private void refreshPinStatus() {
        boolean pinEnabled = preferences.getBoolean(KEY_PIN_ENABLED, false);
        String savedPin = preferences.getString(KEY_PIN, "");

        if (pinEnabled && !savedPin.isEmpty()) {
            txtPinStatus.setText(
                    "Status: PIN Lock and biometric unlock are available."
            );

            btnSetChangePin.setText("Change 4-digit PIN");
            btnDisablePin.setEnabled(true);
            btnDisablePin.setAlpha(1f);

        } else {
            txtPinStatus.setText("Status: PIN Lock is disabled.");

            btnSetChangePin.setText("Set 4-digit PIN");
            btnDisablePin.setEnabled(false);
            btnDisablePin.setAlpha(0.5f);
        }
    }

    private void showPinDialog() {
        LinearLayout dialogLayout = new LinearLayout(this);
        dialogLayout.setOrientation(LinearLayout.VERTICAL);
        dialogLayout.setPadding(dp(24), dp(8), dp(24), 0);

        EditText etNewPin = new EditText(this);
        etNewPin.setHint("Enter new 4-digit PIN");

        etNewPin.setInputType(
                InputType.TYPE_CLASS_NUMBER
                        | InputType.TYPE_NUMBER_VARIATION_PASSWORD
        );

        EditText etConfirmPin = new EditText(this);
        etConfirmPin.setHint("Confirm new PIN");

        etConfirmPin.setInputType(
                InputType.TYPE_CLASS_NUMBER
                        | InputType.TYPE_NUMBER_VARIATION_PASSWORD
        );

        dialogLayout.addView(etNewPin);
        dialogLayout.addView(etConfirmPin);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Set App PIN")
                .setMessage("PIN will be requested when the app opens.")
                .setView(dialogLayout)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(listener -> {
            Button saveButton = dialog.getButton(
                    AlertDialog.BUTTON_POSITIVE
            );

            saveButton.setOnClickListener(v -> {
                String newPin = etNewPin.getText().toString().trim();

                String confirmPin = etConfirmPin.getText()
                        .toString()
                        .trim();

                if (!newPin.matches("\\d{4}")) {
                    Toast.makeText(
                            this,
                            "PIN must contain exactly 4 digits",
                            Toast.LENGTH_SHORT
                    ).show();

                    return;
                }

                if (!newPin.equals(confirmPin)) {
                    Toast.makeText(
                            this,
                            "Both PIN values must match",
                            Toast.LENGTH_SHORT
                    ).show();

                    return;
                }

                preferences.edit()
                        .putString(KEY_PIN, newPin)
                        .putBoolean(KEY_SETUP_COMPLETE, true)
                        .putBoolean(KEY_PIN_ENABLED, true)
                        .apply();

                dialog.dismiss();

                Toast.makeText(
                        this,
                        "PIN Lock enabled successfully",
                        Toast.LENGTH_SHORT
                ).show();

                refreshPinStatus();
            });
        });

        dialog.show();
    }

    private void confirmDisablePin() {
        new AlertDialog.Builder(this)
                .setTitle("Disable PIN Lock")
                .setMessage(
                        "Fingerprint unlock will also be unavailable until PIN Lock is enabled again."
                )
                .setPositiveButton("Disable", (dialog, which) -> {
                    preferences.edit()
                            .remove(KEY_PIN)
                            .putBoolean(KEY_SETUP_COMPLETE, true)
                            .putBoolean(KEY_PIN_ENABLED, false)
                            .apply();

                    Toast.makeText(
                            this,
                            "PIN Lock disabled",
                            Toast.LENGTH_SHORT
                    ).show();

                    refreshPinStatus();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private int dp(int value) {
        return (int) (
                value * getResources().getDisplayMetrics().density
        );
    }
}