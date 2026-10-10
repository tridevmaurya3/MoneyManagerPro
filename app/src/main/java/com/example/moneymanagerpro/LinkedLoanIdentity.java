package com.example.moneymanagerpro;

import java.math.BigDecimal;

/** Pure identity/money rules shared by UI and background loan commands. */
public final class LinkedLoanIdentity {
    private LinkedLoanIdentity() { }
    public static String marker(String note, String prefix) {
        String value = note == null ? "" : note.trim();
        int start = value.indexOf(prefix), end = start < 0 ? -1 : value.indexOf(']', start);
        return end < 0 ? "" : value.substring(start, end + 1);
    }
    public static String value(String note, String prefix) {
        String marker = marker(note, prefix);
        return marker.isEmpty() ? "" : marker.substring(prefix.length(), marker.length() - 1);
    }
    public static String key(String note) {
        String key = value(note, "[LMP_LINK:");
        return key.matches("[a-fA-F0-9\\-]{36}:[1-9][0-9]*") ? key : "";
    }
    public static String humanNote(String note) {
        return (note == null ? "" : note).replaceAll(
                "\\s*\\[(LMP_LINK|LMP_REV|MM_ORIGIN|LMP_PAYMENT|LMP_TERM|LMP_PAYOFF):[^\\]]*\\]", "").trim();
    }
    public static long wholeRupees(double amount) {
        if (Double.isNaN(amount) || Double.isInfinite(amount) || amount < 0) {
            throw new IllegalArgumentException("Enter a valid nonnegative amount");
        }
        try {
            long value = BigDecimal.valueOf(amount).longValueExact();
            if (value > Long.MAX_VALUE / 100) throw new ArithmeticException("Amount too large");
            return value;
        } catch (ArithmeticException invalid) {
            throw new IllegalArgumentException("LoanManagerPro supports whole rupees; enter an amount without paise.");
        }
    }
}
