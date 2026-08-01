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
 * This manager:
 *
 * 1. Verifies the active Firebase account.
 * 2. Requires a verified email account.
 * 3. Checks whether a usable encrypted backup exists.
 * 4. Deletes all encrypted chunk documents.
 * 5. Deletes the latest backup metadata document.
 * 6. Cancels automatic cloud backup scheduling.
 * 7. Clears local cloud-backup status metadata.
 *
 * This manager does NOT:
 *
 * - delete the Firebase Authentication account,
 * - delete offline backups,
 * - delete local Room data,
 * - remove the locally saved recovery passphrase,
 * - sign the user out.
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
     * Checks whether the current Firebase account has a usable encrypted
     * cloud backup.
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
     * Loads the current cloud-backup metadata before the final deletion
     * confirmation is displayed.
     *
     * A null metadata result means no cloud backup is currently stored.
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
     * Permanently deletes the latest encrypted backup belonging to the
     * currently signed-in Firebase account.
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

        /*
         * Re-check server metadata immediately before deletion.
         * This provides a clear No Backup result and verifies ownership.
         */
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
     * Stops future automatic uploads and clears local backup status after
     * the Firestore deletion has completed successfully.
     *
     * User-selected cloud frequency is intentionally not overwritten.
     * Therefore the user can later save the schedule again after creating
     * a new cloud backup and recovery passphrase.
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
                    "Cloud backup was deleted, but automatic work "
                            + "could not be cancelled.",
                    exception
            );
        }

        try {
            schedulePreferences.setCloudNextScheduledAt(
                    firebaseUserId,
                    0L
            );

        } catch (Exception exception) {
            Log.w(
                    TAG,
                    "Cloud backup was deleted, but next scheduled "
                            + "time could not be cleared.",
                    exception
            );
        }

        /*
         * BackupSchedulePreferences may not expose a complete status-reset
         * method in every project version. The server remains the source of
         * truth. Future UI refreshes must check Firestore availability and
         * display No Cloud Backup Available.
         */
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