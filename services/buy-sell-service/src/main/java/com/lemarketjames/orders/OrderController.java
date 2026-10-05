package com.lemarketjames.orders;

import com.lemarketjames.orders.dto.CreateOrderRequest;
import com.lemarketjames.orders.dto.OrderResponse;
import com.lemarketjames.orders.entity.Order;
import com.lemarketjames.orders.entity.RejectionReason;
import com.lemarketjames.orders.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {
    
    private final OrderService orderService;
    
    private final com.lemarketjames.orders.execution.OrderExecutionService execution;
    public OrderController(OrderService orderService, com.lemarketjames.orders.execution.OrderExecutionService execution) {
        this.orderService = orderService;
        this.execution = execution;
    }
    
    /**
     * Create a new order with cash validation
     * POST /api/v1/orders
     * Returns 201 on success, 400 if insufficient balance
     */
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        OrderResponse response = orderService.createOrder(request);
        
        // If validation failed, return 400 Bad Request
        if (!response.isSuccess()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
        
        // Order created successfully
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
    public ResponseEntity<List<OrderResponse>> getOrdersByAccountId(@PathVariable Integer accountId,
            @RequestParam(required = false) String date,
            @RequestParam(required = false) String timeZone) {
        List<OrderResponse> orders = orderService.getOrdersByAccountId(accountId, date, timeZone);
        return ResponseEntity.ok(orders);
    }
    
    /**
     * Get orders for an account with a specific status
     * GET /api/v1/orders/account/{accountId}/status/{status}
     */
    @GetMapping("/account/{accountId}/status/{status}")
    public ResponseEntity<List<OrderResponse>> getOrdersByAccountAndStatus(
            @PathVariable Integer accountId,
            @PathVariable Order.OrderStatus status,
            @RequestParam(required = false) String date,
            @RequestParam(required = false) String timeZone) {
        List<OrderResponse> orders = orderService.getOrdersByAccountAndStatus(accountId, status, date, timeZone);
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
     * Move an order to its next status (TRADING_OPS only, see SecurityConfig)
     * PUT /api/v1/orders/{orderId}/status/{status}
     */
    @PutMapping("/{orderId}/status/{status}")
    public ResponseEntity<OrderResponse> updateOrderStatus(
            @PathVariable Integer orderId,
            @PathVariable Order.OrderStatus status) {
        if (status == Order.OrderStatus.FILLED) {
            orderService.getOrderById(orderId); // Preserve ownership/missing-id behavior before internal execution.
            execution.execute(orderId, true);
            return ResponseEntity.ok(orderService.getOrderById(orderId));
        }
        OrderResponse response = orderService.updateOrderStatus(orderId, status);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Reject an order with a RejectionReason code (TRADING_OPS only, see SecurityConfig)
     * POST /api/v1/orders/{orderId}/reject?reason=CODE
     */
    @PostMapping("/{orderId}/reject")
    public ResponseEntity<OrderResponse> rejectOrder(
            @PathVariable Integer orderId,
            @RequestParam(defaultValue = "REJECTED_BY_OPERATIONS") RejectionReason reason) {
        OrderResponse response = orderService.rejectOrder(orderId, reason);
        return ResponseEntity.ok(response);
    }

    /**
    * Get the chronological audit timeline for an order (TRADING_OPS only).
    * 
    * <p>Returns all audit events for the order in the order they were recorded.
    * Each event captures a step in the order's lifecycle: placement, validation, acceptance,
    * execution, rejection, or settlement. Events include their type, timestamp, and details
    * (e.g., price, quantity, rejection reason, cash/holdings deltas).
    * 
    * <p><strong>Access control:</strong> Only TRADING_OPS staff may call this endpoint.
    * The order ID must exist and be accessible to the caller (see {@link #findOwnOrder(Integer)}).
    * 
    * <p><strong>Response:</strong> Returns 200 with a list of audit events in chronological order (oldest first).
    * If the order has no events, returns an empty list.
    * 
    * @param orderId the order to retrieve the audit timeline for
    * @return 200 OK with list of {@link com.lemarketjames.orders.dto.AuditEventDto}
    * @throws AccessDeniedException (403) if caller lacks TRADING_OPS role or order is not found/accessible
    */
    @GetMapping("/{orderId}/timeline")
    public ResponseEntity<List<com.lemarketjames.orders.dto.AuditEventDto>> getOrderTimeline(
            @PathVariable Integer orderId) {
        // Validates order exists and caller has access (TRADING_OPS or owner)
        orderService.findOwnOrder(orderId);
    
        List<com.lemarketjames.orders.dto.AuditEventDto> timeline = orderService.getOrderTimelineEvents(orderId);
        return ResponseEntity.ok(timeline);
    }
}
