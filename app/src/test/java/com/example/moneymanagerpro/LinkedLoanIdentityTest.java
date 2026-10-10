package com.example.moneymanagerpro;
import org.junit.Test;
import static org.junit.Assert.*;
public class LinkedLoanIdentityTest {
    private static final String ID = "49da3e70-65f6-4ab7-a657-cb64689177e8:7";
    @Test public void identitySurvivesRenameAndHumanNoteEdit() {
        assertEquals(ID, LinkedLoanIdentity.key("Renamed bank loan\n[LMP_LINK:" + ID + "]"));
        assertEquals(ID, LinkedLoanIdentity.key("New note\n[LMP_LINK:" + ID + "]"));
    }
    @Test public void ordinaryAndMalformedNotesAreNeverRemoteIdentity() {
        assertEquals("", LinkedLoanIdentity.key("Bank loan #7"));
        assertEquals("", LinkedLoanIdentity.key("[LMP_LINK:7]"));
        assertEquals("", LinkedLoanIdentity.key("[LMP_LINK:" + ID));
        assertEquals("", LinkedLoanIdentity.key("[LMP_LINK:49da3e70-65f6-4ab7-a657-cb64689177e8:0]"));
    }
    @Test public void internalMetadataDoesNotLeakIntoEditableNote() {
        String source = "Bank loan note\n[LMP_LINK:" + ID + "]\n[LMP_REV:abc]"
                + "\n[LMP_PAYMENT:" + ID + ":9]\n[LMP_TERM:20]\n[LMP_PAYOFF:Jun 2028]";
        assertEquals("Bank loan note", LinkedLoanIdentity.humanNote(source));
        assertEquals("", LinkedLoanIdentity.humanNote(null));
    }
    @Test public void wholeRupeesDoNotUsePaiseAsRupees() {
        assertEquals(5000L, LinkedLoanIdentity.wholeRupees(5000.0));
        assertEquals(0L, LinkedLoanIdentity.wholeRupees(0));
    }
    @Test(expected = IllegalArgumentException.class) public void paiseMustNeverBeSilentlyRounded() {
        LinkedLoanIdentity.wholeRupees(5000.75);
    }
    @Test(expected = IllegalArgumentException.class) public void oversizedAmountsMustNotOverflowTheExistingMinorUnitBridge() {
        LinkedLoanIdentity.wholeRupees(Double.MAX_VALUE);
    }
}
