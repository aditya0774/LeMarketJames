package com.lemarketjames.holdings.service;

import com.lemarketjames.holdings.dto.HoldingDto;
import com.lemarketjames.holdings.dto.HoldingsResponse;
import com.lemarketjames.holdings.entity.HoldingsEntity;
import com.lemarketjames.holdings.exception.InsufficientHoldingsException;
import com.lemarketjames.holdings.repository.HoldingsRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class HoldingsService {

    private final HoldingsRepository holdingsRepository;

    public HoldingsService(HoldingsRepository holdingsRepository) {
        this.holdingsRepository = holdingsRepository;
    }

    /**
     * AC1: Retrieve all holdings for an authenticated user.
     */
    public HoldingsResponse getHoldingsForAccount(Integer accountId) {
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
     * Throws InsufficientHoldingsException if validation fails.
     */
    public void validateSufficientHoldings(Integer accountId, Integer instrumentId, 
                                          BigDecimal sellQuantity) {
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
