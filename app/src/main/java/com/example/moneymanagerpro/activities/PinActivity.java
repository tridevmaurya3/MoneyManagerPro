package com.example.moneymanagerpro.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.utils.BubbleTouchAnimator;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.Executor;

public class PinActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "MoneyManagerSecurity";
    private static final String KEY_PIN = "app_pin";
    private static final String KEY_SETUP_COMPLETE = "pin_setup_complete";
    private static final String KEY_PIN_ENABLED = "pin_enabled";

    private TextView txtPinTitle;
    private TextView txtPinSubtitle;
    private TextInputLayout inputPin;
    private TextInputLayout inputConfirmPin;
    private TextInputEditText etPin;
    private TextInputEditText etConfirmPin;
    private LinearLayout confirmPinContainer;
    private MaterialButton btnPinAction;
    private MaterialButton btnBiometricUnlock;
    private MaterialButton btnEmailUnlock;
    private MaterialButton btnSkipPinSetup;

    private SharedPreferences preferences;
    private boolean isFirstSetup;
    private boolean biometricAvailable;
    private boolean actionInProgress;
    private boolean dashboardOpened;
    private boolean biometricPromptVisible;
    private boolean automaticBiometricAttempted;
    private BiometricPrompt biometricPrompt;
    private BiometricPrompt.PromptInfo biometricPromptInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pin);

        bindViews();
        preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        setupBiometricPrompt();

        if (!resolveSecurityMode()) {
            return;
        }

        configurePinInputs();
        setupScreen();
        setupActions();
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();

        if (automaticBiometricAttempted
                || isFirstSetup
                || !biometricAvailable
                || dashboardOpened
                || actionInProgress
                || btnBiometricUnlock == null
                || btnBiometricUnlock.getVisibility() != View.VISIBLE) {
            return;
        }

        automaticBiometricAttempted = true;
        btnBiometricUnlock.postDelayed(this::showBiometricPrompt, 180L);
    }

    private void bindViews() {
        txtPinTitle = findViewById(R.id.txtPinTitle);
        txtPinSubtitle = findViewById(R.id.txtPinSubtitle);
        inputPin = findViewById(R.id.inputPin);
        inputConfirmPin = findViewById(R.id.inputConfirmPin);
        etPin = findViewById(R.id.etPin);
        etConfirmPin = findViewById(R.id.etConfirmPin);
        confirmPinContainer = findViewById(R.id.confirmPinContainer);
        btnPinAction = findViewById(R.id.btnPinAction);
        btnBiometricUnlock = findViewById(R.id.btnBiometricUnlock);
        btnEmailUnlock = findViewById(R.id.btnEmailUnlock);
        btnSkipPinSetup = findViewById(R.id.btnSkipPinSetup);
    }

    private boolean resolveSecurityMode() {
        String savedPin = safeText(preferences.getString(KEY_PIN, ""));
        boolean setupComplete = preferences.getBoolean(KEY_SETUP_COMPLETE, false);
        boolean pinEnabled = preferences.getBoolean(KEY_PIN_ENABLED, false);

        if (setupComplete && !pinEnabled) {
            openDashboard();
            return false;
        }

        isFirstSetup = !pinEnabled || !savedPin.matches("\\d{4}");
        return true;
    }

    private void configurePinInputs() {
        InputFilter[] filter = {new InputFilter.LengthFilter(4)};
        etPin.setFilters(filter);
        etConfirmPin.setFilters(filter);

        int inputType = InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_VARIATION_PASSWORD;
        etPin.setInputType(inputType);
        etConfirmPin.setInputType(inputType);
        etPin.setSingleLine(true);
        etConfirmPin.setSingleLine(true);
        etPin.setImeOptions(
                isFirstSetup ? EditorInfo.IME_ACTION_NEXT : EditorInfo.IME_ACTION_DONE
        );
        etConfirmPin.setImeOptions(EditorInfo.IME_ACTION_DONE);

        etPin.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {
                inputPin.setError(null);
                if (isFirstSetup
                        && editable != null
                        && editable.length() == 4
                        && etConfirmPin.getText() != null
                        && etConfirmPin.getText().length() == 0) {
                    etConfirmPin.requestFocus();
                }
            }
        });

        etConfirmPin.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {
                inputConfirmPin.setError(null);
            }
        });

        etPin.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_NEXT && isFirstSetup) {
                etConfirmPin.requestFocus();
                return true;
            }
            if (actionId == EditorInfo.IME_ACTION_DONE
                    || actionId == EditorInfo.IME_ACTION_GO) {
                handlePinAction();
                return true;
            }
            return false;
        });

        etConfirmPin.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE
                    || actionId == EditorInfo.IME_ACTION_GO) {
                handlePinAction();
                return true;
            }
            return false;
        });
    }

    private void setupScreen() {
        inputPin.setError(null);
        inputConfirmPin.setError(null);
        etPin.setText("");
        etConfirmPin.setText("");

        if (isFirstSetup) {
            txtPinTitle.setText("Secure Your Money");
            txtPinSubtitle.setText(
                    "Create a 4-digit PIN to protect access to your finance data."
            );
            etPin.setHint("Create PIN");
            etConfirmPin.setHint("Confirm PIN");
            confirmPinContainer.setVisibility(View.VISIBLE);
            btnBiometricUnlock.setVisibility(View.GONE);
            btnEmailUnlock.setVisibility(View.GONE);
            btnPinAction.setText("Save PIN");
            btnSkipPinSetup.setVisibility(View.VISIBLE);
            etPin.requestFocus();
        } else {
            txtPinTitle.setText("Unlock Money Manager");
            txtPinSubtitle.setText(
                    biometricAvailable
                            ? "Use fingerprint, face unlock or your 4-digit PIN."
                            : "Enter your 4-digit PIN to continue."
            );
            etPin.setHint("Enter PIN");
            confirmPinContainer.setVisibility(View.GONE);
            btnPinAction.setText("Unlock with PIN");
            btnSkipPinSetup.setVisibility(View.GONE);
            btnBiometricUnlock.setVisibility(
                    biometricAvailable ? View.VISIBLE : View.GONE
            );
            btnEmailUnlock.setVisibility(View.VISIBLE);
            etPin.requestFocus();
        }
    }

    private void setupActions() {
        btnPinAction.setOnClickListener(view -> handlePinAction());
        btnBiometricUnlock.setOnClickListener(view -> showBiometricPrompt());
        btnEmailUnlock.setOnClickListener(
                view -> startActivity(
                        AuthenticationActivity.createReauthenticationIntent(this)
                )
        );
        btnSkipPinSetup.setOnClickListener(view -> confirmSkipPinSetup());

        BubbleTouchAnimator.apply(btnPinAction);
        BubbleTouchAnimator.apply(btnBiometricUnlock);
        BubbleTouchAnimator.apply(btnEmailUnlock);
        BubbleTouchAnimator.apply(btnSkipPinSetup);
    }

    private void setupBiometricPrompt() {
        BiometricManager biometricManager = BiometricManager.from(this);
        int result = biometricManager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_WEAK
        );
        biometricAvailable = result == BiometricManager.BIOMETRIC_SUCCESS;

        if (!biometricAvailable) return;

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
                        biometricPromptVisible = false;
                        if (dashboardOpened || actionInProgress) return;

                        actionInProgress = true;
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
                        if (!dashboardOpened) {
                            Toast.makeText(
                                    PinActivity.this,
                                    "Fingerprint or face was not recognized",
                                    Toast.LENGTH_SHORT
                            ).show();
                        }
                    }

                    @Override
                    public void onAuthenticationError(
                            int errorCode,
                            CharSequence errorMessage
                    ) {
                        super.onAuthenticationError(errorCode, errorMessage);
                        biometricPromptVisible = false;
                        if (dashboardOpened || isFinishing() || isDestroyed()) return;

                        if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON
                                || errorCode == BiometricPrompt.ERROR_USER_CANCELED
                                || errorCode == BiometricPrompt.ERROR_CANCELED) {
                            txtPinSubtitle.setText("Enter your 4-digit PIN to continue.");
                            etPin.requestFocus();
                            return;
                        }

                        Toast.makeText(
                                PinActivity.this,
                                safeText(
                                        errorMessage == null ? "" : errorMessage.toString(),
                                        "Biometric authentication is unavailable"
                                ),
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                }
        );

        biometricPromptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Money Manager Pro")
                .setSubtitle("Authenticate to access your finance data")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                .setNegativeButtonText("Use PIN Instead")
                .build();
    }

    private void showBiometricPrompt() {
        if (isFirstSetup
                || !biometricAvailable
                || biometricPrompt == null
                || biometricPromptInfo == null
                || biometricPromptVisible
                || actionInProgress
                || dashboardOpened
                || isFinishing()
                || isDestroyed()) {
            return;
        }

        try {
            biometricPromptVisible = true;
            biometricPrompt.authenticate(biometricPromptInfo);
        } catch (Exception exception) {
            biometricPromptVisible = false;
            Toast.makeText(
                    this,
                    "Biometric unlock could not be started",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    private void handlePinAction() {
        if (actionInProgress || dashboardOpened) return;

        inputPin.setError(null);
        inputConfirmPin.setError(null);
        String enteredPin = getInputText(etPin);

        if (!enteredPin.matches("\\d{4}")) {
            inputPin.setError("Enter exactly four digits");
            etPin.requestFocus();
            return;
        }

        if (isFirstSetup) saveNewPin(enteredPin);
        else verifySavedPin(enteredPin);
    }

    private void saveNewPin(String newPin) {
        String confirmedPin = getInputText(etConfirmPin);

        if (!confirmedPin.matches("\\d{4}")) {
            inputConfirmPin.setError("Confirm the four-digit PIN");
            etConfirmPin.requestFocus();
            return;
        }

        if (!newPin.equals(confirmedPin)) {
            inputConfirmPin.setError("Both PIN values must match");
            etConfirmPin.setText("");
            etConfirmPin.requestFocus();
            return;
        }

        setActionState(true, "Saving PIN...");
        boolean saved = preferences.edit()
                .putString(KEY_PIN, newPin)
                .putBoolean(KEY_SETUP_COMPLETE, true)
                .putBoolean(KEY_PIN_ENABLED, true)
                .commit();

        if (!saved) {
            setActionState(false, "Save PIN");
            Toast.makeText(this, "PIN could not be saved", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "PIN Lock enabled successfully", Toast.LENGTH_SHORT).show();
        openDashboard();
    }

    private void verifySavedPin(String enteredPin) {
        String savedPin = safeText(preferences.getString(KEY_PIN, ""));
        if (securePinEquals(enteredPin, savedPin)) {
            setActionState(true, "Unlocking...");
            openDashboard();
            return;
        }

        etPin.setText("");
        inputPin.setError("Incorrect PIN. Please try again.");
        etPin.requestFocus();
        Toast.makeText(this, "Incorrect PIN", Toast.LENGTH_SHORT).show();
    }

    private boolean securePinEquals(String enteredPin, String savedPin) {
        try {
            byte[] firstValue = safeText(enteredPin).getBytes(StandardCharsets.UTF_8);
            byte[] secondValue = safeText(savedPin).getBytes(StandardCharsets.UTF_8);
            return MessageDigest.isEqual(firstValue, secondValue);
        } catch (Exception exception) {
            return safeText(enteredPin).equals(safeText(savedPin));
        }
    }

    private void confirmSkipPinSetup() {
        if (!isFirstSetup || actionInProgress || dashboardOpened) return;

        new AlertDialog.Builder(this)
                .setTitle("Continue Without PIN?")
                .setMessage(
                        "Money Manager Pro will open without PIN or biometric protection. "
                                + "You can enable PIN Lock later from Settings."
                )
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Skip", (dialog, which) -> skipPinSetup())
                .show();
    }

    private void skipPinSetup() {
        if (actionInProgress || dashboardOpened) return;

        setActionState(true, "Continuing...");
        boolean saved = preferences.edit()
                .remove(KEY_PIN)
                .putBoolean(KEY_SETUP_COMPLETE, true)
                .putBoolean(KEY_PIN_ENABLED, false)
                .commit();

        if (!saved) {
            setActionState(false, "Save PIN");
            Toast.makeText(
                    this,
                    "Security preference could not be saved",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        openDashboard();
    }

    private void setActionState(boolean inProgress, String buttonText) {
        actionInProgress = inProgress;
        btnPinAction.setEnabled(!inProgress);
        btnSkipPinSetup.setEnabled(!inProgress);
        btnBiometricUnlock.setEnabled(!inProgress);
        btnEmailUnlock.setEnabled(!inProgress);

        float alpha = inProgress ? 0.55f : 1f;
        btnPinAction.setAlpha(alpha);
        btnSkipPinSetup.setAlpha(alpha);
        btnBiometricUnlock.setAlpha(alpha);
        btnEmailUnlock.setAlpha(alpha);
        btnPinAction.setText(buttonText);
    }

    private void openDashboard() {
        if (dashboardOpened || isFinishing() || isDestroyed()) return;

        dashboardOpened = true;
        actionInProgress = true;
        biometricPromptVisible = false;

        Intent intent = new Intent(PinActivity.this, DashboardActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private String getInputText(TextInputEditText editText) {
        if (editText == null || editText.getText() == null) return "";
        return editText.getText().toString().trim();
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    private String safeText(String value, String fallback) {
        String safeValue = safeText(value);
        return safeValue.isEmpty() ? fallback : safeValue;
    }

    private abstract static class SimpleTextWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence sequence, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence sequence, int start, int before, int count) {
        }
    }
}
