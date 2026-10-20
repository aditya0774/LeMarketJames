package com.lemarketjames.watchlist;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/** Maps watchlist refusals to the status and body contract C6 gives each of them. */
@RestControllerAdvice
public class WatchlistExceptionHandler {

    @ExceptionHandler(WatchlistException.class)
    public ResponseEntity<Map<String, Object>> handle(WatchlistException ex) {
        return switch (ex.getCode()) {
            // C6 gives an unknown symbol no code, only the error text.
            case WatchlistService.SYMBOL_NOT_FOUND -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "error", ex.getMessage()));
            case WatchlistService.ALREADY_WATCHED -> body(HttpStatus.CONFLICT, ex);
            // ACCOUNT_NOT_FOUND: staff have no account, answered like holdings does.
            default -> body(HttpStatus.NOT_FOUND, ex);
        };
    }

    private static ResponseEntity<Map<String, Object>> body(HttpStatus status, WatchlistException ex) {
        return ResponseEntity.status(status).body(Map.of(
                "success", false,
                "code", ex.getCode(),
                "error", ex.getMessage()));
    }
}
