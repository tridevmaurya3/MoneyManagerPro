package com.example.moneymanagerpro.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class UpiQrPayloadParserTest {

    @Test
    public void parsesStandardUpiPaymentQr() {
        UpiQrPayloadParser.Result result =
                UpiQrPayloadParser.parse(
                        "upi://pay?pa=shop%40okbank"
                                + "&pn=Sharma+Store"
                                + "&am=250.00"
                                + "&tn=Order+42"
                );

        assertTrue(result.isValid());
        assertEquals(
                "shop@okbank",
                result.getPayeeId()
        );
        assertEquals(
                "Sharma Store",
                result.getPayeeName()
        );
        assertEquals("250.00", result.getAmount());
        assertEquals("Order 42", result.getNote());
    }

    @Test
    public void acceptsCaseInsensitiveParameterNames() {
        UpiQrPayloadParser.Result result =
                UpiQrPayloadParser.parse(
                        "upi://pay?PA=merchant@bank"
                                + "&PN=Merchant"
                );

        assertTrue(result.isValid());
        assertEquals(
                "merchant@bank",
                result.getPayeeId()
        );
    }

    @Test
    public void rejectsNonUpiQrCode() {
        assertFalse(
                UpiQrPayloadParser.parse(
                        "https://example.com/payment"
                ).isValid()
        );
    }
}
