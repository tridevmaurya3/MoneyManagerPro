package com.example.moneymanagerpro.cloud;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.example.moneymanagerpro.TridevEventQueue;
import com.example.moneymanagerpro.TridevIntegrationContract;
import com.example.moneymanagerpro.TridevMoneyMappingEngine;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * STEP 13 - privacy-safe integration recovery snapshot.
 *
 * The snapshot contains only:
 * - hashed account/category mapping keys and stable MoneyManager references;
 * - unresolved integration events (PENDING / FAILED / NEEDS_REVIEW);
 * - structured event metadata required for reconciliation.
 *
 * It never contains a raw SMS body, contacts, passwords or the cloud recovery
 * passphrase. The returned JSON bytes must be encrypted before leaving device.
 */
public final class TridevIntegrationCloudSnapshot {

    public static final int SNAPSHOT_VERSION = 1;

    private static final String APP_PACKAGE = "com.example.moneymanagerpro";
    private static final String MAPPING_PREFS = "tridev_integration_mappings_v1";
    private static final String ACCOUNT_PREFIX = "account_alias_";
    private static final String CATEGORY_PREFIX = "category_alias_";
    private static final int MAX_PENDING = 100;
    private static final int MAX_REVIEW = 100;
    private static final int MAX_MAPPINGS = 1000;
    private static final int MAX_PLAINTEXT_BYTES = 450 * 1024;

    private TridevIntegrationCloudSnapshot() { }

    public static final class BuiltSnapshot {
        public final byte[] plaintextBytes;
        public final int eventCount;
        public final int mappingCount;
        public final boolean truncated;
        public final long createdAt;

        private BuiltSnapshot(
                byte[] plaintextBytes,
                int eventCount,
                int mappingCount,
                boolean truncated,
                long createdAt) {
            this.plaintextBytes = plaintextBytes;
            this.eventCount = eventCount;
            this.mappingCount = mappingCount;
            this.truncated = truncated;
            this.createdAt = createdAt;
        }
    }

    public static final class RestoreResult {
        public final int restoredMappings;
        public final int skippedMappings;
        public final int restoredEvents;
        public final int alreadyPresentEvents;
        public final int rejectedEvents;
        public final boolean sourceWasTruncated;

        private RestoreResult(
                int restoredMappings,
                int skippedMappings,
                int restoredEvents,
                int alreadyPresentEvents,
                int rejectedEvents,
                boolean sourceWasTruncated) {
            this.restoredMappings = restoredMappings;
            this.skippedMappings = skippedMappings;
            this.restoredEvents = restoredEvents;
            this.alreadyPresentEvents = alreadyPresentEvents;
            this.rejectedEvents = rejectedEvents;
            this.sourceWasTruncated = sourceWasTruncated;
        }
    }

    @NonNull
    public static BuiltSnapshot build(@NonNull Context context) throws SnapshotException {
        Context appContext = context.getApplicationContext();
        long createdAt = System.currentTimeMillis();

        try {
            JSONObject root = new JSONObject();
            root.put("snapshotVersion", SNAPSHOT_VERSION);
            root.put("appPackage", APP_PACKAGE);
            root.put("createdAt", createdAt);

            JSONArray mappings = buildMappings(appContext);
            root.put("mappings", mappings);

            TridevEventQueue queue = TridevEventQueue.getInstance(appContext);
            List<TridevEventQueue.QueueItem> pending = queue.getPendingBatch(MAX_PENDING);
            List<TridevEventQueue.QueueItem> review = queue.getReviewBatch(MAX_REVIEW);

            LinkedHashMap<String, TridevEventQueue.QueueItem> unique = new LinkedHashMap<>();
            addItems(unique, pending);
            addItems(unique, review);

            JSONArray events = new JSONArray();
            for (TridevEventQueue.QueueItem item : unique.values()) {
                if (item == null || item.event == null) continue;
                TridevIntegrationContract.SyncState state = item.event.syncState;
                if (state != TridevIntegrationContract.SyncState.PENDING
                        && state != TridevIntegrationContract.SyncState.FAILED
                        && state != TridevIntegrationContract.SyncState.NEEDS_REVIEW) {
                    continue;
                }
                events.put(eventJson(item));
            }
            root.put("events", events);

            boolean truncated = pending.size() >= MAX_PENDING || review.size() >= MAX_REVIEW;
            root.put("truncated", truncated);

            byte[] bytes = root.toString().getBytes(StandardCharsets.UTF_8);
            if (bytes.length <= 0 || bytes.length > MAX_PLAINTEXT_BYTES) {
                CloudBackupEncryption.clearSensitiveBytes(bytes);
                throw new SnapshotException(
                        "Integration recovery snapshot is too large. Resolve older pending reviews first.");
            }
            return new BuiltSnapshot(
                    bytes,
                    events.length(),
                    mappings.length(),
                    truncated,
                    createdAt);
        } catch (JSONException failure) {
            throw new SnapshotException("Integration recovery snapshot could not be created.", failure);
        }
    }

    @NonNull
    public static RestoreResult restore(
            @NonNull Context context,
            @NonNull byte[] plaintextBytes) throws SnapshotException {
        if (plaintextBytes.length <= 0 || plaintextBytes.length > MAX_PLAINTEXT_BYTES) {
            throw new SnapshotException("Integration recovery snapshot size is invalid.");
        }

        Context appContext = context.getApplicationContext();
        try {
            JSONObject root = new JSONObject(new String(plaintextBytes, StandardCharsets.UTF_8));
            int version = root.optInt("snapshotVersion", -1);
            if (version != SNAPSHOT_VERSION) {
                throw new SnapshotException("Unsupported integration recovery snapshot version.");
            }
            if (!APP_PACKAGE.equals(root.optString("appPackage", ""))) {
                throw new SnapshotException("Integration recovery snapshot belongs to another app.");
            }

            MappingRestore mappingRestore = restoreMappings(
                    appContext,
                    root.optJSONArray("mappings"));
            EventRestore eventRestore = restoreEvents(
                    appContext,
                    root.optJSONArray("events"));

            return new RestoreResult(
                    mappingRestore.restored,
                    mappingRestore.skipped,
                    eventRestore.restored,
                    eventRestore.alreadyPresent,
                    eventRestore.rejected,
                    root.optBoolean("truncated", false));
        } catch (JSONException failure) {
            throw new SnapshotException("Integration recovery snapshot is corrupted.", failure);
        }
    }

    private static JSONArray buildMappings(Context context) throws JSONException {
        SharedPreferences preferences = context.getSharedPreferences(
                MAPPING_PREFS,
                Context.MODE_PRIVATE);
        JSONArray result = new JSONArray();
        int count = 0;
        for (Map.Entry<String, ?> entry : preferences.getAll().entrySet()) {
            if (count >= MAX_MAPPINGS) break;
            String key = safe(entry.getKey());
            Object rawValue = entry.getValue();
            if (!(rawValue instanceof String)) continue;
            String ref = safe((String) rawValue).toLowerCase(Locale.ROOT);
            if (!isMappingKey(key) || !isCanonicalRef(ref)) continue;

            JSONObject item = new JSONObject();
            item.put("key", key);
            item.put("ref", ref);
            result.put(item);
            count++;
        }
        return result;
    }

    private static JSONObject eventJson(TridevEventQueue.QueueItem item) throws JSONException {
        TridevIntegrationContract.Event event = item.event;
        JSONObject json = new JSONObject();
        json.put("eventId", event.eventId);
        json.put("sourceApp", event.sourceApp);
        json.put("sourceRecordId", event.sourceRecordId);
        json.put("eventType", event.eventType.name());
        json.put("direction", event.direction.name());
        json.put("scope", event.scope.name());
        json.put("amountMinor", event.amountMinor);
        json.put("currency", event.currency);
        json.put("occurredAt", event.occurredAt);
        json.put("createdAt", event.createdAt);
        json.put("accountHint", event.accountHint);
        json.put("merchantHint", event.merchantHint);
        json.put("categoryHint", event.categoryHint);
        json.put("linkedEventId", event.linkedEventId);
        json.put("syncState", event.syncState.name());
        json.put("matchConfidence", event.matchConfidence.name());
        json.put("duplicateScore", item.duplicateScore);
        json.put("duplicateOfEventId", item.duplicateOfEventId == null ? "" : item.duplicateOfEventId);

        TridevIntegrationContract.References refs = event.references;
        JSONObject references = new JSONObject();
        references.put("moneyAccount", refs.moneyManagerAccountId);
        references.put("moneyCategory", refs.moneyManagerCategoryId);
        references.put("moneyTransaction", refs.moneyManagerTransactionId);
        references.put("familyFinance", refs.familyFinanceRecordId);
        references.put("familyGrocery", refs.familyGroceryRecordId);
        references.put("loan", refs.loanManagerLoanId);
        references.put("loanPayment", refs.loanManagerPaymentId);
        json.put("references", references);
        return json;
    }

    private static MappingRestore restoreMappings(Context context, JSONArray array) {
        if (array == null) return new MappingRestore(0, 0);

        TridevMoneyMappingEngine.Catalog catalog =
                new TridevMoneyMappingEngine(context).readCatalog();
        SharedPreferences preferences = context.getSharedPreferences(
                MAPPING_PREFS,
                Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        int restored = 0;
        int skipped = 0;

        int limit = Math.min(array.length(), MAX_MAPPINGS);
        for (int i = 0; i < limit; i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) {
                skipped++;
                continue;
            }
            String key = safe(item.optString("key", ""));
            String ref = safe(item.optString("ref", "")).toLowerCase(Locale.ROOT);
            if (!isMappingKey(key)
                    || !isCanonicalRef(ref)
                    || !canonicalRefExists(catalog, ref, key.startsWith(CATEGORY_PREFIX))) {
                skipped++;
                continue;
            }
            editor.putString(key, ref);
            restored++;
        }
        if (restored > 0 && !editor.commit()) {
            return new MappingRestore(0, restored + skipped);
        }
        return new MappingRestore(restored, skipped);
    }

    private static EventRestore restoreEvents(Context context, JSONArray array) {
        if (array == null) return new EventRestore(0, 0, 0);
        TridevEventQueue queue = TridevEventQueue.getInstance(context);
        int restored = 0;
        int already = 0;
        int rejected = 0;

        int limit = Math.min(array.length(), MAX_PENDING + MAX_REVIEW);
        for (int i = 0; i < limit; i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) {
                rejected++;
                continue;
            }
            try {
                String eventId = safe(item.optString("eventId", ""));
                if (eventId.isEmpty()) {
                    rejected++;
                    continue;
                }
                if (queue.find(eventId) != null) {
                    already++;
                    continue;
                }

                TridevIntegrationContract.SyncState originalState = safeEnum(
                        TridevIntegrationContract.SyncState.class,
                        item.optString("syncState", ""),
                        TridevIntegrationContract.SyncState.NEEDS_REVIEW);
                if (originalState != TridevIntegrationContract.SyncState.PENDING
                        && originalState != TridevIntegrationContract.SyncState.FAILED
                        && originalState != TridevIntegrationContract.SyncState.NEEDS_REVIEW) {
                    originalState = TridevIntegrationContract.SyncState.NEEDS_REVIEW;
                }

                JSONObject refs = item.optJSONObject("references");
                if (refs == null) refs = new JSONObject();
                TridevIntegrationContract.References references =
                        new TridevIntegrationContract.References(
                                refs.optString("moneyAccount", ""),
                                refs.optString("moneyCategory", ""),
                                refs.optString("moneyTransaction", ""),
                                refs.optString("familyFinance", ""),
                                refs.optString("familyGrocery", ""),
                                refs.optString("loan", ""),
                                refs.optString("loanPayment", ""));

                TridevIntegrationContract.Event event = new TridevIntegrationContract.Event(
                        eventId,
                        item.optString("sourceApp", ""),
                        item.optString("sourceRecordId", ""),
                        safeEnum(
                                TridevIntegrationContract.EventType.class,
                                item.optString("eventType", ""),
                                TridevIntegrationContract.EventType.EXPENSE),
                        safeEnum(
                                TridevIntegrationContract.Direction.class,
                                item.optString("direction", ""),
                                TridevIntegrationContract.Direction.UNKNOWN),
                        safeEnum(
                                TridevIntegrationContract.Scope.class,
                                item.optString("scope", ""),
                                TridevIntegrationContract.Scope.UNKNOWN),
                        Math.max(0L, item.optLong("amountMinor", 0L)),
                        item.optString("currency", TridevIntegrationContract.DEFAULT_CURRENCY),
                        Math.max(0L, item.optLong("occurredAt", 0L)),
                        Math.max(0L, item.optLong("createdAt", 0L)),
                        item.optString("accountHint", ""),
                        item.optString("merchantHint", ""),
                        item.optString("categoryHint", ""),
                        item.optString("linkedEventId", ""),
                        "",
                        originalState,
                        safeEnum(
                                TridevIntegrationContract.MatchConfidence.class,
                                item.optString("matchConfidence", ""),
                                TridevIntegrationContract.MatchConfidence.UNMATCHED),
                        references);

                TridevEventQueue.EnqueueResult enqueue = queue.enqueue(event);
                if (enqueue.queueId <= 0L) {
                    rejected++;
                    continue;
                }

                if (originalState == TridevIntegrationContract.SyncState.FAILED
                        && enqueue.syncState == TridevIntegrationContract.SyncState.PENDING) {
                    queue.markFailed(eventId, "Restored from encrypted integration cloud recovery");
                } else if (originalState == TridevIntegrationContract.SyncState.NEEDS_REVIEW
                        && enqueue.syncState != TridevIntegrationContract.SyncState.SYNCED
                        && enqueue.syncState != TridevIntegrationContract.SyncState.SUPERSEDED) {
                    queue.markNeedsReview(
                            eventId,
                            null,
                            Math.max(0, Math.min(100, item.optInt("duplicateScore", 0))));
                }
                restored++;
            } catch (Exception invalidEvent) {
                rejected++;
            }
        }
        return new EventRestore(restored, already, rejected);
    }

    private static void addItems(
            LinkedHashMap<String, TridevEventQueue.QueueItem> result,
            List<TridevEventQueue.QueueItem> items) {
        for (TridevEventQueue.QueueItem item : items) {
            if (item == null || item.event == null) continue;
            result.put(item.event.eventId, item);
        }
    }

    private static boolean canonicalRefExists(
            TridevMoneyMappingEngine.Catalog catalog,
            String ref,
            boolean category) {
        if (category) {
            for (TridevMoneyMappingEngine.CategoryCatalogItem item : catalog.categories) {
                if (item != null && ref.equalsIgnoreCase(safe(item.canonicalRef))) return true;
            }
            return false;
        }
        for (TridevMoneyMappingEngine.CatalogItem item : catalog.accounts) {
            if (item != null && !item.unavailableForNewPosting
                    && ref.equalsIgnoreCase(safe(item.canonicalRef))) return true;
        }
        for (TridevMoneyMappingEngine.CatalogItem item : catalog.creditCards) {
            if (item != null && !item.unavailableForNewPosting
                    && ref.equalsIgnoreCase(safe(item.canonicalRef))) return true;
        }
        return false;
    }

    private static boolean isMappingKey(String key) {
        String suffix;
        if (key.startsWith(ACCOUNT_PREFIX)) suffix = key.substring(ACCOUNT_PREFIX.length());
        else if (key.startsWith(CATEGORY_PREFIX)) suffix = key.substring(CATEGORY_PREFIX.length());
        else return false;
        return suffix.matches("[0-9a-fA-F]{64}");
    }

    private static boolean isCanonicalRef(String ref) {
        return ref.matches("(account|card|category):[0-9]+");
    }

    private static <T extends Enum<T>> T safeEnum(
            Class<T> type,
            String raw,
            T fallback) {
        try {
            return Enum.valueOf(type, safe(raw).toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class MappingRestore {
        final int restored;
        final int skipped;

        MappingRestore(int restored, int skipped) {
            this.restored = restored;
            this.skipped = skipped;
        }
    }

    private static final class EventRestore {
        final int restored;
        final int alreadyPresent;
        final int rejected;

        EventRestore(int restored, int alreadyPresent, int rejected) {
            this.restored = restored;
            this.alreadyPresent = alreadyPresent;
            this.rejected = rejected;
        }
    }

    public static class SnapshotException extends Exception {
        public SnapshotException(String message) {
            super(message);
        }

        public SnapshotException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
