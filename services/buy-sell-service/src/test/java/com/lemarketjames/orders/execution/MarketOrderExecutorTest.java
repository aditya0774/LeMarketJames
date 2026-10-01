package com.lemarketjames.orders.execution;

import com.lemarketjames.common.config.PlatformSettings;
import com.lemarketjames.common.domain.*;
import com.lemarketjames.common.instruments.*;
import com.lemarketjames.market.model.QuoteSnapshot;
import com.lemarketjames.market.service.MarketDataService;
import com.lemarketjames.orders.entity.*;
import com.lemarketjames.orders.service.TradingRestrictions;
import java.math.BigDecimal;
import java.time.*;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MarketOrderExecutorTest {
    final MarketDataService market = mock(MarketDataService.class);
    final InstrumentRepository instruments = mock(InstrumentRepository.class);
    final AccountRepository accounts = mock(AccountRepository.class);
    final ClientRepository clients = mock(ClientRepository.class);
    final TradingRestrictions restrictions = mock(TradingRestrictions.class);
    final ExecutionSettings settings = new ExecutionSettings();
    final Instant now = Instant.parse("2026-09-30T15:00:00Z");
    final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    MarketOrderExecutor executor;
    Order order;
    @BeforeEach void setup() {
        var account = new AccountEntity(); account.setClientId(1); account.setTradingEnabled(true);
        var client = new ClientEntity(); client.setAccountStatus(AccountStatus.ACTIVE);
        var instrument = new Instrument(); instrument.setTradable(true); instrument.setAssetClass(Instrument.AssetClass.EQUITY);
        when(accounts.findById(1)).thenReturn(Optional.of(account));
        when(clients.findById(1)).thenReturn(Optional.of(client));
        when(instruments.findById(1)).thenReturn(Optional.of(instrument));
        executor = new MarketOrderExecutor(market, instruments, accounts, clients, new PlatformSettings(), settings, clock, restrictions);
        order = new Order(1, 1, Order.OrderType.BUY, BigDecimal.ONE); order.setOrderStatus(Order.OrderStatus.ACCEPTED);
    }
    QuoteSnapshot quote(Instant time) { return new QuoteSnapshot(null, 100, 99, 101, 100, 100, 100, 100, 0, time, null); }
    @Test void buysAtAskAndSellsAtBidIgnoringBrowserPrice() {
        when(market.findByInstrumentId(1)).thenReturn(Optional.of(quote(now)));
        order.setPricePerUnit(BigDecimal.ONE);
        assertEquals(new BigDecimal("101.0000"), executor.execute(order).fillPrice());
        order.setOrderType(Order.OrderType.SELL);
        assertEquals(new BigDecimal("99.0000"), executor.execute(order).fillPrice());
    }
    @Test void rejectsUnavailableAndStaleQuotes() {
        when(market.findByInstrumentId(1)).thenReturn(Optional.empty());
        assertEquals(RejectionReason.PRICE_UNAVAILABLE, executor.execute(order).reason());
        when(market.findByInstrumentId(1)).thenReturn(Optional.of(quote(now.minusSeconds(61))));
        assertEquals(RejectionReason.STALE_QUOTE, executor.execute(order).reason());
    }
    @Test void holidayWaitsWithoutFetchingAQuote() {
        settings.getHolidays().add(LocalDate.of(2026, 9, 30));
        assertEquals(Order.OrderStatus.DELAYED, executor.execute(order).nextStatus());
        verifyNoInteractions(market);
    }
    @Test void delayedOrderExecutesWhenMarketOpens() {
        order.setOrderStatus(Order.OrderStatus.DELAYED);
        when(market.findByInstrumentId(1)).thenReturn(Optional.of(quote(now)));
        assertEquals(Order.OrderStatus.FILLED, executor.execute(order).nextStatus());
    }
    @Test void rechecksTradabilityAndLocation() {
        when(restrictions.locationRestricted(1)).thenReturn(true);
        assertEquals(RejectionReason.LOCATION_RESTRICTED, executor.execute(order).reason());
        when(restrictions.locationRestricted(1)).thenReturn(false);
        instruments.findById(1).orElseThrow().setTradable(false);
        assertEquals(RejectionReason.NOT_TRADABLE, executor.execute(order).reason());
    }
}
