package com.example.moneymanagerpro;

import androidx.annotation.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Builds privacy-safe, deterministic fingerprints for structured finance events.
 *
 * IMPORTANT: raw SMS bodies must never be passed here. Only already-extracted
 * structured metadata such as amount, account hint, merchant hint and category
 * hint is used. The returned fingerprint is a SHA-256 digest and contains no
 * readable financial text.
 */
public final class TridevEventFingerprint {

    private static final long BUCKET_MILLIS = 15L * 60L * 1000L;

    private TridevEventFingerprint() { }

    public static String build(TridevIntegrationContract.Event event) {
        if (event == null) throw new IllegalArgumentException("event is required");

        long bucket = event.occurredAt > 0L
                ? event.occurredAt / BUCKET_MILLIS
                : event.createdAt / BUCKET_MILLIS;

        String account = canonicalAccountHint(event.accountHint);
        String merchant = normalizeHint(event.merchantHint);
        String category = normalizeHint(event.categoryHint);

        String material = "v1|"
                + event.direction.name() + "|"
                + event.amountMinor + "|"
                + safeCurrency(event.currency) + "|"
                + bucket + "|"
                + account + "|"
                + merchant + "|"
                + category;
        return sha256(material);
    }

    /**
     * A coarser signature used only to find possible duplicates for scoring.
     * It deliberately excludes source app and event id so the same real-world
     * purchase reported by two different apps can be reconciled.
     */
    public static String buildAmountDirectionKey(TridevIntegrationContract.Event event) {
        if (event == null) throw new IllegalArgumentException("event is required");
        return event.amountMinor + "|"
                + event.direction.name() + "|"
                + safeCurrency(event.currency);
    }

    public static String canonicalAccountHint(@Nullable String value) {
        String normalized = normalizeHint(value);
        String lastFour = lastFour(value);
        if (!lastFour.isEmpty()) return "last4:" + lastFour;
        return normalized;
    }

    public static String normalizeHint(@Nullable String value) {
        if (value == null) return "";
        String normalized = value.toLowerCase(Locale.ROOT)
                .replace('•', ' ')
                .replaceAll("[^a-z0-9]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (normalized.length() > 120) {
            normalized = normalized.substring(0, 120).trim();
        }
        return normalized;
    }

    public static String lastFour(@Nullable String value) {
        if (value == null) return "";
        String digits = value.replaceAll("[^0-9]", "");
        return digits.length() >= 4
                ? digits.substring(digits.length() - 4)
                : "";
    }

    public static double tokenSimilarity(@Nullable String left, @Nullable String right) {
        Set<String> a = tokens(normalizeHint(left));
        Set<String> b = tokens(normalizeHint(right));
        if (a.isEmpty() || b.isEmpty()) return 0d;

        Set<String> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        if (intersection.isEmpty()) return 0d;

        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return (double) intersection.size() / (double) union.size();
    }

    private static Set<String> tokens(String value) {
        Set<String> result = new HashSet<>();
        if (value == null || value.isEmpty()) return result;
        for (String token : value.split(" ")) {
            if (token.length() >= 2) result.add(token);
        }
        return result;
    }

    private static String safeCurrency(@Nullable String currency) {
        String value = currency == null ? "" : currency.trim().toUpperCase(Locale.ROOT);
        return value.isEmpty() ? TridevIntegrationContract.DEFAULT_CURRENCY : value;
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte current : hash) {
                builder.append(String.format(Locale.ROOT, "%02x", current & 0xff));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException impossibleOnAndroid) {
            throw new IllegalStateException("SHA-256 unavailable", impossibleOnAndroid);
        }
    }
}
