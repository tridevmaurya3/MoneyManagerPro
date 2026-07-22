package com.example.moneymanagerpro.utils;

import android.content.Context;

import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

public class BudgetAlertScheduler {

    private static final String DAILY_WORK_NAME =
            "daily_budget_alert_work";

    private static final String QUICK_CHECK_WORK_NAME =
            "quick_budget_alert_check";

    private BudgetAlertScheduler() {
    }

    public static void schedule(Context context) {
        OneTimeWorkRequest quickCheck =
                new OneTimeWorkRequest.Builder(
                        BudgetAlertWorker.class
                ).build();

        WorkManager.getInstance(context)
                .enqueueUniqueWork(
                        QUICK_CHECK_WORK_NAME,
                        ExistingWorkPolicy.REPLACE,
                        quickCheck
                );

        PeriodicWorkRequest dailyCheck =
                new PeriodicWorkRequest.Builder(
                        BudgetAlertWorker.class,
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