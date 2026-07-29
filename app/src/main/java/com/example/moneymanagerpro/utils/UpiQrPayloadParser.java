package com.example.moneymanagerpro.utils;

import java.io.Serializable;
import java.net.URI;
import java.net.URLDecoder;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class UpiQrPayloadParser {

    public static final class Result
            implements Serializable {

        private final boolean valid;
        private final String payeeId;
        private final String payeeName;
        private final String amount;
        private final String note;

        private Result(
                boolean valid,
                String payeeId,
                String payeeName,
                String amount,
                String note
        ) {
            this.valid = valid;
            this.payeeId = safe(payeeId);
            this.payeeName = safe(payeeName);
            this.amount = safe(amount);
            this.note = safe(note);
        }

        public boolean isValid() {
            return valid;
        }

        public String getPayeeId() {
            return payeeId;
        }

        public String getPayeeName() {
            return payeeName;
        }

        public String getAmount() {
            return amount;
        }

        public String getNote() {
            return note;
        }
    }

    private UpiQrPayloadParser() {
    }

    public static Result parse(String rawValue) {
        String value = safe(rawValue);

        if (value.isEmpty()) {
            return invalid();
        }

        try {
            URI uri = new URI(value);
            String scheme = safe(uri.getScheme())
                    .toLowerCase(Locale.US);

            if (!"upi".equals(scheme)) {
                return invalid();
            }

            Map<String, String> parameters =
                    parseQuery(uri.getRawQuery());
            String payeeId = first(
                    parameters,
                    "pa",
                    "vpa"
            );

            if (!isValidUpiId(payeeId)) {
                return invalid();
            }

            return new Result(
                    true,
                    payeeId,
                    first(parameters, "pn", "name"),
                    first(parameters, "am", "amount"),
                    first(parameters, "tn", "note")
            );
        } catch (Exception exception) {
            return invalid();
        }
    }

    private static Map<String, String> parseQuery(
            String query
    ) {
        Map<String, String> values =
                new LinkedHashMap<>();

        if (safe(query).isEmpty()) {
            return values;
        }

        for (String part : query.split("&")) {
            int separator = part.indexOf('=');

            if (separator <= 0) {
                continue;
            }

            String key = decode(
                    part.substring(0, separator)
            ).toLowerCase(Locale.US);
            String value = decode(
                    part.substring(separator + 1)
            );

            values.put(key, value);
        }

        return values;
    }

    private static String first(
            Map<String, String> values,
            String... keys
    ) {
        for (String key : keys) {
            String value = values.get(key);

            if (!safe(value).isEmpty()) {
                return value.trim();
            }
        }

        return "";
    }

    private static boolean isValidUpiId(String value) {
        return safe(value).matches(
                "^[A-Za-z0-9._-]{2,}@[A-Za-z0-9.-]{2,}$"
        );
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(
                    safe(value),
                    "UTF-8"
            );
        } catch (Exception exception) {
            return safe(value);
        }
    }

    private static Result invalid() {
        return new Result(
                false,
                "",
                "",
                "",
                ""
        );
    }

    private static String safe(String value) {
        return value == null
                ? ""
                : value.trim();
    }
}
