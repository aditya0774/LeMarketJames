package com.lemarketjames.holdings.dto;

import java.util.List;

public class HoldingsResponse {
    private boolean success;
    private List<HoldingDto> holdings;
    private String message;

    public HoldingsResponse() {}

    public HoldingsResponse(boolean success, List<HoldingDto> holdings) {
        this.success = success;
        this.holdings = holdings;
    }

    public HoldingsResponse(boolean success, List<HoldingDto> holdings, String message) {
        this.success = success;
        this.holdings = holdings;
        this.message = message;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public List<HoldingDto> getHoldings() {
        return holdings;
    }

    public void setHoldings(List<HoldingDto> holdings) {
        this.holdings = holdings;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
