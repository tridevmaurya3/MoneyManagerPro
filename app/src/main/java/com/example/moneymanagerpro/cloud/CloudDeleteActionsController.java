package com.example.moneymanagerpro.cloud;

import android.app.Activity;
import android.content.DialogInterface;
import android.text.Editable;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.auth.AuthNavigator;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Restores and handles the two permanent cloud-delete buttons on
 * BackupActivity without changing the existing large backup controller.
 */
public final class CloudDeleteActionsController {

    private static final String CONFIRM_BACKUP =
            "DELETE CLOUD BACKUP";

    private static final String CONFIRM_ACCOUNT =
            "DELETE ACCOUNT";

    private final Activity activity;
    private final FirebaseAuth firebaseAuth;
    private final CloudBackupDeletionManager backupDeletionManager;
    private final BackupSchedulePreferences schedulePreferences;
    private final CloudBackupKeyVault keyVault;

    private final MaterialButton btnDeleteCloudBackup;
    private final MaterialButton btnDeleteCloudAccount;
    private final MaterialButton btnCloudBackupNow;
    private final MaterialButton btnCloudRestore;
    private final TextView txtCloudBackupAvailability;
    private final TextView txtCloudBackupStatus;

    private boolean operationRunning;

    public CloudDeleteActionsController(
            @NonNull Activity activity
    ) {
        this.activity = activity;
        firebaseAuth = FirebaseAuth.getInstance();
        backupDeletionManager =
                new CloudBackupDeletionManager(
                        activity.getApplicationContext()
                );
        schedulePreferences =
                new BackupSchedulePreferences(
                        activity.getApplicationContext()
                );
        keyVault =
                new CloudBackupKeyVault(
                        activity.getApplicationContext()
                );

        btnDeleteCloudBackup =
                requireView(R.id.btnDeleteCloudBackup);
        btnDeleteCloudAccount =
                requireView(R.id.btnDeleteCloudAccount);
        btnCloudBackupNow =
                requireView(R.id.btnCloudBackupNow);
        btnCloudRestore =
                requireView(R.id.btnCloudRestore);
        txtCloudBackupAvailability =
                requireView(R.id.txtCloudBackupAvailability);
        txtCloudBackupStatus =
                requireView(R.id.txtCloudBackupStatus);
    }

    public void attach() {
        btnDeleteCloudBackup.setOnClickListener(
                view -> startBackupDeletion()
        );
        btnDeleteCloudAccount.setOnClickListener(
                view -> startAccountDeletion()
        );
        refreshButtonState();
    }

    private void refreshButtonState() {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        boolean verified =
                !operationRunning
                        && user != null
                        && user.isEmailVerified();
        boolean emailAccount =
                verified
                        && user.getEmail() != null
                        && !user.getEmail().trim().isEmpty();

        setButtonState(btnDeleteCloudBackup, verified);
        setButtonState(btnDeleteCloudAccount, emailAccount);
    }

    private void startBackupDeletion() {
        FirebaseUser user = validatedUser();
        if (user == null || !canStart()) {
            return;
        }

        setBusy(
                "Checking Cloud Backup...",
                "Firebase server से encrypted backup जाँचा जा रहा है।"
        );

        String expectedUid = user.getUid();
        backupDeletionManager.loadBackupMetadata(
                new CloudBackupDeletionManager.MetadataCallback() {
                    @Override
                    public void onLoaded(
                            @Nullable CloudBackupUploader
                                    .CloudBackupMetadata metadata
                    ) {
                        if (!sameUser(expectedUid)) {
                            finishBusy();
                            return;
                        }

                        if (metadata == null) {
                            showNoBackup();
                            return;
                        }

                        showBackupPreview(user, metadata);
                    }

                    @Override
                    public void onError(
                            @NonNull Exception exception
                    ) {
                        showError(
                                "Cloud Backup Check Failed",
                                message(exception,
                                        "Cloud backup details could not be loaded.")
                        );
                    }
                }
        );
    }

    private void showBackupPreview(
            @NonNull FirebaseUser user,
            @NonNull CloudBackupUploader.CloudBackupMetadata metadata
    ) {
        String details =
                "Backup created: "
                        + formatDateTime(metadata.getBackupCreatedAt())
                        + "\nRecords: "
                        + metadata.getTotalRecordCount()
                        + "\nEncrypted size: "
                        + formatFileSize(metadata.getEncryptedByteCount())
                        + "\nChunks: "
                        + metadata.getChunkCount()
                        + "\nBackup ID: "
                        + metadata.getBackupId()
                        + "\n\nLocal data और offline backups सुरक्षित रहेंगे।";

        AlertDialog dialog =
                new AlertDialog.Builder(activity)
                        .setTitle("Delete Cloud Backup?")
                        .setMessage(details)
                        .setPositiveButton(
                                "Continue",
                                (d, w) -> showFinalBackupDialog(user)
                        )
                        .setNegativeButton(
                                "Cancel",
                                (d, w) -> cancel("Cloud backup deletion cancelled.")
                        )
                        .create();
        dialog.setOnCancelListener(
                d -> cancel("Cloud backup deletion cancelled.")
        );
        dialog.show();
    }

    private void showFinalBackupDialog(
            @NonNull FirebaseUser user
    ) {
        EditText confirmation = confirmationInput(
                "Type " + CONFIRM_BACKUP
        );
        LinearLayout box = dialogBox();
        box.addView(confirmation);

        AlertDialog dialog =
                new AlertDialog.Builder(activity)
                        .setTitle("Final Backup Deletion")
                        .setMessage(
                                "जारी रखने के लिए "
                                        + CONFIRM_BACKUP
                                        + " लिखें।"
                        )
                        .setView(box)
                        .setPositiveButton("Delete Permanently", null)
                        .setNegativeButton(
                                "Cancel",
                                (d, w) -> cancel("Cloud backup deletion cancelled.")
                        )
                        .create();

        dialog.setOnShowListener(
                d -> dialog.getButton(
                        DialogInterface.BUTTON_POSITIVE
                ).setOnClickListener(v -> {
                    if (!CONFIRM_BACKUP.equals(
                            confirmation.getText().toString().trim()
                    )) {
                        confirmation.setError(
                                CONFIRM_BACKUP + " बड़े अक्षरों में लिखें।"
                        );
                        return;
                    }
                    dialog.dismiss();
                    deleteBackup(user);
                })
        );
        dialog.setOnCancelListener(
                d -> cancel("Cloud backup deletion cancelled.")
        );
        dialog.show();
    }

    private void deleteBackup(
            @NonNull FirebaseUser user
    ) {
        String expectedUid = user.getUid();
        setBusy(
                "Deleting Cloud Backup...",
                "Encrypted chunks और metadata स्थायी रूप से हटाए जा रहे हैं।"
        );

        backupDeletionManager.deleteLatestCloudBackup(
                new CloudBackupDeletionManager.DeleteCallback() {
                    @Override
                    public void onSuccess(
                            @NonNull CloudBackupDeletionManager.DeleteResult result
                    ) {
                        if (!sameUser(expectedUid)) {
                            finishBusy();
                            return;
                        }
                        finishBusy();
                        txtCloudBackupAvailability.setText("No cloud backup");
                        txtCloudBackupStatus.setText(
                                "Cloud backup permanently deleted.\n"
                                        + "Deleted: "
                                        + formatDateTime(result.getDeletedAtMillis())
                                        + "\nRecords removed: "
                                        + result.getDeletedRecordCount()
                        );
                        showInfo(
                                "Cloud Backup Deleted",
                                "Encrypted backup और सभी chunks Firebase से हट गए हैं।\n\n"
                                        + "Local finance data सुरक्षित है।"
                        );
                    }

                    @Override
                    public void onNoBackupFound() {
                        showNoBackup();
                    }

                    @Override
                    public void onError(
                            @NonNull Exception exception
                    ) {
                        showError(
                                "Cloud Backup Delete Failed",
                                message(exception,
                                        "Cloud backup could not be deleted.")
                        );
                    }
                }
        );
    }

    private void startAccountDeletion() {
        FirebaseUser user = validatedUser();
        if (user == null || !canStart()) {
            return;
        }

        String email = user.getEmail();
        if (email == null || email.trim().isEmpty()) {
            showInfo(
                    "Password Account Required",
                    "इस account में email/password reauthentication उपलब्ध नहीं है।"
            );
            return;
        }

        operationRunning = true;
        refreshButtonState();

        AlertDialog dialog =
                new AlertDialog.Builder(activity)
                        .setTitle("Permanently Delete Cloud Account?")
                        .setMessage(
                                "Account: " + email.trim()
                                        + "\n\nEncrypted cloud backup, Firebase sign-in account, "
                                        + "cloud schedule और saved recovery passphrase हटेंगे।\n\n"
                                        + "Local Room data और offline backup files सुरक्षित रहेंगे।"
                        )
                        .setPositiveButton(
                                "Continue",
                                (d, w) -> showFinalAccountDialog(user)
                        )
                        .setNegativeButton(
                                "Cancel",
                                (d, w) -> cancel("Cloud account deletion cancelled.")
                        )
                        .create();
        dialog.setOnCancelListener(
                d -> cancel("Cloud account deletion cancelled.")
        );
        dialog.show();
    }

    private void showFinalAccountDialog(
            @NonNull FirebaseUser user
    ) {
        EditText password = passwordInput("Current Firebase password");
        EditText confirmation = confirmationInput(
                "Type " + CONFIRM_ACCOUNT
        );
        LinearLayout box = dialogBox();
        box.addView(password);

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
        params.topMargin = dp(10);
        box.addView(confirmation, params);

        AlertDialog dialog =
                new AlertDialog.Builder(activity)
                        .setTitle("Final Account Deletion")
                        .setMessage(
                                "Current password दर्ज करें और "
                                        + CONFIRM_ACCOUNT
                                        + " लिखें।"
                        )
                        .setView(box)
                        .setPositiveButton("Delete Account Permanently", null)
                        .setNegativeButton(
                                "Cancel",
                                (d, w) -> cancel("Cloud account deletion cancelled.")
                        )
                        .create();

        dialog.setOnShowListener(
                d -> dialog.getButton(
                        DialogInterface.BUTTON_POSITIVE
                ).setOnClickListener(v -> {
                    if (!CONFIRM_ACCOUNT.equals(
                            confirmation.getText().toString().trim()
                    )) {
                        confirmation.setError(
                                CONFIRM_ACCOUNT + " बड़े अक्षरों में लिखें।"
                        );
                        return;
                    }

                    char[] passwordChars = toChars(password.getText());
                    if (passwordChars.length == 0) {
                        password.setError("Current Firebase password दर्ज करें।");
                        return;
                    }

                    password.setText("");
                    confirmation.setText("");
                    dialog.dismiss();
                    reauthenticateAndDeleteAccount(user, passwordChars);
                })
        );
        dialog.setOnCancelListener(
                d -> cancel("Cloud account deletion cancelled.")
        );
        dialog.show();
    }

    private void reauthenticateAndDeleteAccount(
            @NonNull FirebaseUser user,
            @NonNull char[] password
    ) {
        String email = user.getEmail();
        String expectedUid = user.getUid();
        String passwordText = new String(password);
        CloudBackupEncryption.clearSensitiveCharacters(password);

        setBusy(
                "Deleting Cloud Account...",
                "Password verify किया जा रहा है।"
        );

        user.reauthenticate(
                EmailAuthProvider.getCredential(email, passwordText)
        ).addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                showError(
                        "Password Verification Failed",
                        message(task.getException(),
                                "Current Firebase password is incorrect.")
                );
                return;
            }
            deleteBackupBeforeAccount(user, expectedUid);
        });
    }

    private void deleteBackupBeforeAccount(
            @NonNull FirebaseUser user,
            @NonNull String expectedUid
    ) {
        backupDeletionManager.deleteLatestCloudBackup(
                new CloudBackupDeletionManager.DeleteCallback() {
                    @Override
                    public void onSuccess(
                            @NonNull CloudBackupDeletionManager.DeleteResult result
                    ) {
                        deleteFirebaseAccount(user, expectedUid, true);
                    }

                    @Override
                    public void onNoBackupFound() {
                        deleteFirebaseAccount(user, expectedUid, false);
                    }

                    @Override
                    public void onError(
                            @NonNull Exception exception
                    ) {
                        showError(
                                "Cloud Account Delete Failed",
                                "Cloud backup delete नहीं हुआ, इसलिए account सुरक्षित रखा गया।\n\n"
                                        + message(exception, "Unknown cloud error.")
                        );
                    }
                }
        );
    }

    private void deleteFirebaseAccount(
            @NonNull FirebaseUser user,
            @NonNull String expectedUid,
            boolean backupDeleted
    ) {
        if (!sameUser(expectedUid)) {
            showError(
                    "Account Changed",
                    "Firebase account बदल गया है।"
            );
            return;
        }

        user.delete().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                showError(
                        "Cloud Account Delete Failed",
                        message(task.getException(),
                                backupDeleted
                                        ? "Cloud backup हट गया, लेकिन account delete नहीं हुआ।"
                                        : "Firebase account delete नहीं हुआ।")
                );
                return;
            }

            cleanupLocalCloudState(expectedUid);
            firebaseAuth.signOut();
            operationRunning = false;

            new AlertDialog.Builder(activity)
                    .setTitle("Cloud Account Deleted")
                    .setMessage(
                            "Firebase account permanently deleted.\n\n"
                                    + (backupDeleted
                                    ? "Encrypted cloud backup भी delete हो गया।\n"
                                    : "Cloud backup उपलब्ध नहीं था।\n")
                                    + "Local finance data और offline backups सुरक्षित हैं।"
                    )
                    .setCancelable(false)
                    .setPositiveButton(
                            "Go to Login",
                            (d, w) -> AuthNavigator.logout(activity)
                    )
                    .show();
        });
    }

    private void cleanupLocalCloudState(
            @NonNull String uid
    ) {
        try {
            CloudBackupScheduler.cancelForAccount(
                    activity.getApplicationContext(), uid
            );
        } catch (Exception ignored) {
        }
        try {
            schedulePreferences.clearCloudAccountData(uid);
        } catch (Exception ignored) {
        }
        try {
            keyVault.clearPassphrase(uid);
        } catch (Exception ignored) {
        }
    }

    private FirebaseUser validatedUser() {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user == null) {
            showInfo(
                    "Cloud Account Required",
                    "Firebase account में sign in करें।"
            );
            return null;
        }
        if (!user.isEmailVerified()) {
            showInfo(
                    "Email Verification Required",
                    "Permanent cloud action से पहले email verify करें।"
            );
            return null;
        }
        return user;
    }

    private boolean canStart() {
        if (operationRunning) {
            Toast.makeText(
                    activity,
                    "Cloud delete operation पहले से चल रही है।",
                    Toast.LENGTH_SHORT
            ).show();
            return false;
        }

        String state = String.valueOf(
                txtCloudBackupAvailability.getText()
        ).toLowerCase(Locale.US);
        if (state.contains("upload")
                || state.contains("download")
                || state.contains("restor")
                || state.contains("validat")
                || state.contains("waiting")
                || state.contains("queued")) {
            Toast.makeText(
                    activity,
                    "Backup या Restore पूरा होने के बाद delete करें।",
                    Toast.LENGTH_LONG
            ).show();
            return false;
        }
        return true;
    }

    private void setBusy(
            @NonNull String buttonText,
            @NonNull String status
    ) {
        operationRunning = true;
        setButtonState(btnDeleteCloudBackup, false);
        setButtonState(btnDeleteCloudAccount, false);
        setButtonState(btnCloudBackupNow, false);
        setButtonState(btnCloudRestore, false);

        if (buttonText.contains("Account")) {
            btnDeleteCloudAccount.setText(buttonText);
        } else {
            btnDeleteCloudBackup.setText(buttonText);
        }
        txtCloudBackupStatus.setText(status);
    }

    private void finishBusy() {
        operationRunning = false;
        btnDeleteCloudBackup.setText(
                "Permanently Delete Cloud Backup"
        );
        btnDeleteCloudAccount.setText(
                "Permanently Delete Cloud Account"
        );

        FirebaseUser user = firebaseAuth.getCurrentUser();
        setButtonState(btnCloudBackupNow, user != null);
        setButtonState(
                btnCloudRestore,
                user != null && user.isEmailVerified()
        );
        refreshButtonState();
    }

    private void cancel(
            @NonNull String status
    ) {
        finishBusy();
        txtCloudBackupStatus.setText(status);
    }

    private void showNoBackup() {
        finishBusy();
        txtCloudBackupAvailability.setText("No cloud backup");
        txtCloudBackupStatus.setText(
                "इस Firebase account में encrypted cloud backup नहीं मिला।"
        );
        showInfo(
                "No Cloud Backup",
                "Delete करने के लिए कोई cloud backup उपलब्ध नहीं है।"
        );
    }

    private void showError(
            @NonNull String title,
            @NonNull String details
    ) {
        finishBusy();
        txtCloudBackupAvailability.setText("Operation failed");
        txtCloudBackupStatus.setText(details);
        showInfo(title, details);
    }

    private void showInfo(
            @NonNull String title,
            @NonNull String details
    ) {
        if (usable()) {
            new AlertDialog.Builder(activity)
                    .setTitle(title)
                    .setMessage(details)
                    .setPositiveButton("OK", null)
                    .show();
        }
    }

    private boolean sameUser(
            @NonNull String uid
    ) {
        FirebaseUser current = firebaseAuth.getCurrentUser();
        return usable()
                && current != null
                && uid.equals(current.getUid());
    }

    private boolean usable() {
        return !activity.isFinishing()
                && !activity.isDestroyed();
    }

    private void setButtonState(
            @NonNull MaterialButton button,
            boolean enabled
    ) {
        button.setEnabled(enabled);
        button.setAlpha(enabled ? 1F : 0.55F);
    }

    @NonNull
    private LinearLayout dialogBox() {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(22), dp(8), dp(22), 0);
        return box;
    }

    @NonNull
    private EditText confirmationInput(
            @NonNull String hint
    ) {
        EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setHint(hint);
        input.setInputType(
                InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
        );
        return input;
    }

    @NonNull
    private EditText passwordInput(
            @NonNull String hint
    ) {
        EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setHint(hint);
        input.setInputType(
                InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_VARIATION_PASSWORD
        );
        return input;
    }

    @NonNull
    private char[] toChars(
            @Nullable Editable editable
    ) {
        if (editable == null || editable.length() == 0) {
            return new char[0];
        }
        char[] result = new char[editable.length()];
        for (int i = 0; i < editable.length(); i++) {
            result[i] = editable.charAt(i);
        }
        return result;
    }

    @NonNull
    private String formatDateTime(long value) {
        if (value <= 0L) {
            return "Not available";
        }
        return new SimpleDateFormat(
                "dd MMM yyyy, hh:mm a",
                Locale.getDefault()
        ).format(new Date(value));
    }

    @NonNull
    private String formatFileSize(long bytes) {
        if (bytes < 1024L) {
            return Math.max(0L, bytes) + " Bytes";
        }
        double kb = bytes / 1024D;
        if (kb < 1024D) {
            return String.format(
                    Locale.getDefault(), "%.2f KB", kb
            );
        }
        return String.format(
                Locale.getDefault(), "%.2f MB", kb / 1024D
        );
    }

    @NonNull
    private String message(
            @Nullable Throwable error,
            @NonNull String fallback
    ) {
        Throwable current = error;
        String result = "";
        for (int i = 0; current != null && i < 10; i++) {
            if (current.getMessage() != null
                    && !current.getMessage().trim().isEmpty()) {
                result = current.getMessage().trim();
            }
            current = current.getCause();
        }
        if (result.isEmpty()) {
            return fallback;
        }
        return result.replace('\n', ' ')
                .replace('\r', ' ')
                .trim();
    }

    private int dp(int value) {
        return Math.round(
                value
                        * activity.getResources()
                        .getDisplayMetrics().density
        );
    }

    @NonNull
    @SuppressWarnings("unchecked")
    private <T extends View> T requireView(int id) {
        View view = activity.findViewById(id);
        if (view == null) {
            throw new IllegalStateException(
                    "Required cloud-delete view is missing."
            );
        }
        return (T) view;
    }
}
