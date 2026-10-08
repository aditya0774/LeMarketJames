package com.lemarketjames.reports.controller;

import com.lemarketjames.reports.dto.TradeReportRequest;
import com.lemarketjames.reports.dto.TradeReportResponse;
import com.lemarketjames.reports.service.TradeReportService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * REST controller for trade aggregation reports.
 * Exposes endpoints under /api/v1/reports for analysts only.
 * Role enforcement is handled by SecurityConfig.
 */
@RestController
@RequestMapping("/api/v1/reports")
public class TradeReportController {

    private final TradeReportService tradeReportService;

    public TradeReportController(TradeReportService tradeReportService) {
        this.tradeReportService = tradeReportService;
    }

    /**
     * Generate a trade aggregation report grouped by the specified period.
     *
     * @param periodType required: DAILY, WEEKLY, MONTHLY, or YEARLY
     * @param from       optional: start date (YYYY-MM-DD); defaults to 365 days ago
     * @param to         optional: end date (YYYY-MM-DD); defaults to today
     * @param timeZone   optional: IANA time zone ID; defaults to configured reports time zone
     * @return 200 with TradeReportResponse, 400 if periodType invalid or dates bad, 401 if not authenticated, 403 if not ANALYST
     */
    @GetMapping("/trades")
    @PreAuthorize("hasRole('ANALYST')")
    public ResponseEntity<TradeReportResponse> getTradeReport(
            @RequestParam(value = "periodType", required = true) String periodType,
            @RequestParam(value = "from", required = false) LocalDate from,
            @RequestParam(value = "to", required = false) LocalDate to,
            @RequestParam(value = "timeZone", required = false) String timeZone) {

        TradeReportRequest request = new TradeReportRequest(periodType, from, to, timeZone);
        TradeReportResponse response = tradeReportService.generateReport(request);

        return ResponseEntity.ok(response);
    }
}
