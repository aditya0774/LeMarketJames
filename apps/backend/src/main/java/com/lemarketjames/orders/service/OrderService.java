package com.lemarketjames.orders.service;

import com.lemarketjames.orders.dto.CreateOrderRequest;
import com.lemarketjames.orders.dto.OrderResponse;
import com.lemarketjames.orders.entity.Order;
import com.lemarketjames.orders.repository.OrderRepository;
import com.lemarketjames.common.security.UserIdExtractorUtil;
import com.lemarketjames.common.security.AccountOwnershipValidator;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class OrderService {
    
    private final OrderRepository orderRepository;
    private final AccountOwnershipValidator accountOwnershipValidator;
    
    public OrderService(OrderRepository orderRepository, AccountOwnershipValidator accountOwnershipValidator) {
        this.orderRepository = orderRepository;
        this.accountOwnershipValidator = accountOwnershipValidator;
    }
    
    /**
     * Create a new order
     * 
     * SECURITY: Validates that the authenticated user owns the account.
     * If they don't own it, throws UnauthorizedAccessException (caught by GlobalExceptionHandler → 403 Forbidden)
     */
    public OrderResponse createOrder(CreateOrderRequest request) {
        // Step 1: Extract the authenticated username from the JWT token
        String authenticatedUsername = UserIdExtractorUtil.extractAuthenticatedUsername();
        
        // Step 2: Validate that this user owns the requested account
        accountOwnershipValidator.validateAccountOwnership(authenticatedUsername, request.getAccountId());
        
        // Step 3: If validation passed, create the order
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
     * Get order by ID
     */
    public OrderResponse getOrderById(Integer orderId) {
        return orderRepository.findById(orderId)
            .map(OrderResponse::new)
            .orElseThrow(() -> new IllegalArgumentException("Order not found with ID: " + orderId));
    }
    
    /**
     * Get all orders for an account
     * 
     * SECURITY: Validates that the authenticated user owns the account.
     * If they don't own it, throws UnauthorizedAccessException (caught by GlobalExceptionHandler → 403 Forbidden)
     */
    public List<OrderResponse> getOrdersByAccountId(Integer accountId) {
        // Step 1: Extract the authenticated username from the JWT token
        String authenticatedUsername = UserIdExtractorUtil.extractAuthenticatedUsername();
        
        // Step 2: Validate that this user owns the requested account
        accountOwnershipValidator.validateAccountOwnership(authenticatedUsername, accountId);
        
        // Step 3: If validation passed, fetch and return their orders
        return orderRepository.findByAccountId(accountId)
            .stream()
            .map(OrderResponse::new)
            .collect(Collectors.toList());
    }
    
    /**
     * Get orders for an account with a specific status
     * 
     * SECURITY: Validates that the authenticated user owns the account.
     * If they don't own it, throws UnauthorizedAccessException (caught by GlobalExceptionHandler → 403 Forbidden)
     */
    public List<OrderResponse> getOrdersByAccountAndStatus(Integer accountId, Order.OrderStatus status) {
        // Step 1: Extract the authenticated username from the JWT token
        String authenticatedUsername = UserIdExtractorUtil.extractAuthenticatedUsername();
        
        // Step 2: Validate that this user owns the requested account
        accountOwnershipValidator.validateAccountOwnership(authenticatedUsername, accountId);
        
        // Step 3: If validation passed, fetch and return their orders
        return orderRepository.findByAccountIdAndOrderStatus(accountId, status)
            .stream()
            .map(OrderResponse::new)
            .collect(Collectors.toList());
    }
    
    /**
     * Get all orders for an instrument
     */
    public List<OrderResponse> getOrdersByInstrumentId(Integer instrumentId) {
        return orderRepository.findByInstrumentId(instrumentId)
            .stream()
            .map(OrderResponse::new)
            .collect(Collectors.toList());
    }
    
    /**
     * Update order status
     */
    public OrderResponse updateOrderStatus(Integer orderId, Order.OrderStatus newStatus) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new IllegalArgumentException("Order not found with ID: " + orderId));
        
        order.setOrderStatus(newStatus);
        Order updatedOrder = orderRepository.save(order);
        return new OrderResponse(updatedOrder);
    }
    
    /**
     * Reject an order with a reason
     */
    public OrderResponse rejectOrder(Integer orderId, String reason) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new IllegalArgumentException("Order not found with ID: " + orderId));
        
        order.setOrderStatus(Order.OrderStatus.REJECTED);
        order.setRejectionReason(reason);
        Order rejectedOrder = orderRepository.save(order);
        return new OrderResponse(rejectedOrder);
    }
}
