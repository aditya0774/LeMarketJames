package com.lemarketjames.reports.insight;

import com.lemarketjames.common.config.PlatformSettings;
import com.lemarketjames.reports.period.ReportCalendar;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MostActiveStocksServiceTest {

    private final MostActiveStocksRepository repository = mock(MostActiveStocksRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-14T14:00:00Z"), ZoneOffset.UTC);
    private final MostActiveStocksService service =
            new MostActiveStocksService(repository, new ReportCalendar(new PlatformSettings()),
                    new InsightFlagRule(new PlatformSettings()), clock);

    @Test
    void ranksTheCurrentReportsWeekAndReturnsRefreshTime() {
        var first = row("AAPL", "1000.00", 4, "10");
        var second = row("MSFT", "900.00", 3, "8");
        when(repository.findTopFive(any(), any())).thenReturn(List.of(first, second));

        var result = service.currentWeek();

        assertEquals("2026-10-12", result.startDate().toString());
        assertEquals("2026-10-14", result.endDate().toString());
        assertEquals(1, result.stocks().get(0).rank());
        assertEquals("AAPL", result.stocks().get(0).symbol());
        assertEquals("2026-10-14T14:00:00Z", result.generatedAt().toString());
        verify(repository).findTopFive(any(), any());
    }

    @Test
    void comparesEachStockWithTheSameDaysOfLastWeek() {
        var first = row("AAPL", "1000.00", 4, "10");
        var second = row("MSFT", "900.00", 3, "8");
        var third = row("TSLA", "300.00", 1, "2");
        when(repository.findTopFive(any(), any())).thenReturn(List.of(first, second, third));
        // Wednesday 14 Oct in New York: this week is 12-14 Oct, so last week is 5-7 Oct, not all of it.
        var lastWeek = List.of(value("AAPL", "800.00"), value("MSFT", "1000.00"));
        when(repository.tradedValueOf(List.of("AAPL", "MSFT", "TSLA"),
                LocalDateTime.parse("2026-10-05T04:00:00"), LocalDateTime.parse("2026-10-08T04:00:00")))
                .thenReturn(lastWeek);

        var result = service.currentWeek();

        assertEquals(LocalDate.parse("2026-10-05"), result.previousStartDate());
        assertEquals(LocalDate.parse("2026-10-07"), result.previousEndDate());
        var aapl = result.stocks().get(0);
        assertEquals(new BigDecimal("800.00"), aapl.previousTradedValue());
        assertTrue(aapl.flag().flagged());
        assertEquals(new BigDecimal("25.00"), aapl.flag().changePercent());
        var msft = result.stocks().get(1);
        assertFalse(msft.flag().flagged());
        assertEquals(InsightFlag.Direction.DOWN, msft.flag().direction());
        // Did not trade last week: no percent, but new to the list, so flagged.
        var tsla = result.stocks().get(2);
        assertEquals(BigDecimal.ZERO, tsla.previousTradedValue());
        assertTrue(tsla.flag().flagged());
        assertNull(tsla.flag().changePercent());
    }

    @Test
    void aWeekWithoutTradesAsksNothingAboutLastWeek() {
        when(repository.findTopFive(any(), any())).thenReturn(List.of());

        assertTrue(service.currentWeek().stocks().isEmpty());
        verify(repository, never()).tradedValueOf(any(), any(), any());
    }

    private static MostActiveStocksRepository.SymbolTradedValue value(String symbol, String tradedValue) {
        var row = mock(MostActiveStocksRepository.SymbolTradedValue.class);
        when(row.getSymbol()).thenReturn(symbol);
        when(row.getTradedValue()).thenReturn(new BigDecimal(tradedValue));
        return row;
    }

    private static MostActiveStocksRepository.MostActiveStockAggregate row(
            String symbol, String value, long count, String volume) {
        var row = mock(MostActiveStocksRepository.MostActiveStockAggregate.class);
        when(row.getSymbol()).thenReturn(symbol);
        when(row.getTradedValue()).thenReturn(new BigDecimal(value));
        when(row.getTradeCount()).thenReturn(count);
        when(row.getShareVolume()).thenReturn(new BigDecimal(volume));
        return row;
    }
}