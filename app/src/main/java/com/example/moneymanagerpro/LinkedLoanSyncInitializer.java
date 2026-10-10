package com.example.moneymanagerpro;

import android.app.Activity;
import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.InvalidationTracker;
import com.example.moneymanagerpro.database.DatabaseClient;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Notification-driven snapshots, plus foreground retries if the companion was offline. */
public final class LinkedLoanSyncInitializer extends ContentProvider {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService WORK = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean RUNNING = new AtomicBoolean();
    private static final AtomicBoolean AGAIN = new AtomicBoolean();
    private static volatile String lastError = "";
    private Context app;
    private int started;
    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (started == 0) return;
            request(app); MAIN.postDelayed(this, 10_000);
        }
    };
    public static String lastError() { return lastError; }
    public static void request(Context context) {
        Context app = context.getApplicationContext();
        if (!RUNNING.compareAndSet(false, true)) { AGAIN.set(true); return; }
        WORK.execute(() -> {
            try {
                do {
                    AGAIN.set(false);
                    try { LinkedLoanBridge.sync(app); lastError = ""; }
                    catch (RuntimeException unavailable) {
                        lastError = unavailable.getMessage() == null ? "LoanManager sync unavailable" : unavailable.getMessage();
                    }
                } while (AGAIN.get());
            } finally {
                RUNNING.set(false);
                if (AGAIN.getAndSet(false)) request(app);
            }
        });
    }
    @Override public boolean onCreate() {
        Context context = getContext(); if (context == null) return false;
        app = context.getApplicationContext();
        try {
            app.getContentResolver().registerContentObserver(LinkedLoanBridge.URI, true, new ContentObserver(MAIN) {
                @Override public void onChange(boolean selfChange) { request(app); }
            });
        } catch (RuntimeException notInstalled) { /* Foreground retry also covers later installation. */ }
        WORK.execute(() -> DatabaseClient.getInstance(app).getAppDatabase().getInvalidationTracker().addObserver(
                new InvalidationTracker.Observer("loans") {
                    @Override public void onInvalidated(@NonNull Set<String> tables) { request(app); }
                }));
        if (app instanceof Application) ((Application) app).registerActivityLifecycleCallbacks(
                new Application.ActivityLifecycleCallbacks() {
                    @Override public void onActivityCreated(@NonNull Activity a, @Nullable Bundle b) { }
                    @Override public void onActivityStarted(@NonNull Activity a) {
                        if (++started == 1) { MAIN.removeCallbacks(poll); MAIN.post(poll); }
                    }
                    @Override public void onActivityStopped(@NonNull Activity a) {
                        started = Math.max(0, started - 1); if (started == 0) MAIN.removeCallbacks(poll);
                    }
                    @Override public void onActivityResumed(@NonNull Activity a) { }
                    @Override public void onActivityPaused(@NonNull Activity a) { }
                    @Override public void onActivitySaveInstanceState(@NonNull Activity a, @NonNull Bundle b) { }
                    @Override public void onActivityDestroyed(@NonNull Activity a) { }
                });
        return true;
    }
    @Nullable @Override public Cursor query(@NonNull Uri u, @Nullable String[] p, @Nullable String s, @Nullable String[] a, @Nullable String o) { return null; }
    @Nullable @Override public String getType(@NonNull Uri u) { return null; }
    @Nullable @Override public Uri insert(@NonNull Uri u, @Nullable ContentValues v) { return null; }
    @Override public int delete(@NonNull Uri u, @Nullable String s, @Nullable String[] a) { return 0; }
    @Override public int update(@NonNull Uri u, @Nullable ContentValues v, @Nullable String s, @Nullable String[] a) { return 0; }
}
