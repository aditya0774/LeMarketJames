package com.lemarketjames.portfolio;

import com.lemarketjames.common.domain.AccountRepository;
import com.lemarketjames.market.model.ChartRange;
import com.lemarketjames.market.model.MarketHours;
import com.lemarketjames.market.model.PricePoint;
import com.lemarketjames.market.model.QuoteSnapshot;
import com.lemarketjames.market.service.MarketDataService;
import com.lemarketjames.market.service.PriceHistoryService;
import com.lemarketjames.orders.client.OrderSummary;
import com.lemarketjames.orders.client.OrdersClient;
import com.lemarketjames.portfolio.dto.PortfolioPoint;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * What the caller's stocks were worth over a {@link ChartRange}, for the overview's portfolio chart.
 * Like the overview's "Portfolio value" card it counts stocks only, not cash, so the chart's last
 * point is the card's figure.
 *
 * <p>There is no stored record of past holdings, so they are rebuilt from the account's filled
 * orders (orders-service, via {@link OrdersClient}): at each moment, the shares held are the sum
 * of the fills up to then, valued at each stock's price at that moment.
 */
@Service
public class PortfolioHistoryService {

    private final AccountRepository accountRepository;
    private final OrdersClient ordersClient;
    private final MarketDataService marketData;
    private final PriceHistoryService priceHistory;

    public PortfolioHistoryService(AccountRepository accountRepository, OrdersClient ordersClient,
                                   MarketDataService marketData, PriceHistoryService priceHistory) {
        this.accountRepository = accountRepository;
        this.ordersClient = ordersClient;
        this.marketData = marketData;
        this.priceHistory = priceHistory;
    }

    /**
     * @param username the signed-in user, from the JWT
     * @param range    the span to cover
     * @return the value of the user's stocks over {@code range}, oldest first; empty if they have
     *         never owned a stock in it or prices are unavailable
     */
    public List<PortfolioPoint> history(String username, ChartRange range) {
        Integer accountId = accountRepository.findAccountIdByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("No account found for username: " + username));

        Map<Integer, List<OrderSummary>> fillsByStock = ordersClient.getOrdersForAccount(accountId).stream()
                .filter(OrderSummary::isFilled)
                .filter(order -> order.filledAt() != null && order.instrumentId() != null)
                .collect(Collectors.groupingBy(OrderSummary::instrumentId));

        List<StockSeries> stocks = new ArrayList<>();
        fillsByStock.forEach((instrumentId, fills) -> marketData.findByInstrumentId(instrumentId)
                .map(quote -> series(quote, fills, range))
                .filter(series -> !series.prices.isEmpty())
                .ifPresent(stocks::add));

        // Every moment any held stock has a price is a point on the chart.
        TreeSet<Instant> times = new TreeSet<>();
        stocks.forEach(stock -> times.addAll(stock.prices.keySet()));

        List<PortfolioPoint> points = new ArrayList<>();
        for (Instant time : times) {
            BigDecimal value = stocks.stream().map(stock -> stock.valueAt(time)).reduce(BigDecimal.ZERO, BigDecimal::add);
            // Before the first purchase there is nothing to draw; starting at the first holding keeps
            // the chart's change over the range meaningful.
            if (points.isEmpty() && value.signum() == 0) {
                continue;
            }
            points.add(new PortfolioPoint(time, value.setScale(2, RoundingMode.HALF_UP)));
        }
        return points;
    }

    private StockSeries series(QuoteSnapshot quote, List<OrderSummary> fills, ChartRange range) {
        int instrumentId = quote.instrument().instrumentId();
        NavigableMap<Instant, Double> prices = new TreeMap<>();
        if (range.intraday()) {
            for (PricePoint point : priceHistory.intraday(instrumentId)) {
                prices.put(point.time(), point.price());
            }
        } else {
            LocalDate today = quote.tradingDate() == null ? LocalDate.now(ZoneOffset.UTC) : quote.tradingDate();
            MarketHours hours = quote.instrument().marketHours();
            priceHistory.dailyBars(instrumentId, range.startDate(today))
                    .forEach(bar -> prices.put(hours.closingTime(bar.date()), bar.close()));
        }

        // Shares held after each fill; order timestamps are UTC (contract C6).
        NavigableMap<Instant, BigDecimal> sharesHeld = new TreeMap<>();
        BigDecimal held = BigDecimal.ZERO;
        for (OrderSummary fill : fills.stream().sorted(Comparator.comparing(OrderSummary::filledAt)).toList()) {
            held = fill.isBuy() ? held.add(fill.quantity()) : held.subtract(fill.quantity());
            sharesHeld.put(fill.filledAt().toInstant(ZoneOffset.UTC), held);
        }
        return new StockSeries(prices, sharesHeld);
    }

    /** One stock's prices and the shares held over time. */
    private record StockSeries(NavigableMap<Instant, Double> prices, NavigableMap<Instant, BigDecimal> sharesHeld) {

        BigDecimal valueAt(Instant time) {
            BigDecimal shares = Optional.ofNullable(sharesHeld.floorEntry(time)).map(Map.Entry::getValue)
                    .orElse(BigDecimal.ZERO);
            if (shares.signum() == 0) {
                return BigDecimal.ZERO;
            }
            // The latest price at or before this moment; a stock whose prices start later than
            // another's is valued at its first price until then.
            Map.Entry<Instant, Double> price = Optional.ofNullable(prices.floorEntry(time)).orElse(prices.firstEntry());
            return shares.multiply(BigDecimal.valueOf(price.getValue()));
        }
    }
}
