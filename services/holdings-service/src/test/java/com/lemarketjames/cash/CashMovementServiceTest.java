package com.lemarketjames.cash;

import com.lemarketjames.common.audit.AuditEventType;
import com.lemarketjames.common.audit.AuditRecorder;
import com.lemarketjames.common.audit.CashAuditEvent;
import com.lemarketjames.common.config.PlatformSettings;
import com.lemarketjames.common.domain.AccountEntity;
import com.lemarketjames.common.domain.AccountRepository;
import com.lemarketjames.orders.client.OrderSummary;
import com.lemarketjames.orders.client.OrdersClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CashMovementServiceTest {

    private static final UUID REQUEST_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final Instant NOW = Instant.parse("2026-10-19T12:00:00Z");

    @Mock private AccountRepository accounts;
    @Mock private CashMovementReceiptRepository receipts;
    @Mock private OrdersClient orders;
    @Mock private AuditRecorder audit;
    @Mock private AccountEntity account;

    private CashMovementService service;

    @BeforeEach
    void setUp() {
        PlatformSettings settings = new PlatformSettings();
        service = new CashMovementService(accounts, receipts, orders, audit, settings,
                Clock.fixed(NOW, ZoneOffset.UTC));
        lenient().when(accounts.findAccountIdByUsername("alice")).thenReturn(Optional.of(7));
        lenient().when(accounts.findLockedById(7)).thenReturn(Optional.of(account));
        lenient().when(account.getAccountId()).thenReturn(7);
        lenient().when(account.getClientId()).thenReturn(3);
    }

    @Test
    void depositsAndAuditsInOneServiceCall() {
        when(account.getCashBalance()).thenReturn(new BigDecimal("1000.00"));
        when(receipts.findById(REQUEST_ID)).thenReturn(Optional.empty());

        CashMovementResult result = service.move(
                "alice", CashMovementType.DEPOSIT, new BigDecimal("250.25"), REQUEST_ID);

        assertEquals(new BigDecimal("1250.25"), result.cashBalanceAfter());
        verify(account).setCashBalance(new BigDecimal("1250.25"));
        verify(receipts).save(any(CashMovementReceipt.class));
        ArgumentCaptor<CashAuditEvent> event = ArgumentCaptor.forClass(CashAuditEvent.class);
        verify(audit).recordCashMovement(event.capture());
        assertEquals(AuditEventType.CASH_DEPOSITED, event.getValue().type());
        assertEquals(new BigDecimal("250.25"), event.getValue().details().get("amount"));
        assertEquals(new BigDecimal("1250.25"), event.getValue().details().get("balanceAfter"));
        assertEquals(REQUEST_ID.toString(), event.getValue().details().get("requestId"));
    }

    @Test
    void withdrawalLeavesCashReservedForOpenBuyOrders() {
        when(account.getCashBalance()).thenReturn(new BigDecimal("1000.00"));
        when(receipts.findById(REQUEST_ID)).thenReturn(Optional.empty());
        when(orders.getOrdersForAccountRequired(7)).thenReturn(List.of(
                new OrderSummary(1, "BUY", new BigDecimal("2"), new BigDecimal("100.00"), "ACCEPTED", null),
                new OrderSummary(2, "SELL", new BigDecimal("9"), new BigDecimal("100.00"), "ACCEPTED", null),
                new OrderSummary(3, "BUY", new BigDecimal("5"), new BigDecimal("100.00"), "FILLED", null)));

        CashMovementResult result = service.move(
                "alice", CashMovementType.WITHDRAWAL, new BigDecimal("800.00"), REQUEST_ID);

        assertEquals(new BigDecimal("200.00"), result.cashBalanceAfter());
        verify(account).setCashBalance(new BigDecimal("200.00"));
    }

    @Test
    void refusesWithdrawalAboveBuyingPower() {
        when(account.getCashBalance()).thenReturn(new BigDecimal("1000.00"));
        when(receipts.findById(REQUEST_ID)).thenReturn(Optional.empty());
        when(orders.getOrdersForAccountRequired(7)).thenReturn(List.of(
                new OrderSummary(1, "BUY", new BigDecimal("2"), new BigDecimal("100.00"), "ACCEPTED", null)));

        CashMovementException error = assertThrows(CashMovementException.class, () -> service.move(
                "alice", CashMovementType.WITHDRAWAL, new BigDecimal("800.01"), REQUEST_ID));

        assertEquals(CashMovementService.INSUFFICIENT_FUNDS, error.getCode());
        assertEquals("You can withdraw up to $800.00.", error.getMessage());
        verify(account, never()).setCashBalance(any());
    }

    @Test
    void withdrawalFailsClosedWhenOpenOrdersCannotBeRead() {
        when(receipts.findById(REQUEST_ID)).thenReturn(Optional.empty());
        when(orders.getOrdersForAccountRequired(7)).thenThrow(new RestClientException("unavailable"));

        assertThrows(RestClientException.class, () -> service.move(
                "alice", CashMovementType.WITHDRAWAL, new BigDecimal("10.00"), REQUEST_ID));

        verify(account, never()).setCashBalance(any());
        verify(receipts, never()).save(any());
        verify(audit, never()).recordCashMovement(any());
    }

    @Test
    void validatesPositiveCentsAndConfiguredLimit() {
        when(receipts.findById(REQUEST_ID)).thenReturn(Optional.empty());

        for (BigDecimal amount : List.of(
                BigDecimal.ZERO, new BigDecimal("-1.00"), new BigDecimal("1.001"), new BigDecimal("50000.01"))) {
            CashMovementException error = assertThrows(CashMovementException.class, () -> service.move(
                    "alice", CashMovementType.DEPOSIT, amount, REQUEST_ID));
            assertEquals(CashMovementService.INVALID_AMOUNT, error.getCode());
        }
        verify(account, never()).setCashBalance(any());
    }

    @Test
    void retryReturnsTheStoredOutcomeWithoutMovingOrAuditingAgain() {
        CashMovementReceipt receipt = new CashMovementReceipt(
                REQUEST_ID, 7, CashMovementType.DEPOSIT, new BigDecimal("10.00"),
                new BigDecimal("1010.00"), NOW);
        when(receipts.findById(REQUEST_ID)).thenReturn(Optional.of(receipt));

        CashMovementResult result = service.move(
                "alice", CashMovementType.WITHDRAWAL, new BigDecimal("99999.999"), REQUEST_ID);

        assertTrue(result.replayed());
        assertEquals(new BigDecimal("1010.00"), result.cashBalanceAfter());
        verify(account, never()).setCashBalance(any());
        verify(audit, never()).recordCashMovement(any());
    }

    @Test
    void accountIsRequiredEvenForStaffAndReceiptsCannotCrossAccounts() {
        when(accounts.findAccountIdByUsername("staff")).thenReturn(Optional.empty());
        CashMovementException missing = assertThrows(CashMovementException.class, () -> service.move(
                "staff", CashMovementType.DEPOSIT, BigDecimal.ONE, REQUEST_ID));
        assertEquals(CashMovementService.ACCOUNT_NOT_FOUND, missing.getCode());

        CashMovementReceipt anotherAccount = new CashMovementReceipt(
                REQUEST_ID, 99, CashMovementType.DEPOSIT, BigDecimal.ONE, BigDecimal.TEN, NOW);
        when(receipts.findById(REQUEST_ID)).thenReturn(Optional.of(anotherAccount));
        CashMovementException reused = assertThrows(CashMovementException.class, () -> service.move(
                "alice", CashMovementType.DEPOSIT, BigDecimal.ONE, REQUEST_ID));
        assertEquals(CashMovementService.INVALID_AMOUNT, reused.getCode());
    }
}
