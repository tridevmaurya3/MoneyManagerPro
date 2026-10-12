package com.example.moneymanagerpro;

import org.junit.Test;
import static org.junit.Assert.*;

public class LoanPersonalExpenseShareTest {
    @Test public void displaysFullPaymentAndBothContributions() {
        String note = LoanPersonalExpenseShare.note("TRIDEV_EVENT:test • Synced from LoanManagerPro", 3044700L, 2044700L);
        assertTrue(note.contains("Total ₹30447"));
        assertTrue(note.contains("Yours ₹20447"));
        assertTrue(note.contains("Others ₹10000"));
        assertTrue(note.startsWith("TRIDEV_EVENT:test"));
    }
    @Test public void editsReplaceBreakdownAndRestoreFullPayment() {
        String base = "TRIDEV_EVENT:test • Synced from LoanManagerPro";
        String first = LoanPersonalExpenseShare.note(base, 3044700L, 2044700L);
        String edited = LoanPersonalExpenseShare.note(first, 3044700L, 1044700L);
        assertFalse(edited.contains("Yours ₹20447"));
        assertEquals(edited, LoanPersonalExpenseShare.note(edited, 3044700L, 1044700L));
        assertEquals(base, LoanPersonalExpenseShare.note(edited, 3044700L, 3044700L));
    }
    @Test public void permitsZeroPersonalContributionButRejectsInvalidAmounts() {
        LoanPersonalExpenseShare.validate(3044700L, 0L);
        assertTrue(LoanPersonalExpenseShare.note("source", 3044700L, 0L).contains("Yours ₹0"));
        for (long personal : new long[]{-1L, 3044701L}) {
            try { LoanPersonalExpenseShare.validate(3044700L, personal); fail("Invalid share accepted"); }
            catch (IllegalArgumentException expected) { }
        }
    }
}
