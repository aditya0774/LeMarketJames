package com.lemarketjames.orders.execution;

import com.lemarketjames.common.audit.AuditEventType;
import com.lemarketjames.common.audit.AuditRecorder;
import com.lemarketjames.orders.entity.Order;
import com.lemarketjames.orders.entity.Order.OrderStatus;
import com.lemarketjames.orders.entity.RejectionReason;
import com.lemarketjames.orders.repository.OrderRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BR-15: every step execution moves an order through is in its audit trail, in order, including
 * the time it spent waiting for the market and the retries after a failed attempt (contract C2).
 * The executor and the repository are stubs; the transitions are the real ones.
 */
class OrderExecutionTransactionsTest {

    final OrderRepository orders = mock(OrderRepository.class);
    final OrderExecutor executor = mock(OrderExecutor.class);
    final AuditRecorder audit = mock(AuditRecorder.class);
    final OrderExecutionTransactions transactions = new OrderExecutionTransactions(
        orders, executor, new OrderTransitions(audit, mock(ApplicationEventPublisher.class)));
    Order order;

    @BeforeEach
    void submittedBuy() {
        order = new Order(7, 5, Order.OrderType.BUY, BigDecimal.ONE);
        order.setOrderId(42);
        order.setOrderStatus(OrderStatus.SUBMITTED);
        when(orders.findLockedById(42)).thenReturn(Optional.of(order));
    }

    private List<AuditEventType> trail() {
        ArgumentCaptor<AuditEventType> types = ArgumentCaptor.forClass(AuditEventType.class);
        verify(audit, atLeastOnce()).record(types.capture(), eq(42), eq(7), anyMap());
        return types.getAllValues();
    }

    @Test
    void anOrderPlacedWhileTheMarketIsClosedShowsEveryStep() {
        when(executor.execute(any())).thenReturn(ExecutionResult.waiting(OrderStatus.DELAYED, RejectionReason.MARKET_CLOSED));
        transactions.prepare(42, false);
        // The scheduler tries again while the market is still closed: the order stays DELAYED, once.
        transactions.prepare(42, false);

        when(executor.execute(any())).thenReturn(ExecutionResult.filled(
            new QuoteUsed(new BigDecimal("101.0000"), "SIMULATED_FEED", Instant.parse("2026-10-01T13:30:01Z"))));
        transactions.prepare(42, false);
        transactions.finish(42, null);

        // SETTLED is written by trading-service between PENDING and FILLED.
        assertEquals(List.of(AuditEventType.ACCEPTED, AuditEventType.DELAYED, AuditEventType.PENDING, AuditEventType.FILLED),
            trail());
        verify(audit).record(AuditEventType.DELAYED, 42, 7, Map.of("reason", "MARKET_CLOSED"));
    }

    @Test
    void aFailedAttemptIsAuditedAsPendingOnce() {
        when(executor.execute(any())).thenThrow(new IllegalStateException("feed timed out"));
        transactions.prepare(42, false);
        transactions.prepare(42, false);

        assertEquals(List.of(AuditEventType.ACCEPTED, AuditEventType.PENDING), trail());
    }

    @Test
    void aRejectionOnAStaleQuoteRecordsTheQuote() {
        Instant quoteTime = Instant.parse("2026-10-01T14:00:00Z");
        when(executor.execute(any())).thenReturn(ExecutionResult.rejected(RejectionReason.STALE_QUOTE,
            new QuoteUsed(new BigDecimal("101.0000"), "SIMULATED_FEED", quoteTime)));
        transactions.prepare(42, false);

        assertEquals(List.of(AuditEventType.ACCEPTED, AuditEventType.REJECTED), trail());
        verify(audit).record(AuditEventType.REJECTED, 42, 7, Map.of(
            "reason", "STALE_QUOTE", "quotePrice", new BigDecimal("101.0000"), "quoteTime", "2026-10-01T14:00:00Z"));
    }
}
