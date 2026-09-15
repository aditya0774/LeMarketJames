package com.lemarketjames.orders.service;

import com.lemarketjames.orders.dto.CreateOrderRequest;
import com.lemarketjames.orders.dto.OrderResponse;
import com.lemarketjames.orders.entity.Instrument;
import com.lemarketjames.orders.entity.Order;
import com.lemarketjames.orders.exception.NotTradableException;
import com.lemarketjames.orders.repository.InstrumentRepository;
import com.lemarketjames.orders.repository.OrderRepository;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class OrderService {
    
    private final OrderRepository orderRepository;
    private final InstrumentRepository instrumentRepository;
    
    public OrderService(OrderRepository orderRepository, InstrumentRepository instrumentRepository) {
        this.orderRepository = orderRepository;
        this.instrumentRepository = instrumentRepository;
    }
    
    /**
     * Create a new order
     */
    public OrderResponse createOrder(CreateOrderRequest request) {
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

    private void validateInstrumentTradability(Integer instrumentId) {
        Instrument instrument = instrumentRepository.findById(instrumentId)
            .orElseThrow(() -> new IllegalArgumentException("Instrument not found with ID: " + instrumentId));

        if (!instrument.isTradable()) {
            throw new NotTradableException("Instrument is currently not tradable");
        }
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
