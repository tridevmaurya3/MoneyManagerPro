package com.example.moneymanagerpro.notification;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Stores SMS-app notification previews locally. No SMS permission is used. */
public final class SmsAlertStore {

    private static final String PREFS = "sms_alert_notification_inbox";
    private static final String KEY_ITEMS = "items";
    private static final int MAX_ITEMS = 200;

    private SmsAlertStore() {
    }

    public static synchronized void add(
            @NonNull Context context,
            @NonNull String packageName,
            @NonNull String sender,
            @NonNull String message,
            long postedAt
    ) {
        if (message.trim().isEmpty()) return;

        String id = Integer.toHexString(
                (packageName + "|" + sender + "|" + message)
                        .toLowerCase(Locale.ROOT)
                        .hashCode()
        );

        List<Item> items = getAll(context);
        for (Item item : items) {
            if (item.id.equals(id)) return;
        }

        items.add(new Item(
                id,
                packageName,
                sender.trim().isEmpty() ? "SMS Alert" : sender.trim(),
                message.trim(),
                categorize(message),
                postedAt,
                false
        ));

        sort(items);
        while (items.size() > MAX_ITEMS) items.remove(items.size() - 1);
        save(context, items);
    }

    @NonNull
    public static synchronized List<Item> getAll(@NonNull Context context) {
        String raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_ITEMS, "[]");
        List<Item> items = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                items.add(Item.fromJson(array.getJSONObject(i)));
            }
        } catch (Exception ignored) {
        }
        sort(items);
        return items;
    }

    public static synchronized void markRead(
            @NonNull Context context,
            @NonNull String id,
            boolean read
    ) {
        List<Item> items = getAll(context);
        for (Item item : items) {
            if (item.id.equals(id)) {
                item.read = read;
                break;
            }
        }
        save(context, items);
    }

    public static synchronized void delete(
            @NonNull Context context,
            @NonNull String id
    ) {
        List<Item> items = getAll(context);
        for (int i = items.size() - 1; i >= 0; i--) {
            if (items.get(i).id.equals(id)) items.remove(i);
        }
        save(context, items);
    }

    public static synchronized void clear(@NonNull Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_ITEMS)
                .apply();
    }

    private static String categorize(String message) {
        String lower = message.toLowerCase(Locale.ROOT);
        if (containsAny(lower, "debited", "credited", "upi", "a/c", "account", "bank", "txn", "transaction", "payment", "paid")) {
            return "BANKING";
        }
        if (containsAny(lower, "offer", "discount", "sale", "coupon", "cashback", "deal")) {
            return "OFFERS";
        }
        return "OTHER";
    }

    private static boolean containsAny(String text, String... values) {
        for (String value : values) if (text.contains(value)) return true;
        return false;
    }

    private static void sort(List<Item> items) {
        Collections.sort(items, (left, right) -> Long.compare(right.postedAt, left.postedAt));
    }

    private static void save(Context context, List<Item> items) {
        JSONArray array = new JSONArray();
        for (Item item : items) array.put(item.toJson());
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        preferences.edit().putString(KEY_ITEMS, array.toString()).apply();
    }

    public static final class Item {
        public final String id;
        public final String packageName;
        public final String sender;
        public final String message;
        public final String category;
        public final long postedAt;
        public boolean read;

        Item(
                String id,
                String packageName,
                String sender,
                String message,
                String category,
                long postedAt,
                boolean read
        ) {
            this.id = id;
            this.packageName = packageName;
            this.sender = sender;
            this.message = message;
            this.category = category;
            this.postedAt = postedAt;
            this.read = read;
        }

        JSONObject toJson() {
            JSONObject object = new JSONObject();
            try {
                object.put("id", id);
                object.put("packageName", packageName);
                object.put("sender", sender);
                object.put("message", message);
                object.put("category", category);
                object.put("postedAt", postedAt);
                object.put("read", read);
            } catch (Exception ignored) {
            }
            return object;
        }

        static Item fromJson(JSONObject object) {
            return new Item(
                    object.optString("id"),
                    object.optString("packageName"),
                    object.optString("sender", "SMS Alert"),
                    object.optString("message"),
                    object.optString("category", "OTHER"),
                    object.optLong("postedAt", System.currentTimeMillis()),
                    object.optBoolean("read", false)
            );
        }
    }
}
