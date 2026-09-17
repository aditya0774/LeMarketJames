package com.lemarketjames.orders.service;

import com.lemarketjames.auth.domain.AccountEntity;
import com.lemarketjames.auth.domain.AccountRepository;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;

/**
 * CashValidationService
 *
 * Validates that an account has sufficient cash balance to execute an order.
 * Uses authoritative data from the database (AccountEntity.cashBalance).
 * All monetary calculations use BigDecimal for precision.
 */
@Service
public class CashValidationService {

    private final AccountRepository accountRepository;

    public CashValidationService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    /**
     * Validates that an account has sufficient cash balance for an order.
     *
     * @param accountId the account to validate
     * @param orderCost the total cost of the order (quantity * pricePerUnit)
     * @return true if cashBalance >= orderCost; false otherwise
     * @throws IllegalArgumentException if accountId does not exist
     */
    public boolean validateSufficientCash(Integer accountId, BigDecimal orderCost) {
        if (orderCost == null || orderCost.compareTo(BigDecimal.ZERO) <= 0) {
            // Cost is zero or negative; no validation needed
            return true;
        }

        AccountEntity account = accountRepository.findById(accountId)
            .orElseThrow(() -> new IllegalArgumentException("Account not found with ID: " + accountId));

        BigDecimal cashBalance = account.getCashBalance();
        // Compare using BigDecimal to avoid floating-point precision errors
        return cashBalance.compareTo(orderCost) >= 0;
    }

    /**
     * Retrieves the current cash balance for an account.
     *
     * @param accountId the account to query
     * @return the BigDecimal cash balance
     * @throws IllegalArgumentException if accountId does not exist
     */
    public BigDecimal getCashBalance(Integer accountId) {
        AccountEntity account = accountRepository.findById(accountId)
            .orElseThrow(() -> new IllegalArgumentException("Account not found with ID: " + accountId));
        return account.getCashBalance();
    }
}
