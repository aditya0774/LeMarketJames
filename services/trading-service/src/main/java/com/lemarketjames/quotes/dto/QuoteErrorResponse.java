package com.lemarketjames.quotes.dto;

/**
 * Error contract for quote lookups that cannot be resolved.
 */
public class QuoteErrorResponse {

    private final boolean success;
    private final String error;

    public QuoteErrorResponse(boolean success, String error) {
        this.success = success;
        this.error = error;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getError() {
        return error;
    }
}
