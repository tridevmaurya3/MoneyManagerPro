package com.example.moneymanagerpro.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SmsTransactionParserTest {

    @Test
    public void parsesDebitExpense() {
        SmsTransactionParser.Result result =
                SmsTransactionParser.parse(
                        "Rs. 850.00 debited from HDFC Bank "
                                + "for UPI payment to ZOMATO. "
                                + "UPI Ref 426812345678"
                );

        assertEquals(
                SmsTransactionParser.Type.EXPENSE,
                result.getType()
        );
        assertEquals(850.0, result.getAmount(), 0.001);
        assertEquals(
                "Food",
                result.getCategorySuggestion()
        );
        assertTrue(result.isHighConfidence());
    }

    @Test
    public void parsesCreditIncome() {
        SmsTransactionParser.Result result =
                SmsTransactionParser.parse(
                        "INR 25000 credited to your SBI account "
                                + "as salary. Ref 123456789012"
                );

        assertEquals(
                SmsTransactionParser.Type.INCOME,
                result.getType()
        );
        assertEquals(
                "Salary",
                result.getCategorySuggestion()
        );
        assertTrue(result.isFinancialTransaction());
    }

    @Test
    public void rejectsFailedPayment() {
        SmsTransactionParser.Result result =
                SmsTransactionParser.parse(
                        "Payment of Rs 500 failed at merchant"
                );

        assertFalse(result.isFinancialTransaction());
    }

    @Test
    public void ignoresOtpOnlyMessage() {
        SmsTransactionParser.Result result =
                SmsTransactionParser.parse(
                        "Your OTP is 123456. Do not share it."
                );

        assertFalse(result.isFinancialTransaction());
    }
}
