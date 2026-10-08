package com.lemarketjames.orders.service;

import com.lemarketjames.market.service.MarketDataService;
import com.lemarketjames.market.model.QuoteSnapshot;
import com.lemarketjames.common.audit.AuditEventType;
import com.lemarketjames.common.audit.AuditRecorder;
import com.lemarketjames.common.audit.AuditEventEntity;
import com.lemarketjames.common.audit.AuditEventRepository;
import com.lemarketjames.common.audit.SubmissionAuditEvent;
import com.lemarketjames.orders.submission.SubmissionRecorder;
import org.mockito.ArgumentCaptor;
import com.lemarketjames.common.domain.AccountEntity;
import com.lemarketjames.common.domain.AccountRepository;
import com.lemarketjames.common.domain.AccountStatus;
import com.lemarketjames.common.domain.ClientEntity;
import com.lemarketjames.common.domain.ClientRepository;
import com.lemarketjames.orders.entity.RejectionReason;
import com.lemarketjames.orders.events.OrderStatusChanged;
import com.lemarketjames.orders.events.OrderSubmitted;
import com.lemarketjames.orders.exception.InvalidStatusTransitionException;
import org.springframework.context.ApplicationEventPublisher;
import java.util.Map;
import com.lemarketjames.holdings.client.HoldingsSettlementClient;
import com.lemarketjames.holdings.client.HoldingsValidationClient;
import com.lemarketjames.orders.dto.AuditEventDto;
import com.lemarketjames.orders.dto.CreateOrderRequest;
import com.lemarketjames.orders.dto.OrderResponse;
import com.lemarketjames.orders.dto.SubmitBuyOrderRequest;
import com.lemarketjames.common.instruments.Instrument;
import com.lemarketjames.orders.entity.Order;
import com.lemarketjames.orders.exception.InsufficientHoldingsException;
import com.lemarketjames.orders.exception.NotTradableException;
import com.lemarketjames.common.instruments.InstrumentRepository;
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

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private AuditRecorder auditRecorder;

    @Mock
    private AuditEventRepository auditEventRepository;

    @Mock
    private ApplicationEventPublisher events;

    @Mock private TradingRestrictions restrictions;
    private OrderService orderService;

    private static final String REQUEST_ID = "req-1";

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(orderRepository.findLockedById(any())).thenAnswer(inv -> orderRepository.findById(inv.getArgument(0)));
        // The real checks and the real recorder run against the mocks below, so these tests cover
        // placement end to end, down to the audit events handed to AuditRecorder.
        AccountAccess accountAccess = new AccountAccess(accountRepository, clientRepository);
        var validator = new com.lemarketjames.orders.submission.SubmissionValidator(accountAccess, accountRepository,
            clientRepository, instrumentRepository, restrictions, holdingsValidationClient, marketDataService,
            cashValidationService);
        orderService = new OrderService(orderRepository, accountAccess, validator,
            new SubmissionRecorder(orderRepository, auditRecorder, events),
            new com.lemarketjames.orders.execution.OrderTransitions(auditRecorder, events),
            auditEventRepository);
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
        var response = orderService.createOrder(request, REQUEST_ID);
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
        assertThrows(InsufficientHoldingsException.class, () -> orderService.createOrder(request, REQUEST_ID));
        verifyNoInteractions(orderRepository, cashValidationService);
    }

    // ---- LMKT-99: the audit trail each kind of submission leaves (contract C2) ----

    /** The events handed to the audit trail, as TYPE or TYPE:RULE:RESULT[:reason]. */
    private List<String> auditedSteps() {
        return audited().stream().map(event -> event.rule() == null ? event.type().name()
            : event.type() + ":" + event.rule() + ":" + event.details().get("result")
                + (event.details().containsKey("reason") ? ":" + event.details().get("reason") : "")).toList();
    }

    private List<SubmissionAuditEvent> audited() {
        ArgumentCaptor<SubmissionAuditEvent> events = ArgumentCaptor.forClass(SubmissionAuditEvent.class);
        verify(auditRecorder, atLeast(0)).recordSubmission(events.capture());
        return events.getAllValues();
    }

    private void callerIsClient(int clientId) {
        ClientEntity client = mock(ClientEntity.class);
        when(client.getClientId()).thenReturn(clientId);
        when(clientRepository.findByUsername("testuser")).thenReturn(Optional.of(client));
    }

    @Test
    @DisplayName("AC1+AC2: an accepted order is saved with SUBMITTED, a pass per rule, then VALIDATED")
    void acceptedOrderIsSavedWithItsWholeTrail() {
        var request = validSell();
        callerIsClient(42);
        Order saved = new Order(1, 1, Order.OrderType.SELL, BigDecimal.ONE);
        saved.setOrderId(77);
        when(orderRepository.save(any())).thenReturn(saved);

        assertTrue(orderService.createOrder(request, REQUEST_ID).isSuccess());

        assertEquals(List.of("SUBMITTED", "RULE_CHECKED:ACCOUNT_ACCESS:PASS", "RULE_CHECKED:ACCOUNT_STATUS:PASS",
            "RULE_CHECKED:LOCATION:PASS", "RULE_CHECKED:TRADABLE:PASS", "RULE_CHECKED:HOLDINGS:PASS", "VALIDATED"),
            auditedSteps());
        // Every event names the order, the caller's client and account, and the one request.
        audited().forEach(event -> {
            assertEquals(77, event.orderId());
            assertEquals(42, event.clientId());
            assertEquals(1, event.accountId());
            assertEquals(REQUEST_ID, event.requestId());
        });
        var submitted = audited().get(0).details();
        assertEquals("SELL", submitted.get("side"));
        assertEquals(BigDecimal.ONE, submitted.get("quantity"));
        assertEquals(1, submitted.get("instrumentId"));
        assertNull(submitted.get("price"));
        assertFalse(submitted.containsKey("requestedAccountId"));
        // VALIDATED lists the rules that actually ran, not a fixed list.
        assertEquals(List.of("ACCOUNT_ACCESS", "ACCOUNT_STATUS", "LOCATION", "TRADABLE", "HOLDINGS"),
            audited().get(6).details().get("checks"));
        // The order is saved before its events, so they can carry its ID.
        var sequence = inOrder(orderRepository, auditRecorder);
        sequence.verify(orderRepository).save(any());
        sequence.verify(auditRecorder, times(7)).recordSubmission(any());
        // The new order is announced once, with what was ordered (contract C6).
        verify(events).publishEvent(argThat((Object e) -> e instanceof OrderSubmitted announced
            && announced.orderId() == 77 && announced.accountId() == 1 && announced.instrumentId() == 1
            && announced.side() == Order.OrderType.SELL && announced.quantity().equals(BigDecimal.ONE)
            && announced.price() == null && announced.submittedAt() != null));
    }

    @Test
    @DisplayName("AC2: an order refused with a response saves no order but leaves its trail, ending on the failure")
    void orderRefusedByResponseLeavesItsTrailWithoutAnOrder() {
        var request = validBuy();
        callerIsClient(42);
        when(marketDataService.findByInstrumentId(1)).thenReturn(Optional.of(quote(250)));
        when(cashValidationService.validateSufficientCash(1, new BigDecimal("500.0000"))).thenReturn(false);
        when(cashValidationService.getCashBalance(1)).thenReturn(BigDecimal.ONE);

        assertEquals("INSUFFICIENT_CASH", orderService.createOrder(request, REQUEST_ID).getCode());

        assertEquals(List.of("SUBMITTED", "RULE_CHECKED:ACCOUNT_ACCESS:PASS", "RULE_CHECKED:ACCOUNT_STATUS:PASS",
            "RULE_CHECKED:LOCATION:PASS", "RULE_CHECKED:TRADABLE:PASS", "RULE_CHECKED:PRICE_AVAILABLE:PASS",
            "RULE_CHECKED:QUOTE_FRESH:PASS", "RULE_CHECKED:CASH:FAIL:INSUFFICIENT_CASH"), auditedSteps());
        audited().forEach(event -> {
            assertNull(event.orderId(), "a refused order has no order ID");
            assertEquals(42, event.clientId());
            assertEquals(REQUEST_ID, event.requestId());
        });
        assertEquals(new BigDecimal("250.0000"), audited().get(0).details().get("price"));
        // No order was saved, so there is none to announce.
        verifyNoInteractions(orderRepository, events);
    }

    @Test
    @DisplayName("AC2: an order refused by an exception leaves its trail before the exception reaches the caller")
    void orderRefusedByExceptionLeavesItsTrail() {
        when(accountRepository.existsByAccountIdAndUsername(1, "testuser")).thenReturn(true);
        Instrument suspended = new Instrument();
        suspended.setTradable(false);
        when(instrumentRepository.findById(1)).thenReturn(Optional.of(suspended));

        assertThrows(NotTradableException.class, () -> orderService.createOrder(
            new CreateOrderRequest(1, 1, Order.OrderType.BUY, BigDecimal.ONE), REQUEST_ID));

        assertEquals(List.of("SUBMITTED", "RULE_CHECKED:ACCOUNT_ACCESS:PASS", "RULE_CHECKED:ACCOUNT_STATUS:PASS",
            "RULE_CHECKED:LOCATION:PASS", "RULE_CHECKED:TRADABLE:FAIL:NOT_TRADABLE"), auditedSteps());
        verifyNoInteractions(orderRepository);
    }

    @Test
    @DisplayName("AC2: refused account access is audited against the caller, never the account they asked for")
    void refusedAccountAccessIsAuditedAgainstTheCaller() {
        callerIsClient(42);
        when(accountRepository.existsByAccountIdAndUsername(99, "testuser")).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> orderService.createOrder(
            new CreateOrderRequest(99, 1, Order.OrderType.BUY, BigDecimal.ONE), REQUEST_ID));

        assertEquals(List.of("SUBMITTED", "RULE_CHECKED:ACCOUNT_ACCESS:FAIL:ACCOUNT_ACCESS_DENIED"), auditedSteps());
        audited().forEach(event -> {
            assertEquals(42, event.clientId());
            assertNull(event.accountId());
        });
        assertEquals(99, audited().get(0).details().get("requestedAccountId"));
    }

    @Test
    @DisplayName("A check that can't complete is audited as ERROR and its exception still reaches the caller")
    void checkThatCannotCompleteIsAuditedAsError() {
        var request = validSell();
        var unreachable = new IllegalStateException("holdings-service unreachable");
        doThrow(unreachable).when(holdingsValidationClient)
            .validateSufficientHoldings(1, "testuser", 1, BigDecimal.ONE);

        assertSame(unreachable, assertThrows(IllegalStateException.class,
            () -> orderService.createOrder(request, REQUEST_ID)));

        assertEquals("RULE_CHECKED:HOLDINGS:ERROR", auditedSteps().get(5));
        assertEquals(6, auditedSteps().size());
        verifyNoInteractions(orderRepository);
    }

    @Test
    @DisplayName("A refusal that can't be recorded is not reported as a refusal")
    void refusalThatCannotBeAuditedSurfacesTheAuditFailure() {
        var request = validSell();
        var refusal = new InsufficientHoldingsException("Insufficient holdings");
        doThrow(refusal).when(holdingsValidationClient)
            .validateSufficientHoldings(1, "testuser", 1, BigDecimal.ONE);
        var auditDown = new IllegalStateException("audit store unavailable");
        doThrow(auditDown).when(auditRecorder).recordSubmission(any());

        var thrown = assertThrows(IllegalStateException.class, () -> orderService.createOrder(request, REQUEST_ID));

        assertSame(auditDown, thrown);
        assertArrayEquals(new Throwable[]{refusal}, thrown.getSuppressed());
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

        OrderResponse response = orderService.submitBuyOrder(request, REQUEST_ID);

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
        OrderResponse response = orderService.createOrder(request, REQUEST_ID);
        
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
        assertThrows(NotTradableException.class, () -> orderService.createOrder(request, REQUEST_ID));
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
        assertThrows(IllegalArgumentException.class, () -> orderService.createOrder(request, REQUEST_ID));
    }

    @Test
    @DisplayName("Create order throws access denied when account does not belong to authenticated user")
    void testCreateOrderAccountAccessDenied() {
        CreateOrderRequest request = new CreateOrderRequest(
            99, 1, Order.OrderType.BUY, new BigDecimal("10")
        );

        when(accountRepository.existsByAccountIdAndUsername(99, "testuser")).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> orderService.createOrder(request, REQUEST_ID));
        verifyNoInteractions(cashValidationService, marketDataService, orderRepository);
    }

    private QuoteSnapshot quote(double ask) {
        return new QuoteSnapshot(null, ask, ask, ask, ask, ask, ask, ask, 0, java.time.Instant.now(), null);
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

        var response = orderService.createOrder(request, REQUEST_ID);

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

        var response = orderService.createOrder(request, REQUEST_ID);

        assertFalse(response.isSuccess());
        assertEquals("INSUFFICIENT_CASH", response.getCode());
        verifyNoInteractions(orderRepository);
    }

    @Test
    void unavailableMarketPriceDoesNotSaveOrCheckCash() {
        var request = validBuy();
        when(marketDataService.findByInstrumentId(1)).thenReturn(Optional.empty());
        assertEquals("PRICE_UNAVAILABLE", orderService.createOrder(request, REQUEST_ID).getCode());
        verifyNoInteractions(orderRepository, cashValidationService);
    }

    @Test
    void invalidMarketPricesDoNotSave() {
        var request = validBuy();
        for (double price : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY, 0.000001}) {
            when(marketDataService.findByInstrumentId(1)).thenReturn(Optional.of(quote(price)));
            assertEquals("PRICE_UNAVAILABLE", orderService.createOrder(request, REQUEST_ID).getCode());
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
    @DisplayName("Reject order with a reason code, audited and published")
    void testRejectOrder() {
        when(accountRepository.existsByAccountIdAndUsername(1, "testuser")).thenReturn(true);
        // Arrange
        Order order = new Order(1, 1, Order.OrderType.BUY, new BigDecimal("10"));
        order.setOrderId(1);

        when(orderRepository.findById(1)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        OrderResponse response = orderService.rejectOrder(1, RejectionReason.INSUFFICIENT_CASH);

        // Assert
        assertEquals(Order.OrderStatus.REJECTED, response.getOrderStatus());
        assertEquals("INSUFFICIENT_CASH", response.getRejectionReason());
        verify(auditRecorder).record(AuditEventType.REJECTED, 1, 1, Map.of("reason", "INSUFFICIENT_CASH"));
        verify(events).publishEvent(argThat((Object e) -> e instanceof OrderStatusChanged changed
            && changed.from() == Order.OrderStatus.SUBMITTED && changed.to() == Order.OrderStatus.REJECTED));
    }

    @Test
    @DisplayName("A move the lifecycle doesn't allow is refused before anything is saved or settled")
    void illegalTransitionIsRefused() {
        when(accountRepository.existsByAccountIdAndUsername(1, "testuser")).thenReturn(true);
        Order order = new Order(1, 1, Order.OrderType.BUY, new BigDecimal("10"));
        order.setOrderId(1);
        order.setPricePerUnit(new BigDecimal("100"));
        when(orderRepository.findById(1)).thenReturn(Optional.of(order));

        // SUBMITTED cannot skip acceptance and jump straight to PENDING.
        assertThrows(InvalidStatusTransitionException.class,
            () -> orderService.updateOrderStatus(1, Order.OrderStatus.PENDING));
        verify(holdingsSettlementClient, never()).settle(any(Order.class));
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("Trading operations staff can act on any client's order")
    void tradingOpsBypassesOwnership() {
        SecurityContextHolder.getContext()
            .setAuthentication(new TestingAuthenticationToken("olivia_ops", "n/a", "ROLE_TRADING_OPS"));
        Order order = new Order(7, 1, Order.OrderType.BUY, new BigDecimal("10"));
        order.setOrderId(1);
        when(orderRepository.findById(1)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.updateOrderStatus(1, Order.OrderStatus.ACCEPTED);

        assertEquals(Order.OrderStatus.ACCEPTED, response.getOrderStatus());
        verify(accountRepository, never()).existsByAccountIdAndUsername(any(), any());
    }

    @Test
    @DisplayName("An account with trading disabled can't place orders")
    void restrictedAccountIsRefused() {
        when(accountRepository.existsByAccountIdAndUsername(1, "testuser")).thenReturn(true);
        AccountEntity account = new AccountEntity();
        account.setClientId(3);
        account.setTradingEnabled(false);
        when(accountRepository.findById(1)).thenReturn(Optional.of(account));

        OrderResponse response = orderService.createOrder(
            new CreateOrderRequest(1, 1, Order.OrderType.BUY, new BigDecimal("1")), REQUEST_ID);

        assertFalse(response.isSuccess());
        assertEquals("ACCOUNT_RESTRICTED", response.getCode());
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("An EXPIRED client can't place orders even with trading enabled")
    void expiredClientIsRefused() {
        when(accountRepository.existsByAccountIdAndUsername(1, "testuser")).thenReturn(true);
        AccountEntity account = new AccountEntity();
        account.setClientId(3);
        account.setTradingEnabled(true);
        ClientEntity client = new ClientEntity();
        client.setAccountStatus(AccountStatus.EXPIRED);
        when(accountRepository.findById(1)).thenReturn(Optional.of(account));
        when(clientRepository.findById(3)).thenReturn(Optional.of(client));

        OrderResponse response = orderService.createOrder(
            new CreateOrderRequest(1, 1, Order.OrderType.BUY, new BigDecimal("1")), REQUEST_ID);

        assertEquals("ACCOUNT_RESTRICTED", response.getCode());
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

    /**
     * Unit tests for {@link OrderService#getOrderTimelineEvents(Integer)}.
     * Tests that the service correctly retrieves and maps audit events in chronological order.
     */

    @Test
    @DisplayName("getOrderTimelineEvents returns events in chronological order")
    void getOrderTimelineEventsReturnsChronologicalOrder() {
        Integer orderId = 42;
        
        // Create mock audit events in chronological order
        AuditEventEntity submitted = new AuditEventEntity(
            orderId, 7, 1, AuditEventType.SUBMITTED,
            Map.of("side", "BUY", "quantity", 10, "price", 100.00),
            java.time.Instant.parse("2026-09-21T10:30:00Z")
        );
        AuditEventEntity validated = new AuditEventEntity(
            orderId, 7, 1, AuditEventType.VALIDATED,
            Map.of("checks", new String[]{"ACCOUNT", "TRADABLE", "CASH"}),
            java.time.Instant.parse("2026-09-21T10:30:01Z")
        );
        AuditEventEntity filled = new AuditEventEntity(
            orderId, 7, 1, AuditEventType.FILLED,
            Map.of("quantity", 10, "price", 100.00),
            java.time.Instant.parse("2026-09-21T10:30:02Z")
        );
        
        when(auditEventRepository.findByOrderIdOrderByOccurredAtAsc(orderId))
            .thenReturn(List.of(submitted, validated, filled));
        
        List<AuditEventDto> timeline = orderService.getOrderTimelineEvents(orderId);
        
        assertEquals(3, timeline.size());
        assertEquals(AuditEventType.SUBMITTED, timeline.get(0).eventType());
        assertEquals(AuditEventType.VALIDATED, timeline.get(1).eventType());
        assertEquals(AuditEventType.FILLED, timeline.get(2).eventType());
        
        // Verify timestamps are in order
        assertTrue(timeline.get(0).occurredAt().isBefore(timeline.get(1).occurredAt()));
        assertTrue(timeline.get(1).occurredAt().isBefore(timeline.get(2).occurredAt()));
    }

    @Test
    @DisplayName("getOrderTimelineEvents includes all event details")
    void getOrderTimelineEventsIncludesAllDetails() {
        Integer orderId = 42;
        
        Map<String, Object> expectedDetails = Map.of(
            "side", "BUY",
            "quantity", 10,
            "price", 244.2366
        );
        
        AuditEventEntity event = new AuditEventEntity(
            orderId, 7, 1, AuditEventType.SUBMITTED,
            expectedDetails,
            java.time.Instant.parse("2026-09-21T10:30:00Z")
        );
        
        when(auditEventRepository.findByOrderIdOrderByOccurredAtAsc(orderId))
            .thenReturn(List.of(event));
        
        List<AuditEventDto> timeline = orderService.getOrderTimelineEvents(orderId);
        
        assertEquals(1, timeline.size());
        assertEquals(AuditEventType.SUBMITTED, timeline.get(0).eventType());
        assertEquals(java.time.Instant.parse("2026-09-21T10:30:00Z"), timeline.get(0).occurredAt());
        assertEquals(expectedDetails, timeline.get(0).details());
    }

    @Test
    @DisplayName("getOrderTimelineEvents returns empty list when no events exist")
    void getOrderTimelineEventsReturnsEmptyListWhenNoEvents() {
        Integer orderId = 42;
        
        when(auditEventRepository.findByOrderIdOrderByOccurredAtAsc(orderId))
            .thenReturn(List.of());
        
        List<AuditEventDto> timeline = orderService.getOrderTimelineEvents(orderId);
        
        assertTrue(timeline.isEmpty());
    }

    @Test
    @DisplayName("getOrderTimelineEvents throws IllegalArgumentException when orderId is null")
    void getOrderTimelineEventsThrowsWhenOrderIdIsNull() {
        assertThrows(IllegalArgumentException.class, () -> {
            orderService.getOrderTimelineEvents(null);
        });
    }
}
