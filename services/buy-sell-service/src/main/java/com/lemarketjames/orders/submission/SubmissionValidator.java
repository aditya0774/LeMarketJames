package com.lemarketjames.orders.submission;

import com.lemarketjames.common.domain.AccountEntity;
import com.lemarketjames.common.domain.AccountRepository;
import com.lemarketjames.common.domain.ClientEntity;
import com.lemarketjames.common.domain.ClientRepository;
import com.lemarketjames.common.instruments.Instrument;
import com.lemarketjames.common.instruments.InstrumentRepository;
import com.lemarketjames.holdings.client.HoldingsValidationClient;
import com.lemarketjames.market.service.MarketDataService;
import com.lemarketjames.orders.dto.CreateOrderRequest;
import com.lemarketjames.orders.entity.Order;
import com.lemarketjames.orders.entity.RejectionReason;
import com.lemarketjames.orders.exception.NotTradableException;
import com.lemarketjames.orders.service.AccountAccess;
import com.lemarketjames.orders.service.CashValidationService;
import com.lemarketjames.orders.service.TradingRestrictions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The checks an order must pass before it is saved, in the order they run: ownership, account
 * status, location, tradability, then holdings for a SELL or price and cash for a BUY. Kept apart
 * from {@code OrderService} so deciding whether an order may be placed is separate from saving it.
 */
@Service
public class SubmissionValidator {

    private static final Logger log = LoggerFactory.getLogger(SubmissionValidator.class);

    private final AccountAccess accountAccess;
    private final AccountRepository accountRepository;
    private final ClientRepository clientRepository;
    private final InstrumentRepository instrumentRepository;
    private final TradingRestrictions restrictions;
    private final HoldingsValidationClient holdingsValidationClient;
    private final MarketDataService marketDataService;
    private final CashValidationService cashValidationService;

    public SubmissionValidator(AccountAccess accountAccess,
                               AccountRepository accountRepository,
                               ClientRepository clientRepository,
                               InstrumentRepository instrumentRepository,
                               TradingRestrictions restrictions,
                               HoldingsValidationClient holdingsValidationClient,
                               MarketDataService marketDataService,
                               CashValidationService cashValidationService) {
        this.accountAccess = accountAccess;
        this.accountRepository = accountRepository;
        this.clientRepository = clientRepository;
        this.instrumentRepository = instrumentRepository;
        this.restrictions = restrictions;
        this.holdingsValidationClient = holdingsValidationClient;
        this.marketDataService = marketDataService;
        this.cashValidationService = cashValidationService;
    }

    /**
     * Runs the checks for one order. SELL orders require sufficient holdings; BUY orders are
     * priced from the market, never from the browser, and require sufficient cash at that price.
     *
     * @throws org.springframework.security.access.AccessDeniedException if the account isn't the caller's
     * @throws NotTradableException if the instrument is suspended
     * @throws com.lemarketjames.orders.exception.InsufficientHoldingsException if a SELL exceeds the holding
     */
    public ValidationOutcome validate(CreateOrderRequest request) {
        accountAccess.requireOwnAccount(request.getAccountId());
        if (!accountMayTrade(request.getAccountId())) {
            log.warn("Order refused for restricted accountId={}", request.getAccountId());
            return ValidationOutcome.refused("This account can't place orders right now. Please contact support.",
                RejectionReason.ACCOUNT_RESTRICTED);
        }
        if (restrictions.locationRestricted(request.getAccountId())) {
            return ValidationOutcome.refused("Trading is restricted in your location", RejectionReason.LOCATION_RESTRICTED);
        }
        validateInstrumentTradability(request.getInstrumentId());
        // Browser validation is advisory; every SELL must be checked again before saving.
        if (request.getOrderType() == Order.OrderType.SELL) {
            holdingsValidationClient.validateSufficientHoldings(request.getAccountId(),
                accountAccess.authenticatedUsername(), request.getInstrumentId(), request.getQuantity());
        }
        if (request.getOrderType() == Order.OrderType.BUY) {
            return priceAndCheckCash(request);
        }
        return ValidationOutcome.passed(null);
    }

    private ValidationOutcome priceAndCheckCash(CreateOrderRequest request) {
        var quote = marketDataService.findByInstrumentId(request.getInstrumentId());
        if (quote.isEmpty() || !Double.isFinite(quote.get().askPrice()) || quote.get().askPrice() <= 0) {
            return priceUnavailable();
        }
        if (restrictions.stale(quote.get())) {
            return ValidationOutcome.refused("Market quote is stale", RejectionReason.STALE_QUOTE);
        }
        // Match the database scale so validation and the persisted snapshot agree.
        BigDecimal price = BigDecimal.valueOf(quote.get().askPrice()).setScale(4, RoundingMode.HALF_UP);
        if (price.signum() <= 0) {
            return priceUnavailable();
        }
        BigDecimal orderCost = request.getQuantity().multiply(price);
        if (!cashValidationService.validateSufficientCash(request.getAccountId(), orderCost)) {
            BigDecimal availableCash = cashValidationService.getCashBalance(request.getAccountId());
            return ValidationOutcome.refused(
                String.format("Insufficient balance. Required: $%.2f, Available: $%.2f", orderCost, availableCash),
                RejectionReason.INSUFFICIENT_CASH);
        }
        return ValidationOutcome.passed(price);
    }

    private ValidationOutcome priceUnavailable() {
        return ValidationOutcome.refused("Market price is unavailable. Please try again.",
            RejectionReason.PRICE_UNAVAILABLE);
    }

    /**
     * Whether the account may trade: trading enabled on the account and the client ACTIVE (an
     * EXPIRED client may log in but not trade). Runs after the ownership check, so the account exists.
     */
    private boolean accountMayTrade(Integer accountId) {
        return accountRepository.findById(accountId)
            .map(account -> account.isTradingEnabled() && clientMayTrade(account))
            .orElse(true);
    }

    private boolean clientMayTrade(AccountEntity account) {
        return clientRepository.findById(account.getClientId())
            .map(ClientEntity::getAccountStatus)
            .map(status -> status.canTrade())
            .orElse(true);
    }

    private void validateInstrumentTradability(Integer instrumentId) {
        Instrument instrument = instrumentRepository.findById(instrumentId)
            .orElseThrow(() -> new IllegalArgumentException("Instrument not found with ID: " + instrumentId));

        if (!instrument.isTradable()) {
            log.warn("Tradability check failed for instrumentId={}", instrumentId);
            throw new NotTradableException("Instrument is currently not tradable");
        }
    }
}
