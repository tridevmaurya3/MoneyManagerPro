package com.example.moneymanagerpro.cloud;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Calendar;
import java.util.Locale;

/**
 * Stores cloud and offline backup scheduling preferences and non-sensitive
 * backup status information.
 *
 * Security rules:
 *
 * 1. The Firebase UID is never stored directly in preference keys.
 * 2. The cloud-backup recovery passphrase is never stored here.
 * 3. Only scheduling choices and technical backup status are stored.
 * 4. Cloud settings are isolated per Firebase account.
 * 5. Offline settings are device-wide because the selected document-tree
 *    folder belongs to the device rather than a Firebase account.
 */
public final class BackupSchedulePreferences {

    public static final int PREFERENCES_SCHEMA_VERSION = 1;

    public static final int DEFAULT_PREFERRED_HOUR = 2;
    public static final int DEFAULT_PREFERRED_MINUTE = 0;
    public static final int DEFAULT_WEEKLY_DAY = Calendar.SUNDAY;
    public static final int DEFAULT_MONTHLY_DAY = 1;

    private static final String PREFERENCES_NAME =
            "money_manager_backup_schedule_preferences";

    private static final String SCOPE_OFFLINE = "offline_";
    private static final String SCOPE_CLOUD_PREFIX = "cloud_";

    private static final String KEY_SCHEMA_VERSION = "schema_version";
    private static final String KEY_FREQUENCY = "frequency";
    private static final String KEY_WIFI_ONLY = "wifi_only";
    private static final String KEY_CHARGING_ONLY = "charging_only";
    private static final String KEY_PREFERRED_HOUR = "preferred_hour";
    private static final String KEY_PREFERRED_MINUTE = "preferred_minute";
    private static final String KEY_WEEKLY_DAY = "weekly_day";
    private static final String KEY_MONTHLY_DAY = "monthly_day";
    private static final String KEY_UPDATED_AT = "updated_at";

    private static final String KEY_LAST_ATTEMPT_AT = "last_attempt_at";
    private static final String KEY_LAST_SUCCESS_AT = "last_success_at";
    private static final String KEY_LAST_FAILURE_AT = "last_failure_at";

    private static final String KEY_LAST_FAILURE_MESSAGE =
            "last_failure_message";

    private static final String KEY_LAST_BACKUP_ID = "last_backup_id";
    private static final String KEY_LAST_RECORD_COUNT = "last_record_count";
    private static final String KEY_LAST_BYTE_COUNT = "last_byte_count";

    private static final String KEY_NEXT_SCHEDULED_AT =
            "next_scheduled_at";

    private static final String KEY_CONSECUTIVE_FAILURES =
            "consecutive_failures";

    private static final int MAX_FIREBASE_UID_LENGTH = 256;
    private static final int MAX_BACKUP_ID_LENGTH = 180;
    private static final int MAX_FAILURE_MESSAGE_LENGTH = 500;

    private final SharedPreferences preferences;

    public BackupSchedulePreferences(
            @NonNull Context context
    ) {
        Context applicationContext =
                context.getApplicationContext();

        preferences =
                applicationContext.getSharedPreferences(
                        PREFERENCES_NAME,
                        Context.MODE_PRIVATE
                );
    }

    /**
     * Returns schedule settings for the current Firebase account.
     *
     * Default cloud behavior is Manual only, Wi-Fi only and no charging
     * requirement.
     */
    @NonNull
    public synchronized ScheduleSettings getCloudSchedule(
            @NonNull String firebaseUserId
    ) throws BackupScheduleException {

        String scope =
                createCloudScope(
                        firebaseUserId
                );

        return readSchedule(
                scope,
                BackupFrequency.MANUAL_ONLY,
                true,
                false
        );
    }

    /**
     * Saves cloud schedule settings for one Firebase account.
     */
    public synchronized void saveCloudSchedule(
            @NonNull String firebaseUserId,
            @NonNull ScheduleSettings settings
    ) throws BackupScheduleException {

        validateSchedule(
                settings
        );

        saveSchedule(
                createCloudScope(
                        firebaseUserId
                ),
                settings
        );
    }

    /**
     * Returns device-wide offline-backup schedule settings.
     *
     * Wi-Fi is always false for offline backup because no network is needed.
     */
    @NonNull
    public synchronized ScheduleSettings getOfflineSchedule() {
        try {
            ScheduleSettings saved =
                    readSchedule(
                            SCOPE_OFFLINE,
                            BackupFrequency.MANUAL_ONLY,
                            false,
                            false
                    );

            if (!saved.isWifiOnly()) {
                return saved;
            }

            return saved.withWifiOnly(
                    false
            );

        } catch (BackupScheduleException exception) {
            return createDefaultOfflineSchedule();
        }
    }

    /**
     * Saves the device-wide offline-backup schedule.
     */
    public synchronized void saveOfflineSchedule(
            @NonNull ScheduleSettings settings
    ) throws BackupScheduleException {

        validateSchedule(
                settings
        );

        saveSchedule(
                SCOPE_OFFLINE,
                settings.withWifiOnly(
                        false
                )
        );
    }

    /**
     * Restores default cloud scheduling without deleting backup history.
     */
    public synchronized void resetCloudSchedule(
            @NonNull String firebaseUserId
    ) throws BackupScheduleException {

        saveCloudSchedule(
                firebaseUserId,
                createDefaultCloudSchedule()
        );
    }

    /**
     * Restores default offline scheduling without deleting backup history.
     */
    public synchronized void resetOfflineSchedule()
            throws BackupScheduleException {

        saveOfflineSchedule(
                createDefaultOfflineSchedule()
        );
    }

    @NonNull
    public ScheduleSettings createDefaultCloudSchedule() {
        return new ScheduleSettings(
                BackupFrequency.MANUAL_ONLY,
                true,
                false,
                DEFAULT_PREFERRED_HOUR,
                DEFAULT_PREFERRED_MINUTE,
                DEFAULT_WEEKLY_DAY,
                DEFAULT_MONTHLY_DAY,
                0L
        );
    }

    @NonNull
    public ScheduleSettings createDefaultOfflineSchedule() {
        return new ScheduleSettings(
                BackupFrequency.MANUAL_ONLY,
                false,
                false,
                DEFAULT_PREFERRED_HOUR,
                DEFAULT_PREFERRED_MINUTE,
                DEFAULT_WEEKLY_DAY,
                DEFAULT_MONTHLY_DAY,
                0L
        );
    }

    @NonNull
    public synchronized BackupStatus getCloudStatus(
            @NonNull String firebaseUserId
    ) throws BackupScheduleException {

        return readStatus(
                createCloudScope(
                        firebaseUserId
                )
        );
    }

    @NonNull
    public synchronized BackupStatus getOfflineStatus() {
        return readStatus(
                SCOPE_OFFLINE
        );
    }

    public synchronized void recordCloudBackupAttempt(
            @NonNull String firebaseUserId,
            long attemptedAtMillis
    ) throws BackupScheduleException {

        recordAttempt(
                createCloudScope(
                        firebaseUserId
                ),
                attemptedAtMillis
        );
    }

    public synchronized void recordOfflineBackupAttempt(
            long attemptedAtMillis
    ) throws BackupScheduleException {

        recordAttempt(
                SCOPE_OFFLINE,
                attemptedAtMillis
        );
    }

    public synchronized void recordCloudBackupSuccess(
            @NonNull String firebaseUserId,
            long succeededAtMillis,
            @Nullable String backupId,
            int recordCount,
            long encryptedByteCount
    ) throws BackupScheduleException {

        recordSuccess(
                createCloudScope(
                        firebaseUserId
                ),
                succeededAtMillis,
                backupId,
                recordCount,
                encryptedByteCount
        );
    }

    public synchronized void recordOfflineBackupSuccess(
            long succeededAtMillis,
            @Nullable String backupId,
            int recordCount,
            long backupByteCount
    ) throws BackupScheduleException {

        recordSuccess(
                SCOPE_OFFLINE,
                succeededAtMillis,
                backupId,
                recordCount,
                backupByteCount
        );
    }

    public synchronized void recordCloudBackupFailure(
            @NonNull String firebaseUserId,
            long failedAtMillis,
            @Nullable String failureMessage
    ) throws BackupScheduleException {

        recordFailure(
                createCloudScope(
                        firebaseUserId
                ),
                failedAtMillis,
                failureMessage
        );
    }

    public synchronized void recordOfflineBackupFailure(
            long failedAtMillis,
            @Nullable String failureMessage
    ) throws BackupScheduleException {

        recordFailure(
                SCOPE_OFFLINE,
                failedAtMillis,
                failureMessage
        );
    }

    public synchronized void setCloudNextScheduledAt(
            @NonNull String firebaseUserId,
            long nextScheduledAtMillis
    ) throws BackupScheduleException {

        setNextScheduledAt(
                createCloudScope(
                        firebaseUserId
                ),
                nextScheduledAtMillis
        );
    }

    public synchronized void setOfflineNextScheduledAt(
            long nextScheduledAtMillis
    ) throws BackupScheduleException {

        setNextScheduledAt(
                SCOPE_OFFLINE,
                nextScheduledAtMillis
        );
    }

    /**
     * Removes schedule and status data for one Firebase account.
     *
     * This should be called after permanent account deletion. It does not
     * remove the Android-Keystore passphrase; CloudBackupKeyVault handles it.
     */
    public synchronized void clearCloudAccountData(
            @NonNull String firebaseUserId
    ) throws BackupScheduleException {

        clearScope(
                createCloudScope(
                        firebaseUserId
                )
        );
    }

    /**
     * Removes only device-wide offline schedule and status information.
     *
     * It does not delete the selected backup folder or any backup file.
     */
    public synchronized void clearOfflineScheduleData()
            throws BackupScheduleException {

        clearScope(
                SCOPE_OFFLINE
        );
    }

    /**
     * Calculates the next preferred run time in the device timezone.
     *
     * WorkManager may execute later because Android background scheduling is
     * inexact. A return value of zero means automatic backup is disabled.
     */
    public long calculateNextPreferredRunAt(
            @NonNull ScheduleSettings settings,
            long nowMillis
    ) throws BackupScheduleException {

        validateSchedule(
                settings
        );

        if (!settings.getFrequency().isAutomatic()) {
            return 0L;
        }

        long safeNow =
                nowMillis > 0L
                        ? nowMillis
                        : System.currentTimeMillis();

        Calendar now =
                Calendar.getInstance();

        now.setTimeInMillis(
                safeNow
        );

        Calendar next =
                Calendar.getInstance();

        next.setTimeInMillis(
                safeNow
        );

        next.set(
                Calendar.HOUR_OF_DAY,
                settings.getPreferredHour()
        );

        next.set(
                Calendar.MINUTE,
                settings.getPreferredMinute()
        );

        next.set(
                Calendar.SECOND,
                0
        );

        next.set(
                Calendar.MILLISECOND,
                0
        );

        switch (settings.getFrequency()) {
            case DAILY:
                if (!next.after(now)) {
                    next.add(
                            Calendar.DAY_OF_MONTH,
                            1
                    );
                }
                break;

            case WEEKLY:
                moveToWeeklyDay(
                        next,
                        settings.getWeeklyDayOfWeek()
                );

                if (!next.after(now)) {
                    next.add(
                            Calendar.DAY_OF_MONTH,
                            7
                    );
                }
                break;

            case MONTHLY:
                next.set(
                        Calendar.DAY_OF_MONTH,
                        settings.getMonthlyDayOfMonth()
                );

                if (!next.after(now)) {
                    next.add(
                            Calendar.MONTH,
                            1
                    );

                    next.set(
                            Calendar.DAY_OF_MONTH,
                            settings.getMonthlyDayOfMonth()
                    );
                }
                break;

            default:
                return 0L;
        }

        return next.getTimeInMillis();
    }

    /**
     * Determines whether an automatic backup is overdue according to the
     * most recent successful backup time.
     */
    public boolean isAutomaticBackupDue(
            @NonNull ScheduleSettings settings,
            @NonNull BackupStatus status,
            long nowMillis
    ) throws BackupScheduleException {

        validateSchedule(
                settings
        );

        if (!settings.getFrequency().isAutomatic()) {
            return false;
        }

        long safeNow =
                nowMillis > 0L
                        ? nowMillis
                        : System.currentTimeMillis();

        long lastSuccess =
                status.getLastSuccessAtMillis();

        if (lastSuccess <= 0L) {
            return true;
        }

        Calendar dueAt =
                Calendar.getInstance();

        dueAt.setTimeInMillis(
                lastSuccess
        );

        switch (settings.getFrequency()) {
            case DAILY:
                dueAt.add(
                        Calendar.DAY_OF_MONTH,
                        1
                );
                break;

            case WEEKLY:
                dueAt.add(
                        Calendar.DAY_OF_MONTH,
                        7
                );
                break;

            case MONTHLY:
                dueAt.add(
                        Calendar.MONTH,
                        1
                );
                break;

            default:
                return false;
        }

        return safeNow >= dueAt.getTimeInMillis();
    }

    private void moveToWeeklyDay(
            @NonNull Calendar calendar,
            int requestedDayOfWeek
    ) {
        int currentDay =
                calendar.get(
                        Calendar.DAY_OF_WEEK
                );

        int daysForward =
                requestedDayOfWeek - currentDay;

        if (daysForward < 0) {
            daysForward += 7;
        }

        calendar.add(
                Calendar.DAY_OF_MONTH,
                daysForward
        );
    }

    @NonNull
    private ScheduleSettings readSchedule(
            @NonNull String scope,
            @NonNull BackupFrequency defaultFrequency,
            boolean defaultWifiOnly,
            boolean defaultChargingOnly
    ) throws BackupScheduleException {

        String storedFrequency =
                getStringSafely(
                        scopedKey(
                                scope,
                                KEY_FREQUENCY
                        ),
                        defaultFrequency.getStoredValue()
                );

        BackupFrequency frequency =
                BackupFrequency.fromStoredValue(
                        storedFrequency,
                        defaultFrequency
                );

        int preferredHour =
                getIntSafely(
                        scopedKey(
                                scope,
                                KEY_PREFERRED_HOUR
                        ),
                        DEFAULT_PREFERRED_HOUR
                );

        int preferredMinute =
                getIntSafely(
                        scopedKey(
                                scope,
                                KEY_PREFERRED_MINUTE
                        ),
                        DEFAULT_PREFERRED_MINUTE
                );

        int weeklyDay =
                getIntSafely(
                        scopedKey(
                                scope,
                                KEY_WEEKLY_DAY
                        ),
                        DEFAULT_WEEKLY_DAY
                );

        int monthlyDay =
                getIntSafely(
                        scopedKey(
                                scope,
                                KEY_MONTHLY_DAY
                        ),
                        DEFAULT_MONTHLY_DAY
                );

        ScheduleSettings settings =
                new ScheduleSettings(
                        frequency,
                        getBooleanSafely(
                                scopedKey(
                                        scope,
                                        KEY_WIFI_ONLY
                                ),
                                defaultWifiOnly
                        ),
                        getBooleanSafely(
                                scopedKey(
                                        scope,
                                        KEY_CHARGING_ONLY
                                ),
                                defaultChargingOnly
                        ),
                        preferredHour,
                        preferredMinute,
                        weeklyDay,
                        monthlyDay,
                        getLongSafely(
                                scopedKey(
                                        scope,
                                        KEY_UPDATED_AT
                                ),
                                0L
                        )
                );

        try {
            validateSchedule(
                    settings
            );

            return settings;

        } catch (BackupScheduleException exception) {
            return new ScheduleSettings(
                    defaultFrequency,
                    defaultWifiOnly,
                    defaultChargingOnly,
                    DEFAULT_PREFERRED_HOUR,
                    DEFAULT_PREFERRED_MINUTE,
                    DEFAULT_WEEKLY_DAY,
                    DEFAULT_MONTHLY_DAY,
                    0L
            );
        }
    }

    private void saveSchedule(
            @NonNull String scope,
            @NonNull ScheduleSettings settings
    ) throws BackupScheduleException {

        long updatedAt =
                System.currentTimeMillis();

        boolean saved =
                preferences.edit()
                        .putInt(
                                KEY_SCHEMA_VERSION,
                                PREFERENCES_SCHEMA_VERSION
                        )
                        .putString(
                                scopedKey(
                                        scope,
                                        KEY_FREQUENCY
                                ),
                                settings
                                        .getFrequency()
                                        .getStoredValue()
                        )
                        .putBoolean(
                                scopedKey(
                                        scope,
                                        KEY_WIFI_ONLY
                                ),
                                settings.isWifiOnly()
                        )
                        .putBoolean(
                                scopedKey(
                                        scope,
                                        KEY_CHARGING_ONLY
                                ),
                                settings.isChargingOnly()
                        )
                        .putInt(
                                scopedKey(
                                        scope,
                                        KEY_PREFERRED_HOUR
                                ),
                                settings.getPreferredHour()
                        )
                        .putInt(
                                scopedKey(
                                        scope,
                                        KEY_PREFERRED_MINUTE
                                ),
                                settings.getPreferredMinute()
                        )
                        .putInt(
                                scopedKey(
                                        scope,
                                        KEY_WEEKLY_DAY
                                ),
                                settings.getWeeklyDayOfWeek()
                        )
                        .putInt(
                                scopedKey(
                                        scope,
                                        KEY_MONTHLY_DAY
                                ),
                                settings.getMonthlyDayOfMonth()
                        )
                        .putLong(
                                scopedKey(
                                        scope,
                                        KEY_UPDATED_AT
                                ),
                                updatedAt
                        )
                        .commit();

        if (!saved) {
            throw new BackupScheduleException(
                    "Backup schedule settings could not be saved."
            );
        }
    }

    @NonNull
    private BackupStatus readStatus(
            @NonNull String scope
    ) {
        return new BackupStatus(
                getLongSafely(
                        scopedKey(
                                scope,
                                KEY_LAST_ATTEMPT_AT
                        ),
                        0L
                ),
                getLongSafely(
                        scopedKey(
                                scope,
                                KEY_LAST_SUCCESS_AT
                        ),
                        0L
                ),
                getLongSafely(
                        scopedKey(
                                scope,
                                KEY_LAST_FAILURE_AT
                        ),
                        0L
                ),
                getStringSafely(
                        scopedKey(
                                scope,
                                KEY_LAST_FAILURE_MESSAGE
                        ),
                        ""
                ),
                getStringSafely(
                        scopedKey(
                                scope,
                                KEY_LAST_BACKUP_ID
                        ),
                        ""
                ),
                getIntSafely(
                        scopedKey(
                                scope,
                                KEY_LAST_RECORD_COUNT
                        ),
                        0
                ),
                getLongSafely(
                        scopedKey(
                                scope,
                                KEY_LAST_BYTE_COUNT
                        ),
                        0L
                ),
                getLongSafely(
                        scopedKey(
                                scope,
                                KEY_NEXT_SCHEDULED_AT
                        ),
                        0L
                ),
                getIntSafely(
                        scopedKey(
                                scope,
                                KEY_CONSECUTIVE_FAILURES
                        ),
                        0
                )
        );
    }

    private void recordAttempt(
            @NonNull String scope,
            long attemptedAtMillis
    ) throws BackupScheduleException {

        long safeTime =
                normalizeTimestamp(
                        attemptedAtMillis
                );

        boolean saved =
                preferences.edit()
                        .putLong(
                                scopedKey(
                                        scope,
                                        KEY_LAST_ATTEMPT_AT
                                ),
                                safeTime
                        )
                        .commit();

        if (!saved) {
            throw new BackupScheduleException(
                    "Backup attempt status could not be saved."
            );
        }
    }

    private void recordSuccess(
            @NonNull String scope,
            long succeededAtMillis,
            @Nullable String backupId,
            int recordCount,
            long byteCount
    ) throws BackupScheduleException {

        if (recordCount < 0) {
            throw new BackupScheduleException(
                    "Backup record count cannot be negative."
            );
        }

        if (byteCount < 0L) {
            throw new BackupScheduleException(
                    "Backup byte count cannot be negative."
            );
        }

        String safeBackupId =
                sanitizeBackupId(
                        backupId
                );

        long safeTime =
                normalizeTimestamp(
                        succeededAtMillis
                );

        SharedPreferences.Editor editor =
                preferences.edit()
                        .putLong(
                                scopedKey(
                                        scope,
                                        KEY_LAST_ATTEMPT_AT
                                ),
                                safeTime
                        )
                        .putLong(
                                scopedKey(
                                        scope,
                                        KEY_LAST_SUCCESS_AT
                                ),
                                safeTime
                        )
                        .putLong(
                                scopedKey(
                                        scope,
                                        KEY_LAST_FAILURE_AT
                                ),
                                0L
                        )
                        .putString(
                                scopedKey(
                                        scope,
                                        KEY_LAST_FAILURE_MESSAGE
                                ),
                                ""
                        )
                        .putString(
                                scopedKey(
                                        scope,
                                        KEY_LAST_BACKUP_ID
                                ),
                                safeBackupId
                        )
                        .putInt(
                                scopedKey(
                                        scope,
                                        KEY_LAST_RECORD_COUNT
                                ),
                                recordCount
                        )
                        .putLong(
                                scopedKey(
                                        scope,
                                        KEY_LAST_BYTE_COUNT
                                ),
                                byteCount
                        )
                        .putInt(
                                scopedKey(
                                        scope,
                                        KEY_CONSECUTIVE_FAILURES
                                ),
                                0
                        );

        if (!editor.commit()) {
            throw new BackupScheduleException(
                    "Successful backup status could not be saved."
            );
        }
    }

    private void recordFailure(
            @NonNull String scope,
            long failedAtMillis,
            @Nullable String failureMessage
    ) throws BackupScheduleException {

        long safeTime =
                normalizeTimestamp(
                        failedAtMillis
                );

        int previousFailures =
                getIntSafely(
                        scopedKey(
                                scope,
                                KEY_CONSECUTIVE_FAILURES
                        ),
                        0
                );

        int nextFailureCount =
                previousFailures == Integer.MAX_VALUE
                        ? Integer.MAX_VALUE
                        : previousFailures + 1;

        boolean saved =
                preferences.edit()
                        .putLong(
                                scopedKey(
                                        scope,
                                        KEY_LAST_ATTEMPT_AT
                                ),
                                safeTime
                        )
                        .putLong(
                                scopedKey(
                                        scope,
                                        KEY_LAST_FAILURE_AT
                                ),
                                safeTime
                        )
                        .putString(
                                scopedKey(
                                        scope,
                                        KEY_LAST_FAILURE_MESSAGE
                                ),
                                sanitizeFailureMessage(
                                        failureMessage
                                )
                        )
                        .putInt(
                                scopedKey(
                                        scope,
                                        KEY_CONSECUTIVE_FAILURES
                                ),
                                nextFailureCount
                        )
                        .commit();

        if (!saved) {
            throw new BackupScheduleException(
                    "Failed backup status could not be saved."
            );
        }
    }

    private void setNextScheduledAt(
            @NonNull String scope,
            long nextScheduledAtMillis
    ) throws BackupScheduleException {

        if (nextScheduledAtMillis < 0L) {
            throw new BackupScheduleException(
                    "Next scheduled backup time cannot be negative."
            );
        }

        boolean saved =
                preferences.edit()
                        .putLong(
                                scopedKey(
                                        scope,
                                        KEY_NEXT_SCHEDULED_AT
                                ),
                                nextScheduledAtMillis
                        )
                        .commit();

        if (!saved) {
            throw new BackupScheduleException(
                    "Next backup schedule time could not be saved."
            );
        }
    }

    private void clearScope(
            @NonNull String scope
    ) throws BackupScheduleException {

        SharedPreferences.Editor editor =
                preferences.edit();

        editor.remove(
                scopedKey(
                        scope,
                        KEY_FREQUENCY
                )
        );

        editor.remove(
                scopedKey(
                        scope,
                        KEY_WIFI_ONLY
                )
        );

        editor.remove(
                scopedKey(
                        scope,
                        KEY_CHARGING_ONLY
                )
        );

        editor.remove(
                scopedKey(
                        scope,
                        KEY_PREFERRED_HOUR
                )
        );

        editor.remove(
                scopedKey(
                        scope,
                        KEY_PREFERRED_MINUTE
                )
        );

        editor.remove(
                scopedKey(
                        scope,
                        KEY_WEEKLY_DAY
                )
        );

        editor.remove(
                scopedKey(
                        scope,
                        KEY_MONTHLY_DAY
                )
        );

        editor.remove(
                scopedKey(
                        scope,
                        KEY_UPDATED_AT
                )
        );

        editor.remove(
                scopedKey(
                        scope,
                        KEY_LAST_ATTEMPT_AT
                )
        );

        editor.remove(
                scopedKey(
                        scope,
                        KEY_LAST_SUCCESS_AT
                )
        );

        editor.remove(
                scopedKey(
                        scope,
                        KEY_LAST_FAILURE_AT
                )
        );

        editor.remove(
                scopedKey(
                        scope,
                        KEY_LAST_FAILURE_MESSAGE
                )
        );

        editor.remove(
                scopedKey(
                        scope,
                        KEY_LAST_BACKUP_ID
                )
        );

        editor.remove(
                scopedKey(
                        scope,
                        KEY_LAST_RECORD_COUNT
                )
        );

        editor.remove(
                scopedKey(
                        scope,
                        KEY_LAST_BYTE_COUNT
                )
        );

        editor.remove(
                scopedKey(
                        scope,
                        KEY_NEXT_SCHEDULED_AT
                )
        );

        editor.remove(
                scopedKey(
                        scope,
                        KEY_CONSECUTIVE_FAILURES
                )
        );

        if (!editor.commit()) {
            throw new BackupScheduleException(
                    "Backup schedule data could not be cleared."
            );
        }
    }

    private void validateSchedule(
            @NonNull ScheduleSettings settings
    ) throws BackupScheduleException {

        if (settings.getFrequency() == null) {
            throw new BackupScheduleException(
                    "Backup frequency is unavailable."
            );
        }

        if (settings.getPreferredHour() < 0
                || settings.getPreferredHour() > 23) {

            throw new BackupScheduleException(
                    "Preferred backup hour must be between 0 and 23."
            );
        }

        if (settings.getPreferredMinute() < 0
                || settings.getPreferredMinute() > 59) {

            throw new BackupScheduleException(
                    "Preferred backup minute must be between 0 and 59."
            );
        }

        if (settings.getWeeklyDayOfWeek() < Calendar.SUNDAY
                || settings.getWeeklyDayOfWeek() > Calendar.SATURDAY) {

            throw new BackupScheduleException(
                    "Weekly backup day is invalid."
            );
        }

        /*
         * The range ends at 28 so monthly backup exists in every month.
         */
        if (settings.getMonthlyDayOfMonth() < 1
                || settings.getMonthlyDayOfMonth() > 28) {

            throw new BackupScheduleException(
                    "Monthly backup day must be between 1 and 28."
            );
        }
    }

    @NonNull
    private String createCloudScope(
            @NonNull String firebaseUserId
    ) throws BackupScheduleException {

        String verifiedUserId =
                validateFirebaseUserId(
                        firebaseUserId
                );

        return SCOPE_CLOUD_PREFIX
                + createAccountHash(
                verifiedUserId
        )
                + "_";
    }

    @NonNull
    private String validateFirebaseUserId(
            @NonNull String firebaseUserId
    ) throws BackupScheduleException {

        String cleanUserId =
                firebaseUserId.trim();

        if (cleanUserId.isEmpty()) {
            throw new BackupScheduleException(
                    "Firebase cloud account UID is unavailable."
            );
        }

        if (cleanUserId.length()
                > MAX_FIREBASE_UID_LENGTH) {

            throw new BackupScheduleException(
                    "Firebase cloud account UID exceeds "
                            + "the supported length."
            );
        }

        if (cleanUserId.indexOf('\n') >= 0
                || cleanUserId.indexOf('\r') >= 0
                || cleanUserId.indexOf('\0') >= 0) {

            throw new BackupScheduleException(
                    "Firebase cloud account UID contains "
                            + "unsupported characters."
            );
        }

        return cleanUserId;
    }

    @NonNull
    private String createAccountHash(
            @NonNull String firebaseUserId
    ) throws BackupScheduleException {

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
            throw new BackupScheduleException(
                    "Cloud account schedule identifier "
                            + "could not be created.",
                    exception
            );

        } finally {
            CloudBackupEncryption.clearSensitiveBytes(
                    sourceBytes
            );

            CloudBackupEncryption.clearSensitiveBytes(
                    hashBytes
            );
        }
    }

    @NonNull
    private String scopedKey(
            @NonNull String scope,
            @NonNull String key
    ) {
        return scope + key;
    }

    private long normalizeTimestamp(
            long timestamp
    ) {
        return timestamp > 0L
                ? timestamp
                : System.currentTimeMillis();
    }

    @NonNull
    private String sanitizeBackupId(
            @Nullable String backupId
    ) throws BackupScheduleException {

        if (backupId == null) {
            return "";
        }

        String cleanBackupId =
                backupId.trim();

        if (cleanBackupId.length()
                > MAX_BACKUP_ID_LENGTH) {

            throw new BackupScheduleException(
                    "Backup ID exceeds the supported length."
            );
        }

        if (!cleanBackupId.isEmpty()
                && !cleanBackupId.matches(
                "[A-Za-z0-9_.-]+"
        )) {

            throw new BackupScheduleException(
                    "Backup ID contains unsupported characters."
            );
        }

        return cleanBackupId;
    }

    @NonNull
    private String sanitizeFailureMessage(
            @Nullable String message
    ) {
        if (message == null) {
            return "Backup failed.";
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
                    "Backup failed.";
        }

        if (cleanMessage.length()
                > MAX_FAILURE_MESSAGE_LENGTH) {

            cleanMessage =
                    cleanMessage.substring(
                            0,
                            MAX_FAILURE_MESSAGE_LENGTH
                    );
        }

        return cleanMessage;
    }

    @NonNull
    private String getStringSafely(
            @NonNull String key,
            @NonNull String fallback
    ) {
        try {
            String value =
                    preferences.getString(
                            key,
                            fallback
                    );

            return value == null
                    ? fallback
                    : value;

        } catch (ClassCastException exception) {
            return fallback;
        }
    }

    private boolean getBooleanSafely(
            @NonNull String key,
            boolean fallback
    ) {
        try {
            return preferences.getBoolean(
                    key,
                    fallback
            );

        } catch (ClassCastException exception) {
            return fallback;
        }
    }

    private int getIntSafely(
            @NonNull String key,
            int fallback
    ) {
        try {
            return preferences.getInt(
                    key,
                    fallback
            );

        } catch (ClassCastException exception) {
            return fallback;
        }
    }

    private long getLongSafely(
            @NonNull String key,
            long fallback
    ) {
        try {
            return preferences.getLong(
                    key,
                    fallback
            );

        } catch (ClassCastException exception) {
            return fallback;
        }
    }

    public enum BackupFrequency {

        OFF(
                "off",
                "Off",
                false
        ),

        MANUAL_ONLY(
                "manual_only",
                "Manual only",
                false
        ),

        DAILY(
                "daily",
                "Daily",
                true
        ),

        WEEKLY(
                "weekly",
                "Weekly",
                true
        ),

        MONTHLY(
                "monthly",
                "Monthly",
                true
        );

        private final String storedValue;
        private final String displayName;
        private final boolean automatic;

        BackupFrequency(
                @NonNull String storedValue,
                @NonNull String displayName,
                boolean automatic
        ) {
            this.storedValue =
                    storedValue;

            this.displayName =
                    displayName;

            this.automatic =
                    automatic;
        }

        @NonNull
        public String getStoredValue() {
            return storedValue;
        }

        @NonNull
        public String getDisplayName() {
            return displayName;
        }

        public boolean isAutomatic() {
            return automatic;
        }

        @NonNull
        public static BackupFrequency fromStoredValue(
                @Nullable String storedValue,
                @NonNull BackupFrequency fallback
        ) {
            if (storedValue == null) {
                return fallback;
            }

            String normalized =
                    storedValue
                            .trim()
                            .toLowerCase(
                                    Locale.US
                            );

            if ("manual".equals(
                    normalized
            )
                    || "manual-only".equals(
                    normalized
            )) {

                normalized =
                        MANUAL_ONLY.storedValue;
            }

            for (BackupFrequency frequency :
                    values()) {

                if (frequency
                        .storedValue
                        .equals(
                                normalized
                        )) {

                    return frequency;
                }
            }

            return fallback;
        }
    }

    /**
     * Immutable schedule selected by the user.
     */
    public static final class ScheduleSettings {

        private final BackupFrequency frequency;
        private final boolean wifiOnly;
        private final boolean chargingOnly;
        private final int preferredHour;
        private final int preferredMinute;
        private final int weeklyDayOfWeek;
        private final int monthlyDayOfMonth;
        private final long updatedAtMillis;

        public ScheduleSettings(
                @NonNull BackupFrequency frequency,
                boolean wifiOnly,
                boolean chargingOnly,
                int preferredHour,
                int preferredMinute,
                int weeklyDayOfWeek,
                int monthlyDayOfMonth
        ) {
            this(
                    frequency,
                    wifiOnly,
                    chargingOnly,
                    preferredHour,
                    preferredMinute,
                    weeklyDayOfWeek,
                    monthlyDayOfMonth,
                    0L
            );
        }

        private ScheduleSettings(
                @NonNull BackupFrequency frequency,
                boolean wifiOnly,
                boolean chargingOnly,
                int preferredHour,
                int preferredMinute,
                int weeklyDayOfWeek,
                int monthlyDayOfMonth,
                long updatedAtMillis
        ) {
            this.frequency =
                    frequency;

            this.wifiOnly =
                    wifiOnly;

            this.chargingOnly =
                    chargingOnly;

            this.preferredHour =
                    preferredHour;

            this.preferredMinute =
                    preferredMinute;

            this.weeklyDayOfWeek =
                    weeklyDayOfWeek;

            this.monthlyDayOfMonth =
                    monthlyDayOfMonth;

            this.updatedAtMillis =
                    updatedAtMillis;
        }

        @NonNull
        public BackupFrequency getFrequency() {
            return frequency;
        }

        public boolean isWifiOnly() {
            return wifiOnly;
        }

        public boolean isChargingOnly() {
            return chargingOnly;
        }

        public int getPreferredHour() {
            return preferredHour;
        }

        public int getPreferredMinute() {
            return preferredMinute;
        }

        public int getWeeklyDayOfWeek() {
            return weeklyDayOfWeek;
        }

        public int getMonthlyDayOfMonth() {
            return monthlyDayOfMonth;
        }

        public long getUpdatedAtMillis() {
            return updatedAtMillis;
        }

        public boolean isAutomaticEnabled() {
            return frequency.isAutomatic();
        }

        @NonNull
        public ScheduleSettings withFrequency(
                @NonNull BackupFrequency newFrequency
        ) {
            return new ScheduleSettings(
                    newFrequency,
                    wifiOnly,
                    chargingOnly,
                    preferredHour,
                    preferredMinute,
                    weeklyDayOfWeek,
                    monthlyDayOfMonth,
                    updatedAtMillis
            );
        }

        @NonNull
        public ScheduleSettings withWifiOnly(
                boolean newWifiOnly
        ) {
            return new ScheduleSettings(
                    frequency,
                    newWifiOnly,
                    chargingOnly,
                    preferredHour,
                    preferredMinute,
                    weeklyDayOfWeek,
                    monthlyDayOfMonth,
                    updatedAtMillis
            );
        }

        @NonNull
        public ScheduleSettings withChargingOnly(
                boolean newChargingOnly
        ) {
            return new ScheduleSettings(
                    frequency,
                    wifiOnly,
                    newChargingOnly,
                    preferredHour,
                    preferredMinute,
                    weeklyDayOfWeek,
                    monthlyDayOfMonth,
                    updatedAtMillis
            );
        }
    }

    /**
     * Immutable non-sensitive status used by the future backup screen.
     */
    public static final class BackupStatus {

        private final long lastAttemptAtMillis;
        private final long lastSuccessAtMillis;
        private final long lastFailureAtMillis;
        private final String lastFailureMessage;
        private final String lastBackupId;
        private final int lastRecordCount;
        private final long lastByteCount;
        private final long nextScheduledAtMillis;
        private final int consecutiveFailures;

        private BackupStatus(
                long lastAttemptAtMillis,
                long lastSuccessAtMillis,
                long lastFailureAtMillis,
                @NonNull String lastFailureMessage,
                @NonNull String lastBackupId,
                int lastRecordCount,
                long lastByteCount,
                long nextScheduledAtMillis,
                int consecutiveFailures
        ) {
            this.lastAttemptAtMillis =
                    Math.max(
                            0L,
                            lastAttemptAtMillis
                    );

            this.lastSuccessAtMillis =
                    Math.max(
                            0L,
                            lastSuccessAtMillis
                    );

            this.lastFailureAtMillis =
                    Math.max(
                            0L,
                            lastFailureAtMillis
                    );

            this.lastFailureMessage =
                    lastFailureMessage;

            this.lastBackupId =
                    lastBackupId;

            this.lastRecordCount =
                    Math.max(
                            0,
                            lastRecordCount
                    );

            this.lastByteCount =
                    Math.max(
                            0L,
                            lastByteCount
                    );

            this.nextScheduledAtMillis =
                    Math.max(
                            0L,
                            nextScheduledAtMillis
                    );

            this.consecutiveFailures =
                    Math.max(
                            0,
                            consecutiveFailures
                    );
        }

        public long getLastAttemptAtMillis() {
            return lastAttemptAtMillis;
        }

        public long getLastSuccessAtMillis() {
            return lastSuccessAtMillis;
        }

        public long getLastFailureAtMillis() {
            return lastFailureAtMillis;
        }

        @NonNull
        public String getLastFailureMessage() {
            return lastFailureMessage;
        }

        @NonNull
        public String getLastBackupId() {
            return lastBackupId;
        }

        public int getLastRecordCount() {
            return lastRecordCount;
        }

        public long getLastByteCount() {
            return lastByteCount;
        }

        public long getNextScheduledAtMillis() {
            return nextScheduledAtMillis;
        }

        public int getConsecutiveFailures() {
            return consecutiveFailures;
        }

        public boolean hasSuccessfulBackup() {
            return lastSuccessAtMillis > 0L;
        }

        public boolean hasFailureAfterLastSuccess() {
            return lastFailureAtMillis
                    > lastSuccessAtMillis;
        }
    }

    public static class BackupScheduleException
            extends Exception {

        public BackupScheduleException(
                @NonNull String message
        ) {
            super(
                    message
            );
        }

        public BackupScheduleException(
                @NonNull String message,
                @NonNull Throwable cause
        ) {
            super(
                    message,
                    cause
            );
        }
    }
}