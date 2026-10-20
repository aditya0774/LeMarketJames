package com.lemarketjames.reports.insight;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

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

    /**
     * Traded volume of two date ranges the caller chose. A missing, malformed or reversed date is
     * answered with 400 by the shared exception handler.
     */
    @GetMapping("/volume/compare")
    public VolumeComparisonResponse compare(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate currentFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate currentTo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate previousFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate previousTo) {
        return service.compareRanges(currentFrom, currentTo, previousFrom, previousTo);
    }
}
