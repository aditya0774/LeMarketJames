package com.lemarketjames.orders.execution;

import com.lemarketjames.holdings.client.HoldingsSettlementClient;
import com.lemarketjames.holdings.client.SettlementRejectedException;
import com.lemarketjames.orders.entity.Order;
import com.lemarketjames.orders.entity.RejectionReason;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderExecutionService Tests")
class OrderExecutionServiceTest {

    @Mock
    private OrderExecutionTransactions transactions;

    @Mock
    private HoldingsSettlementClient settlement;

    @InjectMocks
    private OrderExecutionService service;

    @Test
    @DisplayName("Should return when prepare has no executable intent")
    void execute_NoIntent_ReturnsEarly() {
        int id = 101;
        when(transactions.prepare(id, false)).thenReturn(null);

        service.execute(id, false);

        verify(settlement, never()).settle(any());
        verify(transactions, never()).finish(anyInt(), any());
    }

    @Test
    @DisplayName("Should finish filled when settlement succeeds")
    void execute_SettlementSucceeds_FinishesFilled() {
        int id = 102;
        Order intent = new Order(1, 1, Order.OrderType.BUY, BigDecimal.ONE);
        when(transactions.prepare(id, false)).thenReturn(intent);

        service.execute(id, false);

        verify(settlement).settle(intent);
        verify(transactions).finish(id, null);
    }

    @Test
    @DisplayName("Should finish rejected on definitive settlement refusal")
    void execute_DefinitiveRefusal_FinishesRejected() {
        int id = 103;
        Order intent = new Order(1, 1, Order.OrderType.SELL, BigDecimal.ONE);
        when(transactions.prepare(id, false)).thenReturn(intent);
        doThrow(new SettlementRejectedException(RejectionReason.INSUFFICIENT_HOLDINGS))
            .when(settlement).settle(intent);

        service.execute(id, false);

        verify(transactions).finish(id, RejectionReason.INSUFFICIENT_HOLDINGS);
    }

    @Test
    @DisplayName("Should leave intent pending on unknown failure for retry")
    void execute_UnknownFailure_LeavesPendingForRetry() {
        int id = 104;
        Order intent = new Order(1, 1, Order.OrderType.BUY, BigDecimal.ONE);
        when(transactions.prepare(id, false)).thenReturn(intent);
        doThrow(new IllegalStateException("network timeout")).when(settlement).settle(intent);

        assertThrows(IllegalStateException.class, () -> service.execute(id, false));

        verify(transactions, never()).finish(eq(id), any());
    }
}
