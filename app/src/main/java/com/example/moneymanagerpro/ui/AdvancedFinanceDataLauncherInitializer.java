package com.example.moneymanagerpro.ui;

import android.app.Activity;
import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.activities.AccountActivity;
import com.example.moneymanagerpro.activities.AdvancedFinanceDataActivity;
import com.example.moneymanagerpro.activities.CreditCardActivity;
import com.google.android.material.button.MaterialButton;

public final class AdvancedFinanceDataLauncherInitializer extends ContentProvider {

    private static final String TAG = "advanced_finance_data_center_launcher";

    @Override
    public boolean onCreate() {
        if (getContext() == null) return false;
        Application application =
                (Application) getContext().getApplicationContext();

        application.registerActivityLifecycleCallbacks(
                new Application.ActivityLifecycleCallbacks() {
                    @Override
                    public void onActivityResumed(@NonNull Activity activity) {
                        if (activity instanceof AccountActivity
                                || activity instanceof CreditCardActivity) {
                            attach(activity);
                        }
                    }

                    @Override
                    public void onActivityDestroyed(@NonNull Activity activity) {
                    }

                    @Override
                    public void onActivityCreated(
                            @NonNull Activity activity,
                            @Nullable Bundle state
                    ) {
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
                            @NonNull Bundle state
                    ) {
                    }
                }
        );
        return true;
    }

    private void attach(Activity activity) {
        FrameLayout content = activity.findViewById(android.R.id.content);
        if (content == null || content.findViewWithTag(TAG) != null) return;

        MaterialButton button = new MaterialButton(activity);
        button.setTag(TAG);
        button.setText("Data Center");
        button.setAllCaps(false);
        button.setTextSize(11);
        button.setIconResource(android.R.drawable.ic_menu_manage);
        button.setCornerRadius(dp(activity, 18));
        button.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(
                        androidx.core.content.ContextCompat.getColor(
                                activity, R.color.secondary
                        )
                )
        );
        button.setTextColor(android.graphics.Color.WHITE);
        button.setOnClickListener(view -> activity.startActivity(
                new Intent(activity, AdvancedFinanceDataActivity.class)
        ));

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                dp(activity, 132),
                dp(activity, 50),
                Gravity.END | Gravity.BOTTOM
        );
        params.setMargins(
                dp(activity, 12),
                dp(activity, 12),
                dp(activity, 18),
                dp(activity, 24)
        );
        content.addView(button, params);
        button.setAlpha(0f);
        button.setTranslationY(dp(activity, 18));
        button.animate().alpha(1f).translationY(0).setDuration(250).start();
    }

    private int dp(Activity activity, int value) {
        return Math.round(
                value * activity.getResources().getDisplayMetrics().density
        );
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
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
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
