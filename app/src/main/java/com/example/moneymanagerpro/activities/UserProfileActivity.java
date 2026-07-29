package com.example.moneymanagerpro.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.auth.AuthNavigator;
import com.example.moneymanagerpro.auth.LocalProfilePhotoStore;
import com.example.moneymanagerpro.utils.ThemeManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;

public class UserProfileActivity extends AppCompatActivity {

    private ImageView imgProfilePhoto;
    private TextView txtProfileInitial;
    private TextView txtProfileEmail;
    private TextView txtVerificationStatus;
    private TextInputLayout inputDisplayName;
    private TextInputEditText etDisplayName;
    private MaterialButton btnSaveProfile;
    private MaterialButton btnVerifyEmail;

    private FirebaseAuth firebaseAuth;
    private boolean updateInProgress;

    private final ActivityResultLauncher<String[]> photoPicker =
            registerForActivityResult(
                    new ActivityResultContracts.OpenDocument(),
                    this::onPhotoSelected
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applySavedTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_profile);

        firebaseAuth = FirebaseAuth.getInstance();
        bindViews();
        setupActions();
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (firebaseAuth.getCurrentUser() == null) {
            AuthNavigator.logout(this);
            return;
        }

        firebaseAuth.getCurrentUser()
                .reload()
                .addOnCompleteListener(task -> loadProfile());
    }

    private void bindViews() {
        imgProfilePhoto = findViewById(R.id.imgProfilePhoto);
        txtProfileInitial = findViewById(R.id.txtProfileInitial);
        txtProfileEmail = findViewById(R.id.txtProfileEmail);
        txtVerificationStatus =
                findViewById(R.id.txtProfileVerificationStatus);
        inputDisplayName = findViewById(R.id.inputProfileDisplayName);
        etDisplayName = findViewById(R.id.etProfileDisplayName);
        btnSaveProfile = findViewById(R.id.btnSaveProfile);
        btnVerifyEmail = findViewById(R.id.btnVerifyProfileEmail);
    }

    private void setupActions() {
        findViewById(R.id.btnProfileBack)
                .setOnClickListener(view -> finish());

        findViewById(R.id.btnChooseProfilePhoto)
                .setOnClickListener(view ->
                        photoPicker.launch(
                                new String[]{"image/*"}
                        )
                );

        findViewById(R.id.btnRemoveProfilePhoto)
                .setOnClickListener(view -> {
                    LocalProfilePhotoStore.clear(this);
                    showProfilePhoto(null);
                    showMessage("Local profile photo removed");
                });

        btnSaveProfile.setOnClickListener(view -> saveDisplayName());
        btnVerifyEmail.setOnClickListener(view -> sendVerificationEmail());

        findViewById(R.id.btnChangeProfilePassword)
                .setOnClickListener(view ->
                        startActivity(
                                AuthenticationActivity.createIntent(
                                        this,
                                        AuthenticationActivity.MODE_CHANGE_PASSWORD
                                )
                        )
                );

        findViewById(R.id.btnProfileLogout)
                .setOnClickListener(view -> confirmLogout());
    }

    private void loadProfile() {
        FirebaseUser user = firebaseAuth.getCurrentUser();

        if (user == null) {
            return;
        }

        String displayName = safe(user.getDisplayName());
        String email = safe(user.getEmail());

        etDisplayName.setText(displayName);
        txtProfileEmail.setText(email);

        if (user.isEmailVerified()) {
            txtVerificationStatus.setText("Email verified");
            txtVerificationStatus.setTextColor(
                    getColor(R.color.success)
            );
            btnVerifyEmail.setVisibility(View.GONE);
        } else {
            txtVerificationStatus.setText("Email verification pending");
            txtVerificationStatus.setTextColor(
                    getColor(R.color.orange)
            );
            btnVerifyEmail.setVisibility(View.VISIBLE);
        }

        showProfilePhoto(LocalProfilePhotoStore.get(this));
        updateInitial(displayName, email);
    }

    private void saveDisplayName() {
        if (updateInProgress) {
            return;
        }

        FirebaseUser user = firebaseAuth.getCurrentUser();
        String displayName = value(etDisplayName);

        if (user == null) {
            AuthNavigator.logout(this);
            return;
        }

        if (displayName.length() < 2) {
            inputDisplayName.setError("Enter your full name");
            return;
        }

        inputDisplayName.setError(null);
        setLoading(true);

        UserProfileChangeRequest request =
                new UserProfileChangeRequest.Builder()
                        .setDisplayName(displayName)
                        .build();

        user.updateProfile(request)
                .addOnCompleteListener(task -> {
                    setLoading(false);

                    if (task.isSuccessful()) {
                        updateInitial(
                                displayName,
                                safe(user.getEmail())
                        );
                        showMessage("Profile updated");
                    } else {
                        showMessage("Unable to update profile");
                    }
                });
    }

    private void sendVerificationEmail() {
        FirebaseUser user = firebaseAuth.getCurrentUser();

        if (user == null) {
            AuthNavigator.logout(this);
            return;
        }

        btnVerifyEmail.setEnabled(false);

        user.sendEmailVerification()
                .addOnCompleteListener(task -> {
                    btnVerifyEmail.setEnabled(true);

                    if (task.isSuccessful()) {
                        showMessage("Verification email sent");
                    } else {
                        showMessage(
                                "Unable to send verification email"
                        );
                    }
                });
    }

    private void onPhotoSelected(Uri uri) {
        if (uri == null) {
            return;
        }

        try {
            getContentResolver().takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
            );
        } catch (SecurityException ignored) {
            // Some document providers grant access without a persistable flag.
        }

        LocalProfilePhotoStore.save(this, uri);
        showProfilePhoto(uri);
        showMessage("Profile photo saved on this device");
    }

    private void showProfilePhoto(Uri uri) {
        if (uri == null) {
            imgProfilePhoto.setImageDrawable(null);
            imgProfilePhoto.setVisibility(View.GONE);
            txtProfileInitial.setVisibility(View.VISIBLE);
            return;
        }

        try {
            imgProfilePhoto.setImageURI(null);
            imgProfilePhoto.setImageURI(uri);
            imgProfilePhoto.setVisibility(View.VISIBLE);
            txtProfileInitial.setVisibility(View.GONE);
        } catch (Exception exception) {
            LocalProfilePhotoStore.clear(this);
            imgProfilePhoto.setVisibility(View.GONE);
            txtProfileInitial.setVisibility(View.VISIBLE);
        }
    }

    private void updateInitial(String name, String email) {
        String source = !safe(name).isEmpty()
                ? safe(name)
                : safe(email);

        String initial = source.isEmpty()
                ? "U"
                : source.substring(0, 1).toUpperCase();

        txtProfileInitial.setText(initial);
    }

    private void confirmLogout() {
        new AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage(
                        "Do you want to logout from Money Manager Pro?"
                )
                .setNegativeButton("Cancel", null)
                .setPositiveButton(
                        "Logout",
                        (dialog, which) ->
                                AuthNavigator.logout(this)
                )
                .show();
    }

    private void setLoading(boolean loading) {
        updateInProgress = loading;
        btnSaveProfile.setEnabled(!loading);
        btnSaveProfile.setText(
                loading ? "Saving..." : "Save Profile"
        );
    }

    private String value(TextInputEditText editText) {
        return editText.getText() == null
                ? ""
                : editText.getText().toString().trim();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private void showMessage(String message) {
        Toast.makeText(
                this,
                message,
                Toast.LENGTH_LONG
        ).show();
    }
}
