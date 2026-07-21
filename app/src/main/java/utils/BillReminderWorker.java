package com.example.moneymanagerpro.utils;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.moneymanagerpro.database.DatabaseClient;
import com.example.moneymanagerpro.model.Subscription;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class BillReminderWorker extends Worker {

    public BillReminderWorker(
            @NonNull Context context,
            @NonNull WorkerParameters workerParameters
    ) {
        super(context, workerParameters);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            List<Subscription> subscriptions = DatabaseClient
                    .getInstance(getApplicationContext())
                    .getAppDatabase()
                    .subscriptionDao()
                    .getActiveSubscriptions();

            for (Subscription subscription : subscriptions) {
                int daysUntilDue = getDaysUntilDue(
                        subscription.getNextDueDate()
                );

                boolean isOverdue = daysUntilDue < 0;
                boolean isDueToday = daysUntilDue == 0;
                boolean isReminderDay = daysUntilDue > 0
                        && daysUntilDue <= subscription.getRemindDays();

                if (isOverdue || isDueToday || isReminderDay) {
                    String message;

                    if (isOverdue) {
                        message = subscription.getName()
                                + " is overdue by "
                                + Math.abs(daysUntilDue)
                                + " day(s). Amount: ₹"
                                + subscription.getAmount();

                    } else if (isDueToday) {
                        message = subscription.getName()
                                + " is due today. Amount: ₹"
                                + subscription.getAmount();

                    } else {
                        message = subscription.getName()
                                + " is due in "
                                + daysUntilDue
                                + " day(s). Amount: ₹"
                                + subscription.getAmount();
                    }

                    NotificationHelper.showBillReminder(
                            getApplicationContext(),
                            subscription.getId(),
                            "Bill Reminder",
                            message
                    );
                }
            }

            return Result.success();

        } catch (Exception exception) {
            return Result.retry();
        }
    }

    private int getDaysUntilDue(String dueDate) {
        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat(
                    "yyyy-MM-dd",
                    Locale.US
            );

            Calendar today = Calendar.getInstance();
            today.set(Calendar.HOUR_OF_DAY, 0);
            today.set(Calendar.MINUTE, 0);
            today.set(Calendar.SECOND, 0);
            today.set(Calendar.MILLISECOND, 0);

            Calendar due = Calendar.getInstance();
            due.setTime(dateFormat.parse(dueDate));
            due.set(Calendar.HOUR_OF_DAY, 0);
            due.set(Calendar.MINUTE, 0);
            due.set(Calendar.SECOND, 0);
            due.set(Calendar.MILLISECOND, 0);

            long difference = due.getTimeInMillis() - today.getTimeInMillis();

            return (int) (difference / (24 * 60 * 60 * 1000));

        } catch (Exception exception) {
            return 0;
        }
    }
}