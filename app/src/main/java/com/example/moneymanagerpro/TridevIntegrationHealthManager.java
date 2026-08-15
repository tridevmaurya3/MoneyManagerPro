package com.example.moneymanagerpro;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.provider.Telephony;

import androidx.annotation.NonNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Read-only Integration Health & Sync Center backend. */
public final class TridevIntegrationHealthManager {

    private static final String QUEUE_DB = "TridevIntegrationQueue.db";
    private static final String QUEUE_TABLE = "integration_events";
    private static final String MAPPING_PREFS = "tridev_integration_mappings_v1";
    private static final String ACCOUNT_PREFIX = "account_alias_";
    private static final String CATEGORY_PREFIX = "category_alias_";

    private static final String SMART_SMS_PACKAGE = "com.tridev.smartsmspro";
    private static final String FAMILY_HUB_PACKAGE = "com.tridev.familyhub";
    private static final String LOAN_MANAGER_PACKAGE = "com.tridev.loanmanagerpro";

    public enum Readiness {
        CONNECTED,
        READY,
        ACTION_REQUIRED,
        NOT_INSTALLED
    }

    public static final class AppHealth {
        public final String appId;
        public final String label;
        public final Readiness readiness;
        public final String detail;
        public final int eventCount;
        public final int pendingCount;
        public final int reviewCount;
        public final int failedCount;
        public final int syncedCount;
        public final int supersededCount;
        public final long lastActivityAt;

        private AppHealth(
                String appId,
                String label,
                Readiness readiness,
                String detail,
                SourceStats stats) {
            this.appId = appId;
            this.label = label;
            this.readiness = readiness;
            this.detail = detail == null ? "" : detail;
            this.eventCount = stats == null ? 0 : stats.total;
            this.pendingCount = stats == null ? 0 : stats.pending;
            this.reviewCount = stats == null ? 0 : stats.review;
            this.failedCount = stats == null ? 0 : stats.failed;
            this.syncedCount = stats == null ? 0 : stats.synced;
            this.supersededCount = stats == null ? 0 : stats.superseded;
            this.lastActivityAt = stats == null ? 0L : stats.lastUpdatedAt;
        }
    }

    public static final class Snapshot {
        public final List<AppHealth> apps;
        public final int totalEvents;
        public final int pendingCount;
        public final int reviewCount;
        public final int failedCount;
        public final int syncedCount;
        public final int supersededCount;
        public final int confirmedAccountMappings;
        public final int confirmedCategoryMappings;
        public final long lastActivityAt;

        private Snapshot(
                List<AppHealth> apps,
                int totalEvents,
                int pendingCount,
                int reviewCount,
                int failedCount,
                int syncedCount,
                int supersededCount,
                int confirmedAccountMappings,
                int confirmedCategoryMappings,
                long lastActivityAt) {
            this.apps = Collections.unmodifiableList(apps);
            this.totalEvents = totalEvents;
            this.pendingCount = pendingCount;
            this.reviewCount = reviewCount;
            this.failedCount = failedCount;
            this.syncedCount = syncedCount;
            this.supersededCount = supersededCount;
            this.confirmedAccountMappings = confirmedAccountMappings;
            this.confirmedCategoryMappings = confirmedCategoryMappings;
            this.lastActivityAt = lastActivityAt;
        }

        public int totalMappings() {
            return confirmedAccountMappings + confirmedCategoryMappings;
        }
    }

    public static final class RetrySummary {
        public final int attempted;
        public final int posted;
        public final int reconciled;
        public final int movedToReview;
        public final int failed;
        public final String message;

        private RetrySummary(
                int attempted,
                int posted,
                int reconciled,
                int movedToReview,
                int failed) {
            this.attempted = attempted;
            this.posted = posted;
            this.reconciled = reconciled;
            this.movedToReview = movedToReview;
            this.failed = failed;
            if (attempted == 0) {
                this.message = "No pending or failed integration events need retry.";
            } else {
                this.message = "Retried " + attempted
                        + " • Posted " + posted
                        + " • Reconciled " + reconciled
                        + " • Review " + movedToReview
                        + (failed > 0 ? " • Failed " + failed : "");
            }
        }
    }

    private final Context appContext;

    public TridevIntegrationHealthManager(Context context) {
        appContext = context.getApplicationContext();
    }

    @NonNull
    public Snapshot loadSnapshot() {
        Map<String, SourceStats> stats = readQueueStats();
        SourceStats smartStats = stats.get(TridevIntegrationContract.APP_SMART_SMS);
        SourceStats familyStats = stats.get(TridevIntegrationContract.APP_FAMILY_HUB);
        SourceStats loanStats = stats.get(TridevIntegrationContract.APP_LOAN_MANAGER);
        SourceStats moneyStats = stats.get(TridevIntegrationContract.APP_MONEY_MANAGER);

        List<AppHealth> apps = new ArrayList<>();
        apps.add(new AppHealth(
                TridevIntegrationContract.APP_MONEY_MANAGER,
                "MoneyManagerPro",
                Readiness.CONNECTED,
                "Master ledger active on this device",
                moneyStats));
        apps.add(buildSmartSmsHealth(smartStats));
        apps.add(buildCompanionHealth(
                TridevIntegrationContract.APP_FAMILY_HUB,
                "Family Hub",
                FAMILY_HUB_PACKAGE,
                familyStats));
        apps.add(buildCompanionHealth(
                TridevIntegrationContract.APP_LOAN_MANAGER,
                "LoanManagerPro",
                LOAN_MANAGER_PACKAGE,
                loanStats));

        int total = 0;
        int pending = 0;
        int review = 0;
        int failed = 0;
        int synced = 0;
        int superseded = 0;
        long last = 0L;
        for (SourceStats item : stats.values()) {
            total += item.total;
            pending += item.pending;
            review += item.review;
            failed += item.failed;
            synced += item.synced;
            superseded += item.superseded;
            last = Math.max(last, item.lastUpdatedAt);
        }

        int[] mappings = readMappingCounts();
        return new Snapshot(
                apps,
                total,
                pending,
                review,
                failed,
                synced,
                superseded,
                mappings[0],
                mappings[1],
                last);
    }

    @NonNull
    public RetrySummary retryPendingFailed(int requestedLimit) {
        int limit = Math.max(1, Math.min(50, requestedLimit));
        List<TridevTransactionPostingEngine.Result> results =
                new TridevTransactionPostingEngine(appContext)
                        .processPendingBatch(limit);

        int posted = 0;
        int reconciled = 0;
        int review = 0;
        int failed = 0;
        for (TridevTransactionPostingEngine.Result result : results) {
            if (result == null) continue;
            switch (result.outcome) {
                case POSTED:
                    posted++;
                    break;
                case RECONCILED_EXISTING:
                case ALREADY_HANDLED:
                    reconciled++;
                    break;
                case NEEDS_REVIEW:
                    review++;
                    break;
                case FAILED:
                case NOT_FOUND:
                default:
                    failed++;
                    break;
            }
        }
        return new RetrySummary(results.size(), posted, reconciled, review, failed);
    }

    private AppHealth buildSmartSmsHealth(SourceStats stats) {
        if (!isInstalled(SMART_SMS_PACKAGE)) {
            return new AppHealth(
                    TridevIntegrationContract.APP_SMART_SMS,
                    "SmartSMSPro",
                    Readiness.NOT_INSTALLED,
                    "App not installed or not visible on this device",
                    stats);
        }

        String defaultSms = null;
        try {
            defaultSms = Telephony.Sms.getDefaultSmsPackage(appContext);
        } catch (RuntimeException ignored) {
        }
        if (!SMART_SMS_PACKAGE.equals(defaultSms)) {
            return new AppHealth(
                    TridevIntegrationContract.APP_SMART_SMS,
                    "SmartSMSPro",
                    Readiness.ACTION_REQUIRED,
                    "Installed • set SmartSMSPro as the default SMS app to enable trusted sync",
                    stats);
        }
        return new AppHealth(
                TridevIntegrationContract.APP_SMART_SMS,
                "SmartSMSPro",
                stats != null && stats.total > 0 ? Readiness.CONNECTED : Readiness.READY,
                stats != null && stats.total > 0
                        ? "Trusted default SMS app • integration activity detected"
                        : "Trusted default SMS app • ready for the first financial SMS",
                stats);
    }

    /**
     * Family Hub and LoanManagerPro keep independent signing keys. Their exact
     * package certificate is pinned on first trusted connection and must remain
     * unchanged afterwards.
     */
    private AppHealth buildCompanionHealth(
            String appId,
            String label,
            String packageName,
            SourceStats stats) {
        if (!isInstalled(packageName)) {
            return new AppHealth(
                    appId,
                    label,
                    Readiness.NOT_INSTALLED,
                    "App not installed or not visible on this device",
                    stats);
        }

        boolean trusted;
        try {
            trusted = TridevCompanionTrust.verifyOrPinInstalledPackage(
                    appContext, packageName);
        } catch (RuntimeException failure) {
            trusted = false;
        }
        if (!trusted) {
            return new AppHealth(
                    appId,
                    label,
                    Readiness.ACTION_REQUIRED,
                    "Installed • companion signing certificate changed or could not be verified",
                    stats);
        }

        boolean active = stats != null && stats.total > 0;
        return new AppHealth(
                appId,
                label,
                active ? Readiness.CONNECTED : Readiness.READY,
                active
                        ? "Trusted companion certificate • integration activity detected"
                        : "Trusted companion certificate • master catalog ready",
                stats);
    }

    private boolean isInstalled(String packageName) {
        try {
            appContext.getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException | RuntimeException ignored) {
            return false;
        }
    }

    private Map<String, SourceStats> readQueueStats() {
        Map<String, SourceStats> result = new LinkedHashMap<>();
        File dbFile = appContext.getDatabasePath(QUEUE_DB);
        if (dbFile == null || !dbFile.exists()) return result;

        SQLiteDatabase db = null;
        try {
            db = SQLiteDatabase.openDatabase(
                    dbFile.getAbsolutePath(),
                    null,
                    SQLiteDatabase.OPEN_READONLY);
            String sql = "SELECT source_app, sync_state, COUNT(*) AS row_count, "
                    + "MAX(updated_at) AS last_updated FROM " + QUEUE_TABLE
                    + " GROUP BY source_app, sync_state";
            try (Cursor cursor = db.rawQuery(sql, null)) {
                while (cursor.moveToNext()) {
                    String source = safe(cursor.getString(0));
                    String state = safe(cursor.getString(1)).toUpperCase(Locale.ROOT);
                    int count = cursor.getInt(2);
                    long updated = cursor.getLong(3);
                    if (source.isEmpty()) continue;
                    SourceStats stats = result.get(source);
                    if (stats == null) {
                        stats = new SourceStats();
                        result.put(source, stats);
                    }
                    stats.total += Math.max(0, count);
                    stats.lastUpdatedAt = Math.max(stats.lastUpdatedAt, updated);
                    if (TridevIntegrationContract.SyncState.PENDING.name().equals(state)) {
                        stats.pending += count;
                    } else if (TridevIntegrationContract.SyncState.NEEDS_REVIEW.name().equals(state)) {
                        stats.review += count;
                    } else if (TridevIntegrationContract.SyncState.FAILED.name().equals(state)) {
                        stats.failed += count;
                    } else if (TridevIntegrationContract.SyncState.SYNCED.name().equals(state)) {
                        stats.synced += count;
                    } else if (TridevIntegrationContract.SyncState.SUPERSEDED.name().equals(state)) {
                        stats.superseded += count;
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // Health UI fails closed without touching the queue.
        } finally {
            if (db != null) {
                try {
                    db.close();
                } catch (RuntimeException ignored) {
                }
            }
        }
        return result;
    }

    private int[] readMappingCounts() {
        SharedPreferences prefs = appContext.getSharedPreferences(
                MAPPING_PREFS, Context.MODE_PRIVATE);
        int account = 0;
        int category = 0;
        for (String key : prefs.getAll().keySet()) {
            if (key.startsWith(ACCOUNT_PREFIX)) account++;
            else if (key.startsWith(CATEGORY_PREFIX)) category++;
        }
        return new int[]{account, category};
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class SourceStats {
        int total;
        int pending;
        int review;
        int failed;
        int synced;
        int superseded;
        long lastUpdatedAt;
    }
}
