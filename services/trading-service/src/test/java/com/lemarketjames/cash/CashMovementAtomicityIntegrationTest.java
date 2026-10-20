package com.lemarketjames.cash;

import com.lemarketjames.common.audit.AuditEventRepository;
import com.lemarketjames.common.domain.AccountEntity;
import com.lemarketjames.common.domain.AccountRepository;
import com.lemarketjames.common.domain.AccountStatus;
import com.lemarketjames.common.domain.ClientEntity;
import com.lemarketjames.common.domain.ClientRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

/** Database proof that cash, receipt and audit are one transaction and one retry-safe effect. */
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:cashMovementAtomicity;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
class CashMovementAtomicityIntegrationTest {

    private static final AtomicInteger IDS = new AtomicInteger();

    @Autowired private ClientRepository clients;
    @Autowired private AccountRepository accounts;
    @Autowired private CashMovementReceiptRepository receipts;
    @Autowired private CashMovementService movements;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;
    @MockitoSpyBean private AuditEventRepository auditEvents;

    @Test
    void failedAuditRollsBackCashAndReceiptThenRetryAppliesOnce() {
        AccountFixture fixture = accountWithCash("1000.00");
        UUID requestId = UUID.randomUUID();
        AtomicReference<BigDecimal> cashSeenInside = new AtomicReference<>();

        doAnswer(write -> {
            entityManager.flush();
            cashSeenInside.set(cashOf(fixture.accountId()));
            throw new IllegalStateException("Audit write failed");
        }).when(auditEvents).save(any());

        assertThrows(IllegalStateException.class, () -> movements.move(
                fixture.username(), CashMovementType.DEPOSIT, new BigDecimal("250.00"), requestId));

        assertAmount("1250.00", cashSeenInside.get());
        assertAmount("1000.00", cashOf(fixture.accountId()));
        assertTrue(receipts.findById(requestId).isEmpty());
        assertEquals(0, cashAuditEvents(requestId));

        reset(auditEvents);
        CashMovementResult applied = movements.move(
                fixture.username(), CashMovementType.DEPOSIT, new BigDecimal("250.00"), requestId);
        CashMovementResult replayed = movements.move(
                fixture.username(), CashMovementType.DEPOSIT, new BigDecimal("250.00"), requestId);

        assertFalse(applied.replayed());
        assertTrue(replayed.replayed());
        assertAmount("1250.00", cashOf(fixture.accountId()));
        assertTrue(receipts.findById(requestId).isPresent());
        assertEquals(1, cashAuditEvents(requestId));
    }

    @Test
    void concurrentDuplicateRequestsHaveOneBalanceChangeAndOneAuditEvent() throws Exception {
        AccountFixture fixture = accountWithCash("1000.00");
        UUID requestId = UUID.randomUUID();

        try (var pool = Executors.newFixedThreadPool(2)) {
            CountDownLatch start = new CountDownLatch(1);
            var first = pool.submit(() -> {
                start.await();
                return movements.move(
                        fixture.username(), CashMovementType.DEPOSIT, new BigDecimal("100.00"), requestId);
            });
            var second = pool.submit(() -> {
                start.await();
                return movements.move(
                        fixture.username(), CashMovementType.DEPOSIT, new BigDecimal("100.00"), requestId);
            });
            start.countDown();

            CashMovementResult left = first.get(10, TimeUnit.SECONDS);
            CashMovementResult right = second.get(10, TimeUnit.SECONDS);
            assertTrue(left.replayed() ^ right.replayed());
        }

        assertAmount("1100.00", cashOf(fixture.accountId()));
        assertEquals(1, cashAuditEvents(requestId));
        assertTrue(receipts.findById(requestId).isPresent());
    }

    private AccountFixture accountWithCash(String cash) {
        int suffix = IDS.incrementAndGet();
        String username = "cash_client_" + suffix;
        ClientEntity client = new ClientEntity();
        client.setUsername(username);
        client.setPassword("unused");
        client.setEmail(username + "@test.invalid");
        client.setFullName("Cash Client " + suffix);
        client.setDateOfBirth(LocalDate.of(1990, 1, 1));
        client.setPhone("555000" + suffix);
        client.setRegisteredDate(LocalDateTime.now());
        client.setSsn("unused-" + suffix);
        client.setEmploymentStatus("EMPLOYED");
        client.setAccountStatus(AccountStatus.ACTIVE);
        client.setInvestmentExperience("BEGINNER");
        client = clients.saveAndFlush(client);

        AccountEntity account = new AccountEntity();
        account.setClientId(client.getClientId());
        account.setCashBalance(new BigDecimal(cash));
        account.setCurrency("USD");
        account.setTradingEnabled(true);
        account.setOpenedDate(LocalDate.now());
        account = accounts.saveAndFlush(account);
        return new AccountFixture(username, account.getAccountId());
    }

    private BigDecimal cashOf(int accountId) {
        return jdbc.queryForObject(
                "SELECT cash_balance FROM accounts WHERE account_id = ?", BigDecimal.class, accountId);
    }

    private int cashAuditEvents(UUID requestId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE request_id = ? AND action IN ('CASH_DEPOSITED', 'CASH_WITHDRAWN')",
                Integer.class, requestId.toString());
    }

    private static void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                "expected " + expected + " but was " + actual);
    }

    private record AccountFixture(String username, int accountId) {
    }
}
