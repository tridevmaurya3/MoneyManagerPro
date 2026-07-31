package com.example.moneymanagerpro.utils;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Stores privacy-related preferences for the Notification Transaction Reader.
 *
 * Important security rules:
 *
 * 1. Notification transaction detection is disabled by default.
 * 2. Notification access and the in-app reader switch are separate controls.
 * 3. Only explicitly selected application packages are processed.
 * 4. The selected application list remains stored locally on the device.
 * 5. No notification content is stored by this class.
 * 6. No information is uploaded to Firebase or any external server.
 */
public final class NotificationReaderPreferences {

    private static final String PREFERENCES_NAME =
            "notification_transaction_reader_preferences";

    private static final String KEY_READER_ENABLED =
            "reader_enabled";

    private static final String KEY_ALLOWED_PACKAGES =
            "allowed_packages";

    private static final String KEY_DISCLOSURE_ACCEPTED =
            "disclosure_accepted";

    private static final String KEY_LAST_ACCESS_CHECK_TIME =
            "last_access_check_time";

    private static final String KEY_LAST_DETECTED_TIME =
            "last_detected_time";

    private static final String KEY_LAST_DETECTED_SOURCE =
            "last_detected_source";

    /**
     * Prevents an unexpectedly large package list from being saved.
     */
    private static final int MAX_ALLOWED_PACKAGES = 100;

    private NotificationReaderPreferences() {
        // Utility class.
    }

    /**
     * Returns whether the user enabled transaction notification detection
     * inside Money Manager Pro.
     *
     * This does not confirm that Android Notification Access is granted.
     * Notification Access must be checked separately.
     */
    public static boolean isReaderEnabled(
            @NonNull Context context
    ) {
        return getPreferences(context)
                .getBoolean(
                        KEY_READER_ENABLED,
                        false
                );
    }

    /**
     * Enables or disables notification transaction detection inside the app.
     *
     * This method cannot grant or revoke Android Notification Access.
     */
    public static void setReaderEnabled(
            @NonNull Context context,
            boolean enabled
    ) {
        getPreferences(context)
                .edit()
                .putBoolean(
                        KEY_READER_ENABLED,
                        enabled
                )
                .apply();
    }

    /**
     * Returns true only when:
     *
     * 1. Reader is enabled.
     * 2. Privacy disclosure was accepted.
     * 3. The notification source application was selected by the user.
     */
    public static boolean shouldProcessPackage(
            @NonNull Context context,
            @Nullable String packageName
    ) {
        if (!isReaderEnabled(context)) {
            return false;
        }

        if (!isDisclosureAccepted(context)) {
            return false;
        }

        return isPackageAllowed(
                context,
                packageName
        );
    }

    /**
     * Checks whether one package is present in the user-approved list.
     */
    public static boolean isPackageAllowed(
            @NonNull Context context,
            @Nullable String packageName
    ) {
        String normalizedPackage =
                normalizePackageName(
                        packageName
                );

        if (normalizedPackage.isEmpty()) {
            return false;
        }

        Set<String> allowedPackages =
                getAllowedPackages(context);

        return allowedPackages.contains(
                normalizedPackage
        );
    }

    /**
     * Adds or removes one application package from the approved list.
     */
    public static boolean setPackageAllowed(
            @NonNull Context context,
            @Nullable String packageName,
            boolean allowed
    ) {
        String normalizedPackage =
                normalizePackageName(
                        packageName
                );

        if (normalizedPackage.isEmpty()) {
            return false;
        }

        Set<String> currentPackages =
                new HashSet<>(
                        getAllowedPackages(context)
                );

        boolean changed;

        if (allowed) {
            if (currentPackages.size()
                    >= MAX_ALLOWED_PACKAGES
                    && !currentPackages.contains(
                    normalizedPackage
            )) {
                return false;
            }

            changed =
                    currentPackages.add(
                            normalizedPackage
                    );

        } else {
            changed =
                    currentPackages.remove(
                            normalizedPackage
                    );
        }

        if (!changed) {
            return true;
        }

        return getPreferences(context)
                .edit()
                .putStringSet(
                        KEY_ALLOWED_PACKAGES,
                        new HashSet<>(
                                currentPackages
                        )
                )
                .commit();
    }

    /**
     * Replaces the complete approved package list.
     *
     * Invalid and duplicate package names are ignored.
     */
    public static boolean replaceAllowedPackages(
            @NonNull Context context,
            @Nullable Set<String> packageNames
    ) {
        Set<String> cleanPackages =
                new HashSet<>();

        if (packageNames != null) {
            for (String packageName : packageNames) {
                String normalizedPackage =
                        normalizePackageName(
                                packageName
                        );

                if (normalizedPackage.isEmpty()) {
                    continue;
                }

                cleanPackages.add(
                        normalizedPackage
                );

                if (cleanPackages.size()
                        >= MAX_ALLOWED_PACKAGES) {
                    break;
                }
            }
        }

        return getPreferences(context)
                .edit()
                .putStringSet(
                        KEY_ALLOWED_PACKAGES,
                        cleanPackages
                )
                .commit();
    }

    /**
     * Returns a safe copy of the approved package list.
     */
    @NonNull
    public static Set<String> getAllowedPackages(
            @NonNull Context context
    ) {
        Set<String> savedPackages =
                getPreferences(context)
                        .getStringSet(
                                KEY_ALLOWED_PACKAGES,
                                Collections.emptySet()
                        );

        if (savedPackages == null
                || savedPackages.isEmpty()) {
            return Collections.emptySet();
        }

        Set<String> cleanPackages =
                new HashSet<>();

        for (String packageName : savedPackages) {
            String normalizedPackage =
                    normalizePackageName(
                            packageName
                    );

            if (!normalizedPackage.isEmpty()) {
                cleanPackages.add(
                        normalizedPackage
                );
            }
        }

        return Collections.unmodifiableSet(
                cleanPackages
        );
    }

    /**
     * Removes every application from the approved source list.
     */
    public static boolean clearAllowedPackages(
            @NonNull Context context
    ) {
        return getPreferences(context)
                .edit()
                .remove(
                        KEY_ALLOWED_PACKAGES
                )
                .commit();
    }

    /**
     * Returns the number of applications approved by the user.
     */
    public static int getAllowedPackageCount(
            @NonNull Context context
    ) {
        return getAllowedPackages(context)
                .size();
    }

    /**
     * Records whether the user accepted the privacy disclosure.
     *
     * The disclosure screen will explain:
     *
     * - which notifications are processed,
     * - that processing happens locally,
     * - that transactions are not automatically saved,
     * - and that access can be disabled at any time.
     */
    public static void setDisclosureAccepted(
            @NonNull Context context,
            boolean accepted
    ) {
        SharedPreferences.Editor editor =
                getPreferences(context)
                        .edit()
                        .putBoolean(
                                KEY_DISCLOSURE_ACCEPTED,
                                accepted
                        );

        if (!accepted) {
            /*
             * Reader must immediately stop when consent is withdrawn.
             */
            editor.putBoolean(
                    KEY_READER_ENABLED,
                    false
            );
        }

        editor.apply();
    }

    public static boolean isDisclosureAccepted(
            @NonNull Context context
    ) {
        return getPreferences(context)
                .getBoolean(
                        KEY_DISCLOSURE_ACCEPTED,
                        false
                );
    }

    /**
     * Saves the last time the app checked whether Android Notification
     * Access was available.
     */
    public static void setLastAccessCheckTime(
            @NonNull Context context,
            long timestamp
    ) {
        getPreferences(context)
                .edit()
                .putLong(
                        KEY_LAST_ACCESS_CHECK_TIME,
                        Math.max(
                                timestamp,
                                0L
                        )
                )
                .apply();
    }

    public static long getLastAccessCheckTime(
            @NonNull Context context
    ) {
        return getPreferences(context)
                .getLong(
                        KEY_LAST_ACCESS_CHECK_TIME,
                        0L
                );
    }

    /**
     * Stores only basic reader status information after a valid parsed
     * transaction is detected.
     *
     * Notification title, message text, amount and account details are
     * deliberately not stored here.
     */
    public static void recordSuccessfulDetection(
            @NonNull Context context,
            @Nullable String sourceName,
            long detectedAt
    ) {
        String safeSourceName =
                limitLength(
                        sourceName,
                        60
                );

        getPreferences(context)
                .edit()
                .putLong(
                        KEY_LAST_DETECTED_TIME,
                        detectedAt > 0
                                ? detectedAt
                                : System.currentTimeMillis()
                )
                .putString(
                        KEY_LAST_DETECTED_SOURCE,
                        safeSourceName
                )
                .apply();
    }

    public static long getLastDetectedTime(
            @NonNull Context context
    ) {
        return getPreferences(context)
                .getLong(
                        KEY_LAST_DETECTED_TIME,
                        0L
                );
    }

    @NonNull
    public static String getLastDetectedSource(
            @NonNull Context context
    ) {
        String source =
                getPreferences(context)
                        .getString(
                                KEY_LAST_DETECTED_SOURCE,
                                ""
                        );

        return source == null
                ? ""
                : source.trim();
    }

    /**
     * Disables the reader and clears all approved apps and consent data.
     *
     * This method does not delete pending transaction suggestions.
     * PendingTransactionStore.clear(context) can be used separately.
     */
    public static boolean resetReaderSettings(
            @NonNull Context context
    ) {
        return getPreferences(context)
                .edit()
                .clear()
                .commit();
    }

    @NonNull
    private static SharedPreferences getPreferences(
            @NonNull Context context
    ) {
        return context
                .getApplicationContext()
                .getSharedPreferences(
                        PREFERENCES_NAME,
                        Context.MODE_PRIVATE
                );
    }

    /**
     * Normalizes a package name before storing or comparing it.
     */
    @NonNull
    private static String normalizePackageName(
            @Nullable String packageName
    ) {
        String normalized =
                packageName == null
                        ? ""
                        : packageName
                        .trim()
                        .toLowerCase(
                                Locale.ROOT
                        );

        if (normalized.isEmpty()) {
            return "";
        }

        /*
         * Normal Android package names contain letters, numbers,
         * underscores and dots.
         */
        if (!normalized.matches(
                "[a-z0-9_]+(?:\\.[a-z0-9_]+)+"
        )) {
            return "";
        }

        if (normalized.length() > 180) {
            return "";
        }

        return normalized;
    }

    @NonNull
    private static String limitLength(
            @Nullable String value,
            int maximumLength
    ) {
        String cleanValue =
                value == null
                        ? ""
                        : value
                        .replace('\n', ' ')
                        .replace('\r', ' ')
                        .replaceAll(
                                "\\s+",
                                " "
                        )
                        .trim();

        if (cleanValue.length()
                <= maximumLength) {
            return cleanValue;
        }

        return cleanValue
                .substring(
                        0,
                        maximumLength
                )
                .trim();
    }
}