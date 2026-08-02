package com.example.moneymanagerpro.budget;

import android.app.Activity;
import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.moneymanagerpro.activities.BudgetActivity;

import java.util.Map;
import java.util.WeakHashMap;

public final class AiBudgetPlannerInitializer extends ContentProvider {

    private final Map<Activity, AiBudgetPlannerUiController> controllers =
            new WeakHashMap<>();

    @Override
    public boolean onCreate() {
        if (getContext() == null) {
            return false;
        }

        Application application =
                (Application) getContext().getApplicationContext();

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
                        if (!(activity instanceof BudgetActivity)) {
                            return;
                        }

                        AiBudgetPlannerUiController controller =
                                controllers.get(activity);

                        if (controller == null) {
                            controller =
                                    new AiBudgetPlannerUiController(activity);
                            controllers.put(activity, controller);
                        }

                        controller.attach();
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
                        controllers.remove(activity);
                    }
                }
        );

        return true;
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
    public String getType(@NonNull Uri uri) {
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
