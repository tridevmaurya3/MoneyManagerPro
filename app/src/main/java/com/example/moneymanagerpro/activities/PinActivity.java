package com.example.moneymanagerpro.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import com.example.moneymanagerpro.R;
import com.google.android.material.button.MaterialButton;

import java.util.concurrent.Executor;

public class PinActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "MoneyManagerSecurity";
    private static final String KEY_PIN = "app_pin";
    private static final String KEY_SETUP_COMPLETE = "pin_setup_complete";
    private static final String KEY_PIN_ENABLED = "pin_enabled";

    private TextView txtPinTitle;
    private TextView txtPinSubtitle;

    private EditText etPin;
    private EditText etConfirmPin;

    private MaterialButton btnPinAction;
    private MaterialButton btnBiometricUnlock;
    private MaterialButton btnSkipPinSetup;

    private SharedPreferences preferences;
    private boolean isFirstSetup;
    private boolean biometricAvailable;

    private BiometricPrompt biometricPrompt;
    private BiometricPrompt.PromptInfo biometricPromptInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pin);

        txtPinTitle = findViewById(R.id.txtPinTitle);
        txtPinSubtitle = findViewById(R.id.txtPinSubtitle);

        etPin = findViewById(R.id.etPin);
        etConfirmPin = findViewById(R.id.etConfirmPin);

        btnPinAction = findViewById(R.id.btnPinAction);
        btnBiometricUnlock = findViewById(R.id.btnBiometricUnlock);
        btnSkipPinSetup = findViewById(R.id.btnSkipPinSetup);

        preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        setupBiometricPrompt();
        setupScreen();

        btnPinAction.setOnClickListener(v -> handlePinAction());

        btnBiometricUnlock.setOnClickListener(v -> showBiometricPrompt());

        btnSkipPinSetup.setOnClickListener(v -> skipPinSetup());
    }

    private void setupScreen() {
        String savedPin = preferences.getString(KEY_PIN, "");
        isFirstSetup = savedPin.isEmpty();

        etPin.setInputType(
                InputType.TYPE_CLASS_NUMBER
                        | InputType.TYPE_NUMBER_VARIATION_PASSWORD
        );

        etConfirmPin.setInputType(
                InputType.TYPE_CLASS_NUMBER
                        | InputType.TYPE_NUMBER_VARIATION_PASSWORD
        );

        if (isFirstSetup) {
            txtPinTitle.setText("Secure Your Money");
            txtPinSubtitle.setText(
                    "Create a 4-digit PIN to protect your finance data."
            );

            etPin.setHint("Create 4-digit PIN");
            etConfirmPin.setHint("Confirm PIN");

            etConfirmPin.setVisibility(View.VISIBLE);
            btnBiometricUnlock.setVisibility(View.GONE);

            btnPinAction.setText("Save PIN");
            btnSkipPinSetup.setVisibility(View.VISIBLE);

        } else {
            txtPinTitle.setText("Unlock Money Manager");
            txtPinSubtitle.setText(
                    biometricAvailable
                            ? "Use fingerprint, face unlock, or your 4-digit PIN."
                            : "Enter your 4-digit PIN to continue."
            );

            etPin.setHint("Enter your 4-digit PIN");

            etConfirmPin.setVisibility(View.GONE);
            btnPinAction.setText("Unlock with PIN");
            btnSkipPinSetup.setVisibility(View.GONE);

            if (biometricAvailable) {
                btnBiometricUnlock.setVisibility(View.VISIBLE);

                btnBiometricUnlock.postDelayed(
                        this::showBiometricPrompt,
                        350
                );
            } else {
                btnBiometricUnlock.setVisibility(View.GONE);
            }
        }
    }

    private void setupBiometricPrompt() {
        BiometricManager biometricManager = BiometricManager.from(this);

        int result = biometricManager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_WEAK
        );

        biometricAvailable = result == BiometricManager.BIOMETRIC_SUCCESS;

        if (!biometricAvailable) {
            return;
        }

        Executor executor = ContextCompat.getMainExecutor(this);

        biometricPrompt = new BiometricPrompt(
                this,
                executor,
                new BiometricPrompt.AuthenticationCallback() {

                    @Override
                    public void onAuthenticationSucceeded(
                            BiometricPrompt.AuthenticationResult result
                    ) {
                        super.onAuthenticationSucceeded(result);

                        Toast.makeText(
                                PinActivity.this,
                                "Biometric authentication successful",
                                Toast.LENGTH_SHORT
                        ).show();

                        openDashboard();
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        super.onAuthenticationFailed();

                        Toast.makeText(
                                PinActivity.this,
                                "Fingerprint or face was not recognized",
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                }
        );

        biometricPromptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Money Manager Pro")
                .setSubtitle("Authenticate to access your finance data")
                .setAllowedAuthenticators(
                        BiometricManager.Authenticators.BIOMETRIC_WEAK
                )
                .setNegativeButtonText("Use PIN Instead")
                .build();
    }

    private void showBiometricPrompt() {
        if (!biometricAvailable
                || biometricPrompt == null
                || biometricPromptInfo == null
                || isFinishing()) {
            return;
        }

        biometricPrompt.authenticate(biometricPromptInfo);
    }

    private void handlePinAction() {
        String pin = etPin.getText() == null
                ? ""
                : etPin.getText().toString().trim();

        if (!pin.matches("\\d{4}")) {
            Toast.makeText(
                    this,
                    "Please enter a valid 4-digit PIN",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        if (isFirstSetup) {
            String confirmPin = etConfirmPin.getText() == null
                    ? ""
                    : etConfirmPin.getText().toString().trim();

            if (!pin.equals(confirmPin)) {
                Toast.makeText(
                        this,
                        "Both PIN values must match",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }

            preferences.edit()
                    .putString(KEY_PIN, pin)
                    .putBoolean(KEY_SETUP_COMPLETE, true)
                    .putBoolean(KEY_PIN_ENABLED, true)
                    .apply();

            Toast.makeText(
                    this,
                    "PIN Lock enabled successfully",
                    Toast.LENGTH_SHORT
            ).show();

            openDashboard();

        } else {
            String savedPin = preferences.getString(KEY_PIN, "");

            if (pin.equals(savedPin)) {
                openDashboard();
            } else {
                etPin.setText("");

                Toast.makeText(
                        this,
                        "Incorrect PIN. Please try again.",
                        Toast.LENGTH_SHORT
                ).show();
            }
        }
    }

    private void skipPinSetup() {
        preferences.edit()
                .putBoolean(KEY_SETUP_COMPLETE, true)
                .putBoolean(KEY_PIN_ENABLED, false)
                .apply();

        openDashboard();
    }

    private void openDashboard() {
        Intent intent = new Intent(
                PinActivity.this,
                DashboardActivity.class
        );

        intent.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK
        );

        startActivity(intent);
        finish();
    }
}