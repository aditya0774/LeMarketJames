package com.lemarketjames.orders;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Service class for Order business logic.
 * Handles order creation, retrieval, and validation.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;

    /**
     * Constructor for OrderService.
     * 
     * @param orderRepository the order repository for database operations
     */
    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    /**
     * Create a new order with validation.
     * 
     * @param accountId the account ID placing the order
     * @param instrumentId the instrument to buy/sell
     * @param orderType "BUY" or "SELL"
     * @param quantity number of shares
     * @return the created Order
     * @throws IllegalArgumentException if validation fails
     */
    public Order createOrder(Integer accountId, Integer instrumentId, String orderType, BigDecimal quantity) {
        // Validate inputs
        if (accountId == null || accountId <= 0) {
            throw new IllegalArgumentException("Invalid account ID");
        }
        if (instrumentId == null || instrumentId <= 0) {
            throw new IllegalArgumentException("Invalid instrument ID");
        }
        if (!orderType.equals("BUY") && !orderType.equals("SELL")) {
            throw new IllegalArgumentException("Order type must be BUY or SELL");
        }
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than 0");
        }

        log.info("Creating order: accountId={}, instrumentId={}, type={}, quantity={}", 
                 accountId, instrumentId, orderType, quantity);

        // Create and save order
        Order order = new Order(accountId, instrumentId, orderType, quantity);
        Order savedOrder = orderRepository.save(order);

        log.info("Order created successfully: orderId={}", savedOrder.getOrderId());
        return savedOrder;
    }

    /**
     * Retrieve an order by ID.
     * 
     * @param orderId the order ID
     * @return the Order if found, empty Optional otherwise
     */
    public Optional<Order> getOrderById(Integer orderId) {
        return orderRepository.findById(orderId);
    }

    /**
     * Retrieve all orders for an account.
     * 
     * @param accountId the account ID
     * @return list of orders for that account
     */
    public List<Order> getOrdersByAccountId(Integer accountId) {
        return orderRepository.findByAccountId(accountId);
    }

    /**
     * Retrieve all orders.
     * 
     * @return list of all orders
     */
    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }
}
