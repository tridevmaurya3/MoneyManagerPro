package com.example.moneymanagerpro.ui;

import android.app.Activity;
import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.text.method.PasswordTransformationMethod;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.activities.PinActivity;
import com.google.android.material.textfield.TextInputEditText;

/** Keeps PIN fields securely masked. SMS and external-notification features were removed. */
public final class PinUiInitializer extends ContentProvider {
    @Override public boolean onCreate() {
        Context context = getContext();
        if (context == null) return true;
        // Remove locally cached SMS/external-notification previews left by older versions.
        context.getSharedPreferences("sms_alert_notification_inbox", Context.MODE_PRIVATE)
                .edit().clear().apply();
        context.getSharedPreferences("financial_notification_inbox", Context.MODE_PRIVATE)
                .edit().clear().apply();
        Application application = (Application) context.getApplicationContext();
        application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle state) { applyPinMasking(activity); }
            @Override public void onActivityResumed(@NonNull Activity activity) { applyPinMasking(activity); }
            @Override public void onActivityStarted(@NonNull Activity activity) {}
            @Override public void onActivityPaused(@NonNull Activity activity) {}
            @Override public void onActivityStopped(@NonNull Activity activity) {}
            @Override public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle state) {}
            @Override public void onActivityDestroyed(@NonNull Activity activity) {}
        });
        return true;
    }

    private static void applyPinMasking(@NonNull Activity activity) {
        if (!(activity instanceof PinActivity)) return;
        mask(activity.findViewById(R.id.etPin));
        mask(activity.findViewById(R.id.etConfirmPin));
    }

    private static void mask(@Nullable TextInputEditText input) {
        if (input == null) return;
        int selection = input.getSelectionStart();
        input.setTransformationMethod(PasswordTransformationMethod.getInstance());
        if (selection >= 0 && input.getText() != null) input.setSelection(Math.min(selection, input.getText().length()));
    }

    @Nullable @Override public Cursor query(@NonNull Uri u, @Nullable String[] p, @Nullable String s, @Nullable String[] a, @Nullable String o) { return null; }
    @Nullable @Override public String getType(@NonNull Uri u) { return null; }
    @Nullable @Override public Uri insert(@NonNull Uri u, @Nullable ContentValues v) { return null; }
    @Override public int delete(@NonNull Uri u, @Nullable String s, @Nullable String[] a) { return 0; }
    @Override public int update(@NonNull Uri u, @Nullable ContentValues v, @Nullable String s, @Nullable String[] a) { return 0; }
}

