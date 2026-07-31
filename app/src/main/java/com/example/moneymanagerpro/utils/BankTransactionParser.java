package com.example.moneymanagerpro.utils;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Common offline parser for bank SMS and payment-app notifications.
 *
 * This class only detects and structures a possible transaction.
 * It never saves anything automatically.
 *
 * The detected result must be reviewed by the user before it is
 * added to the Room database.
 */
public final class BankTransactionParser {

    public static final String TYPE_EXPENSE = "EXPENSE";
    public static final String TYPE_INCOME = "INCOME";

    public static final String SOURCE_SMS = "SMS";
    public static final String SOURCE_NOTIFICATION =
            "NOTIFICATION";

    /*
     * Supported examples:
     *
     * ₹1,250
     * INR 1,250.00
     * Rs.850
     * Rupees 500
     */
    private static final Pattern AMOUNT_PREFIX =
            Pattern.compile(
                    "(?i)"
                            + "(?:₹|INR|RS\\.?|RUPEES?)"
                            + "\\s*"
                            + "([0-9][0-9,]*(?:\\.[0-9]{1,2})?)"
            );

    /*
     * Supported examples:
     *
     * 1,250 INR
     * 850 Rs
     */
    private static final Pattern AMOUNT_SUFFIX =
            Pattern.compile(
                    "(?i)"
                            + "([0-9][0-9,]*(?:\\.[0-9]{1,2})?)"
                            + "\\s*"
                            + "(?:INR|RS\\.?)"
            );

    /*
     * Supported examples:
     *
     * UTR 123456789012
     * UPI Ref 987654321098
     * Ref No ABC123456
     * Txn ID T12345678
     * RRN 123456789012
     */
    private static final Pattern REFERENCE =
            Pattern.compile(
                    "(?i)"
                            + "(?:"
                            + "UTR"
                            + "|UPI\\s*(?:REF(?:ERENCE)?|TXN)"
                            + "|REF(?:ERENCE)?"
                            + "(?:\\s*(?:NO|NUMBER|ID))?"
                            + "|TXN(?:\\s*(?:NO|NUMBER|ID))?"
                            + "|TRANSACTION\\s*ID"
                            + "|RRN"
                            + ")"
                            + "\\s*[:#-]?\\s*"
                            + "([A-Z0-9][A-Z0-9/-]{5,39})"
            );

    /*
     * Supported examples:
     *
     * A/C XX1234
     * Account ending 4582
     * Card XXXX8899
     * Acct *1234
     */
    private static final Pattern ACCOUNT =
            Pattern.compile(
                    "(?i)"
                            + "(?:A/C|ACCT|ACCOUNT|CARD)"
                            + "(?:\\s*(?:NO\\.?|NUMBER))?"
                            + "\\s*"
                            + "(?:ENDING|XX|X+|\\*+|NO\\.?)?"
                            + "\\s*"
                            + "([A-Z0-9*X-]{3,20})"
            );

    /*
     * Expense merchant examples:
     *
     * paid to AMAZON
     * debited at INDIAN OIL
     * payment towards SCHOOL FEES
     */
    private static final Pattern EXPENSE_MERCHANT =
            Pattern.compile(
                    "(?i)"
                            + "(?:TO|AT|TOWARDS)"
                            + "\\s+"
                            + "([A-Z0-9]"
                            + "[A-Z0-9&@._'() /-]{1,48}?)"
                            + "(?="
                            + "\\s+(?:"
                            + "ON"
                            + "|USING"
                            + "|VIA"
                            + "|UPI"
                            + "|REF"
                            + "|TXN"
                            + "|TRANSACTION"
                            + "|FROM"
                            + "|AVBL"
                            + "|AVL"
                            + "|AVAILABLE"
                            + "|BALANCE"
                            + "|INFO"
                            + ")\\b"
                            + "|[.,;]"
                            + "|$"
                            + ")"
            );

    /*
     * Income merchant/source examples:
     *
     * credited from ABC COMPANY
     * received by XYZ PERSON
     */
    private static final Pattern INCOME_MERCHANT =
            Pattern.compile(
                    "(?i)"
                            + "(?:FROM|BY)"
                            + "\\s+"
                            + "([A-Z0-9]"
                            + "[A-Z0-9&@._'() /-]{1,48}?)"
                            + "(?="
                            + "\\s+(?:"
                            + "ON"
                            + "|USING"
                            + "|VIA"
                            + "|UPI"
                            + "|REF"
                            + "|TXN"
                            + "|TRANSACTION"
                            + "|INTO"
                            + "|TO"
                            + "|AVBL"
                            + "|AVL"
                            + "|AVAILABLE"
                            + "|BALANCE"
                            + "|INFO"
                            + ")\\b"
                            + "|[.,;]"
                            + "|$"
                            + ")"
            );

    private static final String[] EXPENSE_WORDS = {
            "debited",
            "debit",
            "spent",
            "paid",
            "payment of",
            "purchase",
            "purchased",
            "sent",
            "withdrawn",
            "withdrawal",
            "transferred to",
            "txn of",
            "transaction of"
    };

    private static final String[] INCOME_WORDS = {
            "credited",
            "credit of",
            "received",
            "deposited",
            "deposit of",
            "refund",
            "refunded",
            "reversal",
            "reversed",
            "cashback"
    };

    private static final String[] FINANCIAL_WORDS = {
            "account",
            "a/c",
            "acct",
            "card",
            "bank",
            "upi",
            "wallet",
            "transaction",
            "txn",
            "payment",
            "debited",
            "credited",
            "balance",
            "avl bal",
            "available balance",
            "utr",
            "rrn"
    };

    private static final String[] FAILED_WORDS = {
            "failed",
            "declined",
            "unsuccessful",
            "cancelled",
            "canceled",
            "could not be processed",
            "not processed"
    };

    private static final String[] OTP_WORDS = {
            " otp",
            "otp ",
            "one time password",
            "one-time password",
            "verification code",
            "do not share",
            "valid for",
            "expires in"
    };

    private BankTransactionParser() {
        /*
         * Utility class.
         * Object creation is not required.
         */
    }

    /**
     * Incoming bank SMS के लिए।
     */
    public static Result parseSms(
            String sender,
            String messageBody,
            long receivedAt
    ) {
        return parse(
                SOURCE_SMS,
                safe(sender),
                "",
                messageBody,
                "",
                receivedAt
        );
    }

    /**
     * Payment या banking app notification के लिए।
     */
    public static Result parseNotification(
            String packageName,
            String title,
            String messageBody,
            long postedAt
    ) {
        return parse(
                SOURCE_NOTIFICATION,
                resolveSourceName(
                        packageName,
                        title
                ),
                title,
                messageBody,
                packageName,
                postedAt
        );
    }

    /**
     * SMS और Notification दोनों के लिए common parsing method।
     */
    public static Result parse(
            String sourceType,
            String sourceName,
            String title,
            String messageBody,
            String sourcePackage,
            long eventTime
    ) {
        String cleanTitle =
                safe(title);

        String cleanBody =
                safe(messageBody);

        String combined =
                normalize(
                        cleanTitle
                                + " "
                                + cleanBody
                );

        String lower =
                combined.toLowerCase(
                        Locale.ROOT
                );

        Result result =
                new Result();

        result.sourceType =
                safe(sourceType);

        result.sourceName =
                firstNonEmpty(
                        sourceName,
                        resolveSourceName(
                                sourcePackage,
                                cleanTitle
                        ),
                        "Unknown source"
                );

        result.sourcePackage =
                safe(sourcePackage);

        result.title =
                cleanTitle;

        result.rawText =
                cleanBody;

        result.detectedAt =
                eventTime > 0
                        ? eventTime
                        : System.currentTimeMillis();

        if (combined.isEmpty()) {
            return invalid(
                    result,
                    "Empty message"
            );
        }

        /*
         * OTP या verification message को transaction नहीं मानना है।
         */
        if (containsAny(
                lower,
                OTP_WORDS
        )) {
            return invalid(
                    result,
                    "OTP or verification message"
            );
        }

        /*
         * Failed या declined payment save नहीं होगी।
         */
        if (containsAny(
                lower,
                FAILED_WORDS
        )) {
            return invalid(
                    result,
                    "Failed or declined transaction"
            );
        }

        AmountMatch amountMatch =
                findAmount(combined);

        if (amountMatch == null
                || amountMatch.amount <= 0) {

            return invalid(
                    result,
                    "Transaction amount not found"
            );
        }

        result.amount =
                amountMatch.amount;

        result.amountText =
                amountMatch.originalText;

        result.transactionType =
                detectTransactionType(lower);

        if (result.transactionType.isEmpty()) {
            return invalid(
                    result,
                    "Debit or credit direction not found"
            );
        }

        /*
         * सामान्य promotional message में केवल amount लिखा हो सकता है।
         * Financial keyword के बिना उसे transaction नहीं मानेंगे।
         */
        if (!containsAny(
                lower,
                FINANCIAL_WORDS
        )
                && !containsAny(
                lower,
                EXPENSE_WORDS
        )
                && !containsAny(
                lower,
                INCOME_WORDS
        )) {

            return invalid(
                    result,
                    "Not recognized as a financial transaction"
            );
        }

        result.reference =
                findGroup(
                        REFERENCE,
                        combined
                );

        result.accountHint =
                findGroup(
                        ACCOUNT,
                        combined
                );

        result.merchant =
                findMerchant(
                        combined,
                        result.transactionType
                );

        result.suggestedCategory =
                suggestCategory(
                        result.transactionType,
                        result.merchant,
                        combined
                );

        /*
         * Base confidence:
         *
         * Amount        = 35
         * Debit/Credit  = 25
         * Financial text= 15
         *
         * Total base    = 75
         */
        int score = 75;

        if (!result.reference.isEmpty()) {
            score += 10;
        }

        if (!result.merchant.isEmpty()) {
            score += 10;
        }

        if (!result.accountHint.isEmpty()) {
            score += 5;
        }

        result.confidence =
                Math.min(
                        score,
                        100
                );

        result.valid =
                result.confidence >= 60;

        result.reason =
                result.valid
                        ? "Transaction detected"
                        : "Low-confidence result";

        return result;
    }

    private static Result invalid(
            Result result,
            String reason
    ) {
        result.valid = false;
        result.reason = reason;

        return result;
    }

    /**
     * Debit/Credit keywords के आधार पर transaction type पहचानता है।
     */
    private static String detectTransactionType(
            String lowerText
    ) {
        boolean income =
                containsAny(
                        lowerText,
                        INCOME_WORDS
                );

        boolean expense =
                containsAny(
                        lowerText,
                        EXPENSE_WORDS
                );

        if (income && !expense) {
            return TYPE_INCOME;
        }

        if (expense && !income) {
            return TYPE_EXPENSE;
        }

        /*
         * कभी-कभी message में debit और reversal दोनों शब्द आ सकते हैं।
         * जो keyword पहले आया है, उसे प्राथमिकता दी जाएगी।
         */
        if (income && expense) {
            int incomeIndex =
                    firstIndexOfAny(
                            lowerText,
                            INCOME_WORDS
                    );

            int expenseIndex =
                    firstIndexOfAny(
                            lowerText,
                            EXPENSE_WORDS
                    );

            return incomeIndex >= 0
                    && (expenseIndex < 0
                    || incomeIndex < expenseIndex)
                    ? TYPE_INCOME
                    : TYPE_EXPENSE;
        }

        return "";
    }

    private static AmountMatch findAmount(
            String text
    ) {
        AmountMatch match =
                findAmount(
                        AMOUNT_PREFIX,
                        text
                );

        if (match != null) {
            return match;
        }

        return findAmount(
                AMOUNT_SUFFIX,
                text
        );
    }

    private static AmountMatch findAmount(
            Pattern pattern,
            String text
    ) {
        Matcher matcher =
                pattern.matcher(text);

        while (matcher.find()) {
            String numeric =
                    safe(
                            matcher.group(1)
                    ).replace(
                            ",",
                            ""
                    );

            try {
                double amount =
                        Double.parseDouble(
                                numeric
                        );

                if (amount > 0) {
                    return new AmountMatch(
                            amount,
                            matcher
                                    .group(0)
                                    .trim()
                    );
                }

            } catch (NumberFormatException ignored) {
                /*
                 * Invalid amount pattern ignored.
                 */
            }
        }

        return null;
    }

    private static String findMerchant(
            String text,
            String type
    ) {
        Pattern pattern =
                TYPE_INCOME.equals(type)
                        ? INCOME_MERCHANT
                        : EXPENSE_MERCHANT;

        String merchant =
                normalize(
                        findGroup(
                                pattern,
                                text
                        )
                )
                        .replaceAll(
                                "(?i)"
                                        + "\\b"
                                        + "(?:A/C|ACCT|ACCOUNT|CARD)"
                                        + "\\b.*$",
                                ""
                        )
                        .replaceAll(
                                "^[^A-Za-z0-9]+"
                                        + "|"
                                        + "[^A-Za-z0-9)]+$",
                                ""
                        )
                        .trim();

        if (merchant.length() > 48) {
            merchant =
                    merchant
                            .substring(
                                    0,
                                    48
                            )
                            .trim();
        }

        String lower =
                merchant.toLowerCase(
                        Locale.ROOT
                );

        /*
         * इन values को merchant नहीं मानना है।
         */
        if (lower.isEmpty()
                || lower.equals(
                "your account"
        )
                || lower.equals(
                "your a/c"
        )
                || lower.equals(
                "account"
        )
                || lower.equals(
                "card"
        )
                || lower.equals(
                "upi"
        )
                || lower.matches(
                "[x*0-9 -]{3,}"
        )) {

            return "";
        }

        return merchant;
    }

    /**
     * Merchant और message keywords के आधार पर category suggestion।
     */
    private static String suggestCategory(
            String type,
            String merchant,
            String fullText
    ) {
        String text =
                (
                        merchant
                                + " "
                                + fullText
                ).toLowerCase(
                        Locale.ROOT
                );

        if (TYPE_INCOME.equals(type)) {

            if (containsAny(
                    text,
                    "salary",
                    "payroll",
                    "wages"
            )) {
                return "Salary";
            }

            if (containsAny(
                    text,
                    "refund",
                    "reversal",
                    "reversed",
                    "cashback"
            )) {
                return "Refund";
            }

            if (containsAny(
                    text,
                    "interest",
                    "dividend"
            )) {
                return "Interest";
            }

            return "Other Income";
        }

        if (containsAny(
                text,
                "swiggy",
                "zomato",
                "restaurant",
                "cafe",
                "coffee",
                "food",
                "pizza",
                "burger",
                "domino",
                "mcdonald"
        )) {
            return "Food";
        }

        if (containsAny(
                text,
                "grocery",
                "supermarket",
                "mart",
                "bigbasket",
                "blinkit",
                "zepto",
                "dmart",
                "reliance fresh"
        )) {
            return "Grocery";
        }

        if (containsAny(
                text,
                "petrol",
                "diesel",
                "fuel",
                "indian oil",
                "iocl",
                "bharat petroleum",
                "bpcl",
                "hindustan petroleum",
                "hpcl"
        )) {
            return "Fuel";
        }

        if (containsAny(
                text,
                "amazon",
                "flipkart",
                "myntra",
                "meesho",
                "shopping",
                "store",
                "retail"
        )) {
            return "Shopping";
        }

        if (containsAny(
                text,
                "electricity",
                "water bill",
                "gas bill",
                "broadband",
                "mobile recharge",
                "recharge",
                "airtel",
                "jio",
                "bsnl",
                "utility"
        )) {
            return "Bills";
        }

        if (containsAny(
                text,
                "uber",
                "ola",
                "rapido",
                "railway",
                "irctc",
                "metro",
                "flight",
                "airlines",
                "travel",
                "bus"
        )) {
            return "Travel";
        }

        if (containsAny(
                text,
                "hospital",
                "clinic",
                "medical",
                "pharmacy",
                "medicine",
                "apollo",
                "doctor",
                "health"
        )) {
            return "Health";
        }

        if (containsAny(
                text,
                "school",
                "college",
                "university",
                "tuition",
                "course",
                "education",
                "fees",
                "fee payment"
        )) {
            return "Education";
        }

        if (containsAny(
                text,
                "netflix",
                "spotify",
                "hotstar",
                "prime video",
                "cinema",
                "movie",
                "gaming",
                "entertainment"
        )) {
            return "Entertainment";
        }

        if (containsAny(
                text,
                "emi",
                "loan",
                "credit card bill"
        )) {
            return "EMI / Loan";
        }

        if (containsAny(
                text,
                "transfer",
                "self transfer"
        )) {
            return "Transfer";
        }

        return "Other Expense";
    }

    private static String findGroup(
            Pattern pattern,
            String text
    ) {
        Matcher matcher =
                pattern.matcher(text);

        if (!matcher.find()) {
            return "";
        }

        return normalize(
                matcher.group(1)
        );
    }

    private static boolean containsAny(
            String text,
            String... values
    ) {
        if (text == null
                || text.isEmpty()) {

            return false;
        }

        for (String value : values) {

            if (value != null
                    && !value.isEmpty()
                    && text.contains(
                    value.toLowerCase(
                            Locale.ROOT
                    )
            )) {

                return true;
            }
        }

        return false;
    }

    private static int firstIndexOfAny(
            String text,
            String... values
    ) {
        int best = -1;

        for (String value : values) {
            int index =
                    text.indexOf(
                            value.toLowerCase(
                                    Locale.ROOT
                            )
                    );

            if (index >= 0
                    && (best < 0
                    || index < best)) {

                best = index;
            }
        }

        return best;
    }

    /**
     * Known payment apps के package name को readable नाम में बदलता है।
     */
    private static String resolveSourceName(
            String packageName,
            String title
    ) {
        String packageValue =
                safe(packageName)
                        .toLowerCase(
                                Locale.ROOT
                        );

        if (packageValue.equals(
                "com.google.android.apps.nbu.paisa.user"
        )) {
            return "Google Pay";
        }

        if (packageValue.contains(
                "phonepe"
        )) {
            return "PhonePe";
        }

        if (packageValue.contains(
                "paytm"
        )
                || packageValue.contains(
                "one97"
        )) {
            return "Paytm";
        }

        if (packageValue.contains(
                "npci"
        )
                || packageValue.contains(
                "bhim"
        )) {
            return "BHIM";
        }

        if (packageValue.contains(
                "amazon"
        )) {
            return "Amazon Pay";
        }

        if (!safe(title).isEmpty()) {
            return safe(title);
        }

        return safe(packageName);
    }

    private static String normalize(
            String value
    ) {
        return safe(value)
                .replace(
                        '\n',
                        ' '
                )
                .replace(
                        '\r',
                        ' '
                )
                .replaceAll(
                        "\\s+",
                        " "
                )
                .trim();
    }

    private static String firstNonEmpty(
            String first,
            String second,
            String fallback
    ) {
        if (!safe(first).isEmpty()) {
            return safe(first);
        }

        if (!safe(second).isEmpty()) {
            return safe(second);
        }

        return safe(fallback);
    }

    private static String safe(
            String value
    ) {
        return value == null
                ? ""
                : value.trim();
    }

    private static final class AmountMatch {

        private final double amount;
        private final String originalText;

        private AmountMatch(
                double amount,
                String originalText
        ) {
            this.amount = amount;
            this.originalText = originalText;
        }
    }

    /**
     * Structured result returned by SMS/Notification parser.
     */
    public static final class Result {

        private boolean valid;

        private String transactionType = "";

        private double amount;

        private String amountText = "";

        private String merchant = "";

        private String accountHint = "";

        private String reference = "";

        private String sourceType = "";

        private String sourceName = "";

        private String sourcePackage = "";

        private String suggestedCategory = "";

        private String title = "";

        private String rawText = "";

        private int confidence;

        private long detectedAt;

        private String reason = "";

        private Result() {
        }

        public boolean isValid() {
            return valid;
        }

        public String getTransactionType() {
            return transactionType;
        }

        public double getAmount() {
            return amount;
        }

        public String getAmountText() {
            return amountText;
        }

        public String getMerchant() {
            return merchant;
        }

        public String getAccountHint() {
            return accountHint;
        }

        public String getReference() {
            return reference;
        }

        public String getSourceType() {
            return sourceType;
        }

        public String getSourceName() {
            return sourceName;
        }

        public String getSourcePackage() {
            return sourcePackage;
        }

        public String getSuggestedCategory() {
            return suggestedCategory;
        }

        public String getTitle() {
            return title;
        }

        public String getRawText() {
            return rawText;
        }

        public int getConfidence() {
            return confidence;
        }

        public long getDetectedAt() {
            return detectedAt;
        }

        public String getReason() {
            return reason;
        }

        /**
         * Add Expense/Income note field के लिए उपयोगी summary।
         */
        public String buildReviewNote() {
            StringBuilder note =
                    new StringBuilder();

            appendPart(
                    note,
                    merchant.isEmpty()
                            ? ""
                            : "Merchant: "
                              + merchant
            );

            appendPart(
                    note,
                    reference.isEmpty()
                            ? ""
                            : "Reference: "
                              + reference
            );

            appendPart(
                    note,
                    sourceName.isEmpty()
                            ? ""
                            : "Detected via: "
                              + sourceName
            );

            return note.toString();
        }

        private static void appendPart(
                StringBuilder builder,
                String value
        ) {
            if (value == null
                    || value.trim().isEmpty()) {

                return;
            }

            if (builder.length() > 0) {
                builder.append(" | ");
            }

            builder.append(
                    value.trim()
            );
        }
    }
}