package com.lemarketjames.accountstatus;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/** The error bodies of the account status endpoints (contract C6), in the shared {success, code, error} shape. */
@RestControllerAdvice
public class AccountStatusExceptionHandler {

    @ExceptionHandler(ClientNotFoundException.class)
    public ResponseEntity<Map<String, Object>> clientNotFound(ClientNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("success", false, "code", ClientNotFoundException.CODE, "error", ex.getMessage()));
    }

    @ExceptionHandler(AccountNotEmptyException.class)
    public ResponseEntity<Map<String, Object>> accountNotEmpty(AccountNotEmptyException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("success", false, "code", AccountNotEmptyException.CODE, "error", ex.getMessage(),
                        "holdings", ex.getContents().holdings(), "openOrders", ex.getContents().openOrders()));
    }
}
