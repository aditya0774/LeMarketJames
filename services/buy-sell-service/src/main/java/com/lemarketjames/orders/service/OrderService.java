package com.lemarketjames.orders.service;

import com.lemarketjames.common.audit.AuditEventType;
import com.lemarketjames.common.audit.AuditRecorder;
import com.lemarketjames.common.domain.AccountEntity;
import com.lemarketjames.common.domain.ClientEntity;
import com.lemarketjames.common.domain.ClientRepository;
import com.lemarketjames.common.security.Role;
import com.lemarketjames.market.service.MarketDataService;
import com.lemarketjames.common.domain.AccountRepository;
import com.lemarketjames.holdings.client.HoldingsValidationClient;
import com.lemarketjames.orders.dto.CreateOrderRequest;
import com.lemarketjames.orders.dto.OrderResponse;
import com.lemarketjames.orders.dto.SubmitBuyOrderRequest;
import com.lemarketjames.common.instruments.Instrument;
import com.lemarketjames.orders.entity.Order;
import com.lemarketjames.orders.entity.RejectionReason;
import com.lemarketjames.orders.execution.OrderTransitions;
import com.lemarketjames.orders.exception.NotTradableException;
import com.lemarketjames.common.instruments.InstrumentRepository;
import com.lemarketjames.orders.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final CashValidationService cashValidationService;
    private final InstrumentRepository instrumentRepository;
    private final AccountRepository accountRepository;
    private final ClientRepository clientRepository;
    private final MarketDataService marketDataService;
    private final TradingRestrictions restrictions;
    private final HoldingsValidationClient holdingsValidationClient;
    private final AuditRecorder auditRecorder;
    private final OrderTransitions transitions;
    private final AuditEventRepository auditEventRepository;

    public OrderService(OrderRepository orderRepository,
                        InstrumentRepository instrumentRepository,
                        AccountRepository accountRepository,
                        ClientRepository clientRepository,
                        CashValidationService cashValidationService,
                        MarketDataService marketDataService,
                        TradingRestrictions restrictions,
                        HoldingsValidationClient holdingsValidationClient,
                        AuditRecorder auditRecorder,
                        OrderTransitions transitions,
                        AuditEventRepository auditEventRepository) {
        this.orderRepository = orderRepository;
        this.instrumentRepository = instrumentRepository;
        this.accountRepository = accountRepository;
        this.clientRepository = clientRepository;
        this.cashValidationService = cashValidationService;
        this.marketDataService = marketDataService;
        this.restrictions = restrictions;
        this.holdingsValidationClient = holdingsValidationClient;
        this.auditRecorder = auditRecorder;
        this.transitions = transitions;
        this.auditEventRepository = auditEventRepository;
    }

    /**
     * Submit an order after ownership, account, tradability and side-specific validation.
     * SELL orders require sufficient holdings; submission does not execute a trade.
     * For BUY orders, validates that the account has sufficient cash.
     * BUY prices are captured from the market, never trusted from the browser.
     * A saved order is audited as SUBMITTED then VALIDATED in the same transaction (contract C2).
     */
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        validateAccountAccess(request.getAccountId());
        if (!accountMayTrade(request.getAccountId())) {
            log.warn("Order refused for restricted accountId={}", request.getAccountId());
            return new OrderResponse(false, "This account can't place orders right now. Please contact support.",
                RejectionReason.ACCOUNT_RESTRICTED.name());
        }
        if (restrictions.locationRestricted(request.getAccountId())) {
            return new OrderResponse(false, "Trading is restricted in your location", RejectionReason.LOCATION_RESTRICTED.name());
        }
        validateInstrumentTradability(request.getInstrumentId());
        // Browser validation is advisory; every SELL must be checked again before saving.
        if (request.getOrderType() == Order.OrderType.SELL) {
            holdingsValidationClient.validateSufficientHoldings(request.getAccountId(), authenticatedUsername(),
                request.getInstrumentId(), request.getQuantity());
        }
        BigDecimal price = null;
        // For BUY orders, validate sufficient cash
        if (request.getOrderType() == Order.OrderType.BUY) {
            var quote = marketDataService.findByInstrumentId(request.getInstrumentId());
            if (quote.isEmpty() || !Double.isFinite(quote.get().askPrice()) || quote.get().askPrice() <= 0) {
                return new OrderResponse(false, "Market price is unavailable. Please try again.",
                    RejectionReason.PRICE_UNAVAILABLE.name());
            }
            if (restrictions.stale(quote.get())) {
                return new OrderResponse(false, "Market quote is stale", RejectionReason.STALE_QUOTE.name());
            }
            // Match the database scale so validation and the persisted snapshot agree.
            price = BigDecimal.valueOf(quote.get().askPrice()).setScale(4, RoundingMode.HALF_UP);
            if (price.signum() <= 0) {
                return new OrderResponse(false, "Market price is unavailable. Please try again.",
                    RejectionReason.PRICE_UNAVAILABLE.name());
            }
            BigDecimal orderCost = request.getQuantity().multiply(price);

            boolean hasSufficientCash = cashValidationService.validateSufficientCash(
                request.getAccountId(),
                orderCost
            );

            if (!hasSufficientCash) {
                BigDecimal availableCash = cashValidationService.getCashBalance(request.getAccountId());
                return new OrderResponse(false,
                    String.format("Insufficient balance. Required: $%.2f, Available: $%.2f",
                        orderCost, availableCash), RejectionReason.INSUFFICIENT_CASH.name());
            }
        }

        // Persist only after all submission checks pass.
        Order order = new Order(
            request.getAccountId(),
            request.getInstrumentId(),
            request.getOrderType(),
            request.getQuantity()
        );

        if (price != null) {
            order.setPricePerUnit(price);
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
        authenticatedUsername();
        // Use the same denial for missing and foreign IDs to avoid exposing their existence.
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new AccessDeniedException("Account access is not allowed"));
        if (!callerHasRole(Role.TRADING_OPS)) {
            validateAccountAccess(order.getAccountId());
        }
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

    private boolean callerHasRole(Role role) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
            .anyMatch(authority -> role.authority().equals(authority.getAuthority()));
    }

    private void validateAccountAccess(Integer accountId) {
        String username = authenticatedUsername();
        boolean ownsAccount = accountRepository.existsByAccountIdAndUsername(accountId, username);
        if (!ownsAccount) {
            log.warn("Order access denied for username={} accountId={}", username, accountId);
            throw new AccessDeniedException("Account access is not allowed");
        }
    }

    /**
     * Whether the account may trade: trading enabled on the account and the client ACTIVE (an
     * EXPIRED client may log in but not trade). Runs after the ownership check, so the account exists.
     */
    private boolean accountMayTrade(Integer accountId) {
        return accountRepository.findById(accountId)
            .map(account -> account.isTradingEnabled() && clientMayTrade(account))
            .orElse(true);
    }

    private boolean clientMayTrade(AccountEntity account) {
        return clientRepository.findById(account.getClientId())
            .map(ClientEntity::getAccountStatus)
            .map(status -> status.canTrade())
            .orElse(true);
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

    public List<OrderResponse> getOrdersByAccountId(Integer accountId, String date, String timeZone) {
        return getOrdersByAccountAndStatus(accountId, null, date, timeZone);
    }

    public List<OrderResponse> getOrdersByAccountAndStatus(
            Integer accountId, Order.OrderStatus status, String date, String timeZone) {
        validateAccountAccess(accountId);
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
        return orderRepository.findOwnByInstrumentId(instrumentId, authenticatedUsername())
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
