package com.example.moneymanagerpro.notification;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;

import androidx.annotation.NonNull;

import java.util.LinkedHashMap;
import java.util.Map;

/** Receives new SMS only after RECEIVE_SMS is granted. */
public class FinancialSmsReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;
        if (!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) return;
        if (!DirectSmsAccessManager.hasReceivePermission(context)) return;

        SmsMessage[] messages = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        if (messages == null || messages.length == 0) return;

        Map<String, StringBuilder> groupedBodies = new LinkedHashMap<>();
        Map<String, Long> groupedTimes = new LinkedHashMap<>();

        for (SmsMessage message : messages) {
            if (message == null) continue;

            String sender = cleanSender(message.getDisplayOriginatingAddress());
            String body = message.getDisplayMessageBody();
            if (body == null || body.trim().isEmpty()) continue;

            StringBuilder builder = groupedBodies.get(sender);
            if (builder == null) {
                builder = new StringBuilder();
                groupedBodies.put(sender, builder);
            }
            builder.append(body);
            groupedTimes.put(sender, message.getTimestampMillis());
        }

        for (Map.Entry<String, StringBuilder> entry : groupedBodies.entrySet()) {
            String sender = entry.getKey();
            String body = entry.getValue().toString().trim();
            if (!SmsFinancialClassifier.isFinancialMessage(sender, body)) continue;

            long receivedAt = groupedTimes.containsKey(sender)
                    ? groupedTimes.get(sender)
                    : System.currentTimeMillis();

            SmsAlertStore.add(
                    context,
                    "direct-sms",
                    sender,
                    body,
                    receivedAt
            );
        }
    }

    @NonNull
    private String cleanSender(String sender) {
        if (sender == null || sender.trim().isEmpty()) return "SMS";
        return sender.trim();
    }
}
