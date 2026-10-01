package com.lemarketjames.holdings.dto;

import java.math.BigDecimal;

/**
 * Body for {@code POST /internal/holdings/settle}: the fill buy-sell-service just recorded, sent so
 * this service can apply its cash/holdings side effects. Called server-to-server, never by a browser.
 */
public class SettlementRequest {

    public enum OrderType { BUY, SELL }

    @jakarta.validation.constraints.NotNull
    @jakarta.validation.constraints.Positive
    private Integer orderId;
    @jakarta.validation.constraints.NotNull
    @jakarta.validation.constraints.Positive
    private Integer accountId;
    @jakarta.validation.constraints.NotNull
    @jakarta.validation.constraints.Positive
    private Integer instrumentId;
    @jakarta.validation.constraints.NotNull
    private OrderType orderType;
    @jakarta.validation.constraints.NotNull
    @jakarta.validation.constraints.Positive
    @jakarta.validation.constraints.Digits(integer = 10, fraction = 4)
    private BigDecimal quantity;
    @jakarta.validation.constraints.NotNull
    @jakarta.validation.constraints.Positive
    @jakarta.validation.constraints.Digits(integer = 10, fraction = 4)
    private BigDecimal pricePerUnit;

    public SettlementRequest() {}

    public Integer getOrderId() {
        return orderId;
    }

    public void setOrderId(Integer orderId) {
        this.orderId = orderId;
    }

    public Integer getAccountId() {
        return accountId;
    }

    public void setAccountId(Integer accountId) {
        this.accountId = accountId;
    }

    public Integer getInstrumentId() {
        return instrumentId;
    }

    public void setInstrumentId(Integer instrumentId) {
        this.instrumentId = instrumentId;
    }

    public OrderType getOrderType() {
        return orderType;
    }

    public void setOrderType(OrderType orderType) {
        this.orderType = orderType;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getPricePerUnit() {
        return pricePerUnit;
    }

    public void setPricePerUnit(BigDecimal pricePerUnit) {
        this.pricePerUnit = pricePerUnit;
    }
}
