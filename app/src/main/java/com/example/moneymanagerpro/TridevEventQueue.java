package com.example.moneymanagerpro;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Durable, isolated queue for finance events received from the Tridev app suite.
 *
 * This queue intentionally uses its own SQLite database instead of adding tables
 * to MoneyManagerDB. Therefore STEP 3 does not change MoneyManagerPro's Room
 * schema/version and cannot invalidate existing finance migrations.
 *
 * Duplicate policy:
 * - Same eventId -> duplicate, do not insert another row.
 * - Same sourceApp + sourceRecordId -> duplicate, do not insert another row.
 * - Cross-app candidates are scored using amount, direction, time, account,
 *   merchant and category evidence.
 * - Strong match -> stored as SUPERSEDED and linked to the original event.
 * - Medium match -> stored as NEEDS_REVIEW; never guessed/posted automatically.
 * - Weak/no match -> stored as PENDING.
 *
 * Raw SMS bodies, contacts and passwords must never be supplied to this class.
 * Call database methods from a worker/executor rather than the Android main
 * thread.
 */
public final class TridevEventQueue {

    private static final String DB_NAME = "TridevIntegrationQueue.db";
    private static final int DB_VERSION = 1;
    private static final String TABLE = "integration_events";

    private static final long DUPLICATE_WINDOW_MILLIS = 6L * 60L * 60L * 1000L;
    private static final int DUPLICATE_SCORE = 90;
    private static final int REVIEW_SCORE = 65;
    private static final int MAX_BATCH = 100;

    public enum Decision {
        ACCEPTED,
        DUPLICATE,
        NEEDS_REVIEW
    }

    public static final class EnqueueResult {
        public final Decision decision;
        public final long queueId;
        public final String eventId;
        @Nullable public final String duplicateOfEventId;
        public final int duplicateScore;
        public final TridevIntegrationContract.SyncState syncState;
        public final String reason;

        private EnqueueResult(
                Decision decision,
                long queueId,
                String eventId,
                @Nullable String duplicateOfEventId,
                int duplicateScore,
                TridevIntegrationContract.SyncState syncState,
                String reason) {
            this.decision = decision;
            this.queueId = queueId;
            this.eventId = eventId;
            this.duplicateOfEventId = duplicateOfEventId;
            this.duplicateScore = duplicateScore;
            this.syncState = syncState;
            this.reason = reason == null ? "" : reason;
        }
    }

    public static final class QueueItem {
        public final long queueId;
        public final TridevIntegrationContract.Event event;
        @Nullable public final String duplicateOfEventId;
        public final int duplicateScore;
        public final int attemptCount;
        public final String lastError;
        public final long updatedAt;

        private QueueItem(
                long queueId,
                TridevIntegrationContract.Event event,
                @Nullable String duplicateOfEventId,
                int duplicateScore,
                int attemptCount,
                String lastError,
                long updatedAt) {
            this.queueId = queueId;
            this.event = event;
            this.duplicateOfEventId = duplicateOfEventId;
            this.duplicateScore = duplicateScore;
            this.attemptCount = attemptCount;
            this.lastError = lastError == null ? "" : lastError;
            this.updatedAt = updatedAt;
        }
    }

    private static volatile TridevEventQueue instance;

    private final QueueDbHelper helper;

    private TridevEventQueue(Context context) {
        helper = new QueueDbHelper(context.getApplicationContext());
    }

    public static TridevEventQueue getInstance(Context context) {
        if (instance == null) {
            synchronized (TridevEventQueue.class) {
                if (instance == null) {
                    instance = new TridevEventQueue(context);
                }
            }
        }
        return instance;
    }

    /**
     * Adds an event using fail-closed duplicate protection.
     * Existing MoneyManager transactions are not modified here.
     */
    public EnqueueResult enqueue(TridevIntegrationContract.Event event) {
        validateEvent(event);
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            ExistingRow byEventId = findByEventId(db, event.eventId);
            if (byEventId != null) {
                db.setTransactionSuccessful();
                return new EnqueueResult(
                        Decision.DUPLICATE,
                        byEventId.queueId,
                        event.eventId,
                        byEventId.eventId,
                        100,
                        byEventId.syncState,
                        "Event id is already queued");
            }

            ExistingRow bySourceRecord = findBySourceRecord(
                    db,
                    event.sourceApp,
                    safeNullable(event.sourceRecordId, 160));
            if (bySourceRecord != null) {
                db.setTransactionSuccessful();
                return new EnqueueResult(
                        Decision.DUPLICATE,
                        bySourceRecord.queueId,
                        event.eventId,
                        bySourceRecord.eventId,
                        100,
                        bySourceRecord.syncState,
                        "Source record is already queued");
            }

            String fingerprint = TridevEventFingerprint.build(event);
            CandidateMatch candidateMatch = findBestCandidate(db, event, fingerprint);

            Decision decision = Decision.ACCEPTED;
            TridevIntegrationContract.SyncState state =
                    event.syncState == TridevIntegrationContract.SyncState.NEEDS_REVIEW
                            ? TridevIntegrationContract.SyncState.NEEDS_REVIEW
                            : TridevIntegrationContract.SyncState.PENDING;
            String duplicateOf = null;
            int score = 0;
            String reason = "New event queued for reconciliation";

            if (event.amountMinor <= 0L) {
                decision = Decision.NEEDS_REVIEW;
                state = TridevIntegrationContract.SyncState.NEEDS_REVIEW;
                reason = "Amount is missing or zero";
            } else if (candidateMatch != null) {
                score = candidateMatch.score;
                if (score >= DUPLICATE_SCORE) {
                    decision = Decision.DUPLICATE;
                    state = TridevIntegrationContract.SyncState.SUPERSEDED;
                    duplicateOf = candidateMatch.row.eventId;
                    reason = "Strong cross-app duplicate match";
                } else if (score >= REVIEW_SCORE) {
                    decision = Decision.NEEDS_REVIEW;
                    state = TridevIntegrationContract.SyncState.NEEDS_REVIEW;
                    duplicateOf = candidateMatch.row.eventId;
                    reason = "Possible duplicate requires user review";
                }
            }

            long queueId = insertEvent(
                    db,
                    event,
                    fingerprint,
                    state,
                    duplicateOf,
                    score);
            if (queueId <= 0L) {
                throw new IllegalStateException("Unable to queue integration event");
            }

            db.setTransactionSuccessful();
            return new EnqueueResult(
                    decision,
                    queueId,
                    event.eventId,
                    duplicateOf,
                    score,
                    state,
                    reason);
        } finally {
            db.endTransaction();
        }
    }

    /** Returns oldest actionable events first. */
    public List<QueueItem> getPendingBatch(int requestedLimit) {
        int limit = Math.max(1, Math.min(MAX_BATCH, requestedLimit));
        SQLiteDatabase db = helper.getReadableDatabase();
        List<QueueItem> result = new ArrayList<>();
        String sql = "SELECT * FROM " + TABLE
                + " WHERE sync_state IN (?, ?)"
                + " ORDER BY occurred_at ASC, id ASC LIMIT " + limit;
        try (Cursor cursor = db.rawQuery(sql, new String[]{
                TridevIntegrationContract.SyncState.PENDING.name(),
                TridevIntegrationContract.SyncState.FAILED.name()
        })) {
            while (cursor.moveToNext()) {
                QueueItem item = readQueueItem(cursor);
                if (item != null) result.add(item);
            }
        }
        return Collections.unmodifiableList(result);
    }

    public List<QueueItem> getReviewBatch(int requestedLimit) {
        int limit = Math.max(1, Math.min(MAX_BATCH, requestedLimit));
        SQLiteDatabase db = helper.getReadableDatabase();
        List<QueueItem> result = new ArrayList<>();
        String sql = "SELECT * FROM " + TABLE
                + " WHERE sync_state = ?"
                + " ORDER BY occurred_at DESC, id DESC LIMIT " + limit;
        try (Cursor cursor = db.rawQuery(sql, new String[]{
                TridevIntegrationContract.SyncState.NEEDS_REVIEW.name()
        })) {
            while (cursor.moveToNext()) {
                QueueItem item = readQueueItem(cursor);
                if (item != null) result.add(item);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /** Marks successful posting/reconciliation with the existing MoneyManager row id. */
    public boolean markSynced(String eventId, @Nullable String moneyManagerTransactionId) {
        String safeEventId = safeShortValue(eventId, 120);
        if (safeEventId.isEmpty()) return false;

        ContentValues values = new ContentValues();
        values.put("sync_state", TridevIntegrationContract.SyncState.SYNCED.name());
        values.put("money_transaction_ref", safeShortValue(moneyManagerTransactionId, 80));
        values.put("last_error", "");
        values.put("updated_at", System.currentTimeMillis());
        return helper.getWritableDatabase().update(
                TABLE,
                values,
                "event_id = ?",
                new String[]{safeEventId}) == 1;
    }

    public boolean markFailed(String eventId, @Nullable String safeTechnicalError) {
        String safeEventId = safeShortValue(eventId, 120);
        if (safeEventId.isEmpty()) return false;

        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            db.execSQL(
                    "UPDATE " + TABLE
                            + " SET sync_state = ?, attempt_count = attempt_count + 1,"
                            + " last_error = ?, updated_at = ? WHERE event_id = ?",
                    new Object[]{
                            TridevIntegrationContract.SyncState.FAILED.name(),
                            safeTechnicalError(safeTechnicalError),
                            System.currentTimeMillis(),
                            safeEventId
                    });
            boolean exists = findByEventId(db, safeEventId) != null;
            db.setTransactionSuccessful();
            return exists;
        } finally {
            db.endTransaction();
        }
    }

    public boolean markNeedsReview(
            String eventId,
            @Nullable String duplicateOfEventId,
            int duplicateScore) {
        String safeEventId = safeShortValue(eventId, 120);
        if (safeEventId.isEmpty()) return false;

        ContentValues values = new ContentValues();
        values.put("sync_state", TridevIntegrationContract.SyncState.NEEDS_REVIEW.name());
        values.put("duplicate_of_event_id", safeNullable(duplicateOfEventId, 120));
        values.put("duplicate_score", Math.max(0, Math.min(100, duplicateScore)));
        values.put("updated_at", System.currentTimeMillis());
        return helper.getWritableDatabase().update(
                TABLE,
                values,
                "event_id = ?",
                new String[]{safeEventId}) == 1;
    }

    /**
     * User has confirmed that this event is genuinely separate. It becomes
     * pending again without deleting any audit history.
     */
    public boolean confirmNotDuplicate(String eventId) {
        String safeEventId = safeShortValue(eventId, 120);
        if (safeEventId.isEmpty()) return false;

        ContentValues values = new ContentValues();
        values.put("sync_state", TridevIntegrationContract.SyncState.PENDING.name());
        values.putNull("duplicate_of_event_id");
        values.put("duplicate_score", 0);
        values.put("last_error", "");
        values.put("updated_at", System.currentTimeMillis());
        return helper.getWritableDatabase().update(
                TABLE,
                values,
                "event_id = ?",
                new String[]{safeEventId}) == 1;
    }

    /**
     * User has confirmed that two queued events describe the same transaction.
     * The duplicate remains in history as SUPERSEDED; nothing is deleted.
     */
    public boolean confirmDuplicate(String eventId, String canonicalEventId) {
        String safeEventId = safeShortValue(eventId, 120);
        String safeCanonical = safeShortValue(canonicalEventId, 120);
        if (safeEventId.isEmpty() || safeCanonical.isEmpty()
                || safeEventId.equals(safeCanonical)) return false;

        SQLiteDatabase db = helper.getWritableDatabase();
        if (findByEventId(db, safeCanonical) == null) return false;

        ContentValues values = new ContentValues();
        values.put("sync_state", TridevIntegrationContract.SyncState.SUPERSEDED.name());
        values.put("duplicate_of_event_id", safeCanonical);
        values.put("duplicate_score", 100);
        values.put("last_error", "");
        values.put("updated_at", System.currentTimeMillis());
        return db.update(
                TABLE,
                values,
                "event_id = ?",
                new String[]{safeEventId}) == 1;
    }

    @Nullable
    public QueueItem find(String eventId) {
        String safeEventId = safeShortValue(eventId, 120);
        if (safeEventId.isEmpty()) return null;
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor cursor = db.query(
                TABLE,
                null,
                "event_id = ?",
                new String[]{safeEventId},
                null,
                null,
                null,
                "1")) {
            if (!cursor.moveToFirst()) return null;
            return readQueueItem(cursor);
        }
    }

    private long insertEvent(
            SQLiteDatabase db,
            TridevIntegrationContract.Event event,
            String fingerprint,
            TridevIntegrationContract.SyncState state,
            @Nullable String duplicateOfEventId,
            int duplicateScore) {
        ContentValues values = new ContentValues();
        values.put("event_id", safeShortValue(event.eventId, 120));
        values.put("source_app", event.sourceApp);
        putNullable(values, "source_record_id", event.sourceRecordId, 160);
        values.put("event_type", event.eventType.name());
        values.put("direction", event.direction.name());
        values.put("scope", event.scope.name());
        values.put("amount_minor", event.amountMinor);
        values.put("currency", safeCurrency(event.currency));
        long effectiveOccurredAt = event.occurredAt > 0L ? event.occurredAt : event.createdAt;
        values.put("occurred_at", effectiveOccurredAt);
        values.put("created_at", event.createdAt);
        values.put("account_hint", safeMetadata(event.accountHint));
        values.put("merchant_hint", safeMetadata(event.merchantHint));
        values.put("category_hint", safeMetadata(event.categoryHint));
        putNullable(values, "linked_event_id", event.linkedEventId, 120);
        values.put("dedupe_fingerprint", fingerprint);
        values.put("sync_state", state.name());
        values.put("match_confidence", event.matchConfidence.name());
        putNullable(values, "money_account_ref", event.references.moneyManagerAccountId, 80);
        putNullable(values, "money_category_ref", event.references.moneyManagerCategoryId, 80);
        putNullable(values, "money_transaction_ref", event.references.moneyManagerTransactionId, 80);
        putNullable(values, "family_finance_ref", event.references.familyFinanceRecordId, 120);
        putNullable(values, "family_grocery_ref", event.references.familyGroceryRecordId, 120);
        putNullable(values, "loan_ref", event.references.loanManagerLoanId, 120);
        putNullable(values, "loan_payment_ref", event.references.loanManagerPaymentId, 120);
        putNullable(values, "duplicate_of_event_id", duplicateOfEventId, 120);
        values.put("duplicate_score", Math.max(0, Math.min(100, duplicateScore)));
        values.put("attempt_count", 0);
        values.put("last_error", "");
        values.put("updated_at", System.currentTimeMillis());
        return db.insertOrThrow(TABLE, null, values);
    }

    @Nullable
    private CandidateMatch findBestCandidate(
            SQLiteDatabase db,
            TridevIntegrationContract.Event incoming,
            String fingerprint) {
        long center = incoming.occurredAt > 0L ? incoming.occurredAt : incoming.createdAt;
        long from = Math.max(0L, center - DUPLICATE_WINDOW_MILLIS);
        long to = center + DUPLICATE_WINDOW_MILLIS;

        String sql = "SELECT * FROM " + TABLE
                + " WHERE amount_minor = ? AND currency = ?"
                + " AND occurred_at BETWEEN ? AND ?"
                + " AND sync_state != ?"
                + " ORDER BY ABS(occurred_at - ?) ASC LIMIT 30";

        CandidateMatch best = null;
        try (Cursor cursor = db.rawQuery(sql, new String[]{
                String.valueOf(incoming.amountMinor),
                safeCurrency(incoming.currency),
                String.valueOf(from),
                String.valueOf(to),
                TridevIntegrationContract.SyncState.SUPERSEDED.name(),
                String.valueOf(center)
        })) {
            while (cursor.moveToNext()) {
                ExistingRow row = readExistingRow(cursor);
                int score = duplicateScore(incoming, fingerprint, row);
                if (best == null || score > best.score) {
                    best = new CandidateMatch(row, score);
                }
            }
        }
        return best;
    }

    private int duplicateScore(
            TridevIntegrationContract.Event incoming,
            String fingerprint,
            ExistingRow existing) {
        int score = 25; // amount + currency are exact because of the candidate query.
        boolean identityEvidence = false;

        if (incoming.direction.name().equals(existing.direction)) {
            score += 15;
        } else if (incoming.direction == TridevIntegrationContract.Direction.UNKNOWN
                || TridevIntegrationContract.Direction.UNKNOWN.name().equals(existing.direction)) {
            score += 5;
        } else {
            score -= 30;
        }

        long incomingTime = incoming.occurredAt > 0L ? incoming.occurredAt : incoming.createdAt;
        long delta = Math.abs(incomingTime - existing.occurredAt);
        if (delta <= 5L * 60L * 1000L) score += 25;
        else if (delta <= 30L * 60L * 1000L) score += 20;
        else if (delta <= 2L * 60L * 60L * 1000L) score += 12;
        else score += 5;

        String incomingLastFour = TridevEventFingerprint.lastFour(incoming.accountHint);
        String existingLastFour = TridevEventFingerprint.lastFour(existing.accountHint);
        String incomingAccount = TridevEventFingerprint.normalizeHint(incoming.accountHint);
        String existingAccount = TridevEventFingerprint.normalizeHint(existing.accountHint);
        if (!incomingLastFour.isEmpty() && incomingLastFour.equals(existingLastFour)) {
            score += 25;
            identityEvidence = true;
        } else if (!incomingAccount.isEmpty() && incomingAccount.equals(existingAccount)) {
            score += 20;
            identityEvidence = true;
        }

        String incomingMerchant = TridevEventFingerprint.normalizeHint(incoming.merchantHint);
        String existingMerchant = TridevEventFingerprint.normalizeHint(existing.merchantHint);
        if (!incomingMerchant.isEmpty() && incomingMerchant.equals(existingMerchant)) {
            score += 20;
            identityEvidence = true;
        } else if (TridevEventFingerprint.tokenSimilarity(
                incomingMerchant,
                existingMerchant) >= 0.60d) {
            score += 12;
            identityEvidence = true;
        }

        String incomingCategory = TridevEventFingerprint.normalizeHint(incoming.categoryHint);
        String existingCategory = TridevEventFingerprint.normalizeHint(existing.categoryHint);
        if (!incomingCategory.isEmpty() && incomingCategory.equals(existingCategory)) {
            score += 10;
        } else if (TridevEventFingerprint.tokenSimilarity(
                incomingCategory,
                existingCategory) >= 0.60d) {
            score += 5;
        }

        if (incoming.scope.name().equals(existing.scope)
                && incoming.scope != TridevIntegrationContract.Scope.UNKNOWN) {
            score += 5;
        }
        if (!incoming.sourceApp.equals(existing.sourceApp)) {
            score += 5;
        } else {
            score -= 5;
        }

        if (fingerprint.equals(existing.fingerprint)) {
            // Equal amount/time/hash without account or merchant evidence is not
            // enough to auto-merge two real transactions.
            score += identityEvidence ? 20 : 5;
        }

        return Math.max(0, Math.min(100, score));
    }

    @Nullable
    private ExistingRow findByEventId(SQLiteDatabase db, String eventId) {
        try (Cursor cursor = db.query(
                TABLE,
                null,
                "event_id = ?",
                new String[]{safeShortValue(eventId, 120)},
                null,
                null,
                null,
                "1")) {
            if (!cursor.moveToFirst()) return null;
            return readExistingRow(cursor);
        }
    }

    @Nullable
    private ExistingRow findBySourceRecord(
            SQLiteDatabase db,
            String sourceApp,
            @Nullable String sourceRecordId) {
        if (sourceRecordId == null) return null;
        try (Cursor cursor = db.query(
                TABLE,
                null,
                "source_app = ? AND source_record_id = ?",
                new String[]{sourceApp, sourceRecordId},
                null,
                null,
                null,
                "1")) {
            if (!cursor.moveToFirst()) return null;
            return readExistingRow(cursor);
        }
    }

    private ExistingRow readExistingRow(Cursor cursor) {
        return new ExistingRow(
                getLong(cursor, "id"),
                getString(cursor, "event_id"),
                getString(cursor, "source_app"),
                getString(cursor, "direction"),
                getString(cursor, "scope"),
                getLong(cursor, "occurred_at"),
                getString(cursor, "account_hint"),
                getString(cursor, "merchant_hint"),
                getString(cursor, "category_hint"),
                getString(cursor, "dedupe_fingerprint"),
                safeSyncState(getString(cursor, "sync_state")));
    }

    @Nullable
    private QueueItem readQueueItem(Cursor cursor) {
        try {
            TridevIntegrationContract.References references =
                    new TridevIntegrationContract.References(
                            getString(cursor, "money_account_ref"),
                            getString(cursor, "money_category_ref"),
                            getString(cursor, "money_transaction_ref"),
                            getString(cursor, "family_finance_ref"),
                            getString(cursor, "family_grocery_ref"),
                            getString(cursor, "loan_ref"),
                            getString(cursor, "loan_payment_ref"));

            TridevIntegrationContract.Event event = new TridevIntegrationContract.Event(
                    getString(cursor, "event_id"),
                    getString(cursor, "source_app"),
                    getString(cursor, "source_record_id"),
                    safeEnum(
                            TridevIntegrationContract.EventType.class,
                            getString(cursor, "event_type"),
                            TridevIntegrationContract.EventType.EXPENSE),
                    safeEnum(
                            TridevIntegrationContract.Direction.class,
                            getString(cursor, "direction"),
                            TridevIntegrationContract.Direction.UNKNOWN),
                    safeEnum(
                            TridevIntegrationContract.Scope.class,
                            getString(cursor, "scope"),
                            TridevIntegrationContract.Scope.UNKNOWN),
                    getLong(cursor, "amount_minor"),
                    getString(cursor, "currency"),
                    getLong(cursor, "occurred_at"),
                    getLong(cursor, "created_at"),
                    getString(cursor, "account_hint"),
                    getString(cursor, "merchant_hint"),
                    getString(cursor, "category_hint"),
                    getString(cursor, "linked_event_id"),
                    getString(cursor, "dedupe_fingerprint"),
                    safeSyncState(getString(cursor, "sync_state")),
                    safeEnum(
                            TridevIntegrationContract.MatchConfidence.class,
                            getString(cursor, "match_confidence"),
                            TridevIntegrationContract.MatchConfidence.UNMATCHED),
                    references);

            return new QueueItem(
                    getLong(cursor, "id"),
                    event,
                    trimToNull(getString(cursor, "duplicate_of_event_id")),
                    getInt(cursor, "duplicate_score"),
                    getInt(cursor, "attempt_count"),
                    getString(cursor, "last_error"),
                    getLong(cursor, "updated_at"));
        } catch (RuntimeException invalidPersistedRow) {
            return null;
        }
    }

    private void validateEvent(TridevIntegrationContract.Event event) {
        if (event == null) throw new IllegalArgumentException("event is required");
        if (!TridevIntegrationContract.isKnownApp(event.sourceApp)) {
            throw new IllegalArgumentException("Unknown source app");
        }
        rejectStructuredId(event.eventId, "eventId", 120, false);
        rejectStructuredId(event.sourceRecordId, "sourceRecordId", 160, true);
        if (event.schemaVersion != TridevIntegrationContract.SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported event schema version");
        }
        rejectUnsafeMetadata(event.accountHint, "accountHint");
        rejectUnsafeMetadata(event.merchantHint, "merchantHint");
        rejectUnsafeMetadata(event.categoryHint, "categoryHint");
    }

    private void rejectStructuredId(
            @Nullable String value,
            String fieldName,
            int maxLength,
            boolean optional) {
        String safe = value == null ? "" : value.trim();
        if (!optional && safe.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        if (safe.length() > maxLength || safe.indexOf('\n') >= 0 || safe.indexOf('\r') >= 0) {
            throw new IllegalArgumentException(fieldName + " is not a valid structured id");
        }
    }

    private void rejectUnsafeMetadata(@Nullable String value, String fieldName) {
        if (value == null) return;
        if (value.length() > 240 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            throw new IllegalArgumentException(fieldName + " must contain structured metadata only");
        }
    }

    private static String safeMetadata(@Nullable String value) {
        String safe = value == null ? "" : value.trim();
        if (safe.length() > 240) safe = safe.substring(0, 240).trim();
        return safe.replace('\n', ' ').replace('\r', ' ');
    }

    private static String safeTechnicalError(@Nullable String value) {
        String safe = value == null ? "" : value.trim();
        if (safe.length() > 240) safe = safe.substring(0, 240).trim();
        return safe.replace('\n', ' ').replace('\r', ' ');
    }

    private static String safeCurrency(@Nullable String currency) {
        String value = currency == null ? "" : currency.trim().toUpperCase(Locale.ROOT);
        return value.isEmpty() ? TridevIntegrationContract.DEFAULT_CURRENCY : value;
    }

    private static String safeShortValue(@Nullable String value, int maxLength) {
        String safe = value == null ? "" : value.trim();
        if (safe.length() > maxLength) safe = safe.substring(0, maxLength).trim();
        return safe.replace('\n', ' ').replace('\r', ' ');
    }

    @Nullable
    private static String safeNullable(@Nullable String value, int maxLength) {
        String safe = safeShortValue(value, maxLength);
        return safe.isEmpty() ? null : safe;
    }

    private static void putNullable(
            ContentValues values,
            String key,
            @Nullable String value,
            int maxLength) {
        String safe = safeShortValue(value, maxLength);
        if (safe.isEmpty()) values.putNull(key);
        else values.put(key, safe);
    }

    @Nullable
    private static String trimToNull(@Nullable String value) {
        if (value == null) return null;
        String safe = value.trim();
        return safe.isEmpty() ? null : safe;
    }

    private static TridevIntegrationContract.SyncState safeSyncState(String value) {
        return safeEnum(
                TridevIntegrationContract.SyncState.class,
                value,
                TridevIntegrationContract.SyncState.NEEDS_REVIEW);
    }

    private static <T extends Enum<T>> T safeEnum(
            Class<T> type,
            @Nullable String value,
            T fallback) {
        if (value == null) return fallback;
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private static String getString(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        return index < 0 || cursor.isNull(index) ? "" : cursor.getString(index);
    }

    private static long getLong(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        return index < 0 || cursor.isNull(index) ? 0L : cursor.getLong(index);
    }

    private static int getInt(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        return index < 0 || cursor.isNull(index) ? 0 : cursor.getInt(index);
    }

    private static final class ExistingRow {
        final long queueId;
        final String eventId;
        final String sourceApp;
        final String direction;
        final String scope;
        final long occurredAt;
        final String accountHint;
        final String merchantHint;
        final String categoryHint;
        final String fingerprint;
        final TridevIntegrationContract.SyncState syncState;

        ExistingRow(
                long queueId,
                String eventId,
                String sourceApp,
                String direction,
                String scope,
                long occurredAt,
                String accountHint,
                String merchantHint,
                String categoryHint,
                String fingerprint,
                TridevIntegrationContract.SyncState syncState) {
            this.queueId = queueId;
            this.eventId = eventId;
            this.sourceApp = sourceApp;
            this.direction = direction;
            this.scope = scope;
            this.occurredAt = occurredAt;
            this.accountHint = accountHint;
            this.merchantHint = merchantHint;
            this.categoryHint = categoryHint;
            this.fingerprint = fingerprint;
            this.syncState = syncState;
        }
    }

    private static final class CandidateMatch {
        final ExistingRow row;
        final int score;

        CandidateMatch(ExistingRow row, int score) {
            this.row = row;
            this.score = score;
        }
    }

    private static final class QueueDbHelper extends SQLiteOpenHelper {

        QueueDbHelper(Context context) {
            super(context, DB_NAME, null, DB_VERSION);
        }

        @Override
        public void onConfigure(SQLiteDatabase db) {
            super.onConfigure(db);
            db.setForeignKeyConstraintsEnabled(true);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + TABLE + " ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,"
                    + "event_id TEXT NOT NULL UNIQUE,"
                    + "source_app TEXT NOT NULL,"
                    + "source_record_id TEXT,"
                    + "event_type TEXT NOT NULL,"
                    + "direction TEXT NOT NULL,"
                    + "scope TEXT NOT NULL,"
                    + "amount_minor INTEGER NOT NULL,"
                    + "currency TEXT NOT NULL,"
                    + "occurred_at INTEGER NOT NULL,"
                    + "created_at INTEGER NOT NULL,"
                    + "account_hint TEXT NOT NULL DEFAULT '',"
                    + "merchant_hint TEXT NOT NULL DEFAULT '',"
                    + "category_hint TEXT NOT NULL DEFAULT '',"
                    + "linked_event_id TEXT,"
                    + "dedupe_fingerprint TEXT NOT NULL,"
                    + "sync_state TEXT NOT NULL,"
                    + "match_confidence TEXT NOT NULL,"
                    + "money_account_ref TEXT,"
                    + "money_category_ref TEXT,"
                    + "money_transaction_ref TEXT,"
                    + "family_finance_ref TEXT,"
                    + "family_grocery_ref TEXT,"
                    + "loan_ref TEXT,"
                    + "loan_payment_ref TEXT,"
                    + "duplicate_of_event_id TEXT,"
                    + "duplicate_score INTEGER NOT NULL DEFAULT 0,"
                    + "attempt_count INTEGER NOT NULL DEFAULT 0,"
                    + "last_error TEXT NOT NULL DEFAULT '',"
                    + "updated_at INTEGER NOT NULL,"
                    + "FOREIGN KEY(duplicate_of_event_id) REFERENCES " + TABLE
                    + "(event_id) ON UPDATE CASCADE ON DELETE SET NULL"
                    + ")");

            db.execSQL("CREATE UNIQUE INDEX idx_integration_source_record ON "
                    + TABLE + "(source_app, source_record_id)");
            db.execSQL("CREATE INDEX idx_integration_state ON "
                    + TABLE + "(sync_state, occurred_at)");
            db.execSQL("CREATE INDEX idx_integration_amount_time ON "
                    + TABLE + "(amount_minor, currency, occurred_at)");
            db.execSQL("CREATE INDEX idx_integration_fingerprint ON "
                    + TABLE + "(dedupe_fingerprint)");
            db.execSQL("CREATE INDEX idx_integration_duplicate_of ON "
                    + TABLE + "(duplicate_of_event_id)");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            if (oldVersion < 1) onCreate(db);
        }
    }
}
