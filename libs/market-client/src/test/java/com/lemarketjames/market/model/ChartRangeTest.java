package com.lemarketjames.market.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChartRangeTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 15);

    @Test
    void codesAreMatchedIgnoringCase() {
        assertEquals(Optional.of(ChartRange.THREE_MONTHS), ChartRange.fromCode("3m"));
        assertEquals(Optional.empty(), ChartRange.fromCode("5Y"));
        assertEquals(Optional.empty(), ChartRange.fromCode(null));
    }

    @Test
    void onlyOneDayIsIntraday() {
        assertTrue(ChartRange.ONE_DAY.intraday());
        assertFalse(ChartRange.ONE_WEEK.intraday());
    }

    @Test
    void startDatesCountBackFromToday() {
        assertEquals(TODAY, ChartRange.ONE_DAY.startDate(TODAY));
        assertEquals(LocalDate.of(2026, 10, 8), ChartRange.ONE_WEEK.startDate(TODAY));
        assertEquals(LocalDate.of(2026, 4, 15), ChartRange.SIX_MONTHS.startDate(TODAY));
    }
}
