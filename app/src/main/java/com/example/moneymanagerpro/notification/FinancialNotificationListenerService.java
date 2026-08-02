package com.example.moneymanagerpro.notification;

import android.app.Notification;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/**
 * Reads notification content only after the user explicitly grants
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

        String sourcePackage = statusBarNotification.getPackageName();
        if (getPackageName().equals(sourcePackage)) return;

        Notification notification = statusBarNotification.getNotification();
        NotificationTextExtractor.Result extracted =
                NotificationTextExtractor.extract(notification);

        boolean smsApp = looksLikeSmsApp(sourcePackage);

        if (extracted.redacted) {
            if (smsApp) {
                SmsAlertStore.recordRedacted(
                        this,
                        sourcePackage,
                        statusBarNotification.getPostTime()
                );
            }
            return;
        }

        if (extracted.body.isEmpty()) return;

        if (smsApp) {
            SmsAlertStore.add(
                    this,
                    sourcePackage,
                    extracted.title,
                    extracted.body,
                    statusBarNotification.getPostTime()
            );
        }

        if (!FinancialNotificationStore.isCaptureEnabled(this)) return;

        FinancialNotificationParser.ParsedNotification parsed =
                FinancialNotificationParser.parse(
                        sourcePackage,
                        extracted.title,
                        extracted.body,
                        statusBarNotification.getPostTime()
                );

        if (parsed != null) {
            FinancialNotificationStore.add(this, parsed);
        }
    }

    private boolean looksLikeSmsApp(String packageName) {
        String value = packageName == null ? "" : packageName.toLowerCase();
        return value.contains("messaging")
                || value.contains("message")
                || value.contains("mms")
                || value.contains("sms")
                || value.equals("com.google.android.apps.messaging")
                || value.equals("com.samsung.android.messaging");
    }
}
