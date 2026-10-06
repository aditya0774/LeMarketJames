package com.lemarketjames.orders;

import com.lemarketjames.common.audit.AuditEventEntity;
import com.lemarketjames.common.audit.AuditEventRepository;
import com.lemarketjames.common.audit.AuditEventType;
import com.lemarketjames.common.domain.*;
import com.lemarketjames.common.instruments.InstrumentRepository;
import com.lemarketjames.holdings.client.HoldingsValidationClient;
import com.lemarketjames.market.model.QuoteSnapshot;
import com.lemarketjames.market.service.MarketDataService;
import com.lemarketjames.orders.dto.CreateOrderRequest;
import com.lemarketjames.orders.dto.OrderResponse;
import com.lemarketjames.orders.entity.Order;
import com.lemarketjames.orders.execution.OrderTransitions;
import com.lemarketjames.orders.repository.OrderRepository;
import com.lemarketjames.orders.service.AccountAccess;
import com.lemarketjames.orders.service.OrderService;
import com.lemarketjames.orders.submission.SubmissionRecorder;
import com.lemarketjames.orders.submission.SubmissionValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * LMKT-99 AC3: a restart or failed deployment in the middle of a submission loses no audit event
 * and duplicates none (contract C2).
 *
 * <p>The process stopping is simulated by failing an audit write part-way through the submission's
 * transaction, after earlier events of the same trail were already written to it. What matters is
 * the same either way: the transaction never commits. The restart is a new coordinator that has
 * nothing of the first attempt in memory, given the same request ID.
 *
 * <p>No test transaction, so every count is of committed rows. Runs with H2 by default and the
 * real migrated PostgreSQL schema in Jenkins, where the unique index comes from 014.
 */
@SpringBootTest
class SubmissionRestartIntegrationTest {

    @Autowired OrderRepository orders;
    @Autowired AccountRepository accounts;
    @Autowired ClientRepository clients;
    @Autowired AddressRepository addresses;
    @Autowired InstrumentRepository instruments;
    @Autowired AccountAccess accountAccess;
    @Autowired SubmissionValidator validator;
    @Autowired SubmissionRecorder submissions;
    @Autowired OrderTransitions transitions;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuditTestCleanup cleanup;
    // Spied, not mocked: writes are real except the one that stands in for the process stopping.
    @SpyBean AuditEventRepository events;
    @MockBean MarketDataService market;
    @MockBean HoldingsValidationClient holdings;
    String username, requestId;
    Integer clientId, accountId, instrumentId;

    @BeforeEach
    void registerAndLogin() {
        username = "rst" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        accountId = register(username);
        clientId = clients.findByUsername(username).orElseThrow().getClientId();
        instrumentId = instruments.findByTicker("AAPL").orElseThrow().getInstrumentId();
        requestId = UUID.randomUUID().toString();
        when(market.findByInstrumentId(instrumentId)).thenReturn(Optional.of(
            new QuoteSnapshot(null, 100, 100, 100, 100, 100, 100, 100, 0, Instant.now(), null)));
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(username, "n/a", "ROLE_CLIENT"));
    }

    @AfterEach
    void removeOnlyThisTestsCommittedData() {
        SecurityContextHolder.clearContext();
        reset(events);
        // By request too: the direct insert below names no client, should it ever get through.
        cleanup.removeSubmission(requestId);
        cleanup.removeClient(username);
    }

    @Test
    void aRestartMidwayThroughAnAcceptedSubmissionLosesNothingAndDuplicatesNothing() {
        CreateOrderRequest buy = new CreateOrderRequest(accountId, instrumentId, Order.OrderType.BUY, BigDecimal.ONE);

        // The process stops while writing the last event: the order and eight events are already
        // in the transaction, which then never commits.
        stopTheProcessWhenWriting(requestId + ":VALIDATED");
        assertThrows(IllegalStateException.class, () -> restartedService().createOrder(buy, requestId));

        assertWrittenBeforeTheStop(8, "SUBMITTED and the seven checks were written before the stop");
        assertEquals(0, countEvents(), "no partial trail survives");
        assertEquals(0, countOrders(), "and no order without its trail");

        // After the restart the submission is made again and completes: nothing was lost with the
        // first attempt, because nothing of it was ever acknowledged to the caller.
        reset(events);
        OrderResponse accepted = restartedService().createOrder(buy, requestId);

        assertTrue(accepted.isSuccess());
        assertWholeAcceptedTrailExactlyOnce(accepted.getOrderId());

        // The same submission arriving again is refused by the unique key as a whole: no second
        // copy of any event, and no second order either, since the order shares the transaction.
        assertThrows(DataIntegrityViolationException.class, () -> restartedService().createOrder(buy, requestId));

        assertWholeAcceptedTrailExactlyOnce(accepted.getOrderId());

        // The guard is the database's, not just this code path's: a direct second copy is refused too.
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
            "INSERT INTO audit_log (action, created_at, archived, request_id, event_key) "
                + "VALUES ('SUBMITTED', CURRENT_TIMESTAMP, FALSE, ?, ?)", requestId, requestId + ":SUBMITTED"));
        assertWholeAcceptedTrailExactlyOnce(accepted.getOrderId());
    }

    @Test
    void aRestartMidwayThroughARefusedSubmissionLosesNothingAndDuplicatesNothing() {
        // 100 shares at 100.00 against 500.00 of cash: refused at the last check.
        CreateOrderRequest tooBig = new CreateOrderRequest(accountId, instrumentId, Order.OrderType.BUY, new BigDecimal("100"));

        stopTheProcessWhenWriting(requestId + ":RULE_CHECKED:CASH");
        assertThrows(IllegalStateException.class, () -> restartedService().createOrder(tooBig, requestId));

        assertWrittenBeforeTheStop(7, "SUBMITTED and six checks were written before the stop");
        assertEquals(0, countEvents(), "no partial trail survives");

        reset(events);
        OrderResponse refused = restartedService().createOrder(tooBig, requestId);

        assertEquals("INSUFFICIENT_CASH", refused.getCode());
        assertWholeRefusedTrailExactlyOnce();

        assertThrows(DataIntegrityViolationException.class, () -> restartedService().createOrder(tooBig, requestId));

        assertWholeRefusedTrailExactlyOnce();
    }

    /** A coordinator with nothing in memory from an earlier attempt, as after a restart. */
    private OrderService restartedService() {
        return new OrderService(orders, accountAccess, validator, submissions, transitions, events);
    }

    /** Fails the write of one event, as if the process stopped there; every other write is real. */
    private void stopTheProcessWhenWriting(String eventKey) {
        doThrow(new IllegalStateException("Process stopped before the transaction committed"))
            .when(events).save(argThat((AuditEventEntity event) -> eventKey.equals(event.getEventKey())));
    }

    /** How many events reached the transaction before the write that stopped it. */
    private void assertWrittenBeforeTheStop(int expected, String what) {
        // Every attempted write is counted, so one more than were written: the last one is the stop.
        verify(events, times(expected + 1).description(what)).save(any());
    }

    private void assertWholeAcceptedTrailExactlyOnce(Integer orderId) {
        assertEquals(1, countEvents(AuditEventType.SUBMITTED));
        assertEquals(7, countEvents(AuditEventType.RULE_CHECKED));
        assertEquals(1, countEvents(AuditEventType.VALIDATED));
        assertEquals(9, countEvents());
        assertEquals(9, countDistinctEventKeys(), "one event per type and rule");
        assertEquals(9, jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE request_id=? AND order_id=?",
            Integer.class, requestId, orderId), "all of them for the one order");
        assertEquals(1, countOrders());
    }

    private void assertWholeRefusedTrailExactlyOnce() {
        assertEquals(1, countEvents(AuditEventType.SUBMITTED));
        assertEquals(7, countEvents(AuditEventType.RULE_CHECKED));
        assertEquals(0, countEvents(AuditEventType.VALIDATED));
        assertEquals(8, countEvents());
        assertEquals(8, countDistinctEventKeys(), "one event per type and rule");
        assertEquals(0, countOrders());
    }

    private int countEvents() {
        return jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE request_id=?", Integer.class, requestId);
    }

    private int countEvents(AuditEventType type) {
        return jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE request_id=? AND action=?",
            Integer.class, requestId, type.name());
    }

    private int countDistinctEventKeys() {
        return jdbc.queryForObject("SELECT count(DISTINCT event_key) FROM audit_log WHERE request_id=?",
            Integer.class, requestId);
    }

    private int countOrders() {
        return jdbc.queryForObject("SELECT count(*) FROM orders WHERE account_id=?", Integer.class, accountId);
    }

    // Auth is a separate service; seed the shared client/address/account rows it writes.
    private Integer register(String username) {
        ClientEntity client = new ClientEntity();
        client.setUsername(username);
        client.setPassword("not-used-by-buy-sell-service");
        client.setEmail(username + "@example.com");
        client.setFullName("Restart Test");
        client.setDateOfBirth(LocalDate.of(1990, 1, 1));
        client.setPhone("5551234567");
        client.setRegisteredDate(LocalDateTime.now());
        client.setSsn("test-only");
        client.setEmploymentStatus("EMPLOYED");
        client.setInvestmentExperience("beginner");
        client.setAccountStatus(AccountStatus.ACTIVE);
        client = clients.saveAndFlush(client);
        AddressEntity address = new AddressEntity();
        address.setClientId(client.getClientId());
        address.setAddressType("RESIDENTIAL");
        address.setStreetAddress("123 Main St");
        address.setCity("Boston");
        address.setState("MA");
        address.setPostalCode("02110");
        address.setCountry("US");
        addresses.saveAndFlush(address);
        AccountEntity account = new AccountEntity();
        account.setClientId(client.getClientId());
        account.setCashBalance(new BigDecimal("500"));
        account.setCurrency("USD");
        account.setTradingEnabled(true);
        account.setOpenedDate(LocalDate.now());
        return accounts.saveAndFlush(account).getAccountId();
    }
}
