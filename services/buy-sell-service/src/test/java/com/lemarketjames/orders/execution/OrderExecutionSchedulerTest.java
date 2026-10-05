package com.lemarketjames.orders.execution;

import com.lemarketjames.orders.entity.Order.OrderStatus;
import com.lemarketjames.orders.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * Unit tests for OrderExecutionScheduler.
 * Tests background polling for executable orders and scheduling reliability.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderExecutionScheduler Tests")
class OrderExecutionSchedulerTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderExecutionService executionService;

    @Mock
    private ExecutionSettings settings;

    @InjectMocks
    private OrderExecutionScheduler scheduler;

    private static final Integer ORDER_ID_1 = 101;
    private static final Integer ORDER_ID_2 = 102;
    private static final Integer ORDER_ID_3 = 103;
    private static final long POLL_MS = 1000L;

    @BeforeEach
    void setUp() {
        // No common setup - each test configures what it needs
    }

    // ========== Contract Validation Tests ==========

    @Test
    @DisplayName("Should have poll method with void return type")
    void testMethodSignature() {
        assertDoesNotThrow(() -> {
            var method = OrderExecutionScheduler.class.getDeclaredMethod("poll");
            assertNotNull(method);
            assertEquals(void.class, method.getReturnType());
        });
    }

    // ========== Enabled/Disabled Tests ==========

    @Test
    @DisplayName("Should return early when execution is disabled")
    void testPoll_ExecutionDisabled() {
        when(settings.isEnabled()).thenReturn(false);

        // Act
        scheduler.poll();

        // Assert - should not query for candidates
        verify(orderRepository, never()).findExecutionCandidates(anyList());
        verify(executionService, never()).execute(anyInt(), anyBoolean());
    }

    @Test
    @DisplayName("Should proceed when execution is enabled")
    void testPoll_ExecutionEnabled() {
        when(settings.isEnabled()).thenReturn(true);
        when(orderRepository.findExecutionCandidates(anyList())).thenReturn(List.of());

        // Act
        scheduler.poll();

        // Assert - should query for candidates
        verify(orderRepository).findExecutionCandidates(anyList());
    }

    // ========== Candidate Discovery Tests ==========

    @Test
    @DisplayName("Should query for open order statuses")
    void testPoll_QueriesOpenStatuses() {
        when(settings.isEnabled()).thenReturn(true);
        when(orderRepository.findExecutionCandidates(anyList())).thenReturn(List.of());

        // Act
        scheduler.poll();

        // Assert - verify open statuses are requested
        // Open statuses: SUBMITTED, ACCEPTED, PENDING, DELAYED
        verify(orderRepository).findExecutionCandidates(argThat(statuses ->
                statuses.size() >= 4 &&
                statuses.contains(OrderStatus.SUBMITTED) &&
                statuses.contains(OrderStatus.ACCEPTED) &&
                statuses.contains(OrderStatus.PENDING) &&
                statuses.contains(OrderStatus.DELAYED)
        ));
    }

    @Test
    @DisplayName("Should not query for closed order statuses (FILLED, REJECTED)")
    void testPoll_ExcludesClosedStatuses() {
        when(settings.isEnabled()).thenReturn(true);
        when(orderRepository.findExecutionCandidates(anyList())).thenReturn(List.of());

        // Act
        scheduler.poll();

        // Assert - filled and rejected should not be included
        verify(orderRepository).findExecutionCandidates(argThat(statuses ->
                !statuses.contains(OrderStatus.FILLED) &&
                !statuses.contains(OrderStatus.REJECTED)
        ));
    }

    @Test
    @DisplayName("Should handle empty candidate list")
    void testPoll_NoCandidates() {
        when(settings.isEnabled()).thenReturn(true);
        when(orderRepository.findExecutionCandidates(anyList())).thenReturn(List.of());

        // Act & Assert - should not throw
        assertDoesNotThrow(() -> scheduler.poll());
        verify(executionService, never()).execute(anyInt(), anyBoolean());
    }

    // ========== Execution Tests ==========

    @Test
    @DisplayName("Should execute each candidate with automatic execution (false)")
    void testPoll_ExecutesAllCandidates() {
        when(settings.isEnabled()).thenReturn(true);
        when(orderRepository.findExecutionCandidates(anyList()))
                .thenReturn(List.of(ORDER_ID_1, ORDER_ID_2, ORDER_ID_3));

        // Act
        scheduler.poll();

        // Assert - each order should be executed with manual=false
        verify(executionService).execute(ORDER_ID_1, false);
        verify(executionService).execute(ORDER_ID_2, false);
        verify(executionService).execute(ORDER_ID_3, false);
        verify(executionService, times(3)).execute(anyInt(), eq(false));
    }

    @Test
    @DisplayName("Should execute single candidate order")
    void testPoll_SingleCandidate() {
        when(settings.isEnabled()).thenReturn(true);
        when(orderRepository.findExecutionCandidates(anyList())).thenReturn(List.of(ORDER_ID_1));

        // Act
        scheduler.poll();

        // Assert
        verify(executionService, times(1)).execute(ORDER_ID_1, false);
    }

    @Test
    @DisplayName("Should execute multiple candidates in order")
    void testPoll_MultipleOrderExecution() {
        when(settings.isEnabled()).thenReturn(true);
        when(orderRepository.findExecutionCandidates(anyList()))
                .thenReturn(List.of(ORDER_ID_1, ORDER_ID_2, ORDER_ID_3));

        // Act
        scheduler.poll();

        // Assert - all three should be called
        InOrder inOrder = inOrder(executionService);
        inOrder.verify(executionService).execute(ORDER_ID_1, false);
        inOrder.verify(executionService).execute(ORDER_ID_2, false);
        inOrder.verify(executionService).execute(ORDER_ID_3, false);
    }

    // ========== Error Handling Tests ==========

    @Test
    @DisplayName("Should catch RuntimeException and log, then continue polling")
    void testPoll_CatchesRuntimeException() {
        when(settings.isEnabled()).thenReturn(true);
        when(orderRepository.findExecutionCandidates(anyList()))
                .thenReturn(List.of(ORDER_ID_1, ORDER_ID_2, ORDER_ID_3));
        
        // First order throws, others should still execute
        doThrow(new RuntimeException("Market error"))
                .when(executionService).execute(ORDER_ID_1, false);

        // Act & Assert - should not propagate exception
        assertDoesNotThrow(() -> scheduler.poll());
        
        // ORDER_ID_2 and ORDER_ID_3 should still be executed despite first failure
        verify(executionService).execute(ORDER_ID_2, false);
        verify(executionService).execute(ORDER_ID_3, false);
    }

    @Test
    @DisplayName("Should continue on RestClientException")
    void testPoll_ContinuesOnRestClientException() {
        when(settings.isEnabled()).thenReturn(true);
        when(orderRepository.findExecutionCandidates(anyList()))
                .thenReturn(List.of(ORDER_ID_1, ORDER_ID_2));
        
        doThrow(new RestClientException("Service unavailable"))
                .when(executionService).execute(ORDER_ID_1, false);

        // Act
        assertDoesNotThrow(() -> scheduler.poll());

        // Assert - second order should still execute
        verify(executionService).execute(ORDER_ID_2, false);
    }

    @Test
    @DisplayName("Should handle null from execution service (no NPE)")
    void testPoll_NullCandidateList() {
        when(settings.isEnabled()).thenReturn(true);
        when(orderRepository.findExecutionCandidates(anyList())).thenReturn(null);

        // Act & Assert - should throw or handle gracefully
        // Depends on implementation, but should not silently fail
        assertThrows(Exception.class, () -> scheduler.poll());
    }

    // ========== Idempotency Tests ==========

    @Test
    @DisplayName("Should poll multiple times without state carryover")
    void testPoll_IdempotentMultiplePollCycles() {
        when(settings.isEnabled()).thenReturn(true);
        when(orderRepository.findExecutionCandidates(anyList()))
                .thenReturn(List.of(ORDER_ID_1));

        // Act - poll twice
        scheduler.poll();
        scheduler.poll();

        // Assert - should execute same order twice (no state carryover)
        verify(executionService, times(2)).execute(ORDER_ID_1, false);
    }

    @Test
    @DisplayName("Should poll with different candidates each time")
    void testPoll_DifferentCandidatesPerCycle() {
        when(settings.isEnabled()).thenReturn(true);
        when(orderRepository.findExecutionCandidates(anyList()))
                .thenReturn(List.of(ORDER_ID_1))
                .thenReturn(List.of(ORDER_ID_2));

        // Act - poll twice
        scheduler.poll();
        scheduler.poll();

        // Assert - should execute different orders
        verify(executionService).execute(ORDER_ID_1, false);
        verify(executionService).execute(ORDER_ID_2, false);
    }

    // ========== Settings Integration Tests ==========

    @Test
    @DisplayName("Should use settings.isEnabled() to gate execution")
    void testPoll_RespectSettingsEnabled() {
        when(settings.isEnabled()).thenReturn(true);
        when(orderRepository.findExecutionCandidates(anyList())).thenReturn(List.of());

        // Act
        scheduler.poll();

        // Assert - should check settings.isEnabled()
        verify(settings).isEnabled();
    }

    @Test
    @DisplayName("Should not call settings.getPollMs() during poll")
    void testPoll_PollMsNotCalledDuringExecution() {
        when(settings.isEnabled()).thenReturn(true);
        when(orderRepository.findExecutionCandidates(anyList())).thenReturn(List.of());

        // Act
        scheduler.poll();

        // Assert - getPollMs is only used during scheduling setup, not during poll
        // (It's used by @Scheduled annotation, not in the poll() method itself)
        verify(settings, never()).getPollMs();
    }
}
