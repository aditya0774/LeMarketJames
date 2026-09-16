package com.lemarketjames.holdings;

import com.lemarketjames.holdings.dto.HoldingsResponse;
import com.lemarketjames.holdings.dto.ValidateHoldingRequest;
import com.lemarketjames.holdings.exception.InsufficientHoldingsException;
import com.lemarketjames.holdings.service.HoldingsService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for holdings endpoints.
 * AC1: GET /api/holdings - retrieve user's holdings
 * AC2: POST /api/holdings/validate - validate sufficient holdings for sell order
 */
@RestController
@RequestMapping("/api/holdings")
public class HoldingsController {

    private final HoldingsService holdingsService;

    public HoldingsController(HoldingsService holdingsService) {
        this.holdingsService = holdingsService;
    }

    /**
     * AC1: Retrieve all holdings for the authenticated user.
     * AC2: Requests scoped to authenticated user
     * 
     * @param accountId the account ID
     * @return holdings response with success flag and holdings list
     */
    @GetMapping
    public ResponseEntity<HoldingsResponse> getHoldings(
            @RequestParam Integer accountId) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        HoldingsResponse response = holdingsService.getHoldingsForAccount(accountId, username);
        return ResponseEntity.ok(response);
    }

    /**
     * AC1: Unauthorized data access prevented
     * AC2: Validate that user has sufficient holdings to sell a given quantity.
     * Returns 200 if validation passes, 403 if unauthorized, 400 if insufficient holdings.
     * @param request the validation request
     * @return success/error response
     */
    @PostMapping("/validate")
    public ResponseEntity<?> validateHoldings(
            @RequestBody ValidateHoldingRequest request) {
        try {
            String username = SecurityContextHolder.getContext().getAuthentication().getName();
            holdingsService.validateSufficientHoldings(
                request.getAccountId(),
                username,
                request.getInstrumentId(),
                request.getSellQuantity()
            );
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Sufficient holdings available"
            ));
        } catch (InsufficientHoldingsException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                    "success", false,
                    "error", e.getMessage()
                ));
        }
    }
}
