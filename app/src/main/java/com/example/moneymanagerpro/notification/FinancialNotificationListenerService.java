package com.example.moneymanagerpro.notification;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import androidx.annotation.Nullable;

/**
 * Reads posted notification title/text only after the user explicitly grants
 * Notification Access. No SMS permission is used and nothing is uploaded.
 */
public class FinancialNotificationListenerService extends NotificationListenerService {

    @Override
    public void onNotificationPosted(StatusBarNotification statusBarNotification) {
        if (statusBarNotification == null
                || statusBarNotification.getNotification() == null
                || statusBarNotification.isOngoing()) {
            return;
        }

        if (getPackageName().equals(statusBarNotification.getPackageName())) return;
        if (!FinancialNotificationStore.isCaptureEnabled(this)) return;

        Notification notification = statusBarNotification.getNotification();
        Bundle extras = notification.extras;

        String title = firstNonEmpty(
                valueOf(extras.getCharSequence(Notification.EXTRA_TITLE_BIG)),
                valueOf(extras.getCharSequence(Notification.EXTRA_TITLE)),
                valueOf(extras.getCharSequence(Notification.EXTRA_SUB_TEXT))
        );

        String body = firstNonEmpty(
                valueOf(extras.getCharSequence(Notification.EXTRA_BIG_TEXT)),
                joinLines(extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)),
                valueOf(extras.getCharSequence(Notification.EXTRA_TEXT)),
                valueOf(extras.getCharSequence(Notification.EXTRA_INFO_TEXT))
        );

        if (body.isEmpty()) return;

        FinancialNotificationParser.ParsedNotification parsed =
                FinancialNotificationParser.parse(
                        statusBarNotification.getPackageName(),
                        title,
                        body,
                        statusBarNotification.getPostTime()
                );

        if (parsed != null) {
            FinancialNotificationStore.add(this, parsed);
        }
    }

    private String joinLines(@Nullable CharSequence[] lines) {
        if (lines == null || lines.length == 0) return "";
        StringBuilder builder = new StringBuilder();
        for (CharSequence line : lines) {
            String value = valueOf(line);
            if (value.isEmpty()) continue;
            if (builder.length() > 0) builder.append(' ');
            builder.append(value);
        }
        return builder.toString().trim();
    }

    private String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return "";
    }

    private String valueOf(@Nullable CharSequence value) {
        return value == null ? "" : value.toString().replace('\n', ' ').trim();
    }
}
