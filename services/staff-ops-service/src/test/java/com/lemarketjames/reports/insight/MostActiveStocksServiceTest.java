package com.lemarketjames.reports.insight;

import com.lemarketjames.common.config.PlatformSettings;
import com.lemarketjames.reports.period.ReportCalendar;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MostActiveStocksServiceTest {

    private final MostActiveStocksRepository repository = mock(MostActiveStocksRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-14T14:00:00Z"), ZoneOffset.UTC);
    private final MostActiveStocksService service =
            new MostActiveStocksService(repository, new ReportCalendar(new PlatformSettings()), clock);

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