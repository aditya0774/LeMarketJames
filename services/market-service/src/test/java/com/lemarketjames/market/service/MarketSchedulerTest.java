package com.lemarketjames.market.service;

import com.lemarketjames.market.model.PriceCandle;
import com.lemarketjames.market.model.QuoteSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for MarketScheduler.
 * Tests background scheduling of market ticks and price snapshots.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MarketScheduler Tests")
class MarketSchedulerTest {

    @Mock
    private MarketSimulator simulator;

    @Mock
    private MarketPersistenceService persistence;

    @InjectMocks
    private MarketScheduler scheduler;

    private static final Instant TEST_INSTANT = Instant.parse("2026-10-05T18:00:00Z");

    @BeforeEach
    void setUp() {
        // Setup done by MockitoExtension
    }

    // ========== tick() Tests ==========

    @Test
    @DisplayName("Should call simulator.tick() on scheduled tick")
    void testTick_CallsSimulator() {
        // Arrange
        doNothing().when(simulator).tick();

        // Act
        scheduler.tick();

        // Assert
        verify(simulator).tick();
    }

    @Test
    @DisplayName("Should propagate exceptions from simulator.tick()")
    void testTick_PropagatesException() {
        // Arrange
        doThrow(new RuntimeException("Simulator error")).when(simulator).tick();

        // Act & Assert
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () -> scheduler.tick());
        verify(simulator).tick();
    }

    @Test
    @DisplayName("Should handle simulator.tick() called multiple times")
    void testTick_MultipleInvocations() {
        // Arrange
        doNothing().when(simulator).tick();

        // Act
        scheduler.tick();
        scheduler.tick();
        scheduler.tick();

        // Assert
        verify(simulator, org.mockito.Mockito.times(3)).tick();
    }

    // ========== saveSnapshot() Tests ==========

    @Test
    @DisplayName("Should save latest snapshots and candles")
    void testSaveSnapshot_Success() {
        // Arrange
        List<QuoteSnapshot> snapshots = Collections.emptyList();
        List<PriceCandle> candles = Collections.emptyList();

        when(simulator.latestSnapshots()).thenReturn(snapshots);
        when(simulator.drainCompletedCandles()).thenReturn(candles);
        doNothing().when(persistence).save(anyList(), anyList());

        // Act
        scheduler.saveSnapshot();

        // Assert
        verify(simulator).latestSnapshots();
        verify(simulator).drainCompletedCandles();
        verify(persistence).save(eq(snapshots), eq(candles));
    }

    @Test
    @DisplayName("Should drain candles on each save")
    void testSaveSnapshot_DrainsCandles() {
        // Arrange
        List<QuoteSnapshot> snapshots = Collections.emptyList();
        List<PriceCandle> candles = List.of(createTestCandle());

        when(simulator.latestSnapshots()).thenReturn(snapshots);
        when(simulator.drainCompletedCandles()).thenReturn(candles);
        doNothing().when(persistence).save(anyList(), anyList());

        // Act
        scheduler.saveSnapshot();

        // Assert
        verify(simulator).drainCompletedCandles();
        verify(persistence).save(eq(snapshots), eq(candles));
    }

    @Test
    @DisplayName("Should handle empty snapshots and candles")
    void testSaveSnapshot_Empty() {
        // Arrange
        when(simulator.latestSnapshots()).thenReturn(Collections.emptyList());
        when(simulator.drainCompletedCandles()).thenReturn(Collections.emptyList());
        doNothing().when(persistence).save(anyList(), anyList());

        // Act & Assert
        assertDoesNotThrow(() -> scheduler.saveSnapshot());
        verify(persistence).save(anyList(), anyList());
    }

    @Test
    @DisplayName("Should handle persistence failures gracefully")
    void testSaveSnapshot_PersistenceError() {
        // Arrange
        when(simulator.latestSnapshots()).thenReturn(Collections.emptyList());
        when(simulator.drainCompletedCandles()).thenReturn(Collections.emptyList());
        doThrow(new RuntimeException("Database error")).when(persistence).save(anyList(), anyList());

        // Act & Assert - should not throw, error is logged
        assertDoesNotThrow(() -> scheduler.saveSnapshot());
        verify(persistence).save(anyList(), anyList());
    }

    @Test
    @DisplayName("Should not rethrow persistence errors")
    void testSaveSnapshot_ErrorHandling() {
        // Arrange
        when(simulator.latestSnapshots()).thenReturn(Collections.emptyList());
        when(simulator.drainCompletedCandles()).thenReturn(Collections.emptyList());
        doThrow(new RuntimeException("Persistence error"))
            .when(persistence).save(anyList(), anyList());

        // Act - should not throw
        assertDoesNotThrow(() -> scheduler.saveSnapshot());

        // Assert - persistence was still called despite the error
        verify(persistence).save(anyList(), anyList());
    }

    @Test
    @DisplayName("Should get latest snapshots before saving")
    void testSaveSnapshot_GetsLatestSnapshots() {
        // Arrange
        List<QuoteSnapshot> snapshots = Collections.emptyList();
        when(simulator.latestSnapshots()).thenReturn(snapshots);
        when(simulator.drainCompletedCandles()).thenReturn(Collections.emptyList());
        doNothing().when(persistence).save(anyList(), anyList());

        // Act
        scheduler.saveSnapshot();

        // Assert - latestSnapshots must be called before save
        verify(simulator).latestSnapshots();
        verify(persistence).save(eq(snapshots), anyList());
    }

    @Test
    @DisplayName("Should call save with exact snapshots and candles from simulator")
    void testSaveSnapshot_ExactArguments() {
        // Arrange
        List<QuoteSnapshot> snapshots = List.of();
        List<PriceCandle> candles = List.of();

        when(simulator.latestSnapshots()).thenReturn(snapshots);
        when(simulator.drainCompletedCandles()).thenReturn(candles);
        doNothing().when(persistence).save(anyList(), anyList());

        // Act
        scheduler.saveSnapshot();

        // Assert - verify exact arguments passed
        verify(persistence).save(snapshots, candles);
    }

    @Test
    @DisplayName("Should handle simulator returning null snapshots")
    void testSaveSnapshot_NullSnapshots() {
        // Arrange
        when(simulator.latestSnapshots()).thenReturn(Collections.emptyList());
        when(simulator.drainCompletedCandles()).thenReturn(Collections.emptyList());
        doNothing().when(persistence).save(anyList(), anyList());

        // Act & Assert
        assertDoesNotThrow(() -> scheduler.saveSnapshot());
    }

    @Test
    @DisplayName("Should handle simulator returning null candles")
    void testSaveSnapshot_NullCandles() {
        // Arrange
        when(simulator.latestSnapshots()).thenReturn(Collections.emptyList());
        when(simulator.drainCompletedCandles()).thenReturn(Collections.emptyList());
        doNothing().when(persistence).save(anyList(), anyList());

        // Act & Assert
        assertDoesNotThrow(() -> scheduler.saveSnapshot());
    }

    @Test
    @DisplayName("Should continue operating after persistence error")
    void testSaveSnapshot_ContinuesAfterError() {
        // Arrange
        when(simulator.latestSnapshots()).thenReturn(Collections.emptyList());
        when(simulator.drainCompletedCandles()).thenReturn(Collections.emptyList());
        doThrow(new RuntimeException("First error"))
            .doNothing()
            .when(persistence).save(anyList(), anyList());

        // Act - call twice, first one fails
        assertDoesNotThrow(() -> scheduler.saveSnapshot());
        assertDoesNotThrow(() -> scheduler.saveSnapshot());

        // Assert - both calls proceeded
        verify(persistence, org.mockito.Mockito.times(2)).save(anyList(), anyList());
    }

    @Test
    @DisplayName("Should handle multiple snapshots and candles")
    void testSaveSnapshot_Multiple() {
        // Arrange
        List<QuoteSnapshot> snapshots = List.of(createTestSnapshot(), createTestSnapshot());
        List<PriceCandle> candles = List.of(createTestCandle(), createTestCandle());

        when(simulator.latestSnapshots()).thenReturn(snapshots);
        when(simulator.drainCompletedCandles()).thenReturn(candles);
        doNothing().when(persistence).save(anyList(), anyList());

        // Act
        scheduler.saveSnapshot();

        // Assert
        verify(persistence).save(eq(snapshots), eq(candles));
    }

    // ========== Helper Methods ==========

    private QuoteSnapshot createTestSnapshot() {
        return new QuoteSnapshot(
            null,
            100.5, 100.0, 101.0,
            101.0, 102.0, 99.5,
            98.0, 50000000L,
            TEST_INSTANT, null
        );
    }

    private PriceCandle createTestCandle() {
        return new PriceCandle(
            5,
            TEST_INSTANT,
            100.0, 102.0, 99.0, 101.0,
            50000000L
        );
    }
}
