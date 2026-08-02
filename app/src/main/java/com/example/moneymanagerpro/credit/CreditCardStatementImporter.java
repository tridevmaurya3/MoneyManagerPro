package com.example.moneymanagerpro.credit;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.moneymanagerpro.database.AppDatabase;
import com.example.moneymanagerpro.model.CreditCard;
import com.example.moneymanagerpro.model.Transaction;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class CreditCardStatementImporter {

    private static final String[] INPUT_DATE_PATTERNS = {
            "yyyy-MM-dd", "dd-MM-yyyy", "dd/MM/yyyy", "MM/dd/yyyy",
            "dd MMM yyyy", "dd-MMM-yyyy"
    };

    private CreditCardStatementImporter() {
    }

    @NonNull
    public static Result importCsv(
            @NonNull Context context,
            @NonNull AppDatabase database,
            @NonNull CreditCard card,
            @NonNull Uri uri
    ) throws Exception {
        List<Row> rows = readRows(context, uri);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException(
                    "No valid statement rows found. Required columns: date, description, amount, type"
            );
        }

        int[] imported = {0};
        int[] refunds = {0};
        int[] payments = {0};
        int[] skipped = {0};

        database.runInTransaction(() -> {
            SupportSQLiteDatabase sql =
                    database.getOpenHelper().getWritableDatabase();

            for (Row row : rows) {
                String fingerprint = fingerprint(
                        card.getId() + "|" + row.date + "|" + row.amount
                                + "|" + row.description + "|" + row.kind
                );
                String marker = "CC-IMPORT:" + fingerprint;

                if (exists(sql, marker)) {
                    skipped[0]++;
                    continue;
                }

                Transaction transaction = new Transaction();
                transaction.setAmount(row.amount);
                transaction.setAccount(card.getAccountName());
                transaction.setDate(row.date + " 12:00");

                String lower = (row.kind + " " + row.description)
                        .toLowerCase(Locale.ROOT);
                if (containsAny(lower, "refund", "reversal", "reversed")) {
                    transaction.setType("INCOME");
                    transaction.setCategory("Credit Card Refund");
                    refunds[0]++;
                } else if (containsAny(
                        lower,
                        "payment",
                        "paid",
                        "autopay",
                        "bill payment"
                )) {
                    transaction.setType("INCOME");
                    transaction.setCategory("Credit Card Payment");
                    payments[0]++;
                } else if (containsAny(
                        row.kind.toLowerCase(Locale.ROOT),
                        "credit",
                        " cr",
                        "cr "
                )) {
                    transaction.setType("INCOME");
                    transaction.setCategory("Credit Card Credit");
                } else {
                    transaction.setType("EXPENSE");
                    transaction.setCategory(card.getName());
                }

                transaction.setNote(
                        trimTo(row.description, 180) + " • " + marker
                );
                database.transactionDao().insert(transaction);
                imported[0]++;
            }
        });

        return new Result(
                rows.size(),
                imported[0],
                refunds[0],
                payments[0],
                skipped[0]
        );
    }

    @NonNull
    private static List<Row> readRows(
            Context context,
            Uri uri
    ) throws Exception {
        InputStream stream = context.getContentResolver().openInputStream(uri);
        if (stream == null) {
            throw new IllegalArgumentException("Unable to open statement file");
        }

        List<Row> result = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8)
        )) {
            String headerLine = reader.readLine();
            if (headerLine == null) return result;
            List<String> headers = parseCsvLine(headerLine);
            int dateIndex = indexOf(headers, "date", "transaction date", "txn date");
            int descriptionIndex = indexOf(
                    headers, "description", "narration", "details", "merchant"
            );
            int amountIndex = indexOf(
                    headers, "amount", "transaction amount", "txn amount"
            );
            int typeIndex = indexOf(
                    headers, "type", "transaction type", "debit/credit", "dr/cr"
            );

            if (dateIndex < 0 || descriptionIndex < 0 || amountIndex < 0) {
                throw new IllegalArgumentException(
                        "CSV must contain date, description and amount columns"
                );
            }

            String line;
            while ((line = reader.readLine()) != null) {
                List<String> values = parseCsvLine(line);
                String date = normalizeDate(value(values, dateIndex));
                String description = value(values, descriptionIndex).trim();
                String amountText = value(values, amountIndex)
                        .replace("₹", "")
                        .replace(",", "")
                        .replace("INR", "")
                        .trim();
                String kind = typeIndex < 0 ? "" : value(values, typeIndex).trim();

                double amount;
                try {
                    amount = Math.abs(Double.parseDouble(amountText));
                } catch (Exception ignored) {
                    continue;
                }
                if (date.isEmpty() || description.isEmpty() || amount <= 0) continue;
                result.add(new Row(date, description, amount, kind));
            }
        }
        return result;
    }

    private static boolean exists(
            SupportSQLiteDatabase database,
            String marker
    ) {
        try (android.database.Cursor cursor = database.query(
                "SELECT COUNT(*) FROM transactions WHERE note LIKE ?",
                new Object[]{"%" + marker + "%"}
        )) {
            return cursor.moveToFirst() && cursor.getInt(0) > 0;
        }
    }

    @NonNull
    private static String normalizeDate(@Nullable String value) {
        String clean = value == null ? "" : value.trim();
        for (String pattern : INPUT_DATE_PATTERNS) {
            SimpleDateFormat input = new SimpleDateFormat(pattern, Locale.ENGLISH);
            input.setLenient(false);
            ParsePosition position = new ParsePosition(0);
            Date parsed = input.parse(clean, position);
            if (parsed != null && position.getIndex() == clean.length()) {
                return new SimpleDateFormat(
                        "yyyy-MM-dd", Locale.US
                ).format(parsed);
            }
        }
        return "";
    }

    @NonNull
    private static List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char ch = line.charAt(index);
            if (ch == '"') {
                if (quoted && index + 1 < line.length()
                        && line.charAt(index + 1) == '"') {
                    current.append('"');
                    index++;
                } else {
                    quoted = !quoted;
                }
            } else if (ch == ',' && !quoted) {
                values.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        values.add(current.toString().trim());
        return values;
    }

    private static int indexOf(List<String> headers, String... candidates) {
        for (int index = 0; index < headers.size(); index++) {
            String header = headers.get(index)
                    .replace("\uFEFF", "")
                    .trim()
                    .toLowerCase(Locale.ROOT);
            for (String candidate : candidates) {
                if (header.equals(candidate)) return index;
            }
        }
        return -1;
    }

    private static String value(List<String> values, int index) {
        return index >= 0 && index < values.size() ? values.get(index) : "";
    }

    private static boolean containsAny(String text, String... values) {
        for (String value : values) if (text.contains(value)) return true;
        return false;
    }

    private static String fingerprint(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (int index = 0; index < 10; index++) {
                result.append(String.format(Locale.US, "%02x", bytes[index]));
            }
            return result.toString();
        } catch (Exception ignored) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private static String trimTo(String value, int length) {
        String clean = value == null ? "" : value.trim();
        return clean.length() <= length ? clean : clean.substring(0, length);
    }

    private static final class Row {
        final String date;
        final String description;
        final double amount;
        final String kind;

        Row(String date, String description, double amount, String kind) {
            this.date = date;
            this.description = description;
            this.amount = amount;
            this.kind = kind;
        }
    }

    public static final class Result {
        public final int rows;
        public final int imported;
        public final int refunds;
        public final int payments;
        public final int duplicatesSkipped;

        Result(int rows, int imported, int refunds, int payments, int duplicatesSkipped) {
            this.rows = rows;
            this.imported = imported;
            this.refunds = refunds;
            this.payments = payments;
            this.duplicatesSkipped = duplicatesSkipped;
        }
    }
}
