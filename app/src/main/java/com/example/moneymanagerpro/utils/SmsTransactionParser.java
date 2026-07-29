package com.example.moneymanagerpro.utils;

import java.io.Serializable;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SmsTransactionParser {

    public enum Type {
        EXPENSE,
        INCOME,
        UNKNOWN
    }

    public static final class Result
            implements Serializable {

        private final Double amount;
        private final Type type;
        private final String bank;
        private final String merchant;
        private final String reference;
        private final String categorySuggestion;
        private final boolean successful;
        private final int confidence;

        private Result(
                Double amount,
                Type type,
                String bank,
                String merchant,
                String reference,
                String categorySuggestion,
                boolean successful,
                int confidence
        ) {
            this.amount = amount;
            this.type = type;
            this.bank = safe(bank);
            this.merchant = safe(merchant);
            this.reference = safe(reference);
            this.categorySuggestion =
                    safe(categorySuggestion);
            this.successful = successful;
            this.confidence = confidence;
        }

        public Double getAmount() {
            return amount;
        }

        public Type getType() {
            return type;
        }

        public String getBank() {
            return bank;
        }

        public String getMerchant() {
            return merchant;
        }

        public String getReference() {
            return reference;
        }

        public String getCategorySuggestion() {
            return categorySuggestion;
        }

        public boolean isSuccessful() {
            return successful;
        }

        public int getConfidence() {
            return confidence;
        }

        public boolean isFinancialTransaction() {
            return amount != null
                    && amount > 0
                    && type != Type.UNKNOWN
                    && successful;
        }

        public boolean isHighConfidence() {
            return isFinancialTransaction()
                    && confidence >= 75;
        }
    }

    private SmsTransactionParser() {
    }

    public static Result parse(String message) {
        String normalized =
                safe(message)
                        .replace('\u20b9', '₹');
        String lower =
                normalized.toLowerCase(
                        Locale.ENGLISH
                );

        TransactionScreenshotParser.Result base =
                TransactionScreenshotParser.parse(
                        normalized
                );
        Double amount = parseSmsAmount(normalized);

        if (amount == null) {
            amount = base.getAmount();
        }

        Type type = detectType(lower, base);
        boolean successful =
                !containsAny(
                        lower,
                        "failed",
                        "declined",
                        "unsuccessful",
                        "reversed",
                        "pending",
                        "processing"
                );

        String category =
                suggestCategory(
                        lower,
                        type
                );

        int confidence = 0;

        if (amount != null) {
            confidence += 35;
        }
        if (type != Type.UNKNOWN) {
            confidence += 30;
        }
        if (!base.getReference().isEmpty()) {
            confidence += 15;
        }
        if (!base.getBank().isEmpty()) {
            confidence += 10;
        }
        if (!base.getMerchant().isEmpty()) {
            confidence += 5;
        }
        if (containsAny(
                lower,
                "debited",
                "credited",
                "spent",
                "received",
                "paid",
                "sent"
        )) {
            confidence += 5;
        }
        if (!successful) {
            confidence = 0;
        }

        return new Result(
                amount,
                type,
                base.getBank(),
                base.getMerchant(),
                base.getReference(),
                category,
                successful,
                Math.min(confidence, 100)
        );
    }

    private static Type detectType(
            String lower,
            TransactionScreenshotParser.Result base
    ) {
        if (containsAny(
                lower,
                "credited",
                "credit alert",
                "received",
                "deposited",
                "salary credited",
                "refund credited"
        )
                || base.getDirection()
                == TransactionScreenshotParser
                .Direction.INCOMING) {
            return Type.INCOME;
        }

        if (containsAny(
                lower,
                "debited",
                "debit alert",
                "spent",
                "paid",
                "sent",
                "purchase",
                "withdrawn",
                "txn of"
        )
                || base.getDirection()
                == TransactionScreenshotParser
                .Direction.OUTGOING) {
            return Type.EXPENSE;
        }

        return Type.UNKNOWN;
    }

    private static Double parseSmsAmount(
            String message
    ) {
        Pattern pattern = Pattern.compile(
                "(?i)(?:₹|INR|Rs\\.?)\\s*"
                        + "([0-9][0-9,]*"
                        + "(?:\\.[0-9]{1,2})?)"
        );
        Matcher matcher = pattern.matcher(message);

        if (!matcher.find()) {
            return null;
        }

        try {
            double amount = Double.parseDouble(
                    matcher.group(1)
                            .replace(",", "")
            );

            return amount > 0
                    ? amount
                    : null;
        } catch (Exception exception) {
            return null;
        }
    }

    private static String suggestCategory(
            String lower,
            Type type
    ) {
        if (type == Type.INCOME) {
            if (containsAny(
                    lower,
                    "salary",
                    "payroll"
            )) {
                return "Salary";
            }
            if (containsAny(
                    lower,
                    "interest",
                    "dividend"
            )) {
                return "Investment";
            }
            if (lower.contains("refund")) {
                return "Refund";
            }
            return "Other Income";
        }

        if (containsAny(
                lower,
                "swiggy",
                "zomato",
                "restaurant",
                "food",
                "cafe"
        )) {
            return "Food";
        }
        if (containsAny(
                lower,
                "uber",
                "ola",
                "metro",
                "railway",
                "irctc",
                "fuel",
                "petrol"
        )) {
            return "Travel";
        }
        if (containsAny(
                lower,
                "electricity",
                "recharge",
                "broadband",
                "mobile bill",
                "gas bill"
        )) {
            return "Bills";
        }
        if (containsAny(
                lower,
                "amazon",
                "flipkart",
                "myntra",
                "shopping"
        )) {
            return "Shopping";
        }
        if (containsAny(
                lower,
                "atm",
                "cash withdrawal",
                "withdrawn"
        )) {
            return "Cash Withdrawal";
        }

        return "Other Expense";
    }

    private static boolean containsAny(
            String value,
            String... candidates
    ) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }

        return false;
    }

    private static String safe(String value) {
        return value == null
                ? ""
                : value.trim();
    }
}
