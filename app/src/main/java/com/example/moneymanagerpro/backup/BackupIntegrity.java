package com.example.moneymanagerpro.backup;

import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

public final class BackupIntegrity {

    private static final String CHECKSUM_FIELD =
            "integritySha256";

    private BackupIntegrity() {
    }

    public static String calculateSha256(
            JSONObject backupRoot
    ) throws Exception {
        String canonicalJson =
                canonicalizeJsonValue(
                        backupRoot,
                        true,
                        true
                );

        return calculateSha256FromText(
                canonicalJson
        );
    }

    private static String calculateLegacySha256(
            JSONObject backupRoot
    ) throws Exception {
        String canonicalJson =
                canonicalizeJsonValue(
                        backupRoot,
                        true,
                        false
                );

        return calculateSha256FromText(
                canonicalJson
        );
    }

    private static String calculateSha256FromText(
            String canonicalJson
    ) throws Exception {
        MessageDigest digest =
                MessageDigest.getInstance("SHA-256");

        byte[] hash =
                digest.digest(
                        canonicalJson.getBytes(
                                StandardCharsets.UTF_8
                        )
                );

        StringBuilder hex =
                new StringBuilder(hash.length * 2);

        for (byte value : hash) {
            hex.append(
                    String.format(
                            Locale.US,
                            "%02x",
                            value & 0xff
                    )
            );
        }

        return hex.toString();
    }

    public static boolean verify(
            JSONObject backupRoot,
            String storedChecksum
    ) throws Exception {
        if (storedChecksum == null) {
            return false;
        }

        String normalizedChecksum =
                storedChecksum
                        .trim()
                        .toLowerCase(Locale.US);

        if (normalizedChecksum.length() != 64) {
            return false;
        }

        String calculatedChecksum =
                calculateSha256(backupRoot);

        if (checksumsMatch(
                normalizedChecksum,
                calculatedChecksum
        )) {
            return true;
        }

        /*
         * Version 2-4 की पुरानी backup files पुराने number
         * canonicalization से बनी हो सकती हैं। Stable algorithm में
         * बदलाव के बाद भी उन्हें restore करने योग्य रखें।
         */
        String legacyChecksum =
                calculateLegacySha256(
                        backupRoot
                );

        return checksumsMatch(
                normalizedChecksum,
                legacyChecksum
        );
    }

    private static boolean checksumsMatch(
            String expectedChecksum,
            String calculatedChecksum
    ) {
        return MessageDigest.isEqual(
                expectedChecksum.getBytes(
                        StandardCharsets.US_ASCII
                ),
                calculatedChecksum.getBytes(
                        StandardCharsets.US_ASCII
                )
        );
    }

    private static String canonicalizeJsonValue(
            Object value,
            boolean rootLevel,
            boolean normalizeNumbers
    ) throws Exception {
        if (value == null || value == JSONObject.NULL) {
            return "null";
        }

        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            Set<String> keys = new TreeSet<>();
            Iterator<String> iterator = object.keys();

            while (iterator.hasNext()) {
                keys.add(iterator.next());
            }

            StringBuilder result =
                    new StringBuilder("{");
            boolean first = true;

            for (String key : keys) {
                if (rootLevel
                        && CHECKSUM_FIELD.equals(key)) {
                    continue;
                }

                if (!first) {
                    result.append(',');
                }

                first = false;
                result.append(JSONObject.quote(key));
                result.append(':');
                result.append(
                        canonicalizeJsonValue(
                                object.get(key),
                                false,
                                normalizeNumbers
                        )
                );
            }

            return result.append('}').toString();
        }

        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            StringBuilder result =
                    new StringBuilder("[");

            for (int index = 0;
                 index < array.length();
                 index++) {

                if (index > 0) {
                    result.append(',');
                }

                result.append(
                        canonicalizeJsonValue(
                                array.get(index),
                                false,
                                normalizeNumbers
                        )
                );
            }

            return result.append(']').toString();
        }

        if (value instanceof Number) {
            if (!normalizeNumbers) {
                return String.valueOf(value);
            }

            return normalizeJsonNumber(
                    (Number) value
            );
        }

        if (value instanceof Boolean) {
            return String.valueOf(value);
        }

        return JSONObject.quote(
                String.valueOf(value)
        );
    }

    /**
     * JSON serialization 100.0 को 100 और 1.0E-7 को दूसरे textual
     * रूप में लिख सकती है। BigDecimal based representation समान
     * numeric value को हमेशा वही canonical text देती है।
     */
    private static String normalizeJsonNumber(
            Number number
    ) {
        BigDecimal decimal =
                new BigDecimal(
                        String.valueOf(number)
                );

        if (decimal.compareTo(BigDecimal.ZERO) == 0) {
            return "0";
        }

        return decimal
                .stripTrailingZeros()
                .toPlainString();
    }
}
