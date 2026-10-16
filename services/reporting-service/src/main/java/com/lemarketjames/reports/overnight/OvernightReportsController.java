package com.lemarketjames.reports.overnight;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The overnight reports list for the Analyst dashboard. Access needs no code here: every path
 * under {@code /api/v1/reports/} is ANALYST-only in {@code SecurityConfig} (contract C7).
 */
@RestController
public class OvernightReportsController {

    private final OvernightReportsService service;

    public OvernightReportsController(OvernightReportsService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/reports/overnight")
    public OvernightReportsResponse list() {
        return service.recentRuns();
    }
}
