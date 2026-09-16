package com.lemarketjames.holdings.service;

import com.lemarketjames.auth.domain.AccountRepository;
import com.lemarketjames.holdings.dto.HoldingDto;
import com.lemarketjames.holdings.dto.HoldingsResponse;
import com.lemarketjames.holdings.entity.HoldingsEntity;
import com.lemarketjames.holdings.exception.InsufficientHoldingsException;
import com.lemarketjames.holdings.exception.UnauthorizedException;
import com.lemarketjames.holdings.repository.HoldingsRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service layer for holdings management.
 * Manages user stock holdings and validates sell orders to prevent overselling.
 * 
 * AC1: Retrieve all holdings for an authenticated user
 * AC2: Validate user has sufficient holdings to sell a given quantity
 */
@Service
public class HoldingsService {

    private final HoldingsRepository holdingsRepository;
    private final AccountRepository accountRepository;

    public HoldingsService(HoldingsRepository holdingsRepository,
                           AccountRepository accountRepository) {
        this.holdingsRepository = holdingsRepository;
        this.accountRepository = accountRepository;
    }

    /**
     * AC1: Unauthorized data access prevented
     * 
     * Validates that the authenticated user owns the given account.
     * Throws UnauthorizedException if they don't own it.
     * 
     * This is called before any data operation to prevent users from viewing
     * or modifying accounts that don't belong to them.
     * 
     * @param accountId the account ID to validate ownership of
     * @param username the authenticated user's username from JWT
     * @throws UnauthorizedException if user doesn't own the account
     */
    private void validateAccountOwnership(Integer accountId, String username) {
        boolean ownsAccount = accountRepository.existsByAccountIdAndUsername(accountId, username);
        
        if (!ownsAccount) {
            throw new UnauthorizedException(
                "User is not authorized to access this account");
        }
    }

    /**
     * AC1: Retrieve all holdings for an authenticated user.
     * AC2: Requests scoped to authenticated user
     * 
     * @param accountId the account ID to retrieve holdings for
     * @param username the authenticated user's username from JWT
     * @return HoldingsResponse containing success flag and list of holdings DTOs
     * @throws UnauthorizedException if user doesn't own the account
     */
    public HoldingsResponse getHoldingsForAccount(Integer accountId, String username) {
        // AC1: Validate user owns this account before returning any data
        validateAccountOwnership(accountId, username);
        
        List<HoldingsEntity> holdings = holdingsRepository.findByAccountId(accountId);
        
        List<HoldingDto> holdingDtos = holdings.stream()
            .map(h -> new HoldingDto(
                null,  // symbol will be enriched by controller
                h.getQuantity(),
                BigDecimal.ZERO,  // averageCost not yet tracked in DB
                BigDecimal.ZERO,  // currentPrice fetched from quotes
                BigDecimal.ZERO,  // totalCost
                BigDecimal.ZERO,  // currentValue
                BigDecimal.ZERO,  // gainLoss
                BigDecimal.ZERO   // gainLossPercent
            ))
            .collect(Collectors.toList());
        
        return new HoldingsResponse(true, holdingDtos);
    }

    /**
     * AC2: Validate that user has sufficient holdings to sell a given quantity.
     * AC1: Unauthorized data access prevented - validates ownership first
     * 
     * Throws UnauthorizedException if user doesn't own the account.
     * Throws InsufficientHoldingsException if validation fails.
     * 
     * @param accountId the account ID
     * @param username the authenticated user's username from JWT
     * @param instrumentId the instrument ID to validate holdings for
     * @param sellQuantity the quantity attempting to be sold
     * @throws UnauthorizedException if user doesn't own the account
     * @throws InsufficientHoldingsException if holding does not exist or quantity insufficient
     */
    public void validateSufficientHoldings(Integer accountId, String username,
                                          Integer instrumentId, BigDecimal sellQuantity) {
        // AC1: Validate user owns this account before proceeding
        validateAccountOwnership(accountId, username);
        
        HoldingsEntity holding = holdingsRepository
            .findByAccountIdAndInstrumentId(accountId, instrumentId)
            .orElseThrow(() -> new InsufficientHoldingsException(
                "No holdings found for this instrument"));

        if (!holding.canSell(sellQuantity)) {
            throw new InsufficientHoldingsException(
                String.format("Insufficient holdings. Available: %s, Requested: %s",
                    holding.getQuantity(), sellQuantity));
        }
    }
}
