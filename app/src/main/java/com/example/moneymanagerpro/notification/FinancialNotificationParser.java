package com.example.moneymanagerpro.notification;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Local-only parser for bank, card, wallet and UPI notifications. */
public final class FinancialNotificationParser {

    private static final Pattern AMOUNT_PATTERN = Pattern.compile(
            "(?i)(?:₹|rs\\.?|inr)\\s*[:=-]?\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)"
    );
    private static final Pattern LAST_FOUR_PATTERN = Pattern.compile(
            "(?i)(?:a/c|acct|account|card|ending|xx|x{2,}|\\*{2,})[^0-9]{0,12}([0-9]{4})"
    );
    private static final Pattern REFERENCE_PATTERN = Pattern.compile(
            "(?i)(?:ref(?:erence)?(?: no)?|utr|txn(?: id| no)?|transaction id|rrn)[:#\\s-]*([a-z0-9-]{6,})"
    );
    private static final Pattern VPA_PATTERN = Pattern.compile(
            "(?i)\\b([a-z0-9._-]{2,}@[a-z]{2,})\\b"
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
        String cleanTitle = normalize(title);
        String cleanBody = normalize(body);
        String combined = normalize(cleanTitle + " " + cleanBody);
        String lower = combined.toLowerCase(Locale.ROOT);

        if (!looksFinancial(lower) || looksPromotional(lower)) {
            return null;
        }

        Matcher amountMatcher = AMOUNT_PATTERN.matcher(combined);
        if (!amountMatcher.find()) {
            return null;
        }

        double amount;
        try {
            amount = Double.parseDouble(amountMatcher.group(1).replace(",", ""));
        } catch (Exception ignored) {
            return null;
        }

        if (amount <= 0d || amount > 999999999d) {
            return null;
        }

        String type = detectType(lower);
        if (type == null) {
            return null;
        }

        String lastFour = findGroup(LAST_FOUR_PATTERN, combined);
        String reference = findGroup(REFERENCE_PATTERN, combined);
        String vpa = findGroup(VPA_PATTERN, combined);
        String merchant = detectMerchant(combined, lower, vpa);
        String category = suggestCategory(lower, type);
        String source = detectSource(packageName, title, lower);

        String fingerprintSource = packageName + "|" + type + "|" + amount + "|"
                + lastFour + "|" + reference + "|" + merchant + "|" + cleanBody;
        String fingerprint = Integer.toHexString(
                fingerprintSource.toLowerCase(Locale.ROOT).hashCode()
        );

        return new ParsedNotification(
                fingerprint,
                packageName,
                cleanTitle,
                cleanBody,
                type,
                amount,
                merchant,
                lastFour,
                reference,
                vpa,
                category,
                source,
                postedAt
        );
    }

    private static boolean looksFinancial(String text) {
        boolean hasAmount = text.contains("₹")
                || text.contains("inr")
                || text.contains("rs.")
                || text.contains("rs ");

        boolean hasTransactionWord = containsAny(
                text,
                "debited", "credited", "spent", "paid", "payment", "purchase",
                "withdrawn", "withdrawal", "refund", "reversed", "received",
                "deposited", "transferred", "sent", "upi", "transaction", "txn"
        );

        return hasAmount && hasTransactionWord;
    }

    private static boolean looksPromotional(String text) {
        return containsAny(
                text,
                "get cashback", "win cashback", "offer", "coupon", "discount",
                "apply now", "pre-approved", "pre approved", "limited period",
                "reward points", "earn up to", "flat off"
        ) && !containsAny(text, "debited", "credited", "paid", "received", "withdrawn");
    }

    @Nullable
    private static String detectType(String text) {
        if (containsAny(
                text,
                "refund", "reversed", "reversal", "cashback credited", "credited",
                "received", "deposited", "salary credited", "money received"
        )) {
            return "INCOME";
        }

        if (containsAny(
                text,
                "debited", "spent", "paid", "purchase", "withdrawn", "withdrawal",
                "sent", "transferred to", "payment successful", "payment of"
        )) {
            return "EXPENSE";
        }

        return null;
    }

    private static String suggestCategory(String text, String type) {
        if ("INCOME".equals(type)) {
            if (containsAny(text, "salary", "payroll")) return "Salary";
            if (containsAny(text, "refund", "reversal")) return "Refund";
            if (text.contains("cashback")) return "Cashback";
            if (containsAny(text, "interest", "dividend")) return "Investment Income";
            return "Other Income";
        }

        if (containsAny(text, "fuel", "petrol", "diesel", "indian oil", "hpcl", "bpcl")) return "Fuel";
        if (containsAny(text, "swiggy", "zomato", "restaurant", "food", "cafe", "dominos")) return "Food";
        if (containsAny(text, "amazon", "flipkart", "myntra", "shopping", "mart", "store")) return "Shopping";
        if (containsAny(text, "uber", "ola", "metro", "railway", "irctc", "travel", "airline")) return "Travel";
        if (containsAny(text, "hospital", "pharmacy", "medical", "medicine", "clinic")) return "Medical";
        if (containsAny(text, "electricity", "recharge", "broadband", "postpaid", "utility", "bill")) return "Bills";
        if (containsAny(text, "school", "college", "tuition", "course", "education")) return "Education";
        if (containsAny(text, "insurance", "premium")) return "Insurance";
        if (containsAny(text, "emi", "loan")) return "Loan / EMI";
        if (containsAny(text, "atm", "withdraw")) return "Cash Withdrawal";
        return "Other Expense";
    }

    private static String detectMerchant(String original, String lower, String vpa) {
        String[] markers = {
                " paid to ", " sent to ", " transferred to ", " at ", " to ", " for ", " via "
        };

        for (String marker : markers) {
            int index = lower.indexOf(marker);
            if (index >= 0) {
                String candidate = original.substring(index + marker.length()).trim();
                candidate = candidate.split("(?i)(?:\\s+on\\s+|\\s+ref|\\s+utr|[.;|])")[0].trim();
                candidate = candidate.replaceAll("(?i)using.*$", "").trim();
                if (candidate.length() > 48) candidate = candidate.substring(0, 48).trim();
                if (!candidate.isEmpty()) return candidate;
            }
        }

        if (!vpa.isEmpty()) return vpa;
        return "Financial Transaction";
    }

    private static String detectSource(String packageName, String title, String lower) {
        String packageLower = packageName.toLowerCase(Locale.ROOT);
        if (packageLower.contains("phonepe")) return "PhonePe";
        if (packageLower.contains("google") && packageLower.contains("apps.nbu")) return "Google Pay";
        if (packageLower.contains("paytm")) return "Paytm";
        if (packageLower.contains("amazon")) return "Amazon Pay";
        if (lower.contains("hdfc")) return "HDFC Bank";
        if (lower.contains("sbi")) return "State Bank of India";
        if (lower.contains("icici")) return "ICICI Bank";
        if (lower.contains("axis")) return "Axis Bank";
        if (lower.contains("kotak")) return "Kotak Bank";
        if (lower.contains("bob") || lower.contains("bank of baroda")) return "Bank of Baroda";
        if (title != null && !title.trim().isEmpty()) return title.trim();
        return "Financial App";
    }

    private static boolean containsAny(String text, String... values) {
        for (String value : values) {
            if (text.contains(value)) return true;
        }
        return false;
    }

    private static String findGroup(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replace('\n', ' ').replaceAll("\\s+", " ").trim();
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
        public final String vpa;
        public final String category;
        public final String source;
        public final long postedAt;

        ParsedNotification(
                String id,
                String packageName,
                String title,
                String body,
                String type,
                double amount,
                String merchant,
                String lastFour,
                String reference,
                String vpa,
                String category,
                String source,
                long postedAt
        ) {
            this.id = id;
            this.packageName = packageName;
            this.title = title;
            this.body = body;
            this.type = type;
            this.amount = amount;
            this.merchant = merchant;
            this.lastFour = lastFour;
            this.reference = reference;
            this.vpa = vpa;
            this.category = category;
            this.source = source;
            this.postedAt = postedAt;
        }
    }
}
