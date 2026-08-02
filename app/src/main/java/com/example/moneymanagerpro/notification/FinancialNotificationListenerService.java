package com.example.moneymanagerpro.notification;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import androidx.annotation.Nullable;

/**
 * Reads only posted notification title/text after the user explicitly grants
 * Notification Access in Android settings. No SMS permission is used.
 */
public class FinancialNotificationListenerService extends NotificationListenerService {

    @Override
    public void onNotificationPosted(StatusBarNotification statusBarNotification) {
        if (statusBarNotification == null || statusBarNotification.getNotification() == null) return;
        if (getPackageName().equals(statusBarNotification.getPackageName())) return;

        Notification notification = statusBarNotification.getNotification();
        Bundle extras = notification.extras;
        String title = valueOf(extras.getCharSequence(Notification.EXTRA_TITLE));
        String body = valueOf(extras.getCharSequence(Notification.EXTRA_BIG_TEXT));
        if (body.isEmpty()) body = valueOf(extras.getCharSequence(Notification.EXTRA_TEXT));
        if (body.isEmpty()) return;

        FinancialNotificationParser.ParsedNotification parsed = FinancialNotificationParser.parse(
                statusBarNotification.getPackageName(),
                title,
                body,
                statusBarNotification.getPostTime()
        );
        if (parsed != null) FinancialNotificationStore.add(this, parsed);
    }

    private String valueOf(@Nullable CharSequence value) {
        return value == null ? "" : value.toString().trim();
    }
}
