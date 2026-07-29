package com.example.moneymanagerpro.auth;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public final class LocalProfilePhotoStore {

    private static final String PREFERENCES =
            "money_manager_local_profile";

    private static final String KEY_PHOTO_URI_PREFIX =
            "profile_photo_uri_";

    private LocalProfilePhotoStore() {
    }

    public static void save(Context context, Uri uri) {
        context.getSharedPreferences(
                PREFERENCES,
                Context.MODE_PRIVATE
        ).edit().putString(
                getPhotoKey(),
                uri.toString()
        ).apply();
    }

    @Nullable
    public static Uri get(Context context) {
        SharedPreferences preferences =
                context.getSharedPreferences(
                        PREFERENCES,
                        Context.MODE_PRIVATE
                );

        String value = preferences.getString(getPhotoKey(), "");

        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        return Uri.parse(value);
    }

    public static void clear(Context context) {
        context.getSharedPreferences(
                PREFERENCES,
                Context.MODE_PRIVATE
        ).edit().remove(getPhotoKey()).apply();
    }

    private static String getPhotoKey() {
        FirebaseUser user =
                FirebaseAuth.getInstance().getCurrentUser();

        String userId = user == null
                ? "signed_out"
                : user.getUid();

        return KEY_PHOTO_URI_PREFIX + userId;
    }
}
