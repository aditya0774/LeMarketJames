package com.lemarketjames.holdings.service;

import com.lemarketjames.holdings.dto.HoldingsResponse;
import com.lemarketjames.holdings.entity.HoldingsEntity;
import com.lemarketjames.holdings.exception.InsufficientHoldingsException;
import com.lemarketjames.holdings.repository.HoldingsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ExtendWith(MockitoExtension.class)
public class HoldingsServiceTest {

    @Mock
    private HoldingsRepository holdingsRepository;

    private HoldingsService holdingsService;

    @BeforeEach
    public void setUp() {
        holdingsService = new HoldingsService(holdingsRepository);
    }

    /**
     * AC1: Holdings retrieved - verify getHoldingsForAccount returns holdings
     */
    @Test
    public void testGetHoldingsForAccount_Success() {
        Integer accountId = 1;
        HoldingsEntity holding = new HoldingsEntity(accountId, 1, new BigDecimal("10.0000"));
        
        when(holdingsRepository.findByAccountId(accountId))
            .thenReturn(List.of(holding));

        HoldingsResponse response = holdingsService.getHoldingsForAccount(accountId);

        assertTrue(response.isSuccess());
        assertEquals(1, response.getHoldings().size());
        assertEquals(new BigDecimal("10.0000"), response.getHoldings().get(0).getQuantity());
    }

    /**
     * AC1: Holdings retrieved - verify empty list when no holdings
     */
    @Test
    public void testGetHoldingsForAccount_EmptyList() {
        Integer accountId = 1;
        
        when(holdingsRepository.findByAccountId(accountId))
            .thenReturn(List.of());

        HoldingsResponse response = holdingsService.getHoldingsForAccount(accountId);

        assertTrue(response.isSuccess());
        assertEquals(0, response.getHoldings().size());
    }

    /**
     * AC2: Overselling rejected - verify exception when insufficient holdings
     */
    @Test
    public void testValidateSufficientHoldings_InsufficientQuantity() {
        Integer accountId = 1;
        Integer instrumentId = 1;
        HoldingsEntity holding = new HoldingsEntity(accountId, instrumentId, new BigDecimal("5.0000"));
        
        when(holdingsRepository.findByAccountIdAndInstrumentId(accountId, instrumentId))
            .thenReturn(Optional.of(holding));

        assertThrows(InsufficientHoldingsException.class, () -> {
            holdingsService.validateSufficientHoldings(accountId, instrumentId, new BigDecimal("10.0000"));
        });
    }

    /**
     * AC2: Overselling rejected - verify success when sufficient holdings
     */
    @Test
    public void testValidateSufficientHoldings_SufficientQuantity() {
        Integer accountId = 1;
        Integer instrumentId = 1;
        HoldingsEntity holding = new HoldingsEntity(accountId, instrumentId, new BigDecimal("10.0000"));
        
        when(holdingsRepository.findByAccountIdAndInstrumentId(accountId, instrumentId))
            .thenReturn(Optional.of(holding));

        // Should not throw
        assertDoesNotThrow(() -> {
            holdingsService.validateSufficientHoldings(accountId, instrumentId, new BigDecimal("5.0000"));
        });
    }

    /**
     * AC2: Overselling rejected - verify exception when no holdings exist
     */
    @Test
    public void testValidateSufficientHoldings_NoHoldings() {
        Integer accountId = 1;
        Integer instrumentId = 1;
        
        when(holdingsRepository.findByAccountIdAndInstrumentId(accountId, instrumentId))
            .thenReturn(Optional.empty());

        assertThrows(InsufficientHoldingsException.class, () -> {
            holdingsService.validateSufficientHoldings(accountId, instrumentId, new BigDecimal("1.0000"));
        });
    }
}
