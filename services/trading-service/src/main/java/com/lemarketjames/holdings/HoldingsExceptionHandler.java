package com.lemarketjames.holdings;

import com.lemarketjames.holdings.exception.InsufficientHoldingsException;
import com.lemarketjames.holdings.exception.UnauthorizedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Translates holdings feature exceptions into structured error responses.
 * 
 * AC1: Handles account-not-found (user is signed in but has no account registered)
 * Generic errors (validation, bad arguments, access denied) are handled by the shared
 * {@link com.lemarketjames.common.error.GlobalExceptionHandler} from libs/common.
 */
@RestControllerAdvice
public class HoldingsExceptionHandler {

    @ExceptionHandler(InsufficientHoldingsException.class)
    public ResponseEntity<Map<String, Object>> handleInsufficientHoldings(InsufficientHoldingsException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                    "success", false,
                    "error", ex.getMessage(),
                    "code", "INSUFFICIENT_HOLDINGS"
                ));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<Map<String, Object>> handleUnauthorized(UnauthorizedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of(
                    "success", false,
                    "error", "Access denied",
                    "code", "ACCOUNT_ACCESS_DENIED"
                ));
    }

    /**
     * AC1: When authenticated user has no account registered.
     * User is signed in (JWT valid) but account lookup fails (should not happen in normal operation).
     * Returns 404 to indicate the account does not exist in the system.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleAccountNotFound(IllegalArgumentException ex) {
        String message = ex.getMessage();
        // Only handle account-not-found cases; let other IllegalArgumentExceptions propagate
        if (message != null && message.contains("No account found")) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(
                        "success", false,
                        "error", "Account not found",
                        "code", "ACCOUNT_NOT_FOUND"
                    ));
        }
        // Re-throw for other IllegalArgumentException cases (let GlobalExceptionHandler deal with them)
        throw ex;
    }
}
