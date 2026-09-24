package com.lemarketjames.orders.service;

import com.lemarketjames.market.service.MarketDataService;
import com.lemarketjames.market.model.QuoteSnapshot;
import com.lemarketjames.common.domain.AccountRepository;
import com.lemarketjames.holdings.client.HoldingsSettlementClient;
import com.lemarketjames.holdings.client.HoldingsValidationClient;
import com.lemarketjames.orders.dto.CreateOrderRequest;
import com.lemarketjames.orders.dto.OrderResponse;
import com.lemarketjames.orders.dto.SubmitBuyOrderRequest;
import com.lemarketjames.orders.entity.Instrument;
import com.lemarketjames.orders.entity.Order;
import com.lemarketjames.orders.exception.InsufficientHoldingsException;
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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

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

    @Mock
    private MarketDataService marketDataService;
    
    @Mock
    private HoldingsSettlementClient holdingsSettlementClient;

    @Mock
    private HoldingsValidationClient holdingsValidationClient;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, instrumentRepository, accountRepository, cashValidationService, marketDataService, holdingsSettlementClient, holdingsValidationClient);
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
    void sellValidatesHoldingsBeforeSavingAndDoesNotCheckCash() {
        var request = validSell();
        when(orderRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        var response = orderService.createOrder(request);
        var sequence = inOrder(holdingsValidationClient, orderRepository);
        sequence.verify(holdingsValidationClient).validateSufficientHoldings(1, "testuser", 1, BigDecimal.ONE);
        sequence.verify(orderRepository).save(any());
        verifyNoInteractions(cashValidationService);
        assertTrue(response.isSuccess());
        assertEquals(Order.OrderType.SELL, response.getOrderType());
    }

    @Test
    void insufficientHoldingsNeverSavesSell() {
        var request = validSell();
        doThrow(new InsufficientHoldingsException("Insufficient holdings"))
            .when(holdingsValidationClient).validateSufficientHoldings(1, "testuser", 1, BigDecimal.ONE);
        assertThrows(InsufficientHoldingsException.class, () -> orderService.createOrder(request));
        verifyNoInteractions(orderRepository, cashValidationService);
    }

    private CreateOrderRequest validSell() {
        when(accountRepository.existsByAccountIdAndUsername(1, "testuser")).thenReturn(true);
        Instrument instrument = new Instrument();
        instrument.setTradable(true);
        when(instrumentRepository.findById(1)).thenReturn(Optional.of(instrument));
        return new CreateOrderRequest(1, 1, Order.OrderType.SELL, BigDecimal.ONE);
    }
    
    @Test
    @DisplayName("Submit buy order always persists BUY type")
    void testSubmitBuyOrderAlwaysUsesBuyType() {
        SubmitBuyOrderRequest request = new SubmitBuyOrderRequest(
            1,
            1,
            new BigDecimal("3.0000"),
            new BigDecimal("100.50")
        );

        Instrument instrument = new Instrument();
        instrument.setInstrumentId(1);
        instrument.setTradable(true);

        Order savedOrder = new Order(1, 1, Order.OrderType.BUY, new BigDecimal("3.0000"));
        savedOrder.setOrderId(77);
        savedOrder.setPricePerUnit(new BigDecimal("100.50"));

        // The dedicated entrypoint also uses the server's market price for BUY orders.
        when(marketDataService.findByInstrumentId(1)).thenReturn(Optional.of(quote(100.50)));

        when(accountRepository.existsByAccountIdAndUsername(1, "testuser")).thenReturn(true);
        when(instrumentRepository.findById(1)).thenReturn(Optional.of(instrument));
        when(orderRepository.save(argThat(order ->
            order.getOrderType() == Order.OrderType.BUY
                && order.getAccountId().equals(1)
                && order.getInstrumentId().equals(1)
        ))).thenReturn(savedOrder);

        OrderResponse response = orderService.submitBuyOrder(request);

        assertNotNull(response);
        assertEquals(77, response.getOrderId());
        assertEquals(Order.OrderType.BUY, response.getOrderType());
        assertEquals(new BigDecimal("100.50"), response.getPricePerUnit());
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
        when(marketDataService.findByInstrumentId(1)).thenReturn(Optional.of(quote(227.55)));
        
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
        verifyNoInteractions(cashValidationService, marketDataService, orderRepository);
    }

    private QuoteSnapshot quote(double ask) {
        return new QuoteSnapshot(null, ask, ask, ask, ask, ask, ask, ask, 0, null, null);
    }

    private CreateOrderRequest validBuy() {
        when(accountRepository.existsByAccountIdAndUsername(1, "testuser")).thenReturn(true);
        Instrument instrument = new Instrument();
        instrument.setTradable(true);
        when(instrumentRepository.findById(1)).thenReturn(Optional.of(instrument));
        return new CreateOrderRequest(1, 1, Order.OrderType.BUY, new BigDecimal("2"));
    }

    @Test
    void buyUsesMarketPriceEvenWhenClientSuppliesCheaperPrice() {
        var request = validBuy();
        request.setPricePerUnit(new BigDecimal("0.01"));
        when(marketDataService.findByInstrumentId(1)).thenReturn(Optional.of(quote(250.12345)));
        when(orderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = orderService.createOrder(request);

        assertTrue(response.isSuccess());
        assertEquals(new BigDecimal("250.1235"), response.getPricePerUnit());
        verify(cashValidationService).validateSufficientCash(1, new BigDecimal("500.2470"));
    }

    @Test
    void buyWithoutClientPriceStillChecksCashAndDoesNotSaveWhenInsufficient() {
        var request = validBuy();
        when(marketDataService.findByInstrumentId(1)).thenReturn(Optional.of(quote(250)));
        when(cashValidationService.validateSufficientCash(1, new BigDecimal("500.0000"))).thenReturn(false);
        when(cashValidationService.getCashBalance(1)).thenReturn(BigDecimal.ONE);

        var response = orderService.createOrder(request);

        assertFalse(response.isSuccess());
        assertEquals("INSUFFICIENT_CASH", response.getCode());
        verifyNoInteractions(orderRepository);
    }

    @Test
    void unavailableMarketPriceDoesNotSaveOrCheckCash() {
        var request = validBuy();
        when(marketDataService.findByInstrumentId(1)).thenReturn(Optional.empty());
        assertEquals("PRICE_UNAVAILABLE", orderService.createOrder(request).getCode());
        verifyNoInteractions(orderRepository, cashValidationService);
    }

    @Test
    void invalidMarketPricesDoNotSave() {
        var request = validBuy();
        for (double price : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY, 0.000001}) {
            when(marketDataService.findByInstrumentId(1)).thenReturn(Optional.of(quote(price)));
            assertEquals("PRICE_UNAVAILABLE", orderService.createOrder(request).getCode());
        }
        verifyNoInteractions(orderRepository, cashValidationService);
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
    @DisplayName("Filling an order settles it via holdings-service and stamps filledAt")
    void testUpdateOrderStatusToFilledSettles() {
        when(accountRepository.existsByAccountIdAndUsername(1, "testuser")).thenReturn(true);
        Order order = new Order(1, 1, Order.OrderType.BUY, new BigDecimal("10"));
        order.setOrderId(1);
        order.setOrderStatus(Order.OrderStatus.ACCEPTED);
        order.setPricePerUnit(new BigDecimal("227.55"));

        when(orderRepository.findById(1)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.updateOrderStatus(1, Order.OrderStatus.FILLED);

        assertEquals(Order.OrderStatus.FILLED, response.getOrderStatus());
        assertNotNull(response.getFilledAt());
        org.mockito.Mockito.verify(holdingsSettlementClient).settle(order);
    }

    @Test
    @DisplayName("Filling an order with no price is rejected and never settled")
    void testUpdateOrderStatusToFilledWithoutPriceRejected() {
        when(accountRepository.existsByAccountIdAndUsername(1, "testuser")).thenReturn(true);
        Order order = new Order(1, 1, Order.OrderType.BUY, new BigDecimal("10"));
        order.setOrderId(1);
        order.setOrderStatus(Order.OrderStatus.ACCEPTED);

        when(orderRepository.findById(1)).thenReturn(Optional.of(order));

        assertThrows(IllegalArgumentException.class, () -> orderService.updateOrderStatus(1, Order.OrderStatus.FILLED));
        org.mockito.Mockito.verify(holdingsSettlementClient, org.mockito.Mockito.never()).settle(any(Order.class));
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
