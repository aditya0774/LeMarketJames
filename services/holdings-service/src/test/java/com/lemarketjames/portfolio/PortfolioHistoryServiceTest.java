package com.lemarketjames.portfolio;

import com.lemarketjames.common.domain.AccountRepository;
import com.lemarketjames.market.model.ChartRange;
import com.lemarketjames.market.model.DailyBar;
import com.lemarketjames.market.model.MarketInstrument;
import com.lemarketjames.market.model.PricePoint;
import com.lemarketjames.market.model.QuoteSnapshot;
import com.lemarketjames.market.service.MarketDataService;
import com.lemarketjames.market.service.PriceHistoryService;
import com.lemarketjames.orders.client.OrderSummary;
import com.lemarketjames.orders.client.OrdersClient;
import com.lemarketjames.portfolio.dto.PortfolioPoint;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioHistoryServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 15);
    private static final Instant NOW = Instant.parse("2026-10-15T15:00:00Z");
    private static final MarketInstrument AAPL = instrument(1, "AAPL");
    private static final MarketInstrument MSFT = instrument(2, "MSFT");

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private OrdersClient ordersClient;

    @Mock
    private MarketDataService marketData;

    @Mock
    private PriceHistoryService priceHistory;

    private PortfolioHistoryService service;

    @BeforeEach
    void setUp() {
        service = new PortfolioHistoryService(accountRepository, ordersClient, marketData, priceHistory);
    }

    @Test
    void dailyValueIsTheSharesHeldAtEachCloseTimesThatClose() {
        signedInAsAlice();
        when(ordersClient.getOrdersForAccount(7)).thenReturn(List.of(
                // Bought 10 on the 13th, sold 4 on the 14th (UTC times, during the session).
                fill(1, "BUY", "10", LocalDateTime.of(2026, 10, 13, 15, 0)),
                fill(1, "SELL", "4", LocalDateTime.of(2026, 10, 14, 15, 0)),
                new OrderSummary(1, "BUY", new BigDecimal("99"), null, "REJECTED", null)));
        when(marketData.findByInstrumentId(1)).thenReturn(Optional.of(quote(AAPL)));
        when(priceHistory.dailyBars(1, LocalDate.of(2026, 10, 8))).thenReturn(List.of(
                bar(LocalDate.of(2026, 10, 12), 90), bar(LocalDate.of(2026, 10, 13), 100),
                bar(LocalDate.of(2026, 10, 14), 110), bar(TODAY, 120)));

        List<PortfolioPoint> points = service.history("alice", ChartRange.ONE_WEEK);

        // The 12th, before the first purchase, is left out; closes are stamped at 16:00 New York (20:00 UTC).
        assertEquals(List.of(
                new PortfolioPoint(Instant.parse("2026-10-13T20:00:00Z"), new BigDecimal("1000.00")),
                new PortfolioPoint(Instant.parse("2026-10-14T20:00:00Z"), new BigDecimal("660.00")),
                new PortfolioPoint(Instant.parse("2026-10-15T20:00:00Z"), new BigDecimal("720.00"))), points);
    }

    @Test
    void intradayValueAddsUpEveryStockAtItsLatestPrice() {
        Instant first = Instant.parse("2026-10-15T13:31:00Z");
        Instant second = Instant.parse("2026-10-15T13:32:00Z");
        signedInAsAlice();
        when(ordersClient.getOrdersForAccount(7)).thenReturn(List.of(
                fill(1, "BUY", "2", LocalDateTime.of(2026, 10, 1, 15, 0)),
                fill(2, "BUY", "1", LocalDateTime.of(2026, 10, 1, 15, 0))));
        when(marketData.findByInstrumentId(1)).thenReturn(Optional.of(quote(AAPL)));
        when(marketData.findByInstrumentId(2)).thenReturn(Optional.of(quote(MSFT)));
        when(priceHistory.intraday(1)).thenReturn(List.of(new PricePoint(first, 100), new PricePoint(second, 101)));
        // MSFT has no price at the second minute, so its first one is carried forward.
        when(priceHistory.intraday(2)).thenReturn(List.of(new PricePoint(first, 50)));

        assertEquals(List.of(new PortfolioPoint(first, new BigDecimal("250.00")),
                        new PortfolioPoint(second, new BigDecimal("252.00"))),
                service.history("alice", ChartRange.ONE_DAY));
    }

    @Test
    void anAccountThatNeverTradedHasNoHistory() {
        signedInAsAlice();
        when(ordersClient.getOrdersForAccount(7)).thenReturn(List.of());

        assertTrue(service.history("alice", ChartRange.SIX_MONTHS).isEmpty());
    }

    @Test
    void anUnknownUserIsRefused() {
        when(accountRepository.findAccountIdByUsername("bob")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.history("bob", ChartRange.ONE_DAY));
    }

    private void signedInAsAlice() {
        when(accountRepository.findAccountIdByUsername("alice")).thenReturn(Optional.of(7));
    }

    private static OrderSummary fill(int instrumentId, String side, String quantity, LocalDateTime filledAt) {
        return new OrderSummary(instrumentId, side, new BigDecimal(quantity), new BigDecimal("1"), "FILLED", filledAt);
    }

    private static DailyBar bar(LocalDate date, double close) {
        return new DailyBar(date, close, close, close, close, 1_000L);
    }

    private static QuoteSnapshot quote(MarketInstrument instrument) {
        return new QuoteSnapshot(instrument, 120, 119.9, 120.1, 118, 121, 117, 110, 5_000L, NOW, TODAY);
    }

    private static MarketInstrument instrument(int id, String ticker) {
        return new MarketInstrument(id, ticker, ticker + " Inc", "EQUITY", "USD", "US",
                100, 0.08, 0.25, 0.65, 1.5, 1_000_000L, 10_000L, 6.75, 1.00);
    }
}
