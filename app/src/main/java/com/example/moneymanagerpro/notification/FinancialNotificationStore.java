package com.example.moneymanagerpro.notification;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Stores only parsed financial notification summaries on-device. */
public final class FinancialNotificationStore {

    private static final String PREFS = "financial_notification_inbox";
    private static final String KEY_ITEMS = "items";
    private static final int MAX_ITEMS = 120;

    private FinancialNotificationStore() {
    }

    public static synchronized boolean add(
            @NonNull Context context,
            @NonNull FinancialNotificationParser.ParsedNotification parsed
    ) {
        List<Item> items = getAll(context);
        for (Item item : items) if (item.id.equals(parsed.id)) return false;

        items.add(new Item(
                parsed.id,
                parsed.packageName,
                parsed.title,
                parsed.body,
                parsed.type,
                parsed.amount,
                parsed.merchant,
                parsed.lastFour,
                parsed.reference,
                parsed.category,
                parsed.postedAt,
                "PENDING"
        ));
        sort(items);
        while (items.size() > MAX_ITEMS) items.remove(items.size() - 1);
        save(context, items);
        return true;
    }

    @NonNull
    public static synchronized List<Item> getAll(@NonNull Context context) {
        String raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_ITEMS, "[]");
        List<Item> items = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.getJSONObject(i);
                items.add(Item.fromJson(object));
            }
        } catch (Exception ignored) {
        }
        sort(items);
        return items;
    }

    public static synchronized void updateStatus(
            @NonNull Context context,
            @NonNull String id,
            @NonNull String status
    ) {
        List<Item> items = getAll(context);
        for (Item item : items) {
            if (item.id.equals(id)) {
                item.status = status;
                break;
            }
        }
        save(context, items);
    }

    public static synchronized void delete(@NonNull Context context, @NonNull String id) {
        List<Item> items = getAll(context);
        for (int i = items.size() - 1; i >= 0; i--) {
            if (items.get(i).id.equals(id)) items.remove(i);
        }
        save(context, items);
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
        public final String title;
        public final String body;
        public final String type;
        public final double amount;
        public final String merchant;
        public final String lastFour;
        public final String reference;
        public final String category;
        public final long postedAt;
        public String status;

        Item(String id, String packageName, String title, String body, String type,
             double amount, String merchant, String lastFour, String reference,
             String category, long postedAt, String status) {
            this.id = id;
            this.packageName = packageName;
            this.title = title;
            this.body = body;
            this.type = type;
            this.amount = amount;
            this.merchant = merchant;
            this.lastFour = lastFour;
            this.reference = reference;
            this.category = category;
            this.postedAt = postedAt;
            this.status = status;
        }

        JSONObject toJson() {
            JSONObject object = new JSONObject();
            try {
                object.put("id", id);
                object.put("packageName", packageName);
                object.put("title", title);
                object.put("body", body);
                object.put("type", type);
                object.put("amount", amount);
                object.put("merchant", merchant);
                object.put("lastFour", lastFour);
                object.put("reference", reference);
                object.put("category", category);
                object.put("postedAt", postedAt);
                object.put("status", status);
            } catch (Exception ignored) {
            }
            return object;
        }

        static Item fromJson(JSONObject object) {
            return new Item(
                    object.optString("id"),
                    object.optString("packageName"),
                    object.optString("title"),
                    object.optString("body"),
                    object.optString("type", "EXPENSE"),
                    object.optDouble("amount", 0d),
                    object.optString("merchant", "Financial Notification"),
                    object.optString("lastFour"),
                    object.optString("reference"),
                    object.optString("category", "Other Expense"),
                    object.optLong("postedAt", System.currentTimeMillis()),
                    object.optString("status", "PENDING")
            );
        }
    }
}
