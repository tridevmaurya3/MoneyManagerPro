package com.example.moneymanagerpro.pro;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.moneymanagerpro.activities.CreditCardActivity;
import com.example.moneymanagerpro.activities.TransactionsActivity;
import com.example.moneymanagerpro.database.AppDatabase;
import com.example.moneymanagerpro.database.DatabaseClient;
import com.example.moneymanagerpro.model.AccountBalance;
import com.example.moneymanagerpro.model.CreditCard;
import com.example.moneymanagerpro.model.Transaction;
import com.example.moneymanagerpro.utils.LoanReminderScheduler;
import com.example.moneymanagerpro.utils.NotificationHelper;
import com.example.moneymanagerpro.utils.ReminderScheduler;

import java.text.NumberFormat;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Smart finance intelligence that works only with data already recorded inside
 * Money Manager Pro. It intentionally does not read the SMS inbox and does not
 * require READ_SMS or RECEIVE_SMS permissions.
 */
public final class SmartFinanceNotificationWorker extends Worker {

    private static final String PERIODIC_WORK = "smart_finance_notification_pro";
    private static final String QUICK_WORK = "smart_finance_notification_quick";
    private static final String PREFS = "smart_finance_notification_state";
    private static final String KEY_LAST_MAX_TRANSACTION_ID = "last_max_transaction_id";
    private static final String KEY_DUPLICATE_SIGNATURE = "duplicate_signature";
    private static final String KEY_DUPLICATE_INITIALIZED = "duplicate_initialized";

    private static final String[] DATE_PATTERNS = {
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "yyyy-MM-dd",
            "dd-MM-yyyy HH:mm:ss",
            "dd-MM-yyyy HH:mm",
            "dd-MM-yyyy",
            "dd/MM/yyyy HH:mm:ss",
            "dd/MM/yyyy HH:mm",
            "dd/MM/yyyy",
            "MMM dd, yyyy HH:mm",
            "MMM dd, yyyy"
    };

    public SmartFinanceNotificationWorker(
            @NonNull Context context,
            @NonNull WorkerParameters workerParams
    ) {
        super(context, workerParams);
    }

    public static void schedule(@NonNull Context context) {
        Context appContext = context.getApplicationContext();

        // Keep the app's established bill/subscription and EMI reminder engines.
        ReminderScheduler.scheduleDaily(appContext);
        LoanReminderScheduler.schedule(appContext);

        OneTimeWorkRequest quick = new OneTimeWorkRequest.Builder(
                SmartFinanceNotificationWorker.class
        ).build();

        WorkManager.getInstance(appContext).enqueueUniqueWork(
                QUICK_WORK,
                ExistingWorkPolicy.REPLACE,
                quick
        );

        PeriodicWorkRequest periodic = new PeriodicWorkRequest.Builder(
                SmartFinanceNotificationWorker.class,
                12,
                TimeUnit.HOURS
        ).build();

        WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
                PERIODIC_WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                periodic
        );
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            Context context = getApplicationContext();
            AppDatabase database = DatabaseClient
                    .getInstance(context)
                    .getAppDatabase();

            List<Transaction> transactions = database.transactionDao().getAllTransactions();
            List<CreditCard> cards = database.creditCardDao().getActiveCreditCards();
            List<AccountBalance> balances = database.accountDao().getAccountBalances();

            SharedPreferences preferences = context.getSharedPreferences(
                    PREFS,
                    Context.MODE_PRIVATE
            );

            detectNewAppTransactions(context, preferences, transactions);
            detectLikelyDuplicates(context, preferences, transactions);
            checkCreditCardDueDates(context, preferences, cards, balances);

            return Result.success();
        } catch (Exception exception) {
            return Result.retry();
        }
    }

    private void detectNewAppTransactions(
            @NonNull Context context,
            @NonNull SharedPreferences preferences,
            List<Transaction> transactions
    ) {
        if (transactions == null || transactions.isEmpty()) {
            return;
        }

        int maxId = 0;
        for (Transaction transaction : transactions) {
            if (transaction != null) {
                maxId = Math.max(maxId, transaction.getId());
            }
        }

        int previousMaxId = preferences.getInt(KEY_LAST_MAX_TRANSACTION_ID, 0);

        // First run establishes the baseline so existing history does not flood notifications.
        if (previousMaxId <= 0) {
            preferences.edit().putInt(KEY_LAST_MAX_TRANSACTION_ID, maxId).apply();
            return;
        }

        if (maxId <= previousMaxId) {
            return;
        }

        int count = 0;
        double income = 0d;
        double expense = 0d;

        for (Transaction transaction : transactions) {
            if (transaction == null || transaction.getId() <= previousMaxId) {
                continue;
            }

            count++;
            double amount = Math.abs(transaction.getAmount());
            if ("INCOME".equalsIgnoreCase(safe(transaction.getType()))) {
                income += amount;
            } else if ("EXPENSE".equalsIgnoreCase(safe(transaction.getType()))) {
                expense += amount;
            }
        }

        preferences.edit().putInt(KEY_LAST_MAX_TRANSACTION_ID, maxId).apply();

        if (count <= 0) {
            return;
        }

        String message = count + " new app transaction"
                + (count == 1 ? "" : "s")
                + " detected";

        if (income > 0d) {
            message += " • Income " + money(income);
        }

        if (expense > 0d) {
            message += " • Expense " + money(expense);
        }

        NotificationHelper.showReminder(
                context,
                61001,
                "New finance activity",
                message,
                TransactionsActivity.class
        );
    }

    private void detectLikelyDuplicates(
            @NonNull Context context,
            @NonNull SharedPreferences preferences,
            List<Transaction> transactions
    ) {
        if (transactions == null || transactions.size() < 2) {
            return;
        }

        long now = System.currentTimeMillis();
        long cutoff = now - 30L * 24L * 60L * 60L * 1000L;

        Map<String, List<TransactionPoint>> groups = new LinkedHashMap<>();

        for (Transaction transaction : transactions) {
            if (transaction == null) continue;

            Date parsed = parseDate(transaction.getDate());
            if (parsed == null || parsed.getTime() < cutoff) continue;

            String fingerprint = safe(transaction.getType()).toLowerCase(Locale.ROOT)
                    + "|" + cents(transaction.getAmount())
                    + "|" + safe(transaction.getCategory()).toLowerCase(Locale.ROOT)
                    + "|" + safe(transaction.getAccount()).toLowerCase(Locale.ROOT);

            List<TransactionPoint> points = groups.get(fingerprint);
            if (points == null) {
                points = new ArrayList<>();
                groups.put(fingerprint, points);
            }
            points.add(new TransactionPoint(transaction.getId(), parsed.getTime()));
        }

        int duplicateExtraCount = 0;
        int highestDuplicateId = 0;

        for (List<TransactionPoint> points : groups.values()) {
            if (points.size() < 2) continue;

            Collections.sort(points, Comparator.comparingLong(point -> point.time));
            for (int index = 1; index < points.size(); index++) {
                TransactionPoint previous = points.get(index - 1);
                TransactionPoint current = points.get(index);
                long gap = Math.abs(current.time - previous.time);
                if (gap <= 24L * 60L * 60L * 1000L) {
                    duplicateExtraCount++;
                    highestDuplicateId = Math.max(highestDuplicateId, current.id);
                }
            }
        }

        String signature = duplicateExtraCount + ":" + highestDuplicateId;
        boolean initialized = preferences.getBoolean(KEY_DUPLICATE_INITIALIZED, false);
        String previousSignature = preferences.getString(KEY_DUPLICATE_SIGNATURE, "");

        preferences.edit()
                .putBoolean(KEY_DUPLICATE_INITIALIZED, true)
                .putString(KEY_DUPLICATE_SIGNATURE, signature)
                .apply();

        if (!initialized || duplicateExtraCount <= 0 || signature.equals(previousSignature)) {
            return;
        }

        NotificationHelper.showReminder(
                context,
                61002,
                "Possible duplicate transaction",
                duplicateExtraCount + " recent matching entr"
                        + (duplicateExtraCount == 1 ? "y needs" : "ies need")
                        + " review. No entry was deleted automatically.",
                TransactionsActivity.class
        );
    }

    private void checkCreditCardDueDates(
            @NonNull Context context,
            @NonNull SharedPreferences preferences,
            List<CreditCard> cards,
            List<AccountBalance> balances
    ) {
        if (cards == null || cards.isEmpty()) {
            return;
        }

        Map<String, Double> balanceByAccount = new HashMap<>();
        if (balances != null) {
            for (AccountBalance balance : balances) {
                if (balance != null && balance.name != null) {
                    balanceByAccount.put(
                            balance.name.trim().toLowerCase(Locale.ROOT),
                            balance.currentBalance
                    );
                }
            }
        }

        Calendar today = Calendar.getInstance();
        clearTime(today);
        String dayKey = new SimpleDateFormat("yyyyMMdd", Locale.US).format(today.getTime());

        for (CreditCard card : cards) {
            if (card == null) continue;

            double accountBalance = balanceByAccount.getOrDefault(
                    safe(card.getAccountName()).toLowerCase(Locale.ROOT),
                    0d
            );
            double outstanding = Math.max(0d, -accountBalance);

            if (outstanding <= 0.005d) {
                continue;
            }

            Calendar due = nextDueDate(card.getDueDay(), today);
            long difference = due.getTimeInMillis() - today.getTimeInMillis();
            int daysLeft = (int) Math.round(difference / 86400000d);
            int reminderWindow = Math.max(3, card.getReminderDays());

            if (daysLeft < 0 || daysLeft > reminderWindow) {
                continue;
            }

            String sentKey = "card_due_" + card.getId() + "_" + dayKey;
            if (preferences.getBoolean(sentKey, false)) {
                continue;
            }

            String title = daysLeft == 0
                    ? "Credit card payment due today"
                    : "Credit card payment due soon";

            String message = safe(card.getName())
                    + " • Outstanding " + money(outstanding)
                    + (daysLeft == 0
                    ? " • Due today"
                    : " • Due in " + daysLeft + " day(s)");

            NotificationHelper.showReminder(
                    context,
                    62000 + card.getId(),
                    title,
                    message,
                    CreditCardActivity.class
            );

            preferences.edit().putBoolean(sentKey, true).apply();
        }

        cleanupOldDailyKeys(preferences, dayKey);
    }

    private void cleanupOldDailyKeys(
            @NonNull SharedPreferences preferences,
            @NonNull String currentDayKey
    ) {
        SharedPreferences.Editor editor = preferences.edit();
        boolean changed = false;
        for (String key : preferences.getAll().keySet()) {
            if (key.startsWith("card_due_") && !key.endsWith("_" + currentDayKey)) {
                editor.remove(key);
                changed = true;
            }
        }
        if (changed) {
            editor.apply();
        }
    }

    @NonNull
    private Calendar nextDueDate(int rawDay, @NonNull Calendar today) {
        int dueDay = Math.max(1, Math.min(31, rawDay));
        Calendar due = (Calendar) today.clone();
        due.set(Calendar.DAY_OF_MONTH, Math.min(dueDay, due.getActualMaximum(Calendar.DAY_OF_MONTH)));
        clearTime(due);

        if (due.before(today)) {
            due.add(Calendar.MONTH, 1);
            due.set(Calendar.DAY_OF_MONTH, Math.min(dueDay, due.getActualMaximum(Calendar.DAY_OF_MONTH)));
        }
        return due;
    }

    private Date parseDate(String value) {
        String clean = safe(value);
        if (clean.isEmpty()) return null;

        for (String pattern : DATE_PATTERNS) {
            SimpleDateFormat formatter = new SimpleDateFormat(pattern, Locale.US);
            formatter.setLenient(false);
            ParsePosition position = new ParsePosition(0);
            Date parsed = formatter.parse(clean, position);
            if (parsed != null && position.getIndex() == clean.length()) {
                return parsed;
            }
        }
        return null;
    }

    private void clearTime(@NonNull Calendar calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
    }

    private long cents(double amount) {
        return Math.round(Math.abs(amount) * 100d);
    }

    @NonNull
    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    @NonNull
    private String money(double amount) {
        NumberFormat format = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));
        format.setMaximumFractionDigits(0);
        return format.format(Math.abs(amount));
    }

    private static final class TransactionPoint {
        final int id;
        final long time;

        TransactionPoint(int id, long time) {
            this.id = id;
            this.time = time;
        }
    }
}
