package com.lemarketjames.orders.service;

import com.lemarketjames.orders.dto.CreateOrderRequest;
import com.lemarketjames.orders.dto.OrderResponse;
import com.lemarketjames.orders.entity.Order;
import com.lemarketjames.orders.repository.OrderRepository;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class OrderService {
    
    private final OrderRepository orderRepository;
    private final CashValidationService cashValidationService;
    
    public OrderService(OrderRepository orderRepository, CashValidationService cashValidationService) {
        this.orderRepository = orderRepository;
        this.cashValidationService = cashValidationService;
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
        return orderRepository.findById(orderId)
            .map(OrderResponse::new)
            .orElseThrow(() -> new IllegalArgumentException("Order not found with ID: " + orderId));
    }
    
    /**
     * Get all orders for an account
     */
    public List<OrderResponse> getOrdersByAccountId(Integer accountId) {
        return orderRepository.findByAccountId(accountId)
            .stream()
            .map(OrderResponse::new)
            .collect(Collectors.toList());
    }
    
    /**
     * Get orders for an account with a specific status
     */
    public List<OrderResponse> getOrdersByAccountAndStatus(Integer accountId, Order.OrderStatus status) {
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
