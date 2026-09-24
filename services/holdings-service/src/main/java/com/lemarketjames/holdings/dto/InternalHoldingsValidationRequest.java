package com.lemarketjames.holdings.dto;

import java.math.BigDecimal;

/**
 * Body for {@code POST /internal/holdings/validate}: core-service re-checking a SELL order's
 * holdings before persisting it. Unlike {@link ValidateHoldingRequest} (the browser-facing,
 * JWT-authenticated {@code /api/v1/holdings/validate}), this call has no security context of its
 * own, so the username is carried explicitly. Called server-to-server, never by a browser.
 */
public class InternalHoldingsValidationRequest {

    private Integer accountId;
    private String username;
    private Integer instrumentId;
    private BigDecimal sellQuantity;

    public InternalHoldingsValidationRequest() {}

    public Integer getAccountId() {
        return accountId;
    }

    public void setAccountId(Integer accountId) {
        this.accountId = accountId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
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
