package com.example.moneymanagerpro.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class ReceiptStore {

    private static final String PREFS_NAME = "expense_receipts";
    private static final String KEY_PREFIX = "receipt_uri_";

    private ReceiptStore() {
    }

    public static void saveReceiptUri(
            Context context,
            long transactionId,
            String receiptUri
    ) {
        SharedPreferences preferences = context.getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
        );

        preferences.edit()
                .putString(KEY_PREFIX + transactionId, receiptUri)
                .apply();
    }

    public static String getReceiptUri(Context context, long transactionId) {
        SharedPreferences preferences = context.getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
        );

        return preferences.getString(KEY_PREFIX + transactionId, "");
    }

    public static void removeReceiptUri(Context context, long transactionId) {
        SharedPreferences preferences = context.getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
        );

        preferences.edit()
                .remove(KEY_PREFIX + transactionId)
                .apply();
    }
}