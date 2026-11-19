package com.example.moneymanagerpro.utils;

import android.app.AlertDialog;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.ContextThemeWrapper;
import android.view.WindowManager;
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
        public final String name, quantity, unit;

        Item(String name, String raw) {
            this.name = name;
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

    public static void pick(Context context, boolean floating, BooleanSupplier active,
                            Consumer<AlertDialog> onShown, Consumer<List<Item>> onImport) {
        new Thread(() -> {
            List<Item> items = new ArrayList<>();
            boolean available;
            try (Cursor rows = context.getContentResolver().query(ITEMS, null, null, null, null)) {
                available = rows != null;
                if (rows != null) {
                    while (rows.moveToNext()) items.add(new Item(rows.getString(0), rows.getString(1)));
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
                CharSequence[] labels = new CharSequence[items.size()];
                boolean[] selected = new boolean[items.size()];
                for (int i = 0; i < items.size(); i++) labels[i] = items.get(i).label();
                Context themed = new ContextThemeWrapper(context, android.R.style.Theme_Material_Light_Dialog_Alert);
                AlertDialog dialog = new AlertDialog.Builder(themed)
                        .setTitle("Import Grocery")
                        .setMultiChoiceItems(labels, selected, (d, index, checked) -> selected[index] = checked)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton("Import items", (d, which) -> {
                            if (!active.getAsBoolean()) return;
                            List<Item> chosen = new ArrayList<>();
                            for (int i = 0; i < items.size(); i++) if (selected[i]) chosen.add(items.get(i));
                            if (!chosen.isEmpty()) onImport.accept(chosen);
                        })
                        .create();
                if (floating) dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
                dialog.setOnDismissListener(d -> onShown.accept(null));
                onShown.accept(dialog);
                dialog.show();
            });
        }, "FamilyGroceryImport").start();
    }
}
