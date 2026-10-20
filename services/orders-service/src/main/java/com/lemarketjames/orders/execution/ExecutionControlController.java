package com.lemarketjames.orders.execution;

import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Disposable-stack controls for E2E execution recovery tests. */
@RestController
@RequestMapping("/internal/execution/control")
@ConditionalOnProperty(prefix = "lmj.execution", name = "control-enabled", havingValue = "true")
public class ExecutionControlController {
    private final ExecutionSettings settings;

    public ExecutionControlController(ExecutionSettings settings) {
        this.settings = settings;
    }

    @GetMapping
    public ControlState state() {
        return new ControlState(settings.isEnabled(), settings.isSettlementAvailable());
    }

    @PutMapping
    public ControlState update(@RequestBody ControlRequest request) {
        if (request.enabled() != null) settings.setEnabled(request.enabled());
        if (request.settlementAvailable() != null) settings.setSettlementAvailable(request.settlementAvailable());
        return state();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
    }

    public record ControlRequest(Boolean enabled, Boolean settlementAvailable) {}
    public record ControlState(boolean enabled, boolean settlementAvailable) {}
}