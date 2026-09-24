package com.lemarketjames.quotes.dto;

/**
 * Successful contract-aligned response for GET /api/quotes/{symbol}.
 */
public class QuoteSuccessResponse {

    private final boolean success;
    private final QuoteDto quote;

    public QuoteSuccessResponse(boolean success, QuoteDto quote) {
        this.success = success;
        this.quote = quote;
    }

    public boolean isSuccess() {
        return success;
    }

    public QuoteDto getQuote() {
        return quote;
    }
}
