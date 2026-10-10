package com.example.moneymanagerpro;

import org.junit.Test;
import static org.junit.Assert.*;

public class LinkedLoanMatchPolicyTest {
    @Test public void differentlyNamedExistingLoanDefersImportUntilExplicitLink() {
        assertTrue(LinkedLoanMatchPolicy.possibleSameLoan("Bank loan", 1770000, 30447,
                "Loan 1", 1770000, 30447));
    }
    @Test public void sameNameStillNeedsLinkEvenWhenAmountsDiffer() {
        assertTrue(LinkedLoanMatchPolicy.possibleSameLoan(" Bank Loan ", 1770000, 30447,
                "bank loan", 1800000, 31000));
    }
    @Test public void distinctTermsAndNameAllowNewLoanImport() {
        assertFalse(LinkedLoanMatchPolicy.possibleSameLoan("Bank loan", 1770000, 30447,
                "Car loan", 500000, 12000));
    }
    @Test public void unconfiguredAmountsDoNotMatchEveryNewLoan() {
        assertFalse(LinkedLoanMatchPolicy.possibleSameLoan("Old loan", 0, 0,
                "New loan", 0, 0));
    }
}
