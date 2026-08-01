package com.example.moneymanagerpro.cloud;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserInfo;

import java.util.Arrays;

/**
 * Permanently deletes the current Money Manager Pro Firebase account.
 *
 * Security and privacy flow:
 *
 * 1. The active account and password provider are verified.
 * 2. The user is reauthenticated with the current password.
 * 3. The latest encrypted cloud backup is deleted first.
 * 4. The Firebase Authentication account is deleted.
 * 5. Account-specific schedule status and the local recovery passphrase
 *    are removed from this device.
 *
 * Local Room data and offline backup files are never deleted by this class.
 */
public final class CloudAccountDeletionManager {

    private static final String TAG =
            "CloudAccountDeletion";

    private final Context applicationContext;

    private final FirebaseAuth firebaseAuth;

    private final CloudBackupDeletionManager backupDeletionManager;

    private final BackupSchedulePreferences schedulePreferences;

    private final CloudBackupKeyVault keyVault;

    public CloudAccountDeletionManager(
            @NonNull Context context
    ) {
        applicationContext =
                context.getApplicationContext();

        firebaseAuth =
                FirebaseAuth.getInstance();

        backupDeletionManager =
                new CloudBackupDeletionManager(
                        applicationContext
                );

        schedulePreferences =
                new BackupSchedulePreferences(
                        applicationContext
                );

        keyVault =
                new CloudBackupKeyVault(
                        applicationContext
                );
    }

    /**
     * Reauthenticates and permanently deletes the current account together
     * with its encrypted cloud backup.
     *
     * The supplied password is copied synchronously. The caller may clear
     * its original char[] immediately after this method returns.
     */
    public void deleteCurrentAccountAndCloudBackup(
            @NonNull char[] currentPassword,
            @NonNull DeleteAccountCallback callback
    ) {
        FirebaseUser firebaseUser =
                firebaseAuth.getCurrentUser();

        Exception validationError =
                validateAccount(
                        firebaseUser,
                        currentPassword
                );

        if (validationError != null) {
            callback.onError(
                    validationError,
                    false
            );

            return;
        }

        String expectedFirebaseUserId =
                firebaseUser.getUid();

        String email =
                firebaseUser.getEmail();

        char[] passwordCopy =
                Arrays.copyOf(
                        currentPassword,
                        currentPassword.length
                );

        String passwordText;

        try {
            passwordText =
                    new String(
                            passwordCopy
                    );

        } finally {
            Arrays.fill(
                    passwordCopy,
                    '\0'
            );
        }

        AuthCredential credential =
                EmailAuthProvider.getCredential(
                        email,
                        passwordText
                );

        firebaseUser.reauthenticate(
                credential
        ).addOnSuccessListener(
                unused -> deleteBackupBeforeAccount(
                        expectedFirebaseUserId,
                        callback
                )
        ).addOnFailureListener(
                exception -> callback.onError(
                        asException(
                                exception,
                                "Firebase account reauthentication failed."
                        ),
                        false
                )
        );
    }

    private void deleteBackupBeforeAccount(
            @NonNull String expectedFirebaseUserId,
            @NonNull DeleteAccountCallback callback
    ) {
        if (!isSameCurrentUser(
                expectedFirebaseUserId
        )) {
            callback.onError(
                    new SecurityException(
                            "Firebase account changed before deletion."
                    ),
                    false
            );

            return;
        }

        backupDeletionManager.deleteLatestCloudBackup(
                new CloudBackupDeletionManager.DeleteCallback() {
                    @Override
                    public void onSuccess(
                            @NonNull CloudBackupDeletionManager
                                    .DeleteResult result
                    ) {
                        deleteFirebaseAccount(
                                expectedFirebaseUserId,
                                true,
                                result.getDeletedBackupId(),
                                callback
                        );
                    }

                    @Override
                    public void onNoBackupFound() {
                        deleteFirebaseAccount(
                                expectedFirebaseUserId,
                                false,
                                "",
                                callback
                        );
                    }

                    @Override
                    public void onError(
                            @NonNull Exception exception
                    ) {
                        callback.onError(
                                exception,
                                false
                        );
                    }
                }
        );
    }

    private void deleteFirebaseAccount(
            @NonNull String expectedFirebaseUserId,
            boolean cloudBackupDeleted,
            @NonNull String deletedBackupId,
            @NonNull DeleteAccountCallback callback
    ) {
        FirebaseUser currentUser =
                firebaseAuth.getCurrentUser();

        if (currentUser == null
                || !expectedFirebaseUserId.equals(
                currentUser.getUid()
        )) {
            callback.onError(
                    new SecurityException(
                            "Firebase account changed before final deletion."
                    ),
                    cloudBackupDeleted
            );

            return;
        }

        currentUser.delete()
                .addOnSuccessListener(
                        unused -> {
                            cleanupLocalAccountData(
                                    expectedFirebaseUserId
                            );

                            firebaseAuth.signOut();

                            callback.onSuccess(
                                    new DeleteAccountResult(
                                            expectedFirebaseUserId,
                                            cloudBackupDeleted,
                                            deletedBackupId,
                                            System.currentTimeMillis()
                                    )
                            );
                        }
                )
                .addOnFailureListener(
                        exception -> callback.onError(
                                asException(
                                        exception,
                                        "Firebase account could not be deleted."
                                ),
                                cloudBackupDeleted
                        )
                );
    }

    private void cleanupLocalAccountData(
            @NonNull String firebaseUserId
    ) {
        try {
            CloudBackupScheduler.cancelForAccount(
                    applicationContext,
                    firebaseUserId
            );

        } catch (Exception exception) {
            Log.w(
                    TAG,
                    "Account deleted, but scheduled cloud work "
                            + "could not be cancelled.",
                    exception
            );
        }

        try {
            schedulePreferences.clearCloudAccountData(
                    firebaseUserId
            );

        } catch (Exception exception) {
            Log.w(
                    TAG,
                    "Account deleted, but local cloud schedule data "
                            + "could not be cleared.",
                    exception
            );
        }

        try {
            keyVault.clearPassphrase(
                    firebaseUserId
            );

        } catch (Exception exception) {
            Log.w(
                    TAG,
                    "Account deleted, but the local recovery passphrase "
                            + "could not be cleared.",
                    exception
            );
        }
    }

    @Nullable
    private Exception validateAccount(
            @Nullable FirebaseUser firebaseUser,
            @NonNull char[] currentPassword
    ) {
        if (firebaseUser == null) {
            return new IllegalStateException(
                    "Firebase account is not signed in."
            );
        }

        if (firebaseUser.getUid().trim().isEmpty()) {
            return new IllegalStateException(
                    "Firebase account UID is unavailable."
            );
        }

        String email =
                firebaseUser.getEmail();

        if (email == null
                || email.trim().isEmpty()) {
            return new IllegalStateException(
                    "Firebase account email is unavailable."
            );
        }

        if (!firebaseUser.isEmailVerified()) {
            return new IllegalStateException(
                    "Permanent account deletion requires a verified email."
            );
        }

        if (!usesPasswordProvider(
                firebaseUser
        )) {
            return new IllegalStateException(
                    "This account is not using email and password sign-in."
            );
        }

        if (currentPassword.length < 8) {
            return new IllegalArgumentException(
                    "Enter the current account password."
            );
        }

        return null;
    }

    private boolean usesPasswordProvider(
            @NonNull FirebaseUser firebaseUser
    ) {
        for (UserInfo userInfo :
                firebaseUser.getProviderData()) {

            if (EmailAuthProvider.PROVIDER_ID.equals(
                    userInfo.getProviderId()
            )) {
                return true;
            }
        }

        return false;
    }

    private boolean isSameCurrentUser(
            @NonNull String expectedFirebaseUserId
    ) {
        FirebaseUser currentUser =
                firebaseAuth.getCurrentUser();

        return currentUser != null
                && expectedFirebaseUserId.equals(
                currentUser.getUid()
        );
    }

    @NonNull
    private Exception asException(
            @NonNull Throwable throwable,
            @NonNull String fallbackMessage
    ) {
        if (throwable instanceof Exception) {
            return (Exception) throwable;
        }

        return new IllegalStateException(
                fallbackMessage,
                throwable
        );
    }

    public interface DeleteAccountCallback {

        void onSuccess(
                @NonNull DeleteAccountResult result
        );

        void onError(
                @NonNull Exception exception,
                boolean cloudBackupDeleted
        );
    }

    public static final class DeleteAccountResult {

        private final String deletedFirebaseUserId;

        private final boolean cloudBackupDeleted;

        private final String deletedBackupId;

        private final long deletedAtMillis;

        private DeleteAccountResult(
                @NonNull String deletedFirebaseUserId,
                boolean cloudBackupDeleted,
                @NonNull String deletedBackupId,
                long deletedAtMillis
        ) {
            this.deletedFirebaseUserId =
                    deletedFirebaseUserId;

            this.cloudBackupDeleted =
                    cloudBackupDeleted;

            this.deletedBackupId =
                    deletedBackupId;

            this.deletedAtMillis =
                    Math.max(
                            0L,
                            deletedAtMillis
                    );
        }

        @NonNull
        public String getDeletedFirebaseUserId() {
            return deletedFirebaseUserId;
        }

        public boolean wasCloudBackupDeleted() {
            return cloudBackupDeleted;
        }

        @NonNull
        public String getDeletedBackupId() {
            return deletedBackupId;
        }

        public long getDeletedAtMillis() {
            return deletedAtMillis;
        }
    }
}