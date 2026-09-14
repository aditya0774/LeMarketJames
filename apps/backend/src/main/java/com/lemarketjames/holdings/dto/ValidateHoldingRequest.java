package com.lemarketjames.holdings.dto;

import java.math.BigDecimal;

public class ValidateHoldingRequest {
    private Integer accountId;
    private Integer instrumentId;
    private BigDecimal sellQuantity;

    public ValidateHoldingRequest() {}

    public ValidateHoldingRequest(Integer accountId, Integer instrumentId, BigDecimal sellQuantity) {
        this.accountId = accountId;
        this.instrumentId = instrumentId;
        this.sellQuantity = sellQuantity;
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

    public BigDecimal getSellQuantity() {
        return sellQuantity;
    }

    public void setSellQuantity(BigDecimal sellQuantity) {
        this.sellQuantity = sellQuantity;
    }
}
