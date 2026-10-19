package com.lemarketjames.cash;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/** Maps cash refusals to the stable status and body consumed by the Move money dialog. */
@RestControllerAdvice
public class CashMovementExceptionHandler {

    @ExceptionHandler(CashMovementException.class)
    public ResponseEntity<Map<String, Object>> handle(CashMovementException ex) {
        HttpStatus status = CashMovementService.ACCOUNT_NOT_FOUND.equals(ex.getCode())
                ? HttpStatus.NOT_FOUND
                : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(Map.of(
                "success", false,
                "code", ex.getCode(),
                "error", ex.getMessage()));
    }
}
