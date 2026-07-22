package com.example.moneymanagerpro.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.utils.BubbleTouchAnimator;
import com.example.moneymanagerpro.utils.ThemeManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class SettingsActivity extends AppCompatActivity {

    private static final String PREFS_NAME =
            "MoneyManagerSecurity";

    private static final String KEY_PIN =
            "app_pin";

    private static final String KEY_SETUP_COMPLETE =
            "pin_setup_complete";

    private static final String KEY_PIN_ENABLED =
            "pin_enabled";

    private TextView txtPinStatus;

    private MaterialButton btnThemeSystem;
    private MaterialButton btnThemeLight;
    private MaterialButton btnThemeDark;

    private MaterialButton btnSetChangePin;
    private MaterialButton btnDisablePin;
    private MaterialButton btnOpenBackup;

    private SharedPreferences preferences;

    private boolean dialogActionInProgress = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        bindViews();
        prepareScreen();
    }

    @Override
    protected void onResume() {
        super.onResume();

        refreshThemeButtons();
        refreshPinStatus();
    }

    private void bindViews() {
        TextView btnBack =
                findViewById(R.id.btnBack);

        txtPinStatus =
                findViewById(R.id.txtPinStatus);

        btnThemeSystem =
                findViewById(R.id.btnThemeSystem);

        btnThemeLight =
                findViewById(R.id.btnThemeLight);

        btnThemeDark =
                findViewById(R.id.btnThemeDark);

        btnSetChangePin =
                findViewById(R.id.btnSetChangePin);

        btnDisablePin =
                findViewById(R.id.btnDisablePin);

        btnOpenBackup =
                findViewById(R.id.btnOpenBackup);

        preferences =
                getSharedPreferences(
                        PREFS_NAME,
                        MODE_PRIVATE
                );

        btnBack.setOnClickListener(
                view -> finish()
        );

        BubbleTouchAnimator.apply(btnBack);
    }

    private void prepareScreen() {
        btnThemeSystem.setOnClickListener(
                view -> chooseTheme(
                        ThemeManager.THEME_SYSTEM
                )
        );

        btnThemeLight.setOnClickListener(
                view -> chooseTheme(
                        ThemeManager.THEME_LIGHT
                )
        );

        btnThemeDark.setOnClickListener(
                view -> chooseTheme(
                        ThemeManager.THEME_DARK
                )
        );

        btnSetChangePin.setOnClickListener(
                view -> showPinDialog()
        );

        btnDisablePin.setOnClickListener(
                view -> showDisablePinDialog()
        );

        btnOpenBackup.setOnClickListener(
                view -> openBackupCenter()
        );

        BubbleTouchAnimator.apply(btnThemeSystem);
        BubbleTouchAnimator.apply(btnThemeLight);
        BubbleTouchAnimator.apply(btnThemeDark);
        BubbleTouchAnimator.apply(btnSetChangePin);
        BubbleTouchAnimator.apply(btnDisablePin);
        BubbleTouchAnimator.apply(btnOpenBackup);

        refreshPinStatus();
        refreshThemeButtons();
    }

    private void chooseTheme(String theme) {
        String currentTheme =
                ThemeManager.getSelectedTheme(this);

        if (theme.equals(currentTheme)) {
            refreshThemeButtons();
            return;
        }

        ThemeManager.setTheme(this, theme);
        refreshThemeButtons();
    }

    private void refreshThemeButtons() {
        String selectedTheme =
                ThemeManager.getSelectedTheme(this);

        styleThemeButton(
                btnThemeSystem,
                ThemeManager.THEME_SYSTEM.equals(
                        selectedTheme
                )
        );

        styleThemeButton(
                btnThemeLight,
                ThemeManager.THEME_LIGHT.equals(
                        selectedTheme
                )
        );

        styleThemeButton(
                btnThemeDark,
                ThemeManager.THEME_DARK.equals(
                        selectedTheme
                )
        );
    }

    private void styleThemeButton(
            MaterialButton button,
            boolean selected
    ) {
        int selectedBackground =
                getColorValue(R.color.primary);

        int selectedText =
                getColorValue(R.color.white);

        int unselectedBackground =
                getColorValue(R.color.app_surface);

        int unselectedText =
                getColorValue(R.color.app_text_primary);

        int selectedStroke =
                getColorValue(R.color.primary);

        int unselectedStroke =
                getColorValue(R.color.app_outline);

        button.setBackgroundTintList(
                ColorStateList.valueOf(
                        selected
                                ? selectedBackground
                                : unselectedBackground
                )
        );

        button.setTextColor(
                selected
                        ? selectedText
                        : unselectedText
        );

        button.setStrokeColor(
                ColorStateList.valueOf(
                        selected
                                ? selectedStroke
                                : unselectedStroke
                )
        );

        button.setStrokeWidth(dp(1));
        button.setAllCaps(false);
        button.setAlpha(selected ? 1f : 0.92f);
    }

    private void refreshPinStatus() {
        boolean pinEnabled =
                isPinEnabled();

        if (pinEnabled) {
            txtPinStatus.setText(
                    "Status: PIN Lock is enabled. Biometric unlock may also be used on supported devices."
            );

            txtPinStatus.setTextColor(
                    getColorValue(R.color.success)
            );

            btnSetChangePin.setText(
                    "Change 4-digit PIN"
            );

            btnDisablePin.setEnabled(true);
            btnDisablePin.setAlpha(1f);

        } else {
            txtPinStatus.setText(
                    "Status: PIN Lock is disabled."
            );

            txtPinStatus.setTextColor(
                    getColorValue(
                            R.color.app_text_secondary
                    )
            );

            btnSetChangePin.setText(
                    "Set 4-digit PIN"
            );

            btnDisablePin.setEnabled(false);
            btnDisablePin.setAlpha(0.45f);
        }
    }

    private boolean isPinEnabled() {
        boolean pinEnabled =
                preferences.getBoolean(
                        KEY_PIN_ENABLED,
                        false
                );

        String savedPin =
                preferences.getString(
                        KEY_PIN,
                        ""
                );

        return pinEnabled
                && savedPin != null
                && savedPin.matches("\\d{4}");
    }

    private void showPinDialog() {
        if (dialogActionInProgress) {
            return;
        }

        boolean changingExistingPin =
                isPinEnabled();

        LinearLayout dialogContent =
                new LinearLayout(this);

        dialogContent.setOrientation(
                LinearLayout.VERTICAL
        );

        dialogContent.setPadding(
                dp(22),
                dp(8),
                dp(22),
                dp(14)
        );

        TextView securityNote =
                createDialogInfoText(
                        changingExistingPin
                                ? "Verify your current PIN before creating a new one."
                                : "Create a memorable four-digit PIN. It will be requested when Money Manager Pro opens."
                );

        dialogContent.addView(securityNote);

        TextInputLayout currentPinLayout =
                null;

        TextInputEditText currentPinInput =
                null;

        if (changingExistingPin) {
            TextView currentPinLabel =
                    createFieldLabel(
                            "Current PIN"
                    );

            LinearLayout.LayoutParams labelParams =
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    );

            labelParams.setMargins(
                    0,
                    dp(18),
                    0,
                    dp(7)
            );

            currentPinLabel.setLayoutParams(
                    labelParams
            );

            dialogContent.addView(
                    currentPinLabel
            );

            currentPinLayout =
                    createPinInputLayout();

            currentPinInput =
                    createPinEditText(
                            "Enter current 4-digit PIN"
                    );

            currentPinLayout.addView(
                    currentPinInput
            );

            dialogContent.addView(
                    currentPinLayout
            );
        }

        TextView newPinLabel =
                createFieldLabel(
                        changingExistingPin
                                ? "New PIN"
                                : "Create New PIN"
                );

        LinearLayout.LayoutParams newLabelParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        newLabelParams.setMargins(
                0,
                changingExistingPin
                        ? dp(15)
                        : dp(18),
                0,
                dp(7)
        );

        newPinLabel.setLayoutParams(
                newLabelParams
        );

        dialogContent.addView(
                newPinLabel
        );

        TextInputLayout newPinLayout =
                createPinInputLayout();

        TextInputEditText newPinInput =
                createPinEditText(
                        "Enter new 4-digit PIN"
                );

        newPinLayout.addView(
                newPinInput
        );

        dialogContent.addView(
                newPinLayout
        );

        TextView confirmPinLabel =
                createFieldLabel(
                        "Confirm PIN"
                );

        LinearLayout.LayoutParams confirmLabelParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        confirmLabelParams.setMargins(
                0,
                dp(15),
                0,
                dp(7)
        );

        confirmPinLabel.setLayoutParams(
                confirmLabelParams
        );

        dialogContent.addView(
                confirmPinLabel
        );

        TextInputLayout confirmPinLayout =
                createPinInputLayout();

        TextInputEditText confirmPinInput =
                createPinEditText(
                        "Re-enter new 4-digit PIN"
                );

        confirmPinLayout.addView(
                confirmPinInput
        );

        dialogContent.addView(
                confirmPinLayout
        );

        LinearLayout actionRow =
                new LinearLayout(this);

        actionRow.setOrientation(
                LinearLayout.HORIZONTAL
        );

        actionRow.setGravity(
                Gravity.CENTER_VERTICAL
        );

        LinearLayout.LayoutParams actionRowParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(54)
                );

        actionRowParams.setMargins(
                0,
                dp(20),
                0,
                0
        );

        actionRow.setLayoutParams(
                actionRowParams
        );

        MaterialButton cancelButton =
                createDialogButton(
                        "Cancel",
                        false
                );

        MaterialButton saveButton =
                createDialogButton(
                        changingExistingPin
                                ? "Change PIN"
                                : "Create PIN",
                        true
                );

        LinearLayout.LayoutParams cancelParams =
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        1f
                );

        cancelParams.setMargins(
                0,
                0,
                dp(5),
                0
        );

        cancelButton.setLayoutParams(
                cancelParams
        );

        LinearLayout.LayoutParams saveParams =
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        1f
                );

        saveParams.setMargins(
                dp(5),
                0,
                0,
                0
        );

        saveButton.setLayoutParams(
                saveParams
        );

        actionRow.addView(
                cancelButton
        );

        actionRow.addView(
                saveButton
        );

        dialogContent.addView(
                actionRow
        );

        AlertDialog dialog =
                new AlertDialog.Builder(this)
                        .setTitle(
                                changingExistingPin
                                        ? "Change App PIN"
                                        : "Set App PIN"
                        )
                        .setView(dialogContent)
                        .create();

        TextInputLayout finalCurrentPinLayout =
                currentPinLayout;

        TextInputEditText finalCurrentPinInput =
                currentPinInput;

        cancelButton.setOnClickListener(
                view -> dialog.dismiss()
        );

        saveButton.setOnClickListener(view -> {
            clearPinErrors(
                    finalCurrentPinLayout,
                    newPinLayout,
                    confirmPinLayout
            );

            String currentPin =
                    getInputText(
                            finalCurrentPinInput
                    );

            String newPin =
                    getInputText(
                            newPinInput
                    );

            String confirmPin =
                    getInputText(
                            confirmPinInput
                    );

            if (changingExistingPin) {
                String savedPin =
                        preferences.getString(
                                KEY_PIN,
                                ""
                        );

                if (!currentPin.equals(savedPin)) {
                    if (finalCurrentPinLayout != null) {
                        finalCurrentPinLayout.setError(
                                "Current PIN is incorrect"
                        );
                    }

                    if (finalCurrentPinInput != null) {
                        finalCurrentPinInput.requestFocus();
                    }

                    return;
                }
            }

            if (!newPin.matches("\\d{4}")) {
                newPinLayout.setError(
                        "PIN must contain exactly 4 digits"
                );

                newPinInput.requestFocus();
                return;
            }

            if (changingExistingPin
                    && newPin.equals(currentPin)) {

                newPinLayout.setError(
                        "New PIN must be different from current PIN"
                );

                newPinInput.requestFocus();
                return;
            }

            if (!newPin.equals(confirmPin)) {
                confirmPinLayout.setError(
                        "Both PIN values must match"
                );

                confirmPinInput.requestFocus();
                return;
            }

            dialogActionInProgress = true;

            saveButton.setEnabled(false);
            cancelButton.setEnabled(false);

            saveButton.setText(
                    changingExistingPin
                            ? "Changing..."
                            : "Creating..."
            );

            boolean saved =
                    preferences.edit()
                            .putString(
                                    KEY_PIN,
                                    newPin
                            )
                            .putBoolean(
                                    KEY_SETUP_COMPLETE,
                                    true
                            )
                            .putBoolean(
                                    KEY_PIN_ENABLED,
                                    true
                            )
                            .commit();

            dialogActionInProgress = false;

            if (!saved) {
                saveButton.setEnabled(true);
                cancelButton.setEnabled(true);

                saveButton.setText(
                        changingExistingPin
                                ? "Change PIN"
                                : "Create PIN"
                );

                Toast.makeText(
                        SettingsActivity.this,
                        "PIN could not be saved",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }

            dialog.dismiss();

            Toast.makeText(
                    SettingsActivity.this,
                    changingExistingPin
                            ? "PIN changed successfully"
                            : "PIN Lock enabled successfully",
                    Toast.LENGTH_SHORT
            ).show();

            refreshPinStatus();
        });

        BubbleTouchAnimator.apply(
                cancelButton
        );

        BubbleTouchAnimator.apply(
                saveButton
        );

        dialog.setOnDismissListener(
                listener -> dialogActionInProgress = false
        );

        dialog.setOnShowListener(listener -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setSoftInputMode(
                        WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                );
            }

            if (changingExistingPin
                    && finalCurrentPinInput != null) {

                finalCurrentPinInput.requestFocus();
            } else {
                newPinInput.requestFocus();
            }
        });

        dialog.show();
    }

    private TextView createDialogInfoText(
            String message
    ) {
        TextView textView =
                new TextView(this);

        textView.setText(message);
        textView.setTextSize(12);
        textView.setTextColor(
                getColorValue(
                        R.color.app_text_secondary
                )
        );

        textView.setLineSpacing(
                dp(2),
                1f
        );

        return textView;
    }

    private TextView createFieldLabel(
            String label
    ) {
        TextView textView =
                new TextView(this);

        textView.setText(label);
        textView.setTextSize(12);
        textView.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        textView.setTextColor(
                getColorValue(
                        R.color.app_text_primary
                )
        );

        return textView;
    }

    private TextInputLayout createPinInputLayout() {
        TextInputLayout inputLayout =
                new TextInputLayout(this);

        inputLayout.setLayoutParams(
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        inputLayout.setHintEnabled(false);

        inputLayout.setBoxBackgroundMode(
                TextInputLayout.BOX_BACKGROUND_OUTLINE
        );

        inputLayout.setBoxBackgroundColor(
                getColorValue(
                        R.color.app_surface
                )
        );

        inputLayout.setBoxCornerRadii(
                dp(15),
                dp(15),
                dp(15),
                dp(15)
        );

        inputLayout.setBoxStrokeColor(
                getColorValue(
                        R.color.primary
                )
        );

        inputLayout.setErrorEnabled(true);

        return inputLayout;
    }

    private TextInputEditText createPinEditText(
            String placeholder
    ) {
        TextInputEditText editText =
                new TextInputEditText(this);

        editText.setLayoutParams(
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(58)
                )
        );

        editText.setHint(
                placeholder
        );

        editText.setInputType(
                InputType.TYPE_CLASS_NUMBER
                        | InputType.TYPE_NUMBER_VARIATION_PASSWORD
        );

        editText.setFilters(
                new InputFilter[]{
                        new InputFilter.LengthFilter(4)
                }
        );

        editText.setSingleLine(true);
        editText.setTextSize(18);

        editText.setTextColor(
                getColorValue(
                        R.color.app_text_primary
                )
        );

        editText.setHintTextColor(
                getColorValue(
                        R.color.app_text_secondary
                )
        );

        editText.setTypeface(
                Typeface.MONOSPACE,
                Typeface.BOLD
        );

        editText.setGravity(
                Gravity.CENTER_VERTICAL | Gravity.START
        );

        editText.setPadding(
                dp(14),
                0,
                dp(14),
                0
        );

        return editText;
    }

    private MaterialButton createDialogButton(
            String text,
            boolean primary
    ) {
        MaterialButton button =
                new MaterialButton(this);

        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(13);
        button.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        button.setCornerRadius(
                dp(16)
        );

        button.setMinimumHeight(0);
        button.setMinHeight(0);

        if (primary) {
            button.setTextColor(
                    getColorValue(
                            R.color.white
                    )
            );

            button.setBackgroundTintList(
                    ColorStateList.valueOf(
                            getColorValue(
                                    R.color.primary
                            )
                    )
            );

            button.setStrokeWidth(0);

        } else {
            button.setTextColor(
                    getColorValue(
                            R.color.app_text_primary
                    )
            );

            button.setBackgroundTintList(
                    ColorStateList.valueOf(
                            getColorValue(
                                    R.color.app_surface
                            )
                    )
            );

            button.setStrokeColor(
                    ColorStateList.valueOf(
                            getColorValue(
                                    R.color.app_outline
                            )
                    )
            );

            button.setStrokeWidth(
                    dp(1)
            );
        }

        return button;
    }

    private void showDisablePinDialog() {
        if (!isPinEnabled()
                || dialogActionInProgress) {

            return;
        }

        LinearLayout dialogContent =
                new LinearLayout(this);

        dialogContent.setOrientation(
                LinearLayout.VERTICAL
        );

        dialogContent.setPadding(
                dp(22),
                dp(8),
                dp(22),
                0
        );

        TextView label =
                createFieldLabel(
                        "Current PIN"
                );

        LinearLayout.LayoutParams labelParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        labelParams.setMargins(
                0,
                dp(8),
                0,
                dp(7)
        );

        label.setLayoutParams(
                labelParams
        );

        dialogContent.addView(
                label
        );

        TextInputLayout currentPinLayout =
                createPinInputLayout();

        TextInputEditText currentPinInput =
                createPinEditText(
                        "Enter current 4-digit PIN"
                );

        currentPinLayout.addView(
                currentPinInput
        );

        dialogContent.addView(
                currentPinLayout
        );

        AlertDialog dialog =
                new AlertDialog.Builder(this)
                        .setTitle(
                                "Disable PIN Lock"
                        )
                        .setMessage(
                                "Biometric unlock will also become unavailable."
                        )
                        .setView(dialogContent)
                        .setPositiveButton(
                                "Disable",
                                null
                        )
                        .setNegativeButton(
                                "Cancel",
                                null
                        )
                        .create();

        dialog.setOnShowListener(listener -> {
            android.widget.Button disableButton =
                    dialog.getButton(
                            AlertDialog.BUTTON_POSITIVE
                    );

            disableButton.setTextColor(
                    getColorValue(
                            R.color.expense
                    )
            );

            disableButton.setOnClickListener(view -> {
                currentPinLayout.setError(null);

                String enteredPin =
                        getInputText(
                                currentPinInput
                        );

                String savedPin =
                        preferences.getString(
                                KEY_PIN,
                                ""
                        );

                if (!enteredPin.equals(savedPin)) {
                    currentPinLayout.setError(
                            "Current PIN is incorrect"
                    );

                    currentPinInput.requestFocus();
                    return;
                }

                dialogActionInProgress = true;
                disableButton.setEnabled(false);

                boolean saved =
                        preferences.edit()
                                .remove(KEY_PIN)
                                .putBoolean(
                                        KEY_SETUP_COMPLETE,
                                        true
                                )
                                .putBoolean(
                                        KEY_PIN_ENABLED,
                                        false
                                )
                                .commit();

                dialogActionInProgress = false;

                if (!saved) {
                    disableButton.setEnabled(true);

                    Toast.makeText(
                            SettingsActivity.this,
                            "PIN Lock could not be disabled",
                            Toast.LENGTH_SHORT
                    ).show();

                    return;
                }

                dialog.dismiss();

                Toast.makeText(
                        SettingsActivity.this,
                        "PIN Lock disabled",
                        Toast.LENGTH_SHORT
                ).show();

                refreshPinStatus();
            });
        });

        dialog.setOnDismissListener(
                listener -> dialogActionInProgress = false
        );

        dialog.show();
    }

    private void clearPinErrors(
            TextInputLayout currentPinLayout,
            TextInputLayout newPinLayout,
            TextInputLayout confirmPinLayout
    ) {
        if (currentPinLayout != null) {
            currentPinLayout.setError(null);
        }

        if (newPinLayout != null) {
            newPinLayout.setError(null);
        }

        if (confirmPinLayout != null) {
            confirmPinLayout.setError(null);
        }
    }

    private void openBackupCenter() {
        try {
            startActivity(
                    new Intent(
                            SettingsActivity.this,
                            BackupActivity.class
                    )
            );

        } catch (Exception exception) {
            Toast.makeText(
                    this,
                    "Backup Center could not be opened",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    private String getInputText(
            TextInputEditText editText
    ) {
        if (editText == null
                || editText.getText() == null) {

            return "";
        }

        return editText
                .getText()
                .toString()
                .trim();
    }

    private int getColorValue(
            int colorResource
    ) {
        return ContextCompat.getColor(
                this,
                colorResource
        );
    }

    private int dp(int value) {
        return Math.round(
                value
                        * getResources()
                        .getDisplayMetrics()
                        .density
        );
    }
}