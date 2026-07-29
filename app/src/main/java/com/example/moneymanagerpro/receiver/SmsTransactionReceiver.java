package com.example.moneymanagerpro.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Telephony;
import android.telephony.SmsMessage;

import com.example.moneymanagerpro.utils.SmsTransactionProcessor;

public class SmsTransactionReceiver
        extends BroadcastReceiver {

    @Override
    public void onReceive(
            Context context,
            Intent intent
    ) {
        if (intent == null
                || !Telephony.Sms.Intents
                .SMS_RECEIVED_ACTION.equals(
                        intent.getAction()
                )) {
            return;
        }

        Bundle extras = intent.getExtras();

        if (extras == null) {
            return;
        }

        SmsMessage[] messages =
                Telephony.Sms.Intents
                        .getMessagesFromIntent(intent);

        if (messages == null
                || messages.length == 0) {
            return;
        }

        StringBuilder body = new StringBuilder();
        String sender = "";
        long timestamp =
                System.currentTimeMillis();

        for (SmsMessage message : messages) {
            if (message == null) {
                continue;
            }

            if (sender.isEmpty()) {
                String address = message
                        .getDisplayOriginatingAddress();
                sender = address == null
                        ? ""
                        : address;
                timestamp =
                        message.getTimestampMillis();
            }

            body.append(
                    message.getDisplayMessageBody()
            );
        }

        SmsTransactionProcessor.processAsync(
                context,
                sender,
                body.toString(),
                timestamp
        );
    }
}
