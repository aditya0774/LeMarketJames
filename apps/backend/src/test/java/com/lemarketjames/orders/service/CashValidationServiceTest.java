package com.lemarketjames.orders.service;

import com.lemarketjames.auth.domain.AccountEntity;
import com.lemarketjames.auth.domain.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CashValidationService
 * Tests cash validation logic using mocked AccountRepository
 */
@ExtendWith(MockitoExtension.class)
class CashValidationServiceTest {
    
    @Mock
    private AccountRepository accountRepository;
    
    private CashValidationService cashValidationService;
    
    @BeforeEach
    void setUp() {
        cashValidationService = new CashValidationService(accountRepository);
    }
    
    /**
     * Test: Sufficient cash balance
     * Account has $5000.00, order cost is $2500.00
     * Expected: returns true
     */
    @Test
    void testValidateSufficientCash_HasSufficientBalance() {
        // Arrange
        Integer accountId = 1;
        BigDecimal orderCost = new BigDecimal("2500.00");
        
        AccountEntity account = createTestAccount(1, new BigDecimal("5000.00"));
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        
        // Act
        boolean result = cashValidationService.validateSufficientCash(accountId, orderCost);
        
        // Assert
        assertTrue(result, "Should return true when account has sufficient balance");
        verify(accountRepository).findById(accountId);
    }
    
    /**
     * Test: Insufficient cash balance
     * Account has $1000.00, order cost is $2500.00
     * Expected: returns false
     */
    @Test
    void testValidateSufficientCash_InsufficientBalance() {
        // Arrange
        Integer accountId = 2;
        BigDecimal orderCost = new BigDecimal("2500.00");
        
        AccountEntity account = createTestAccount(2, new BigDecimal("1000.00"));
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        
        // Act
        boolean result = cashValidationService.validateSufficientCash(accountId, orderCost);
        
        // Assert
        assertFalse(result, "Should return false when account has insufficient balance");
        verify(accountRepository).findById(accountId);
    }
    
    /**
     * Test: Cash balance exactly equals order cost (edge case)
     * Account has $2500.00, order cost is $2500.00
     * Expected: returns true (enough cash)
     */
    @Test
    void testValidateSufficientCash_ExactBalance() {
        // Arrange
        Integer accountId = 3;
        BigDecimal orderCost = new BigDecimal("2500.00");
        
        AccountEntity account = createTestAccount(3, new BigDecimal("2500.00"));
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        
        // Act
        boolean result = cashValidationService.validateSufficientCash(accountId, orderCost);
        
        // Assert
        assertTrue(result, "Should return true when cash balance exactly equals order cost");
        verify(accountRepository).findById(accountId);
    }
    
    /**
     * Test: Zero or negative order cost
     * Order cost is $0 or negative
     * Expected: returns true (no validation needed)
     */
    @Test
    void testValidateSufficientCash_ZeroCost() {
        // Arrange
        Integer accountId = 4;
        BigDecimal orderCost = BigDecimal.ZERO;
        
        // Act
        boolean result = cashValidationService.validateSufficientCash(accountId, orderCost);
        
        // Assert
        assertTrue(result, "Should return true for zero order cost");
        // Repository should not be called for zero/negative cost
        verify(accountRepository, never()).findById(any());
    }
    
    /**
     * Test: Null order cost
     * Order cost is null
     * Expected: returns true (no validation needed)
     */
    @Test
    void testValidateSufficientCash_NullCost() {
        // Arrange
        Integer accountId = 5;
        BigDecimal orderCost = null;
        
        // Act
        boolean result = cashValidationService.validateSufficientCash(accountId, orderCost);
        
        // Assert
        assertTrue(result, "Should return true for null order cost");
        verify(accountRepository, never()).findById(any());
    }
    
    /**
     * Test: Account not found
     * AccountId does not exist in repository
     * Expected: throws IllegalArgumentException
     */
    @Test
    void testValidateSufficientCash_AccountNotFound() {
        // Arrange
        Integer accountId = 999;
        BigDecimal orderCost = new BigDecimal("1000.00");
        
        when(accountRepository.findById(accountId)).thenReturn(Optional.empty());
        
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
            () -> cashValidationService.validateSufficientCash(accountId, orderCost));
        
        assertTrue(exception.getMessage().contains("Account not found"));
    }
    
    /**
     * Test: Get cash balance for existing account
     * Expected: returns the correct cash balance
     */
    @Test
    void testGetCashBalance_Success() {
        // Arrange
        Integer accountId = 1;
        BigDecimal expectedBalance = new BigDecimal("5000.00");
        
        AccountEntity account = createTestAccount(1, expectedBalance);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        
        // Act
        BigDecimal result = cashValidationService.getCashBalance(accountId);
        
        // Assert
        assertEquals(expectedBalance, result, "Should return correct cash balance");
        verify(accountRepository).findById(accountId);
    }
    
    /**
     * Test: Get cash balance for non-existent account
     * Expected: throws IllegalArgumentException
     */
    @Test
    void testGetCashBalance_AccountNotFound() {
        // Arrange
        Integer accountId = 999;
        when(accountRepository.findById(accountId)).thenReturn(Optional.empty());
        
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
            () -> cashValidationService.getCashBalance(accountId));
        
        assertTrue(exception.getMessage().contains("Account not found"));
    }
    
    /**
     * Test: Precision with decimal values
     * Account has $1000.50, order cost is $500.25
     * Expected: returns true, BigDecimal precision preserved
     */
    @Test
    void testValidateSufficientCash_DecimalPrecision() {
        // Arrange
        Integer accountId = 6;
        BigDecimal orderCost = new BigDecimal("500.25");
        
        AccountEntity account = createTestAccount(6, new BigDecimal("1000.50"));
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        
        // Act
        boolean result = cashValidationService.validateSufficientCash(accountId, orderCost);
        
        // Assert
        assertTrue(result, "Should handle decimal precision correctly");
    }
    
    /**
     * Helper method to create test account entities
     */
    private AccountEntity createTestAccount(Integer accountId, BigDecimal cashBalance) {
        AccountEntity account = new AccountEntity();
        // Use reflection to set the accountId since it's @Id
        try {
            var field = AccountEntity.class.getDeclaredField("accountId");
            field.setAccessible(true);
            field.set(account, accountId);
        } catch (Exception e) {
            fail("Failed to set accountId via reflection: " + e.getMessage());
        }
        account.setCashBalance(cashBalance);
        account.setCurrency("USD");
        account.setClientId(accountId); // Use same value for simplicity in tests
        account.setTradingEnabled(true);
        account.setOpenedDate(LocalDate.now());
        return account;
    }
}
