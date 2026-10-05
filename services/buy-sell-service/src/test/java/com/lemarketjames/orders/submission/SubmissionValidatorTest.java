package com.lemarketjames.orders.submission;

import com.lemarketjames.common.domain.AccountEntity;
import com.lemarketjames.common.domain.AccountRepository;
import com.lemarketjames.common.domain.ClientRepository;
import com.lemarketjames.common.instruments.Instrument;
import com.lemarketjames.common.instruments.InstrumentRepository;
import com.lemarketjames.holdings.client.HoldingsValidationClient;
import com.lemarketjames.market.model.QuoteSnapshot;
import com.lemarketjames.market.service.MarketDataService;
import com.lemarketjames.orders.dto.CreateOrderRequest;
import com.lemarketjames.orders.entity.Order;
import com.lemarketjames.orders.exception.InsufficientHoldingsException;
import com.lemarketjames.orders.exception.NotTradableException;
import com.lemarketjames.orders.service.AccountAccess;
import com.lemarketjames.orders.service.CashValidationService;
import com.lemarketjames.orders.service.TradingRestrictions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * LMKT-99 AC2: every check that runs is noted on the trail with its rule and result, a failure
 * with the reason code the caller is given, and nothing after the first failure runs.
 */
@ExtendWith(MockitoExtension.class)
class SubmissionValidatorTest {

    private static final List<String> COMMON_CHECKS_PASSED =
        List.of("ACCOUNT_ACCESS:PASS", "ACCOUNT_STATUS:PASS", "LOCATION:PASS", "TRADABLE:PASS");

    @Mock AccountRepository accountRepository;
    @Mock ClientRepository clientRepository;
    @Mock InstrumentRepository instrumentRepository;
    @Mock TradingRestrictions restrictions;
    @Mock HoldingsValidationClient holdings;
    @Mock MarketDataService market;
    @Mock CashValidationService cash;
    SubmissionValidator validator;

    @BeforeEach
    void setUp() {
        validator = new SubmissionValidator(new AccountAccess(accountRepository, clientRepository), accountRepository,
            clientRepository, instrumentRepository, restrictions, holdings, market, cash);
        SecurityContextHolder.getContext()
            .setAuthentication(new TestingAuthenticationToken("testuser", "n/a", "ROLE_USER"));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static CreateOrderRequest order(Order.OrderType side) {
        return new CreateOrderRequest(1, 1, side, new BigDecimal("2"));
    }

    private static SubmissionTrail trailFor(CreateOrderRequest request) {
        return new SubmissionTrail("req-1", 42, request);
    }

    /** Each check as RULE:RESULT, plus :reason when it failed. */
    private static List<String> results(SubmissionTrail trail) {
        return trail.checks().stream()
            .map(check -> check.rule() + ":" + check.result() + (check.reason() == null ? "" : ":" + check.reason()))
            .toList();
    }

    private static List<String> commonChecksThen(String... more) {
        return java.util.stream.Stream.concat(COMMON_CHECKS_PASSED.stream(), java.util.stream.Stream.of(more)).toList();
    }

    private void ownsAccount() {
        when(accountRepository.existsByAccountIdAndUsername(1, "testuser")).thenReturn(true);
    }

    private void instrumentIsTradable(boolean tradable) {
        Instrument instrument = new Instrument();
        instrument.setTradable(tradable);
        when(instrumentRepository.findById(1)).thenReturn(Optional.of(instrument));
    }

    private void quoted(double ask) {
        when(market.findByInstrumentId(1)).thenReturn(Optional.of(
            new QuoteSnapshot(null, ask, ask, ask, ask, ask, ask, ask, 0, Instant.now(), null)));
    }

    @Test
    void anAcceptedBuyPassesEveryBuyRuleInOrder() {
        ownsAccount();
        instrumentIsTradable(true);
        quoted(250);
        when(cash.validateSufficientCash(1, new BigDecimal("500.0000"))).thenReturn(true);
        var request = order(Order.OrderType.BUY);
        var trail = trailFor(request);

        var outcome = validator.validate(request, trail);

        assertFalse(outcome.isRefused());
        assertEquals(new BigDecimal("250.0000"), outcome.price());
        assertEquals(new BigDecimal("250.0000"), trail.price());
        assertEquals(commonChecksThen("PRICE_AVAILABLE:PASS", "QUOTE_FRESH:PASS", "CASH:PASS"), results(trail));
        assertEquals(1, trail.accountId());
        verifyNoInteractions(holdings);
    }

    @Test
    void anAcceptedSellPassesTheHoldingsRuleInsteadOfPriceAndCash() {
        ownsAccount();
        instrumentIsTradable(true);
        var request = order(Order.OrderType.SELL);
        var trail = trailFor(request);

        var outcome = validator.validate(request, trail);

        assertFalse(outcome.isRefused());
        assertNull(outcome.price());
        assertEquals(commonChecksThen("HOLDINGS:PASS"), results(trail));
        verify(holdings).validateSufficientHoldings(1, "testuser", 1, new BigDecimal("2"));
        verifyNoInteractions(market, cash);
    }

    @Test
    void anAccountThatIsNotTheCallersFailsAccessAndIsNotAttributedToThem() {
        when(accountRepository.existsByAccountIdAndUsername(1, "testuser")).thenReturn(false);
        var request = order(Order.OrderType.BUY);
        var trail = trailFor(request);

        assertThrows(AccessDeniedException.class, () -> validator.validate(request, trail));

        assertEquals(List.of("ACCOUNT_ACCESS:FAIL:ACCOUNT_ACCESS_DENIED"), results(trail));
        assertNull(trail.accountId(), "the account isn't theirs, so no event may name it as theirs");
        assertEquals(1, trail.requestedAccountId());
        verifyNoInteractions(instrumentRepository, restrictions, market, cash, holdings);
    }

    @Test
    void anAccountWithTradingDisabledFailsAccountStatus() {
        ownsAccount();
        AccountEntity account = new AccountEntity();
        account.setClientId(3);
        account.setTradingEnabled(false);
        when(accountRepository.findById(1)).thenReturn(Optional.of(account));
        var request = order(Order.OrderType.BUY);
        var trail = trailFor(request);

        var outcome = validator.validate(request, trail);

        assertEquals("ACCOUNT_RESTRICTED", outcome.refusal().getCode());
        assertEquals(List.of("ACCOUNT_ACCESS:PASS", "ACCOUNT_STATUS:FAIL:ACCOUNT_RESTRICTED"), results(trail));
        verifyNoInteractions(instrumentRepository, market, cash, holdings);
    }

    @Test
    void aRestrictedResidenceFailsLocation() {
        ownsAccount();
        when(restrictions.locationRestricted(1)).thenReturn(true);
        var request = order(Order.OrderType.SELL);
        var trail = trailFor(request);

        var outcome = validator.validate(request, trail);

        assertEquals("LOCATION_RESTRICTED", outcome.refusal().getCode());
        assertEquals(List.of("ACCOUNT_ACCESS:PASS", "ACCOUNT_STATUS:PASS", "LOCATION:FAIL:LOCATION_RESTRICTED"),
            results(trail));
        verifyNoInteractions(instrumentRepository, holdings);
    }

    @Test
    void aSuspendedInstrumentFailsTradable() {
        ownsAccount();
        instrumentIsTradable(false);
        var request = order(Order.OrderType.BUY);
        var trail = trailFor(request);

        assertThrows(NotTradableException.class, () -> validator.validate(request, trail));

        assertEquals("TRADABLE:FAIL:NOT_TRADABLE", results(trail).get(3));
        assertEquals(4, trail.checks().size());
        verifyNoInteractions(market, cash);
    }

    @Test
    void anUnknownInstrumentFailsTradableAndIsStillReportedAsNotFound() {
        ownsAccount();
        when(instrumentRepository.findById(1)).thenReturn(Optional.empty());
        var request = order(Order.OrderType.BUY);
        var trail = trailFor(request);

        var notFound = assertThrows(IllegalArgumentException.class, () -> validator.validate(request, trail));

        assertEquals("Instrument not found with ID: 1", notFound.getMessage());
        assertEquals("TRADABLE:FAIL:NOT_TRADABLE", results(trail).get(3));
    }

    @Test
    void aSellForMoreThanIsHeldFailsHoldings() {
        ownsAccount();
        instrumentIsTradable(true);
        doThrow(new InsufficientHoldingsException("Insufficient holdings"))
            .when(holdings).validateSufficientHoldings(1, "testuser", 1, new BigDecimal("2"));
        var request = order(Order.OrderType.SELL);
        var trail = trailFor(request);

        assertThrows(InsufficientHoldingsException.class, () -> validator.validate(request, trail));

        assertEquals(commonChecksThen("HOLDINGS:FAIL:INSUFFICIENT_HOLDINGS"), results(trail));
    }

    @Test
    void aMissingQuoteFailsPriceAvailable() {
        ownsAccount();
        instrumentIsTradable(true);
        when(market.findByInstrumentId(1)).thenReturn(Optional.empty());
        var request = order(Order.OrderType.BUY);
        var trail = trailFor(request);

        var outcome = validator.validate(request, trail);

        assertEquals("PRICE_UNAVAILABLE", outcome.refusal().getCode());
        assertEquals(commonChecksThen("PRICE_AVAILABLE:FAIL:PRICE_UNAVAILABLE"), results(trail));
        assertNull(trail.price());
        verifyNoInteractions(cash);
    }

    @Test
    void anUnusableAskFailsPriceAvailableBeforeFreshnessIsAsked() {
        ownsAccount();
        instrumentIsTradable(true);
        // 0.000001 is positive but rounds to zero at the four decimals an order is priced to.
        for (double ask : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY, 0.000001}) {
            quoted(ask);
            var request = order(Order.OrderType.BUY);
            var trail = trailFor(request);

            assertEquals("PRICE_UNAVAILABLE", validator.validate(request, trail).refusal().getCode());
            assertEquals(commonChecksThen("PRICE_AVAILABLE:FAIL:PRICE_UNAVAILABLE"), results(trail), "ask " + ask);
        }
        verify(restrictions, never()).stale(any());
    }

    @Test
    void aStaleQuoteFailsQuoteFresh() {
        ownsAccount();
        instrumentIsTradable(true);
        quoted(250);
        when(restrictions.stale(any())).thenReturn(true);
        var request = order(Order.OrderType.BUY);
        var trail = trailFor(request);

        var outcome = validator.validate(request, trail);

        assertEquals("STALE_QUOTE", outcome.refusal().getCode());
        assertEquals(commonChecksThen("PRICE_AVAILABLE:PASS", "QUOTE_FRESH:FAIL:STALE_QUOTE"), results(trail));
        verifyNoInteractions(cash);
    }

    @Test
    void aBuyCostingMoreThanTheCashFailsCashAndKeepsThePriceItWasValuedAt() {
        ownsAccount();
        instrumentIsTradable(true);
        quoted(250);
        when(cash.validateSufficientCash(1, new BigDecimal("500.0000"))).thenReturn(false);
        when(cash.getCashBalance(1)).thenReturn(BigDecimal.ONE);
        var request = order(Order.OrderType.BUY);
        var trail = trailFor(request);

        var outcome = validator.validate(request, trail);

        assertEquals("INSUFFICIENT_CASH", outcome.refusal().getCode());
        assertEquals(commonChecksThen("PRICE_AVAILABLE:PASS", "QUOTE_FRESH:PASS", "CASH:FAIL:INSUFFICIENT_CASH"),
            results(trail));
        assertEquals(new BigDecimal("250.0000"), trail.price());
    }

    @Test
    void aCheckThatCannotCompleteIsLeftOpenAndRecordedAsAnErrorWhenAbandoned() {
        ownsAccount();
        instrumentIsTradable(true);
        doThrow(new IllegalStateException("holdings-service unreachable"))
            .when(holdings).validateSufficientHoldings(1, "testuser", 1, new BigDecimal("2"));
        var request = order(Order.OrderType.SELL);
        var trail = trailFor(request);

        assertThrows(IllegalStateException.class, () -> validator.validate(request, trail));
        assertEquals(COMMON_CHECKS_PASSED, results(trail), "not a pass and not a refusal yet");

        trail.abandon();

        assertEquals(commonChecksThen("HOLDINGS:ERROR"), results(trail));
        trail.abandon();
        assertEquals(5, trail.checks().size(), "abandoning twice records the check once");
    }
}
