package com.example.moneymanagerpro.utils;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class UpiPaymentResultParserTest {

    @Test
    public void parsesSuccessfulResponse() {
        UpiPaymentResultParser.Result result =
                UpiPaymentResultParser.parse(
                        "Status=SUCCESS&txnRef=426812345678"
                                + "&responseCode=00"
                );

        assertEquals(
                UpiPaymentResultParser.Status.SUCCESS,
                result.getStatus()
        );
        assertEquals(
                "426812345678",
                result.getTransactionReference()
        );
    }

    @Test
    public void parsesFailedResponseCaseInsensitively() {
        UpiPaymentResultParser.Result result =
                UpiPaymentResultParser.parse(
                        "status=Failure&ApprovalRefNo=ABC123"
                );

        assertEquals(
                UpiPaymentResultParser.Status.FAILED,
                result.getStatus()
        );
        assertEquals(
                "ABC123",
                result.getTransactionReference()
        );
    }

    @Test
    public void emptyResponseIsUnknown() {
        assertEquals(
                UpiPaymentResultParser.Status.UNKNOWN,
                UpiPaymentResultParser.parse("")
                        .getStatus()
        );
    }
}
