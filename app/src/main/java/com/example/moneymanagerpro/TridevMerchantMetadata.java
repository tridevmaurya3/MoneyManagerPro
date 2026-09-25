package com.example.moneymanagerpro;

import androidx.annotation.Nullable;

/** Preserves the complete human-readable purchase detail across the sync boundary. */
final class TridevMerchantMetadata {
    private TridevMerchantMetadata() { }

    static String clean(@Nullable String value) {
        return value == null ? "" : value.trim().replace('\n', ' ').replace('\r', ' ')
                .replaceAll("\\s+", " ");
    }
}
