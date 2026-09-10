package com.lemarketjames.orders;

import com.lemarketjames.holdings.HoldingsValidationService;
import com.lemarketjames.holdings.dto.SellOrderRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Service class for managing orders.
 * Handles order creation with validation including holdings validation for sell orders.
 */
@Service
public class OrdersService {

    private final HoldingsValidationService holdingsValidationService;

    /**
     * Constructs an OrdersService with the provided dependencies.
     *
     * @param holdingsValidationService the holdings validation service
     */
    public OrdersService(HoldingsValidationService holdingsValidationService) {
        this.holdingsValidationService = holdingsValidationService;
    }

    /**
     * Creates a sell order after validating that the user has sufficient holdings.
     * The order is only created if holdings validation passes.
     *
     * @param sellOrderRequest the sell order request containing account, instrument, and quantity
     * @throws IllegalArgumentException if holdings validation fails (insufficient holdings)
     */
    @Transactional
    public void createSellOrder(SellOrderRequest sellOrderRequest) {
        // Validate that the user has sufficient holdings to sell
        holdingsValidationService.validateSellOrder(
            sellOrderRequest.getAccountId(),
            sellOrderRequest.getInstrumentId(),
            sellOrderRequest.getSellQuantity()
        );

        // If validation passes, proceed with order creation
        // (Order persistence logic would go here in a full implementation)
        // For now, just confirming that validation passed by returning normally
    }

    /**
     * Creates a buy order.
     * Buy orders don't require holdings validation, only balance validation.
     *
     * @param accountId the account ID
     * @param instrumentId the instrument ID
     * @param quantity the quantity to buy
     */
    @Transactional
    public void createBuyOrder(Long accountId, Long instrumentId, BigDecimal quantity) {
        // Buy orders would validate against available cash balance, not holdings
        // (Implementation would go here in a full feature)
    }
}
