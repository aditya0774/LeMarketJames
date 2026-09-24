package com.lemarketjames.holdings.service;

import com.lemarketjames.common.domain.AccountRepository;
import com.lemarketjames.holdings.dto.HoldingDto;
import com.lemarketjames.holdings.dto.HoldingsResponse;
import com.lemarketjames.holdings.entity.HoldingsEntity;
import com.lemarketjames.holdings.exception.InsufficientHoldingsException;
import com.lemarketjames.holdings.exception.UnauthorizedException;
import com.lemarketjames.holdings.repository.HoldingsRepository;
import com.lemarketjames.market.model.QuoteSnapshot;
import com.lemarketjames.market.service.MarketDataService;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger log = LoggerFactory.getLogger(HoldingsService.class);

    private final HoldingsRepository holdingsRepository;
    private final MarketDataService marketData;
    private final AccountRepository accountRepository;

    public HoldingsService(HoldingsRepository holdingsRepository,
                           MarketDataService marketData,
                           AccountRepository accountRepository) {
        this.holdingsRepository = holdingsRepository;
        this.marketData = marketData;
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
            log.warn("Holdings access denied for username={} accountId={}", username, accountId);
            throw new UnauthorizedException(
                "User is not authorized to access this account");
        }
    }

    /**
     * AC1: Retrieve all holdings for an authenticated user.
     *
     * <p>Symbol, current price and current value come from the simulated market. A holding whose
     * instrument is not simulated keeps a null symbol and zero price/value rather than failing
     * the whole response.
     *
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

        BigDecimal averageCost = holding.getAverageCost().setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalCost = holding.getQuantity().multiply(holding.getAverageCost()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal gainLoss = currentValue.subtract(totalCost).setScale(2, RoundingMode.HALF_UP);
        BigDecimal gainLossPercent = totalCost.compareTo(BigDecimal.ZERO) == 0
            ? BigDecimal.ZERO
            : gainLoss.divide(totalCost, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);

        return new HoldingDto(
            quote.map(q -> q.instrument().ticker()).orElse(null),
            holding.getQuantity(),
            averageCost,
            currentPrice,
            totalCost,
            currentValue,
            gainLoss,
            gainLossPercent
        );
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
