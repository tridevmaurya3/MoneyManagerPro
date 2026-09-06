package com.example.moneymanagerpro.utils;

import android.net.Uri;

/**
 * Prepares a scanned UPI QR for an external UPI intent without turning a
 * static QR into a different merchant request.
 *
 * Rules:
 * - preserve every parameter from the scanned QR;
 * - add a unique transaction reference only when the QR has no tr;
 * - add INR only when cu is missing;
 * - never inject/replace amount, merchant code, URL, payee or signature data.
 */
public final class UpiScannedIntentBuilder {

    private UpiScannedIntentBuilder() {
    }

    public static Uri prepare(String rawUpiUri) {
        String raw = rawUpiUri == null ? "" : rawUpiUri.trim();
        if (raw.isEmpty()) {
            return null;
        }

        try {
            Uri original = Uri.parse(raw);
            if (!"upi".equalsIgnoreCase(original.getScheme())
                    || !"pay".equalsIgnoreCase(original.getAuthority())) {
                return null;
            }

            Uri.Builder builder = original.buildUpon();

            String transactionReference = original.getQueryParameter("tr");
            if (transactionReference == null
                    || transactionReference.trim().isEmpty()) {
                builder.appendQueryParameter(
                        "tr",
                        "MMP" + System.currentTimeMillis()
                );
            }

            String currency = original.getQueryParameter("cu");
            if (currency == null || currency.trim().isEmpty()) {
                builder.appendQueryParameter("cu", "INR");
            }

            return builder.build();
        } catch (Exception exception) {
            return null;
        }
    }
}
