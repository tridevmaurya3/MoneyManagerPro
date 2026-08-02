package com.example.moneymanagerpro.notification;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Stores only parsed financial notification summaries on-device. */
public final class FinancialNotificationStore {

    private static final String PREFS = "financial_notification_inbox";
    private static final String KEY_ITEMS = "items";
    private static final String KEY_CAPTURE_ENABLED = "capture_enabled";
    private static final String KEY_SHOW_SAVED = "show_saved";
    private static final int MAX_ITEMS = 250;

    private FinancialNotificationStore() {
    }

    public static synchronized boolean add(
            @NonNull Context context,
            @NonNull FinancialNotificationParser.ParsedNotification parsed
    ) {
        if (!isCaptureEnabled(context)) return false;

        List<Item> items = getAll(context);
        for (Item item : items) {
            if (item.id.equals(parsed.id)) return false;
            if (isProbableDuplicate(item, parsed)) return false;
        }

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
                parsed.vpa,
                parsed.category,
                defaultAccount(parsed.lastFour),
                parsed.source,
                parsed.postedAt,
                "PENDING",
                ""
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
                items.add(Item.fromJson(array.getJSONObject(i)));
            }
        } catch (Exception ignored) {
        }

        sort(items);
        return items;
    }

    @NonNull
    public static synchronized List<Item> getByStatus(
            @NonNull Context context,
            @NonNull String status
    ) {
        List<Item> result = new ArrayList<>();
        for (Item item : getAll(context)) {
            if ("ALL".equals(status) || status.equals(item.status)) result.add(item);
        }
        return result;
    }

    @Nullable
    public static synchronized Item find(@NonNull Context context, @NonNull String id) {
        for (Item item : getAll(context)) {
            if (item.id.equals(id)) return item;
        }
        return null;
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

    public static synchronized void updateDetails(
            @NonNull Context context,
            @NonNull String id,
            @NonNull String type,
            double amount,
            @NonNull String merchant,
            @NonNull String category,
            @NonNull String account,
            @NonNull String note
    ) {
        List<Item> items = getAll(context);
        for (Item item : items) {
            if (item.id.equals(id)) {
                item.type = type;
                item.amount = amount;
                item.merchant = merchant;
                item.category = category;
                item.account = account;
                item.userNote = note;
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

    public static synchronized void clearStatus(
            @NonNull Context context,
            @NonNull String status
    ) {
        List<Item> items = getAll(context);
        for (int i = items.size() - 1; i >= 0; i--) {
            if (status.equals(items.get(i).status)) items.remove(i);
        }
        save(context, items);
    }

    public static boolean isCaptureEnabled(@NonNull Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_CAPTURE_ENABLED, true);
    }

    public static void setCaptureEnabled(@NonNull Context context, boolean enabled) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_CAPTURE_ENABLED, enabled).apply();
    }

    public static boolean shouldShowSaved(@NonNull Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_SHOW_SAVED, true);
    }

    public static void setShowSaved(@NonNull Context context, boolean show) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_SHOW_SAVED, show).apply();
    }

    private static boolean isProbableDuplicate(
            Item existing,
            FinancialNotificationParser.ParsedNotification parsed
    ) {
        if (!existing.type.equals(parsed.type)) return false;
        if (Math.abs(existing.amount - parsed.amount) > 0.001d) return false;
        if (Math.abs(existing.postedAt - parsed.postedAt) > 120000L) return false;

        if (!existing.reference.isEmpty() && !parsed.reference.isEmpty()) {
            return existing.reference.equalsIgnoreCase(parsed.reference);
        }

        return existing.merchant.equalsIgnoreCase(parsed.merchant)
                && existing.packageName.equals(parsed.packageName);
    }

    private static String defaultAccount(String lastFour) {
        return lastFour == null || lastFour.isEmpty()
                ? "Cash"
                : "Account ••••" + lastFour;
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
        public String type;
        public double amount;
        public String merchant;
        public final String lastFour;
        public final String reference;
        public final String vpa;
        public String category;
        public String account;
        public final String source;
        public final long postedAt;
        public String status;
        public String userNote;

        Item(
                String id,
                String packageName,
                String title,
                String body,
                String type,
                double amount,
                String merchant,
                String lastFour,
                String reference,
                String vpa,
                String category,
                String account,
                String source,
                long postedAt,
                String status,
                String userNote
        ) {
            this.id = id;
            this.packageName = packageName;
            this.title = title;
            this.body = body;
            this.type = type;
            this.amount = amount;
            this.merchant = merchant;
            this.lastFour = lastFour;
            this.reference = reference;
            this.vpa = vpa;
            this.category = category;
            this.account = account;
            this.source = source;
            this.postedAt = postedAt;
            this.status = status;
            this.userNote = userNote;
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
                object.put("vpa", vpa);
                object.put("category", category);
                object.put("account", account);
                object.put("source", source);
                object.put("postedAt", postedAt);
                object.put("status", status);
                object.put("userNote", userNote);
            } catch (Exception ignored) {
            }
            return object;
        }

        static Item fromJson(JSONObject object) {
            String lastFour = object.optString("lastFour");
            return new Item(
                    object.optString("id"),
                    object.optString("packageName"),
                    object.optString("title"),
                    object.optString("body"),
                    object.optString("type", "EXPENSE"),
                    object.optDouble("amount", 0d),
                    object.optString("merchant", "Financial Transaction"),
                    lastFour,
                    object.optString("reference"),
                    object.optString("vpa"),
                    object.optString("category", "Other Expense"),
                    object.optString("account", defaultAccount(lastFour)),
                    object.optString("source", "Financial App"),
                    object.optLong("postedAt", System.currentTimeMillis()),
                    object.optString("status", "PENDING"),
                    object.optString("userNote")
            );
        }
    }
}
