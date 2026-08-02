package com.example.moneymanagerpro.notification;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Local-only parser for bank, card, wallet and UPI notifications. */
public final class FinancialNotificationParser {

    private static final Pattern AMOUNT_PATTERN = Pattern.compile(
            "(?i)(?:₹|rs\\.?|inr)\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)"
    );
    private static final Pattern LAST_FOUR_PATTERN = Pattern.compile(
            "(?i)(?:a/c|acct|account|card|xx|x{2,})[^0-9]{0,8}([0-9]{4})"
    );
    private static final Pattern REFERENCE_PATTERN = Pattern.compile(
            "(?i)(?:ref(?:erence)?|utr|txn(?: id)?|rrn)[:#\\s-]*([a-z0-9-]{6,})"
    );

    private FinancialNotificationParser() {
    }

    @Nullable
    public static ParsedNotification parse(
            @NonNull String packageName,
            @NonNull String title,
            @NonNull String body,
            long postedAt
    ) {
        String combined = (title + " " + body).trim();
        String lower = combined.toLowerCase(Locale.ROOT);

        if (!looksFinancial(lower)) return null;

        Matcher amountMatcher = AMOUNT_PATTERN.matcher(combined);
        if (!amountMatcher.find()) return null;

        double amount;
        try {
            amount = Double.parseDouble(amountMatcher.group(1).replace(",", ""));
        } catch (Exception ignored) {
            return null;
        }
        if (amount <= 0d) return null;

        String type = detectType(lower);
        if (type == null) return null;

        String lastFour = findGroup(LAST_FOUR_PATTERN, combined);
        String reference = findGroup(REFERENCE_PATTERN, combined);
        String merchant = detectMerchant(combined, lower);
        String category = suggestCategory(lower, type);

        String fingerprint = Integer.toHexString(
                (packageName + "|" + type + "|" + amount + "|" + lastFour + "|" + reference + "|" + body)
                        .toLowerCase(Locale.ROOT)
                        .hashCode()
        );

        return new ParsedNotification(
                fingerprint,
                packageName,
                title,
                body,
                type,
                amount,
                merchant,
                lastFour,
                reference,
                category,
                postedAt
        );
    }

    private static boolean looksFinancial(String text) {
        boolean amountWord = text.contains("₹") || text.contains("inr") || text.contains("rs.") || text.contains("rs ");
        boolean transactionWord = text.contains("debited") || text.contains("credited")
                || text.contains("spent") || text.contains("paid") || text.contains("payment")
                || text.contains("purchase") || text.contains("withdrawn") || text.contains("refund")
                || text.contains("received") || text.contains("upi") || text.contains("transaction");
        return amountWord && transactionWord;
    }

    @Nullable
    private static String detectType(String text) {
        if (text.contains("refund") || text.contains("reversed") || text.contains("cashback")
                || text.contains("credited") || text.contains("received") || text.contains("deposited")) {
            return "INCOME";
        }
        if (text.contains("debited") || text.contains("spent") || text.contains("paid")
                || text.contains("purchase") || text.contains("withdrawn") || text.contains("sent")) {
            return "EXPENSE";
        }
        return null;
    }

    private static String suggestCategory(String text, String type) {
        if ("INCOME".equals(type)) {
            if (text.contains("salary")) return "Salary";
            if (text.contains("refund") || text.contains("cashback")) return "Refund";
            return "Other Income";
        }
        if (containsAny(text, "fuel", "petrol", "diesel", "indian oil", "hpcl", "bpcl")) return "Fuel";
        if (containsAny(text, "swiggy", "zomato", "restaurant", "food", "cafe")) return "Food";
        if (containsAny(text, "amazon", "flipkart", "myntra", "shopping", "store")) return "Shopping";
        if (containsAny(text, "uber", "ola", "metro", "railway", "irctc", "travel")) return "Travel";
        if (containsAny(text, "hospital", "pharmacy", "medical", "medicine")) return "Medical";
        if (containsAny(text, "electricity", "recharge", "broadband", "bill")) return "Bills";
        if (text.contains("atm") || text.contains("withdraw")) return "Cash Withdrawal";
        return "Other Expense";
    }

    private static boolean containsAny(String text, String... values) {
        for (String value : values) if (text.contains(value)) return true;
        return false;
    }

    private static String detectMerchant(String original, String lower) {
        String[] markers = {" at ", " to ", " for ", " via "};
        for (String marker : markers) {
            int index = lower.indexOf(marker);
            if (index >= 0) {
                String candidate = original.substring(index + marker.length()).trim();
                candidate = candidate.split("[.;|]")[0].trim();
                if (candidate.length() > 40) candidate = candidate.substring(0, 40).trim();
                if (!candidate.isEmpty()) return candidate;
            }
        }
        return "Financial Notification";
    }

    private static String findGroup(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : "";
    }

    public static final class ParsedNotification {
        public final String id;
        public final String packageName;
        public final String title;
        public final String body;
        public final String type;
        public final double amount;
        public final String merchant;
        public final String lastFour;
        public final String reference;
        public final String category;
        public final long postedAt;

        ParsedNotification(String id, String packageName, String title, String body,
                           String type, double amount, String merchant, String lastFour,
                           String reference, String category, long postedAt) {
            this.id = id;
            this.packageName = packageName;
            this.title = title;
            this.body = body;
            this.type = type;
            this.amount = amount;
            this.merchant = merchant;
            this.lastFour = lastFour;
            this.reference = reference;
            this.category = category;
            this.postedAt = postedAt;
        }
    }
}
