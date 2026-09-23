package com.lemarketjames.market.service;

import com.lemarketjames.market.entity.MarketQuoteEntity;
import com.lemarketjames.market.entity.PriceCandleEntity;
import com.lemarketjames.market.model.MarketInstrument;
import com.lemarketjames.market.model.PriceCandle;
import com.lemarketjames.market.model.QuoteSnapshot;
import com.lemarketjames.market.repository.InstrumentMarketParamsRepository;
import com.lemarketjames.market.repository.MarketQuoteRepository;
import com.lemarketjames.market.repository.PriceCandleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Moves market data between the database and the in-memory simulator.
 *
 * <p>Live prices change every second, which is too often to write on every tick, so the latest
 * prices are written periodically ({@code sim.snapshot-interval-ms}) along with any completed
 * 1-minute candles. All timestamps are stored as UTC.
 */
@Service
public class MarketPersistenceService {

    /** Matches the NUMERIC(14,4) price columns. */
    private static final int PRICE_SCALE = 4;

    private final InstrumentMarketParamsRepository paramsRepository;
    private final MarketQuoteRepository quoteRepository;
    private final PriceCandleRepository candleRepository;

    public MarketPersistenceService(InstrumentMarketParamsRepository paramsRepository,
                                    MarketQuoteRepository quoteRepository,
                                    PriceCandleRepository candleRepository) {
        this.paramsRepository = paramsRepository;
        this.quoteRepository = quoteRepository;
        this.candleRepository = candleRepository;
    }

    /** @return every instrument that has simulation parameters */
    @Transactional(readOnly = true)
    public List<MarketInstrument> loadInstruments() {
        return paramsRepository.findAllSimulatedInstruments().stream()
                .map(MarketPersistenceService::toMarketInstrument)
                .toList();
    }

    /** @return the last stored price for each instrument, keyed by instrument id */
    @Transactional(readOnly = true)
    public Map<Integer, MarketSimulator.StoredQuote> loadStoredQuotes() {
        Map<Integer, MarketSimulator.StoredQuote> stored = new HashMap<>();
        for (MarketQuoteEntity entity : quoteRepository.findAll()) {
            stored.put(entity.getInstrumentId(), new MarketSimulator.StoredQuote(
                    entity.getLastPrice().doubleValue(),
                    entity.getOpenPrice().doubleValue(),
                    entity.getHighPrice().doubleValue(),
                    entity.getLowPrice().doubleValue(),
                    entity.getPreviousClose().doubleValue(),
                    entity.getVolume(),
                    entity.getLastUpdated().toInstant(ZoneOffset.UTC)));
        }
        return stored;
    }

    /**
     * Upserts the latest price of every instrument into {@code market_quotes} (one row each) and
     * appends completed candles to {@code price_candles}, in a single transaction.
     */
    @Transactional
    public void save(Collection<QuoteSnapshot> snapshots, List<PriceCandle> candles) {
        Map<Integer, MarketQuoteEntity> existing = quoteRepository.findAll().stream()
                .collect(Collectors.toMap(MarketQuoteEntity::getInstrumentId, Function.identity()));

        List<MarketQuoteEntity> quotes = snapshots.stream()
                .map(snapshot -> applySnapshot(
                        existing.computeIfAbsent(snapshot.instrument().instrumentId(), id -> new MarketQuoteEntity()),
                        snapshot))
                .toList();
        quoteRepository.saveAll(quotes);

        candleRepository.saveAll(candles.stream().map(MarketPersistenceService::toEntity).toList());
    }

    /**
     * Maps one row of {@link InstrumentMarketParamsRepository#findAllSimulatedInstruments()}.
     * Numeric columns are read through {@link Number} because JDBC drivers differ in the exact
     * boxed type they return (e.g. BigDecimal vs Long).
     */
    static MarketInstrument toMarketInstrument(Object[] row) {
        return new MarketInstrument(
                ((Number) row[0]).intValue(),
                (String) row[1],
                (String) row[2],
                (String) row[3],
                (String) row[4],
                (String) row[5],
                ((Number) row[6]).doubleValue(),
                ((Number) row[7]).doubleValue(),
                ((Number) row[8]).doubleValue(),
                ((Number) row[9]).doubleValue(),
                ((Number) row[10]).doubleValue(),
                row[11] == null ? null : ((Number) row[11]).longValue(),
                ((Number) row[12]).longValue(),
                row[13] == null ? null : ((Number) row[13]).doubleValue(),
                ((Number) row[14]).doubleValue());
    }

    private static MarketQuoteEntity applySnapshot(MarketQuoteEntity entity, QuoteSnapshot snapshot) {
        entity.setInstrumentId(snapshot.instrument().instrumentId());
        entity.setBidPrice(price(snapshot.bidPrice()));
        entity.setAskPrice(price(snapshot.askPrice()));
        entity.setLastPrice(price(snapshot.lastPrice()));
        entity.setOpenPrice(price(snapshot.openPrice()));
        entity.setHighPrice(price(snapshot.highPrice()));
        entity.setLowPrice(price(snapshot.lowPrice()));
        entity.setPreviousClose(price(snapshot.previousClose()));
        entity.setVolume(snapshot.volume());
        entity.setLastUpdated(utc(snapshot.lastUpdated()));
        return entity;
    }

    private static PriceCandleEntity toEntity(PriceCandle candle) {
        return new PriceCandleEntity(candle.instrumentId(), utc(candle.intervalStart()),
                price(candle.openPrice()), price(candle.highPrice()), price(candle.lowPrice()),
                price(candle.closePrice()), candle.volume());
    }

    private static BigDecimal price(double value) {
        return BigDecimal.valueOf(value).setScale(PRICE_SCALE, RoundingMode.HALF_UP);
    }

    private static LocalDateTime utc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
