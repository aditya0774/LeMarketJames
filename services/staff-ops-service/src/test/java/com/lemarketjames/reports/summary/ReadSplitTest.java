package com.lemarketjames.reports.summary;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** BR-16: which days of a report come from the nightly summary and which from the live view, and how the two add up. */
class ReadSplitTest {

    private static final LocalDate FROM = LocalDate.parse("2026-01-01");
    private static final LocalDate TO = LocalDate.parse("2026-10-21");

    private static Optional<ReadSplit.DayRange> days(String from, String to) {
        return Optional.of(new ReadSplit.DayRange(LocalDate.parse(from), LocalDate.parse(to)));
    }

    @Test
    void withNoSummaryEveryDayIsLive() {
        assertEquals(new ReadSplit(Optional.empty(), days("2026-01-01", "2026-10-21")), ReadSplit.of(FROM, TO, Optional.empty()));
    }

    @Test
    void theSummarizedDaysComeFromTheSummaryAndTheRestLive() {
        assertEquals(new ReadSplit(days("2026-01-01", "2026-10-20"), days("2026-10-21", "2026-10-21")),
                ReadSplit.of(FROM, TO, Optional.of(LocalDate.parse("2026-10-20"))));
    }

    @Test
    void aRangeTheSummaryCoversIsReadFromItAlone() {
        assertEquals(new ReadSplit(days("2026-01-01", "2026-10-21"), Optional.empty()),
                ReadSplit.of(FROM, TO, Optional.of(LocalDate.parse("2026-10-25"))));
        assertEquals(new ReadSplit(days("2026-01-01", "2026-10-21"), Optional.empty()),
                ReadSplit.of(FROM, TO, Optional.of(TO)));
    }

    @Test
    void aRangeAfterTheSummaryIsLive() {
        assertEquals(new ReadSplit(Optional.empty(), days("2026-01-01", "2026-10-21")),
                ReadSplit.of(FROM, TO, Optional.of(LocalDate.parse("2025-12-31"))));
    }

    @Test
    void aPeriodOnBothSidesIsAddedUpOnce() {
        List<PeriodTotals> merged = PeriodTotals.merge(List.of(
                new PeriodTotals("2026-10-19", 5, 3, 2, new BigDecimal("1000.50")),
                new PeriodTotals("2026-10-12", 1, 1, 0, new BigDecimal("10")),
                new PeriodTotals("2026-10-19", 2, 0, 2, new BigDecimal("99.50"))));

        assertEquals(List.of(new PeriodTotals("2026-10-12", 1, 1, 0, new BigDecimal("10")),
                new PeriodTotals("2026-10-19", 7, 3, 4, new BigDecimal("1100.00"))), merged);
    }

    @Test
    void aStockOnBothSidesIsAddedUpOnceAndSortedBySymbol() {
        List<SymbolTotals> merged = SymbolTotals.merge(List.of(
                new SymbolTotals("MSFT", new BigDecimal("10"), new BigDecimal("4000"), 1, 0),
                new SymbolTotals("AAPL", new BigDecimal("2"), new BigDecimal("400"), 0, 1),
                new SymbolTotals("MSFT", new BigDecimal("5"), new BigDecimal("2000"), 1, 1)));

        assertEquals(List.of(new SymbolTotals("AAPL", new BigDecimal("2"), new BigDecimal("400"), 0, 1),
                new SymbolTotals("MSFT", new BigDecimal("15"), new BigDecimal("6000"), 2, 1)), merged);
    }
}
