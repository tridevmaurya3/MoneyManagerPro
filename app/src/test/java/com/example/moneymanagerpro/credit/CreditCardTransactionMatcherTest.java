package com.example.moneymanagerpro.credit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class CreditCardTransactionMatcherTest {

    @Test
    public void includesManagedAndExactLegacyAccount() {
        List<String> aliases =
                CreditCardTransactionMatcher
                        .findAccountAliases(
                                "Amazon ICICI Bank Credit Card",
                                "1008",
                                "Amazon ICICI Bank Credit Card •••• 1008",
                                Arrays.asList(
                                        "Cash",
                                        "Amazon ICICI Bank Credit Card",
                                        "Amazon ICICI Bank Credit Card •••• 1008"
                                )
                        );

        assertEquals(2, aliases.size());
        assertTrue(
                aliases.contains(
                        "Amazon ICICI Bank Credit Card"
                )
        );
        assertTrue(
                aliases.contains(
                        "Amazon ICICI Bank Credit Card •••• 1008"
                )
        );
    }

    @Test
    public void ignoresUnrelatedAccountWithSameBankWord() {
        List<String> aliases =
                CreditCardTransactionMatcher
                        .findAccountAliases(
                                "Amazon ICICI Bank Credit Card",
                                "1008",
                                "Amazon ICICI Bank Credit Card •••• 1008",
                                Arrays.asList(
                                        "ICICI Savings Account",
                                        "Other Credit Card •••• 1008"
                                )
                        );

        assertEquals(1, aliases.size());
        assertEquals(
                "Amazon ICICI Bank Credit Card •••• 1008",
                aliases.get(0)
        );
    }

    @Test
    public void normalizesEmojiAndPunctuation() {
        List<String> aliases =
                CreditCardTransactionMatcher
                        .findAccountAliases(
                                "Bank Of Baroda Vikram Credit Card 💳",
                                "9371",
                                "Bank Of Baroda Vikram Credit Card 💳 •••• 9371",
                                Arrays.asList(
                                        "Bank Of Baroda Vikram Credit Card"
                                )
                        );

        assertEquals(2, aliases.size());
        assertTrue(
                aliases.contains(
                        "Bank Of Baroda Vikram Credit Card"
                )
        );
    }
}
