package com.lemarketjames.quotes;

import com.lemarketjames.market.client.MarketFeedStatus;
import com.lemarketjames.market.model.ChartRange;
import com.lemarketjames.market.model.MarketHours;
import com.lemarketjames.market.model.QuoteSnapshot;
import com.lemarketjames.market.service.MarketDataService;
import com.lemarketjames.market.service.PriceHistoryService;
import com.lemarketjames.quotes.dto.ChartPointDto;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * A stock's price over a {@link ChartRange}, for the trade page's chart. Kept apart from
 * {@link QuoteService} (current prices) since the two read different sources and change for
 * different reasons.
 */
@Service
public class QuoteHistoryService {

    private static final int DISPLAY_SCALE = 2;

    private final MarketDataService marketData;
    private final PriceHistoryService priceHistory;
    private final MarketFeedStatus feedStatus;

    public QuoteHistoryService(MarketDataService marketData, PriceHistoryService priceHistory,
                               MarketFeedStatus feedStatus) {
        this.marketData = marketData;
        this.priceHistory = priceHistory;
        this.feedStatus = feedStatus;
    }

    /**
     * @param rawSymbol ticker symbol; surrounding whitespace and case are ignored
     * @param range     the span to cover
     * @return the stock's prices over {@code range}, oldest first, ending with the live price
     * @throws QuoteService.QuoteFeedUnavailableException if the feed is currently down
     * @throws QuoteService.SymbolNotFoundException       if no simulated instrument has that ticker
     */
    public List<ChartPointDto> history(String rawSymbol, ChartRange range) {
        String symbol = rawSymbol == null ? "" : rawSymbol.trim().toUpperCase(Locale.ROOT);
        QuoteSnapshot quote = marketData.findByTicker(symbol).orElseThrow(() -> feedStatus.isAvailable()
                ? new QuoteService.SymbolNotFoundException("Symbol not found")
                : new QuoteService.QuoteFeedUnavailableException("Price unavailable"));
        int instrumentId = quote.instrument().instrumentId();

        if (range.intraday()) {
            return priceHistory.intraday(instrumentId).stream()
                    .map(point -> new ChartPointDto(point.time(), money(point.price())))
                    .toList();
        }
        LocalDate today = quote.tradingDate() == null ? LocalDate.now() : quote.tradingDate();
        MarketHours hours = quote.instrument().marketHours();
        return priceHistory.dailyBars(instrumentId, range.startDate(today)).stream()
                // A daily close is stamped at the exchange's closing time, so every point is an instant.
                .map(bar -> new ChartPointDto(hours.closingTime(bar.date()), money(bar.close())))
                .toList();
    }

    private static BigDecimal money(double value) {
        return BigDecimal.valueOf(value).setScale(DISPLAY_SCALE, RoundingMode.HALF_UP);
    }
}
