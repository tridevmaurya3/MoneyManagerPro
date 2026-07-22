package com.example.moneymanagerpro.utils;

import android.content.Context;

import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

public class LoanReminderScheduler {

    private static final String DAILY_WORK_NAME =
            "daily_loan_reminder_work";

    private static final String QUICK_CHECK_WORK_NAME =
            "quick_loan_reminder_check";

    private LoanReminderScheduler() {
    }

    public static void schedule(Context context) {
        OneTimeWorkRequest quickCheck =
                new OneTimeWorkRequest.Builder(
                        LoanReminderWorker.class
                ).build();

        WorkManager.getInstance(context)
                .enqueueUniqueWork(
                        QUICK_CHECK_WORK_NAME,
                        ExistingWorkPolicy.REPLACE,
                        quickCheck
                );

        PeriodicWorkRequest dailyCheck =
                new PeriodicWorkRequest.Builder(
                        LoanReminderWorker.class,
                        24,
                        TimeUnit.HOURS
                ).build();

        WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                        DAILY_WORK_NAME,
                        ExistingPeriodicWorkPolicy.KEEP,
                        dailyCheck
                );
    }
}