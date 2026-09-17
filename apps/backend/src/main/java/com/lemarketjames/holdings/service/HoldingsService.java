package com.lemarketjames.holdings.service;

import com.lemarketjames.holdings.dto.HoldingDto;
import com.lemarketjames.holdings.dto.HoldingsResponse;
import com.lemarketjames.holdings.entity.HoldingsEntity;
import com.lemarketjames.holdings.exception.InsufficientHoldingsException;
import com.lemarketjames.holdings.repository.HoldingsRepository;
import com.lemarketjames.market.model.QuoteSnapshot;
import com.lemarketjames.market.service.MarketDataService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
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
    private final MarketDataService marketData;

    public HoldingsService(HoldingsRepository holdingsRepository, MarketDataService marketData) {
        this.holdingsRepository = holdingsRepository;
        this.marketData = marketData;
    }

    /**
     * AC1: Retrieve all holdings for an authenticated user.
     *
     * <p>Symbol, current price and current value come from the simulated market. A holding whose
     * instrument is not simulated keeps a null symbol and zero price/value rather than failing
     * the whole response.
     *
     * @param accountId the account ID to retrieve holdings for
     * @return HoldingsResponse containing success flag and list of holdings DTOs
     */
    public HoldingsResponse getHoldingsForAccount(Integer accountId) {
        List<HoldingsEntity> holdings = holdingsRepository.findByAccountId(accountId);

        List<HoldingDto> holdingDtos = holdings.stream()
            .map(this::toDto)
            .collect(Collectors.toList());

        return new HoldingsResponse(true, holdingDtos);
    }

    private HoldingDto toDto(HoldingsEntity holding) {
        Optional<QuoteSnapshot> quote = marketData.findByInstrumentId(holding.getInstrumentId());
        BigDecimal currentPrice = quote
            .map(q -> BigDecimal.valueOf(q.lastPrice()).setScale(2, RoundingMode.HALF_UP))
            .orElse(BigDecimal.ZERO);
        BigDecimal currentValue = holding.getQuantity().multiply(currentPrice).setScale(2, RoundingMode.HALF_UP);

        return new HoldingDto(
            quote.map(q -> q.instrument().ticker()).orElse(null),
            holding.getQuantity(),
            BigDecimal.ZERO,  // averageCost: needs cost basis from fills, not yet tracked in DB
            currentPrice,
            BigDecimal.ZERO,  // totalCost: depends on averageCost
            currentValue,
            BigDecimal.ZERO,  // gainLoss: depends on averageCost
            BigDecimal.ZERO   // gainLossPercent: depends on averageCost
        );
    }

    /**
     * AC2: Validate that user has sufficient holdings to sell a given quantity.
     * Throws InsufficientHoldingsException if validation fails.
     * 
     * @param accountId the account ID
     * @param instrumentId the instrument ID to validate holdings for
     * @param sellQuantity the quantity attempting to be sold
     * @throws InsufficientHoldingsException if holding does not exist or quantity insufficient
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
