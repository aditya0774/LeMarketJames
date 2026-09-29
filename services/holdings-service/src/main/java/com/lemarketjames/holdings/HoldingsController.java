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
@RequestMapping({"/api/v1/holdings", "/api/holdings"})
public class HoldingsController {

    private final HoldingsService holdingsService;

    public HoldingsController(HoldingsService holdingsService) {
        this.holdingsService = holdingsService;
    }

    /**
     * AC1: Retrieve all holdings for the authenticated user, scoped from JWT identity.
     * 
     * Returns each stock held with its quantity and current market value, as currently recorded
     * in the database. Returns empty array if user holds no stocks (AC2).
     * 
     * AC3: Holdings persist exactly as seeded; platform restarts maintain the same data.
     *
     * @return 200 OK with holdings array and success flag
     * @return 401 Unauthorized if JWT missing/expired (not signed in — AC1 requirement)
     * @return 404 Not Found if signed-in user has no account (edge case, should not happen)
     */
    @GetMapping
    public ResponseEntity<HoldingsResponse> getHoldings() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        HoldingsResponse response = holdingsService.getHoldingsForAccount(username);
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
