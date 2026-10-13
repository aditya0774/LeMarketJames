package com.lemarketjames.reports.insight;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Insights for analysts. Access needs no code here: every path under {@code /api/v1/reports/} is
 * ANALYST-only in {@code SecurityConfig} (contract C7).
 */
@RestController
@RequestMapping("/api/v1/reports/insights")
public class VolumeInsightController {

    private final VolumeInsightService service;

    public VolumeInsightController(VolumeInsightService service) {
        this.service = service;
    }

    /** Traded volume of the last completed period against the one before it. */
    @GetMapping("/volume")
    public VolumeInsightResponse volume(@RequestParam(required = false) String periodType) {
        // Validated in the service, so a missing and a wrong value both get the same 400 body.
        return service.compareWithPreviousPeriod(periodType);
    }
}
