package com.example.moneymanagerpro.notification;

import androidx.annotation.NonNull;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Identifies completed financial/transactional SMS messages.
 * OTP and verification messages are intentionally excluded because they do not
 * prove that a transaction was completed.
 */
public final class SmsFinancialClassifier {

    private static final Pattern AMOUNT_PATTERN = Pattern.compile(
            "(?i)(?:₹|rs\\.?|inr)\\s*[0-9][0-9,]*(?:\\.[0-9]{1,2})?"
    );

    private SmsFinancialClassifier() {
    }

    public static boolean isFinancialMessage(
            @NonNull String sender,
            @NonNull String message
    ) {
        String lower = (sender + " " + message)
                .toLowerCase(Locale.ROOT)
                .replace('\n', ' ')
                .trim();

        if (lower.isEmpty()) return false;

        if (containsAny(
                lower,
                "otp",
                "one time password",
                "verification code",
                "do not share this code",
                "login code"
        )) {
            return false;
        }

        boolean hasTransactionAction = containsAny(
                lower,
                "debited",
                "credited",
                "has been debit",
                "has been credit",
                "spent",
                "paid",
                "payment successful",
                "payment received",
                "received",
                "withdrawn",
                "withdrawal",
                "deposited",
                "refund",
                "refunded",
                "reversal",
                "reversed",
                "cash withdrawal",
                "purchase of",
                "sent to",
                "transferred to",
                "transferred from"
        );

        boolean hasFinancialRail = containsAny(
                lower,
                "upi",
                "imps",
                "neft",
                "rtgs",
                "txn",
                "transaction",
                "a/c",
                "acct",
                "account",
                "credit card",
                "debit card",
                "card ending",
                "available balance",
                "avl bal",
                "wallet"
        );

        boolean hasAmount = AMOUNT_PATTERN.matcher(lower).find();

        if (hasTransactionAction && (hasAmount || hasFinancialRail)) {
            return true;
        }

        return hasAmount && hasFinancialRail && containsAny(
                lower,
                "successful",
                "successfully",
                "completed",
                "processed"
        );
    }

    @NonNull
    public static String category(@NonNull String message) {
        String lower = message.toLowerCase(Locale.ROOT);
        if (containsAny(
                lower,
                "debited",
                "credited",
                "upi",
                "a/c",
                "account",
                "bank",
                "txn",
                "transaction",
                "payment",
                "paid",
                "withdrawn",
                "received",
                "refund",
                "imps",
                "neft",
                "rtgs",
                "card"
        )) {
            return "BANKING";
        }
        if (containsAny(
                lower,
                "offer",
                "discount",
                "sale",
                "coupon",
                "cashback",
                "deal"
        )) {
            return "OFFERS";
        }
        return "OTHER";
    }

    private static boolean containsAny(String text, String... values) {
        for (String value : values) {
            if (text.contains(value)) return true;
        }
        return false;
    }
}
