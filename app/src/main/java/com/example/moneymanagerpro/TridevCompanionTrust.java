package com.example.moneymanagerpro;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.security.MessageDigest;
import java.util.Locale;

/**
 * Trust-on-first-use certificate pinning for the user's independently signed
 * Family Hub and LoanManagerPro apps.
 *
 * The Binder caller UID must first resolve to the exact expected package. The
 * currently installed package certificate is then pinned in MoneyManagerPro.
 * Android itself prevents an in-place package update signed by another key, and
 * this additional pin also detects a different certificate after uninstall /
 * reinstall before finance metadata is accepted again.
 */
public final class TridevCompanionTrust {

    public static final String FAMILY_HUB_PACKAGE = "com.tridev.familyhub";
    public static final String LOAN_MANAGER_PACKAGE = "com.tridev.loanmanagerpro";

    private static final String PREFS = "tridev_companion_trust_v1";
    private static final String PREFIX_CERT = "cert_sha256_";

    private TridevCompanionTrust() { }

    public static boolean verifyCaller(
            @NonNull Context context,
            int callingUid,
            @NonNull String expectedPackage) {
        if (!isAllowedPackage(expectedPackage)) return false;
        PackageManager pm = context.getPackageManager();
        String[] packages = pm.getPackagesForUid(callingUid);
        boolean exactPackage = false;
        if (packages != null) {
            for (String packageName : packages) {
                if (expectedPackage.equals(packageName)) {
                    exactPackage = true;
                    break;
                }
            }
        }
        if (!exactPackage) return false;
        return verifyOrPinInstalledPackage(context, expectedPackage);
    }

    /**
     * Verifies the current installed certificate against the pin. If this is the
     * first trusted connection, the certificate is pinned locally.
     */
    public static boolean verifyOrPinInstalledPackage(
            @NonNull Context context,
            @NonNull String packageName) {
        if (!isAllowedPackage(packageName)) return false;
        String digest = installedCertificateSha256(context, packageName);
        if (digest == null || digest.isEmpty()) return false;

        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String key = PREFIX_CERT + packageName;
        String pinned = clean(prefs.getString(key, "")).toLowerCase(Locale.ROOT);
        if (pinned.isEmpty()) {
            prefs.edit().putString(key, digest).apply();
            return true;
        }
        return pinned.equalsIgnoreCase(digest);
    }

    @Nullable
    public static String pinnedCertificate(
            @NonNull Context context,
            @NonNull String packageName) {
        if (!isAllowedPackage(packageName)) return null;
        String value = clean(context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(PREFIX_CERT + packageName, ""));
        return value.isEmpty() ? null : value;
    }

    @Nullable
    public static String installedCertificateSha256(
            @NonNull Context context,
            @NonNull String packageName) {
        if (!isAllowedPackage(packageName)) return null;
        try {
            PackageManager pm = context.getPackageManager();
            Signature[] signatures;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageInfo info = pm.getPackageInfo(
                        packageName,
                        PackageManager.GET_SIGNING_CERTIFICATES);
                if (info.signingInfo == null) return null;
                signatures = info.signingInfo.hasMultipleSigners()
                        ? info.signingInfo.getApkContentsSigners()
                        : info.signingInfo.getSigningCertificateHistory();
            } else {
                @SuppressWarnings("deprecation")
                PackageInfo info = pm.getPackageInfo(
                        packageName,
                        PackageManager.GET_SIGNATURES);
                @SuppressWarnings("deprecation")
                Signature[] legacy = info.signatures;
                signatures = legacy;
            }
            if (signatures == null || signatures.length == 0) return null;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(signatures[0].toByteArray());
            StringBuilder out = new StringBuilder(bytes.length * 2);
            for (byte current : bytes) {
                out.append(String.format(Locale.US, "%02x", current & 0xff));
            }
            return out.toString();
        } catch (Exception unavailable) {
            return null;
        }
    }

    private static boolean isAllowedPackage(@Nullable String packageName) {
        return FAMILY_HUB_PACKAGE.equals(packageName)
                || LOAN_MANAGER_PACKAGE.equals(packageName);
    }

    @NonNull
    private static String clean(@Nullable String value) {
        return value == null ? "" : value.trim();
    }
}
