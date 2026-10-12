package com.lemarketjames.holdings;

import com.lemarketjames.common.audit.AuditEventRepository;
import com.lemarketjames.common.domain.AccountEntity;
import com.lemarketjames.common.domain.AccountRepository;
import com.lemarketjames.holdings.dto.SettlementRequest;
import com.lemarketjames.holdings.entity.HoldingsEntity;
import com.lemarketjames.holdings.repository.HoldingsRepository;
import com.lemarketjames.holdings.repository.SettlementReceiptRepository;
import com.lemarketjames.holdings.service.HoldingsSettlementService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

/**
 * A settlement is all or nothing (LMKT-71, contracts C2 and C6): when a step fails part-way, the
 * cash, the holding and the settlement receipt are all as they were, and the same request sent
 * again later settles exactly once.
 *
 * <p>The step that fails here is the last one, the audit write, so everything before it has
 * already happened. The test flushes first, so the cash debit, the holding and the receipt have
 * really been sent to the database before the failure, and it reads them back inside the
 * transaction to prove that; what is asserted afterwards is therefore a rollback, not writes that
 * were never made. Its own H2 database, so its accounts and orders are nobody else's.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:settlementAtomicity;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
class SettlementAtomicityIntegrationTest {

    private static final int INSTRUMENT = 1;

    @Autowired AccountRepository accounts;
    @Autowired HoldingsRepository holdings;
    @Autowired SettlementReceiptRepository receipts;
    @Autowired HoldingsSettlementService settlement;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;
    // Spring resets the spy after each test, so a failure set up in one never reaches the next.
    @MockitoSpyBean AuditEventRepository auditEvents;

    private int accountWithCash(String cash) {
        AccountEntity account = new AccountEntity();
        account.setClientId((int) accounts.count() + 1);
        account.setCashBalance(new BigDecimal(cash));
        account.setCurrency("USD");
        account.setTradingEnabled(true);
        account.setOpenedDate(LocalDate.now());
        return accounts.saveAndFlush(account).getAccountId();
    }

    private static SettlementRequest buy(int accountId, int orderId, String quantity, String price) {
        SettlementRequest request = new SettlementRequest();
        request.setOrderId(orderId);
        request.setAccountId(accountId);
        request.setInstrumentId(INSTRUMENT);
        request.setOrderType(SettlementRequest.OrderType.BUY);
        request.setQuantity(new BigDecimal(quantity));
        request.setPricePerUnit(new BigDecimal(price));
        return request;
    }

    /**
     * Makes the next audit write fail, after the settlement's other writes have reached the
     * database.
     *
     * @return what the failing transaction itself saw just before it failed: the account's cash
     */
    private AtomicReference<BigDecimal> auditWriteFailsFor(int accountId) {
        AtomicReference<BigDecimal> cashSeenInside = new AtomicReference<>();
        doAnswer(write -> {
            entityManager.flush();
            cashSeenInside.set(cashOf(accountId));
            throw new IllegalStateException("Audit write failed part-way through settlement");
        }).when(auditEvents).save(any());
        return cashSeenInside;
    }

    private BigDecimal cashOf(int accountId) {
        return jdbc.queryForObject("SELECT cash_balance FROM accounts WHERE account_id = ?", BigDecimal.class, accountId);
    }

    private int settledEventsOf(int orderId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE order_id = ? AND action = 'SETTLED'",
            Integer.class, orderId);
    }

    private static void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected " + expected + " but was " + actual);
    }

    @Test
    void aFailedSettlementChangesNothingAndItsRetrySettlesOnce() {
        int accountId = accountWithCash("150");
        int orderId = accountId * 10;
        SettlementRequest request = buy(accountId, orderId, "1", "100");

        AtomicReference<BigDecimal> cashSeenInside = auditWriteFailsFor(accountId);
        assertThrows(IllegalStateException.class, () -> settlement.settle(request));

        // The debit had been made when the failure came...
        assertAmount("50", cashSeenInside.get());
        // ...and none of it is left.
        assertAmount("150", cashOf(accountId));
        assertTrue(holdings.findByAccountIdAndInstrumentId(accountId, INSTRUMENT).isEmpty());
        assertTrue(receipts.findById(orderId).isEmpty());
        assertEquals(0, settledEventsOf(orderId));

        // Later the same request is sent again, as buy-sell-service's retry does (contract C6).
        reset(auditEvents);
        assertNull(settlement.settle(request));

        assertAmount("50", cashOf(accountId));
        assertAmount("1", holdings.findByAccountIdAndInstrumentId(accountId, INSTRUMENT).orElseThrow().getQuantity());
        assertTrue(receipts.findById(orderId).isPresent());
        assertEquals(1, settledEventsOf(orderId));

        // A further retry finds the receipt and moves nothing a second time.
        assertNull(settlement.settle(request));

        assertAmount("50", cashOf(accountId));
        assertAmount("1", holdings.findByAccountIdAndInstrumentId(accountId, INSTRUMENT).orElseThrow().getQuantity());
        assertEquals(1, settledEventsOf(orderId));
    }

    @Test
    void aFailedSettlementLeavesAnExistingHoldingAsItWas() {
        int accountId = accountWithCash("150");
        int firstOrder = accountId * 10;
        int secondOrder = firstOrder + 1;
        assertNull(settlement.settle(buy(accountId, firstOrder, "1", "100")));
        SettlementRequest request = buy(accountId, secondOrder, "1", "40");

        AtomicReference<BigDecimal> cashSeenInside = auditWriteFailsFor(accountId);
        assertThrows(IllegalStateException.class, () -> settlement.settle(request));

        assertAmount("10", cashSeenInside.get());
        assertAmount("50", cashOf(accountId));
        HoldingsEntity untouched = holdings.findByAccountIdAndInstrumentId(accountId, INSTRUMENT).orElseThrow();
        assertAmount("1", untouched.getQuantity());
        assertAmount("100", untouched.getAverageCost());
        assertTrue(receipts.findById(secondOrder).isEmpty());
        assertEquals(0, settledEventsOf(secondOrder));
        // The earlier settlement is not disturbed by the failure of a later one.
        assertEquals(1, settledEventsOf(firstOrder));

        reset(auditEvents);
        assertNull(settlement.settle(request));
        assertNull(settlement.settle(request));

        assertAmount("10", cashOf(accountId));
        HoldingsEntity bought = holdings.findByAccountIdAndInstrumentId(accountId, INSTRUMENT).orElseThrow();
        assertAmount("2", bought.getQuantity());
        // (1 x 100 + 1 x 40) / 2 shares
        assertAmount("70", bought.getAverageCost());
        assertEquals(1, settledEventsOf(secondOrder));
    }
}
