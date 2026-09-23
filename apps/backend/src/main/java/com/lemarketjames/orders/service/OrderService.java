package com.lemarketjames.orders.service;

import com.lemarketjames.auth.domain.AccountRepository;
import com.lemarketjames.market.service.MarketDataService;
import com.lemarketjames.orders.dto.CreateOrderRequest;
import com.lemarketjames.orders.dto.OrderResponse;
import com.lemarketjames.orders.dto.SubmitBuyOrderRequest;
import com.lemarketjames.orders.entity.Instrument;
import com.lemarketjames.orders.entity.Order;
import com.lemarketjames.orders.exception.NotTradableException;
import com.lemarketjames.orders.repository.InstrumentRepository;
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
import java.util.List;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    
    private final OrderRepository orderRepository;
    private final CashValidationService cashValidationService;
    private final InstrumentRepository instrumentRepository;
    private final AccountRepository accountRepository;
    private final MarketDataService marketDataService;
    
    public OrderService(OrderRepository orderRepository,
                        InstrumentRepository instrumentRepository,
                        AccountRepository accountRepository,
                        CashValidationService cashValidationService,
                        MarketDataService marketDataService) {
        this.orderRepository = orderRepository;
        this.instrumentRepository = instrumentRepository;
        this.accountRepository = accountRepository;
        this.cashValidationService = cashValidationService;
        this.marketDataService = marketDataService;
    }
    
    /**
     * Create a new order with cash validation.
     * For BUY orders, validates that the account has sufficient cash.
     * BUY prices are captured from the market, never trusted from the browser.
     */
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        // Authorize before accessing financial information, even for rejected orders.
        validateAccountAccess(request.getAccountId());
        validateInstrumentTradability(request.getInstrumentId());
        BigDecimal price = request.getPricePerUnit();
        // For BUY orders, validate sufficient cash
        if (request.getOrderType() == Order.OrderType.BUY) {
            var quote = marketDataService.findByInstrumentId(request.getInstrumentId());
            if (quote.isEmpty() || !Double.isFinite(quote.get().askPrice()) || quote.get().askPrice() <= 0) {
                return new OrderResponse(false, "Market price is unavailable. Please try again.", "PRICE_UNAVAILABLE");
            }
            // Match the database scale so validation and the persisted snapshot agree.
            price = BigDecimal.valueOf(quote.get().askPrice()).setScale(4, RoundingMode.HALF_UP);
            if (price.signum() <= 0) {
                return new OrderResponse(false, "Market price is unavailable. Please try again.", "PRICE_UNAVAILABLE");
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
                        orderCost, availableCash), "INSUFFICIENT_CASH");
            }
        }
        
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
        log.info("Order submitted orderId={} accountId={} instrumentId={} side={}",
            savedOrder.getOrderId(), savedOrder.getAccountId(), savedOrder.getInstrumentId(), savedOrder.getOrderType());
        return new OrderResponse(savedOrder);
    }

    /**
     * Submit a BUY order via the dedicated buy-order entrypoint.
     */
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

    private Order findOwnOrder(Integer orderId) {
        authenticatedUsername();
        // Use the same denial for missing and foreign IDs to avoid exposing their existence.
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new AccessDeniedException("Account access is not allowed"));
        validateAccountAccess(order.getAccountId());
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

    private void validateAccountAccess(Integer accountId) {
        String username = authenticatedUsername();
        boolean ownsAccount = accountRepository.existsByAccountIdAndUsername(accountId, username);
        if (!ownsAccount) {
            log.warn("Order access denied for username={} accountId={}", username, accountId);
            throw new AccessDeniedException("Account access is not allowed");
        }
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
     * Update order status
     */
    public OrderResponse updateOrderStatus(Integer orderId, Order.OrderStatus newStatus) {
        Order order = findOwnOrder(orderId);
        
        order.setOrderStatus(newStatus);
        Order updatedOrder = orderRepository.save(order);
        return new OrderResponse(updatedOrder);
    }
    
    /**
     * Reject an order with a reason
     */
    public OrderResponse rejectOrder(Integer orderId, String reason) {
        Order order = findOwnOrder(orderId);
        
        order.setOrderStatus(Order.OrderStatus.REJECTED);
        order.setRejectionReason(reason);
        Order rejectedOrder = orderRepository.save(order);
        return new OrderResponse(rejectedOrder);
    }
}
