package com.example.moneymanagerpro;

import android.content.Context;
import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.moneymanagerpro.database.DatabaseClient;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Reconciliation-only helper for SmartSMS history that predates automatic sync.
 *
 * The user's historical MoneyManager ledger may contain only amount, direction
 * and a date. For that specific legacy case we may safely auto-link only when
 * there is exactly one same-day transaction with the same two-decimal amount and
 * the same Income/Expense direction. Two or more candidates always stay in the
 * visible Reconciliation Center for explicit user choice.
 *
 * This class never creates, edits or deletes a MoneyManager transaction.
 */
public final class TridevHistoricalSmartSmsReconciler {

    public static final class Result {
        public final boolean linked;
        public final long transactionId;
        @NonNull public final String reason;

        private Result(boolean linked, long transactionId, @NonNull String reason) {
            this.linked = linked;
            this.transactionId = transactionId;
            this.reason = reason;
        }
    }

    private static final int MAX_AMOUNT_CANDIDATES = 250;

    private final Context appContext;
    private final TridevEventQueue queue;

    public TridevHistoricalSmartSmsReconciler(@NonNull Context context) {
        appContext = context.getApplicationContext();
        queue = TridevEventQueue.getInstance(appContext);
    }

    @NonNull
    public Result reconcile(@NonNull TridevIntegrationContract.Event event) {
        if (!isHistoricalSmartSms(event)) {
            return new Result(false, 0L, "Event is not a historical SmartSMS reconciliation signal");
        }
        if (event.amountMinor <= 0L || event.occurredAt <= 0L) {
            return new Result(false, 0L, "Historical signal is missing amount or date");
        }

        String expectedType = expectedMoneyType(event);
        if (expectedType.isEmpty()) {
            return new Result(false, 0L, "Historical signal direction requires manual review");
        }

        List<Long> sameDay = findSameDayCandidates(event, expectedType);
        if (sameDay.size() != 1) {
            return new Result(
                    false,
                    0L,
                    sameDay.isEmpty()
                            ? "No same-day manual MoneyManager transaction matched this historical SMS"
                            : "More than one same-day MoneyManager transaction could match this historical SMS");
        }

        long transactionId = sameDay.get(0);
        if (!queue.confirmExistingMoneyManagerTransaction(event.eventId, transactionId)) {
            return new Result(false, 0L, "Historical match could not be linked safely");
        }

        return new Result(
                true,
                transactionId,
                "Historical SmartSMS signal linked to the unique same-day manual MoneyManager transaction");
    }

    @NonNull
    private List<Long> findSameDayCandidates(
            @NonNull TridevIntegrationContract.Event event,
            @NonNull String expectedType) {
        List<Long> matches = new ArrayList<>();
        double amount = event.amountMinor / 100.0d;
        SupportSQLiteDatabase db = DatabaseClient
                .getInstance(appContext)
                .getAppDatabase()
                .getOpenHelper()
                .getReadableDatabase();

        try (Cursor cursor = db.query(
                "SELECT id, type, date FROM transactions "
                        + "WHERE ABS(amount - ?) < 0.005 "
                        + "ORDER BY id DESC LIMIT " + MAX_AMOUNT_CANDIDATES,
                new Object[]{amount})) {
            while (cursor.moveToNext()) {
                long id = cursor.isNull(0) ? 0L : cursor.getLong(0);
                String type = cursor.isNull(1) ? "" : cursor.getString(1);
                String date = cursor.isNull(2) ? "" : cursor.getString(2);
                if (id <= 0L || !expectedType.equalsIgnoreCase(clean(type))) continue;

                Long candidateTime = parseMoneyManagerDate(date);
                if (candidateTime == null) continue;
                if (sameLocalDay(event.occurredAt, candidateTime)) {
                    matches.add(id);
                    if (matches.size() > 1) break;
                }
            }
        } catch (RuntimeException ignored) {
            return new ArrayList<>();
        }
        return matches;
    }

    private boolean sameLocalDay(long left, long right) {
        SimpleDateFormat day = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        return day.format(new Date(left)).equals(day.format(new Date(right)));
    }

    @Nullable
    private Long parseMoneyManagerDate(@Nullable String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        String value = raw.trim();
        String[] patterns = {
                "yyyy-MM-dd HH:mm",
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd",
                "dd-MM-yyyy HH:mm",
                "dd/MM/yyyy HH:mm",
                "dd-MM-yyyy",
                "dd/MM/yyyy"
        };
        for (String pattern : patterns) {
            SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
            format.setLenient(false);
            try {
                Date parsed = format.parse(value);
                if (parsed != null) return parsed.getTime();
            } catch (ParseException ignored) {
            }
        }
        return null;
    }

    @NonNull
    private String expectedMoneyType(@NonNull TridevIntegrationContract.Event event) {
        if (event.direction == TridevIntegrationContract.Direction.DEBIT) return "EXPENSE";
        if (event.direction == TridevIntegrationContract.Direction.CREDIT) return "INCOME";
        return "";
    }

    private boolean isHistoricalSmartSms(@NonNull TridevIntegrationContract.Event event) {
        return TridevIntegrationContract.APP_SMART_SMS.equals(event.sourceApp)
                && clean(event.eventId).startsWith("smartsms:history:")
                && clean(event.sourceRecordId).matches("sms:[0-9]+");
    }

    @NonNull
    private String clean(@Nullable String value) {
        return value == null ? "" : value.trim();
    }
}
