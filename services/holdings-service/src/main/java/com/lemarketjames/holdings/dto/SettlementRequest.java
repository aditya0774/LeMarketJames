package com.lemarketjames.holdings.dto;

import java.math.BigDecimal;

/**
 * Body for {@code POST /internal/holdings/settle}: the fill core-service just recorded, sent so
 * this service can apply its cash/holdings side effects. Called server-to-server, never by a browser.
 */
public class SettlementRequest {

    public enum OrderType { BUY, SELL }

    private Integer orderId;
    private Integer accountId;
    private Integer instrumentId;
    private OrderType orderType;
    private BigDecimal quantity;
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
