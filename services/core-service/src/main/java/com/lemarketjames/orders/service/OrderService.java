package com.lemarketjames.orders.service;

import com.lemarketjames.common.domain.AccountRepository;
import com.lemarketjames.holdings.client.HoldingsSettlementClient;
import com.lemarketjames.orders.dto.CreateOrderRequest;
import com.lemarketjames.orders.dto.OrderResponse;
import com.lemarketjames.orders.dto.SubmitBuyOrderRequest;
import com.lemarketjames.orders.entity.Instrument;
import com.lemarketjames.orders.entity.Order;
import com.lemarketjames.orders.exception.NotTradableException;
import com.lemarketjames.orders.repository.InstrumentRepository;
import com.lemarketjames.orders.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final CashValidationService cashValidationService;
    private final InstrumentRepository instrumentRepository;
    private final AccountRepository accountRepository;
    private final HoldingsSettlementClient holdingsSettlementClient;

    public OrderService(OrderRepository orderRepository,
                        InstrumentRepository instrumentRepository,
                        AccountRepository accountRepository,
                        CashValidationService cashValidationService,
                        HoldingsSettlementClient holdingsSettlementClient) {
        this.orderRepository = orderRepository;
        this.instrumentRepository = instrumentRepository;
        this.accountRepository = accountRepository;
        this.cashValidationService = cashValidationService;
        this.holdingsSettlementClient = holdingsSettlementClient;
    }
    
    /**
     * Create a new order with cash validation.
     * For BUY orders, validates that the account has sufficient cash.
     * Cost is calculated as: quantity * pricePerUnit
     */
    public OrderResponse createOrder(CreateOrderRequest request) {
        // For BUY orders, validate sufficient cash
        if (request.getOrderType() == Order.OrderType.BUY) {
            BigDecimal orderCost = calculateOrderCost(request);
            
            boolean hasSufficientCash = cashValidationService.validateSufficientCash(
                request.getAccountId(),
                orderCost
            );
            
            if (!hasSufficientCash) {
                BigDecimal availableCash = cashValidationService.getCashBalance(request.getAccountId());
                return new OrderResponse(false, 
                    String.format("Insufficient balance. Required: $%.2f, Available: $%.2f", 
                        orderCost, availableCash));
            }
        }
        
        // Cash validation passed or SELL order; proceed with order creation
        validateAccountAccess(request.getAccountId());
        validateInstrumentTradability(request.getInstrumentId());

        Order order = new Order(
            request.getAccountId(),
            request.getInstrumentId(),
            request.getOrderType(),
            request.getQuantity()
        );
        
        if (request.getPricePerUnit() != null) {
            order.setPricePerUnit(request.getPricePerUnit());
        }
        
        Order savedOrder = orderRepository.save(order);
        return new OrderResponse(savedOrder);
    }

    /**
     * Submit a BUY order via the dedicated buy-order entrypoint.
     */
    public OrderResponse submitBuyOrder(SubmitBuyOrderRequest request) {
        CreateOrderRequest createOrderRequest = new CreateOrderRequest(
            request.getAccountId(),
            request.getInstrumentId(),
            Order.OrderType.BUY,
            request.getQuantity()
        );
        createOrderRequest.setPricePerUnit(request.getPricePerUnit());
        return createOrder(createOrderRequest);
    }

    private Order findOwnOrder(Integer orderId) {
        authenticatedUsername();
        // Use the same denial for missing and foreign IDs to avoid exposing their existence.
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new AccessDeniedException("Account access is not allowed"));
        validateAccountAccess(order.getAccountId());
        return order;
    }

    private String authenticatedUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getName())) {
            throw new AccessDeniedException("Authentication required");
        }
        return authentication.getName();
    }

    private void validateAccountAccess(Integer accountId) {
        String username = authenticatedUsername();
        boolean ownsAccount = accountRepository.existsByAccountIdAndUsername(accountId, username);
        if (!ownsAccount) {
            log.warn("Order access denied for username={} accountId={}", username, accountId);
            throw new AccessDeniedException("Account access is not allowed");
        }
    }

    private void validateInstrumentTradability(Integer instrumentId) {
        Instrument instrument = instrumentRepository.findById(instrumentId)
            .orElseThrow(() -> new IllegalArgumentException("Instrument not found with ID: " + instrumentId));

        if (!instrument.isTradable()) {
            log.warn("Tradability check failed for instrumentId={}", instrumentId);
            throw new NotTradableException("Instrument is currently not tradable");
        }
    }
    
    /**
     * Calculates the total cost of an order.
     * Cost = quantity * pricePerUnit
     */
    private BigDecimal calculateOrderCost(CreateOrderRequest request) {
        if (request.getQuantity() == null || request.getPricePerUnit() == null) {
            return BigDecimal.ZERO;
        }
        return request.getQuantity().multiply(request.getPricePerUnit());
    }
    
    /**
     * Get order by ID
     */
    public OrderResponse getOrderById(Integer orderId) {
        return new OrderResponse(findOwnOrder(orderId));
    }
    
    /**
     * Get all orders for an account
     */
    public List<OrderResponse> getOrdersByAccountId(Integer accountId) {
        validateAccountAccess(accountId);
        return orderRepository.findByAccountId(accountId)
            .stream()
            .map(OrderResponse::new)
            .collect(Collectors.toList());
    }
    
    /**
     * Get orders for an account with a specific status
     */
    public List<OrderResponse> getOrdersByAccountAndStatus(Integer accountId, Order.OrderStatus status) {
        validateAccountAccess(accountId);
        return orderRepository.findByAccountIdAndOrderStatus(accountId, status)
            .stream()
            .map(OrderResponse::new)
            .collect(Collectors.toList());
    }
    
    /**
     * Get all orders for an instrument
     */
    public List<OrderResponse> getOrdersByInstrumentId(Integer instrumentId) {
        return orderRepository.findOwnByInstrumentId(instrumentId, authenticatedUsername())
            .stream()
            .map(OrderResponse::new)
            .collect(Collectors.toList());
    }
    
    /**
     * Update order status. Transitioning to FILLED settles the order (cash debit/credit and the
     * holdings update) via holdings-service before the status change is persisted, so an order can
     * never end up FILLED without its side effects actually happening.
     */
    public OrderResponse updateOrderStatus(Integer orderId, Order.OrderStatus newStatus) {
        Order order = findOwnOrder(orderId);

        if (newStatus == Order.OrderStatus.FILLED) {
            if (order.getPricePerUnit() == null) {
                throw new IllegalArgumentException(
                    "Order " + orderId + " has no price and cannot be filled");
            }
            holdingsSettlementClient.settle(order);
            order.setFilledAt(LocalDateTime.now());
        }

        order.setOrderStatus(newStatus);
        Order updatedOrder = orderRepository.save(order);
        return new OrderResponse(updatedOrder);
    }
    
    /**
     * Reject an order with a reason
     */
    public OrderResponse rejectOrder(Integer orderId, String reason) {
        Order order = findOwnOrder(orderId);
        
        order.setOrderStatus(Order.OrderStatus.REJECTED);
        order.setRejectionReason(reason);
        Order rejectedOrder = orderRepository.save(order);
        return new OrderResponse(rejectedOrder);
    }
}
