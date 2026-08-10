package com.example.moneymanagerpro.widget;

import android.app.Activity;
import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Refreshes the complete widget pack when finance data may have changed. */
public final class WidgetDataSyncInitializer extends ContentProvider {

    @Override
    public boolean onCreate() {
        if (getContext() == null) return false;

        Application app = (Application) getContext().getApplicationContext();
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityPaused(@NonNull Activity activity) {
                String name = activity.getClass().getSimpleName();
                if (shouldRefreshAfter(name)) {
                    BaseFinanceWidgetProvider.requestRefreshAll(activity.getApplicationContext());
                }
            }

            @Override public void onActivityCreated(@NonNull Activity a, @Nullable Bundle b) {}
            @Override public void onActivityStarted(@NonNull Activity a) {}
            @Override public void onActivityResumed(@NonNull Activity a) {}
            @Override public void onActivityStopped(@NonNull Activity a) {}
            @Override public void onActivitySaveInstanceState(@NonNull Activity a, @NonNull Bundle b) {}
            @Override public void onActivityDestroyed(@NonNull Activity a) {}
        });
        return true;
    }

    private boolean shouldRefreshAfter(@NonNull String activityName) {
        return activityName.startsWith("Add")
                || activityName.startsWith("Edit")
                || activityName.contains("Transaction")
                || activityName.contains("Transfer")
                || activityName.contains("CreditCard")
                || activityName.contains("Loan")
                || activityName.contains("Subscription")
                || activityName.contains("Recurring")
                || activityName.contains("Account")
                || activityName.contains("Budget");
    }

    @Nullable @Override public Cursor query(@NonNull Uri u, @Nullable String[] p, @Nullable String s, @Nullable String[] a, @Nullable String o) { return null; }
    @Nullable @Override public String getType(@NonNull Uri u) { return null; }
    @Nullable @Override public Uri insert(@NonNull Uri u, @Nullable ContentValues v) { return null; }
    @Override public int delete(@NonNull Uri u, @Nullable String s, @Nullable String[] a) { return 0; }
    @Override public int update(@NonNull Uri u, @Nullable ContentValues v, @Nullable String s, @Nullable String[] a) { return 0; }
}
