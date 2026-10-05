package com.lemarketjames.orders.submission;

import com.lemarketjames.common.domain.AccountEntity;
import com.lemarketjames.common.domain.AccountRepository;
import com.lemarketjames.common.domain.ClientEntity;
import com.lemarketjames.common.domain.ClientRepository;
import com.lemarketjames.common.error.GlobalExceptionHandler;
import com.lemarketjames.common.instruments.Instrument;
import com.lemarketjames.common.instruments.InstrumentRepository;
import com.lemarketjames.holdings.client.HoldingsValidationClient;
import com.lemarketjames.market.service.MarketDataService;
import com.lemarketjames.orders.dto.CreateOrderRequest;
import com.lemarketjames.orders.entity.Order;
import com.lemarketjames.orders.entity.RejectionReason;
import com.lemarketjames.orders.exception.InsufficientHoldingsException;
import com.lemarketjames.orders.exception.NotTradableException;
import com.lemarketjames.orders.service.AccountAccess;
import com.lemarketjames.orders.service.CashValidationService;
import com.lemarketjames.orders.service.TradingRestrictions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * The checks an order must pass before it is saved, in the order of {@link ValidationRule}:
 * ownership, account status, location, tradability, then holdings for a SELL or price and cash for
 * a BUY. Kept apart from {@code OrderService} so deciding whether an order may be placed is
 * separate from saving it.
 *
 * <p>Every check that runs is noted on the {@link SubmissionTrail}, pass or fail, before its
 * result is acted on, so the trail is complete however the submission ends.
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
     * Runs the checks for one order, stopping at the first that fails. SELL orders require
     * sufficient holdings; BUY orders are priced from the market, never from the browser, and
     * require sufficient cash at that price.
     *
     * @param trail receives the result of every check that runs
     * @throws AccessDeniedException if the account isn't the caller's
     * @throws NotTradableException if the instrument is suspended
     * @throws InsufficientHoldingsException if a SELL exceeds the holding
     */
    public ValidationOutcome validate(CreateOrderRequest request, SubmissionTrail trail) {
        requireOwnAccount(request.getAccountId(), trail);

        trail.begin(ValidationRule.ACCOUNT_STATUS);
        if (!accountMayTrade(request.getAccountId())) {
            log.warn("Order refused for restricted accountId={}", request.getAccountId());
            return refuse(trail, "This account can't place orders right now. Please contact support.",
                RejectionReason.ACCOUNT_RESTRICTED);
        }
        trail.pass();

        trail.begin(ValidationRule.LOCATION);
        if (restrictions.locationRestricted(request.getAccountId())) {
            return refuse(trail, "Trading is restricted in your location", RejectionReason.LOCATION_RESTRICTED);
        }
        trail.pass();

        requireTradable(request.getInstrumentId(), trail);
        // Browser validation is advisory; every SELL must be checked again before saving.
        if (request.getOrderType() == Order.OrderType.SELL) {
            requireHoldings(request, trail);
        }
        if (request.getOrderType() == Order.OrderType.BUY) {
            return priceAndCheckCash(request, trail);
        }
        return ValidationOutcome.passed(null);
    }

    private void requireOwnAccount(Integer accountId, SubmissionTrail trail) {
        trail.begin(ValidationRule.ACCOUNT_ACCESS);
        try {
            accountAccess.requireOwnAccount(accountId);
        } catch (AccessDeniedException denied) {
            trail.fail(GlobalExceptionHandler.ACCOUNT_ACCESS_DENIED);
            throw denied;
        }
        trail.pass();
    }

    private void requireTradable(Integer instrumentId, SubmissionTrail trail) {
        trail.begin(ValidationRule.TRADABLE);
        Optional<Instrument> instrument = instrumentRepository.findById(instrumentId);
        if (instrument.isEmpty()) {
            // An instrument that doesn't exist can't be traded; the caller still gets "not found".
            trail.fail(RejectionReason.NOT_TRADABLE.name());
            throw new IllegalArgumentException("Instrument not found with ID: " + instrumentId);
        }
        if (!instrument.get().isTradable()) {
            log.warn("Tradability check failed for instrumentId={}", instrumentId);
            trail.fail(RejectionReason.NOT_TRADABLE.name());
            throw new NotTradableException("Instrument is currently not tradable");
        }
        trail.pass();
    }

    private void requireHoldings(CreateOrderRequest request, SubmissionTrail trail) {
        trail.begin(ValidationRule.HOLDINGS);
        try {
            holdingsValidationClient.validateSufficientHoldings(request.getAccountId(),
                accountAccess.authenticatedUsername(), request.getInstrumentId(), request.getQuantity());
        } catch (InsufficientHoldingsException insufficient) {
            trail.fail(RejectionReason.INSUFFICIENT_HOLDINGS.name());
            throw insufficient;
        }
        trail.pass();
    }

    private ValidationOutcome priceAndCheckCash(CreateOrderRequest request, SubmissionTrail trail) {
        trail.begin(ValidationRule.PRICE_AVAILABLE);
        var quote = marketDataService.findByInstrumentId(request.getInstrumentId());
        // Match the database scale so validation and the persisted snapshot agree. A price that
        // rounds to zero is as unusable as none, so it belongs to this check rather than a later one.
        BigDecimal price = quote
            .filter(q -> Double.isFinite(q.askPrice()) && q.askPrice() > 0)
            .map(q -> BigDecimal.valueOf(q.askPrice()).setScale(4, RoundingMode.HALF_UP))
            .filter(rounded -> rounded.signum() > 0)
            .orElse(null);
        if (price == null) {
            return refuse(trail, "Market price is unavailable. Please try again.", RejectionReason.PRICE_UNAVAILABLE);
        }
        trail.pass();

        trail.begin(ValidationRule.QUOTE_FRESH);
        if (restrictions.stale(quote.get())) {
            return refuse(trail, "Market quote is stale", RejectionReason.STALE_QUOTE);
        }
        trail.pass();
        trail.priced(price);

        trail.begin(ValidationRule.CASH);
        BigDecimal orderCost = request.getQuantity().multiply(price);
        if (!cashValidationService.validateSufficientCash(request.getAccountId(), orderCost)) {
            BigDecimal availableCash = cashValidationService.getCashBalance(request.getAccountId());
            return refuse(trail,
                String.format("Insufficient balance. Required: $%.2f, Available: $%.2f", orderCost, availableCash),
                RejectionReason.INSUFFICIENT_CASH);
        }
        trail.pass();
        return ValidationOutcome.passed(price);
    }

    /** Fails the open check with the same code the caller is given. */
    private ValidationOutcome refuse(SubmissionTrail trail, String message, RejectionReason reason) {
        trail.fail(reason.name());
        return ValidationOutcome.refused(message, reason);
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
}
