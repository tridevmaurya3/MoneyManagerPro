package com.example.moneymanagerpro;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.moneymanagerpro.database.DatabaseClient;
import com.example.moneymanagerpro.model.Transaction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * STEP 13C - Read-only account/card aggregate endpoint for Family Hub.
 *
 * MoneyManagerPro remains the canonical ledger. This provider exposes only
 * current-month totals grouped by the transaction account/card label. It never
 * exposes individual transaction rows, notes, merchant text, SMS bodies,
 * account numbers or per-account balances.
 */
public final class TridevCompanionAccountAnalyticsProvider extends ContentProvider {

    public static final String AUTHORITY =
            "com.example.moneymanagerpro.tridev.accountanalytics";
    public static final String METHOD_ACCOUNT_BREAKDOWN =
            "get_account_breakdown_v1";

    @Override
    public boolean onCreate() {
        return getContext() != null;
    }

    @Nullable
    @Override
    public Bundle call(
            @NonNull String method,
            @Nullable String arg,
            @Nullable Bundle extras) {
        Context context = getContext();
        if (context == null) {
            return response("FAILED", "MoneyManager context is unavailable");
        }
        if (!TridevCompanionTrust.verifyCaller(
                context,
                Binder.getCallingUid(),
                TridevCompanionTrust.FAMILY_HUB_PACKAGE)) {
            return response("REJECTED",
                    "Family Hub package or pinned signing certificate is not trusted");
        }
        if (!METHOD_ACCOUNT_BREAKDOWN.equals(method)) {
            return response("REJECTED", "Unsupported account analytics request");
        }
        return loadBreakdown(context);
    }

    @NonNull
    private Bundle loadBreakdown(@NonNull Context context) {
        try {
            Calendar start = Calendar.getInstance();
            start.set(Calendar.DAY_OF_MONTH, 1);
            start.set(Calendar.HOUR_OF_DAY, 0);
            start.set(Calendar.MINUTE, 0);
            start.set(Calendar.SECOND, 0);
            start.set(Calendar.MILLISECOND, 0);

            Calendar end = (Calendar) start.clone();
            end.set(Calendar.DAY_OF_MONTH, end.getActualMaximum(Calendar.DAY_OF_MONTH));
            end.set(Calendar.HOUR_OF_DAY, 23);
            end.set(Calendar.MINUTE, 59);
            end.set(Calendar.SECOND, 59);
            end.set(Calendar.MILLISECOND, 999);

            Map<String, String> canonicalLabels = catalogLabels(context);
            Map<String, BigDecimal> expenseByAccount = new HashMap<>();
            Map<String, BigDecimal> incomeByAccount = new HashMap<>();
            BigDecimal expenseTotal = BigDecimal.ZERO;
            BigDecimal incomeTotal = BigDecimal.ZERO;

            List<Transaction> transactions = DatabaseClient.getInstance(
                    context.getApplicationContext())
                    .getAppDatabase()
                    .transactionDao()
                    .getAllTransactions();
            if (transactions != null) {
                for (Transaction transaction : transactions) {
                    if (transaction == null
                            || !inRange(transaction.getDate(), start, end)) continue;

                    String type = safe(transaction.getType()).toUpperCase(Locale.ROOT);
                    if (!"EXPENSE".equals(type) && !"INCOME".equals(type)) continue;

                    BigDecimal amount = BigDecimal.valueOf(
                            Math.abs(transaction.getAmount()));
                    String account = accountLabel(
                            transaction.getAccount(), canonicalLabels);

                    if ("EXPENSE".equals(type)) {
                        expenseTotal = expenseTotal.add(amount);
                        expenseByAccount.merge(account, amount, BigDecimal::add);
                    } else {
                        incomeTotal = incomeTotal.add(amount);
                        incomeByAccount.merge(account, amount, BigDecimal::add);
                    }
                }
            }

            Breakdown expense = breakdown(expenseByAccount);
            Breakdown income = breakdown(incomeByAccount);
            SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            SimpleDateFormat label = new SimpleDateFormat("MMMM yyyy", Locale.ENGLISH);

            Bundle result = response("OK", "MoneyManager account analytics ready");
            result.putString("currency", TridevIntegrationContract.DEFAULT_CURRENCY);
            result.putString("period_start", iso.format(start.getTime()));
            result.putString("period_end", iso.format(end.getTime()));
            result.putString("period_label", label.format(start.getTime()));
            result.putLong("expense_total_minor", toMinor(expenseTotal));
            result.putLong("income_total_minor", toMinor(incomeTotal));
            result.putStringArray("expense_account_labels", expense.labels);
            result.putLongArray("expense_account_totals_minor", expense.totalsMinor);
            result.putStringArray("income_account_labels", income.labels);
            result.putLongArray("income_account_totals_minor", income.totalsMinor);
            result.putLong("generated_at", System.currentTimeMillis());
            return result;
        } catch (RuntimeException unavailable) {
            return response("FAILED", "MoneyManager account analytics are unavailable");
        }
    }

    @NonNull
    private Map<String, String> catalogLabels(@NonNull Context context) {
        Map<String, String> labels = new HashMap<>();
        TridevMoneyMappingEngine.Catalog catalog =
                new TridevMoneyMappingEngine(context).readCatalog();

        for (TridevMoneyMappingEngine.CatalogItem item : catalog.accounts) {
            if (item == null) continue;
            String value = safe(item.transactionValue);
            if (value.isEmpty()) continue;
            String display = safe(item.displayName);
            if (display.isEmpty()) display = value;
            String type = prettyType(item.type);
            if (!type.isEmpty()
                    && !display.toLowerCase(Locale.ROOT)
                    .contains(type.toLowerCase(Locale.ROOT))) {
                display += " • " + type;
            }
            labels.put(value.toLowerCase(Locale.ROOT), limit(display, 120));
        }

        for (TridevMoneyMappingEngine.CatalogItem item : catalog.creditCards) {
            if (item == null) continue;
            String value = safe(item.transactionValue);
            if (value.isEmpty()) continue;
            String display = safe(item.displayName);
            if (display.isEmpty()) display = value;
            if (!display.toLowerCase(Locale.ROOT).contains("credit card")) {
                display += " • Credit Card";
            }
            labels.put(value.toLowerCase(Locale.ROOT), limit(display, 120));
        }
        return labels;
    }

    @NonNull
    private String accountLabel(
            @Nullable String value,
            @NonNull Map<String, String> canonicalLabels) {
        String clean = metadata(value, 120);
        if (clean.isEmpty()) return "Unassigned";
        String canonical = canonicalLabels.get(clean.toLowerCase(Locale.ROOT));
        return canonical == null || canonical.isEmpty() ? clean : canonical;
    }

    @NonNull
    private Breakdown breakdown(@NonNull Map<String, BigDecimal> source) {
        List<Map.Entry<String, BigDecimal>> entries = new ArrayList<>(source.entrySet());
        entries.sort((left, right) -> {
            int byAmount = right.getValue().compareTo(left.getValue());
            return byAmount != 0
                    ? byAmount
                    : left.getKey().compareToIgnoreCase(right.getKey());
        });

        String[] labels = new String[entries.size()];
        long[] totals = new long[entries.size()];
        for (int index = 0; index < entries.size(); index++) {
            Map.Entry<String, BigDecimal> entry = entries.get(index);
            labels[index] = entry.getKey();
            totals[index] = toMinor(entry.getValue());
        }
        return new Breakdown(labels, totals);
    }

    private static final class Breakdown {
        @NonNull final String[] labels;
        @NonNull final long[] totalsMinor;

        private Breakdown(@NonNull String[] labels, @NonNull long[] totalsMinor) {
            this.labels = labels;
            this.totalsMinor = totalsMinor;
        }
    }

    private long toMinor(@NonNull BigDecimal amount) {
        try {
            return amount.movePointRight(2)
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValueExact();
        } catch (ArithmeticException overflow) {
            return amount.signum() >= 0 ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
    }

    private boolean inRange(
            @Nullable String value,
            @NonNull Calendar start,
            @NonNull Calendar end) {
        Date date = parseDate(value);
        return date != null
                && !date.before(start.getTime())
                && !date.after(end.getTime());
    }

    @Nullable
    private Date parseDate(@Nullable String value) {
        String clean = safe(value);
        if (clean.isEmpty()) return null;
        String[] patterns = {
                "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy-MM-dd",
                "dd-MM-yyyy HH:mm:ss", "dd-MM-yyyy HH:mm", "dd-MM-yyyy",
                "dd/MM/yyyy HH:mm:ss", "dd/MM/yyyy HH:mm", "dd/MM/yyyy",
                "dd MMM yyyy", "dd MMMM yyyy"
        };
        for (String pattern : patterns) {
            SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
            format.setLenient(false);
            ParsePosition position = new ParsePosition(0);
            Date date = format.parse(clean, position);
            if (date != null && position.getIndex() == clean.length()) return date;
        }
        return null;
    }

    @NonNull
    private String prettyType(@Nullable String value) {
        String clean = safe(value).replace('_', ' ').toLowerCase(Locale.ROOT);
        if (clean.isEmpty()) return "";
        String[] words = clean.split("\\s+");
        StringBuilder out = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) out.append(word.substring(1));
        }
        return out.toString();
    }

    @NonNull
    private Bundle response(@NonNull String status, @NonNull String reason) {
        Bundle result = new Bundle();
        result.putString("status", status);
        result.putString("reason", limit(metadata(reason, 240), 240));
        return result;
    }

    @NonNull
    private String metadata(@Nullable String value, int max) {
        String clean = safe(value)
                .replace('\n', ' ')
                .replace('\r', ' ')
                .replaceAll("\\s+", " ");
        return limit(clean, max);
    }

    @NonNull
    private String limit(@NonNull String value, int max) {
        return value.length() <= max ? value : value.substring(0, max).trim();
    }

    @NonNull
    private String safe(@Nullable String value) {
        return value == null ? "" : value.trim();
    }

    @Nullable
    @Override
    public Cursor query(
            @NonNull Uri uri,
            @Nullable String[] projection,
            @Nullable String selection,
            @Nullable String[] selectionArgs,
            @Nullable String sortOrder) {
        return null;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        return null;
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        return null;
    }

    @Override
    public int delete(
            @NonNull Uri uri,
            @Nullable String selection,
            @Nullable String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(
            @NonNull Uri uri,
            @Nullable ContentValues values,
            @Nullable String selection,
            @Nullable String[] selectionArgs) {
        return 0;
    }
}
