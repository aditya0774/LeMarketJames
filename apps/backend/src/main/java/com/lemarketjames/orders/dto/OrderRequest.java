package com.lemarketjames.orders.dto;

import java.math.BigDecimal;

/**
 * DTO for order creation requests.
 * Contains data sent by the client in the HTTP request body.
 */
public class OrderRequest {

    private Integer accountId;
    private Integer instrumentId;
    private String orderType;
    private BigDecimal quantity;

    // Constructors
    public OrderRequest() {}

    public OrderRequest(Integer accountId, Integer instrumentId, String orderType, BigDecimal quantity) {
        this.accountId = accountId;
        this.instrumentId = instrumentId;
        this.orderType = orderType;
        this.quantity = quantity;
    }

    // Getters and Setters
    public Integer getAccountId() { return accountId; }
    public void setAccountId(Integer accountId) { this.accountId = accountId; }

    public Integer getInstrumentId() { return instrumentId; }
    public void setInstrumentId(Integer instrumentId) { this.instrumentId = instrumentId; }

    public String getOrderType() { return orderType; }
    public void setOrderType(String orderType) { this.orderType = orderType; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
}
