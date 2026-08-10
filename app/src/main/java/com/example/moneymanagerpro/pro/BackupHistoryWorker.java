package com.example.moneymanagerpro.pro;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.concurrent.TimeUnit;

/** Periodically converts a changed verified offline backup into a restore point. */
public final class BackupHistoryWorker extends Worker {

    private static final String PERIODIC_WORK = "backup_security_pro_history";
    private static final String QUICK_WORK = "backup_security_pro_quick";

    public BackupHistoryWorker(
            @NonNull Context context,
            @NonNull WorkerParameters workerParams
    ) {
        super(context, workerParams);
    }

    public static void schedule(@NonNull Context context) {
        Context appContext = context.getApplicationContext();

        OneTimeWorkRequest quick = new OneTimeWorkRequest.Builder(
                BackupHistoryWorker.class
        ).build();

        WorkManager.getInstance(appContext).enqueueUniqueWork(
                QUICK_WORK,
                ExistingWorkPolicy.REPLACE,
                quick
        );

        PeriodicWorkRequest periodic = new PeriodicWorkRequest.Builder(
                BackupHistoryWorker.class,
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
            new BackupHistoryManager(getApplicationContext())
                    .captureCheckpoints(false);
            return Result.success();
        } catch (SecurityException exception) {
            // A selected SAF folder may be temporarily unavailable. A later app
            // run/worker can retry without discarding the saved folder setting.
            return Result.retry();
        } catch (Exception exception) {
            return Result.success();
        }
    }
}
