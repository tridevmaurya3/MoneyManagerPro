package com.example.moneymanagerpro.backup;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.moneymanagerpro.cloud.BackupSchedulePreferences;

import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * Creates verified automatic offline backups in the folder previously
 * selected by the user.
 *
 * Complete flow:
 *
 * 1. Read device-wide offline backup schedule.
 * 2. Check whether automatic backup is enabled.
 * 3. Check whether the backup is actually due.
 * 4. Create the complete version-5 offline backup.
 * 5. Verify SHA-256 and safely replace the latest backup.
 * 6. Save success, failure and next-schedule status.
 *
 * Important:
 *
 * - This worker never opens a folder picker.
 * - The folder must already have been selected from BackupActivity.
 * - The previously granted persistent document-tree permission is reused.
 * - No Firebase account or internet connection is required.
 */
public final class OfflineAutomaticBackupWorker
        extends Worker {

    private static final String TAG =
            "OfflineAutoBackup";

    private static final int MAX_TOTAL_ATTEMPTS =
            4;

    public static final String OUTPUT_STATUS =
            "offline_backup_status";

    public static final String OUTPUT_MESSAGE =
            "offline_backup_message";

    public static final String OUTPUT_BACKUP_ID =
            "offline_backup_id";

    public static final String OUTPUT_RECORD_COUNT =
            "offline_backup_record_count";

    public static final String OUTPUT_BACKUP_BYTES =
            "offline_backup_bytes";

    public static final String OUTPUT_BACKUP_URI =
            "offline_backup_uri";

    public static final String OUTPUT_COMPLETED_AT =
            "offline_backup_completed_at";

    public static final String STATUS_SUCCESS =
            "success";

    public static final String STATUS_SKIPPED =
            "skipped";

    public static final String STATUS_FAILURE =
            "failure";

    private final Context applicationContext;

    public OfflineAutomaticBackupWorker(
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

    @NonNull
    @Override
    public Result doWork() {
        long startedAtMillis =
                System.currentTimeMillis();

        BackupSchedulePreferences schedulePreferences =
                new BackupSchedulePreferences(
                        applicationContext
                );

        BackupSchedulePreferences.ScheduleSettings settings =
                schedulePreferences.getOfflineSchedule();

        /*
         * A previously scheduled worker may execute once after the
         * user switches the setting to Off or Manual only.
         */
        if (!settings.isAutomaticEnabled()) {
            saveNextScheduledTimeSafely(
                    schedulePreferences,
                    0L
            );

            return createSkippedResult(
                    "Automatic offline backup is "
                            + settings
                            .getFrequency()
                            .getDisplayName()
                            + "."
            );
        }

        BackupSchedulePreferences.BackupStatus status =
                schedulePreferences.getOfflineStatus();

        try {
            boolean backupDue =
                    schedulePreferences
                            .isAutomaticBackupDue(
                                    settings,
                                    status,
                                    startedAtMillis
                            );

            if (!backupDue) {
                long nextScheduledAt =
                        schedulePreferences
                                .calculateNextPreferredRunAt(
                                        settings,
                                        startedAtMillis
                                );

                saveNextScheduledTimeSafely(
                        schedulePreferences,
                        nextScheduledAt
                );

                return createSkippedResult(
                        "Automatic offline backup is not due yet."
                );
            }

        } catch (Exception exception) {
            String message =
                    safeErrorMessage(
                            exception,
                            "Offline backup schedule could not be checked."
                    );

            recordFailureSafely(
                    schedulePreferences,
                    message
            );

            return createFailureResult(
                    message
            );
        }

        recordAttemptSafely(
                schedulePreferences,
                startedAtMillis
        );

        if (isStopped()) {
            return Result.retry();
        }

        try {
            OfflineBackupEngine backupEngine =
                    new OfflineBackupEngine(
                            applicationContext
                    );

            OfflineBackupEngine.BackupResult backupResult =
                    backupEngine.createVerifiedBackup();

            if (isStopped()) {
                /*
                 * Backup has already been committed successfully.
                 * It must not be repeated merely because WorkManager
                 * stopped immediately after file creation.
                 */
                Log.w(
                        TAG,
                        "Worker stopped after the offline backup "
                                + "had already been committed."
                );
            }

            long completedAtMillis =
                    System.currentTimeMillis();

            int totalRecordCount =
                    backupResult
                            .getRecordCounts()
                            .getTotalRecords();

            recordSuccessSafely(
                    schedulePreferences,
                    completedAtMillis,
                    backupResult.getBackupId(),
                    totalRecordCount,
                    backupResult.getBackupByteCount()
            );

            long nextScheduledAt =
                    calculateNextScheduledTimeSafely(
                            schedulePreferences,
                            settings,
                            completedAtMillis
                    );

            saveNextScheduledTimeSafely(
                    schedulePreferences,
                    nextScheduledAt
            );

            Log.i(
                    TAG,
                    "Automatic offline backup completed. "
                            + "Backup ID: "
                            + backupResult.getBackupId()
                            + ", records: "
                            + totalRecordCount
                            + ", bytes: "
                            + backupResult.getBackupByteCount()
            );

            return Result.success(
                    createOutputData(
                            STATUS_SUCCESS,
                            "Verified offline backup completed successfully.",
                            backupResult.getBackupId(),
                            totalRecordCount,
                            backupResult.getBackupByteCount(),
                            backupResult
                                    .getBackupUri()
                                    .toString(),
                            completedAtMillis
                    )
            );

        } catch (Exception exception) {
            String message =
                    safeErrorMessage(
                            exception,
                            "Automatic offline backup failed."
                    );

            recordFailureSafely(
                    schedulePreferences,
                    message
            );

            Log.e(
                    TAG,
                    "Automatic offline backup failed.",
                    exception
            );

            if (isTemporaryFailure(
                    exception
            )
                    && canRetry()) {

                return Result.retry();
            }

            return createFailureResult(
                    message
            );
        }
    }

    private boolean canRetry() {
        return getRunAttemptCount()
                < MAX_TOTAL_ATTEMPTS - 1;
    }

    /**
     * Storage provider or temporary I/O failures may succeed later.
     *
     * Missing folder permission is not retried repeatedly because the
     * user must open BackupActivity and select the folder again.
     */
    private boolean isTemporaryFailure(
            @NonNull Throwable throwable
    ) {
        Throwable current =
                throwable;

        int inspectedCauses =
                0;

        while (current != null
                && inspectedCauses < 12) {

            if (current
                    instanceof OfflineBackupEngine
                    .BackupFolderUnavailableException) {

                return false;
            }

            if (current instanceof SecurityException) {
                return false;
            }

            if (current instanceof FileNotFoundException) {
                /*
                 * With Storage Access Framework this commonly means
                 * that the selected folder was deleted, moved or its
                 * persistent permission was revoked.
                 */
                return false;
            }

            if (current instanceof IOException) {
                return true;
            }

            current =
                    current.getCause();

            inspectedCauses++;
        }

        return false;
    }

    private void recordAttemptSafely(
            @NonNull BackupSchedulePreferences preferences,
            long attemptedAtMillis
    ) {
        try {
            preferences.recordOfflineBackupAttempt(
                    attemptedAtMillis
            );

        } catch (Exception exception) {
            Log.w(
                    TAG,
                    "Offline backup attempt status could not be saved.",
                    exception
            );
        }
    }

    private void recordSuccessSafely(
            @NonNull BackupSchedulePreferences preferences,
            long completedAtMillis,
            @NonNull String backupId,
            int recordCount,
            long backupByteCount
    ) {
        try {
            preferences.recordOfflineBackupSuccess(
                    completedAtMillis,
                    backupId,
                    recordCount,
                    backupByteCount
            );

        } catch (Exception exception) {
            /*
             * The backup file has already been created successfully.
             * Status failure must not cause duplicate backup retries.
             */
            Log.w(
                    TAG,
                    "Offline backup succeeded, but its local "
                            + "status could not be saved.",
                    exception
            );
        }
    }

    private void recordFailureSafely(
            @NonNull BackupSchedulePreferences preferences,
            @NonNull String message
    ) {
        try {
            preferences.recordOfflineBackupFailure(
                    System.currentTimeMillis(),
                    message
            );

        } catch (Exception exception) {
            Log.w(
                    TAG,
                    "Offline backup failure status could not be saved.",
                    exception
            );
        }
    }

    private void saveNextScheduledTimeSafely(
            @NonNull BackupSchedulePreferences preferences,
            long nextScheduledAtMillis
    ) {
        try {
            preferences.setOfflineNextScheduledAt(
                    Math.max(
                            0L,
                            nextScheduledAtMillis
                    )
            );

        } catch (Exception exception) {
            Log.w(
                    TAG,
                    "Next offline backup time could not be saved.",
                    exception
            );
        }
    }

    private long calculateNextScheduledTimeSafely(
            @NonNull BackupSchedulePreferences preferences,
            @NonNull BackupSchedulePreferences
                    .ScheduleSettings settings,
            long currentTimeMillis
    ) {
        try {
            return preferences.calculateNextPreferredRunAt(
                    settings,
                    currentTimeMillis
            );

        } catch (Exception exception) {
            Log.w(
                    TAG,
                    "Next offline backup time could not be calculated.",
                    exception
            );

            return 0L;
        }
    }

    @NonNull
    private Result createSkippedResult(
            @NonNull String message
    ) {
        return Result.success(
                createOutputData(
                        STATUS_SKIPPED,
                        message,
                        "",
                        0,
                        0L,
                        "",
                        System.currentTimeMillis()
                )
        );
    }

    @NonNull
    private Result createFailureResult(
            @NonNull String message
    ) {
        return Result.failure(
                createOutputData(
                        STATUS_FAILURE,
                        message,
                        "",
                        0,
                        0L,
                        "",
                        System.currentTimeMillis()
                )
        );
    }

    @NonNull
    private Data createOutputData(
            @NonNull String status,
            @NonNull String message,
            @Nullable String backupId,
            int recordCount,
            long backupByteCount,
            @Nullable String backupUri,
            long completedAtMillis
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
                                OUTPUT_BACKUP_BYTES,
                                Math.max(
                                        0L,
                                        backupByteCount
                                )
                        )
                        .putLong(
                                OUTPUT_COMPLETED_AT,
                                Math.max(
                                        0L,
                                        completedAtMillis
                                )
                        );

        if (backupId != null
                && !backupId.trim().isEmpty()) {

            builder.putString(
                    OUTPUT_BACKUP_ID,
                    backupId.trim()
            );
        }

        if (backupUri != null
                && !backupUri.trim().isEmpty()) {

            builder.putString(
                    OUTPUT_BACKUP_URI,
                    backupUri.trim()
            );
        }

        return builder.build();
    }

    @NonNull
    private String safeErrorMessage(
            @NonNull Throwable throwable,
            @NonNull String fallback
    ) {
        Throwable current =
                throwable;

        String usefulMessage =
                "";

        int inspectedCauses =
                0;

        while (current != null
                && inspectedCauses < 12) {

            String message =
                    current.getMessage();

            if (message != null
                    && !message.trim().isEmpty()) {

                usefulMessage =
                        message.trim();
            }

            current =
                    current.getCause();

            inspectedCauses++;
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
            return "Offline backup operation failed.";
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
                    "Offline backup operation failed.";
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
}