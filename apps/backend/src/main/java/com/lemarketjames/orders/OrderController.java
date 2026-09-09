package com.lemarketjames.orders;

import com.lemarketjames.orders.dto.OrderRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST Controller for handling order-related endpoints.
 * Provides endpoints for creating, retrieving, and managing orders.
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    private final OrderService orderService;

    /**
     * Constructor for OrderController.
     * 
     * @param orderService the order service for business logic
     */
    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * Create a new order.
     * 
     * POST /api/orders
     * 
     * Example request body:
     * {
     *   "accountId": 1,
     *   "instrumentId": 1,
     *   "orderType": "BUY",
     *   "quantity": 10.5
     * }
     * 
     * @param request the order creation request
     * @return ResponseEntity with created order and HTTP 201 status
     */
    @PostMapping
    public ResponseEntity<?> createOrder(@RequestBody OrderRequest request) {
        try {
            Order order = orderService.createOrder(
                request.getAccountId(),
                request.getInstrumentId(),
                request.getOrderType(),
                request.getQuantity()
            );

            log.info("Order created via REST API: orderId={}", order.getOrderId());
            return ResponseEntity.status(HttpStatus.CREATED).body(order);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid order request: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error creating order", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to create order"));
        }
    }

    /**
     * Retrieve an order by ID.
     * 
     * GET /api/orders/{orderId}
     * 
     * @param orderId the order ID
     * @return ResponseEntity with the order if found, or 404 if not found
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<?> getOrderById(@PathVariable Integer orderId) {
        return orderService.getOrderById(orderId)
                .map(order -> ResponseEntity.ok().body((Object) order))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Retrieve all orders for an account.
     * 
     * GET /api/orders/account/{accountId}
     * 
     * @param accountId the account ID
     * @return ResponseEntity with list of orders
     */
    @GetMapping("/account/{accountId}")
    public ResponseEntity<?> getOrdersByAccountId(@PathVariable Integer accountId) {
        List<Order> orders = orderService.getOrdersByAccountId(accountId);
        return ResponseEntity.ok(orders);
    }

    /**
     * Retrieve all orders.
     * 
     * GET /api/orders
     * 
     * @return ResponseEntity with list of all orders
     */
    @GetMapping
    public ResponseEntity<?> getAllOrders() {
        List<Order> orders = orderService.getAllOrders();
        return ResponseEntity.ok(orders);
    }
}
