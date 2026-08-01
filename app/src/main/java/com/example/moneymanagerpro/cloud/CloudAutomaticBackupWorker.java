package com.example.moneymanagerpro.cloud;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestoreException;

import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Creates and uploads encrypted cloud backups.
 *
 * Supported execution modes:
 *
 * AUTOMATIC:
 *
 * 1. Read the account-specific cloud schedule.
 * 2. Check whether automatic backup is enabled.
 * 3. Check whether the backup is due.
 * 4. Build, encrypt and upload the backup.
 *
 * MANUAL:
 *
 * 1. Bypass frequency and due checks.
 * 2. Verify that the expected Firebase account is still signed in.
 * 3. Build, encrypt and upload immediately.
 *
 * Security rules:
 *
 * 1. Recovery passphrase is never written to WorkManager Data.
 * 2. Recovery passphrase is never logged.
 * 3. Plain finance data is never uploaded.
 * 4. Temporary passphrase characters are cleared after use.
 * 5. Every backup is bound to the signed-in Firebase UID.
 * 6. A signed-out or unverified account cannot create a cloud backup.
 */
public final class CloudAutomaticBackupWorker
        extends Worker {

    private static final String TAG =
            "CloudAutoBackupWorker";

    private static final long UPLOAD_TIMEOUT_SECONDS =
            180L;

    /**
     * Attempt 0 is the first execution.
     * Attempts 1-4 are WorkManager retries.
     */
    private static final int MAX_TOTAL_ATTEMPTS =
            5;

    private static final int MAX_FIREBASE_UID_LENGTH =
            256;

    /**
     * Input flag used by the Backup Now button.
     */
    public static final String INPUT_MANUAL_RUN =
            "cloud_backup_manual_run";

    /**
     * Firebase UID that was signed in when the manual request
     * was created.
     *
     * This prevents a queued manual backup from running for another
     * account after the user switches Firebase accounts.
     */
    public static final String INPUT_EXPECTED_FIREBASE_UID =
            "cloud_backup_expected_firebase_uid";

    public static final String OUTPUT_STATUS =
            "cloud_backup_status";

    public static final String OUTPUT_MESSAGE =
            "cloud_backup_message";

    public static final String OUTPUT_BACKUP_ID =
            "cloud_backup_id";

    public static final String OUTPUT_RECORD_COUNT =
            "cloud_backup_record_count";

    public static final String OUTPUT_ENCRYPTED_BYTES =
            "cloud_backup_encrypted_bytes";

    public static final String OUTPUT_COMPLETED_AT =
            "cloud_backup_completed_at";

    public static final String OUTPUT_RUN_MODE =
            "cloud_backup_run_mode";

    public static final String STATUS_SUCCESS =
            "success";

    public static final String STATUS_SKIPPED =
            "skipped";

    public static final String STATUS_FAILURE =
            "failure";

    public static final String RUN_MODE_AUTOMATIC =
            "automatic";

    public static final String RUN_MODE_MANUAL =
            "manual";

    private final Context applicationContext;

    public CloudAutomaticBackupWorker(
            @NonNull Context context,
            @NonNull WorkerParameters workerParameters
    ) {
        super(
                context,
                workerParameters
        );

        applicationContext =
                context.getApplicationContext();
    }

    /**
     * Creates safe input data for a one-time manual cloud-backup request.
     *
     * Only the expected Firebase UID and the manual-run flag are stored.
     * The recovery passphrase is never placed in WorkManager Data.
     */
    @NonNull
    public static Data createManualRunInput(
            @NonNull String firebaseUserId
    ) {
        String cleanUserId =
                firebaseUserId.trim();

        if (cleanUserId.isEmpty()) {
            throw new IllegalArgumentException(
                    "Firebase cloud account UID is unavailable."
            );
        }

        if (cleanUserId.length()
                > MAX_FIREBASE_UID_LENGTH) {

            throw new IllegalArgumentException(
                    "Firebase cloud account UID exceeds "
                            + "the supported length."
            );
        }

        if (cleanUserId.indexOf('\n') >= 0
                || cleanUserId.indexOf('\r') >= 0
                || cleanUserId.indexOf('\0') >= 0) {

            throw new IllegalArgumentException(
                    "Firebase cloud account UID contains "
                            + "unsupported characters."
            );
        }

        return new Data.Builder()
                .putBoolean(
                        INPUT_MANUAL_RUN,
                        true
                )
                .putString(
                        INPUT_EXPECTED_FIREBASE_UID,
                        cleanUserId
                )
                .build();
    }

    @NonNull
    @Override
    public Result doWork() {
        long workerStartedAt =
                System.currentTimeMillis();

        boolean manualRun =
                getInputData().getBoolean(
                        INPUT_MANUAL_RUN,
                        false
                );

        String runMode =
                manualRun
                        ? RUN_MODE_MANUAL
                        : RUN_MODE_AUTOMATIC;

        FirebaseUser firebaseUser =
                FirebaseAuth
                        .getInstance()
                        .getCurrentUser();

        if (firebaseUser == null) {
            return permanentFailure(
                    manualRun
                            ? "Manual cloud backup could not start "
                              + "because no Firebase account is signed in."
                            : "Automatic cloud backup was skipped "
                              + "because no Firebase account is signed in.",
                    runMode
            );
        }

        String firebaseUserId =
                firebaseUser
                        .getUid()
                        .trim();

        if (firebaseUserId.isEmpty()) {
            return permanentFailure(
                    "Firebase cloud account UID is unavailable.",
                    runMode
            );
        }

        if (manualRun) {
            String expectedFirebaseUserId =
                    getInputData().getString(
                            INPUT_EXPECTED_FIREBASE_UID
                    );

            if (expectedFirebaseUserId == null
                    || expectedFirebaseUserId.trim().isEmpty()) {

                return permanentFailure(
                        "Manual cloud backup account information "
                                + "is missing.",
                        runMode
                );
            }

            if (!firebaseUserId.equals(
                    expectedFirebaseUserId.trim()
            )) {
                return permanentFailure(
                        "Firebase account changed before the manual "
                                + "cloud backup could start. Open Backup "
                                + "& Restore and try again.",
                        runMode
                );
            }
        }

        BackupSchedulePreferences schedulePreferences =
                new BackupSchedulePreferences(
                        applicationContext
                );

        BackupSchedulePreferences.ScheduleSettings
                scheduleSettings =
                null;

        /*
         * Manual execution bypasses automatic-enabled and due checks.
         *
         * The saved schedule is still read when possible so that the
         * next automatic backup can be recalculated after a successful
         * manual upload.
         */
        if (manualRun) {
            try {
                scheduleSettings =
                        schedulePreferences.getCloudSchedule(
                                firebaseUserId
                        );

            } catch (Exception exception) {
                Log.w(
                        TAG,
                        "Cloud schedule could not be read during "
                                + "manual backup. Manual upload will continue.",
                        exception
                );
            }

        } else {
            try {
                scheduleSettings =
                        schedulePreferences.getCloudSchedule(
                                firebaseUserId
                        );

            } catch (Exception exception) {
                return permanentFailure(
                        safeMessage(
                                exception,
                                "Cloud backup schedule could not be read."
                        ),
                        runMode
                );
            }

            /*
             * A stale periodic request may execute once after the user
             * changes the setting to Off or Manual only.
             */
            if (!scheduleSettings.isAutomaticEnabled()) {
                safeSaveNextScheduledTime(
                        schedulePreferences,
                        firebaseUserId,
                        0L
                );

                return skippedResult(
                        "Automatic cloud backup is currently "
                                + scheduleSettings
                                .getFrequency()
                                .getDisplayName()
                                + ".",
                        runMode
                );
            }

            try {
                BackupSchedulePreferences.BackupStatus backupStatus =
                        schedulePreferences.getCloudStatus(
                                firebaseUserId
                        );

                boolean backupDue =
                        schedulePreferences.isAutomaticBackupDue(
                                scheduleSettings,
                                backupStatus,
                                workerStartedAt
                        );

                if (!backupDue) {
                    long nextScheduledAt =
                            schedulePreferences
                                    .calculateNextPreferredRunAt(
                                            scheduleSettings,
                                            workerStartedAt
                                    );

                    safeSaveNextScheduledTime(
                            schedulePreferences,
                            firebaseUserId,
                            nextScheduledAt
                    );

                    return skippedResult(
                            "Automatic cloud backup is not due yet.",
                            runMode
                    );
                }

            } catch (Exception exception) {
                return permanentFailure(
                        safeMessage(
                                exception,
                                "Cloud backup due status "
                                        + "could not be checked."
                        ),
                        runMode
                );
            }
        }

        safeRecordAttempt(
                schedulePreferences,
                firebaseUserId,
                workerStartedAt
        );

        if (!firebaseUser.isEmailVerified()) {
            String errorMessage =
                    manualRun
                            ? "Manual cloud backup requires a verified "
                              + "Firebase email account."
                            : "Automatic cloud backup requires a verified "
                              + "Firebase email account.";

            safeRecordFailure(
                    schedulePreferences,
                    firebaseUserId,
                    errorMessage
            );

            return permanentFailure(
                    errorMessage,
                    runMode
            );
        }

        if (isStopped()) {
            return Result.retry();
        }

        CloudBackupKeyVault keyVault =
                new CloudBackupKeyVault(
                        applicationContext
                );

        char[] recoveryPassphrase =
                null;

        try {
            recoveryPassphrase =
                    keyVault.readPassphrase(
                            firebaseUserId
                    );

            if (isStopped()) {
                throw new InterruptedException(
                        "Cloud backup was stopped before "
                                + "encryption started."
                );
            }

            EncryptedCloudBackupPayloadBuilder payloadBuilder =
                    new EncryptedCloudBackupPayloadBuilder(
                            applicationContext
                    );

            EncryptedCloudBackupPayloadBuilder
                    .EncryptedCloudBackupPayload encryptedPayload =
                    payloadBuilder.build(
                            firebaseUserId,
                            recoveryPassphrase
                    );

            /*
             * The builder has already used the passphrase for encryption.
             * Clear our local character array before network upload starts.
             */
            CloudBackupEncryption.clearSensitiveCharacters(
                    recoveryPassphrase
            );

            recoveryPassphrase =
                    null;

            if (isStopped()) {
                throw new InterruptedException(
                        "Cloud backup was stopped before "
                                + "cloud upload started."
                );
            }

            CloudBackupUploader.UploadResult uploadResult =
                    uploadAndWait(
                            firebaseUser,
                            encryptedPayload
                    );

            long completedAt =
                    System.currentTimeMillis();

            int totalRecordCount =
                    encryptedPayload
                            .getRecordCounts()
                            .getTotalRecords();

            safeRecordSuccess(
                    schedulePreferences,
                    firebaseUserId,
                    completedAt,
                    uploadResult.getBackupId(),
                    totalRecordCount,
                    uploadResult.getEncryptedByteCount()
            );

            /*
             * A successful manual backup also becomes the latest cloud
             * backup. When automatic scheduling is enabled, calculate
             * the next preferred automatic run from this success time.
             */
            updateNextScheduledTimeAfterSuccess(
                    schedulePreferences,
                    firebaseUserId,
                    scheduleSettings,
                    completedAt
            );

            Log.i(
                    TAG,
                    (
                            manualRun
                                    ? "Encrypted manual cloud backup completed. "
                                    : "Encrypted automatic cloud backup completed. "
                    )
                            + "Backup ID: "
                            + uploadResult.getBackupId()
                            + ", records: "
                            + totalRecordCount
                            + ", encrypted bytes: "
                            + uploadResult.getEncryptedByteCount()
            );

            return Result.success(
                    createOutputData(
                            STATUS_SUCCESS,
                            manualRun
                                    ? "Manual encrypted cloud backup "
                                      + "completed successfully."
                                    : "Automatic encrypted cloud backup "
                                      + "completed successfully.",
                            uploadResult.getBackupId(),
                            totalRecordCount,
                            uploadResult.getEncryptedByteCount(),
                            completedAt,
                            runMode
                    )
            );

        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();

            String errorMessage =
                    safeMessage(
                            exception,
                            manualRun
                                    ? "Manual cloud backup was interrupted."
                                    : "Automatic cloud backup was interrupted."
                    );

            safeRecordFailure(
                    schedulePreferences,
                    firebaseUserId,
                    errorMessage
            );

            return Result.retry();

        } catch (Exception exception) {
            String errorMessage =
                    safeMessage(
                            exception,
                            manualRun
                                    ? "Manual encrypted cloud backup failed."
                                    : "Automatic encrypted cloud backup failed."
                    );

            safeRecordFailure(
                    schedulePreferences,
                    firebaseUserId,
                    errorMessage
            );

            Log.e(
                    TAG,
                    manualRun
                            ? "Manual encrypted cloud backup failed."
                            : "Automatic encrypted cloud backup failed.",
                    exception
            );

            if (isTransientFailure(
                    exception
            )
                    && canRetry()) {

                return Result.retry();
            }

            return Result.failure(
                    createOutputData(
                            STATUS_FAILURE,
                            errorMessage,
                            "",
                            0,
                            0L,
                            System.currentTimeMillis(),
                            runMode
                    )
            );

        } finally {
            CloudBackupEncryption.clearSensitiveCharacters(
                    recoveryPassphrase
            );
        }
    }

    /**
     * Starts Firestore upload and waits on the WorkManager background
     * thread for its callback.
     */
    @NonNull
    private CloudBackupUploader.UploadResult uploadAndWait(
            @NonNull FirebaseUser firebaseUser,
            @NonNull EncryptedCloudBackupPayloadBuilder
                    .EncryptedCloudBackupPayload encryptedPayload
    ) throws Exception {

        CountDownLatch completionLatch =
                new CountDownLatch(
                        1
                );

        AtomicReference<CloudBackupUploader.UploadResult>
                uploadResultReference =
                new AtomicReference<>();

        AtomicReference<Exception>
                uploadErrorReference =
                new AtomicReference<>();

        CloudBackupUploader uploader =
                new CloudBackupUploader();

        uploader.uploadLatestEncryptedBackup(
                firebaseUser,
                encryptedPayload,
                new CloudBackupUploader.UploadCallback() {
                    @Override
                    public void onSuccess(
                            @NonNull CloudBackupUploader
                                    .UploadResult result
                    ) {
                        uploadResultReference.set(
                                result
                        );

                        completionLatch.countDown();
                    }

                    @Override
                    public void onError(
                            @NonNull Exception exception
                    ) {
                        uploadErrorReference.set(
                                exception
                        );

                        completionLatch.countDown();
                    }
                }
        );

        long waitStartedAtNanos =
                System.nanoTime();

        long timeoutNanos =
                TimeUnit.SECONDS.toNanos(
                        UPLOAD_TIMEOUT_SECONDS
                );

        while (true) {
            if (isStopped()) {
                throw new InterruptedException(
                        "Cloud backup worker was stopped."
                );
            }

            long elapsedNanos =
                    System.nanoTime()
                            - waitStartedAtNanos;

            long remainingNanos =
                    timeoutNanos
                            - elapsedNanos;

            if (remainingNanos <= 0L) {
                throw new CloudUploadTimeoutException(
                        "Cloud backup upload did not complete within "
                                + UPLOAD_TIMEOUT_SECONDS
                                + " seconds."
                );
            }

            long waitMilliseconds =
                    Math.min(
                            1000L,
                            Math.max(
                                    1L,
                                    TimeUnit.NANOSECONDS
                                            .toMillis(
                                                    remainingNanos
                                            )
                            )
                    );

            boolean completed =
                    completionLatch.await(
                            waitMilliseconds,
                            TimeUnit.MILLISECONDS
                    );

            if (completed) {
                break;
            }
        }

        Exception uploadError =
                uploadErrorReference.get();

        if (uploadError != null) {
            throw uploadError;
        }

        CloudBackupUploader.UploadResult uploadResult =
                uploadResultReference.get();

        if (uploadResult == null) {
            throw new IllegalStateException(
                    "Cloud backup uploader finished without "
                            + "returning an upload result."
            );
        }

        return uploadResult;
    }

    private void updateNextScheduledTimeAfterSuccess(
            @NonNull BackupSchedulePreferences preferences,
            @NonNull String firebaseUserId,
            @Nullable BackupSchedulePreferences
                    .ScheduleSettings knownSettings,
            long completedAt
    ) {
        BackupSchedulePreferences.ScheduleSettings settings =
                knownSettings;

        if (settings == null) {
            try {
                settings =
                        preferences.getCloudSchedule(
                                firebaseUserId
                        );

            } catch (Exception exception) {
                Log.w(
                        TAG,
                        "Cloud backup succeeded, but the saved schedule "
                                + "could not be read.",
                        exception
                );

                return;
            }
        }

        if (!settings.isAutomaticEnabled()) {
            safeSaveNextScheduledTime(
                    preferences,
                    firebaseUserId,
                    0L
            );

            return;
        }

        long nextScheduledAt =
                safeCalculateNextScheduledTime(
                        preferences,
                        settings,
                        completedAt
                );

        safeSaveNextScheduledTime(
                preferences,
                firebaseUserId,
                nextScheduledAt
        );
    }

    private boolean canRetry() {
        return getRunAttemptCount()
                < MAX_TOTAL_ATTEMPTS - 1;
    }

    /**
     * Returns true only for errors that may succeed when WorkManager
     * retries later.
     */
    private boolean isTransientFailure(
            @NonNull Throwable throwable
    ) {
        Throwable current =
                throwable;

        int inspectedCauseCount =
                0;

        while (current != null
                && inspectedCauseCount < 12) {

            if (current instanceof CloudUploadTimeoutException
                    || current instanceof FirebaseNetworkException
                    || current instanceof SocketTimeoutException
                    || current instanceof UnknownHostException
                    || current instanceof SocketException) {

                return true;
            }

            if (current instanceof FirebaseFirestoreException) {
                FirebaseFirestoreException firestoreException =
                        (FirebaseFirestoreException) current;

                FirebaseFirestoreException.Code code =
                        firestoreException.getCode();

                switch (code) {
                    case ABORTED:
                    case CANCELLED:
                    case DEADLINE_EXCEEDED:
                    case INTERNAL:
                    case RESOURCE_EXHAUSTED:
                    case UNAVAILABLE:
                    case UNKNOWN:
                        return true;

                    case ALREADY_EXISTS:
                    case DATA_LOSS:
                    case FAILED_PRECONDITION:
                    case INVALID_ARGUMENT:
                    case NOT_FOUND:
                    case OUT_OF_RANGE:
                    case PERMISSION_DENIED:
                    case UNAUTHENTICATED:
                    case UNIMPLEMENTED:
                    default:
                        return false;
                }
            }

            if (current instanceof IOException) {
                return true;
            }

            current =
                    current.getCause();

            inspectedCauseCount++;
        }

        return false;
    }

    private void safeRecordAttempt(
            @NonNull BackupSchedulePreferences preferences,
            @NonNull String firebaseUserId,
            long attemptedAt
    ) {
        try {
            preferences.recordCloudBackupAttempt(
                    firebaseUserId,
                    attemptedAt
            );

        } catch (Exception exception) {
            Log.w(
                    TAG,
                    "Cloud backup attempt status could not be saved.",
                    exception
            );
        }
    }

    private void safeRecordSuccess(
            @NonNull BackupSchedulePreferences preferences,
            @NonNull String firebaseUserId,
            long succeededAt,
            @NonNull String backupId,
            int recordCount,
            long encryptedByteCount
    ) {
        try {
            preferences.recordCloudBackupSuccess(
                    firebaseUserId,
                    succeededAt,
                    backupId,
                    recordCount,
                    encryptedByteCount
            );

        } catch (Exception exception) {
            /*
             * Firestore upload has already succeeded.
             * A local status failure must not cause duplicate uploads.
             */
            Log.w(
                    TAG,
                    "Cloud backup succeeded, but its local "
                            + "success status could not be saved.",
                    exception
            );
        }
    }

    private void safeRecordFailure(
            @NonNull BackupSchedulePreferences preferences,
            @NonNull String firebaseUserId,
            @NonNull String failureMessage
    ) {
        try {
            preferences.recordCloudBackupFailure(
                    firebaseUserId,
                    System.currentTimeMillis(),
                    failureMessage
            );

        } catch (Exception statusException) {
            Log.w(
                    TAG,
                    "Cloud backup failure status could not be saved.",
                    statusException
            );
        }
    }

    private void safeSaveNextScheduledTime(
            @NonNull BackupSchedulePreferences preferences,
            @NonNull String firebaseUserId,
            long nextScheduledAt
    ) {
        try {
            preferences.setCloudNextScheduledAt(
                    firebaseUserId,
                    Math.max(
                            0L,
                            nextScheduledAt
                    )
            );

        } catch (Exception exception) {
            Log.w(
                    TAG,
                    "Next cloud backup time could not be saved.",
                    exception
            );
        }
    }

    private long safeCalculateNextScheduledTime(
            @NonNull BackupSchedulePreferences preferences,
            @NonNull BackupSchedulePreferences
                    .ScheduleSettings scheduleSettings,
            long currentTime
    ) {
        try {
            return preferences.calculateNextPreferredRunAt(
                    scheduleSettings,
                    currentTime
            );

        } catch (Exception exception) {
            Log.w(
                    TAG,
                    "Next cloud backup time could not be calculated.",
                    exception
            );

            return 0L;
        }
    }

    @NonNull
    private Result skippedResult(
            @NonNull String message,
            @NonNull String runMode
    ) {
        return Result.success(
                createOutputData(
                        STATUS_SKIPPED,
                        message,
                        "",
                        0,
                        0L,
                        System.currentTimeMillis(),
                        runMode
                )
        );
    }

    @NonNull
    private Result permanentFailure(
            @NonNull String message,
            @NonNull String runMode
    ) {
        Log.w(
                TAG,
                message
        );

        return Result.failure(
                createOutputData(
                        STATUS_FAILURE,
                        message,
                        "",
                        0,
                        0L,
                        System.currentTimeMillis(),
                        runMode
                )
        );
    }

    @NonNull
    private Data createOutputData(
            @NonNull String status,
            @NonNull String message,
            @Nullable String backupId,
            int recordCount,
            long encryptedByteCount,
            long completedAt,
            @NonNull String runMode
    ) {
        Data.Builder builder =
                new Data.Builder()
                        .putString(
                                OUTPUT_STATUS,
                                status
                        )
                        .putString(
                                OUTPUT_MESSAGE,
                                sanitizeMessage(
                                        message
                                )
                        )
                        .putInt(
                                OUTPUT_RECORD_COUNT,
                                Math.max(
                                        0,
                                        recordCount
                                )
                        )
                        .putLong(
                                OUTPUT_ENCRYPTED_BYTES,
                                Math.max(
                                        0L,
                                        encryptedByteCount
                                )
                        )
                        .putLong(
                                OUTPUT_COMPLETED_AT,
                                Math.max(
                                        0L,
                                        completedAt
                                )
                        )
                        .putString(
                                OUTPUT_RUN_MODE,
                                runMode
                        );

        if (backupId != null
                && !backupId.trim().isEmpty()) {

            builder.putString(
                    OUTPUT_BACKUP_ID,
                    backupId.trim()
            );
        }

        return builder.build();
    }

    @NonNull
    private String safeMessage(
            @NonNull Throwable throwable,
            @NonNull String fallback
    ) {
        Throwable current =
                throwable;

        String usefulMessage =
                "";

        int inspectedCauseCount =
                0;

        while (current != null
                && inspectedCauseCount < 12) {

            String message =
                    current.getMessage();

            if (message != null
                    && !message.trim().isEmpty()) {

                usefulMessage =
                        message.trim();
            }

            current =
                    current.getCause();

            inspectedCauseCount++;
        }

        if (usefulMessage.isEmpty()) {
            usefulMessage =
                    fallback;
        }

        return sanitizeMessage(
                usefulMessage
        );
    }

    @NonNull
    private String sanitizeMessage(
            @Nullable String message
    ) {
        if (message == null) {
            return "Cloud backup operation failed.";
        }

        String cleanMessage =
                message
                        .replace(
                                '\n',
                                ' '
                        )
                        .replace(
                                '\r',
                                ' '
                        )
                        .replace(
                                '\0',
                                ' '
                        )
                        .trim()
                        .replaceAll(
                                "\\s+",
                                " "
                        );

        if (cleanMessage.isEmpty()) {
            cleanMessage =
                    "Cloud backup operation failed.";
        }

        if (cleanMessage.length() > 500) {
            cleanMessage =
                    cleanMessage.substring(
                            0,
                            500
                    );
        }

        return cleanMessage;
    }

    public static final class CloudUploadTimeoutException
            extends Exception {

        public CloudUploadTimeoutException(
                @NonNull String message
        ) {
            super(
                    message
            );
        }
    }
}