package com.lemarketjames.reports.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Aggregate-only comparison; absent segments are zero-filled in each period. */
public record SegmentComparisonResponse(boolean success, List<SegmentActivityRow> data,
        LocalDate startDate, LocalDate endDate, LocalDate comparisonStartDate,
        LocalDate comparisonEndDate, String timeZone, List<Row> comparison, List<SegmentActivityRow> allTimeData) {
    public record Change(BigDecimal absolute, BigDecimal percent) {}
    public record Row(String segment, SegmentActivityRow current, SegmentActivityRow previous,
            Change activeClients, Change tradeCount, Change totalTradedValue) {}
}
