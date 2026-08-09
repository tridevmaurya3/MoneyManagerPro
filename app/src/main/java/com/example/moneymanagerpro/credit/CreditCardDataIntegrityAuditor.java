package com.example.moneymanagerpro.credit;

import androidx.annotation.NonNull;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.moneymanagerpro.database.AppDatabase;
import com.example.moneymanagerpro.model.CreditCard;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Preview-first repair for exact transaction duplication caused by legacy account/card merging. */
public final class CreditCardDataIntegrityAuditor {
    private CreditCardDataIntegrityAuditor() {}

    @NonNull public static Preview preview(AppDatabase database, CreditCard card) {
        SupportSQLiteDatabase sql = database.getOpenHelper().getReadableDatabase();
        Map<String, Group> groups = new LinkedHashMap<>();
        String query = "SELECT id,date,type,amount,category,account,note FROM transactions "
                + "WHERE LOWER(TRIM(account)) = LOWER(TRIM(?)) OR LOWER(TRIM(category)) = LOWER(TRIM(?)) ORDER BY id";
        try (android.database.Cursor cursor = sql.query(query, new Object[]{card.getAccountName(), card.getName()})) {
            while (cursor.moveToNext()) {
                int id = cursor.getInt(0);
                String date = value(cursor, 1); String type = value(cursor, 2);
                double amount = cursor.getDouble(3); String category = value(cursor, 4);
                String account = value(cursor, 5); String note = value(cursor, 6);
                String key = normalize(date) + "|" + normalize(type) + "|" + String.format(Locale.US, "%.2f", amount)
                        + "|" + normalize(category) + "|" + normalize(account) + "|" + normalize(note);
                Group group = groups.get(key);
                if (group == null) { group = new Group(date, type, amount, category, account, note); groups.put(key, group); }
                group.ids.add(id);
            }
        }
        List<Group> duplicates = new ArrayList<>(); double duplicateEffect = 0d; int extraRows = 0;
        for (Group group : groups.values()) if (group.ids.size() > 1) {
            duplicates.add(group); int extras = group.ids.size() - 1; extraRows += extras;
            duplicateEffect += ("EXPENSE".equalsIgnoreCase(group.type) ? group.amount : -group.amount) * extras;
        }
        return new Preview(card.getName(), card.getAccountName(), duplicates, extraRows, duplicateEffect);
    }

    public static int repairExactDuplicates(AppDatabase database, CreditCard card) {
        Preview preview = preview(database, card);
        if (preview.extraRows == 0) return 0;
        final int[] removed = {0};
        database.runInTransaction(() -> {
            SupportSQLiteDatabase sql = database.getOpenHelper().getWritableDatabase();
            for (Group group : preview.groups) {
                for (int i = 1; i < group.ids.size(); i++) {
                    sql.execSQL("DELETE FROM transactions WHERE id = ?", new Object[]{group.ids.get(i)});
                    removed[0]++;
                }
            }
        });
        return removed[0];
    }

    private static String value(android.database.Cursor c, int index) { return c.isNull(index) ? "" : c.getString(index); }
    private static String normalize(String v) { return v == null ? "" : v.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " "); }

    public static final class Preview {
        public final String cardName; public final String accountName; public final List<Group> groups;
        public final int extraRows; public final double duplicateOutstandingEffect;
        private Preview(String cardName, String accountName, List<Group> groups, int extraRows, double effect) {
            this.cardName = cardName; this.accountName = accountName; this.groups = groups; this.extraRows = extraRows; this.duplicateOutstandingEffect = effect;
        }
        @NonNull public String describe() {
            if (extraRows == 0) return "No exact duplicate transaction rows were found for " + cardName + ".\nNo data will be changed.";
            StringBuilder text = new StringBuilder("Exact duplicate groups: ").append(groups.size())
                    .append("\nExtra rows: ").append(extraRows)
                    .append("\nPossible outstanding effect: ₹").append(String.format(Locale.US, "%.2f", duplicateOutstandingEffect));
            int limit = Math.min(5, groups.size());
            for (int i = 0; i < limit; i++) { Group g = groups.get(i); text.append("\n\n").append(g.date).append(" • ").append(g.type).append(" • ₹").append(String.format(Locale.US, "%.2f", g.amount)).append(" • repeated ").append(g.ids.size()).append(" times"); }
            text.append("\n\nRepair keeps the oldest row in each exact-match group and removes only later identical copies inside one database transaction.");
            return text.toString();
        }
    }

    public static final class Group {
        public final String date, type, category, account, note; public final double amount; public final List<Integer> ids = new ArrayList<>();
        private Group(String date, String type, double amount, String category, String account, String note) { this.date = date; this.type = type; this.amount = amount; this.category = category; this.account = account; this.note = note; }
    }
}
