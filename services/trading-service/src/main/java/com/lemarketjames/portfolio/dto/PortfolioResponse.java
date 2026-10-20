package com.lemarketjames.portfolio.dto;

/** GET /api/v1/portfolio response envelope: {"success": true, "balance": {...}}. */
public class PortfolioResponse {

    private final boolean success;
    private final PortfolioBalance balance;

    public PortfolioResponse(PortfolioBalance balance) {
        this.success = true;
        this.balance = balance;
    }

    public boolean isSuccess() {
        return success;
    }

    public PortfolioBalance getBalance() {
        return balance;
    }
}
