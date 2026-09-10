package com.lemarketjames.orders;

import com.lemarketjames.orders.dto.CreateOrderRequest;
import com.lemarketjames.orders.dto.OrderResponse;
import com.lemarketjames.orders.entity.Order;
import com.lemarketjames.orders.service.OrderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {
    
    private final OrderService orderService;
    
    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }
    
    /**
     * Create a new order
     * POST /api/v1/orders
     */
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@RequestBody CreateOrderRequest request) {
        OrderResponse response = orderService.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    /**
     * Get order by ID
     * GET /api/v1/orders/{orderId}
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrderById(@PathVariable Integer orderId) {
        OrderResponse response = orderService.getOrderById(orderId);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get all orders for an account
     * GET /api/v1/orders/account/{accountId}
     */
    @GetMapping("/account/{accountId}")
    public ResponseEntity<List<OrderResponse>> getOrdersByAccountId(@PathVariable Integer accountId) {
        List<OrderResponse> orders = orderService.getOrdersByAccountId(accountId);
        return ResponseEntity.ok(orders);
    }
    
    /**
     * Get orders for an account with a specific status
     * GET /api/v1/orders/account/{accountId}/status/{status}
     */
    @GetMapping("/account/{accountId}/status/{status}")
    public ResponseEntity<List<OrderResponse>> getOrdersByAccountAndStatus(
            @PathVariable Integer accountId,
            @PathVariable Order.OrderStatus status) {
        List<OrderResponse> orders = orderService.getOrdersByAccountAndStatus(accountId, status);
        return ResponseEntity.ok(orders);
    }
    
    /**
     * Get all orders for an instrument
     * GET /api/v1/orders/instrument/{instrumentId}
     */
    @GetMapping("/instrument/{instrumentId}")
    public ResponseEntity<List<OrderResponse>> getOrdersByInstrumentId(@PathVariable Integer instrumentId) {
        List<OrderResponse> orders = orderService.getOrdersByInstrumentId(instrumentId);
        return ResponseEntity.ok(orders);
    }
    
    /**
     * Update order status
     * PUT /api/v1/orders/{orderId}/status/{status}
     */
    @PutMapping("/{orderId}/status/{status}")
    public ResponseEntity<OrderResponse> updateOrderStatus(
            @PathVariable Integer orderId,
            @PathVariable Order.OrderStatus status) {
        OrderResponse response = orderService.updateOrderStatus(orderId, status);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Reject an order
     * POST /api/v1/orders/{orderId}/reject
     */
    @PostMapping("/{orderId}/reject")
    public ResponseEntity<OrderResponse> rejectOrder(
            @PathVariable Integer orderId,
            @RequestParam(required = false) String reason) {
        OrderResponse response = orderService.rejectOrder(orderId, reason != null ? reason : "No reason provided");
        return ResponseEntity.ok(response);
    }
}
