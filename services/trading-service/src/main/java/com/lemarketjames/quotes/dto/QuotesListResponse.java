package com.lemarketjames.quotes.dto;

import java.util.List;

/**
 * Successful contract-aligned response for GET /api/quotes (every quote in one call).
 */
public class QuotesListResponse {

    private final boolean success;
    private final List<QuoteDto> quotes;

    public QuotesListResponse(boolean success, List<QuoteDto> quotes) {
        this.success = success;
        this.quotes = quotes;
    }

    public boolean isSuccess() {
        return success;
    }

    public List<QuoteDto> getQuotes() {
        return quotes;
    }
}
