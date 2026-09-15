package com.lemarketjames.holdings.service;

import com.lemarketjames.holdings.dto.HoldingDto;
import com.lemarketjames.holdings.dto.HoldingsResponse;
import com.lemarketjames.holdings.entity.HoldingsEntity;
import com.lemarketjames.holdings.exception.InsufficientHoldingsException;
import com.lemarketjames.holdings.repository.HoldingsRepository;
import com.lemarketjames.common.security.UserIdExtractorUtil;
import com.lemarketjames.common.security.AccountOwnershipValidator;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class HoldingsService {

    private final HoldingsRepository holdingsRepository;
    private final AccountOwnershipValidator accountOwnershipValidator;

    public HoldingsService(HoldingsRepository holdingsRepository, AccountOwnershipValidator accountOwnershipValidator) {
        this.holdingsRepository = holdingsRepository;
        this.accountOwnershipValidator = accountOwnershipValidator;
    }

    /**
     * AC1: Retrieve all holdings for an authenticated user.
     * 
     * SECURITY: Validates that the authenticated user owns the requested account.
     * If they don't own it, throws UnauthorizedAccessException (caught by GlobalExceptionHandler → 403 Forbidden)
     */
    public HoldingsResponse getHoldingsForAccount(Integer accountId) {
        // Step 1: Extract the authenticated username from the JWT token
        String authenticatedUsername = UserIdExtractorUtil.extractAuthenticatedUsername();
        
        // Step 2: Validate that this user owns the requested account
        // If they don't own it, this throws UnauthorizedAccessException
        accountOwnershipValidator.validateAccountOwnership(authenticatedUsername, accountId);
        
        // Step 3: If validation passed, fetch and return their holdings
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
     * 
     * SECURITY: Validates that the authenticated user owns the requested account.
     * Throws InsufficientHoldingsException if validation fails.
     */
    public void validateSufficientHoldings(Integer accountId, Integer instrumentId, 
                                          BigDecimal sellQuantity) {
        // Step 1: Extract the authenticated username from the JWT token
        String authenticatedUsername = UserIdExtractorUtil.extractAuthenticatedUsername();
        
        // Step 2: Validate that this user owns the requested account
        accountOwnershipValidator.validateAccountOwnership(authenticatedUsername, accountId);
        
        // Step 3: If validation passed, check holdings
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
