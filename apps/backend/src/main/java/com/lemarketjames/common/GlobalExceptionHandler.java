package com.lemarketjames.common;

import com.lemarketjames.holdings.exception.InsufficientHoldingsException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/** Translates feature-thrown exceptions into structured, consistent error responses. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(ValidationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("errors", ex.getFieldErrors()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(InsufficientHoldingsException.class)
    public ResponseEntity<Map<String, Object>> handleInsufficientHoldings(InsufficientHoldingsException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                    "success", false,
                    "error", ex.getMessage(),
                    "code", "INSUFFICIENT_HOLDINGS"
                ));
    }

    /**
     * Handles UnauthorizedAccessException when a user tries to access data they don't own.
     * 
     * Returns 403 Forbidden with a generic error message (no details leaked to client).
     * The actual details of who tried to access what are logged internally for security auditing.
     */
    @ExceptionHandler(UnauthorizedAccessException.class)
    public ResponseEntity<Map<String, Object>> handleUnauthorizedAccess(UnauthorizedAccessException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of(
                    "success", false,
                    "error", "You do not have permission to access this resource",
                    "code", "UNAUTHORIZED_ACCESS"
                ));
    }
}
