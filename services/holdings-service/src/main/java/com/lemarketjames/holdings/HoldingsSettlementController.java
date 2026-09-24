package com.lemarketjames.holdings;

import com.lemarketjames.holdings.dto.SettlementRequest;
import com.lemarketjames.holdings.service.HoldingsSettlementService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal, server-to-server only: core-service calls this directly (Docker hostname, bypassing
 * the gateway) when an order transitions to FILLED. Deliberately outside /api/v1/** so no gateway
 * route pattern can accidentally expose it to a browser (see SecurityConfig's /internal/** permit).
 */
@RestController
public class HoldingsSettlementController {

    private final HoldingsSettlementService settlementService;

    public HoldingsSettlementController(HoldingsSettlementService settlementService) {
        this.settlementService = settlementService;
    }

    @PostMapping("/internal/holdings/settle")
    public ResponseEntity<Void> settle(@RequestBody SettlementRequest request) {
        settlementService.settle(request);
        return ResponseEntity.ok().build();
    }
}
