package com.lemarketjames.portfolio;

import com.lemarketjames.common.domain.AccountEntity;
import com.lemarketjames.common.domain.AccountRepository;
import com.lemarketjames.holdings.entity.HoldingsEntity;
import com.lemarketjames.holdings.exception.UnauthorizedException;
import com.lemarketjames.holdings.repository.HoldingsRepository;
import com.lemarketjames.market.model.QuoteSnapshot;
import com.lemarketjames.market.service.MarketDataService;
import com.lemarketjames.orders.client.OrderSummary;
import com.lemarketjames.orders.client.OrdersClient;
import com.lemarketjames.portfolio.dto.PortfolioBalance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

/**
 * Implements the balance aggregate documented in API-CONTRACTS.md (previously undocumented as
 * unimplemented). cash/invested/totalValue/gain-loss come from this service's own data plus live
 * quotes; buyingPower needs the caller's open BUY orders, which live in core-service.
 */
@Service
public class PortfolioService {

    private static final Logger log = LoggerFactory.getLogger(PortfolioService.class);

    private final AccountRepository accountRepository;
    private final HoldingsRepository holdingsRepository;
    private final MarketDataService marketData;
    private final OrdersClient ordersClient;

    public PortfolioService(AccountRepository accountRepository, HoldingsRepository holdingsRepository,
                             MarketDataService marketData, OrdersClient ordersClient) {
        this.accountRepository = accountRepository;
        this.holdingsRepository = holdingsRepository;
        this.marketData = marketData;
        this.ordersClient = ordersClient;
    }

    /** GET /api/balance's entry point: no accountId param, resolved from the JWT like /api/auth/me. */
    public PortfolioBalance getOwnPortfolio(String username) {
        Integer accountId = accountRepository.findAccountIdByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("No account found for username: " + username));
        return getPortfolio(accountId, username);
    }

    public PortfolioBalance getPortfolio(Integer accountId, String username) {
        if (!accountRepository.existsByAccountIdAndUsername(accountId, username)) {
            log.warn("Portfolio access denied for username={} accountId={}", username, accountId);
            throw new UnauthorizedException("User is not authorized to access this account");
        }

        AccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found with ID: " + accountId));
        BigDecimal cash = account.getCashBalance();

        List<HoldingsEntity> holdings = holdingsRepository.findByAccountId(accountId);
        BigDecimal invested = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal dayGainLoss = BigDecimal.ZERO;

        for (HoldingsEntity holding : holdings) {
            Optional<QuoteSnapshot> quote = marketData.findByInstrumentId(holding.getInstrumentId());
            BigDecimal currentPrice = quote.map(q -> BigDecimal.valueOf(q.lastPrice())).orElse(BigDecimal.ZERO);
            BigDecimal openPrice = quote.map(q -> BigDecimal.valueOf(q.openPrice())).orElse(currentPrice);

            invested = invested.add(holding.getQuantity().multiply(currentPrice));
            totalCost = totalCost.add(holding.getQuantity().multiply(holding.getAverageCost()));
            dayGainLoss = dayGainLoss.add(holding.getQuantity().multiply(currentPrice.subtract(openPrice)));
        }

        BigDecimal totalValue = cash.add(invested);
        BigDecimal totalGainLoss = invested.subtract(totalCost);
        BigDecimal dayGainLossPercent = percentOf(dayGainLoss, invested.subtract(dayGainLoss));
        BigDecimal totalGainLossPercent = percentOf(totalGainLoss, totalCost);

        BigDecimal openBuyCost = ordersClient.getOrdersForAccount(accountId).stream()
                .filter(OrderSummary::isOpen)
                .filter(OrderSummary::isBuy)
                .map(o -> o.quantity().multiply(o.pricePerUnit() == null ? BigDecimal.ZERO : o.pricePerUnit()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal buyingPower = cash.subtract(openBuyCost);

        return new PortfolioBalance(
                scale(cash), scale(invested), scale(totalValue), scale(buyingPower),
                scale(dayGainLoss), scale(dayGainLossPercent),
                scale(totalGainLoss), scale(totalGainLossPercent),
                account.getCurrency());
    }

    private static BigDecimal percentOf(BigDecimal change, BigDecimal base) {
        return base.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : change.divide(base, 6, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
