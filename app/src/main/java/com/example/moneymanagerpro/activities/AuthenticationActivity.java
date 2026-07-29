package com.example.moneymanagerpro.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.auth.AuthNavigator;
import com.example.moneymanagerpro.utils.ThemeManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;

public class AuthenticationActivity extends AppCompatActivity {

    public static final String MODE_LOGIN = "login";
    public static final String MODE_SIGN_UP = "sign_up";
    public static final String MODE_FORGOT_PASSWORD = "forgot_password";
    public static final String MODE_CHANGE_PASSWORD = "change_password";

    private static final String EXTRA_MODE = "auth_mode";

    private TextView txtTitle;
    private TextView txtSubtitle;
    private TextView btnTopBack;
    private TextView btnSecondaryAction;
    private TextView btnTertiaryAction;

    private TextInputLayout inputFullName;
    private TextInputLayout inputEmail;
    private TextInputLayout inputCurrentPassword;
    private TextInputLayout inputPassword;
    private TextInputLayout inputConfirmPassword;

    private TextInputEditText etFullName;
    private TextInputEditText etEmail;
    private TextInputEditText etCurrentPassword;
    private TextInputEditText etPassword;
    private TextInputEditText etConfirmPassword;

    private MaterialButton btnPrimaryAction;

    private FirebaseAuth firebaseAuth;
    private String mode;
    private boolean requestInProgress;

    public static Intent createLoginIntent(Context context) {
        return createIntent(context, MODE_LOGIN);
    }

    public static Intent createIntent(Context context, String mode) {
        Intent intent = new Intent(context, AuthenticationActivity.class);
        intent.putExtra(EXTRA_MODE, mode);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applySavedTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_authentication);

        firebaseAuth = FirebaseAuth.getInstance();
        mode = getIntent().getStringExtra(EXTRA_MODE);

        if (mode == null || mode.trim().isEmpty()) {
            mode = MODE_LOGIN;
        }

        bindViews();
        configureMode();
        setupActions();
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (MODE_LOGIN.equals(mode)
                && firebaseAuth.getCurrentUser() != null) {
            AuthNavigator.openAfterAuthentication(this);
        }
    }

    private void bindViews() {
        txtTitle = findViewById(R.id.txtAuthTitle);
        txtSubtitle = findViewById(R.id.txtAuthSubtitle);
        btnTopBack = findViewById(R.id.btnAuthBack);
        btnSecondaryAction = findViewById(R.id.btnAuthSecondary);
        btnTertiaryAction = findViewById(R.id.btnAuthTertiary);

        inputFullName = findViewById(R.id.inputAuthFullName);
        inputEmail = findViewById(R.id.inputAuthEmail);
        inputCurrentPassword = findViewById(R.id.inputAuthCurrentPassword);
        inputPassword = findViewById(R.id.inputAuthPassword);
        inputConfirmPassword = findViewById(R.id.inputAuthConfirmPassword);

        etFullName = findViewById(R.id.etAuthFullName);
        etEmail = findViewById(R.id.etAuthEmail);
        etCurrentPassword = findViewById(R.id.etAuthCurrentPassword);
        etPassword = findViewById(R.id.etAuthPassword);
        etConfirmPassword = findViewById(R.id.etAuthConfirmPassword);

        btnPrimaryAction = findViewById(R.id.btnAuthPrimary);
    }

    private void configureMode() {
        hideAllOptionalFields();

        switch (mode) {
            case MODE_SIGN_UP:
                txtTitle.setText("Create your account");
                txtSubtitle.setText(
                        "Securely sync your Money Manager Pro sign-in"
                );
                inputFullName.setVisibility(View.VISIBLE);
                inputEmail.setVisibility(View.VISIBLE);
                inputPassword.setVisibility(View.VISIBLE);
                inputConfirmPassword.setVisibility(View.VISIBLE);
                inputPassword.setHint("Password");
                btnPrimaryAction.setText("Create Account");
                btnSecondaryAction.setText("Already have an account? Login");
                btnSecondaryAction.setVisibility(View.VISIBLE);
                btnTopBack.setVisibility(View.VISIBLE);
                break;

            case MODE_FORGOT_PASSWORD:
                txtTitle.setText("Reset password");
                txtSubtitle.setText(
                        "We will send a secure reset link to your email"
                );
                inputEmail.setVisibility(View.VISIBLE);
                btnPrimaryAction.setText("Send Reset Link");
                btnSecondaryAction.setText("Back to Login");
                btnSecondaryAction.setVisibility(View.VISIBLE);
                btnTopBack.setVisibility(View.VISIBLE);
                break;

            case MODE_CHANGE_PASSWORD:
                txtTitle.setText("Change password");
                txtSubtitle.setText(
                        "Confirm your current password before choosing a new one"
                );
                inputCurrentPassword.setVisibility(View.VISIBLE);
                inputPassword.setVisibility(View.VISIBLE);
                inputConfirmPassword.setVisibility(View.VISIBLE);
                inputPassword.setHint("New password");
                btnPrimaryAction.setText("Update Password");
                btnTopBack.setVisibility(View.VISIBLE);
                break;

            case MODE_LOGIN:
            default:
                mode = MODE_LOGIN;
                txtTitle.setText("Welcome back");
                txtSubtitle.setText(
                        "Sign in to continue to your financial workspace"
                );
                inputEmail.setVisibility(View.VISIBLE);
                inputPassword.setVisibility(View.VISIBLE);
                inputPassword.setHint("Password");
                btnPrimaryAction.setText("Login");
                btnSecondaryAction.setText("Create a new account");
                btnTertiaryAction.setText("Forgot password?");
                btnSecondaryAction.setVisibility(View.VISIBLE);
                btnTertiaryAction.setVisibility(View.VISIBLE);
                btnTopBack.setVisibility(View.GONE);
                break;
        }
    }

    private void hideAllOptionalFields() {
        inputFullName.setVisibility(View.GONE);
        inputEmail.setVisibility(View.GONE);
        inputCurrentPassword.setVisibility(View.GONE);
        inputPassword.setVisibility(View.GONE);
        inputConfirmPassword.setVisibility(View.GONE);
        btnSecondaryAction.setVisibility(View.GONE);
        btnTertiaryAction.setVisibility(View.GONE);
        btnTopBack.setVisibility(View.GONE);
    }

    private void setupActions() {
        btnTopBack.setOnClickListener(view -> finish());
        btnPrimaryAction.setOnClickListener(view -> submit());

        btnSecondaryAction.setOnClickListener(view -> {
            if (MODE_LOGIN.equals(mode)) {
                startActivity(createIntent(this, MODE_SIGN_UP));
            } else {
                startActivity(createLoginIntent(this));
                finish();
            }
        });

        btnTertiaryAction.setOnClickListener(view ->
                startActivity(
                        createIntent(
                                this,
                                MODE_FORGOT_PASSWORD
                        )
                )
        );
    }

    private void submit() {
        if (requestInProgress) {
            return;
        }

        clearErrors();
        hideKeyboard();

        switch (mode) {
            case MODE_SIGN_UP:
                createAccount();
                break;
            case MODE_FORGOT_PASSWORD:
                sendPasswordReset();
                break;
            case MODE_CHANGE_PASSWORD:
                changePassword();
                break;
            case MODE_LOGIN:
            default:
                login();
                break;
        }
    }

    private void login() {
        String email = value(etEmail);
        String password = value(etPassword);

        if (!validateEmail(email) || !validatePassword(password, inputPassword)) {
            return;
        }

        setLoading(true);

        firebaseAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    setLoading(false);

                    if (task.isSuccessful()) {
                        AuthNavigator.openAfterAuthentication(this);
                    } else {
                        showAuthError(task.getException());
                    }
                });
    }

    private void createAccount() {
        String fullName = value(etFullName);
        String email = value(etEmail);
        String password = value(etPassword);
        String confirmation = value(etConfirmPassword);

        if (fullName.length() < 2) {
            inputFullName.setError("Enter your full name");
            return;
        }

        if (!validateEmail(email)
                || !validatePassword(password, inputPassword)
                || !validateConfirmation(password, confirmation)) {
            return;
        }

        setLoading(true);

        firebaseAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        setLoading(false);
                        showAuthError(task.getException());
                        return;
                    }

                    FirebaseUser user = firebaseAuth.getCurrentUser();

                    if (user == null) {
                        setLoading(false);
                        showMessage("Account created. Please login.");
                        openLogin();
                        return;
                    }

                    UserProfileChangeRequest profileUpdate =
                            new UserProfileChangeRequest.Builder()
                                    .setDisplayName(fullName)
                                    .build();

                    user.updateProfile(profileUpdate)
                            .continueWithTask(ignored ->
                                    user.sendEmailVerification()
                            )
                            .addOnCompleteListener(profileTask -> {
                                setLoading(false);
                                showMessage(
                                        "Account created. Verification email sent."
                                );
                                AuthNavigator.openAfterAuthentication(this);
                            });
                });
    }

    private void sendPasswordReset() {
        String email = value(etEmail);

        if (!validateEmail(email)) {
            return;
        }

        setLoading(true);

        firebaseAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    setLoading(false);

                    if (task.isSuccessful()) {
                        showMessage(
                                "Password reset link sent. Check your email."
                        );
                        openLogin();
                    } else {
                        showAuthError(task.getException());
                    }
                });
    }

    private void changePassword() {
        FirebaseUser user = firebaseAuth.getCurrentUser();

        if (user == null || TextUtils.isEmpty(user.getEmail())) {
            AuthNavigator.logout(this);
            return;
        }

        String currentPassword = value(etCurrentPassword);
        String newPassword = value(etPassword);
        String confirmation = value(etConfirmPassword);

        if (currentPassword.isEmpty()) {
            inputCurrentPassword.setError("Enter current password");
            return;
        }

        if (!validatePassword(newPassword, inputPassword)
                || !validateConfirmation(newPassword, confirmation)) {
            return;
        }

        if (currentPassword.equals(newPassword)) {
            inputPassword.setError(
                    "New password must be different"
            );
            return;
        }

        setLoading(true);

        user.reauthenticate(
                EmailAuthProvider.getCredential(
                        user.getEmail(),
                        currentPassword
                )
        ).onSuccessTask(
                ignored -> user.updatePassword(newPassword)
        )
                .addOnCompleteListener(task -> {
                    setLoading(false);

                    if (task.isSuccessful()) {
                        showMessage("Password updated successfully");
                        finish();
                    } else {
                        showAuthError(task.getException());
                    }
                });
    }

    private boolean validateEmail(String email) {
        if (email.isEmpty()
                || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            inputEmail.setError("Enter a valid email address");
            return false;
        }

        return true;
    }

    private boolean validatePassword(
            String password,
            TextInputLayout inputLayout
    ) {
        if (password.length() < 8) {
            inputLayout.setError(
                    "Password must contain at least 8 characters"
            );
            return false;
        }

        return true;
    }

    private boolean validateConfirmation(
            String password,
            String confirmation
    ) {
        if (!password.equals(confirmation)) {
            inputConfirmPassword.setError("Passwords do not match");
            return false;
        }

        return true;
    }

    private void clearErrors() {
        inputFullName.setError(null);
        inputEmail.setError(null);
        inputCurrentPassword.setError(null);
        inputPassword.setError(null);
        inputConfirmPassword.setError(null);
    }

    private void setLoading(boolean loading) {
        requestInProgress = loading;
        btnPrimaryAction.setEnabled(!loading);
        btnSecondaryAction.setEnabled(!loading);
        btnTertiaryAction.setEnabled(!loading);

        if (loading) {
            btnPrimaryAction.setText("Please wait...");
        } else {
            configureMode();
        }
    }

    private void showAuthError(Exception exception) {
        String message = "Authentication failed. Please try again.";

        if (exception instanceof FirebaseAuthException) {
            String code = ((FirebaseAuthException) exception).getErrorCode();

            if ("ERROR_INVALID_CREDENTIAL".equals(code)
                    || "ERROR_WRONG_PASSWORD".equals(code)
                    || "ERROR_USER_NOT_FOUND".equals(code)) {
                message = "Email or password is incorrect.";
            } else if ("ERROR_EMAIL_ALREADY_IN_USE".equals(code)) {
                message = "An account already exists with this email.";
            } else if ("ERROR_NETWORK_REQUEST_FAILED".equals(code)) {
                message = "Internet connection is unavailable.";
            } else if ("ERROR_TOO_MANY_REQUESTS".equals(code)) {
                message = "Too many attempts. Please try again later.";
            } else if ("ERROR_WEAK_PASSWORD".equals(code)) {
                message = "Choose a stronger password.";
            }
        }

        showMessage(message);
    }

    private void openLogin() {
        Intent intent = createLoginIntent(this);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }

    private void hideKeyboard() {
        View currentView = getCurrentFocus();

        if (currentView == null) {
            return;
        }

        InputMethodManager manager =
                (InputMethodManager) getSystemService(
                        INPUT_METHOD_SERVICE
                );

        if (manager != null) {
            manager.hideSoftInputFromWindow(
                    currentView.getWindowToken(),
                    0
            );
        }
    }

    private String value(TextInputEditText editText) {
        return editText.getText() == null
                ? ""
                : editText.getText().toString().trim();
    }

    private void showMessage(@NonNull String message) {
        Toast.makeText(
                this,
                message,
                Toast.LENGTH_LONG
        ).show();
    }
}
