package com.example.moneymanagerpro.ui;

import android.app.Activity;
import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Map;
import java.util.WeakHashMap;

/** Adds the same filter/export contract to every screen that presents finance data. */
public final class VisibleDataToolsInitializer extends ContentProvider {
    private final Map<Activity, VisibleDataToolsController> controllers = new WeakHashMap<>();

    @Override public boolean onCreate() {
        if (getContext() == null) return false;
        Application app = (Application) getContext().getApplicationContext();
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityResumed(@NonNull Activity activity) {
                VisibleDataToolsController controller = controllers.get(activity);
                if (controller == null) {
                    controller = new VisibleDataToolsController(activity);
                    controllers.put(activity, controller);
                }
                controller.attach();
            }
            @Override public void onActivityDestroyed(@NonNull Activity activity) {
                VisibleDataToolsController controller = controllers.remove(activity);
                if (controller != null) controller.detach();
            }
            @Override public void onActivityCreated(@NonNull Activity a, @Nullable Bundle b) {}
            @Override public void onActivityStarted(@NonNull Activity a) {}
            @Override public void onActivityPaused(@NonNull Activity a) {}
            @Override public void onActivityStopped(@NonNull Activity a) {}
            @Override public void onActivitySaveInstanceState(@NonNull Activity a, @NonNull Bundle b) {}
        });
        return true;
    }

    @Nullable @Override public Cursor query(@NonNull Uri u, @Nullable String[] p, @Nullable String s, @Nullable String[] a, @Nullable String o) { return null; }
    @Nullable @Override public String getType(@NonNull Uri u) { return null; }
    @Nullable @Override public Uri insert(@NonNull Uri u, @Nullable ContentValues v) { return null; }
    @Override public int delete(@NonNull Uri u, @Nullable String s, @Nullable String[] a) { return 0; }
    @Override public int update(@NonNull Uri u, @Nullable ContentValues v, @Nullable String s, @Nullable String[] a) { return 0; }
}
