package com.example.moneymanagerpro;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * Durable store for user-confirmed cross-app mappings.
 *
 * External hints are never stored as plain text. Only a SHA-256 fingerprint of
 * a normalized structured hint is used as the SharedPreferences key. Values are
 * local, non-sensitive stable references such as account:12, card:4 or
 * category:9.
 *
 * This class never modifies MoneyManager Room tables or finance records.
 */
public final class TridevMappingStore {

    private static final String PREFS = "tridev_integration_mappings_v1";
    private static final String ACCOUNT_PREFIX = "account_alias_";
    private static final String CATEGORY_PREFIX = "category_alias_";

    private final SharedPreferences preferences;

    public TridevMappingStore(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void rememberAccountAlias(String externalKey, String canonicalRef) {
        String key = fingerprintKey(externalKey);
        String ref = safeCanonicalRef(canonicalRef, false);
        if (key == null || ref == null) return;
        preferences.edit().putString(ACCOUNT_PREFIX + key, ref).apply();
    }

    @Nullable
    public String findAccountAlias(String externalKey) {
        String key = fingerprintKey(externalKey);
        if (key == null) return null;
        return trimToNull(preferences.getString(ACCOUNT_PREFIX + key, null));
    }

    public void forgetAccountAlias(String externalKey) {
        String key = fingerprintKey(externalKey);
        if (key == null) return;
        preferences.edit().remove(ACCOUNT_PREFIX + key).apply();
    }

    public void rememberCategoryAlias(String externalKey, String categoryCanonicalRef) {
        String key = fingerprintKey(externalKey);
        String ref = safeCanonicalRef(categoryCanonicalRef, true);
        if (key == null || ref == null) return;
        preferences.edit().putString(CATEGORY_PREFIX + key, ref).apply();
    }

    @Nullable
    public String findCategoryAlias(String externalKey) {
        String key = fingerprintKey(externalKey);
        if (key == null) return null;
        return trimToNull(preferences.getString(CATEGORY_PREFIX + key, null));
    }

    public void forgetCategoryAlias(String externalKey) {
        String key = fingerprintKey(externalKey);
        if (key == null) return;
        preferences.edit().remove(CATEGORY_PREFIX + key).apply();
    }

    public void clearAllIntegrationMappings() {
        preferences.edit().clear().apply();
    }

    @Nullable
    private static String safeCanonicalRef(String value, boolean categoryOnly) {
        String safe = trimToNull(value);
        if (safe == null) return null;
        String lower = safe.toLowerCase(Locale.ROOT);
        String pattern = categoryOnly
                ? "category:[0-9]+"
                : "(account|card):[0-9]+";
        return lower.matches(pattern) ? lower : null;
    }

    @Nullable
    private static String fingerprintKey(String raw) {
        String normalized = normalizeExternalKey(raw);
        if (normalized == null) return null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                builder.append(String.format(Locale.ROOT, "%02x", value & 0xff));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException impossibleOnAndroid) {
            return null;
        }
    }

    @Nullable
    private static String normalizeExternalKey(String raw) {
        String safe = trimToNull(raw);
        if (safe == null) return null;
        // Callers should pass structured keys such as bank:hdfc:last4:4582 or
        // family:grocery, never raw SMS bodies or personal free-form content.
        if (safe.length() > 160 || safe.indexOf('\n') >= 0 || safe.indexOf('\r') >= 0) {
            return null;
        }
        return safe.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9:_\\- ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    @Nullable
    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
