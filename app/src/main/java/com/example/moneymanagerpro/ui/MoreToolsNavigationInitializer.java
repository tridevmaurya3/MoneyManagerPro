package com.example.moneymanagerpro.ui;

import android.app.Activity;
import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.activities.DashboardActivity;
import com.example.moneymanagerpro.activities.MoreFeaturesActivity;

/**
 * Keeps Dashboard → More Financial Tools on one permanent, testable screen.
 *
 * DashboardActivity previously opened a dynamically created BottomSheetDialog.
 * Dialog windows are outside the Activity decor view, so runtime view injection
 * could not reliably add Financial Notifications. This initializer replaces the
 * dashboard button action with MoreFeaturesActivity, whose menu permanently
 * contains SMS & Notifications → Financial Notifications.
 */
public final class MoreToolsNavigationInitializer extends ContentProvider {

    @Override
    public boolean onCreate() {
        Context context = getContext();

        if (context == null) {
            return true;
        }

        Application application =
                (Application) context.getApplicationContext();

        application.registerActivityLifecycleCallbacks(
                new Application.ActivityLifecycleCallbacks() {
                    @Override
                    public void onActivityCreated(
                            @NonNull Activity activity,
                            @Nullable Bundle savedInstanceState
                    ) {
                        installNavigation(activity);
                    }

                    @Override
                    public void onActivityResumed(
                            @NonNull Activity activity
                    ) {
                        installNavigation(activity);
                    }

                    @Override
                    public void onActivityStarted(@NonNull Activity activity) {
                    }

                    @Override
                    public void onActivityPaused(@NonNull Activity activity) {
                    }

                    @Override
                    public void onActivityStopped(@NonNull Activity activity) {
                    }

                    @Override
                    public void onActivitySaveInstanceState(
                            @NonNull Activity activity,
                            @NonNull Bundle outState
                    ) {
                    }

                    @Override
                    public void onActivityDestroyed(@NonNull Activity activity) {
                    }
                }
        );

        return true;
    }

    private static void installNavigation(
            @NonNull Activity activity
    ) {
        if (!(activity instanceof DashboardActivity)) {
            return;
        }

        View moreToolsButton =
                activity.findViewById(R.id.btnMoreFeatures);

        if (moreToolsButton == null) {
            return;
        }

        moreToolsButton.setOnClickListener(view -> {
            Intent intent =
                    new Intent(
                            activity,
                            MoreFeaturesActivity.class
                    );

            activity.startActivity(intent);
        });
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
