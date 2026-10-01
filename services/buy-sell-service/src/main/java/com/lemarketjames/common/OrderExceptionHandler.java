package com.lemarketjames.common;

import com.lemarketjames.orders.entity.RejectionReason;
import com.lemarketjames.orders.exception.InsufficientHoldingsException;
import com.lemarketjames.orders.exception.InvalidStatusTransitionException;
import com.lemarketjames.orders.exception.NotTradableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Translates buy-sell-service feature exceptions into structured error responses. Generic errors
 * (validation, bad arguments, access denied) are handled by the shared
 * {@link com.lemarketjames.common.error.GlobalExceptionHandler} from libs/common.
 */
@RestControllerAdvice
public class OrderExceptionHandler {

    @ExceptionHandler(NotTradableException.class)
    public ResponseEntity<Map<String, Object>> handleNotTradable(NotTradableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                    "success", false,
                    "error", ex.getMessage(),
                    "code", RejectionReason.NOT_TRADABLE.name()
                ));
    }

    @ExceptionHandler(InsufficientHoldingsException.class)
    public ResponseEntity<Map<String, Object>> handleInsufficientHoldings(InsufficientHoldingsException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                    "success", false,
                    "error", ex.getMessage(),
                    "code", RejectionReason.INSUFFICIENT_HOLDINGS.name()
                ));
    }

    // The order exists but is somewhere its lifecycle doesn't allow this move from (contract C1).
    @ExceptionHandler(InvalidStatusTransitionException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidTransition(InvalidStatusTransitionException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of(
                    "success", false,
                    "error", ex.getMessage(),
                    "code", "INVALID_STATUS_TRANSITION"
                ));
    }

}
