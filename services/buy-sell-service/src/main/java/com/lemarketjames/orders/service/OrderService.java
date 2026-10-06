package com.lemarketjames.orders.service;

import com.lemarketjames.common.audit.AuditEventType;
import com.lemarketjames.common.audit.AuditRecorder;
import com.lemarketjames.common.audit.AuditEventEntity;
import com.lemarketjames.common.audit.AuditEventRepository;
import com.lemarketjames.common.security.Role;
import com.lemarketjames.orders.dto.AuditEventDto;
import com.lemarketjames.orders.dto.CreateOrderRequest;
import com.lemarketjames.orders.dto.OrderResponse;
import com.lemarketjames.orders.dto.SubmitBuyOrderRequest;
import com.lemarketjames.orders.entity.Order;
import com.lemarketjames.orders.entity.RejectionReason;
import com.lemarketjames.orders.execution.OrderTransitions;
import com.lemarketjames.orders.repository.OrderRepository;
import com.lemarketjames.orders.submission.SubmissionRecorder;
import com.lemarketjames.orders.submission.SubmissionTrail;
import com.lemarketjames.orders.submission.SubmissionValidator;
import com.lemarketjames.orders.submission.ValidationOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final AccountAccess accountAccess;
    private final SubmissionValidator validator;
    private final SubmissionRecorder submissions;
    private final OrderTransitions transitions;
    private final AuditEventRepository auditEventRepository;

    public OrderService(OrderRepository orderRepository,
                        AccountAccess accountAccess,
                        SubmissionValidator validator,
                        SubmissionRecorder submissions,
                        OrderTransitions transitions,
                        AuditEventRepository auditEventRepository) {
        this.orderRepository = orderRepository;
        this.accountAccess = accountAccess;
        this.validator = validator;
        this.submissions = submissions;
        this.transitions = transitions;
        this.auditEventRepository = auditEventRepository;
    }

    /**
     * Submit an order once it passes the submission checks ({@link SubmissionValidator}).
     * Submission does not execute a trade.
     *
     * <p>Every submission leaves an audit trail under {@code requestId} (contract C2): an accepted
     * order's is saved in the same transaction as the order, and a refused order's is saved on its
     * own. Deliberately not transactional itself: the checks only read, and with no transaction
     * around them a refusal has nothing to roll its trail back.
     *
     * @param requestId the server-generated ID of this submission
     */
    public OrderResponse createOrder(CreateOrderRequest request, String requestId) {
        SubmissionTrail trail = new SubmissionTrail(requestId, accountAccess.callerClientId(), request);
        ValidationOutcome outcome;
        try {
            outcome = validator.validate(request, trail);
        } catch (RuntimeException refusal) {
            recordRefusalBeforeRethrowing(trail, refusal);
            throw refusal;
        }
        if (outcome.isRefused()) {
            submissions.refuse(trail);
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

        Order savedOrder = submissions.accept(order, trail);
        log.info("Order submitted orderId={} accountId={} instrumentId={} side={} requestId={}",
            savedOrder.getOrderId(), savedOrder.getAccountId(), savedOrder.getInstrumentId(),
            savedOrder.getOrderType(), requestId);
        return new OrderResponse(savedOrder);
    }

    /**
     * A check that threw still leaves its trail. If the trail itself can't be saved, that failure is
     * what the caller sees: a refusal is only reported once it has been recorded.
     */
    private void recordRefusalBeforeRethrowing(SubmissionTrail trail, RuntimeException refusal) {
        // A check that didn't finish (an unexpected error, not a refusal) is recorded as ERROR.
        trail.abandon();
        try {
            submissions.refuse(trail);
        } catch (RuntimeException auditFailure) {
            auditFailure.addSuppressed(refusal);
            throw auditFailure;
        }
    }

    /**
     * Submit a BUY order via the dedicated buy-order entrypoint.
     */
    public OrderResponse submitBuyOrder(SubmitBuyOrderRequest request, String requestId) {
        CreateOrderRequest createOrderRequest = new CreateOrderRequest(
            request.getAccountId(),
            request.getInstrumentId(),
            Order.OrderType.BUY,
            request.getQuantity()
        );
        createOrderRequest.setPricePerUnit(request.getPricePerUnit());
        return createOrder(createOrderRequest, requestId);
    }

    /**
     * Loads an order the caller may act on: their own, or any order for trading operations staff
     * (contract C7), who manage every client's orders.
     */
    public Order findOwnOrder(Integer orderId) {
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

    /**
    * Retrieves the chronological audit trail for an order.
    * 
    * <p>Returns all audit events (SUBMITTED, VALIDATED, ACCEPTED, FILLED, REJECTED, SETTLED) 
    * in the order they were recorded. Events are ordered by {@code audit_id} (auto-increment),
    * which reflects insertion order across all services.
    * 
    * <p><strong>Security:</strong> This method does not check ownership or role. Callers must validate
    * access separately via {@link #findOwnOrder(Integer)} before calling this method.
    * 
    * <p><strong>Empty timelines:</strong> If the order has no audit events (which should not occur in normal operation),
    * returns an empty list rather than throwing an exception.
    *
    * @param orderId the order ID to retrieve events for
    * @return list of {@link AuditEventDto} in chronological order (oldest first); empty list if no events exist
    * @throws IllegalArgumentException if orderId is null
    */
    public List<AuditEventDto> getOrderTimelineEvents(Integer orderId) {
        if (orderId == null) {
            throw new IllegalArgumentException("orderId cannot be null");
        }
        return auditEventRepository.findByOrderIdOrderByOccurredAtAsc(orderId)
            .stream()
            .map(event -> new AuditEventDto(
                event.getEventType(),
                event.getOccurredAt(),
                event.getDetails()
            ))
            .toList();
    }
}
