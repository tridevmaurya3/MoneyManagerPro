package com.example.moneymanagerpro.utils;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class SmsImportStore {

    private static final String PREFS =
            "SmsTransactionImport";
    private static final String KEY_ENABLED =
            "sms_sync_enabled";
    private static final String KEY_AUTO_ADD =
            "sms_auto_add_enabled";
    private static final String KEY_PENDING =
            "pending_transactions";
    private static final String KEY_PROCESSED =
            "processed_fingerprints";
    private static final int MAX_PENDING = 100;
    private static final int MAX_PROCESSED = 500;

    public static final class PendingTransaction {

        public String fingerprint = "";
        public String sender = "";
        public long receivedAt;
        public double amount;
        public String type = "";
        public String bank = "";
        public String merchant = "";
        public String reference = "";
        public String category = "";
        public int confidence;

        public JSONObject toJson() {
            JSONObject object = new JSONObject();

            try {
                object.put("fingerprint", fingerprint);
                object.put("sender", sender);
                object.put("receivedAt", receivedAt);
                object.put("amount", amount);
                object.put("type", type);
                object.put("bank", bank);
                object.put("merchant", merchant);
                object.put("reference", reference);
                object.put("category", category);
                object.put("confidence", confidence);
            } catch (Exception ignored) {
            }

            return object;
        }

        public static PendingTransaction fromJson(
                JSONObject object
        ) {
            PendingTransaction pending =
                    new PendingTransaction();

            pending.fingerprint =
                    object.optString("fingerprint", "");
            pending.sender =
                    object.optString("sender", "");
            pending.receivedAt =
                    object.optLong("receivedAt", 0L);
            pending.amount =
                    object.optDouble("amount", 0);
            pending.type =
                    object.optString("type", "");
            pending.bank =
                    object.optString("bank", "");
            pending.merchant =
                    object.optString("merchant", "");
            pending.reference =
                    object.optString("reference", "");
            pending.category =
                    object.optString("category", "");
            pending.confidence =
                    object.optInt("confidence", 0);

            return pending;
        }
    }

    private SmsImportStore() {
    }

    public static boolean isEnabled(Context context) {
        return preferences(context).getBoolean(
                KEY_ENABLED,
                false
        );
    }

    public static void setEnabled(
            Context context,
            boolean enabled
    ) {
        preferences(context).edit()
                .putBoolean(KEY_ENABLED, enabled)
                .apply();
    }

    public static boolean isAutoAddEnabled(
            Context context
    ) {
        return preferences(context).getBoolean(
                KEY_AUTO_ADD,
                false
        );
    }

    public static void setAutoAddEnabled(
            Context context,
            boolean enabled
    ) {
        preferences(context).edit()
                .putBoolean(KEY_AUTO_ADD, enabled)
                .apply();
    }

    public static synchronized List<PendingTransaction>
    getPending(Context context) {
        List<PendingTransaction> result =
                new ArrayList<>();
        String json = preferences(context)
                .getString(KEY_PENDING, "[]");

        try {
            JSONArray array = new JSONArray(json);

            for (int index = 0;
                 index < array.length();
                 index++) {
                JSONObject object =
                        array.optJSONObject(index);

                if (object != null) {
                    result.add(
                            PendingTransaction
                                    .fromJson(object)
                    );
                }
            }
        } catch (Exception ignored) {
        }

        return result;
    }

    public static synchronized void addPending(
            Context context,
            PendingTransaction pending
    ) {
        List<PendingTransaction> values =
                getPending(context);

        for (PendingTransaction existing : values) {
            if (existing.fingerprint.equals(
                    pending.fingerprint
            )) {
                return;
            }
        }

        values.add(0, pending);

        while (values.size() > MAX_PENDING) {
            values.remove(values.size() - 1);
        }

        savePending(context, values);
    }

    public static synchronized void removePending(
            Context context,
            String fingerprint
    ) {
        List<PendingTransaction> values =
                getPending(context);

        values.removeIf(
                value -> value.fingerprint.equals(
                        fingerprint
                )
        );

        savePending(context, values);
    }

    public static boolean isProcessed(
            Context context,
            String fingerprint
    ) {
        Set<String> values =
                preferences(context)
                        .getStringSet(
                                KEY_PROCESSED,
                                new HashSet<>()
                        );

        return values != null
                && values.contains(fingerprint);
    }

    public static synchronized void markProcessed(
            Context context,
            String fingerprint
    ) {
        Set<String> stored =
                preferences(context)
                        .getStringSet(
                                KEY_PROCESSED,
                                new HashSet<>()
                        );
        Set<String> values =
                stored == null
                        ? new HashSet<>()
                        : new HashSet<>(stored);

        if (values.size() >= MAX_PROCESSED) {
            values.clear();
        }

        values.add(fingerprint);

        preferences(context).edit()
                .putStringSet(KEY_PROCESSED, values)
                .apply();
    }

    public static String fingerprint(
            String sender,
            long receivedAt,
            String message
    ) {
        String source =
                safe(sender).toLowerCase(Locale.US)
                        + "|"
                        + receivedAt
                        + "|"
                        + safe(message);

        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(
                    source.getBytes(
                            java.nio.charset
                                    .StandardCharsets.UTF_8
                    )
            );
            StringBuilder builder =
                    new StringBuilder();

            for (byte value : hash) {
                builder.append(
                        String.format(
                                Locale.US,
                                "%02x",
                                value
                        )
                );
            }

            return builder.toString();
        } catch (Exception exception) {
            return Integer.toHexString(
                    source.hashCode()
            );
        }
    }

    private static void savePending(
            Context context,
            List<PendingTransaction> values
    ) {
        JSONArray array = new JSONArray();

        for (PendingTransaction value : values) {
            array.put(value.toJson());
        }

        preferences(context).edit()
                .putString(
                        KEY_PENDING,
                        array.toString()
                )
                .commit();
    }

    private static SharedPreferences preferences(
            Context context
    ) {
        return context.getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
        );
    }

    private static String safe(String value) {
        return value == null
                ? ""
                : value.trim();
    }
}
