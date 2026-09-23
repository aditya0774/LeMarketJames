package com.lemarketjames.common;

import com.lemarketjames.holdings.exception.InsufficientHoldingsException;
import com.lemarketjames.holdings.exception.UnauthorizedException;
import com.lemarketjames.orders.exception.NotTradableException;
import com.lemarketjames.sessions.exception.SessionExpiredException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Translates core-service feature exceptions into structured error responses. Generic errors
 * (validation, bad arguments, access denied) are handled by the shared
 * {@link com.lemarketjames.common.error.GlobalExceptionHandler} from libs/common.
 */
@RestControllerAdvice
public class CoreExceptionHandler {

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

    @ExceptionHandler(NotTradableException.class)
    public ResponseEntity<Map<String, Object>> handleNotTradable(NotTradableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                    "success", false,
                    "error", ex.getMessage(),
                    "code", "NOT_TRADABLE"
                ));
    }

    @ExceptionHandler(SessionExpiredException.class)
    public ResponseEntity<Map<String, Object>> handleSessionExpired(SessionExpiredException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of(
                    "success", false,
                    "error", ex.getMessage(),
                    "code", "SESSION_EXPIRED"
                ));
    }
}
