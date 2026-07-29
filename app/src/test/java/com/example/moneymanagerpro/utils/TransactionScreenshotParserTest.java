package com.example.moneymanagerpro.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TransactionScreenshotParserTest {

    @Test
    public void parsesGooglePayOutgoingPayment() {
        TransactionScreenshotParser.Result result =
                TransactionScreenshotParser.parse(
                        "Google Pay\n"
                                + "Payment successful\n"
                                + "Paid to Sharma Stores\n"
                                + "₹1,250.50\n"
                                + "State Bank of India ••1234\n"
                                + "UPI transaction ID: 123456789012\n"
                                + "29 Jul 2026, 10:45 AM"
                );

        assertEquals(
                Double.valueOf(1250.50),
                result.getAmount()
        );
        assertEquals(
                "Sharma Stores",
                result.getMerchant()
        );
        assertEquals(
                "State Bank of India",
                result.getBank()
        );
        assertEquals(
                "123456789012",
                result.getReference()
        );
        assertEquals(
                "Google Pay",
                result.getPaymentApp()
        );
        assertEquals(
                TransactionScreenshotParser.Direction.OUTGOING,
                result.getDirection()
        );
        assertEquals(
                TransactionScreenshotParser.Status.SUCCESS,
                result.getStatus()
        );
    }

    @Test
    public void prefersPaidAmountOverBalanceAndCashback() {
        TransactionScreenshotParser.Result result =
                TransactionScreenshotParser.parse(
                        "PhonePe\n"
                                + "Paid to ABC Mart ₹750\n"
                                + "Cashback ₹25\n"
                                + "Available balance ₹12,450"
                );

        assertEquals(
                Double.valueOf(750),
                result.getAmount()
        );
    }

    @Test
    public void parsesReferenceFromFollowingLine() {
        TransactionScreenshotParser.Result result =
                TransactionScreenshotParser.parse(
                        "Paytm\n"
                                + "Money sent\n"
                                + "To\n"
                                + "Ravi Kumar\n"
                                + "Amount INR 500\n"
                                + "HDFC Bank ••4567\n"
                                + "UTR No.\n"
                                + "HDFC20260729001"
                );

        assertEquals(
                "Ravi Kumar",
                result.getMerchant()
        );
        assertEquals(
                "HDFC20260729001",
                result.getReference()
        );
        assertEquals("HDFC Bank", result.getBank());
    }

    @Test
    public void identifiesIncomingTransaction() {
        TransactionScreenshotParser.Result result =
                TransactionScreenshotParser.parse(
                        "₹2,000\n"
                                + "Money received from Amit\n"
                                + "Credited to ICICI Bank"
                );

        assertEquals(
                TransactionScreenshotParser.Direction.INCOMING,
                result.getDirection()
        );
    }

    @Test
    public void identifiesFailedTransaction() {
        TransactionScreenshotParser.Result result =
                TransactionScreenshotParser.parse(
                        "Payment failed\n"
                                + "Paid to Shop\n"
                                + "₹399\n"
                                + "Transaction ID AB12345678"
                );

        assertNotNull(result.getAmount());
        assertEquals(
                TransactionScreenshotParser.Status.FAILED,
                result.getStatus()
        );
        assertTrue(result.hasUsefulData());
    }

    @Test
    public void doesNotTreatReferencePrefixAsBank() {
        TransactionScreenshotParser.Result result =
                TransactionScreenshotParser.parse(
                        "Payment successful\n"
                                + "Paid to Store\n"
                                + "₹100\n"
                                + "UTR No. HDFC20260729001"
                );

        assertEquals("", result.getBank());
    }
}
