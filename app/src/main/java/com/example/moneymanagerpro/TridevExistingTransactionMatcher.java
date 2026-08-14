package com.example.moneymanagerpro;

import android.content.Context;
import android.database.Cursor;

import androidx.annotation.Nullable;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.moneymanagerpro.database.DatabaseClient;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Read-only matcher used before a queued integration event is ever posted into
 * MoneyManagerPro. Its purpose is to recognise a transaction that the user has
 * already entered manually and prevent the integration layer from creating it
 * again.
 *
 * No existing transaction is updated/deleted by this class.
 */
public final class TridevExistingTransactionMatcher {

    private static final long MAX_TIME_DELTA = 24L * 60L * 60L * 1000L;
    private static final int MAX_AMOUNT_CANDIDATES = 100;

    public static final class Match {
        public final long transactionId;
        public final int score;
        public final String account;
        public final String category;
        public final String reason;

        private Match(
                long transactionId,
                int score,
                String account,
                String category,
                String reason) {
            this.transactionId = transactionId;
            this.score = score;
            this.account = account == null ? "" : account;
            this.category = category == null ? "" : category;
            this.reason = reason == null ? "" : reason;
        }
    }

    private final Context appContext;

    public TridevExistingTransactionMatcher(Context context) {
        appContext = context.getApplicationContext();
    }

    @Nullable
    public Match findBest(TridevIntegrationContract.Event event) {
        if (event == null || event.amountMinor <= 0L) return null;

        SupportSQLiteDatabase db = DatabaseClient
                .getInstance(appContext)
                .getAppDatabase()
                .getOpenHelper()
                .getReadableDatabase();

        double amount = event.amountMinor / 100.0d;
        List<Candidate> candidates = new ArrayList<>();
        try (Cursor cursor = db.query(
                "SELECT id, type, amount, category, account, note, date "
                        + "FROM transactions "
                        + "WHERE ABS(amount - ?) < 0.005 "
                        + "ORDER BY id DESC LIMIT " + MAX_AMOUNT_CANDIDATES,
                new Object[]{amount})) {
            while (cursor.moveToNext()) {
                candidates.add(new Candidate(
                        getLong(cursor, "id"),
                        getString(cursor, "type"),
                        getString(cursor, "category"),
                        getString(cursor, "account"),
                        getString(cursor, "note"),
                        getString(cursor, "date")));
            }
        } catch (RuntimeException unavailable) {
            return null;
        }

        Candidate best = null;
        int bestScore = 0;
        for (Candidate candidate : candidates) {
            int score = score(event, candidate);
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }

        if (best == null || bestScore < 65) return null;
        String reason = bestScore >= 90
                ? "Existing MoneyManager transaction strongly matches this event"
                : "Possible existing MoneyManager transaction requires review";
        return new Match(
                best.id,
                bestScore,
                best.account,
                best.category,
                reason);
    }

    private int score(TridevIntegrationContract.Event event, Candidate candidate) {
        int score = 25; // amount is already an exact two-decimal candidate.

        String expectedType = expectedMoneyManagerType(event);
        if (!expectedType.isEmpty()) {
            if (expectedType.equalsIgnoreCase(candidate.type)) score += 15;
            else return 0;
        }

        long eventTime = event.occurredAt > 0L ? event.occurredAt : event.createdAt;
        Long candidateTime = parseMoneyManagerDate(candidate.date);
        if (candidateTime != null) {
            long delta = Math.abs(eventTime - candidateTime);
            if (delta > MAX_TIME_DELTA) return 0;
            if (delta <= 5L * 60L * 1000L) score += 25;
            else if (delta <= 30L * 60L * 1000L) score += 20;
            else if (delta <= 2L * 60L * 60L * 1000L) score += 15;
            else if (delta <= 6L * 60L * 60L * 1000L) score += 8;
            else score += 3;
        }

        boolean identityEvidence = false;
        String incomingLastFour = TridevEventFingerprint.lastFour(event.accountHint);
        String existingLastFour = TridevEventFingerprint.lastFour(candidate.account);
        String incomingAccount = TridevEventFingerprint.normalizeHint(event.accountHint);
        String existingAccount = TridevEventFingerprint.normalizeHint(candidate.account);
        if (!incomingLastFour.isEmpty() && incomingLastFour.equals(existingLastFour)) {
            score += 25;
            identityEvidence = true;
        } else if (!incomingAccount.isEmpty() && incomingAccount.equals(existingAccount)) {
            score += 20;
            identityEvidence = true;
        } else if (TridevEventFingerprint.tokenSimilarity(
                incomingAccount,
                existingAccount) >= 0.60d) {
            score += 12;
            identityEvidence = true;
        }

        String incomingCategory = TridevEventFingerprint.normalizeHint(event.categoryHint);
        String existingCategory = TridevEventFingerprint.normalizeHint(candidate.category);
        if (!incomingCategory.isEmpty() && incomingCategory.equals(existingCategory)) {
            score += 10;
        } else if (TridevEventFingerprint.tokenSimilarity(
                incomingCategory,
                existingCategory) >= 0.60d) {
            score += 5;
        }

        String incomingMerchant = TridevEventFingerprint.normalizeHint(event.merchantHint);
        if (!incomingMerchant.isEmpty()) {
            double noteSimilarity = TridevEventFingerprint.tokenSimilarity(
                    incomingMerchant,
                    candidate.note);
            if (noteSimilarity >= 0.75d) {
                score += 15;
                identityEvidence = true;
            } else if (noteSimilarity >= 0.50d) {
                score += 8;
            }
        }

        // Amount/type/time alone can suggest a possible duplicate, but an
        // automatic match should normally have account/card or merchant identity.
        if (!identityEvidence && score >= 90) score = 89;
        return Math.max(0, Math.min(100, score));
    }

    private String expectedMoneyManagerType(TridevIntegrationContract.Event event) {
        if (event.direction == TridevIntegrationContract.Direction.DEBIT) return "EXPENSE";
        if (event.direction == TridevIntegrationContract.Direction.CREDIT) return "INCOME";

        switch (event.eventType) {
            case INCOME:
            case REFUND:
                return "INCOME";
            case EXPENSE:
            case GROCERY_PURCHASE:
            case BILL_PAYMENT:
            case LOAN_PAYMENT:
                return "EXPENSE";
            default:
                return "";
        }
    }

    @Nullable
    private Long parseMoneyManagerDate(@Nullable String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        String value = raw.trim();
        String[] patterns = {
                "yyyy-MM-dd HH:mm",
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd",
                "dd-MM-yyyy HH:mm",
                "dd/MM/yyyy HH:mm",
                "dd-MM-yyyy",
                "dd/MM/yyyy"
        };
        for (String pattern : patterns) {
            SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
            format.setLenient(false);
            try {
                Date parsed = format.parse(value);
                if (parsed != null) return parsed.getTime();
            } catch (ParseException ignored) {
            }
        }
        return null;
    }

    private long getLong(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        return index < 0 || cursor.isNull(index) ? 0L : cursor.getLong(index);
    }

    private String getString(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        return index < 0 || cursor.isNull(index) ? "" : cursor.getString(index);
    }

    private static final class Candidate {
        final long id;
        final String type;
        final String category;
        final String account;
        final String note;
        final String date;

        Candidate(
                long id,
                String type,
                String category,
                String account,
                String note,
                String date) {
            this.id = id;
            this.type = type == null ? "" : type;
            this.category = category == null ? "" : category;
            this.account = account == null ? "" : account;
            this.note = note == null ? "" : note;
            this.date = date == null ? "" : date;
        }
    }
}
