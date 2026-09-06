package com.example.moneymanagerpro.floating;

import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Registers the in-app Add Expense native Scan & Pay compatibility layer. */
public final class InAppExpenseUpiInitializer extends ContentProvider {

    @Override
    public boolean onCreate() {
        if (getContext() == null) {
            return false;
        }
        Application application =
                (Application) getContext().getApplicationContext();
        InAppExpenseUpiCompatibility.register(application);
        return true;
    }

    @Nullable @Override public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection, @Nullable String[] args, @Nullable String sortOrder) { return null; }
    @Nullable @Override public String getType(@NonNull Uri uri) { return null; }
    @Nullable @Override public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) { return null; }
    @Override public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] args) { return 0; }
    @Override public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection, @Nullable String[] args) { return 0; }
}
