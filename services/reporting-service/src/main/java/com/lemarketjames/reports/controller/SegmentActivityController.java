package com.lemarketjames.reports.controller;

import com.lemarketjames.reports.dto.SegmentActivityResponse;
import com.lemarketjames.reports.service.SegmentActivityService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** SecurityConfig enforces ANALYST access for every report path. */
@RestController
public class SegmentActivityController {
    private final SegmentActivityService service;

    public SegmentActivityController(SegmentActivityService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/reports/activity")
    public SegmentActivityResponse activity(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return new SegmentActivityResponse(true, service.report(startDate, endDate));
    }
}
