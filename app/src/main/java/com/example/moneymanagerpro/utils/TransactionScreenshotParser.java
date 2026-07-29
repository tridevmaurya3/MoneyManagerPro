package com.example.moneymanagerpro.utils;

import java.math.BigDecimal;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TransactionScreenshotParser {

    public enum Direction {
        OUTGOING,
        INCOMING,
        UNKNOWN
    }

    public enum Status {
        SUCCESS,
        PENDING,
        FAILED,
        UNKNOWN
    }

    public static final class Result implements Serializable {
        private static final long serialVersionUID = 1L;

        private final Double amount;
        private final String bank;
        private final String reference;
        private final String merchant;
        private final String dateText;
        private final String paymentApp;
        private final Direction direction;
        private final Status status;

        private Result(
                Double amount,
                String bank,
                String reference,
                String merchant,
                String dateText,
                String paymentApp,
                Direction direction,
                Status status
        ) {
            this.amount = amount;
            this.bank = safe(bank);
            this.reference = safe(reference);
            this.merchant = safe(merchant);
            this.dateText = safe(dateText);
            this.paymentApp = safe(paymentApp);
            this.direction = direction == null
                    ? Direction.UNKNOWN
                    : direction;
            this.status = status == null
                    ? Status.UNKNOWN
                    : status;
        }

        public Double getAmount() {
            return amount;
        }

        public String getBank() {
            return bank;
        }

        public String getReference() {
            return reference;
        }

        public String getMerchant() {
            return merchant;
        }

        public String getDateText() {
            return dateText;
        }

        public String getPaymentApp() {
            return paymentApp;
        }

        public Direction getDirection() {
            return direction;
        }

        public Status getStatus() {
            return status;
        }

        public boolean hasUsefulData() {
            return amount != null
                    || !bank.isEmpty()
                    || !reference.isEmpty()
                    || !merchant.isEmpty()
                    || !dateText.isEmpty();
        }
    }

    private static final Pattern AMOUNT_PATTERN =
            Pattern.compile(
                    "(?i)(?:₹|\\bINR\\b|\\bRs\\.?)(?:\\s*)"
                            + "([0-9]{1,3}(?:,[0-9]{2,3})*"
                            + "(?:\\.[0-9]{1,2})?"
                            + "|[0-9]+(?:\\.[0-9]{1,2})?)"
            );

    private static final Pattern AMOUNT_LABEL_PATTERN =
            Pattern.compile(
                    "(?i)(?:amount|paid|sent|debited|payment)"
                            + "\\s*(?:of|:|-)?\\s*"
                            + "([0-9]{1,3}(?:,[0-9]{2,3})*"
                            + "(?:\\.[0-9]{1,2})?"
                            + "|[0-9]+(?:\\.[0-9]{1,2})?)"
            );

    private static final Pattern REFERENCE_PATTERN =
            Pattern.compile(
                    "(?i)(?:UPI\\s*(?:transaction\\s*)?"
                            + "(?:ID|ref(?:erence)?(?:\\s*no\\.?)?)"
                            + "|UTR(?:\\s*(?:no\\.?|number))?"
                            + "|transaction\\s*(?:ID|reference)"
                            + "|bank\\s*reference(?:\\s*(?:no\\.?|number))?"
                            + "|reference\\s*(?:ID|no\\.?|number))"
                            + "\\s*[:#-]?\\s*"
                            + "([A-Z0-9][A-Z0-9._/-]{5,39})"
            );

    private static final Pattern DATE_PATTERN =
            Pattern.compile(
                    "(?i)\\b("
                            + "(?:0?[1-9]|[12][0-9]|3[01])"
                            + "[/.-](?:0?[1-9]|1[0-2])"
                            + "[/.-](?:20)?[0-9]{2}"
                            + "(?:\\s*(?:at|,)?\\s*"
                            + "(?:[01]?[0-9]|2[0-3]):[0-5][0-9]"
                            + "(?:\\s*[AP]M)?)?"
                            + "|(?:0?[1-9]|[12][0-9]|3[01])\\s+"
                            + "(?:Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?"
                            + "|Apr(?:il)?|May|Jun(?:e)?|Jul(?:y)?"
                            + "|Aug(?:ust)?|Sep(?:tember)?|Oct(?:ober)?"
                            + "|Nov(?:ember)?|Dec(?:ember)?)"
                            + "\\s+20[0-9]{2}"
                            + "(?:\\s*(?:at|,)?\\s*"
                            + "(?:[01]?[0-9]|2[0-3]):[0-5][0-9]"
                            + "(?:\\s*[AP]M)?)?"
                            + ")\\b"
            );

    private static final List<String> OUTGOING_WORDS =
            Arrays.asList(
                    "paid to",
                    "sent to",
                    "payment to",
                    "money sent",
                    "debited",
                    "you paid",
                    "you sent"
            );

    private static final List<String> INCOMING_WORDS =
            Arrays.asList(
                    "received from",
                    "money received",
                    "credited to",
                    "you received"
            );

    private static final Map<String, String> BANK_ALIASES =
            createBankAliases();

    private static final Map<String, String> PAYMENT_APPS =
            createPaymentApps();

    private TransactionScreenshotParser() {
    }

    public static Result parse(String recognizedText) {
        String text = normalizeText(recognizedText);
        List<String> lines = nonEmptyLines(text);
        String lowerText = text.toLowerCase(Locale.US);

        Direction direction =
                detectDirection(lowerText);
        Status status =
                detectStatus(lowerText);

        return new Result(
                detectAmount(lines),
                detectBank(lines),
                detectReference(lines),
                detectMerchant(lines),
                detectDate(text),
                detectMappedValue(
                        lowerText,
                        PAYMENT_APPS
                ),
                direction,
                status
        );
    }

    private static Double detectAmount(
            List<String> lines
    ) {
        AmountCandidate best = null;

        for (int index = 0;
             index < lines.size();
             index++) {

            String line = lines.get(index);
            String lower =
                    line.toLowerCase(Locale.US);

            Matcher currencyMatcher =
                    AMOUNT_PATTERN.matcher(line);

            while (currencyMatcher.find()) {
                Double amount =
                        parseAmount(
                                currencyMatcher.group(1)
                        );

                int score = scoreAmountLine(
                        lower,
                        true,
                        index
                );

                best = chooseBetter(
                        best,
                        new AmountCandidate(
                                amount,
                                score,
                                index
                        )
                );
            }

            Matcher labelMatcher =
                    AMOUNT_LABEL_PATTERN.matcher(line);

            while (labelMatcher.find()) {
                Double amount =
                        parseAmount(
                                labelMatcher.group(1)
                        );

                int score = scoreAmountLine(
                        lower,
                        false,
                        index
                );

                best = chooseBetter(
                        best,
                        new AmountCandidate(
                                amount,
                                score,
                                index
                        )
                );
            }
        }

        return best == null
                ? null
                : best.amount;
    }

    private static int scoreAmountLine(
            String lower,
            boolean hasCurrency,
            int lineIndex
    ) {
        int score = hasCurrency ? 12 : 5;

        if (containsAny(
                lower,
                "paid",
                "sent",
                "amount",
                "payment",
                "debited",
                "total"
        )) {
            score += 8;
        }

        if (containsAny(
                lower,
                "successful",
                "success",
                "completed"
        )) {
            score += 3;
        }

        if (containsAny(
                lower,
                "balance",
                "cashback",
                "reward",
                "offer",
                "limit"
        )) {
            score -= 15;
        }

        score += Math.max(0, 5 - lineIndex);
        return score;
    }

    private static AmountCandidate chooseBetter(
            AmountCandidate current,
            AmountCandidate candidate
    ) {
        if (candidate.amount == null
                || candidate.amount <= 0
                || candidate.amount.isInfinite()
                || candidate.amount.isNaN()) {
            return current;
        }

        if (current == null
                || candidate.score > current.score
                || (candidate.score == current.score
                && candidate.lineIndex
                < current.lineIndex)) {
            return candidate;
        }

        return current;
    }

    private static Double parseAmount(String value) {
        if (value == null) {
            return null;
        }

        try {
            BigDecimal amount = new BigDecimal(
                    value.replace(",", "").trim()
            );

            if (amount.compareTo(BigDecimal.ZERO) <= 0
                    || amount.compareTo(
                    new BigDecimal("1000000000000")
            ) > 0) {
                return null;
            }

            return amount.doubleValue();
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static String detectReference(
            List<String> lines
    ) {
        for (int index = 0;
             index < lines.size();
             index++) {

            String line = lines.get(index);
            Matcher matcher =
                    REFERENCE_PATTERN.matcher(line);

            if (matcher.find()) {
                return cleanIdentifier(
                        matcher.group(1)
                );
            }

            String lower =
                    line.toLowerCase(Locale.US);

            if (isReferenceLabel(lower)
                    && index + 1 < lines.size()) {

                String candidate =
                        cleanIdentifier(
                                lines.get(index + 1)
                        );

                if (candidate.matches(
                        "[A-Za-z0-9][A-Za-z0-9._/-]{5,39}"
                )) {
                    return candidate;
                }
            }
        }

        return "";
    }

    private static boolean isReferenceLabel(String lower) {
        return containsAny(
                lower,
                "upi transaction id",
                "upi ref",
                "utr",
                "transaction id",
                "bank reference",
                "reference no",
                "reference id"
        );
    }

    private static String detectMerchant(
            List<String> lines
    ) {
        String[] labels = {
                "paid to",
                "sent to",
                "payment to",
                "merchant",
                "recipient",
                "to"
        };

        for (int index = 0;
             index < lines.size();
             index++) {

            String line = lines.get(index);
            String lower =
                    line.toLowerCase(Locale.US);

            for (String label : labels) {
                String prefix = label + " ";

                if (lower.startsWith(prefix)) {
                    String merchant = cleanMerchant(
                            line.substring(
                                    prefix.length()
                            )
                    );

                    if (isUsefulMerchant(merchant)) {
                        return merchant;
                    }
                }

                if (lower.equals(label)
                        && index + 1 < lines.size()) {

                    String merchant = cleanMerchant(
                            lines.get(index + 1)
                    );

                    if (isUsefulMerchant(merchant)) {
                        return merchant;
                    }
                }
            }
        }

        return "";
    }

    private static boolean isUsefulMerchant(
            String merchant
    ) {
        if (merchant.length() < 2
                || merchant.length() > 80) {
            return false;
        }

        String lower =
                merchant.toLowerCase(Locale.US);

        return !containsAny(
                lower,
                "transaction id",
                "upi id",
                "bank account",
                "payment successful",
                "payment complete",
                "₹",
                "inr "
        );
    }

    private static String detectDate(String text) {
        Matcher matcher = DATE_PATTERN.matcher(text);

        return matcher.find()
                ? cleanValue(matcher.group(1))
                : "";
    }

    private static Direction detectDirection(
            String lowerText
    ) {
        boolean outgoing =
                containsAny(
                        lowerText,
                        OUTGOING_WORDS
                );
        boolean incoming =
                containsAny(
                        lowerText,
                        INCOMING_WORDS
                );

        if (outgoing && !incoming) {
            return Direction.OUTGOING;
        }

        if (incoming && !outgoing) {
            return Direction.INCOMING;
        }

        return Direction.UNKNOWN;
    }

    private static Status detectStatus(
            String lowerText
    ) {
        if (containsAny(
                lowerText,
                "payment failed",
                "transaction failed",
                "declined",
                "payment unsuccessful",
                "could not be completed"
        )) {
            return Status.FAILED;
        }

        if (containsAny(
                lowerText,
                "payment pending",
                "transaction pending",
                "processing",
                "in progress"
        )) {
            return Status.PENDING;
        }

        if (containsAny(
                lowerText,
                "payment successful",
                "transaction successful",
                "successfully paid",
                "payment complete",
                "transaction complete",
                "money sent",
                "paid to"
        )) {
            return Status.SUCCESS;
        }

        return Status.UNKNOWN;
    }

    private static String detectMappedValue(
            String lowerText,
            Map<String, String> aliases
    ) {
        for (Map.Entry<String, String> entry :
                aliases.entrySet()) {

            if (lowerText.contains(
                    entry.getKey()
            )) {
                return entry.getValue();
            }
        }

        return "";
    }

    private static String detectBank(
            List<String> lines
    ) {
        for (String line : lines) {
            String lower =
                    line.toLowerCase(Locale.US);

            for (Map.Entry<String, String> entry :
                    BANK_ALIASES.entrySet()) {

                if (containsAlias(
                        lower,
                        entry.getKey()
                )) {
                    return entry.getValue();
                }
            }
        }

        return "";
    }

    private static boolean containsAlias(
            String text,
            String alias
    ) {
        if (alias.length() > 5
                || alias.contains(" ")) {
            return text.contains(alias);
        }

        return Pattern.compile(
                "(^|[^a-z0-9])"
                        + Pattern.quote(alias)
                        + "([^a-z0-9]|$)"
        ).matcher(text).find();
    }

    private static Map<String, String>
    createBankAliases() {
        Map<String, String> aliases =
                new LinkedHashMap<>();

        aliases.put(
                "state bank of india",
                "State Bank of India"
        );
        aliases.put("sbi", "State Bank of India");
        aliases.put("hdfc bank", "HDFC Bank");
        aliases.put("hdfc", "HDFC Bank");
        aliases.put("icici bank", "ICICI Bank");
        aliases.put("icici", "ICICI Bank");
        aliases.put("axis bank", "Axis Bank");
        aliases.put(
                "bank of baroda",
                "Bank of Baroda"
        );
        aliases.put("bob", "Bank of Baroda");
        aliases.put(
                "punjab national bank",
                "Punjab National Bank"
        );
        aliases.put("pnb", "Punjab National Bank");
        aliases.put("canara bank", "Canara Bank");
        aliases.put("union bank", "Union Bank of India");
        aliases.put(
                "kotak mahindra bank",
                "Kotak Mahindra Bank"
        );
        aliases.put("kotak", "Kotak Mahindra Bank");
        aliases.put("indusind bank", "IndusInd Bank");
        aliases.put("yes bank", "YES Bank");
        aliases.put("idfc first", "IDFC FIRST Bank");
        aliases.put(
                "au small finance",
                "AU Small Finance Bank"
        );
        aliases.put(
                "paytm payments bank",
                "Paytm Payments Bank"
        );
        aliases.put(
                "airtel payments bank",
                "Airtel Payments Bank"
        );

        return aliases;
    }

    private static Map<String, String>
    createPaymentApps() {
        Map<String, String> apps =
                new LinkedHashMap<>();

        apps.put("google pay", "Google Pay");
        apps.put("gpay", "Google Pay");
        apps.put("phonepe", "PhonePe");
        apps.put("paytm", "Paytm");
        apps.put("bhim", "BHIM");
        apps.put("amazon pay", "Amazon Pay");
        apps.put("whatsapp pay", "WhatsApp Pay");

        return apps;
    }

    private static List<String> nonEmptyLines(
            String text
    ) {
        List<String> lines = new ArrayList<>();

        for (String line : text.split("\\n")) {
            String cleaned = cleanValue(line);

            if (!cleaned.isEmpty()) {
                lines.add(cleaned);
            }
        }

        return lines;
    }

    private static String normalizeText(String text) {
        if (text == null) {
            return "";
        }

        return text
                .replace('\u00A0', ' ')
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .trim();
    }

    private static String cleanIdentifier(
            String value
    ) {
        return cleanValue(value)
                .replaceAll("^[#:\\-\\s]+", "")
                .replaceAll("[,;:\\s]+$", "");
    }

    private static String cleanMerchant(String value) {
        return cleanValue(value)
                .replaceAll("^[#:\\-\\s]+", "")
                .replaceAll("\\s*(?:₹|\\bINR\\b|\\bRs\\.?).*$", "")
                .trim();
    }

    private static String cleanValue(String value) {
        return safe(value)
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static boolean containsAny(
            String text,
            List<String> values
    ) {
        for (String value : values) {
            if (text.contains(value)) {
                return true;
            }
        }

        return false;
    }

    private static boolean containsAny(
            String text,
            String... values
    ) {
        return containsAny(
                text,
                Arrays.asList(values)
        );
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static final class AmountCandidate {
        final Double amount;
        final int score;
        final int lineIndex;

        AmountCandidate(
                Double amount,
                int score,
                int lineIndex
        ) {
            this.amount = amount;
            this.score = score;
            this.lineIndex = lineIndex;
        }
    }
}
