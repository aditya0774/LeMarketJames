package com.lemarketjames.market.service;

import com.lemarketjames.market.entity.PriceCandleEntity;
import com.lemarketjames.market.entity.PriceHistoryEntity;
import com.lemarketjames.market.model.DailyBar;
import com.lemarketjames.market.model.PricePoint;
import com.lemarketjames.market.model.QuoteSnapshot;
import com.lemarketjames.market.repository.PriceCandleRepository;
import com.lemarketjames.market.repository.PriceHistoryRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * market-service's own {@link PriceHistoryService}: past days come from {@code price_history} and
 * today's minutes from {@code price_candles}, and both end with the live quote. The stored rows lag
 * the simulator by up to one snapshot interval, so the live price is what makes a chart's last point
 * agree with the quote shown next to it.
 */
@Service
public class PriceHistoryReader implements PriceHistoryService {

    private final PriceHistoryRepository historyRepository;
    private final PriceCandleRepository candleRepository;
    private final MarketDataService marketData;

    public PriceHistoryReader(PriceHistoryRepository historyRepository, PriceCandleRepository candleRepository,
                              MarketDataService marketData) {
        this.historyRepository = historyRepository;
        this.candleRepository = candleRepository;
        this.marketData = marketData;
    }

    @Override
    public List<DailyBar> dailyBars(int instrumentId, LocalDate from) {
        Optional<QuoteSnapshot> live = marketData.findByInstrumentId(instrumentId);
        if (live.isEmpty()) {
            return List.of();
        }
        QuoteSnapshot quote = live.get();
        LocalDate today = quote.tradingDate();

        List<DailyBar> bars = new ArrayList<>();
        for (PriceHistoryEntity row : historyRepository
                .findByInstrumentIdAndTradingDateGreaterThanEqualOrderByTradingDate(instrumentId, from)) {
            // The stored bar for today is an older copy of the live one appended below.
            if (today == null || row.getTradingDate().isBefore(today)) {
                bars.add(toBar(row));
            }
        }
        if (today != null && !today.isBefore(from)) {
            bars.add(new DailyBar(today, quote.openPrice(), quote.highPrice(), quote.lowPrice(),
                    quote.lastPrice(), quote.volume()));
        }
        return bars;
    }

    @Override
    public List<PricePoint> intraday(int instrumentId) {
        Optional<QuoteSnapshot> live = marketData.findByInstrumentId(instrumentId);
        if (live.isEmpty() || live.get().tradingDate() == null) {
            return List.of();
        }
        QuoteSnapshot quote = live.get();
        // Candles are stored in UTC, the trading day is exchange-local: start from its local midnight.
        LocalDateTime dayStart = LocalDateTime.ofInstant(
                quote.tradingDate().atStartOfDay(quote.instrument().marketHours().zone()).toInstant(),
                ZoneOffset.UTC);

        List<PricePoint> points = new ArrayList<>();
        for (PriceCandleEntity candle : candleRepository
                .findByInstrumentIdAndIntervalStartGreaterThanEqualOrderByIntervalStart(instrumentId, dayStart)) {
            // A candle's close is the price at the end of its minute.
            points.add(new PricePoint(candle.getIntervalStart().plusMinutes(1).toInstant(ZoneOffset.UTC),
                    candle.getClosePrice().doubleValue()));
        }
        if (quote.lastUpdated() != null) {
            points.add(new PricePoint(quote.lastUpdated(), quote.lastPrice()));
        }
        return points;
    }

    private static DailyBar toBar(PriceHistoryEntity row) {
        return new DailyBar(row.getTradingDate(), value(row.getOpenPrice()), value(row.getHighPrice()),
                value(row.getLowPrice()), value(row.getClosePrice()), row.getVolume());
    }

    private static double value(BigDecimal price) {
        return price.doubleValue();
    }
}
