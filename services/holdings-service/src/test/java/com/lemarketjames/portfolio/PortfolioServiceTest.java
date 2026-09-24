package com.lemarketjames.portfolio;

import com.lemarketjames.common.domain.AccountEntity;
import com.lemarketjames.common.domain.AccountRepository;
import com.lemarketjames.holdings.entity.HoldingsEntity;
import com.lemarketjames.holdings.repository.HoldingsRepository;
import com.lemarketjames.market.model.MarketInstrument;
import com.lemarketjames.market.model.QuoteSnapshot;
import com.lemarketjames.market.service.MarketDataService;
import com.lemarketjames.orders.client.OrderSummary;
import com.lemarketjames.orders.client.OrdersClient;
import com.lemarketjames.portfolio.dto.PortfolioBalance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private HoldingsRepository holdingsRepository;

    @Mock
    private MarketDataService marketData;

    @Mock
    private OrdersClient ordersClient;

    private PortfolioService portfolioService;

    @BeforeEach
    void setUp() {
        portfolioService = new PortfolioService(accountRepository, holdingsRepository, marketData, ordersClient);
    }

    @Test
    void computesTotalsAndBuyingPowerFromHoldingsAndOpenOrders() {
        AccountEntity account = new AccountEntity();
        account.setCashBalance(new BigDecimal("1000.00"));
        account.setCurrency("USD");

        when(accountRepository.existsByAccountIdAndUsername(1, "alice")).thenReturn(true);
        when(accountRepository.findById(1)).thenReturn(Optional.of(account));

        HoldingsEntity holding = new HoldingsEntity(1, 10, new BigDecimal("10"), new BigDecimal("90.00"));
        when(holdingsRepository.findByAccountId(1)).thenReturn(List.of(holding));

        MarketInstrument aapl = new MarketInstrument(10, "AAPL", "Apple Inc", "EQUITY", "USD", "US",
                100.0, 0.08, 0.25, 0.65, 1.5, 15_200_000_000L, 55_000_000L, 6.75, 1.00);
        // last=100, open=95 -> day gain = 10 * (100-95) = 50; total gain = 10*(100-90) = 100
        QuoteSnapshot quote = new QuoteSnapshot(aapl, 100.0, 99.9, 100.1, 95.0, 101.0, 94.0,
                99.0, 1000L, Instant.now(), LocalDate.now());
        when(marketData.findByInstrumentId(10)).thenReturn(Optional.of(quote));

        // One open BUY order for 5 shares @ 20 = 100 reserved.
        OrderSummary openBuy = new OrderSummary(10, "BUY", new BigDecimal("5"), new BigDecimal("20.00"), "SUBMITTED", null);
        when(ordersClient.getOrdersForAccount(1)).thenReturn(List.of(openBuy));

        PortfolioBalance balance = portfolioService.getPortfolio(1, "alice");

        assertEquals(new BigDecimal("1000.00"), balance.getCash());
        assertEquals(new BigDecimal("1000.00"), balance.getInvested()); // 10 * 100
        assertEquals(new BigDecimal("2000.00"), balance.getTotalValue()); // cash + invested
        assertEquals(new BigDecimal("900.00"), balance.getBuyingPower()); // 1000 - 100
        assertEquals(new BigDecimal("50.00"), balance.getDayGainLoss());
        assertEquals(new BigDecimal("100.00"), balance.getTotalGainLoss());
    }
}
