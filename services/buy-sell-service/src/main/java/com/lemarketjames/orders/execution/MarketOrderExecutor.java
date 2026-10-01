package com.lemarketjames.orders.execution;

import com.lemarketjames.common.config.PlatformSettings;
import com.lemarketjames.common.domain.AccountRepository;
import com.lemarketjames.common.domain.ClientRepository;
import com.lemarketjames.common.instruments.InstrumentRepository;
import com.lemarketjames.market.model.MarketHours;
import com.lemarketjames.market.model.QuoteFreshness;
import com.lemarketjames.market.service.MarketDataService;
import com.lemarketjames.orders.entity.Order;
import com.lemarketjames.orders.entity.Order.OrderStatus;
import com.lemarketjames.orders.entity.RejectionReason;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import org.springframework.stereotype.Service;

/** Prices orders only from fresh market quotes; settlement rechecks cash and shares under lock. */
@Service
public class MarketOrderExecutor implements OrderExecutor {
    private final MarketDataService market;
    private final InstrumentRepository instruments;
    private final AccountRepository accounts;
    private final ClientRepository clients;
    private final PlatformSettings platform;
    private final ExecutionSettings settings;
    private final Clock clock;
    private final com.lemarketjames.orders.service.TradingRestrictions restrictions;
    public MarketOrderExecutor(MarketDataService market, InstrumentRepository instruments,
            AccountRepository accounts, ClientRepository clients, PlatformSettings platform,
            ExecutionSettings settings, Clock clock, com.lemarketjames.orders.service.TradingRestrictions restrictions) {
        this.market = market; this.instruments = instruments; this.accounts = accounts;
        this.clients = clients; this.platform = platform; this.settings = settings; this.clock = clock; this.restrictions = restrictions;
    }
    @Override
    public ExecutionResult execute(Order order) {
        var account = accounts.findById(order.getAccountId());
        if (account.isEmpty() || !account.get().isTradingEnabled()
                || !clients.findById(account.get().getClientId()).map(c -> c.getAccountStatus().canTrade()).orElse(false)) {
            return ExecutionResult.rejected(RejectionReason.ACCOUNT_RESTRICTED);
        }
        if (restrictions.locationRestricted(order.getAccountId())) return ExecutionResult.rejected(RejectionReason.LOCATION_RESTRICTED);
        var instrument = instruments.findById(order.getInstrumentId());
        if (instrument.isEmpty() || !instrument.get().isTradable()) {
            return ExecutionResult.rejected(RejectionReason.NOT_TRADABLE);
        }
        var stock = instrument.get();
        if (settings.isRespectMarketHours() && !MarketHours.forInstrument(stock.getAssetClass().name(),
                stock.getLocation()).isOpen(clock.instant(), settings.getHolidays())) {
            return ExecutionResult.waiting(order.getOrderStatus() == OrderStatus.PENDING
                ? OrderStatus.PENDING : OrderStatus.DELAYED);
        }
        var quote = market.findByInstrumentId(order.getInstrumentId());
        if (quote.isEmpty()) return ExecutionResult.rejected(RejectionReason.PRICE_UNAVAILABLE);
        if (QuoteFreshness.isStale(quote.get(), platform.getMarket().getStalenessLimit(), clock)) {
            return ExecutionResult.rejected(RejectionReason.STALE_QUOTE);
        }
        double raw = order.getOrderType() == Order.OrderType.BUY ? quote.get().askPrice() : quote.get().bidPrice();
        if (!Double.isFinite(raw) || raw <= 0) return ExecutionResult.rejected(RejectionReason.PRICE_UNAVAILABLE);
        BigDecimal price = BigDecimal.valueOf(raw).setScale(4, RoundingMode.HALF_UP);
        return price.signum() > 0 ? ExecutionResult.filled(price) : ExecutionResult.rejected(RejectionReason.PRICE_UNAVAILABLE);
    }
}
