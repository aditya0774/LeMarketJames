package com.lemarketjames.market.service;

import com.lemarketjames.market.entity.MarketQuoteEntity;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for MarketPersistenceService.
 * Tests database persistence for market quotes and price candles.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MarketPersistenceService Tests")
class MarketPersistenceServiceTest {

    @Mock
    private InstrumentMarketParamsRepository paramsRepository;

    @Mock
    private MarketQuoteRepository quoteRepository;

    @Mock
    private PriceCandleRepository candleRepository;

    @InjectMocks
    private MarketPersistenceService service;

    private static final Integer TEST_INSTRUMENT_ID = 5;
    private static final String TEST_SYMBOL = "AAPL";
    private static final Instant TEST_INSTANT = Instant.parse("2026-10-05T18:00:00Z");

    @BeforeEach
    void setUp() {
        // Setup done by MockitoExtension
    }

    // ========== loadInstruments Tests ==========

    @Test
    @DisplayName("Should load instruments from repository")
    void testLoadInstruments_Success() {
        // Arrange
        Object[] row = {
            5, "AAPL", "Apple Inc.", "US0378331005", "NASDAQ", "Technology",
            100.5, 102.0, 99.5, 101.0,
            98.0, 1000000L, 50000000L, 0.5, 100.0
        };
        when(paramsRepository.findAllSimulatedInstruments()).thenReturn(Collections.singletonList(row));

        // Act
        List<MarketInstrument> instruments = service.loadInstruments();

        // Assert
        assertEquals(1, instruments.size());
        assertNotNull(instruments.get(0));
        verify(paramsRepository).findAllSimulatedInstruments();
    }

    @Test
    @DisplayName("Should return empty list when no instruments exist")
    void testLoadInstruments_Empty() {
        // Arrange
        when(paramsRepository.findAllSimulatedInstruments()).thenReturn(Collections.emptyList());

        // Act
        List<MarketInstrument> instruments = service.loadInstruments();

        // Assert
        assertTrue(instruments.isEmpty());
        verify(paramsRepository).findAllSimulatedInstruments();
    }

    @Test
    @DisplayName("Should handle multiple instruments")
    void testLoadInstruments_Multiple() {
        // Arrange
        Object[] row1 = {
            5, "AAPL", "Apple Inc.", "US0378331005", "NASDAQ", "Technology",
            100.5, 102.0, 99.5, 101.0, 98.0, 1000000L, 50000000L, 0.5, 100.0
        };
        Object[] row2 = {
            6, "MSFT", "Microsoft", "US5949181045", "NASDAQ", "Technology",
            200.0, 205.0, 195.0, 202.0, 195.0, 1500000L, 25000000L, 1.0, 200.0
        };
        when(paramsRepository.findAllSimulatedInstruments()).thenReturn(Arrays.asList(row1, row2));

        // Act
        List<MarketInstrument> instruments = service.loadInstruments();

        // Assert
        assertEquals(2, instruments.size());
    }

    // ========== loadStoredQuotes Tests ==========

    @Test
    @DisplayName("Should load stored quotes from repository")
    void testLoadStoredQuotes_Success() {
        // Arrange
        MarketQuoteEntity quote = new MarketQuoteEntity();
        quote.setInstrumentId(TEST_INSTRUMENT_ID);
        quote.setLastPrice(BigDecimal.valueOf(100.5000));
        quote.setOpenPrice(BigDecimal.valueOf(101.0000));
        quote.setHighPrice(BigDecimal.valueOf(102.0000));
        quote.setLowPrice(BigDecimal.valueOf(99.5000));
        quote.setPreviousClose(BigDecimal.valueOf(98.0000));
        quote.setVolume(50000000L);
        quote.setLastUpdated(LocalDateTime.ofInstant(TEST_INSTANT, ZoneOffset.UTC));
        
        when(quoteRepository.findAll()).thenReturn(List.of(quote));

        // Act
        Map<Integer, MarketSimulator.StoredQuote> stored = service.loadStoredQuotes();

        // Assert
        assertEquals(1, stored.size());
        assertTrue(stored.containsKey(TEST_INSTRUMENT_ID));
        assertNotNull(stored.get(TEST_INSTRUMENT_ID));
        verify(quoteRepository).findAll();
    }

    @Test
    @DisplayName("Should return empty map when no quotes exist")
    void testLoadStoredQuotes_Empty() {
        // Arrange
        when(quoteRepository.findAll()).thenReturn(Collections.emptyList());

        // Act
        Map<Integer, MarketSimulator.StoredQuote> stored = service.loadStoredQuotes();

        // Assert
        assertTrue(stored.isEmpty());
        verify(quoteRepository).findAll();
    }

    @Test
    @DisplayName("Should handle multiple stored quotes")
    void testLoadStoredQuotes_Multiple() {
        // Arrange
        MarketQuoteEntity quote1 = new MarketQuoteEntity();
        quote1.setInstrumentId(5);
        quote1.setLastPrice(BigDecimal.valueOf(100.5000));
        quote1.setOpenPrice(BigDecimal.valueOf(101.0000));
        quote1.setHighPrice(BigDecimal.valueOf(102.0000));
        quote1.setLowPrice(BigDecimal.valueOf(99.5000));
        quote1.setPreviousClose(BigDecimal.valueOf(98.0000));
        quote1.setVolume(50000000L);
        quote1.setLastUpdated(LocalDateTime.ofInstant(TEST_INSTANT, ZoneOffset.UTC));

        MarketQuoteEntity quote2 = new MarketQuoteEntity();
        quote2.setInstrumentId(6);
        quote2.setLastPrice(BigDecimal.valueOf(200.0000));
        quote2.setOpenPrice(BigDecimal.valueOf(202.0000));
        quote2.setHighPrice(BigDecimal.valueOf(205.0000));
        quote2.setLowPrice(BigDecimal.valueOf(195.0000));
        quote2.setPreviousClose(BigDecimal.valueOf(195.0000));
        quote2.setVolume(25000000L);
        quote2.setLastUpdated(LocalDateTime.ofInstant(TEST_INSTANT, ZoneOffset.UTC));

        when(quoteRepository.findAll()).thenReturn(List.of(quote1, quote2));

        // Act
        Map<Integer, MarketSimulator.StoredQuote> stored = service.loadStoredQuotes();

        // Assert
        assertEquals(2, stored.size());
        assertTrue(stored.containsKey(5));
        assertTrue(stored.containsKey(6));
    }

    // ========== save Tests ==========

    @Test
    @DisplayName("Should save quotes and candles together")
    void testSave_QuotesAndCandles() {
        // Arrange
        MarketInstrument instrument = createTestInstrument();
        QuoteSnapshot snapshot = createTestSnapshot(instrument);
        PriceCandle candle = createTestCandle();

        when(quoteRepository.findAll()).thenReturn(Collections.emptyList());
        when(quoteRepository.saveAll(anyList())).thenReturn(Collections.emptyList());
        when(candleRepository.saveAll(anyList())).thenReturn(Collections.emptyList());

        // Act
        service.save(List.of(snapshot), List.of(candle));

        // Assert
        verify(quoteRepository).saveAll(any());
        verify(candleRepository).saveAll(any());
    }

    @Test
    @DisplayName("Should upsert existing quotes when saving")
    void testSave_UpsertsExistingQuotes() {
        // Arrange
        MarketInstrument instrument = createTestInstrument();
        QuoteSnapshot snapshot = createTestSnapshot(instrument);

        MarketQuoteEntity existing = new MarketQuoteEntity();
        existing.setInstrumentId(TEST_INSTRUMENT_ID);
        existing.setLastPrice(BigDecimal.valueOf(95.0000));

        when(quoteRepository.findAll()).thenReturn(List.of(existing));
        when(quoteRepository.saveAll(anyList())).thenReturn(Collections.emptyList());
        when(candleRepository.saveAll(anyList())).thenReturn(Collections.emptyList());

        // Act
        service.save(List.of(snapshot), Collections.emptyList());

        // Assert
        verify(quoteRepository).saveAll(any());
        verify(quoteRepository).findAll();
    }

    @Test
    @DisplayName("Should save empty collections without error")
    void testSave_Empty() {
        // Arrange
        when(quoteRepository.findAll()).thenReturn(Collections.emptyList());
        when(quoteRepository.saveAll(anyList())).thenReturn(Collections.emptyList());
        when(candleRepository.saveAll(anyList())).thenReturn(Collections.emptyList());

        // Act & Assert
        assertDoesNotThrow(() -> service.save(Collections.emptyList(), Collections.emptyList()));
    }

    @Test
    @DisplayName("Should save only quotes when no candles provided")
    void testSave_OnlyQuotes() {
        // Arrange
        MarketInstrument instrument = createTestInstrument();
        QuoteSnapshot snapshot = createTestSnapshot(instrument);

        when(quoteRepository.findAll()).thenReturn(Collections.emptyList());
        when(quoteRepository.saveAll(anyList())).thenReturn(Collections.emptyList());
        when(candleRepository.saveAll(anyList())).thenReturn(Collections.emptyList());

        // Act
        service.save(List.of(snapshot), Collections.emptyList());

        // Assert
        verify(quoteRepository).saveAll(any());
        verify(candleRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("Should save only candles when no quotes provided")
    void testSave_OnlyCandles() {
        // Arrange
        PriceCandle candle = createTestCandle();

        when(quoteRepository.findAll()).thenReturn(Collections.emptyList());
        when(quoteRepository.saveAll(anyList())).thenReturn(Collections.emptyList());
        when(candleRepository.saveAll(anyList())).thenReturn(Collections.emptyList());

        // Act
        service.save(Collections.emptyList(), List.of(candle));

        // Assert
        verify(quoteRepository).saveAll(any());
        verify(candleRepository).saveAll(any());
    }

    @Test
    @DisplayName("Should handle repository errors when saving quotes")
    void testSave_QuoteRepositoryError() {
        // Arrange
        QuoteSnapshot snapshot = createTestSnapshot(createTestInstrument());
        Collection<QuoteSnapshot> snapshots = List.of(snapshot);
        when(quoteRepository.findAll()).thenReturn(Collections.emptyList());
        when(quoteRepository.saveAll(anyList())).thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        assertThrows(RuntimeException.class, () -> 
            service.save(snapshots, Collections.emptyList())
        );
    }

    @Test
    @DisplayName("Should handle repository errors when saving candles")
    void testSave_CandleRepositoryError() {
        // Arrange
        QuoteSnapshot snapshot = createTestSnapshot(createTestInstrument());
        PriceCandle candle = createTestCandle();
        Collection<QuoteSnapshot> snapshots = List.of(snapshot);
        List<PriceCandle> candles = List.of(candle);

        when(quoteRepository.findAll()).thenReturn(Collections.emptyList());
        when(quoteRepository.saveAll(anyList())).thenReturn(Collections.emptyList());
        when(candleRepository.saveAll(anyList())).thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        assertThrows(RuntimeException.class, () -> 
            service.save(snapshots, candles)
        );
    }

    @Test
    @DisplayName("Should handle multiple snapshots and candles")
    void testSave_MultipleSnapshots() {
        // Arrange
        MarketInstrument inst1 = createTestInstrument();
        MarketInstrument inst2 = createTestInstrument(6, "MSFT");
        
        QuoteSnapshot snap1 = createTestSnapshot(inst1);
        QuoteSnapshot snap2 = createTestSnapshot(inst2);
        
        PriceCandle candle1 = createTestCandle(TEST_INSTRUMENT_ID);
        PriceCandle candle2 = createTestCandle(6);

        when(quoteRepository.findAll()).thenReturn(Collections.emptyList());
        when(quoteRepository.saveAll(anyList())).thenReturn(Collections.emptyList());
        when(candleRepository.saveAll(anyList())).thenReturn(Collections.emptyList());

        // Act
        service.save(List.of(snap1, snap2), List.of(candle1, candle2));

        // Assert
        verify(quoteRepository).saveAll(any());
        verify(candleRepository).saveAll(any());
    }

    // ========== Helper Methods ==========

    private MarketInstrument createTestInstrument() {
        return createTestInstrument(TEST_INSTRUMENT_ID, TEST_SYMBOL);
    }

    private MarketInstrument createTestInstrument(Integer id, String symbol) {
        return new MarketInstrument(
            id, symbol, "Test Company", "US0000000000", "NASDAQ", "Technology",
            100.0, 102.0, 99.0, 101.0, 98.0, 1000000L, 50000000L, 0.5, 100.0
        );
    }

    private QuoteSnapshot createTestSnapshot(MarketInstrument instrument) {
        return new QuoteSnapshot(
            instrument,
            100.5, 100.0, 101.0,
            101.0, 102.0, 99.5,
            98.0, 50000000L,
            TEST_INSTANT, null
        );
    }

    private PriceCandle createTestCandle() {
        return createTestCandle(TEST_INSTRUMENT_ID);
    }

    private PriceCandle createTestCandle(Integer instrumentId) {
        return new PriceCandle(
            instrumentId,
            TEST_INSTANT,
            100.0, 102.0, 99.0, 101.0,
            50000000L
        );
    }
}
