package com.lemarketjames.orders.execution;

import com.lemarketjames.common.domain.*;
import com.lemarketjames.holdings.client.HoldingsSettlementClient;
import com.lemarketjames.holdings.client.SettlementRejectedException;
import com.lemarketjames.orders.entity.*;
import com.lemarketjames.orders.repository.OrderRepository;
import com.lemarketjames.orders.service.OrderService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:executionRecovery;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
class ExecutionRecoveryIntegrationTest {
    @Autowired OrderRepository orders;
    @Autowired AccountRepository accounts;
    @Autowired OrderExecutionService execution;
    @Autowired OrderExecutionTransactions transactions;
    @Autowired OrderService service;
    @MockBean OrderExecutor executor;
    @MockBean HoldingsSettlementClient settlement;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    Order seed() {
        AccountEntity account = new AccountEntity();
        // H2 entities have scalar client ids; no auth fixtures are needed for internal execution.
        account.setClientId((int)(accounts.count() + 1)); account.setCashBalance(new BigDecimal("1000"));
        account.setCurrency("USD"); account.setOpenedDate(LocalDate.now()); account.setTradingEnabled(true);
        account = accounts.saveAndFlush(account);
        return orders.saveAndFlush(new Order(account.getAccountId(), 1, Order.OrderType.SELL, BigDecimal.ONE));
    }
    @Test void lostResponseRetainsPriceAndRecoveryFinishesExactlyOnce() {
        Order order = seed(); int id = order.getOrderId();
        when(executor.execute(any())).thenReturn(ExecutionResult.filled(new BigDecimal("99.0000")));
        AtomicInteger calls = new AtomicInteger();
        doAnswer(inv -> {
            Order sent = inv.getArgument(0);
            Order committed = orders.findById(id).orElseThrow();
            assertTrue(committed.isSettlementPending());
            assertEquals(sent.getPricePerUnit(), committed.getPricePerUnit());
            if (calls.incrementAndGet() == 1) throw new IllegalStateException("Response lost after remote commit");
            return null;
        }).when(settlement).settle(any());
        assertThrows(IllegalStateException.class, () -> execution.execute(id, false));
        assertEquals(Order.OrderStatus.PENDING, orders.findById(id).orElseThrow().getOrderStatus());
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("ops", "n/a", "ROLE_TRADING_OPS"));
        try { assertThrows(org.springframework.web.server.ResponseStatusException.class,
            () -> service.rejectOrder(id, RejectionReason.REJECTED_BY_OPERATIONS)); }
        finally { SecurityContextHolder.clearContext(); }
        // A fresh coordinator simulates restart: all recovery information must come from the database.
        new OrderExecutionService(transactions, settlement).execute(id, false);
        execution.execute(id, false);
        Order filled = orders.findById(id).orElseThrow();
        assertEquals(Order.OrderStatus.FILLED, filled.getOrderStatus());
        assertFalse(filled.isSettlementPending()); assertNotNull(filled.getFilledAt());
        verify(executor, times(1)).execute(any());
        verify(settlement, times(2)).settle(any());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE order_id=? AND action='FILLED'", Integer.class, id));
    }
    @Test void definiteSettlementRefusalBecomesFinalRejection() {
        Order order = seed();
        when(executor.execute(any())).thenReturn(ExecutionResult.filled(BigDecimal.TEN));
        doThrow(new SettlementRejectedException(RejectionReason.INSUFFICIENT_HOLDINGS)).when(settlement).settle(any());
        execution.execute(order.getOrderId(), false);
        Order rejected = orders.findById(order.getOrderId()).orElseThrow();
        assertEquals(Order.OrderStatus.REJECTED, rejected.getOrderStatus());
        assertEquals("INSUFFICIENT_HOLDINGS", rejected.getRejectionReason());
        assertFalse(rejected.isSettlementPending());
    }
}
