package com.lemarketjames.trades;

import com.lemarketjames.common.domain.AccountRepository;
import com.lemarketjames.holdings.exception.UnauthorizedException;
import com.lemarketjames.market.model.MarketInstrument;
import com.lemarketjames.market.model.QuoteSnapshot;
import com.lemarketjames.market.service.MarketDataService;
import com.lemarketjames.orders.client.OrderSummary;
import com.lemarketjames.orders.client.OrdersClient;
import com.lemarketjames.trades.dto.TradeDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TradeServiceTest {

    @Mock
    private OrdersClient ordersClient;

    @Mock
    private MarketDataService marketData;

    @Mock
    private AccountRepository accountRepository;

    private TradeService tradeService;

    @BeforeEach
    void setUp() {
        tradeService = new TradeService(ordersClient, marketData, accountRepository);
    }

    @Test
    void returnsOnlyFilledOrdersAsTrades() {
        when(accountRepository.existsByAccountIdAndUsername(1, "alice")).thenReturn(true);

        OrderSummary filled = new OrderSummary(10, "BUY", new BigDecimal("5"), new BigDecimal("100.00"),
                "FILLED", LocalDateTime.of(2026, 1, 1, 9, 30));
        OrderSummary pending = new OrderSummary(10, "BUY", new BigDecimal("2"), new BigDecimal("100.00"),
                "SUBMITTED", null);
        when(ordersClient.getOrdersForAccount(1)).thenReturn(List.of(filled, pending));

        MarketInstrument aapl = new MarketInstrument(10, "AAPL", "Apple Inc", "EQUITY", "USD", "US",
                100.0, 0.08, 0.25, 0.65, 1.5, 15_200_000_000L, 55_000_000L, 6.75, 1.00);
        QuoteSnapshot quote = new QuoteSnapshot(aapl, 100.0, 99.9, 100.1, 99.0, 101.0, 98.0,
                99.0, 1000L, Instant.now(), LocalDate.now());
        when(marketData.findByInstrumentId(10)).thenReturn(Optional.of(quote));

        List<TradeDto> trades = tradeService.getTradeHistory(1, "alice");

        assertEquals(1, trades.size());
        assertEquals("AAPL", trades.get(0).getSymbol());
        assertEquals(new BigDecimal("5"), trades.get(0).getQuantity());
    }

    @Test
    void rejectsAccessToAnAccountTheCallerDoesNotOwn() {
        when(accountRepository.existsByAccountIdAndUsername(2, "alice")).thenReturn(false);

        assertThrows(UnauthorizedException.class, () -> tradeService.getTradeHistory(2, "alice"));
    }
}
