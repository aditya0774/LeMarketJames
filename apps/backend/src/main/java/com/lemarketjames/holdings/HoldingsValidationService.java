package com.lemarketjames.holdings;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Service class for validating holdings and preventing overselling.
 * Provides methods to validate that users don't sell more shares than they own.
 */
@Service
public class HoldingsValidationService {

    private final HoldingsRepository holdingsRepository;

    /**
     * Constructs a HoldingsValidationService with the provided repository.
     *
     * @param holdingsRepository the holdings repository for database queries
     */
    public HoldingsValidationService(HoldingsRepository holdingsRepository) {
        this.holdingsRepository = holdingsRepository;
    }

    /**
     * Validates that a sell order does not exceed available holdings.
     * Throws an exception if the user attempts to sell more shares than they own.
     *
     * @param accountId the account ID
     * @param instrumentId the instrument ID
     * @param sellQuantity the quantity to sell
     * @throws IllegalArgumentException if the sell quantity exceeds available holdings
     *         or if no holding exists for the account/instrument combination
     */
    public void validateSellOrder(Long accountId, Long instrumentId, BigDecimal sellQuantity) {
        Holdings holding = getHolding(accountId, instrumentId);

        if (sellQuantity.compareTo(holding.getQuantity()) > 0) {
            throw new IllegalArgumentException(
                String.format(
                    "Cannot sell %s shares of instrument %d; only %s available",
                    sellQuantity,
                    instrumentId,
                    holding.getQuantity()
                )
            );
        }
    }

    /**
     * Retrieves a holding for the given account and instrument.
     *
     * @param accountId the account ID
     * @param instrumentId the instrument ID
     * @return the holdings entity if found
     * @throws IllegalArgumentException if no holding exists for the account/instrument combination
     */
    public Holdings getHolding(Long accountId, Long instrumentId) {
        return holdingsRepository.findByAccountIdAndInstrumentId(accountId, instrumentId)
            .orElseThrow(() -> new IllegalArgumentException(
                String.format(
                    "No holdings found for account %d and instrument %d",
                    accountId,
                    instrumentId
                )
            ));
    }
}
