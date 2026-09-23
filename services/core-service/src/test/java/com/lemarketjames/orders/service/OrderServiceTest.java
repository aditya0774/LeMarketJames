package com.lemarketjames.orders.service;

import com.lemarketjames.common.domain.AccountRepository;
import com.lemarketjames.orders.dto.CreateOrderRequest;
import com.lemarketjames.orders.dto.OrderResponse;
import com.lemarketjames.orders.entity.Instrument;
import com.lemarketjames.orders.entity.Order;
import com.lemarketjames.orders.exception.NotTradableException;
import com.lemarketjames.orders.repository.InstrumentRepository;
import com.lemarketjames.orders.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Order Service Unit Tests")
class OrderServiceTest {
    
    @Mock
    private OrderRepository orderRepository;

    @Mock
    private InstrumentRepository instrumentRepository;

    @Mock
    private AccountRepository accountRepository;
    
    @Mock
    private CashValidationService cashValidationService;
    
    private OrderService orderService;
    
    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, instrumentRepository, accountRepository, cashValidationService);
        // Mock cash validation to pass by default (sufficient balance)
        // Use lenient() to avoid "UnnecessaryStubbingException" for tests that don't use cash validation
        lenient().when(cashValidationService.validateSufficientCash(any(Integer.class), any())).thenReturn(true);
        SecurityContextHolder.getContext()
            .setAuthentication(new TestingAuthenticationToken("testuser", "n/a", "ROLE_USER"));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }
    
    @Test
    @DisplayName("Create order successfully")
    void testCreateOrder() {
        // Arrange
        CreateOrderRequest request = new CreateOrderRequest(
            1, 1, Order.OrderType.BUY, new BigDecimal("10")
        );
        request.setPricePerUnit(new BigDecimal("227.55"));
        
        Order savedOrder = new Order(1, 1, Order.OrderType.BUY, new BigDecimal("10"));
        savedOrder.setOrderId(1);
        savedOrder.setPricePerUnit(new BigDecimal("227.55"));

        Instrument instrument = new Instrument();
        instrument.setInstrumentId(1);
        instrument.setTradable(true);
        
        when(accountRepository.existsByAccountIdAndUsername(1, "testuser")).thenReturn(true);
        when(instrumentRepository.findById(1)).thenReturn(Optional.of(instrument));
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);
        
        // Act
        OrderResponse response = orderService.createOrder(request);
        
        // Assert
        assertNotNull(response);
        assertEquals(1, response.getOrderId());
        assertEquals(Order.OrderType.BUY, response.getOrderType());
        assertEquals(new BigDecimal("10"), response.getQuantity());
        assertEquals(new BigDecimal("227.55"), response.getPricePerUnit());
    }

    @Test
    @DisplayName("Create order throws exception when instrument is non-tradable")
    void testCreateOrderNonTradableInstrument() {
        // Arrange
        CreateOrderRequest request = new CreateOrderRequest(
            1, 1, Order.OrderType.BUY, new BigDecimal("10")
        );

        Instrument instrument = new Instrument();
        instrument.setInstrumentId(1);
        instrument.setTradable(false);

        when(accountRepository.existsByAccountIdAndUsername(1, "testuser")).thenReturn(true);
        when(instrumentRepository.findById(1)).thenReturn(Optional.of(instrument));

        // Act & Assert
        assertThrows(NotTradableException.class, () -> orderService.createOrder(request));
    }

    @Test
    @DisplayName("Create order throws exception when instrument is missing")
    void testCreateOrderMissingInstrument() {
        // Arrange
        CreateOrderRequest request = new CreateOrderRequest(
            1, 999, Order.OrderType.BUY, new BigDecimal("10")
        );

        when(accountRepository.existsByAccountIdAndUsername(1, "testuser")).thenReturn(true);
        when(instrumentRepository.findById(999)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> orderService.createOrder(request));
    }

    @Test
    @DisplayName("Create order throws access denied when account does not belong to authenticated user")
    void testCreateOrderAccountAccessDenied() {
        CreateOrderRequest request = new CreateOrderRequest(
            99, 1, Order.OrderType.BUY, new BigDecimal("10")
        );

        when(accountRepository.existsByAccountIdAndUsername(99, "testuser")).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> orderService.createOrder(request));
    }
    
    @Test
    @DisplayName("Get order by ID")
    void testGetOrderById() {
        when(accountRepository.existsByAccountIdAndUsername(1, "testuser")).thenReturn(true);
        // Arrange
        Order order = new Order(1, 1, Order.OrderType.BUY, new BigDecimal("10"));
        order.setOrderId(1);
        
        when(orderRepository.findById(1)).thenReturn(Optional.of(order));
        
        // Act
        OrderResponse response = orderService.getOrderById(1);
        
        // Assert
        assertNotNull(response);
        assertEquals(1, response.getOrderId());
        assertEquals(Order.OrderType.BUY, response.getOrderType());
    }
    
    @Test
    @DisplayName("Get order by ID throws exception when not found")
    void testGetOrderByIdNotFound() {
        // Arrange
        when(orderRepository.findById(999)).thenReturn(Optional.empty());
        
        // Act & Assert
        assertThrows(AccessDeniedException.class, () -> {
            orderService.getOrderById(999);
        });
    }
    
    @Test
    @DisplayName("Get orders by account ID")
    void testGetOrdersByAccountId() {
        when(accountRepository.existsByAccountIdAndUsername(1, "testuser")).thenReturn(true);
        // Arrange
        Order order1 = new Order(1, 1, Order.OrderType.BUY, new BigDecimal("10"));
        order1.setOrderId(1);
        Order order2 = new Order(1, 2, Order.OrderType.SELL, new BigDecimal("5"));
        order2.setOrderId(2);
        
        List<Order> orders = new ArrayList<>();
        orders.add(order1);
        orders.add(order2);
        
        when(orderRepository.findByAccountId(1)).thenReturn(orders);
        
        // Act
        List<OrderResponse> responses = orderService.getOrdersByAccountId(1);
        
        // Assert
        assertEquals(2, responses.size());
        assertEquals(1, responses.get(0).getOrderId());
        assertEquals(2, responses.get(1).getOrderId());
    }
    
    @Test
    @DisplayName("Update order status")
    void testUpdateOrderStatus() {
        when(accountRepository.existsByAccountIdAndUsername(1, "testuser")).thenReturn(true);
        // Arrange
        Order order = new Order(1, 1, Order.OrderType.BUY, new BigDecimal("10"));
        order.setOrderId(1);
        order.setOrderStatus(Order.OrderStatus.SUBMITTED);
        
        Order updatedOrder = new Order(1, 1, Order.OrderType.BUY, new BigDecimal("10"));
        updatedOrder.setOrderId(1);
        updatedOrder.setOrderStatus(Order.OrderStatus.ACCEPTED);
        
        when(orderRepository.findById(1)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenReturn(updatedOrder);
        
        // Act
        OrderResponse response = orderService.updateOrderStatus(1, Order.OrderStatus.ACCEPTED);
        
        // Assert
        assertEquals(Order.OrderStatus.ACCEPTED, response.getOrderStatus());
    }
    
    @Test
    @DisplayName("Reject order with reason")
    void testRejectOrder() {
        when(accountRepository.existsByAccountIdAndUsername(1, "testuser")).thenReturn(true);
        // Arrange
        Order order = new Order(1, 1, Order.OrderType.BUY, new BigDecimal("10"));
        order.setOrderId(1);
        
        Order rejectedOrder = new Order(1, 1, Order.OrderType.BUY, new BigDecimal("10"));
        rejectedOrder.setOrderId(1);
        rejectedOrder.setOrderStatus(Order.OrderStatus.REJECTED);
        rejectedOrder.setRejectionReason("Insufficient funds");
        
        when(orderRepository.findById(1)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenReturn(rejectedOrder);
        
        // Act
        OrderResponse response = orderService.rejectOrder(1, "Insufficient funds");
        
        // Assert
        assertEquals(Order.OrderStatus.REJECTED, response.getOrderStatus());
        assertEquals("Insufficient funds", response.getRejectionReason());
    }
    
    @Test
    @DisplayName("Get orders by account and status")
    void testGetOrdersByAccountAndStatus() {
        when(accountRepository.existsByAccountIdAndUsername(1, "testuser")).thenReturn(true);
        // Arrange
        Order submittedOrder = new Order(1, 1, Order.OrderType.BUY, new BigDecimal("10"));
        submittedOrder.setOrderId(1);
        submittedOrder.setOrderStatus(Order.OrderStatus.SUBMITTED);
        
        List<Order> orders = new ArrayList<>();
        orders.add(submittedOrder);
        
        when(orderRepository.findByAccountIdAndOrderStatus(1, Order.OrderStatus.SUBMITTED))
            .thenReturn(orders);
        
        // Act
        List<OrderResponse> responses = orderService.getOrdersByAccountAndStatus(1, Order.OrderStatus.SUBMITTED);
        
        // Assert
        assertEquals(1, responses.size());
        assertEquals(Order.OrderStatus.SUBMITTED, responses.get(0).getOrderStatus());
    }
}
