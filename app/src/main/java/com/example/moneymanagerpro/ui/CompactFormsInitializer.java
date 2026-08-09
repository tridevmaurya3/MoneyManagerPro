package com.example.moneymanagerpro.ui;

import android.app.Activity;
import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputLayout;

/** Global compact density for data-entry screens without changing validation or field order. */
public final class CompactFormsInitializer extends ContentProvider {
    @Override public boolean onCreate() {
        if (getContext() == null) return false;
        Application app = (Application) getContext().getApplicationContext();
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityResumed(@NonNull Activity activity) {
                if (!isForm(activity)) return;
                activity.getWindow().getDecorView().post(() -> compact(activity.getWindow().getDecorView(), activity));
            }
            @Override public void onActivityCreated(@NonNull Activity a, @Nullable Bundle b) {}
            @Override public void onActivityStarted(@NonNull Activity a) {}
            @Override public void onActivityPaused(@NonNull Activity a) {}
            @Override public void onActivityStopped(@NonNull Activity a) {}
            @Override public void onActivitySaveInstanceState(@NonNull Activity a, @NonNull Bundle b) {}
            @Override public void onActivityDestroyed(@NonNull Activity a) {}
        });
        return true;
    }

    private boolean isForm(Activity activity) {
        String n = activity.getClass().getSimpleName();
        return n.startsWith("Add") || n.startsWith("Edit") || n.contains("Transfer") || n.contains("Account") || n.contains("CreditCard") || n.contains("Loan") || n.contains("Budget") || n.contains("Goal") || n.contains("Investment") || n.contains("Recurring") || n.contains("Subscription") || n.contains("CsvImport") || n.contains("SmartGoalDebtPlanner");
    }

    private void compact(View view, Activity activity) {
        int minField = dp(activity, 44);
        if (view instanceof TextInputLayout) {
            TextInputLayout input = (TextInputLayout) view;
            input.setBoxCornerRadii(dp(activity, 10), dp(activity, 10), dp(activity, 10), dp(activity, 10));
            ViewGroup.LayoutParams raw = input.getLayoutParams();
            if (raw instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams p = (ViewGroup.MarginLayoutParams) raw;
                p.topMargin = Math.min(p.topMargin, dp(activity, 7));
                p.bottomMargin = Math.min(p.bottomMargin, dp(activity, 3));
                input.setLayoutParams(p);
            }
        } else if (view instanceof EditText) {
            EditText edit = (EditText) view;
            edit.setMinHeight(minField);
            edit.setMinimumHeight(minField);
            edit.setPadding(edit.getPaddingLeft(), dp(activity, 7), edit.getPaddingRight(), dp(activity, 7));
            edit.setTextSize(13);
        } else if (view instanceof MaterialButton) {
            MaterialButton button = (MaterialButton) view;
            if (button.getLayoutParams() != null && button.getLayoutParams().height > dp(activity, 48)) button.getLayoutParams().height = dp(activity, 46);
            button.setInsetTop(0); button.setInsetBottom(0);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) compact(group.getChildAt(i), activity);
        }
    }

    private int dp(Activity a, int v) { return Math.round(v * a.getResources().getDisplayMetrics().density); }
    @Nullable @Override public Cursor query(@NonNull Uri u, @Nullable String[] p, @Nullable String s, @Nullable String[] a, @Nullable String o) { return null; }
    @Nullable @Override public String getType(@NonNull Uri u) { return null; }
    @Nullable @Override public Uri insert(@NonNull Uri u, @Nullable ContentValues v) { return null; }
    @Override public int delete(@NonNull Uri u, @Nullable String s, @Nullable String[] a) { return 0; }
    @Override public int update(@NonNull Uri u, @Nullable ContentValues v, @Nullable String s, @Nullable String[] a) { return 0; }
}
