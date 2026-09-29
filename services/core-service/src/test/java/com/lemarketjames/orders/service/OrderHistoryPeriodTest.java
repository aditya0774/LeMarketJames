package com.lemarketjames.orders.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class OrderHistoryPeriodTest {
    @ParameterizedTest
    @CsvSource({
        "2026-03-08,America/New_York,2026-03-08T05:00,2026-03-09T04:00",
        "2026-11-01,America/New_York,2026-11-01T04:00,2026-11-02T05:00",
        "2024-02,UTC,2024-02-01T00:00,2024-03-01T00:00",
        "2026,Asia/Kolkata,2025-12-31T18:30,2026-12-31T18:30",
        "2026-03,America/New_York,2026-03-01T05:00,2026-04-01T04:00"
    })
    void resolvesCalendarBoundaries(String date, String zone, String start, String end) {
        var period = OrderHistoryPeriod.parse(date, zone);
        assertEquals(LocalDateTime.parse(start), period.start());
        assertEquals(LocalDateTime.parse(end), period.end());
    }

    @ParameterizedTest
    @CsvSource({"2026-02-29,UTC", "2026-13,UTC", "26,UTC", "0000,UTC",
        "2026,Bogus/Zone", "2026,", ",UTC", "2026-1,UTC"})
    void rejectsInvalidOrIncompleteFilters(String date, String zone) {
        assertThrows(IllegalArgumentException.class, () -> OrderHistoryPeriod.parse(date, zone));
    }

    @Test
    void absentFilterReturnsNoPeriod() {
        assertNull(OrderHistoryPeriod.parse(null, null));
    }
}
