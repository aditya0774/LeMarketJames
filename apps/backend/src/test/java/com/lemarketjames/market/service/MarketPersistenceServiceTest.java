package com.lemarketjames.market.service;

import com.lemarketjames.market.entity.MarketQuoteEntity;
import com.lemarketjames.market.entity.PriceCandleEntity;
import com.lemarketjames.market.model.MarketInstrument;
import com.lemarketjames.market.model.PriceCandle;
import com.lemarketjames.market.model.QuoteSnapshot;
import com.lemarketjames.market.repository.InstrumentMarketParamsRepository;
import com.lemarketjames.market.repository.MarketQuoteRepository;
import com.lemarketjames.market.repository.PriceCandleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Market Persistence Service Unit Tests")
class MarketPersistenceServiceTest {

    @Mock
    private InstrumentMarketParamsRepository paramsRepository;

    @Mock
    private MarketQuoteRepository quoteRepository;

    @Mock
    private PriceCandleRepository candleRepository;

    private MarketPersistenceService persistenceService;

    @BeforeEach
    void setUp() {
        persistenceService = new MarketPersistenceService(paramsRepository, quoteRepository, candleRepository);
    }

    // ========== loadInstruments() Tests ==========

    @Test
    @DisplayName("loadInstruments returns empty list when no instruments exist")
    void testLoadInstrumentsEmpty() {
        // Arrange
        when(paramsRepository.findAllSimulatedInstruments()).thenReturn(List.<Object[]>of());

        // Act
        List<MarketInstrument> instruments = persistenceService.loadInstruments();

        // Assert
        assertNotNull(instruments);
        assertTrue(instruments.isEmpty());
    }

    @Test
    @DisplayName("loadInstruments maps single row to MarketInstrument with all fields")
    void testLoadInstrumentsSingleInstrument() {
        // Arrange
        Object[] row = createInstrumentRow(1, "AAPL", "Apple Inc.", "EQUITY", "USD", "US",
                150.0, 0.15, 0.25, 0.8, 2.0, 16_000_000_000L, 50_000_000L, 6.05, 0.5);
        when(paramsRepository.findAllSimulatedInstruments()).thenReturn(List.<Object[]>of(row));

        // Act
        List<MarketInstrument> instruments = persistenceService.loadInstruments();

        // Assert
        assertEquals(1, instruments.size());
        MarketInstrument inst = instruments.get(0);
        assertEquals(1, inst.instrumentId());
        assertEquals("AAPL", inst.ticker());
        assertEquals("Apple Inc.", inst.name());
        assertEquals("EQUITY", inst.assetClass());
        assertEquals("USD", inst.currency());
        assertEquals("US", inst.location());
        assertEquals(150.0, inst.initialPrice());
        assertEquals(0.15, inst.drift());
        assertEquals(0.25, inst.volatility());
        assertEquals(0.8, inst.marketCorrelation());
        assertEquals(2.0, inst.spreadBps());
        assertEquals(16_000_000_000L, inst.sharesOutstanding());
        assertEquals(50_000_000L, inst.avgDailyVolume());
        assertEquals(6.05, inst.earningsPerShare());
        assertEquals(0.5, inst.dividendPerShare());
    }

    @Test
    @DisplayName("loadInstruments handles multiple instruments")
    void testLoadInstrumentsMultiple() {
        // Arrange
        Object[] row1 = createInstrumentRow(1, "AAPL", "Apple", "EQUITY", "USD", "US", 
                150.0, 0.15, 0.25, 0.8, 2.0, 16_000_000_000L, 50_000_000L, 6.05, 0.5);
        Object[] row2 = createInstrumentRow(2, "GOOGL", "Google", "EQUITY", "USD", "US", 
                140.0, 0.12, 0.22, 0.8, 1.5, 8_000_000_000L, 30_000_000L, 5.50, 0.0);
        when(paramsRepository.findAllSimulatedInstruments()).thenReturn(List.<Object[]>of(row1, row2));

        // Act
        List<MarketInstrument> instruments = persistenceService.loadInstruments();

        // Assert
        assertEquals(2, instruments.size());
        assertEquals("AAPL", instruments.get(0).ticker());
        assertEquals("GOOGL", instruments.get(1).ticker());
    }

    @Test
    @DisplayName("loadInstruments handles null sharesOutstanding")
    void testLoadInstrumentsNullSharesOutstanding() {
        // Arrange
        Object[] row = createInstrumentRow(1, "CRYPTO", "Bitcoin", "CRYPTO", "USD", null,
                45000.0, 0.5, 1.5, 0.9, 1.0, null, 100_000_000L, null, 0.0);
        when(paramsRepository.findAllSimulatedInstruments()).thenReturn(List.<Object[]>of(row));

        // Act
        List<MarketInstrument> instruments = persistenceService.loadInstruments();

        // Assert
        assertEquals(1, instruments.size());
        assertNull(instruments.get(0).sharesOutstanding());
    }

    @Test
    @DisplayName("loadInstruments handles null earningsPerShare")
    void testLoadInstrumentsNullEarningsPerShare() {
        // Arrange
        Object[] row = createInstrumentRow(1, "CRYPTO", "Bitcoin", "CRYPTO", "USD", null,
                45000.0, 0.5, 1.5, 0.9, 1.0, null, 100_000_000L, null, 0.0);
        when(paramsRepository.findAllSimulatedInstruments()).thenReturn(List.<Object[]>of(row));

        // Act
        List<MarketInstrument> instruments = persistenceService.loadInstruments();

        // Assert
        assertNull(instruments.get(0).earningsPerShare());
    }

    @Test
    @DisplayName("loadInstruments handles BigDecimal type coercion")
    void testLoadInstrumentsWithBigDecimalTypes() {
        // Arrange
        Object[] row = new Object[15];
        row[0] = new BigDecimal("42");
        row[1] = "MSFT";
        row[2] = "Microsoft";
        row[3] = "EQUITY";
        row[4] = "USD";
        row[5] = "US";
        row[6] = new BigDecimal("320.50");
        row[7] = new BigDecimal("0.18");
        row[8] = new BigDecimal("0.23");
        row[9] = new BigDecimal("0.85");
        row[10] = new BigDecimal("1.5");
        row[11] = new BigDecimal("2500000000");
        row[12] = new BigDecimal("40000000");
        row[13] = new BigDecimal("10.50");
        row[14] = new BigDecimal("0.75");
        when(paramsRepository.findAllSimulatedInstruments()).thenReturn(List.<Object[]>of(row));

        // Act
        List<MarketInstrument> instruments = persistenceService.loadInstruments();

        // Assert
        assertEquals(1, instruments.size());
        assertEquals(42, instruments.get(0).instrumentId());
        assertEquals(320.50, instruments.get(0).initialPrice());
    }

    // ========== loadStoredQuotes() Tests ==========

    @Test
    @DisplayName("loadStoredQuotes returns empty map when no quotes exist")
    void testLoadStoredQuotesEmpty() {
        // Arrange
        when(quoteRepository.findAll()).thenReturn(List.of());

        // Act
        Map<Integer, MarketSimulator.StoredQuote> quotes = persistenceService.loadStoredQuotes();

        // Assert
        assertNotNull(quotes);
        assertTrue(quotes.isEmpty());
    }

    @Test
    @DisplayName("loadStoredQuotes converts single entity to StoredQuote")
    void testLoadStoredQuotesSingleQuote() {
        // Arrange
        MarketQuoteEntity entity = new MarketQuoteEntity();
        entity.setInstrumentId(1);
        entity.setLastPrice(new BigDecimal("150.5000"));
        entity.setOpenPrice(new BigDecimal("149.0000"));
        entity.setHighPrice(new BigDecimal("152.0000"));
        entity.setLowPrice(new BigDecimal("148.0000"));
        entity.setPreviousClose(new BigDecimal("148.5000"));
        entity.setVolume(5_000_000L);
        entity.setLastUpdated(LocalDateTime.ofInstant(Instant.parse("2026-09-22T15:30:00Z"), ZoneOffset.UTC));
        when(quoteRepository.findAll()).thenReturn(List.of(entity));

        // Act
        Map<Integer, MarketSimulator.StoredQuote> quotes = persistenceService.loadStoredQuotes();

        // Assert
        assertEquals(1, quotes.size());
        assertTrue(quotes.containsKey(1));
        MarketSimulator.StoredQuote quote = quotes.get(1);
        assertEquals(150.5, quote.lastPrice());
        assertEquals(149.0, quote.openPrice());
        assertEquals(152.0, quote.highPrice());
        assertEquals(148.0, quote.lowPrice());
        assertEquals(148.5, quote.previousClose());
        assertEquals(5_000_000, quote.volume());
    }

    @Test
    @DisplayName("loadStoredQuotes preserves lastUpdated timestamp in UTC")
    void testLoadStoredQuotesPreservesTimestamp() {
        // Arrange
        Instant expectedInstant = Instant.parse("2026-09-22T15:30:45.123Z");
        MarketQuoteEntity entity = new MarketQuoteEntity();
        entity.setInstrumentId(1);
        entity.setLastPrice(BigDecimal.ONE);
        entity.setOpenPrice(BigDecimal.ONE);
        entity.setHighPrice(BigDecimal.ONE);
        entity.setLowPrice(BigDecimal.ONE);
        entity.setPreviousClose(BigDecimal.ONE);
        entity.setVolume(1000L);
        entity.setLastUpdated(LocalDateTime.ofInstant(expectedInstant, ZoneOffset.UTC));
        when(quoteRepository.findAll()).thenReturn(List.of(entity));

        // Act
        Map<Integer, MarketSimulator.StoredQuote> quotes = persistenceService.loadStoredQuotes();

        // Assert
        assertEquals(expectedInstant, quotes.get(1).lastUpdated());
    }

    @Test
    @DisplayName("loadStoredQuotes handles multiple quotes keyed by instrument ID")
    void testLoadStoredQuotesMultiple() {
        // Arrange
        MarketQuoteEntity entity1 = createQuoteEntity(1, 150.5, 5_000_000);
        MarketQuoteEntity entity2 = createQuoteEntity(2, 140.25, 3_000_000);
        when(quoteRepository.findAll()).thenReturn(List.of(entity1, entity2));

        // Act
        Map<Integer, MarketSimulator.StoredQuote> quotes = persistenceService.loadStoredQuotes();

        // Assert
        assertEquals(2, quotes.size());
        assertEquals(150.5, quotes.get(1).lastPrice());
        assertEquals(140.25, quotes.get(2).lastPrice());
        assertEquals(5_000_000, quotes.get(1).volume());
        assertEquals(3_000_000, quotes.get(2).volume());
    }

    @Test
    @DisplayName("loadStoredQuotes converts BigDecimal prices to double")
    void testLoadStoredQuotesDecimalToDoubleConversion() {
        // Arrange
        MarketQuoteEntity entity = new MarketQuoteEntity();
        entity.setInstrumentId(1);
        entity.setLastPrice(new BigDecimal("150.123456789")); // Higher precision than double
        entity.setOpenPrice(new BigDecimal("149.0000"));
        entity.setHighPrice(new BigDecimal("152.0000"));
        entity.setLowPrice(new BigDecimal("148.0000"));
        entity.setPreviousClose(new BigDecimal("148.5000"));
        entity.setVolume(5_000_000L);
        entity.setLastUpdated(LocalDateTime.now(ZoneOffset.UTC));
        when(quoteRepository.findAll()).thenReturn(List.of(entity));

        // Act
        Map<Integer, MarketSimulator.StoredQuote> quotes = persistenceService.loadStoredQuotes();

        // Assert
        double expectedValue = new BigDecimal("150.123456789").doubleValue();
        assertEquals(expectedValue, quotes.get(1).lastPrice(), 0.0001);
    }

    // ========== save() Tests ==========

    @Test
    @DisplayName("save with empty snapshots and candles creates no entities")
    void testSaveEmpty() {
        // Arrange
        when(quoteRepository.findAll()).thenReturn(List.of());

        // Act
        persistenceService.save(List.of(), List.of());

        // Assert
        ArgumentCaptor<List<MarketQuoteEntity>> quoteCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<PriceCandleEntity>> candleCaptor = ArgumentCaptor.forClass(List.class);
        verify(quoteRepository).saveAll(quoteCaptor.capture());
        verify(candleRepository).saveAll(candleCaptor.capture());
        assertTrue(quoteCaptor.getValue().isEmpty());
        assertTrue(candleCaptor.getValue().isEmpty());
    }

    @Test
    @DisplayName("save with no existing quotes creates new entities")
    void testSaveWithNoExistingQuotes() {
        // Arrange
        when(quoteRepository.findAll()).thenReturn(List.of());
        MarketInstrument instrument = new MarketInstrument(1, "AAPL", "Apple", "EQUITY", "USD", "US",
                150.0, 0.15, 0.25, 0.8, 2.0, 16_000_000_000L, 50_000_000L, 6.05, 0.5);
        Instant now = Instant.now();
        QuoteSnapshot snapshot = new QuoteSnapshot(instrument, 150.5, 150.4, 150.6, 149.0, 152.0,
                148.0, 148.5, 5_000_000, now, LocalDate.now());

        // Act
        persistenceService.save(List.of(snapshot), List.of());

        // Assert
        ArgumentCaptor<List<MarketQuoteEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(quoteRepository).saveAll(captor.capture());
        List<MarketQuoteEntity> saved = captor.getValue();
        assertEquals(1, saved.size());
        assertEquals(1, saved.get(0).getInstrumentId());
    }

    @Test
    @DisplayName("save applies snapshot last price to quote entity")
    void testSaveAppliesLastPrice() {
        // Arrange
        when(quoteRepository.findAll()).thenReturn(List.of());
        MarketInstrument instrument = new MarketInstrument(1, "AAPL", "Apple", "EQUITY", "USD", "US",
                150.0, 0.15, 0.25, 0.8, 2.0, 16_000_000_000L, 50_000_000L, 6.05, 0.5);
        QuoteSnapshot snapshot = new QuoteSnapshot(instrument, 150.567, 150.4, 150.6, 149.0, 152.0,
                148.0, 148.5, 5_000_000, Instant.now(), LocalDate.now());

        // Act
        persistenceService.save(List.of(snapshot), List.of());

        // Assert
        ArgumentCaptor<List<MarketQuoteEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(quoteRepository).saveAll(captor.capture());
        BigDecimal savedPrice = captor.getValue().get(0).getLastPrice();
        assertEquals(new BigDecimal("150.5670"), savedPrice);
    }

    @Test
    @DisplayName("save updates existing quote with new snapshot data")
    void testSaveUpdatesExistingQuote() {
        // Arrange
        MarketQuoteEntity existingQuote = new MarketQuoteEntity();
        existingQuote.setInstrumentId(1);
        existingQuote.setLastPrice(new BigDecimal("149.0000"));
        when(quoteRepository.findAll()).thenReturn(List.of(existingQuote));

        MarketInstrument instrument = new MarketInstrument(1, "AAPL", "Apple", "EQUITY", "USD", "US",
                150.0, 0.15, 0.25, 0.8, 2.0, 16_000_000_000L, 50_000_000L, 6.05, 0.5);
        QuoteSnapshot snapshot = new QuoteSnapshot(instrument, 150.5, 150.4, 150.6, 149.0, 152.0,
                148.0, 148.5, 5_000_000, Instant.now(), LocalDate.now());

        // Act
        persistenceService.save(List.of(snapshot), List.of());

        // Assert
        ArgumentCaptor<List<MarketQuoteEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(quoteRepository).saveAll(captor.capture());
        assertEquals(new BigDecimal("150.5000"), captor.getValue().get(0).getLastPrice());
    }

    @Test
    @DisplayName("save persists all price fields from snapshot")
    void testSaveAppliesAllPriceFields() {
        // Arrange
        when(quoteRepository.findAll()).thenReturn(List.of());
        MarketInstrument instrument = new MarketInstrument(1, "AAPL", "Apple", "EQUITY", "USD", "US",
                150.0, 0.15, 0.25, 0.8, 2.0, 16_000_000_000L, 50_000_000L, 6.05, 0.5);
        QuoteSnapshot snapshot = new QuoteSnapshot(instrument, 150.5, 150.4, 150.6, 149.0, 152.0,
                148.0, 148.5, 5_000_000, Instant.now(), LocalDate.now());

        // Act
        persistenceService.save(List.of(snapshot), List.of());

        // Assert
        ArgumentCaptor<List<MarketQuoteEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(quoteRepository).saveAll(captor.capture());
        MarketQuoteEntity entity = captor.getValue().get(0);
        assertEquals(new BigDecimal("150.4000"), entity.getBidPrice());
        assertEquals(new BigDecimal("150.6000"), entity.getAskPrice());
        assertEquals(new BigDecimal("149.0000"), entity.getOpenPrice());
        assertEquals(new BigDecimal("152.0000"), entity.getHighPrice());
        assertEquals(new BigDecimal("148.0000"), entity.getLowPrice());
        assertEquals(new BigDecimal("148.5000"), entity.getPreviousClose());
    }

    @Test
    @DisplayName("save appends candles to repository")
    void testSaveAppendsCandles() {
        // Arrange
        when(quoteRepository.findAll()).thenReturn(List.of());
        Instant intervalStart = Instant.parse("2026-09-22T15:30:00Z");
        PriceCandle candle = new PriceCandle(1, intervalStart, 149.0, 152.0, 148.0, 150.5, 5_000_000);

        // Act
        persistenceService.save(List.of(), List.of(candle));

        // Assert
        ArgumentCaptor<List<PriceCandleEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(candleRepository).saveAll(captor.capture());
        assertEquals(1, captor.getValue().size());
    }

    @Test
    @DisplayName("save converts candle close price with 4 decimal scale")
    void testSaveCandleClosePriceScaling() {
        // Arrange
        when(quoteRepository.findAll()).thenReturn(List.of());
        Instant intervalStart = Instant.now();
        PriceCandle candle = new PriceCandle(1, intervalStart, 149.123456, 152.0, 148.0, 150.5, 5_000_000);

        // Act
        persistenceService.save(List.of(), List.of(candle));

        // Assert
        ArgumentCaptor<List<PriceCandleEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(candleRepository).saveAll(captor.capture());
        assertEquals(4, captor.getValue().get(0).getOpenPrice().scale());
    }

    @Test
    @DisplayName("save preserves candle interval start time in UTC")
    void testSaveCandleIntervalStartInUTC() {
        // Arrange
        when(quoteRepository.findAll()).thenReturn(List.of());
        Instant intervalStart = Instant.parse("2026-09-22T15:30:00Z");
        PriceCandle candle = new PriceCandle(1, intervalStart, 149.0, 152.0, 148.0, 150.5, 5_000_000);

        // Act
        persistenceService.save(List.of(), List.of(candle));

        // Assert
        ArgumentCaptor<List<PriceCandleEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(candleRepository).saveAll(captor.capture());
        LocalDateTime saved = captor.getValue().get(0).getIntervalStart();
        assertEquals(15, saved.getHour());
        assertEquals(30, saved.getMinute());
    }

    // ========== toMarketInstrument() Static Method Tests ==========

    @Test
    @DisplayName("toMarketInstrument maps all 15 fields from object array")
    void testToMarketInstrumentMapsAllFields() {
        // Arrange
        Object[] row = createInstrumentRow(42, "MSFT", "Microsoft", "EQUITY", "USD", "US",
                320.50, 0.18, 0.23, 0.85, 1.5, 2_500_000_000L, 40_000_000L, 10.50, 0.75);

        // Act
        MarketInstrument instrument = MarketPersistenceService.toMarketInstrument(row);

        // Assert
        assertEquals(42, instrument.instrumentId());
        assertEquals("MSFT", instrument.ticker());
        assertEquals("Microsoft", instrument.name());
        assertEquals("EQUITY", instrument.assetClass());
        assertEquals("USD", instrument.currency());
        assertEquals("US", instrument.location());
        assertEquals(320.50, instrument.initialPrice());
        assertEquals(0.18, instrument.drift());
        assertEquals(0.23, instrument.volatility());
        assertEquals(0.85, instrument.marketCorrelation());
        assertEquals(1.5, instrument.spreadBps());
        assertEquals(2_500_000_000L, instrument.sharesOutstanding());
        assertEquals(40_000_000L, instrument.avgDailyVolume());
        assertEquals(10.50, instrument.earningsPerShare());
        assertEquals(0.75, instrument.dividendPerShare());
    }

    @Test
    @DisplayName("toMarketInstrument coerces numeric types through Number interface")
    void testToMarketInstrumentNumericTypeCoercion() {
        // Arrange - Use Long for all numeric fields
        Object[] row = new Object[15];
        row[0] = 1L;
        row[1] = "TEST";
        row[2] = "Test";
        row[3] = "EQUITY";
        row[4] = "USD";
        row[5] = "US";
        row[6] = 100L;
        row[7] = 1L;
        row[8] = 2L;
        row[9] = 1L;
        row[10] = 1L;
        row[11] = 1_000_000L;
        row[12] = 100_000L;
        row[13] = 5L;
        row[14] = 0L;

        // Act
        MarketInstrument instrument = MarketPersistenceService.toMarketInstrument(row);

        // Assert
        assertEquals(1, instrument.instrumentId());
        assertEquals(100.0, instrument.initialPrice());
    }

    @Test
    @DisplayName("toMarketInstrument handles null sharesOutstanding")
    void testToMarketInstrumentNullSharesOutstanding() {
        // Arrange
        Object[] row = new Object[15];
        row[0] = 1L;
        row[1] = "CRYPTO";
        row[2] = "Bitcoin";
        row[3] = "CRYPTO";
        row[4] = "USD";
        row[5] = null;
        row[6] = 45000.0;
        row[7] = 0.5;
        row[8] = 1.5;
        row[9] = 0.9;
        row[10] = 1.0;
        row[11] = null;
        row[12] = 100_000_000L;
        row[13] = null;
        row[14] = 0.0;

        // Act
        MarketInstrument instrument = MarketPersistenceService.toMarketInstrument(row);

        // Assert
        assertNull(instrument.sharesOutstanding());
    }

    @Test
    @DisplayName("toMarketInstrument handles null earningsPerShare")
    void testToMarketInstrumentNullEarningsPerShare() {
        // Arrange
        Object[] row = new Object[15];
        row[0] = 1L;
        row[1] = "CRYPTO";
        row[2] = "Bitcoin";
        row[3] = "CRYPTO";
        row[4] = "USD";
        row[5] = null;
        row[6] = 45000.0;
        row[7] = 0.5;
        row[8] = 1.5;
        row[9] = 0.9;
        row[10] = 1.0;
        row[11] = null;
        row[12] = 100_000_000L;
        row[13] = null;
        row[14] = 0.0;

        // Act
        MarketInstrument instrument = MarketPersistenceService.toMarketInstrument(row);

        // Assert
        assertNull(instrument.earningsPerShare());
    }

    // ========== Helper Tests for Price Conversion ==========

    @Test
    @DisplayName("price conversion uses HALF_UP rounding mode")
    void testPriceRoundingMode() {
        // This is tested implicitly through save() but documenting the expectation
        // BigDecimal.valueOf(150.12345).setScale(4, RoundingMode.HALF_UP) -> 150.1235

        // Arrange
        when(quoteRepository.findAll()).thenReturn(List.of());
        MarketInstrument instrument = new MarketInstrument(1, "TEST", "Test", "EQUITY", "USD", "US",
                100.0, 0.1, 0.2, 0.8, 1.0, 1_000_000L, 1_000L, 1.0, 0.0);
        QuoteSnapshot snapshot = new QuoteSnapshot(instrument, 150.12345, 150.4, 150.6, 149.0, 152.0,
                148.0, 148.5, 5_000_000, Instant.now(), LocalDate.now());

        // Act
        persistenceService.save(List.of(snapshot), List.of());

        // Assert
        ArgumentCaptor<List<MarketQuoteEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(quoteRepository).saveAll(captor.capture());
        // 150.12345 should round to 150.1235 with HALF_UP
        assertEquals(new BigDecimal("150.1235"), captor.getValue().get(0).getLastPrice());
    }

    @Test
    @DisplayName("timestamp conversion preserves UTC timezone")
    void testTimestampPreservesUTC() {
        // Arrange
        when(quoteRepository.findAll()).thenReturn(List.of());
        Instant instantUTC = Instant.parse("2026-09-22T15:30:45Z");
        MarketInstrument instrument = new MarketInstrument(1, "TEST", "Test", "EQUITY", "USD", "US",
                100.0, 0.1, 0.2, 0.8, 1.0, 1_000_000L, 1_000L, 1.0, 0.0);
        QuoteSnapshot snapshot = new QuoteSnapshot(instrument, 150.0, 150.4, 150.6, 149.0, 152.0,
                148.0, 148.5, 5_000_000, instantUTC, LocalDate.now());

        // Act
        persistenceService.save(List.of(snapshot), List.of());

        // Assert
        ArgumentCaptor<List<MarketQuoteEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(quoteRepository).saveAll(captor.capture());
        LocalDateTime saved = captor.getValue().get(0).getLastUpdated();
        assertEquals(15, saved.getHour());
        assertEquals(30, saved.getMinute());
        assertEquals(45, saved.getSecond());
    }

    // ========== Helper Methods ==========

    private Object[] createInstrumentRow(int id, String ticker, String name, String assetClass,
                                         String currency, String location, double initialPrice,
                                         double drift, double volatility, double marketCorrelation,
                                         double spreadBps, Long sharesOutstanding, long avgDailyVolume,
                                         Double earningsPerShare, double dividendPerShare) {
        Object[] row = new Object[15];
        row[0] = id;
        row[1] = ticker;
        row[2] = name;
        row[3] = assetClass;
        row[4] = currency;
        row[5] = location;
        row[6] = initialPrice;
        row[7] = drift;
        row[8] = volatility;
        row[9] = marketCorrelation;
        row[10] = spreadBps;
        row[11] = sharesOutstanding;
        row[12] = avgDailyVolume;
        row[13] = earningsPerShare;
        row[14] = dividendPerShare;
        return row;
    }

    private MarketQuoteEntity createQuoteEntity(int instrumentId, double lastPrice, long volume) {
        MarketQuoteEntity entity = new MarketQuoteEntity();
        entity.setInstrumentId(instrumentId);
        entity.setLastPrice(new BigDecimal(lastPrice));
        entity.setOpenPrice(new BigDecimal("149.0000"));
        entity.setHighPrice(new BigDecimal("152.0000"));
        entity.setLowPrice(new BigDecimal("148.0000"));
        entity.setPreviousClose(new BigDecimal("148.5000"));
        entity.setVolume(volume);
        entity.setLastUpdated(LocalDateTime.now(ZoneOffset.UTC));
        return entity;
    }
}
