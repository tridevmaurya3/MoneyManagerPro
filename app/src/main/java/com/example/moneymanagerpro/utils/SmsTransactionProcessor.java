package com.example.moneymanagerpro.utils;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.activities.SmsTransactionActivity;
import com.example.moneymanagerpro.database.AppDatabase;
import com.example.moneymanagerpro.database.DatabaseClient;
import com.example.moneymanagerpro.model.Account;
import com.example.moneymanagerpro.model.Category;
import com.example.moneymanagerpro.model.Transaction;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class SmsTransactionProcessor {

    private static final String CHANNEL_ID =
            "sms_transaction_sync";

    private SmsTransactionProcessor() {
    }

    public static void processAsync(
            Context context,
            String sender,
            String message,
            long receivedAt
    ) {
        Context appContext =
                context.getApplicationContext();

        new Thread(() -> process(
                appContext,
                sender,
                message,
                receivedAt
        )).start();
    }

    private static void process(
            Context context,
            String sender,
            String message,
            long receivedAt
    ) {
        if (!SmsImportStore.isEnabled(context)) {
            return;
        }

        String fingerprint =
                SmsImportStore.fingerprint(
                        sender,
                        receivedAt,
                        message
                );

        if (SmsImportStore.isProcessed(
                context,
                fingerprint
        )) {
            return;
        }

        SmsTransactionParser.Result result =
                SmsTransactionParser.parse(message);

        if (!result.isFinancialTransaction()) {
            return;
        }

        SmsImportStore.PendingTransaction pending =
                createPending(
                        fingerprint,
                        sender,
                        receivedAt,
                        result
                );

        boolean saved = false;

        if (SmsImportStore.isAutoAddEnabled(context)
                && result.isHighConfidence()) {
            saved = tryAutoSave(
                    context,
                    pending
            );
        }

        if (saved) {
            SmsImportStore.markProcessed(
                    context,
                    fingerprint
            );
            showNotification(
                    context,
                    "SMS transaction added",
                    formatSummary(pending)
            );
        } else {
            SmsImportStore.addPending(
                    context,
                    pending
            );
            showNotification(
                    context,
                    "Review detected transaction",
                    formatSummary(pending)
            );
        }
    }

    public static boolean savePending(
            Context context,
            SmsImportStore.PendingTransaction pending,
            String selectedCategory,
            String selectedAccount
    ) {
        if (pending == null
                || pending.amount <= 0
                || selectedCategory == null
                || selectedCategory.trim().isEmpty()
                || selectedAccount == null
                || selectedAccount.trim().isEmpty()) {
            return false;
        }

        Transaction transaction =
                createTransaction(
                        pending,
                        selectedCategory.trim(),
                        selectedAccount.trim()
                );

        long id = DatabaseClient
                .getInstance(
                        context.getApplicationContext()
                )
                .getAppDatabase()
                .transactionDao()
                .insert(transaction);

        if (id <= 0) {
            return false;
        }

        SmsImportStore.markProcessed(
                context,
                pending.fingerprint
        );
        SmsImportStore.removePending(
                context,
                pending.fingerprint
        );
        return true;
    }

    private static boolean tryAutoSave(
            Context context,
            SmsImportStore.PendingTransaction pending
    ) {
        AppDatabase database =
                DatabaseClient.getInstance(context)
                        .getAppDatabase();

        List<Account> accounts =
                database.accountDao()
                        .getAllAccounts();
        List<Category> categories =
                database.categoryDao()
                        .getAllCategories();

        String account =
                findMatchingAccount(
                        accounts,
                        pending.bank,
                        pending.sender
                );
        String category =
                findMatchingCategory(
                        categories,
                        pending.type,
                        pending.category
                );

        if (account.isEmpty()
                || category.isEmpty()) {
            return false;
        }

        return savePending(
                context,
                pending,
                category,
                account
        );
    }

    public static String findMatchingAccount(
            List<Account> accounts,
            String bank,
            String sender
    ) {
        String bankKey = normalize(bank);
        String senderKey = normalize(sender);

        for (Account account : accounts) {
            String accountName =
                    safe(account.getName());
            String accountKey =
                    normalize(accountName);

            if (!bankKey.isEmpty()
                    && (accountKey.contains(bankKey)
                    || bankKey.contains(accountKey))) {
                return accountName;
            }

            if (bankKey.isEmpty()
                    && senderKey.length() >= 3
                    && accountKey.length() >= 3
                    && (senderKey.contains(accountKey)
                    || accountKey.contains(senderKey))) {
                return accountName;
            }
        }

        return "";
    }

    public static String findMatchingCategory(
            List<Category> categories,
            String transactionType,
            String suggestion
    ) {
        String requiredType =
                "INCOME".equalsIgnoreCase(
                        transactionType
                )
                        ? "Income"
                        : "Expense";
        String fallback =
                "Income".equals(requiredType)
                        ? "Other Income"
                        : "Other Expense";

        String fallbackMatch = "";

        for (Category category : categories) {
            if (!requiredType.equalsIgnoreCase(
                    safe(category.getType())
            )) {
                continue;
            }

            String name = safe(category.getName());

            if (name.equalsIgnoreCase(
                    safe(suggestion)
            )) {
                return name;
            }

            if (name.equalsIgnoreCase(fallback)) {
                fallbackMatch = name;
            }
        }

        return fallbackMatch;
    }

    private static Transaction createTransaction(
            SmsImportStore.PendingTransaction pending,
            String category,
            String account
    ) {
        Transaction transaction = new Transaction();
        transaction.setType(pending.type);
        transaction.setAmount(pending.amount);
        transaction.setCategory(category);
        transaction.setAccount(account);
        transaction.setDate(
                new SimpleDateFormat(
                        "yyyy-MM-dd HH:mm",
                        Locale.US
                ).format(
                        new Date(
                                pending.receivedAt > 0
                                        ? pending.receivedAt
                                        : System.currentTimeMillis()
                        )
                )
        );

        StringBuilder note =
                new StringBuilder("SMS");

        append(note, "Sender", pending.sender);
        append(note, "Merchant", pending.merchant);
        append(note, "Bank", pending.bank);
        append(note, "Ref", pending.reference);

        transaction.setNote(
                note.length() > 250
                        ? note.substring(0, 250)
                        : note.toString()
        );
        return transaction;
    }

    private static SmsImportStore.PendingTransaction
    createPending(
            String fingerprint,
            String sender,
            long receivedAt,
            SmsTransactionParser.Result result
    ) {
        SmsImportStore.PendingTransaction pending =
                new SmsImportStore.PendingTransaction();
        pending.fingerprint = fingerprint;
        pending.sender = safe(sender);
        pending.receivedAt = receivedAt;
        pending.amount = result.getAmount();
        pending.type =
                result.getType().name();
        pending.bank = result.getBank();
        pending.merchant = result.getMerchant();
        pending.reference = result.getReference();
        pending.category =
                result.getCategorySuggestion();
        pending.confidence =
                result.getConfidence();
        return pending;
    }

    private static void append(
            StringBuilder note,
            String label,
            String value
    ) {
        if (!safe(value).isEmpty()) {
            note.append(" • ")
                    .append(label)
                    .append(": ")
                    .append(safe(value));
        }
    }

    private static String formatSummary(
            SmsImportStore.PendingTransaction pending
    ) {
        return ("INCOME".equals(pending.type)
                ? "Income "
                : "Expense ")
                + "₹"
                + String.format(
                Locale.US,
                "%.2f",
                pending.amount
        )
                + " • "
                + pending.category;
    }

    private static void showNotification(
            Context context,
            String title,
            String message
    ) {
        createChannel(context);

        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        Intent intent = new Intent(
                context,
                SmsTransactionActivity.class
        );
        PendingIntent pendingIntent =
                PendingIntent.getActivity(
                        context,
                        8801,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT
                                | PendingIntent.FLAG_IMMUTABLE
                );

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(
                        context,
                        CHANNEL_ID
                )
                        .setSmallIcon(
                                R.mipmap.ic_launcher
                        )
                        .setContentTitle(title)
                        .setContentText(message)
                        .setStyle(
                                new NotificationCompat
                                        .BigTextStyle()
                                        .bigText(message)
                        )
                        .setContentIntent(pendingIntent)
                        .setAutoCancel(true)
                        .setPriority(
                                NotificationCompat
                                        .PRIORITY_HIGH
                        );

        NotificationManagerCompat.from(context)
                .notify(
                        (int) (
                                System.currentTimeMillis()
                                        % Integer.MAX_VALUE
                        ),
                        builder.build()
                );
    }

    private static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT < 26) {
            return;
        }

        NotificationManager manager =
                context.getSystemService(
                        NotificationManager.class
                );

        if (manager != null) {
            manager.createNotificationChannel(
                    new NotificationChannel(
                            CHANNEL_ID,
                            "SMS Transaction Sync",
                            NotificationManager
                                    .IMPORTANCE_HIGH
                    )
            );
        }
    }

    private static String normalize(String value) {
        return safe(value)
                .toLowerCase(Locale.US)
                .replaceAll("[^a-z0-9]", "")
                .replace("bank", "");
    }

    private static String safe(String value) {
        return value == null
                ? ""
                : value.trim();
    }
}
