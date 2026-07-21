package com.example.moneymanagerpro.utils;

import android.content.Context;

import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

public class ReminderScheduler {

    private static final String BILL_REMINDER_WORK = "bill_reminder_work";

    public static void scheduleDaily(Context context) {
        long initialDelay = getInitialDelay();

        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                BillReminderWorker.class,
                24,
                TimeUnit.HOURS
        )
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .build();

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                BILL_REMINDER_WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                request
        );
    }

    private static long getInitialDelay() {
        Calendar now = Calendar.getInstance();

        Calendar nextReminderTime = Calendar.getInstance();
        nextReminderTime.set(Calendar.HOUR_OF_DAY, 9);
        nextReminderTime.set(Calendar.MINUTE, 0);
        nextReminderTime.set(Calendar.SECOND, 0);
        nextReminderTime.set(Calendar.MILLISECOND, 0);

        if (nextReminderTime.before(now)) {
            nextReminderTime.add(Calendar.DAY_OF_MONTH, 1);
        }

        return nextReminderTime.getTimeInMillis() - now.getTimeInMillis();
    }
}