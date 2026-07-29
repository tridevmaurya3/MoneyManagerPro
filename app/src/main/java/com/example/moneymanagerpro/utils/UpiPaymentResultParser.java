package com.example.moneymanagerpro.utils;

import java.io.Serializable;
import java.net.URLDecoder;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class UpiPaymentResultParser {

    public enum Status {
        SUCCESS,
        FAILED,
        PENDING,
        UNKNOWN
    }

    public static final class Result
            implements Serializable {

        private final Status status;
        private final String transactionReference;
        private final String responseCode;

        private Result(
                Status status,
                String transactionReference,
                String responseCode
        ) {
            this.status = status;
            this.transactionReference =
                    safe(transactionReference);
            this.responseCode = safe(responseCode);
        }

        public Status getStatus() {
            return status;
        }

        public String getTransactionReference() {
            return transactionReference;
        }

        public String getResponseCode() {
            return responseCode;
        }
    }

    private UpiPaymentResultParser() {
    }

    public static Result parse(String response) {
        Map<String, String> values =
                parseValues(response);
        String statusValue =
                first(
                        values,
                        "status",
                        "txnstatus",
                        "result"
                ).toLowerCase(Locale.US);
        Status status;

        if (statusValue.equals("success")
                || statusValue.equals("successful")
                || statusValue.equals("00")) {
            status = Status.SUCCESS;
        } else if (statusValue.equals("failure")
                || statusValue.equals("failed")
                || statusValue.equals("declined")) {
            status = Status.FAILED;
        } else if (statusValue.equals("pending")
                || statusValue.equals("processing")
                || statusValue.equals("submitted")) {
            status = Status.PENDING;
        } else {
            status = Status.UNKNOWN;
        }

        return new Result(
                status,
                first(
                        values,
                        "txnref",
                        "approvalrefno",
                        "txnreference",
                        "transactionid",
                        "txnid"
                ),
                first(
                        values,
                        "responsecode",
                        "response_code",
                        "code"
                )
        );
    }

    private static Map<String, String> parseValues(
            String response
    ) {
        Map<String, String> values =
                new LinkedHashMap<>();

        if (safe(response).isEmpty()) {
            return values;
        }

        String normalized =
                response.replace('?', '&');

        for (String part : normalized.split("&")) {
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

    private static String decode(String value) {
        try {
            return URLDecoder.decode(
                    value,
                    "UTF-8"
            );
        } catch (Exception exception) {
            return safe(value);
        }
    }

    private static String safe(String value) {
        return value == null
                ? ""
                : value.trim();
    }
}
