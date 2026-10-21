package com.lemarketjames.orders.service;

import com.lemarketjames.common.domain.AccountEntity;
import com.lemarketjames.common.domain.AccountRepository;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;

/**
 * CashValidationService
 *
 * Validates that an account has enough available cash for a BUY order: the cash balance less what
 * its open BUY orders already reserve (BR-05, {@link OpenOrderReservations}).
 * Uses authoritative data from the database (AccountEntity.cashBalance).
 * All monetary calculations use BigDecimal for precision.
 */
@Service
public class CashValidationService {

    private final AccountRepository accountRepository;
    private final OpenOrderReservations reservations;

    public CashValidationService(AccountRepository accountRepository, OpenOrderReservations reservations) {
        this.accountRepository = accountRepository;
        this.reservations = reservations;
    }

    /**
     * Validates that an account has enough available cash for an order.
     *
     * @param accountId the account to validate
     * @param orderCost the total cost of the order (quantity * pricePerUnit)
     * @return true if availableCash >= orderCost; false otherwise
     * @throws IllegalArgumentException if accountId does not exist
     */
    public boolean validateSufficientCash(Integer accountId, BigDecimal orderCost) {
        if (orderCost == null || orderCost.compareTo(BigDecimal.ZERO) <= 0) {
            // Cost is zero or negative; no validation needed
            return true;
        }
        // Compare using BigDecimal to avoid floating-point precision errors
        return availableCash(accountId).compareTo(orderCost) >= 0;
    }

    /**
     * The cash a new BUY may spend: the balance less the open BUY orders' reservations. The same
     * figure the client sees as buying power.
     *
     * @param accountId the account to query
     * @return the available cash; negative if open orders already promise more than the balance
     * @throws IllegalArgumentException if accountId does not exist
     */
    public BigDecimal availableCash(Integer accountId) {
        AccountEntity account = accountRepository.findById(accountId)
            .orElseThrow(() -> new IllegalArgumentException("Account not found with ID: " + accountId));
        return account.getCashBalance().subtract(reservations.reservedCash(accountId));
    }
}
