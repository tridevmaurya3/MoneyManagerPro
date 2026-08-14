package com.example.moneymanagerpro.cloud;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

/** Debounced + periodic scheduler for encrypted integration recovery snapshots. */
public final class TridevIntegrationCloudScheduler {

    private static final String UNIQUE_ONE_TIME = "tridev_integration_cloud_sync_once_v1";
    private static final String UNIQUE_PERIODIC = "tridev_integration_cloud_sync_periodic_v1";

    private TridevIntegrationCloudScheduler() { }

    /**
     * Replaces any not-yet-run snapshot with a fresh one, effectively debouncing
     * bursts of incoming SMS/family/loan events.
     */
    public static void scheduleSoon(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(
                TridevIntegrationCloudWorker.class)
                .setConstraints(constraints)
                .setInitialDelay(2, TimeUnit.MINUTES)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build();

        WorkManager.getInstance(appContext).enqueueUniqueWork(
                UNIQUE_ONE_TIME,
                ExistingWorkPolicy.REPLACE,
                request);
    }

    /** Daily recovery snapshot is a fallback even if no foreground screen opens. */
    public static void ensurePeriodic(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                TridevIntegrationCloudWorker.class,
                24,
                TimeUnit.HOURS)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build();

        WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                request);
    }
}
