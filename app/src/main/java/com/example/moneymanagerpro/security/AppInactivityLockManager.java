package com.example.moneymanagerpro.security;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.view.WindowCallbackWrapper;

import com.example.moneymanagerpro.activities.AuthenticationActivity;
import com.example.moneymanagerpro.activities.PinActivity;
import com.example.moneymanagerpro.activities.SplashActivity;
import com.google.firebase.auth.FirebaseAuth;

import java.lang.ref.WeakReference;

/**
 * Locks the application after a configurable period without touch, keyboard
 * or pointer interaction.
 *
 * The lock is active only when a valid four-digit PIN is enabled. Background
 * time also counts as inactivity, but the PIN screen is opened only when the
 * application becomes active again.
 */
public final class AppInactivityLockManager {

    public static final String SECURITY_PREFERENCES =
            "MoneyManagerSecurity";

    public static final String KEY_AUTO_LOCK_MINUTES =
            "auto_lock_inactivity_minutes";

    public static final int DEFAULT_AUTO_LOCK_MINUTES =
            2;

    private static final String KEY_PIN =
            "app_pin";

    private static final String KEY_PIN_ENABLED =
            "pin_enabled";

    private static final String KEY_SETUP_COMPLETE =
            "pin_setup_complete";

    private static final String EXTRA_AUTO_LOCK_REASON =
            "money_manager_auto_lock_reason";

    private final Context applicationContext;

    private final Handler mainHandler =
            new Handler(
                    Looper.getMainLooper()
            );

    private WeakReference<Activity> activeActivity =
            new WeakReference<>(null);

    private long lastInteractionElapsedRealtime =
            0L;

    private boolean lockNavigationInProgress =
            false;

    private final Runnable timeoutRunnable =
            () -> {
                Activity activity =
                        activeActivity.get();

                if (activity != null) {
                    lockNow(
                            activity
                    );
                }
            };

    public AppInactivityLockManager(
            @NonNull Context context
    ) {
        applicationContext =
                context.getApplicationContext();
    }

    public void onActivityResumed(
            @NonNull Activity activity
    ) {
        activeActivity =
                new WeakReference<>(
                        activity
                );

        if (isUnlockOrEntryActivity(activity)) {
            lockNavigationInProgress = false;
            markInteractionNow();
            cancelTimeout();
            return;
        }

        if (!isProtectionEnabled()) {
            lockNavigationInProgress = false;
            markInteractionNow();
            cancelTimeout();
            return;
        }

        installInteractionCallback(
                activity
        );

        long now =
                SystemClock.elapsedRealtime();

        if (lastInteractionElapsedRealtime <= 0L) {
            lastInteractionElapsedRealtime = now;
        }

        long elapsed =
                Math.max(
                        0L,
                        now - lastInteractionElapsedRealtime
                );

        long timeoutMillis =
                getTimeoutMillis(
                        activity
                );

        if (elapsed >= timeoutMillis) {
            lockNow(activity);
            return;
        }

        scheduleTimeout(
                timeoutMillis - elapsed
        );
    }

    public void onActivityPaused(
            @NonNull Activity activity
    ) {
        Activity current =
                activeActivity.get();

        if (current == activity) {
            cancelTimeout();
        }
    }

    public void onActivityDestroyed(
            @NonNull Activity activity
    ) {
        Activity current =
                activeActivity.get();

        if (current == activity) {
            activeActivity.clear();
            cancelTimeout();
        }
    }

    public void onUserInteraction(
            @NonNull Activity activity
    ) {
        Activity current =
                activeActivity.get();

        if (current != activity
                || isUnlockOrEntryActivity(activity)
                || !isProtectionEnabled()
                || lockNavigationInProgress) {

            return;
        }

        markInteractionNow();

        scheduleTimeout(
                getTimeoutMillis(
                        activity
                )
        );
    }

    private void installInteractionCallback(
            @NonNull Activity activity
    ) {
        Window window =
                activity.getWindow();

        if (window == null) {
            return;
        }

        Window.Callback currentCallback =
                window.getCallback();

        if (currentCallback == null
                || currentCallback
                instanceof InteractionWindowCallback) {

            return;
        }

        window.setCallback(
                new InteractionWindowCallback(
                        currentCallback,
                        activity,
                        this
                )
        );
    }

    private void scheduleTimeout(
            long delayMillis
    ) {
        cancelTimeout();

        if (delayMillis <= 0L) {
            delayMillis = 1L;
        }

        mainHandler.postDelayed(
                timeoutRunnable,
                delayMillis
        );
    }

    private void cancelTimeout() {
        mainHandler.removeCallbacks(
                timeoutRunnable
        );
    }

    private void markInteractionNow() {
        lastInteractionElapsedRealtime =
                SystemClock.elapsedRealtime();
    }

    private void lockNow(
            @NonNull Activity activity
    ) {
        if (lockNavigationInProgress
                || activity.isFinishing()
                || activity.isDestroyed()
                || isUnlockOrEntryActivity(activity)
                || !isProtectionEnabled()) {

            return;
        }

        lockNavigationInProgress = true;
        cancelTimeout();
        markInteractionNow();

        Intent lockIntent =
                new Intent(
                        activity,
                        PinActivity.class
                );

        lockIntent.putExtra(
                EXTRA_AUTO_LOCK_REASON,
                true
        );

        lockIntent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK
        );

        activity.startActivity(
                lockIntent
        );
    }

    private boolean isProtectionEnabled() {
        if (FirebaseAuth
                .getInstance()
                .getCurrentUser() == null) {

            return false;
        }

        SharedPreferences preferences =
                applicationContext.getSharedPreferences(
                        SECURITY_PREFERENCES,
                        Context.MODE_PRIVATE
                );

        boolean setupComplete =
                preferences.getBoolean(
                        KEY_SETUP_COMPLETE,
                        false
                );

        boolean pinEnabled =
                preferences.getBoolean(
                        KEY_PIN_ENABLED,
                        false
                );

        String pin =
                preferences.getString(
                        KEY_PIN,
                        ""
                );

        return setupComplete
                && pinEnabled
                && pin != null
                && pin.matches("\\d{4}");
    }

    private boolean isUnlockOrEntryActivity(
            @NonNull Activity activity
    ) {
        return activity instanceof PinActivity
                || activity instanceof AuthenticationActivity
                || activity instanceof SplashActivity;
    }

    public static int getTimeoutMinutes(
            @NonNull Context context
    ) {
        int savedMinutes =
                context.getSharedPreferences(
                        SECURITY_PREFERENCES,
                        Context.MODE_PRIVATE
                )
                        .getInt(
                                KEY_AUTO_LOCK_MINUTES,
                                DEFAULT_AUTO_LOCK_MINUTES
                        );

        if (savedMinutes == 1
                || savedMinutes == 2
                || savedMinutes == 5
                || savedMinutes == 10) {

            return savedMinutes;
        }

        return DEFAULT_AUTO_LOCK_MINUTES;
    }

    public static boolean saveTimeoutMinutes(
            @NonNull Context context,
            int minutes
    ) {
        int safeMinutes;

        if (minutes == 1
                || minutes == 2
                || minutes == 5
                || minutes == 10) {

            safeMinutes = minutes;

        } else {
            safeMinutes =
                    DEFAULT_AUTO_LOCK_MINUTES;
        }

        return context.getSharedPreferences(
                SECURITY_PREFERENCES,
                Context.MODE_PRIVATE
        )
                .edit()
                .putInt(
                        KEY_AUTO_LOCK_MINUTES,
                        safeMinutes
                )
                .commit();
    }

    private long getTimeoutMillis(
            @NonNull Context context
    ) {
        return getTimeoutMinutes(context)
                * 60L
                * 1000L;
    }

    private static final class InteractionWindowCallback
            extends WindowCallbackWrapper {

        private final WeakReference<Activity> activityReference;
        private final WeakReference<AppInactivityLockManager>
                managerReference;

        private InteractionWindowCallback(
                @NonNull Window.Callback wrapped,
                @NonNull Activity activity,
                @NonNull AppInactivityLockManager manager
        ) {
            super(wrapped);

            activityReference =
                    new WeakReference<>(
                            activity
                    );

            managerReference =
                    new WeakReference<>(
                            manager
                    );
        }

        @Override
        public boolean dispatchTouchEvent(
                MotionEvent event
        ) {
            if (event != null) {
                notifyInteraction();
            }

            return super.dispatchTouchEvent(
                    event
            );
        }

        @Override
        public boolean dispatchGenericMotionEvent(
                MotionEvent event
        ) {
            if (event != null) {
                notifyInteraction();
            }

            return super.dispatchGenericMotionEvent(
                    event
            );
        }

        @Override
        public boolean dispatchKeyEvent(
                KeyEvent event
        ) {
            if (event != null
                    && event.getAction()
                    == KeyEvent.ACTION_DOWN) {

                notifyInteraction();
            }

            return super.dispatchKeyEvent(
                    event
            );
        }

        private void notifyInteraction() {
            Activity activity =
                    activityReference.get();

            AppInactivityLockManager manager =
                    managerReference.get();

            if (activity != null
                    && manager != null) {

                manager.onUserInteraction(
                        activity
                );
            }
        }
    }
}
