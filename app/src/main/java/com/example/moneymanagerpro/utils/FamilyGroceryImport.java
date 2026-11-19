package com.example.moneymanagerpro.utils;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FamilyGroceryImport {
    private static final Uri ITEMS = Uri.parse("content://com.tridev.familyhub.pending_grocery/items");
    private static final Pattern QUANTITY = Pattern.compile("^([0-9]+(?:[.][0-9]+)?|[.][0-9]+)\\s*(.*)$");

    private FamilyGroceryImport() {}

    public static final class Item {
        public final String name, quantity, unit, sourceId;
        public final int purchaseCount;

        public Item(String name, String raw, String sourceId, int purchaseCount) {
            this.name = name;
            this.sourceId = sourceId;
            this.purchaseCount = purchaseCount;
            Matcher matcher = QUANTITY.matcher(raw == null ? "" : raw.trim());
            if (matcher.matches()) {
                quantity = matcher.group(1);
                String suffix = matcher.group(2).trim();
                unit = suffix.isEmpty() ? "Unit" : suffix;
            } else {
                quantity = "";
                unit = "";
            }
        }

        String label() {
            return quantity.isEmpty() ? name : name + "  •  " + quantity + " " + unit;
        }
    }

    public static final class Purchase {
        private final Item item;
        private final double cost;

        public Purchase(Item item, double cost) {
            this.item = item;
            this.cost = cost;
        }
    }

    public static boolean complete(Context context, List<Purchase> purchases, long transactionId) {
        boolean completed = true;
        long now = System.currentTimeMillis();
        for (Purchase purchase : purchases) {
            Bundle request = new Bundle();
            request.putString("id", purchase.item.sourceId);
            request.putInt("count", purchase.item.purchaseCount);
            request.putString("reference", "mm:" + transactionId + ":" + now);
            request.putDouble("cost", purchase.cost);
            request.putLong("at", now);
            try {
                Bundle result = context.getContentResolver().call(ITEMS, "complete_purchase", null, request);
                if (result == null || !result.getBoolean("completed")) completed = false;
            } catch (RuntimeException error) {
                completed = false;
            }
        }
        return completed;
    }

    public static void pick(Context context, boolean floating, BooleanSupplier active,
                            Consumer<AlertDialog> onShown, Consumer<List<Item>> onImport) {
        new Thread(() -> {
            List<Item> items = new ArrayList<>();
            boolean available;
            try (Cursor rows = context.getContentResolver().query(ITEMS, null, null, null, null)) {
                available = rows != null;
                if (rows != null) {
                    while (rows.moveToNext()) items.add(new Item(rows.getString(0), rows.getString(1),
                            rows.getString(2), rows.getInt(3)));
                }
            } catch (RuntimeException e) {
                available = false;
            }
            String message = !available ? "Update Family Hub and use the signed MoneyManagerPro app"
                    : items.isEmpty() ? "No pending Grocery items" : null;
            new Handler(Looper.getMainLooper()).post(() -> {
                if (!active.getAsBoolean()) return;
                if (message != null) {
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show();
                    onShown.accept(null);
                    return;
                }
                showPicker(context, floating, active, onShown, onImport, items);
            });
        }, "FamilyGroceryImport").start();
    }
    private static void showPicker(Context context, boolean floating, BooleanSupplier active,
                                   Consumer<AlertDialog> onShown, Consumer<List<Item>> onImport,
                                   List<Item> items) {
        Context themed = new ContextThemeWrapper(context, android.R.style.Theme_Material_Light_Dialog_Alert);
        int deepGreen = 0xff146550;
        LinearLayout panel = new LinearLayout(themed);
        panel.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(themed, 14);
        panel.setPadding(padding, padding, padding, padding);
        panel.setBackground(surface(themed, 0xfff7fcfa, 0xffbedbd0, 20));

        TextView title = new TextView(themed);
        title.setText("Import Grocery");
        title.setTextSize(21);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(deepGreen);
        panel.addView(title);

        TextView count = new TextView(themed);
        count.setTextColor(deepGreen);
        count.setTextSize(13);
        count.setPadding(0, dp(themed, 4), 0, dp(themed, 10));
        panel.addView(count);

        ScrollView scroll = new ScrollView(themed);
        LinearLayout list = new LinearLayout(themed);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        int listHeight = Math.min(dp(themed, 310), context.getResources().getDisplayMetrics().heightPixels / 3);
        panel.addView(scroll, new LinearLayout.LayoutParams(-1,
                Math.min(listHeight, items.size() * dp(themed, 58))));

        LinearLayout footer = new LinearLayout(themed);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setPadding(0, dp(themed, 10), 0, 0);
        Button cancel = action(themed, "Cancel", 0xffe7f4ee, deepGreen);
        Button confirm = action(themed, "Import items", deepGreen, Color.WHITE);
        footer.addView(cancel, new LinearLayout.LayoutParams(0, dp(themed, 48), 1));
        LinearLayout.LayoutParams confirmParams = new LinearLayout.LayoutParams(0, dp(themed, 48), 2);
        confirmParams.setMarginStart(dp(themed, 8));
        footer.addView(confirm, confirmParams);
        panel.addView(footer);

        List<Item> selected = new ArrayList<>();
        count.setText("0 selected");
        confirm.setEnabled(false);
        confirm.setAlpha(0.55f);
        for (Item item : items) {
            CheckBox row = new CheckBox(themed);
            row.setText(item.label());
            row.setTextColor(0xff163e35);
            row.setTextSize(15);
            row.setMinHeight(dp(themed, 52));
            row.setPadding(dp(themed, 8), dp(themed, 4), dp(themed, 10), dp(themed, 4));
            row.setButtonTintList(ColorStateList.valueOf(deepGreen));
            row.setBackground(surface(themed, 0xffedf8f3, 0xffd1e8de, 12));
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
            rowParams.bottomMargin = dp(themed, 6);
            list.addView(row, rowParams);
            row.setOnCheckedChangeListener((button, checked) -> {
                if (checked) selected.add(item);
                else selected.remove(item);
                row.setBackground(surface(themed, checked ? 0xffd5eee3 : 0xffedf8f3,
                        checked ? 0xff72aa96 : 0xffd1e8de, 12));
                count.setText(selected.size() + " selected");
                confirm.setEnabled(!selected.isEmpty());
                confirm.setAlpha(selected.isEmpty() ? 0.55f : 1f);
            });
        }

        AlertDialog dialog = new AlertDialog.Builder(themed).setView(panel).create();
        if (floating) dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        cancel.setOnClickListener(view -> dialog.dismiss());
        confirm.setOnClickListener(view -> {
            if (!active.getAsBoolean() || selected.isEmpty()) return;
            List<Item> chosen = new ArrayList<>();
            for (Item item : items) if (selected.contains(item)) chosen.add(item);
            dialog.dismiss();
            onImport.accept(chosen);
        });
        dialog.setOnDismissListener(d -> onShown.accept(null));
        onShown.accept(dialog);
        dialog.show();
        int width = Math.min(dp(themed, 440),
                context.getResources().getDisplayMetrics().widthPixels - dp(themed, 28));
        dialog.getWindow().setLayout(Math.max(1, width), WindowManager.LayoutParams.WRAP_CONTENT);
    }

    private static Button action(Context context, String label, int fill, int foreground) {
        Button button = new Button(context);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextColor(foreground);
        button.setBackground(surface(context, fill, fill, 12));
        return button;
    }

    private static GradientDrawable surface(Context context, int fill, int stroke, int radius) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(fill);
        background.setStroke(dp(context, 1), stroke);
        background.setCornerRadius(dp(context, radius));
        return background;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
