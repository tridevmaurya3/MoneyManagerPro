package com.example.moneymanagerpro.notification;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

/**
 * Reads recent SMS messages only after the user grants READ_SMS.
 * Only completed financial/transactional messages are copied to the local app inbox.
 */
public final class DirectSmsAccessManager {

    private static final Uri SMS_INBOX_URI = Uri.parse("content://sms/inbox");
    private static final int MAX_SMS_TO_SCAN = 750;

    private DirectSmsAccessManager() {
    }

    public static boolean hasReadPermission(@NonNull Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean hasReceivePermission(@NonNull Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean hasAllPermissions(@NonNull Context context) {
        return hasReadPermission(context) && hasReceivePermission(context);
    }

    /**
     * Imports up to the newest 750 inbox rows. Duplicate protection is handled by SmsAlertStore.
     *
     * @return number of newly added transactional messages.
     */
    public static int importRecentFinancialSms(@NonNull Context context) {
        if (!hasReadPermission(context)) return 0;

        String[] projection = new String[]{
                "address",
                "body",
                "date"
        };

        int imported = 0;
        int scanned = 0;

        try (Cursor cursor = context.getContentResolver().query(
                SMS_INBOX_URI,
                projection,
                null,
                null,
                "date DESC"
        )) {
            if (cursor == null) return 0;

            int addressIndex = cursor.getColumnIndex("address");
            int bodyIndex = cursor.getColumnIndex("body");
            int dateIndex = cursor.getColumnIndex("date");

            while (cursor.moveToNext() && scanned < MAX_SMS_TO_SCAN) {
                scanned++;

                String sender = addressIndex >= 0 ? cursor.getString(addressIndex) : "SMS";
                String body = bodyIndex >= 0 ? cursor.getString(bodyIndex) : "";
                long date = dateIndex >= 0 ? cursor.getLong(dateIndex) : System.currentTimeMillis();

                if (sender == null) sender = "SMS";
                if (body == null || body.trim().isEmpty()) continue;
                if (!SmsFinancialClassifier.isFinancialMessage(sender, body)) continue;

                boolean added = SmsAlertStore.add(
                        context,
                        "direct-sms",
                        sender,
                        body,
                        date
                );
                if (added) imported++;
            }
        } catch (SecurityException ignored) {
            return 0;
        } catch (Exception ignored) {
            return imported;
        }

        return imported;
    }
}
