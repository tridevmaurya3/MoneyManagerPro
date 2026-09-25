package com.example.moneymanagerpro;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TridevMerchantMetadataTest {
    @Test
    public void keepsFullBuyerAndLongPurchaseDetails() {
        StringBuilder fullName = new StringBuilder();
        for (int i = 0; i < 12; i++) fullName.append("Tridev Maurya ");
        String buyer = fullName.toString();
        String detail = "By " + buyer + " • Qty 2 • Store Local Market";
        String stored = TridevMerchantMetadata.clean(detail);
        assertTrue(stored.length() > 120);
        assertTrue(stored.startsWith("By " + buyer.trim()));
        assertTrue(stored.endsWith("Store Local Market"));
    }

    @Test
    public void normalizesWhitespaceWithoutShorteningTheName() {
        assertEquals("By Tridev Maurya • Qty 2",
                TridevMerchantMetadata.clean(" By  Tridev\nMaurya  • Qty   2 "));
    }
}
