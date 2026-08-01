package com.example.moneymanagerpro.cloud;

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
import com.example.moneymanagerpro.activities.SettingsActivity;
import com.example.moneymanagerpro.security.AppInactivityLockManager;
import com.example.moneymanagerpro.security.AutoLockSettingsController;
import com.example.moneymanagerpro.ui.DashboardVisualEnhancer;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Internal process initializer for focused runtime controllers.
 *
 * It attaches:
 * - permanent cloud backup/account deletion actions,
 * - global inactivity auto-lock monitoring,
 * - configurable auto-lock controls on Settings,
 * - distinct light colors and updated assistant label on Dashboard.
 *
 * The ContentProvider stores no data and exposes no queryable content.
 */
public final class CloudDeleteActionsInitializer
        extends ContentProvider {

    private final Map<Activity, CloudDeleteActionsController>
            cloudDeleteControllers =
            new WeakHashMap<>();

    private final Map<Activity, AutoLockSettingsController>
            autoLockSettingsControllers =
            new WeakHashMap<>();

    @Nullable
    private AppInactivityLockManager inactivityLockManager;

    @Override
    public boolean onCreate() {
        if (getContext() == null) {
            return false;
        }

        Application application =
                (Application) getContext()
                        .getApplicationContext();

        inactivityLockManager =
                new AppInactivityLockManager(
                        application
                );

        application.registerActivityLifecycleCallbacks(
                new Application.ActivityLifecycleCallbacks() {
                    @Override
                    public void onActivityCreated(
                            @NonNull Activity activity,
                            @Nullable Bundle savedInstanceState
                    ) {
                        // Runtime controllers attach after Activity.onResume().
                    }

                    @Override
                    public void onActivityStarted(
                            @NonNull Activity activity
                    ) {
                        // No action required.
                    }

                    @Override
                    public void onActivityResumed(
                            @NonNull Activity activity
                    ) {
                        if (inactivityLockManager != null) {
                            inactivityLockManager.onActivityResumed(
                                    activity
                            );
                        }

                        DashboardVisualEnhancer.apply(
                                activity
                        );

                        if (activity instanceof BackupActivity) {
                            attachCloudDeleteController(
                                    activity
                            );
                        }

                        if (activity instanceof SettingsActivity) {
                            attachAutoLockSettingsController(
                                    activity
                            );
                        }
                    }

                    @Override
                    public void onActivityPaused(
                            @NonNull Activity activity
                    ) {
                        if (inactivityLockManager != null) {
                            inactivityLockManager.onActivityPaused(
                                    activity
                            );
                        }
                    }

                    @Override
                    public void onActivityStopped(
                            @NonNull Activity activity
                    ) {
                        // Background duration is evaluated at next resume.
                    }

                    @Override
                    public void onActivitySaveInstanceState(
                            @NonNull Activity activity,
                            @NonNull Bundle outState
                    ) {
                        // No action required.
                    }

                    @Override
                    public void onActivityDestroyed(
                            @NonNull Activity activity
                    ) {
                        cloudDeleteControllers.remove(
                                activity
                        );

                        autoLockSettingsControllers.remove(
                                activity
                        );

                        DashboardVisualEnhancer.remove(
                                activity
                        );

                        if (inactivityLockManager != null) {
                            inactivityLockManager.onActivityDestroyed(
                                    activity
                            );
                        }
                    }
                }
        );

        return true;
    }

    private void attachCloudDeleteController(
            @NonNull Activity activity
    ) {
        CloudDeleteActionsController controller =
                cloudDeleteControllers.get(
                        activity
                );

        if (controller == null) {
            controller =
                    new CloudDeleteActionsController(
                            activity
                    );

            cloudDeleteControllers.put(
                    activity,
                    controller
            );
        }

        controller.attach();
    }

    private void attachAutoLockSettingsController(
            @NonNull Activity activity
    ) {
        AutoLockSettingsController controller =
                autoLockSettingsControllers.get(
                        activity
                );

        if (controller == null) {
            controller =
                    new AutoLockSettingsController(
                            activity
                    );

            autoLockSettingsControllers.put(
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
