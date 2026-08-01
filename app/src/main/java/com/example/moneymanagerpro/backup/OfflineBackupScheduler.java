package com.example.moneymanagerpro.backup;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.example.moneymanagerpro.cloud.BackupSchedulePreferences;
import com.example.moneymanagerpro.utils.BackupStorageManager;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Schedules automatic verified offline backups.
 *
 * Scheduling design:
 *
 * 1. Offline backup settings are device-wide.
 * 2. Off and Manual-only settings cancel periodic work.
 * 3. Daily, Weekly and Monthly settings use one unique worker.
 * 4. The periodic worker checks once every 24 hours.
 * 5. OfflineAutomaticBackupWorker performs the final due check.
 * 6. Changing frequency, time or charging preference replaces the
 *    previous unique periodic request.
 * 7. No internet or Firebase account is required.
 *
 * WorkManager execution is inexact. Android may delay a run because of
 * Doze mode, battery optimization, charging conditions or system load.
 */
public final class OfflineBackupScheduler {

    public static final String UNIQUE_WORK_NAME =
            "money_manager_verified_offline_backup";

    public static final String TAG_OFFLINE_BACKUP =
            "money_manager_offline_backup";

    /**
     * The worker checks once every 24 hours.
     *
     * The worker and BackupSchedulePreferences decide whether a Daily,
     * Weekly or Monthly backup is actually due.
     */
    private static final long CHECK_INTERVAL_HOURS =
            24L;

    /**
     * Allows Android to run the periodic check during the final
     * two-hour window of each 24-hour period.
     */
    private static final long FLEX_INTERVAL_HOURS =
            2L;

    private static final long BACKOFF_DELAY_MINUTES =
            30L;

    private OfflineBackupScheduler() {
        // Utility class.
    }

    /**
     * Applies the currently saved offline-backup schedule.
     *
     * Call this after:
     *
     * - the user selects a backup folder,
     * - frequency changes,
     * - preferred time changes,
     * - weekly day changes,
     * - monthly date changes,
     * - charging-only setting changes,
     * - application startup.
     */
    @NonNull
    public static ScheduleResult applySavedSchedule(
            @NonNull Context context
    ) throws OfflineBackupSchedulerException {

        Context applicationContext =
                context.getApplicationContext();

        BackupSchedulePreferences schedulePreferences =
                new BackupSchedulePreferences(
                        applicationContext
                );

        BackupSchedulePreferences.ScheduleSettings settings =
                schedulePreferences.getOfflineSchedule();

        /*
         * No periodic work should remain when automatic backup is disabled.
         */
        if (!settings.isAutomaticEnabled()) {
            cancel(
                    applicationContext
            );

            return ScheduleResult.notScheduled(
                    settings
                            .getFrequency()
                            .getDisplayName(),
                    "Automatic offline backup is "
                            + settings
                            .getFrequency()
                            .getDisplayName()
                            + "."
            );
        }

        BackupStorageManager storageManager =
                new BackupStorageManager(
                        applicationContext
                );

        /*
         * An automatic worker cannot open Android's folder picker.
         * Therefore a valid persistent folder permission must already exist.
         */
        if (!storageManager.hasUsableBackupFolder()) {
            cancel(
                    applicationContext
            );

            throw new BackupFolderNotReadyException(
                    "Automatic offline backup cannot be scheduled "
                            + "until a usable backup folder is selected "
                            + "from Backup & Restore."
            );
        }

        long nowMillis =
                System.currentTimeMillis();

        long nextPreferredRunAtMillis;

        try {
            nextPreferredRunAtMillis =
                    schedulePreferences
                            .calculateNextPreferredRunAt(
                                    settings,
                                    nowMillis
                            );

        } catch (Exception exception) {
            throw new OfflineBackupSchedulerException(
                    safeMessage(
                            exception,
                            "Next offline backup time could not be calculated."
                    ),
                    exception
            );
        }

        if (nextPreferredRunAtMillis <= nowMillis) {
            throw new OfflineBackupSchedulerException(
                    "Calculated offline backup time is invalid."
            );
        }

        long initialDelayMillis =
                nextPreferredRunAtMillis
                        - nowMillis;

        Constraints constraints =
                createConstraints(
                        settings
                );

        PeriodicWorkRequest workRequest =
                new PeriodicWorkRequest.Builder(
                        OfflineAutomaticBackupWorker.class,
                        CHECK_INTERVAL_HOURS,
                        TimeUnit.HOURS,
                        FLEX_INTERVAL_HOURS,
                        TimeUnit.HOURS
                )
                        .setInitialDelay(
                                initialDelayMillis,
                                TimeUnit.MILLISECONDS
                        )
                        .setConstraints(
                                constraints
                        )
                        .setBackoffCriteria(
                                BackoffPolicy.EXPONENTIAL,
                                BACKOFF_DELAY_MINUTES,
                                TimeUnit.MINUTES
                        )
                        .addTag(
                                TAG_OFFLINE_BACKUP
                        )
                        .build();

        try {
            WorkManager
                    .getInstance(
                            applicationContext
                    )
                    .enqueueUniquePeriodicWork(
                            UNIQUE_WORK_NAME,
                            ExistingPeriodicWorkPolicy
                                    .CANCEL_AND_REENQUEUE,
                            workRequest
                    );

        } catch (Exception exception) {
            throw new OfflineBackupSchedulerException(
                    "Automatic offline backup could not be "
                            + "scheduled with WorkManager.",
                    exception
            );
        }

        try {
            schedulePreferences
                    .setOfflineNextScheduledAt(
                            nextPreferredRunAtMillis
                    );

        } catch (Exception exception) {
            /*
             * Scheduling and displayed status must not disagree.
             * Cancel the request if its status cannot be saved.
             */
            WorkManager
                    .getInstance(
                            applicationContext
                    )
                    .cancelUniqueWork(
                            UNIQUE_WORK_NAME
                    );

            throw new OfflineBackupSchedulerException(
                    "Automatic offline backup was prepared, but "
                            + "its next scheduled time could not be saved.",
                    exception
            );
        }

        return ScheduleResult.scheduled(
                workRequest.getId(),
                settings
                        .getFrequency()
                        .getDisplayName(),
                nextPreferredRunAtMillis,
                initialDelayMillis,
                settings.isChargingOnly(),
                storageManager.getBackupLocationLabel()
        );
    }

    /**
     * Cancels the automatic offline-backup worker.
     *
     * This method does not:
     *
     * - delete an existing backup file,
     * - remove the selected backup folder,
     * - clear persistent folder permission,
     * - change the user's selected frequency,
     * - delete offline-backup history.
     */
    public static void cancel(
            @NonNull Context context
    ) throws OfflineBackupSchedulerException {

        Context applicationContext =
                context.getApplicationContext();

        try {
            WorkManager
                    .getInstance(
                            applicationContext
                    )
                    .cancelUniqueWork(
                            UNIQUE_WORK_NAME
                    );

        } catch (Exception exception) {
            throw new OfflineBackupSchedulerException(
                    "Automatic offline backup could not be cancelled.",
                    exception
            );
        }

        try {
            BackupSchedulePreferences preferences =
                    new BackupSchedulePreferences(
                            applicationContext
                    );

            preferences.setOfflineNextScheduledAt(
                    0L
            );

        } catch (Exception exception) {
            throw new OfflineBackupSchedulerException(
                    "Offline backup worker was cancelled, but its "
                            + "next scheduled time could not be cleared.",
                    exception
            );
        }
    }

    /**
     * Cancels every worker carrying the offline-backup tag.
     *
     * Useful during troubleshooting or a complete local backup reset.
     */
    public static void cancelAllTaggedOfflineBackupWork(
            @NonNull Context context
    ) throws OfflineBackupSchedulerException {

        try {
            WorkManager
                    .getInstance(
                            context.getApplicationContext()
                    )
                    .cancelAllWorkByTag(
                            TAG_OFFLINE_BACKUP
                    );

        } catch (Exception exception) {
            throw new OfflineBackupSchedulerException(
                    "All automatic offline-backup work "
                            + "could not be cancelled.",
                    exception
            );
        }
    }

    /**
     * Re-applies the current schedule after the user selects or changes
     * the backup folder.
     *
     * Off and Manual-only settings remain unscheduled.
     */
    @NonNull
    public static ScheduleResult onBackupFolderChanged(
            @NonNull Context context
    ) throws OfflineBackupSchedulerException {

        return applySavedSchedule(
                context
        );
    }

    /**
     * Returns true when the selected frequency requires periodic work.
     *
     * This does not query WorkManager's live state. It reports the saved
     * configuration only.
     */
    public static boolean isAutomaticSchedulingEnabled(
            @NonNull Context context
    ) {
        BackupSchedulePreferences preferences =
                new BackupSchedulePreferences(
                        context.getApplicationContext()
                );

        return preferences
                .getOfflineSchedule()
                .isAutomaticEnabled();
    }

    /**
     * Builds constraints for offline backup.
     *
     * Network is not required because the backup is written to the
     * previously selected local document-tree folder.
     */
    @NonNull
    private static Constraints createConstraints(
            @NonNull BackupSchedulePreferences
                    .ScheduleSettings settings
    ) {
        return new Constraints.Builder()
                .setRequiredNetworkType(
                        NetworkType.NOT_REQUIRED
                )
                .setRequiresCharging(
                        settings.isChargingOnly()
                )
                .setRequiresStorageNotLow(
                        true
                )
                .build();
    }

    @NonNull
    private static String safeMessage(
            @NonNull Throwable throwable,
            @NonNull String fallback
    ) {
        String message =
                throwable.getMessage();

        if (message == null
                || message.trim().isEmpty()) {

            return fallback;
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
            return fallback;
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

    /**
     * Immutable result used by the future backup-settings screen.
     */
    public static final class ScheduleResult {

        private final boolean scheduled;

        private final UUID workRequestId;

        private final String frequencyDisplayName;

        private final String message;

        private final long nextPreferredRunAtMillis;

        private final long initialDelayMillis;

        private final boolean chargingOnly;

        private final String backupLocationLabel;

        private ScheduleResult(
                boolean scheduled,
                @Nullable UUID workRequestId,
                @NonNull String frequencyDisplayName,
                @NonNull String message,
                long nextPreferredRunAtMillis,
                long initialDelayMillis,
                boolean chargingOnly,
                @NonNull String backupLocationLabel
        ) {
            this.scheduled =
                    scheduled;

            this.workRequestId =
                    workRequestId;

            this.frequencyDisplayName =
                    frequencyDisplayName;

            this.message =
                    message;

            this.nextPreferredRunAtMillis =
                    Math.max(
                            0L,
                            nextPreferredRunAtMillis
                    );

            this.initialDelayMillis =
                    Math.max(
                            0L,
                            initialDelayMillis
                    );

            this.chargingOnly =
                    chargingOnly;

            this.backupLocationLabel =
                    backupLocationLabel;
        }

        @NonNull
        private static ScheduleResult scheduled(
                @NonNull UUID workRequestId,
                @NonNull String frequencyDisplayName,
                long nextPreferredRunAtMillis,
                long initialDelayMillis,
                boolean chargingOnly,
                @NonNull String backupLocationLabel
        ) {
            return new ScheduleResult(
                    true,
                    workRequestId,
                    frequencyDisplayName,
                    "Automatic verified offline backup "
                            + "was scheduled successfully.",
                    nextPreferredRunAtMillis,
                    initialDelayMillis,
                    chargingOnly,
                    backupLocationLabel
            );
        }

        @NonNull
        private static ScheduleResult notScheduled(
                @NonNull String frequencyDisplayName,
                @NonNull String message
        ) {
            return new ScheduleResult(
                    false,
                    null,
                    frequencyDisplayName,
                    message,
                    0L,
                    0L,
                    false,
                    ""
            );
        }

        public boolean isScheduled() {
            return scheduled;
        }

        @Nullable
        public UUID getWorkRequestId() {
            return workRequestId;
        }

        @NonNull
        public String getUniqueWorkName() {
            return UNIQUE_WORK_NAME;
        }

        @NonNull
        public String getFrequencyDisplayName() {
            return frequencyDisplayName;
        }

        @NonNull
        public String getMessage() {
            return message;
        }

        public long getNextPreferredRunAtMillis() {
            return nextPreferredRunAtMillis;
        }

        public long getInitialDelayMillis() {
            return initialDelayMillis;
        }

        public boolean isChargingOnly() {
            return chargingOnly;
        }

        @NonNull
        public String getBackupLocationLabel() {
            return backupLocationLabel;
        }
    }

    public static class OfflineBackupSchedulerException
            extends Exception {

        public OfflineBackupSchedulerException(
                @NonNull String message
        ) {
            super(
                    message
            );
        }

        public OfflineBackupSchedulerException(
                @NonNull String message,
                @NonNull Throwable cause
        ) {
            super(
                    message,
                    cause
            );
        }
    }

    public static final class BackupFolderNotReadyException
            extends OfflineBackupSchedulerException {

        public BackupFolderNotReadyException(
                @NonNull String message
        ) {
            super(
                    message
            );
        }
    }
}