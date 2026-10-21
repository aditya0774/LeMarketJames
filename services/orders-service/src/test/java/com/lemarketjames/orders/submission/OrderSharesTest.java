package com.lemarketjames.orders.submission;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/** BR-04: the share arithmetic behind the QUANTITY rule. */
class OrderSharesTest {

    @Test
    void anAmountBuysTheWholeSharesItCoversRoundedDown() {
        assertEquals(new BigDecimal("2"), OrderShares.forAmount(new BigDecimal("250.00"), new BigDecimal("99.9999")));
        assertEquals(new BigDecimal("3"), OrderShares.forAmount(new BigDecimal("300.00"), new BigDecimal("100.0000")));
        assertEquals(new BigDecimal("0"), OrderShares.forAmount(new BigDecimal("99.99"), new BigDecimal("100.0000")));
    }

    @Test
    void onlyWholeNumbersOfAtLeastOneShareAreWholeShares() {
        assertTrue(OrderShares.isWholeShares(new BigDecimal("1")));
        assertTrue(OrderShares.isWholeShares(new BigDecimal("2.0000")));
        assertTrue(OrderShares.isWholeShares(new BigDecimal("1E+3")));
        assertFalse(OrderShares.isWholeShares(new BigDecimal("0.5")));
        assertFalse(OrderShares.isWholeShares(new BigDecimal("1.0001")));
        assertFalse(OrderShares.isWholeShares(BigDecimal.ZERO));
        assertFalse(OrderShares.isWholeShares(new BigDecimal("-1")));
        assertFalse(OrderShares.isWholeShares(null));
    }
}
