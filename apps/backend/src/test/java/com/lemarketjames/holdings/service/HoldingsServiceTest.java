package com.lemarketjames.holdings.service;

import com.lemarketjames.auth.domain.AccountRepository;
import com.lemarketjames.holdings.dto.HoldingsResponse;
import com.lemarketjames.holdings.entity.HoldingsEntity;
import com.lemarketjames.holdings.exception.InsufficientHoldingsException;
import com.lemarketjames.holdings.exception.UnauthorizedException;
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

    @Mock
    private AccountRepository accountRepository;

    private HoldingsService holdingsService;

    @BeforeEach
    public void setUp() {
        holdingsService = new HoldingsService(holdingsRepository, accountRepository);
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
