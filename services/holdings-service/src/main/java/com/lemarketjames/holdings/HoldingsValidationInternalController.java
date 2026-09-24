package com.lemarketjames.holdings;

import com.lemarketjames.holdings.dto.InternalHoldingsValidationRequest;
import com.lemarketjames.holdings.exception.InsufficientHoldingsException;
import com.lemarketjames.holdings.service.HoldingsService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Internal, server-to-server only: core-service calls this directly (Docker hostname, bypassing
 * the gateway) to re-check a SELL order's holdings before persisting it, since the browser's own
 * {@code /api/v1/holdings/validate} call is advisory only. Deliberately outside /api/v1/** so no
 * gateway route pattern can accidentally expose it to a browser (see SecurityConfig's /internal/**
 * permit).
 */
@RestController
public class HoldingsValidationInternalController {

    private final HoldingsService holdingsService;

    public HoldingsValidationInternalController(HoldingsService holdingsService) {
        this.holdingsService = holdingsService;
    }

    @PostMapping("/internal/holdings/validate")
    public ResponseEntity<Map<String, Object>> validate(@RequestBody InternalHoldingsValidationRequest request) {
        try {
            holdingsService.validateSufficientHoldings(
                request.getAccountId(),
                request.getUsername(),
                request.getInstrumentId(),
                request.getSellQuantity()
            );
            return ResponseEntity.ok(Map.of("success", true));
        } catch (InsufficientHoldingsException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                    "success", false,
                    "error", e.getMessage()
                ));
        }
    }
}
