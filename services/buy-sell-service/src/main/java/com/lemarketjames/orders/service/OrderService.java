package com.lemarketjames.orders.service;

import com.lemarketjames.common.audit.AuditEventType;
import com.lemarketjames.common.audit.AuditRecorder;
import com.lemarketjames.common.security.Role;
import com.lemarketjames.orders.dto.CreateOrderRequest;
import com.lemarketjames.orders.dto.OrderResponse;
import com.lemarketjames.orders.dto.SubmitBuyOrderRequest;
import com.lemarketjames.orders.entity.Order;
import com.lemarketjames.orders.entity.RejectionReason;
import com.lemarketjames.orders.execution.OrderTransitions;
import com.lemarketjames.orders.repository.OrderRepository;
import com.lemarketjames.orders.submission.SubmissionValidator;
import com.lemarketjames.orders.submission.ValidationOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final AccountAccess accountAccess;
    private final SubmissionValidator validator;
    private final AuditRecorder auditRecorder;
    private final OrderTransitions transitions;

    public OrderService(OrderRepository orderRepository,
                        AccountAccess accountAccess,
                        SubmissionValidator validator,
                        AuditRecorder auditRecorder,
                        OrderTransitions transitions) {
        this.orderRepository = orderRepository;
        this.accountAccess = accountAccess;
        this.validator = validator;
        this.auditRecorder = auditRecorder;
        this.transitions = transitions;
    }

    /**
     * Submit an order once it passes the submission checks ({@link SubmissionValidator}).
     * Submission does not execute a trade.
     * A saved order is audited as SUBMITTED then VALIDATED in the same transaction (contract C2).
     */
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        ValidationOutcome outcome = validator.validate(request);
        if (outcome.isRefused()) {
            return outcome.refusal();
        }

        // Persist only after all submission checks pass.
        Order order = new Order(
            request.getAccountId(),
            request.getInstrumentId(),
            request.getOrderType(),
            request.getQuantity()
        );

        if (outcome.price() != null) {
            order.setPricePerUnit(outcome.price());
        }

        Order savedOrder = orderRepository.save(order);
        Map<String, Object> submitted = new HashMap<>();
        submitted.put("side", savedOrder.getOrderType().name());
        submitted.put("quantity", savedOrder.getQuantity());
        submitted.put("price", savedOrder.getPricePerUnit());
        audit(AuditEventType.SUBMITTED, savedOrder, submitted);
        audit(AuditEventType.VALIDATED, savedOrder, Map.of("checks",
            savedOrder.getOrderType() == Order.OrderType.BUY
                ? List.of("ACCOUNT", "TRADABLE", "CASH")
                : List.of("ACCOUNT", "TRADABLE", "HOLDINGS")));
        log.info("Order submitted orderId={} accountId={} instrumentId={} side={}",
            savedOrder.getOrderId(), savedOrder.getAccountId(), savedOrder.getInstrumentId(), savedOrder.getOrderType());
        return new OrderResponse(savedOrder);
    }

    /**
     * Submit a BUY order via the dedicated buy-order entrypoint.
     */
    @Transactional
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

    /**
     * Loads an order the caller may act on: their own, or any order for trading operations staff
     * (contract C7), who manage every client's orders.
     */
    private Order findOwnOrder(Integer orderId) {
        accountAccess.authenticatedUsername();
        // Use the same denial for missing and foreign IDs to avoid exposing their existence.
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new AccessDeniedException("Account access is not allowed"));
        if (!callerHasRole(Role.TRADING_OPS)) {
            accountAccess.requireOwnAccount(order.getAccountId());
        }
        return order;
    }

    private boolean callerHasRole(Role role) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
            .anyMatch(authority -> role.authority().equals(authority.getAuthority()));
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
        accountAccess.requireOwnAccount(accountId);
        return orderRepository.findByAccountId(accountId)
            .stream()
            .map(OrderResponse::new)
            .collect(Collectors.toList());
    }

    /**
     * Get orders for an account with a specific status
     */
    public List<OrderResponse> getOrdersByAccountAndStatus(Integer accountId, Order.OrderStatus status) {
        accountAccess.requireOwnAccount(accountId);
        return orderRepository.findByAccountIdAndOrderStatus(accountId, status)
            .stream()
            .map(OrderResponse::new)
            .collect(Collectors.toList());
    }

    public List<OrderResponse> getOrdersByAccountId(Integer accountId, String date, String timeZone) {
        return getOrdersByAccountAndStatus(accountId, null, date, timeZone);
    }

    public List<OrderResponse> getOrdersByAccountAndStatus(
            Integer accountId, Order.OrderStatus status, String date, String timeZone) {
        accountAccess.requireOwnAccount(accountId);
        OrderHistoryPeriod period = OrderHistoryPeriod.parse(date, timeZone);
        List<Order> orders;
        if (period == null) {
            orders = status == null ? orderRepository.findByAccountId(accountId)
                : orderRepository.findByAccountIdAndOrderStatus(accountId, status);
        } else {
            orders = orderRepository.findHistory(accountId, status, period.start(), period.end());
        }
        return orders.stream().map(OrderResponse::new).collect(Collectors.toList());
    }

    /**
     * Get all orders for an instrument
     */
    public List<OrderResponse> getOrdersByInstrumentId(Integer instrumentId) {
        return orderRepository.findOwnByInstrumentId(instrumentId, accountAccess.authenticatedUsername())
            .stream()
            .map(OrderResponse::new)
            .collect(Collectors.toList());
    }

    /**
     * Moves an order to its next status (contract C1); an illegal move throws
     * InvalidStatusTransitionException. Fills are handled by the execution coordinator, which
     * commits its intent before remote settlement. Manual mutations cannot interrupt that intent.
     */
    @Transactional
    public OrderResponse updateOrderStatus(Integer orderId, Order.OrderStatus newStatus) {
        Order order = orderRepository.findLockedById(orderId)
            .orElseThrow(() -> new AccessDeniedException("Account access is not allowed"));
        findOwnOrder(orderId);
        if (order.isSettlementPending()) throw new org.springframework.web.server.ResponseStatusException(
            org.springframework.http.HttpStatus.CONFLICT, "Settlement is in progress; retry after reconciliation");

        if (newStatus == Order.OrderStatus.FILLED) {
            throw new IllegalArgumentException("Fills must use the durable execution coordinator");
        }
        transitions.move(order, newStatus,
            newStatus == Order.OrderStatus.REJECTED ? RejectionReason.REJECTED_BY_OPERATIONS : null);

        Order updatedOrder = orderRepository.save(order);
        return new OrderResponse(updatedOrder);
    }

    /**
     * Rejects an order with a reason code (contract C1). Only open orders can be rejected.
     */
    @Transactional
    public OrderResponse rejectOrder(Integer orderId, RejectionReason reason) {
        Order order = orderRepository.findLockedById(orderId)
            .orElseThrow(() -> new AccessDeniedException("Account access is not allowed"));
        findOwnOrder(orderId);
        if (order.isSettlementPending()) throw new org.springframework.web.server.ResponseStatusException(
            org.springframework.http.HttpStatus.CONFLICT, "Settlement is in progress; retry after reconciliation");

        transitions.move(order, Order.OrderStatus.REJECTED, reason);
        Order rejectedOrder = orderRepository.save(order);
        return new OrderResponse(rejectedOrder);
    }

    private void audit(AuditEventType type, Order order, Map<String, Object> details) {
        auditRecorder.record(type, order.getOrderId(), order.getAccountId(), details);
    }

}
