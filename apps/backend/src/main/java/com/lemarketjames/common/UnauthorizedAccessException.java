package com.lemarketjames.common;

/**
 * Exception thrown when a user tries to access data that doesn't belong to them.
 * 
 * Example: User "joanna_trader" tries to view account 2's holdings (when joanna owns account 1)
 */
public class UnauthorizedAccessException extends RuntimeException {
    
    /**
     * Constructor with error message.
     * 
     * @param message description of what was unauthorized
     */
    public UnauthorizedAccessException(String message) {
        super(message);
    }
    
    /**
     * Constructor with message and cause.
     * 
     * @param message description of what was unauthorized
     * @param cause the underlying exception that caused this
     */
    public UnauthorizedAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
