package com.lemarketjames.orders;

import com.lemarketjames.orders.exception.InsufficientHoldingsException;
import com.lemarketjames.orders.exception.NotTradableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Translates orders feature exceptions into structured error responses. Generic errors
 * (validation, bad arguments, access denied) are handled by the shared
 * {@link com.lemarketjames.common.error.GlobalExceptionHandler} from libs/common.
 */
@RestControllerAdvice
public class OrdersExceptionHandler {

    @ExceptionHandler(NotTradableException.class)
    public ResponseEntity<Map<String, Object>> handleNotTradable(NotTradableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                    "success", false,
                    "error", ex.getMessage(),
                    "code", "NOT_TRADABLE"
                ));
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
}
