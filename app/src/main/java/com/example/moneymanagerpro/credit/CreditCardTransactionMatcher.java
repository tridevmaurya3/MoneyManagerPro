package com.example.moneymanagerpro.credit;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class CreditCardTransactionMatcher {

    private CreditCardTransactionMatcher() {
    }

    public static List<String> findAccountAliases(
            String cardName,
            String lastFour,
            String managedAccountName,
            List<String> availableAccountNames
    ) {
        Set<String> aliases =
                new LinkedHashSet<>();

        addIfPresent(
                aliases,
                managedAccountName
        );

        String normalizedCardName =
                normalize(cardName);
        String normalizedManagedAccount =
                normalize(managedAccountName);
        String digits = safe(lastFour);

        if (availableAccountNames == null) {
            return new ArrayList<>(aliases);
        }

        for (String accountName :
                availableAccountNames) {
            String normalizedAccount =
                    normalize(accountName);

            if (normalizedAccount.isEmpty()) {
                continue;
            }

            boolean exactCardName =
                    !normalizedCardName.isEmpty()
                            && normalizedAccount.equals(
                            normalizedCardName
                    );

            boolean exactManagedAccount =
                    !normalizedManagedAccount.isEmpty()
                            && normalizedAccount.equals(
                            normalizedManagedAccount
                    );

            boolean containsCardIdentity =
                    normalizedCardName.length() >= 4
                            && (normalizedAccount.contains(
                            normalizedCardName
                    )
                            || normalizedCardName.contains(
                            normalizedAccount
                    ));

            boolean matchingLastFour =
                    digits.matches("\\d{4}")
                            && normalizedAccount.contains(
                            digits
                    );

            if (exactCardName
                    || exactManagedAccount
                    || (containsCardIdentity
                    && matchingLastFour)) {
                addIfPresent(
                        aliases,
                        accountName
                );
            }
        }

        return new ArrayList<>(aliases);
    }

    private static void addIfPresent(
            Set<String> values,
            String value
    ) {
        String safeValue = safe(value);

        if (safeValue.isEmpty()) {
            return;
        }

        for (String existing : values) {
            if (existing.equalsIgnoreCase(
                    safeValue
            )) {
                return;
            }
        }

        values.add(safeValue);
    }

    private static String normalize(String value) {
        return safe(value)
                .toLowerCase(Locale.US)
                .replaceAll("[^a-z0-9]", "");
    }

    private static String safe(String value) {
        return value == null
                ? ""
                : value.trim();
    }
}

