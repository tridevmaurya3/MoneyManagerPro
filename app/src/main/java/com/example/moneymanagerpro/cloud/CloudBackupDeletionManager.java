package com.example.moneymanagerpro.cloud;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * Permanently deletes the signed-in user's latest encrypted cloud backup.
 *
 * This manager deletes only cloud-backup data. It never deletes the local
 * Room database, offline backup files, the Firebase Authentication account,
 * or the locally saved recovery passphrase.
 */
public final class CloudBackupDeletionManager {

    private static final String TAG =
            "CloudBackupDeletion";

    private final Context applicationContext;

    private final FirebaseAuth firebaseAuth;

    private final CloudBackupUploader cloudBackupUploader;

    private final BackupSchedulePreferences schedulePreferences;

    public CloudBackupDeletionManager(
            @NonNull Context context
    ) {
        applicationContext =
                context.getApplicationContext();

        firebaseAuth =
                FirebaseAuth.getInstance();

        cloudBackupUploader =
                new CloudBackupUploader();

        schedulePreferences =
                new BackupSchedulePreferences(
                        applicationContext
                );
    }

    /**
     * Checks whether the current account has a usable encrypted backup.
     */
    public void checkBackupAvailability(
            @NonNull AvailabilityCallback callback
    ) {
        FirebaseUser firebaseUser =
                firebaseAuth.getCurrentUser();

        Exception accountError =
                validateCurrentUser(
                        firebaseUser
                );

        if (accountError != null) {
            callback.onError(
                    accountError
            );

            return;
        }

        cloudBackupUploader.hasUsableEncryptedBackup(
                firebaseUser,
                new CloudBackupUploader.AvailabilityCallback() {
                    @Override
                    public void onResult(
                            boolean available
                    ) {
                        callback.onResult(
                                available
                        );
                    }

                    @Override
                    public void onError(
                            @NonNull Exception exception
                    ) {
                        callback.onError(
                                exception
                        );
                    }
                }
        );
    }

    /**
     * Loads current server metadata for confirmation UI.
     * A null result means that no backup is stored for this account.
     */
    public void loadBackupMetadata(
            @NonNull MetadataCallback callback
    ) {
        FirebaseUser firebaseUser =
                firebaseAuth.getCurrentUser();

        Exception accountError =
                validateCurrentUser(
                        firebaseUser
                );

        if (accountError != null) {
            callback.onError(
                    accountError
            );

            return;
        }

        cloudBackupUploader.loadLatestBackupMetadata(
                firebaseUser,
                new CloudBackupUploader.MetadataCallback() {
                    @Override
                    public void onLoaded(
                            @Nullable CloudBackupUploader
                                    .CloudBackupMetadata metadata
                    ) {
                        callback.onLoaded(
                                metadata
                        );
                    }

                    @Override
                    public void onError(
                            @NonNull Exception exception
                    ) {
                        callback.onError(
                                exception
                        );
                    }
                }
        );
    }

    /**
     * Permanently deletes metadata and every encrypted chunk belonging to
     * the currently signed-in Firebase account.
     */
    public void deleteLatestCloudBackup(
            @NonNull DeleteCallback callback
    ) {
        FirebaseUser firebaseUser =
                firebaseAuth.getCurrentUser();

        Exception accountError =
                validateCurrentUser(
                        firebaseUser
                );

        if (accountError != null) {
            callback.onError(
                    accountError
            );

            return;
        }

        String expectedFirebaseUserId =
                firebaseUser.getUid();

        cloudBackupUploader.loadLatestBackupMetadata(
                firebaseUser,
                new CloudBackupUploader.MetadataCallback() {
                    @Override
                    public void onLoaded(
                            @Nullable CloudBackupUploader
                                    .CloudBackupMetadata metadata
                    ) {
                        if (metadata == null) {
                            callback.onNoBackupFound();

                            return;
                        }

                        if (!expectedFirebaseUserId.equals(
                                metadata.getOwnerUserId()
                        )) {
                            callback.onError(
                                    new SecurityException(
                                            "Cloud backup belongs to another "
                                                    + "Firebase account."
                                    )
                            );

                            return;
                        }

                        FirebaseUser currentUser =
                                firebaseAuth.getCurrentUser();

                        if (currentUser == null
                                || !expectedFirebaseUserId.equals(
                                currentUser.getUid()
                        )) {
                            callback.onError(
                                    new SecurityException(
                                            "Firebase account changed before "
                                                    + "cloud backup deletion."
                                    )
                            );

                            return;
                        }

                        performPermanentDeletion(
                                currentUser,
                                metadata,
                                callback
                        );
                    }

                    @Override
                    public void onError(
                            @NonNull Exception exception
                    ) {
                        callback.onError(
                                exception
                        );
                    }
                }
        );
    }

    private void performPermanentDeletion(
            @NonNull FirebaseUser firebaseUser,
            @NonNull CloudBackupUploader
                    .CloudBackupMetadata metadata,
            @NonNull DeleteCallback callback
    ) {
        cloudBackupUploader.deleteLatestBackup(
                firebaseUser,
                new CloudBackupUploader.DeleteCallback() {
                    @Override
                    public void onSuccess() {
                        cleanupLocalCloudBackupState(
                                firebaseUser.getUid()
                        );

                        callback.onSuccess(
                                new DeleteResult(
                                        firebaseUser.getUid(),
                                        metadata.getBackupId(),
                                        metadata.getTotalRecordCount(),
                                        metadata.getChunkCount(),
                                        metadata.getEncryptedByteCount(),
                                        System.currentTimeMillis()
                                )
                        );
                    }

                    @Override
                    public void onError(
                            @NonNull Exception exception
                    ) {
                        callback.onError(
                                exception
                        );
                    }
                }
        );
    }

    /**
     * Cancels future cloud work and clears stale local cloud status. The
     * cloud schedule is reset to Manual only so the deleted backup is not
     * recreated automatically without a new user action.
     */
    private void cleanupLocalCloudBackupState(
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
                    "Cloud backup was deleted, but scheduled work "
                            + "could not be cancelled.",
                    exception
            );
        }

        try {
            schedulePreferences.clearCloudAccountData(
                    firebaseUserId
            );

            schedulePreferences.resetCloudSchedule(
                    firebaseUserId
            );

        } catch (Exception exception) {
            Log.w(
                    TAG,
                    "Cloud backup was deleted, but local cloud status "
                            + "could not be fully reset.",
                    exception
            );

            try {
                schedulePreferences.setCloudNextScheduledAt(
                        firebaseUserId,
                        0L
                );

            } catch (Exception ignored) {
                // Firestore deletion already succeeded. Local status is
                // best-effort cleanup and must not report server deletion
                // as failed.
            }
        }
    }

    @Nullable
    private Exception validateCurrentUser(
            @Nullable FirebaseUser firebaseUser
    ) {
        if (firebaseUser == null) {
            return new IllegalStateException(
                    "Cloud backup operation requires a signed-in "
                            + "Firebase account."
            );
        }

        String firebaseUserId =
                firebaseUser
                        .getUid()
                        .trim();

        if (firebaseUserId.isEmpty()) {
            return new IllegalStateException(
                    "Firebase account UID is unavailable."
            );
        }

        if (!firebaseUser.isEmailVerified()) {
            return new IllegalStateException(
                    "Permanent cloud backup deletion requires "
                            + "a verified Firebase email account."
            );
        }

        return null;
    }

    public interface AvailabilityCallback {

        void onResult(
                boolean available
        );

        void onError(
                @NonNull Exception exception
        );
    }

    public interface MetadataCallback {

        void onLoaded(
                @Nullable CloudBackupUploader
                        .CloudBackupMetadata metadata
        );

        void onError(
                @NonNull Exception exception
        );
    }

    public interface DeleteCallback {

        void onSuccess(
                @NonNull DeleteResult result
        );

        void onNoBackupFound();

        void onError(
                @NonNull Exception exception
        );
    }

    public static final class DeleteResult {

        private final String firebaseUserId;

        private final String deletedBackupId;

        private final int deletedRecordCount;

        private final int deletedChunkCount;

        private final int deletedEncryptedByteCount;

        private final long deletedAtMillis;

        private DeleteResult(
                @NonNull String firebaseUserId,
                @NonNull String deletedBackupId,
                int deletedRecordCount,
                int deletedChunkCount,
                int deletedEncryptedByteCount,
                long deletedAtMillis
        ) {
            this.firebaseUserId =
                    firebaseUserId;

            this.deletedBackupId =
                    deletedBackupId;

            this.deletedRecordCount =
                    Math.max(
                            0,
                            deletedRecordCount
                    );

            this.deletedChunkCount =
                    Math.max(
                            0,
                            deletedChunkCount
                    );

            this.deletedEncryptedByteCount =
                    Math.max(
                            0,
                            deletedEncryptedByteCount
                    );

            this.deletedAtMillis =
                    Math.max(
                            0L,
                            deletedAtMillis
                    );
        }

        @NonNull
        public String getFirebaseUserId() {
            return firebaseUserId;
        }

        @NonNull
        public String getDeletedBackupId() {
            return deletedBackupId;
        }

        public int getDeletedRecordCount() {
            return deletedRecordCount;
        }

        public int getDeletedChunkCount() {
            return deletedChunkCount;
        }

        public int getDeletedEncryptedByteCount() {
            return deletedEncryptedByteCount;
        }

        public long getDeletedAtMillis() {
            return deletedAtMillis;
        }
    }
}