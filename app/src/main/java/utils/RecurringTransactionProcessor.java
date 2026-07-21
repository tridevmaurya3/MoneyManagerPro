package com.example.moneymanagerpro.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.example.moneymanagerpro.database.DatabaseClient;
import com.example.moneymanagerpro.model.RecurringTransaction;
import com.example.moneymanagerpro.model.Transaction;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class RecurringTransactionProcessor {

    public interface CompletionCallback {
        void onCompleted(int createdEntries);
    }

    public static void processDueEntries(
            Context context,
            CompletionCallback callback
    ) {
        Context appContext = context.getApplicationContext();

        new Thread(() -> {
            String currentDate = formatDateTime(Calendar.getInstance());

            List<RecurringTransaction> dueEntries = DatabaseClient
                    .getInstance(appContext)
                    .getAppDatabase()
                    .recurringTransactionDao()
                    .getDueRecurringTransactions(currentDate);

            int createdCount = 0;

            for (RecurringTransaction recurringTransaction : dueEntries) {
                int safetyLimit = 0;

                while (recurringTransaction
                        .getNextRunDate()
                        .compareTo(currentDate) <= 0 &&
                        safetyLimit < 1000) {

                    Transaction transaction = new Transaction();
                    transaction.setType(recurringTransaction.getType());
                    transaction.setAmount(recurringTransaction.getAmount());
                    transaction.setCategory(
                            recurringTransaction.getCategory()
                    );
                    transaction.setAccount(
                            recurringTransaction.getAccount()
                    );
                    transaction.setNote(
                            recurringTransaction.getNote()
                    );
                    transaction.setDate(
                            recurringTransaction.getNextRunDate()
                    );

                    DatabaseClient.getInstance(appContext)
                            .getAppDatabase()
                            .transactionDao()
                            .insert(transaction);

                    recurringTransaction.setNextRunDate(
                            calculateNextRunDate(
                                    recurringTransaction.getNextRunDate(),
                                    recurringTransaction.getFrequency()
                            )
                    );

                    createdCount++;
                    safetyLimit++;
                }

                DatabaseClient.getInstance(appContext)
                        .getAppDatabase()
                        .recurringTransactionDao()
                        .update(recurringTransaction);
            }

            int finalCreatedCount = createdCount;

            new Handler(Looper.getMainLooper()).post(() -> {
                if (callback != null) {
                    callback.onCompleted(finalCreatedCount);
                }
            });
        }).start();
    }

    private static String calculateNextRunDate(
            String currentRunDate,
            String frequency
    ) {
        Calendar calendar = Calendar.getInstance();

        try {
            Date parsedDate = new SimpleDateFormat(
                    "yyyy-MM-dd HH:mm",
                    Locale.getDefault()
            ).parse(currentRunDate);

            if (parsedDate != null) {
                calendar.setTime(parsedDate);
            }
        } catch (ParseException exception) {
            calendar = Calendar.getInstance();
        }

        if (frequency.equals("Daily")) {
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        } else if (frequency.equals("Weekly")) {
            calendar.add(Calendar.WEEK_OF_YEAR, 1);
        } else if (frequency.equals("Yearly")) {
            calendar.add(Calendar.YEAR, 1);
        } else {
            calendar.add(Calendar.MONTH, 1);
        }

        return formatDateTime(calendar);
    }

    private static String formatDateTime(Calendar calendar) {
        return new SimpleDateFormat(
                "yyyy-MM-dd HH:mm",
                Locale.getDefault()
        ).format(calendar.getTime());
    }
}