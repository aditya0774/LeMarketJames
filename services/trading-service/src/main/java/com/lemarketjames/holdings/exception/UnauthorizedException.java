package com.lemarketjames.holdings.exception;

/**
 * Thrown when a user attempts to access account data they do not own.
 * 
 * AC1: Unauthorized data access prevented
 * This exception is raised in the service layer when ownership validation fails.
 */
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }
}
