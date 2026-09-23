package com.lemarketjames.holdings.service;

import com.lemarketjames.common.domain.AccountRepository;
import com.lemarketjames.holdings.dto.HoldingDto;
import com.lemarketjames.holdings.dto.HoldingsResponse;
import com.lemarketjames.holdings.entity.HoldingsEntity;
import com.lemarketjames.holdings.exception.InsufficientHoldingsException;
import com.lemarketjames.holdings.exception.UnauthorizedException;
import com.lemarketjames.holdings.repository.HoldingsRepository;
import com.lemarketjames.market.model.MarketInstrument;
import com.lemarketjames.market.model.QuoteSnapshot;
import com.lemarketjames.market.service.MarketDataService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// Pure Mockito unit test: no Spring context needed since HoldingsService is constructed directly.
@ExtendWith(MockitoExtension.class)
public class HoldingsServiceTest {

    @Mock
    private HoldingsRepository holdingsRepository;

    @Mock
    private MarketDataService marketData;

    @Mock
    private AccountRepository accountRepository;

    private HoldingsService holdingsService;

    @BeforeEach
    public void setUp() {
        holdingsService = new HoldingsService(holdingsRepository, marketData, accountRepository);
    }

    /**
     * AC1: Holdings are valued at the current simulated market price.
     */
    @Test
    public void testGetHoldingsForAccount_ValuedAtMarketPrice() {
        Integer accountId = 1;
        String username = "alice";
        HoldingsEntity holding = new HoldingsEntity(accountId, 1, new BigDecimal("10.5000"));
        MarketInstrument aapl = new MarketInstrument(1, "AAPL", "Apple Inc", "EQUITY", "USD", "US",
            227.55, 0.08, 0.25, 0.65, 1.5, 15_200_000_000L, 55_000_000L, 6.75, 1.00);
        QuoteSnapshot quote = new QuoteSnapshot(aapl, 230.0, 229.98, 230.02, 227.55, 231.0, 227.0,
            227.55, 1_000L, Instant.parse("2026-09-16T15:00:00Z"), LocalDate.of(2026, 9, 16));

        when(accountRepository.existsByAccountIdAndUsername(accountId, username)).thenReturn(true);
        when(holdingsRepository.findByAccountId(accountId)).thenReturn(List.of(holding));
        when(marketData.findByInstrumentId(1)).thenReturn(Optional.of(quote));

        HoldingDto dto = holdingsService.getHoldingsForAccount(accountId, username).getHoldings().get(0);

        assertEquals("AAPL", dto.getSymbol());
        assertEquals(new BigDecimal("230.00"), dto.getCurrentPrice());
        assertEquals(new BigDecimal("2415.00"), dto.getCurrentValue());
    }

    /**
     * AC1: A holding in an instrument with no market price still appears, with zero value.
     */
    @Test
    public void testGetHoldingsForAccount_NoMarketPrice() {
        Integer accountId = 1;
        String username = "alice";
        HoldingsEntity holding = new HoldingsEntity(accountId, 99, new BigDecimal("3.0000"));

        when(accountRepository.existsByAccountIdAndUsername(accountId, username)).thenReturn(true);
        when(holdingsRepository.findByAccountId(accountId)).thenReturn(List.of(holding));
        when(marketData.findByInstrumentId(99)).thenReturn(Optional.empty());

        HoldingDto dto = holdingsService.getHoldingsForAccount(accountId, username).getHoldings().get(0);

        assertNull(dto.getSymbol());
        assertEquals(0, BigDecimal.ZERO.compareTo(dto.getCurrentPrice()));
        assertEquals(0, BigDecimal.ZERO.compareTo(dto.getCurrentValue()));
    }

    /**
     * AC1: Holdings retrieved - verify getHoldingsForAccount returns holdings
     * AC2: Requests scoped to authenticated user - user owns account
     */
    @Test
    public void testGetHoldingsForAccount_Success() {
        Integer accountId = 1;
        String username = "alice";
        HoldingsEntity holding = new HoldingsEntity(accountId, 1, new BigDecimal("10.0000"));
        
        // Mock: alice owns account 1
        when(accountRepository.existsByAccountIdAndUsername(accountId, username))
            .thenReturn(true);
        when(holdingsRepository.findByAccountId(accountId))
            .thenReturn(List.of(holding));

        HoldingsResponse response = holdingsService.getHoldingsForAccount(accountId, username);

        assertTrue(response.isSuccess());
        assertEquals(1, response.getHoldings().size());
        assertEquals(new BigDecimal("10.0000"), response.getHoldings().get(0).getQuantity());
        
        // Verify ownership was checked
        verify(accountRepository).existsByAccountIdAndUsername(accountId, username);
    }

    /**
     * AC1: Holdings retrieved - verify empty list when no holdings
     * AC2: Requests scoped to authenticated user - user owns account but has no holdings
     */
    @Test
    public void testGetHoldingsForAccount_EmptyList() {
        Integer accountId = 1;
        String username = "alice";
        
        // Mock: alice owns account 1
        when(accountRepository.existsByAccountIdAndUsername(accountId, username))
            .thenReturn(true);
        when(holdingsRepository.findByAccountId(accountId))
            .thenReturn(List.of());

        HoldingsResponse response = holdingsService.getHoldingsForAccount(accountId, username);

        assertTrue(response.isSuccess());
        assertEquals(0, response.getHoldings().size());
        
        // Verify ownership was checked
        verify(accountRepository).existsByAccountIdAndUsername(accountId, username);
    }

    /**
     * AC1: Unauthorized data access prevented
     * Verify that user cannot access account they don't own
     */
    @Test
    public void testGetHoldingsForAccount_UnauthorizedAccess() {
        Integer accountId = 2;
        String username = "alice";
        
        // Mock: alice does NOT own account 2
        when(accountRepository.existsByAccountIdAndUsername(accountId, username))
            .thenReturn(false);

        // Expect UnauthorizedException to be thrown
        assertThrows(UnauthorizedException.class, () -> {
            holdingsService.getHoldingsForAccount(accountId, username);
        });
        
        // Verify ownership was checked
        verify(accountRepository).existsByAccountIdAndUsername(accountId, username);
        
        // Verify repository was NOT queried (ownership check failed first)
        verify(holdingsRepository, never()).findByAccountId(accountId);
    }

    /**
     * AC2: Overselling rejected - verify exception when insufficient holdings
     * AC1: Unauthorized data access prevented - user owns account
     */
    @Test
    public void testValidateSufficientHoldings_InsufficientQuantity() {
        Integer accountId = 1;
        String username = "alice";
        Integer instrumentId = 1;
        HoldingsEntity holding = new HoldingsEntity(accountId, instrumentId, new BigDecimal("5.0000"));
        
        // Mock: alice owns account 1
        when(accountRepository.existsByAccountIdAndUsername(accountId, username))
            .thenReturn(true);
        when(holdingsRepository.findByAccountIdAndInstrumentId(accountId, instrumentId))
            .thenReturn(Optional.of(holding));

        assertThrows(InsufficientHoldingsException.class, () -> {
            holdingsService.validateSufficientHoldings(accountId, username, instrumentId, new BigDecimal("10.0000"));
        });
        
        // Verify ownership was checked
        verify(accountRepository).existsByAccountIdAndUsername(accountId, username);
    }

    /**
     * AC2: Overselling rejected - verify success when sufficient holdings
     * AC1: Unauthorized data access prevented - user owns account
     */
    @Test
    public void testValidateSufficientHoldings_SufficientQuantity() {
        Integer accountId = 1;
        String username = "alice";
        Integer instrumentId = 1;
        HoldingsEntity holding = new HoldingsEntity(accountId, instrumentId, new BigDecimal("10.0000"));
        
        // Mock: alice owns account 1
        when(accountRepository.existsByAccountIdAndUsername(accountId, username))
            .thenReturn(true);
        when(holdingsRepository.findByAccountIdAndInstrumentId(accountId, instrumentId))
            .thenReturn(Optional.of(holding));

        // Should not throw
        assertDoesNotThrow(() -> {
            holdingsService.validateSufficientHoldings(accountId, username, instrumentId, new BigDecimal("5.0000"));
        });
        
        // Verify ownership was checked
        verify(accountRepository).existsByAccountIdAndUsername(accountId, username);
    }

    /**
     * AC2: Overselling rejected - verify exception when no holdings exist
     * AC1: Unauthorized data access prevented - user owns account
     */
    @Test
    public void testValidateSufficientHoldings_NoHoldings() {
        Integer accountId = 1;
        String username = "alice";
        Integer instrumentId = 1;
        
        // Mock: alice owns account 1
        when(accountRepository.existsByAccountIdAndUsername(accountId, username))
            .thenReturn(true);
        when(holdingsRepository.findByAccountIdAndInstrumentId(accountId, instrumentId))
            .thenReturn(Optional.empty());

        assertThrows(InsufficientHoldingsException.class, () -> {
            holdingsService.validateSufficientHoldings(accountId, username, instrumentId, new BigDecimal("1.0000"));
        });
        
        // Verify ownership was checked
        verify(accountRepository).existsByAccountIdAndUsername(accountId, username);
    }

    /**
     * AC1: Unauthorized data access prevented
     * Verify that user cannot validate holdings for account they don't own
     */
    @Test
    public void testValidateSufficientHoldings_UnauthorizedAccess() {
        Integer accountId = 2;
        String username = "alice";
        Integer instrumentId = 1;
        
        // Mock: alice does NOT own account 2
        when(accountRepository.existsByAccountIdAndUsername(accountId, username))
            .thenReturn(false);

        // Expect UnauthorizedException to be thrown (even if holdings exist)
        assertThrows(UnauthorizedException.class, () -> {
            holdingsService.validateSufficientHoldings(accountId, username, instrumentId, new BigDecimal("1.0000"));
        });
        
        // Verify ownership was checked
        verify(accountRepository).existsByAccountIdAndUsername(accountId, username);
        
        // Verify repository was NOT queried (ownership check failed first)
        verify(holdingsRepository, never()).findByAccountIdAndInstrumentId(accountId, instrumentId);
    }
}
