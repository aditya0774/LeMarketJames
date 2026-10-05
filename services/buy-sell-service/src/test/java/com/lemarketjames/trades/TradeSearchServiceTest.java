package com.lemarketjames.trades;

import com.lemarketjames.orders.entity.Order.OrderStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TradeSearchServiceTest {
    private final TradeSearchRepository repository = mock(TradeSearchRepository.class);
    private final TradeSearchService service = new TradeSearchService(repository);

    @Test
    void orderSearchOnlyRequestsFilledOrder() {
        when(repository.findTrade(42, OrderStatus.FILLED)).thenReturn(List.of());
        assertEquals(List.of(), service.search(42, null, null, null));
        verify(repository).findTrade(42, OrderStatus.FILLED);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void includesEntireFinalDateAcrossYearBoundary() {
        service.search(null, 7, "2025-12-31", "2025-12-31");
        verify(repository).findClientTrades(7, LocalDateTime.parse("2025-12-31T00:00:00"),
                LocalDateTime.parse("2026-01-01T00:00:00"), OrderStatus.FILLED);
    }

    @ParameterizedTest
    @CsvSource({
        ",,,", "0,,,", "-1,,,", "42,7,2026-01-01,2026-01-02", "42,,2026-01-01,",
        ",0,2026-01-01,2026-01-02", ",7,,2026-01-02", ",7,2026-01-01,",
        ",,2026-01-01,2026-01-02", ",7,2026-01-02,2026-01-01",
        ",7,2026-02-30,2026-03-01", ",7,2026-1-1,2026-01-02",
        ",7,0000-01-01,2026-01-02", ",7,2026-01-01T00:00:00Z,2026-01-02"
    })
    void invalidSearchNeverQueries(Integer orderId, Integer clientId, String from, String to) {
        assertThrows(IllegalArgumentException.class, () -> service.search(orderId, clientId, from, to));
        verifyNoInteractions(repository);
    }
}
