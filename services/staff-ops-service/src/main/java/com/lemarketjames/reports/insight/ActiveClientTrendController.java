package com.lemarketjames.reports.insight;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The active-client trend for analysts. Access needs no code here: every path under
 * {@code /api/v1/reports/} is ANALYST-only in {@code SecurityConfig} (contract C7). Kept apart from
 * {@link VolumeInsightController} so each controller has one insight to serve.
 */
@RestController
@RequestMapping("/api/v1/reports/insights")
public class ActiveClientTrendController {

    private final ActiveClientTrendService service;

    public ActiveClientTrendController(ActiveClientTrendService service) {
        this.service = service;
    }

    /** Distinct trading clients per day over the last 30 days. */
    @GetMapping("/active-clients")
    public ActiveClientTrendResponse activeClients() {
        return service.trend();
    }
}
