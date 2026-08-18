package com.example.moneymanagerpro.pro;

import android.app.Activity;
import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.work.Configuration;
import androidx.work.WorkManager;

import com.example.moneymanagerpro.activities.BackupActivity;
import com.example.moneymanagerpro.activities.HelpActivity;
import com.example.moneymanagerpro.activities.ReportActivity;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Entry point for the Pro notification, backup, reports, help and UI upgrades.
 *
 * Important startup rule:
 *
 * ContentProvider instances can be created before AndroidX Startup has finished
 * initializing WorkManager. Therefore this provider must never call
 * WorkManager from onCreate(). Background jobs are scheduled only after the
 * first Activity reaches the resumed state, when provider initialization has
 * completed.
 */
public final class ProUpgradeInitializer extends ContentProvider {

    private final Map<Activity, BackupSecurityProController> backupControllers =
            new WeakHashMap<>();

    private final Map<Activity, ReportsProController> reportControllers =
            new WeakHashMap<>();

    private final Map<Activity, HelpProController> helpControllers =
            new WeakHashMap<>();

    private boolean backgroundWorkScheduled = false;

    @Override
    public boolean onCreate() {
        if (getContext() == null) {
            return false;
        }

        Application application =
                (Application) getContext().getApplicationContext();

        /*
         * Do NOT schedule WorkManager jobs here.
         *
         * This ContentProvider has a high initOrder and can run before
         * androidx.startup.InitializationProvider. Calling WorkManager here
         * caused an app-start crash on real devices.
         */
        application.registerActivityLifecycleCallbacks(
                new Application.ActivityLifecycleCallbacks() {

                    @Override
                    public void onActivityCreated(
                            @NonNull Activity activity,
                            @Nullable Bundle savedInstanceState
                    ) {
                    }

                    @Override
                    public void onActivityStarted(
                            @NonNull Activity activity
                    ) {
                    }

                    @Override
                    public void onActivityResumed(
                            @NonNull Activity activity
                    ) {
                        /*
                         * At this point all ContentProviders have completed
                         * startup. WorkManager should normally already be
                         * available. If a device/build has disabled its
                         * automatic initializer, initialize it safely here.
                         */
                        scheduleBackgroundWorkWhenReady(
                                application
                        );

                        activity.getWindow()
                                .getDecorView()
                                .post(() -> {
                                    FluentProfessionalPolish.apply(activity);
                                    CompactFormPolish.apply(activity);
                                });

                        if (activity instanceof BackupActivity) {
                            activity.getWindow()
                                    .getDecorView()
                                    .postDelayed(
                                            () -> attachBackup(activity),
                                            160L
                                    );
                        }

                        if (activity instanceof ReportActivity) {
                            activity.getWindow()
                                    .getDecorView()
                                    .postDelayed(
                                            () -> attachReports(activity),
                                            160L
                                    );
                        }

                        if (activity instanceof HelpActivity) {
                            activity.getWindow()
                                    .getDecorView()
                                    .postDelayed(
                                            () -> attachHelp(activity),
                                            120L
                                    );
                        }
                    }

                    @Override
                    public void onActivityPaused(
                            @NonNull Activity activity
                    ) {
                    }

                    @Override
                    public void onActivityStopped(
                            @NonNull Activity activity
                    ) {
                    }

                    @Override
                    public void onActivitySaveInstanceState(
                            @NonNull Activity activity,
                            @NonNull Bundle outState
                    ) {
                    }

                    @Override
                    public void onActivityDestroyed(
                            @NonNull Activity activity
                    ) {
                        backupControllers.remove(activity);
                        reportControllers.remove(activity);
                        helpControllers.remove(activity);
                    }
                }
        );

        return true;
    }

    /**
     * Schedules the Pro workers exactly once per app process.
     *
     * The method is deliberately fail-safe: a WorkManager initialization
     * problem must never crash the app. If scheduling cannot complete, the
     * boolean remains false and the next Activity resume will retry.
     */
    private synchronized void scheduleBackgroundWorkWhenReady(
            @NonNull Application application
    ) {
        if (backgroundWorkScheduled) {
            return;
        }

        try {
            ensureWorkManagerInitialized(
                    application
            );

            // App-recorded transaction intelligence only. No SMS inbox access.
            SmartFinanceNotificationWorker.schedule(
                    application
            );

            BackupHistoryWorker.schedule(
                    application
            );

            backgroundWorkScheduled = true;

        } catch (Exception ignored) {
            /*
             * Never allow a background scheduler problem to crash startup.
             * A later Activity resume will retry automatically.
             */
            backgroundWorkScheduled = false;
        }
    }

    /**
     * Uses the normal WorkManager instance when AndroidX Startup has already
     * initialized it. If automatic initialization is genuinely unavailable,
     * a default configuration is installed only after provider startup has
     * completed.
     */
    private void ensureWorkManagerInitialized(
            @NonNull Application application
    ) {
        try {
            WorkManager.getInstance(
                    application
            );
            return;

        } catch (IllegalStateException notInitializedYet) {
            // Continue to the safe fallback below.
        }

        try {
            WorkManager.initialize(
                    application,
                    new Configuration.Builder()
                            .build()
            );

        } catch (IllegalStateException alreadyInitialized) {
            /*
             * A race can initialize WorkManager between getInstance() and
             * initialize(). In that case the existing instance is correct.
             */
        }

        // Final verification. Throws only if WorkManager is still unavailable.
        WorkManager.getInstance(
                application
        );
    }

    private void attachBackup(
            @NonNull Activity activity
    ) {
        if (activity.isFinishing()
                || activity.isDestroyed()) {
            return;
        }

        BackupSecurityProController controller =
                backupControllers.get(activity);

        if (controller == null) {
            controller =
                    new BackupSecurityProController(
                            activity
                    );

            backupControllers.put(
                    activity,
                    controller
            );
        }

        controller.attach();
    }

    private void attachReports(
            @NonNull Activity activity
    ) {
        if (activity.isFinishing()
                || activity.isDestroyed()) {
            return;
        }

        ReportsProController controller =
                reportControllers.get(activity);

        if (controller == null) {
            controller =
                    new ReportsProController(
                            activity
                    );

            reportControllers.put(
                    activity,
                    controller
            );
        }

        controller.attach();
    }

    private void attachHelp(
            @NonNull Activity activity
    ) {
        if (activity.isFinishing()
                || activity.isDestroyed()) {
            return;
        }

        HelpProController controller =
                helpControllers.get(activity);

        if (controller == null) {
            controller =
                    new HelpProController(
                            activity
                    );

            helpControllers.put(
                    activity,
                    controller
            );
        }

        controller.attach();
    }

    @Nullable
    @Override
    public Cursor query(
            @NonNull Uri uri,
            @Nullable String[] projection,
            @Nullable String selection,
            @Nullable String[] selectionArgs,
            @Nullable String sortOrder
    ) {
        return null;
    }

    @Nullable
    @Override
    public String getType(
            @NonNull Uri uri
    ) {
        return null;
    }

    @Nullable
    @Override
    public Uri insert(
            @NonNull Uri uri,
            @Nullable ContentValues values
    ) {
        return null;
    }

    @Override
    public int delete(
            @NonNull Uri uri,
            @Nullable String selection,
            @Nullable String[] selectionArgs
    ) {
        return 0;
    }

    @Override
    public int update(
            @NonNull Uri uri,
            @Nullable ContentValues values,
            @Nullable String selection,
            @Nullable String[] selectionArgs
    ) {
        return 0;
    }
}
