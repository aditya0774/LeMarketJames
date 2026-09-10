package com.lemarketjames.holdings;

import com.lemarketjames.holdings.HoldingsRepository;
import com.lemarketjames.holdings.HoldingsValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * Unit tests for HoldingsValidationService.
 * Tests validation logic for sell orders to prevent overselling.
 */
@ExtendWith(MockitoExtension.class)
class HoldingsValidationServiceTest {

    @Mock
    private HoldingsRepository holdingsRepository;

    private HoldingsValidationService holdingsValidationService;

    @BeforeEach
    void setUp() {
        holdingsValidationService = new HoldingsValidationService(holdingsRepository);
    }

    /**
     * Tests that a valid sell order (quantity <= holding quantity) succeeds.
     */
    @Test
    void validateSellOrderAcceptsValidSell() {
        // Arrange
        Long accountId = 1L;
        Long instrumentId = 1L;
        BigDecimal holdingQuantity = new BigDecimal("100");
        BigDecimal sellQuantity = new BigDecimal("50");

        Holdings holding = createTestHolding(accountId, instrumentId, holdingQuantity);
        when(holdingsRepository.findByAccountIdAndInstrumentId(accountId, instrumentId))
            .thenReturn(Optional.of(holding));

        // Act & Assert: no exception should be thrown
        assertDoesNotThrow(
            () -> holdingsValidationService.validateSellOrder(accountId, instrumentId, sellQuantity)
        );
    }

    /**
     * Tests that a sell order exceeding holdings (oversell) is rejected.
     */
    @Test
    void validateSellOrderRejectsOversell() {
        // Arrange
        Long accountId = 1L;
        Long instrumentId = 1L;
        BigDecimal holdingQuantity = new BigDecimal("100");
        BigDecimal sellQuantity = new BigDecimal("150");

        Holdings holding = createTestHolding(accountId, instrumentId, holdingQuantity);
        when(holdingsRepository.findByAccountIdAndInstrumentId(accountId, instrumentId))
            .thenReturn(Optional.of(holding));

        // Act & Assert: IllegalArgumentException should be thrown
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> holdingsValidationService.validateSellOrder(accountId, instrumentId, sellQuantity),
            "Expected IllegalArgumentException for oversell attempt"
        );

        // Verify error message contains relevant information
        assert exception.getMessage().contains("Cannot sell");
        assert exception.getMessage().contains("150");
        assert exception.getMessage().contains("100");
    }

    /**
     * Tests that attempting to sell a non-existent holding is rejected.
     */
    @Test
    void validateSellOrderRejectsNonexistentHolding() {
        // Arrange
        Long accountId = 1L;
        Long instrumentId = 1L;
        BigDecimal sellQuantity = new BigDecimal("50");

        when(holdingsRepository.findByAccountIdAndInstrumentId(accountId, instrumentId))
            .thenReturn(Optional.empty());

        // Act & Assert: IllegalArgumentException should be thrown
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> holdingsValidationService.validateSellOrder(accountId, instrumentId, sellQuantity),
            "Expected IllegalArgumentException for non-existent holding"
        );

        // Verify error message indicates holding not found
        assert exception.getMessage().contains("No holdings found");
    }

    /**
     * Helper factory method to create test holdings.
     *
     * @param accountId the account ID
     * @param instrumentId the instrument ID
     * @param quantity the quantity held
     * @return a Holdings entity with the provided values
     */
    private Holdings createTestHolding(Long accountId, Long instrumentId, BigDecimal quantity) {
        Holdings holding = new Holdings();
        holding.setHoldingId(1L);
        holding.setAccountId(accountId);
        holding.setInstrumentId(instrumentId);
        holding.setQuantity(quantity);
        return holding;
    }
}
