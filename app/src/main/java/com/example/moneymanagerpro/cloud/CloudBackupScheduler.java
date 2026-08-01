package com.example.moneymanagerpro.cloud;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Schedules the encrypted automatic cloud-backup worker.
 *
 * Scheduling design:
 *
 * 1. Cloud schedule settings are read per Firebase account.
 * 2. Off and Manual-only settings cancel existing automatic work.
 * 3. Daily, Weekly and Monthly schedules use one unique periodic worker.
 * 4. The worker wakes once every 24 hours near the preferred time.
 * 5. CloudAutomaticBackupWorker performs the final due check.
 * 6. Weekly and monthly backups therefore do not upload every day.
 * 7. Changing time or constraints recreates the unique periodic work.
 * 8. Firebase UID is not placed directly in the WorkManager name or tag.
 *
 * WorkManager execution is inexact. Android may delay a scheduled run
 * because of Doze, battery optimization, network constraints or charging
 * constraints.
 */
public final class CloudBackupScheduler {

    private static final String UNIQUE_WORK_NAME_PREFIX =
            "money_manager_cloud_backup_";

    private static final String ACCOUNT_TAG_PREFIX =
            "money_manager_cloud_account_";

    public static final String TAG_ALL_CLOUD_BACKUPS =
            "money_manager_all_cloud_backups";

    /**
     * The periodic worker checks once each day.
     *
     * BackupSchedulePreferences and CloudAutomaticBackupWorker decide
     * whether a Daily, Weekly or Monthly backup is actually due.
     */
    private static final long CHECK_INTERVAL_HOURS =
            24L;

    /**
     * The worker may run during the final two-hour window of each
     * 24-hour period.
     */
    private static final long FLEX_INTERVAL_HOURS =
            2L;

    private static final long BACKOFF_DELAY_MINUTES =
            30L;

    private static final int MAX_FIREBASE_UID_LENGTH =
            256;

    private CloudBackupScheduler() {
        // Utility class.
    }

    /**
     * Applies the saved cloud-backup schedule for the currently signed-in
     * Firebase account.
     *
     * Call this method after:
     *
     * 1. saving a recovery passphrase,
     * 2. changing cloud backup frequency,
     * 3. changing Wi-Fi-only or charging settings,
     * 4. changing preferred backup time/day/date,
     * 5. Firebase sign-in or app startup.
     */
    @NonNull
    public static ScheduleResult applySavedScheduleForCurrentUser(
            @NonNull Context context
    ) throws CloudBackupSchedulerException {

        FirebaseUser firebaseUser =
                FirebaseAuth
                        .getInstance()
                        .getCurrentUser();

        if (firebaseUser == null) {
            throw new CloudBackupSchedulerException(
                    "No Firebase cloud account is signed in."
            );
        }

        String firebaseUserId =
                validateFirebaseUserId(
                        firebaseUser.getUid()
                );

        if (!firebaseUser.isEmailVerified()) {
            cancelForAccount(
                    context,
                    firebaseUserId
            );

            throw new CloudBackupSchedulerException(
                    "Automatic cloud backup requires a "
                            + "verified Firebase email account."
            );
        }

        return applySavedSchedule(
                context,
                firebaseUserId
        );
    }

    /**
     * Applies the saved schedule for one Firebase account.
     *
     * This method does not store or receive the recovery passphrase.
     * It only verifies that CloudBackupKeyVault already contains one.
     */
    @NonNull
    public static ScheduleResult applySavedSchedule(
            @NonNull Context context,
            @NonNull String firebaseUserId
    ) throws CloudBackupSchedulerException {

        Context applicationContext =
                context.getApplicationContext();

        String verifiedUserId =
                validateFirebaseUserId(
                        firebaseUserId
                );

        BackupSchedulePreferences schedulePreferences =
                new BackupSchedulePreferences(
                        applicationContext
                );

        BackupSchedulePreferences.ScheduleSettings settings;

        try {
            settings =
                    schedulePreferences.getCloudSchedule(
                            verifiedUserId
                    );

        } catch (Exception exception) {
            throw new CloudBackupSchedulerException(
                    safeMessage(
                            exception,
                            "Cloud backup schedule settings "
                                    + "could not be read."
                    ),
                    exception
            );
        }

        /*
         * Off or Manual-only means no periodic worker should remain.
         */
        if (!settings.isAutomaticEnabled()) {
            cancelForAccount(
                    applicationContext,
                    verifiedUserId
            );

            return ScheduleResult.notScheduled(
                    settings
                            .getFrequency()
                            .getDisplayName(),
                    "Automatic cloud backup is "
                            + settings
                            .getFrequency()
                            .getDisplayName()
                            + "."
            );
        }

        CloudBackupKeyVault keyVault =
                new CloudBackupKeyVault(
                        applicationContext
                );

        if (!keyVault.hasSavedPassphrase(
                verifiedUserId
        )) {
            cancelForAccount(
                    applicationContext,
                    verifiedUserId
            );

            throw new MissingSavedPassphraseException(
                    "Automatic cloud backup cannot be scheduled "
                            + "until the recovery passphrase is "
                            + "securely saved on this device."
            );
        }

        long now =
                System.currentTimeMillis();

        long nextPreferredRunAt;

        try {
            nextPreferredRunAt =
                    schedulePreferences
                            .calculateNextPreferredRunAt(
                                    settings,
                                    now
                            );

        } catch (Exception exception) {
            throw new CloudBackupSchedulerException(
                    safeMessage(
                            exception,
                            "Next cloud backup time "
                                    + "could not be calculated."
                    ),
                    exception
            );
        }

        if (nextPreferredRunAt <= now) {
            throw new CloudBackupSchedulerException(
                    "Calculated cloud backup time is invalid."
            );
        }

        long initialDelayMillis =
                nextPreferredRunAt - now;

        Constraints constraints =
                createConstraints(
                        settings
                );

        String accountHash =
                createAccountHash(
                        verifiedUserId
                );

        String uniqueWorkName =
                createUniqueWorkName(
                        accountHash
                );

        String accountTag =
                createAccountTag(
                        accountHash
                );

        PeriodicWorkRequest workRequest =
                new PeriodicWorkRequest.Builder(
                        CloudAutomaticBackupWorker.class,
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
                                TAG_ALL_CLOUD_BACKUPS
                        )
                        .addTag(
                                accountTag
                        )
                        .build();

        try {
            WorkManager
                    .getInstance(
                            applicationContext
                    )
                    .enqueueUniquePeriodicWork(
                            uniqueWorkName,
                            ExistingPeriodicWorkPolicy
                                    .CANCEL_AND_REENQUEUE,
                            workRequest
                    );

        } catch (Exception exception) {
            throw new CloudBackupSchedulerException(
                    "Automatic cloud backup could not "
                            + "be scheduled with WorkManager.",
                    exception
            );
        }

        try {
            schedulePreferences.setCloudNextScheduledAt(
                    verifiedUserId,
                    nextPreferredRunAt
            );

        } catch (Exception exception) {
            /*
             * WorkManager scheduling has already been requested.
             * Cancel it so UI status and actual scheduling cannot disagree.
             */
            WorkManager
                    .getInstance(
                            applicationContext
                    )
                    .cancelUniqueWork(
                            uniqueWorkName
                    );

            throw new CloudBackupSchedulerException(
                    "Automatic cloud backup was prepared, but its "
                            + "next scheduled time could not be saved.",
                    exception
            );
        }

        return ScheduleResult.scheduled(
                workRequest.getId(),
                uniqueWorkName,
                settings
                        .getFrequency()
                        .getDisplayName(),
                nextPreferredRunAt,
                initialDelayMillis,
                settings.isWifiOnly(),
                settings.isChargingOnly()
        );
    }

    /**
     * Cancels automatic cloud backup for the currently signed-in account.
     */
    public static void cancelForCurrentUser(
            @NonNull Context context
    ) throws CloudBackupSchedulerException {

        FirebaseUser firebaseUser =
                FirebaseAuth
                        .getInstance()
                        .getCurrentUser();

        if (firebaseUser == null) {
            throw new CloudBackupSchedulerException(
                    "No Firebase cloud account is signed in."
            );
        }

        cancelForAccount(
                context,
                firebaseUser.getUid()
        );
    }

    /**
     * Cancels automatic cloud backup for one Firebase account.
     *
     * This does not:
     *
     * - delete the Firestore backup,
     * - delete the Firebase account,
     * - delete the locally saved recovery passphrase,
     * - change the user's selected frequency.
     */
    public static void cancelForAccount(
            @NonNull Context context,
            @NonNull String firebaseUserId
    ) throws CloudBackupSchedulerException {

        Context applicationContext =
                context.getApplicationContext();

        String verifiedUserId =
                validateFirebaseUserId(
                        firebaseUserId
                );

        String accountHash =
                createAccountHash(
                        verifiedUserId
                );

        String uniqueWorkName =
                createUniqueWorkName(
                        accountHash
                );

        try {
            WorkManager
                    .getInstance(
                            applicationContext
                    )
                    .cancelUniqueWork(
                            uniqueWorkName
                    );

        } catch (Exception exception) {
            throw new CloudBackupSchedulerException(
                    "Automatic cloud backup could not be cancelled.",
                    exception
            );
        }

        try {
            BackupSchedulePreferences schedulePreferences =
                    new BackupSchedulePreferences(
                            applicationContext
                    );

            schedulePreferences.setCloudNextScheduledAt(
                    verifiedUserId,
                    0L
            );

        } catch (Exception exception) {
            throw new CloudBackupSchedulerException(
                    "Cloud backup worker was cancelled, but its "
                            + "next scheduled time could not be cleared.",
                    exception
            );
        }
    }

    /**
     * Cancels every cloud-backup worker created by this scheduler.
     *
     * This is useful during:
     *
     * - complete app cloud-data reset,
     * - permanent Firebase account deletion,
     * - troubleshooting or emergency local reset.
     *
     * Account-specific schedule preferences are not removed here.
     */
    public static void cancelAllCloudBackupWork(
            @NonNull Context context
    ) throws CloudBackupSchedulerException {

        try {
            WorkManager
                    .getInstance(
                            context.getApplicationContext()
                    )
                    .cancelAllWorkByTag(
                            TAG_ALL_CLOUD_BACKUPS
                    );

        } catch (Exception exception) {
            throw new CloudBackupSchedulerException(
                    "All automatic cloud-backup workers "
                            + "could not be cancelled.",
                    exception
            );
        }
    }

    /**
     * Returns the account-specific unique WorkManager name.
     *
     * The raw Firebase UID is never returned.
     */
    @NonNull
    public static String getUniqueWorkName(
            @NonNull String firebaseUserId
    ) throws CloudBackupSchedulerException {

        String verifiedUserId =
                validateFirebaseUserId(
                        firebaseUserId
                );

        return createUniqueWorkName(
                createAccountHash(
                        verifiedUserId
                )
        );
    }

    /**
     * Builds WorkManager constraints from saved cloud settings.
     */
    @NonNull
    private static Constraints createConstraints(
            @NonNull BackupSchedulePreferences
                    .ScheduleSettings settings
    ) {
        NetworkType requiredNetworkType =
                settings.isWifiOnly()
                        ? NetworkType.UNMETERED
                        : NetworkType.CONNECTED;

        return new Constraints.Builder()
                .setRequiredNetworkType(
                        requiredNetworkType
                )
                .setRequiresCharging(
                        settings.isChargingOnly()
                )
                .build();
    }

    @NonNull
    private static String createUniqueWorkName(
            @NonNull String accountHash
    ) {
        return UNIQUE_WORK_NAME_PREFIX
                + accountHash;
    }

    @NonNull
    private static String createAccountTag(
            @NonNull String accountHash
    ) {
        return ACCOUNT_TAG_PREFIX
                + accountHash;
    }

    @NonNull
    private static String validateFirebaseUserId(
            @NonNull String firebaseUserId
    ) throws CloudBackupSchedulerException {

        String cleanUserId =
                firebaseUserId.trim();

        if (cleanUserId.isEmpty()) {
            throw new CloudBackupSchedulerException(
                    "Firebase cloud account UID is unavailable."
            );
        }

        if (cleanUserId.length()
                > MAX_FIREBASE_UID_LENGTH) {

            throw new CloudBackupSchedulerException(
                    "Firebase cloud account UID exceeds "
                            + "the supported length."
            );
        }

        if (cleanUserId.indexOf('\n') >= 0
                || cleanUserId.indexOf('\r') >= 0
                || cleanUserId.indexOf('\0') >= 0) {

            throw new CloudBackupSchedulerException(
                    "Firebase cloud account UID contains "
                            + "unsupported characters."
            );
        }

        return cleanUserId;
    }

    /**
     * Creates a non-reversible local identifier for WorkManager names.
     */
    @NonNull
    private static String createAccountHash(
            @NonNull String firebaseUserId
    ) throws CloudBackupSchedulerException {

        byte[] sourceBytes = null;
        byte[] hashBytes = null;

        try {
            sourceBytes =
                    firebaseUserId.getBytes(
                            StandardCharsets.UTF_8
                    );

            MessageDigest digest =
                    MessageDigest.getInstance(
                            "SHA-256"
                    );

            hashBytes =
                    digest.digest(
                            sourceBytes
                    );

            StringBuilder result =
                    new StringBuilder(
                            32
                    );

            /*
             * First 16 SHA-256 bytes create a 32-character account
             * identifier without exposing the Firebase UID.
             */
            for (int index = 0;
                 index < 16;
                 index++) {

                result.append(
                        String.format(
                                Locale.US,
                                "%02x",
                                hashBytes[index] & 0xff
                        )
                );
            }

            return result.toString();

        } catch (Exception exception) {
            throw new CloudBackupSchedulerException(
                    "Cloud account WorkManager identifier "
                            + "could not be created.",
                    exception
            );

        } finally {
            clearBytes(
                    sourceBytes
            );

            clearBytes(
                    hashBytes
            );
        }
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

        if (cleanMessage.length() > 500) {
            cleanMessage =
                    cleanMessage.substring(
                            0,
                            500
                    );
        }

        return cleanMessage;
    }

    private static void clearBytes(
            @Nullable byte[] bytes
    ) {
        if (bytes == null) {
            return;
        }

        Arrays.fill(
                bytes,
                (byte) 0
        );
    }

    /**
     * Immutable scheduler result for the future Cloud Backup screen.
     */
    public static final class ScheduleResult {

        private final boolean scheduled;

        private final UUID workRequestId;

        private final String uniqueWorkName;

        private final String frequencyDisplayName;

        private final String message;

        private final long nextPreferredRunAtMillis;

        private final long initialDelayMillis;

        private final boolean wifiOnly;

        private final boolean chargingOnly;

        private ScheduleResult(
                boolean scheduled,
                @Nullable UUID workRequestId,
                @NonNull String uniqueWorkName,
                @NonNull String frequencyDisplayName,
                @NonNull String message,
                long nextPreferredRunAtMillis,
                long initialDelayMillis,
                boolean wifiOnly,
                boolean chargingOnly
        ) {
            this.scheduled =
                    scheduled;

            this.workRequestId =
                    workRequestId;

            this.uniqueWorkName =
                    uniqueWorkName;

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

            this.wifiOnly =
                    wifiOnly;

            this.chargingOnly =
                    chargingOnly;
        }

        @NonNull
        private static ScheduleResult scheduled(
                @NonNull UUID workRequestId,
                @NonNull String uniqueWorkName,
                @NonNull String frequencyDisplayName,
                long nextPreferredRunAtMillis,
                long initialDelayMillis,
                boolean wifiOnly,
                boolean chargingOnly
        ) {
            return new ScheduleResult(
                    true,
                    workRequestId,
                    uniqueWorkName,
                    frequencyDisplayName,
                    "Automatic encrypted cloud backup "
                            + "was scheduled successfully.",
                    nextPreferredRunAtMillis,
                    initialDelayMillis,
                    wifiOnly,
                    chargingOnly
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
                    "",
                    frequencyDisplayName,
                    message,
                    0L,
                    0L,
                    false,
                    false
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
            return uniqueWorkName;
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

        public boolean isWifiOnly() {
            return wifiOnly;
        }

        public boolean isChargingOnly() {
            return chargingOnly;
        }
    }

    public static class CloudBackupSchedulerException
            extends Exception {

        public CloudBackupSchedulerException(
                @NonNull String message
        ) {
            super(
                    message
            );
        }

        public CloudBackupSchedulerException(
                @NonNull String message,
                @NonNull Throwable cause
        ) {
            super(
                    message,
                    cause
            );
        }
    }

    public static final class MissingSavedPassphraseException
            extends CloudBackupSchedulerException {

        public MissingSavedPassphraseException(
                @NonNull String message
        ) {
            super(
                    message
            );
        }
    }
}