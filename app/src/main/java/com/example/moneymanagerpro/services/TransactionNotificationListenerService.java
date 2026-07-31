package com.example.moneymanagerpro.services;

import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.moneymanagerpro.utils.BankTransactionParser;
import com.example.moneymanagerpro.utils.NotificationReaderPreferences;
import com.example.moneymanagerpro.utils.PendingTransactionStore;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * Reads new notifications from user-approved payment and banking apps.
 *
 * Privacy and safety rules:
 *
 * 1. The reader is disabled by default.
 * 2. Notification content is accessed only after the source package
 *    passes the user-controlled allowlist check.
 * 3. Existing notifications are ignored when the listener reconnects.
 * 4. OTP, failed and non-financial messages are rejected by
 *    BankTransactionParser.
 * 5. Raw notification text is never saved.
 * 6. Parsed transactions are stored only as pending suggestions.
 * 7. Income or expense records are never created automatically.
 * 8. No notification data is uploaded to Firebase or any server.
 */
public class TransactionNotificationListenerService
        extends NotificationListenerService {

    private static final String TAG =
            "TransactionListener";

    /**
     * Internal broadcast sent after a new pending suggestion is stored.
     *
     * The package is explicitly restricted to Money Manager Pro,
     * so another application cannot receive it.
     */
    public static final String ACTION_PENDING_TRANSACTION_DETECTED =
            "com.example.moneymanagerpro.action."
                    + "PENDING_TRANSACTION_DETECTED";

    public static final String EXTRA_PENDING_COUNT =
            "pending_count";

    public static final String EXTRA_SOURCE_NAME =
            "source_name";

    /**
     * Existing notifications may briefly appear when the listener
     * reconnects. A notification posted before the current listener
     * session is ignored.
     *
     * A small tolerance prevents a genuine notification posted almost
     * simultaneously with the connection event from being rejected.
     */
    private static final long CONNECTION_TIME_TOLERANCE_MILLIS =
            3_000L;

    /**
     * Prevents an unexpectedly large notification field from being
     * processed or retained in memory.
     */
    private static final int MAX_SINGLE_TEXT_LENGTH =
            500;

    private static final int MAX_COMBINED_TEXT_LENGTH =
            1_500;

    private ExecutorService processingExecutor;

    private volatile long listenerConnectedAt;

    private volatile boolean serviceDestroyed;

    @Override
    public void onCreate() {
        super.onCreate();

        processingExecutor =
                Executors.newSingleThreadExecutor();

        serviceDestroyed = false;
        listenerConnectedAt = 0L;
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();

        listenerConnectedAt =
                System.currentTimeMillis();

        NotificationReaderPreferences
                .setLastAccessCheckTime(
                        getApplicationContext(),
                        listenerConnectedAt
                );

        Log.d(
                TAG,
                "Notification listener connected"
        );
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();

        listenerConnectedAt = 0L;

        NotificationReaderPreferences
                .setLastAccessCheckTime(
                        getApplicationContext(),
                        System.currentTimeMillis()
                );

        Log.d(
                TAG,
                "Notification listener disconnected"
        );

        /*
         * Android may temporarily disconnect a listener.
         * Requesting a rebind allows the system to reconnect it when
         * notification access is still granted.
         */
        try {
            requestRebind(
                    new ComponentName(
                            this,
                            TransactionNotificationListenerService.class
                    )
            );

        } catch (Exception exception) {
            Log.w(
                    TAG,
                    "Unable to request listener rebind",
                    exception
            );
        }
    }

    @Override
    public void onNotificationPosted(
            @Nullable StatusBarNotification statusBarNotification
    ) {
        if (statusBarNotification == null
                || serviceDestroyed) {

            return;
        }

        String sourcePackage =
                safe(
                        statusBarNotification.getPackageName()
                );

        if (sourcePackage.isEmpty()) {
            return;
        }

        /*
         * Never process Money Manager Pro's own notifications.
         *
         * This also prevents a detection notification from causing
         * a feedback loop.
         */
        if (sourcePackage.equalsIgnoreCase(
                getPackageName()
        )) {
            return;
        }

        /*
         * Important privacy gate:
         *
         * Notification content is not accessed until the package has
         * passed all three checks:
         *
         * - Reader enabled
         * - Disclosure accepted
         * - Source package selected by the user
         */
        if (!NotificationReaderPreferences
                .shouldProcessPackage(
                        getApplicationContext(),
                        sourcePackage
                )) {

            return;
        }

        Notification notification =
                statusBarNotification.getNotification();

        if (notification == null) {
            return;
        }

        /*
         * Group summary notifications often repeat information already
         * present in child notifications.
         */
        if ((notification.flags
                & Notification.FLAG_GROUP_SUMMARY) != 0) {

            return;
        }

        long postedAt =
                statusBarNotification.getPostTime();

        if (postedAt <= 0L) {
            postedAt =
                    System.currentTimeMillis();
        }

        /*
         * Ignore active notifications that existed before the listener's
         * current connection. This keeps the feature focused on new
         * notifications instead of historical notification scanning.
         */
        long connectedAt =
                listenerConnectedAt;

        if (connectedAt > 0L
                && postedAt
                < connectedAt
                - CONNECTION_TIME_TOLERANCE_MILLIS) {

            return;
        }

        NotificationContent notificationContent =
                extractNotificationContent(
                        notification
                );

        if (notificationContent.isEmpty()) {
            return;
        }

        submitForProcessing(
                sourcePackage,
                notificationContent.title,
                notificationContent.body,
                postedAt
        );
    }

    @Override
    public void onNotificationRemoved(
            @Nullable StatusBarNotification statusBarNotification
    ) {
        /*
         * Nothing is deleted from pending suggestions when the original
         * notification is dismissed.
         *
         * The user will explicitly confirm or reject the suggestion from
         * Money Manager Pro's review screen.
         */
    }

    @Override
    public void onDestroy() {
        serviceDestroyed = true;
        listenerConnectedAt = 0L;

        if (processingExecutor != null) {
            processingExecutor.shutdownNow();
            processingExecutor = null;
        }

        super.onDestroy();
    }

    /**
     * Moves parsing and local storage away from NotificationListenerService's
     * callback thread.
     */
    private void submitForProcessing(
            @NonNull String sourcePackage,
            @NonNull String title,
            @NonNull String body,
            long postedAt
    ) {
        ExecutorService executor =
                processingExecutor;

        if (executor == null
                || executor.isShutdown()
                || executor.isTerminated()) {

            return;
        }

        Context applicationContext =
                getApplicationContext();

        try {
            executor.execute(
                    new Runnable() {
                        @Override
                        public void run() {
                            processNotification(
                                    applicationContext,
                                    sourcePackage,
                                    title,
                                    body,
                                    postedAt
                            );
                        }
                    }
            );

        } catch (RejectedExecutionException exception) {
            Log.w(
                    TAG,
                    "Notification processing task was rejected",
                    exception
            );
        }
    }

    /**
     * Parses one approved notification and stores only its structured
     * transaction fields.
     */
    private void processNotification(
            @NonNull Context context,
            @NonNull String sourcePackage,
            @NonNull String title,
            @NonNull String body,
            long postedAt
    ) {
        if (serviceDestroyed) {
            return;
        }

        /*
         * Check the setting again because the user may have disabled the
         * reader after the notification callback but before this background
         * task started.
         */
        if (!NotificationReaderPreferences
                .shouldProcessPackage(
                        context,
                        sourcePackage
                )) {

            return;
        }

        BankTransactionParser.Result parserResult =
                BankTransactionParser
                        .parseNotification(
                                sourcePackage,
                                title,
                                body,
                                postedAt
                        );

        if (parserResult == null
                || !parserResult.isValid()) {

            return;
        }

        PendingTransactionStore.SaveResult saveResult =
                PendingTransactionStore.save(
                        context,
                        parserResult
                );

        if (saveResult
                == PendingTransactionStore.SaveResult.SAVED) {

            NotificationReaderPreferences
                    .recordSuccessfulDetection(
                            context,
                            parserResult.getSourceName(),
                            parserResult.getDetectedAt()
                    );

            sendPendingTransactionBroadcast(
                    context,
                    parserResult.getSourceName()
            );

            Log.d(
                    TAG,
                    "New pending transaction suggestion stored"
            );

        } else if (saveResult
                == PendingTransactionStore.SaveResult.DUPLICATE) {

            Log.d(
                    TAG,
                    "Duplicate transaction suggestion ignored"
            );

        } else if (saveResult
                == PendingTransactionStore.SaveResult.STORAGE_ERROR) {

            Log.w(
                    TAG,
                    "Unable to store pending transaction suggestion"
            );
        }
    }

    /**
     * Sends only non-sensitive status information to Money Manager Pro.
     *
     * Amount, account number, merchant, UTR and raw notification text
     * are deliberately not included in this broadcast.
     */
    private void sendPendingTransactionBroadcast(
            @NonNull Context context,
            @Nullable String sourceName
    ) {
        Intent intent =
                new Intent(
                        ACTION_PENDING_TRANSACTION_DETECTED
                );

        intent.setPackage(
                context.getPackageName()
        );

        intent.putExtra(
                EXTRA_PENDING_COUNT,
                PendingTransactionStore.getCount(
                        context
                )
        );

        intent.putExtra(
                EXTRA_SOURCE_NAME,
                limitLength(
                        sourceName,
                        60
                )
        );

        context.sendBroadcast(intent);
    }

    /**
     * Extracts standard visible notification fields.
     *
     * Messaging history, attachments, remote views and other heavyweight
     * notification data are not accessed.
     */
    @NonNull
    private NotificationContent extractNotificationContent(
            @NonNull Notification notification
    ) {
        Bundle extras =
                notification.extras;

        Set<String> titleParts =
                new LinkedHashSet<>();

        Set<String> bodyParts =
                new LinkedHashSet<>();

        if (extras != null) {
            addPart(
                    titleParts,
                    getCharSequence(
                            extras,
                            Notification.EXTRA_TITLE_BIG
                    )
            );

            addPart(
                    titleParts,
                    getCharSequence(
                            extras,
                            Notification.EXTRA_TITLE
                    )
            );

            addPart(
                    bodyParts,
                    getCharSequence(
                            extras,
                            Notification.EXTRA_BIG_TEXT
                    )
            );

            addPart(
                    bodyParts,
                    getCharSequence(
                            extras,
                            Notification.EXTRA_TEXT
                    )
            );

            addPart(
                    bodyParts,
                    getCharSequence(
                            extras,
                            Notification.EXTRA_SUB_TEXT
                    )
            );

            addPart(
                    bodyParts,
                    getCharSequence(
                            extras,
                            Notification.EXTRA_INFO_TEXT
                    )
            );

            addPart(
                    bodyParts,
                    getCharSequence(
                            extras,
                            Notification.EXTRA_SUMMARY_TEXT
                    )
            );

            addTextLines(
                    bodyParts,
                    extras
            );
        }

        /*
         * Ticker text is used only as a fallback because many modern
         * notifications no longer provide it.
         */
        if (bodyParts.isEmpty()) {
            addPart(
                    bodyParts,
                    notification.tickerText
            );
        }

        String title =
                joinParts(
                        titleParts,
                        MAX_COMBINED_TEXT_LENGTH
                );

        String body =
                joinParts(
                        bodyParts,
                        MAX_COMBINED_TEXT_LENGTH
                );

        /*
         * Some payment apps put the full message in the title and leave
         * the normal text field empty. The parser can still inspect it.
         */
        return new NotificationContent(
                title,
                body
        );
    }

    private void addTextLines(
            @NonNull Set<String> destination,
            @NonNull Bundle extras
    ) {
        try {
            CharSequence[] textLines =
                    extras.getCharSequenceArray(
                            Notification.EXTRA_TEXT_LINES
                    );

            if (textLines == null) {
                return;
            }

            for (CharSequence textLine
                    : textLines) {

                addPart(
                        destination,
                        textLine
                );
            }

        } catch (Exception ignored) {
            /*
             * A malformed app notification must not crash the listener.
             */
        }
    }

    @Nullable
    private CharSequence getCharSequence(
            @NonNull Bundle extras,
            @NonNull String key
    ) {
        try {
            return extras.getCharSequence(key);

        } catch (Exception ignored) {
            return null;
        }
    }

    private void addPart(
            @NonNull Set<String> destination,
            @Nullable CharSequence value
    ) {
        String cleanValue =
                limitLength(
                        value == null
                                ? ""
                                : value.toString(),
                        MAX_SINGLE_TEXT_LENGTH
                );

        if (!cleanValue.isEmpty()) {
            destination.add(
                    cleanValue
            );
        }
    }

    @NonNull
    private String joinParts(
            @NonNull Set<String> parts,
            int maximumLength
    ) {
        StringBuilder builder =
                new StringBuilder();

        for (String part : parts) {
            String cleanPart =
                    limitLength(
                            part,
                            MAX_SINGLE_TEXT_LENGTH
                    );

            if (cleanPart.isEmpty()) {
                continue;
            }

            if (builder.length() > 0) {
                builder.append(" | ");
            }

            int remainingLength =
                    maximumLength
                            - builder.length();

            if (remainingLength <= 0) {
                break;
            }

            if (cleanPart.length()
                    > remainingLength) {

                builder.append(
                        cleanPart,
                        0,
                        remainingLength
                );

                break;
            }

            builder.append(cleanPart);
        }

        return builder
                .toString()
                .trim();
    }

    @NonNull
    private static String safe(
            @Nullable String value
    ) {
        return value == null
                ? ""
                : value.trim();
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

    /**
     * Temporary in-memory notification content.
     *
     * This object is never written to SharedPreferences, Room, Firebase,
     * files or logs.
     */
    private static final class NotificationContent {

        private final String title;

        private final String body;

        private NotificationContent(
                @NonNull String title,
                @NonNull String body
        ) {
            this.title = title;
            this.body = body;
        }

        private boolean isEmpty() {
            return title.isEmpty()
                    && body.isEmpty();
        }
    }
}