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

import com.example.moneymanagerpro.activities.BackupActivity;
import com.example.moneymanagerpro.activities.HelpActivity;
import com.example.moneymanagerpro.activities.ReportActivity;

import java.util.Map;
import java.util.WeakHashMap;

/** Entry point for the Pro notification, backup, reports, help and UI upgrades. */
public final class ProUpgradeInitializer extends ContentProvider {

    private final Map<Activity, BackupSecurityProController> backupControllers = new WeakHashMap<>();
    private final Map<Activity, ReportsProController> reportControllers = new WeakHashMap<>();
    private final Map<Activity, HelpProController> helpControllers = new WeakHashMap<>();

    @Override
    public boolean onCreate() {
        if (getContext() == null) return false;

        Application application = (Application) getContext().getApplicationContext();

        // App-recorded transaction intelligence only. No SMS inbox permissions.
        SmartFinanceNotificationWorker.schedule(application);
        BackupHistoryWorker.schedule(application);

        application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
            }

            @Override
            public void onActivityStarted(@NonNull Activity activity) {
            }

            @Override
            public void onActivityResumed(@NonNull Activity activity) {
                activity.getWindow().getDecorView().post(() -> FluentProfessionalPolish.apply(activity));

                if (activity instanceof BackupActivity) {
                    activity.getWindow().getDecorView().postDelayed(
                            () -> attachBackup(activity),
                            160L
                    );
                }

                if (activity instanceof ReportActivity) {
                    activity.getWindow().getDecorView().postDelayed(
                            () -> attachReports(activity),
                            160L
                    );
                }

                if (activity instanceof HelpActivity) {
                    activity.getWindow().getDecorView().postDelayed(
                            () -> attachHelp(activity),
                            120L
                    );
                }
            }

            @Override
            public void onActivityPaused(@NonNull Activity activity) {
            }

            @Override
            public void onActivityStopped(@NonNull Activity activity) {
            }

            @Override
            public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
            }

            @Override
            public void onActivityDestroyed(@NonNull Activity activity) {
                backupControllers.remove(activity);
                reportControllers.remove(activity);
                helpControllers.remove(activity);
            }
        });

        return true;
    }

    private void attachBackup(@NonNull Activity activity) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        BackupSecurityProController controller = backupControllers.get(activity);
        if (controller == null) {
            controller = new BackupSecurityProController(activity);
            backupControllers.put(activity, controller);
        }
        controller.attach();
    }

    private void attachReports(@NonNull Activity activity) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        ReportsProController controller = reportControllers.get(activity);
        if (controller == null) {
            controller = new ReportsProController(activity);
            reportControllers.put(activity, controller);
        }
        controller.attach();
    }

    private void attachHelp(@NonNull Activity activity) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        HelpProController controller = helpControllers.get(activity);
        if (controller == null) {
            controller = new HelpProController(activity);
            helpControllers.put(activity, controller);
        }
        controller.attach();
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection,
                        @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        return null;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        return null;
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        return null;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values,
                      @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }
}
