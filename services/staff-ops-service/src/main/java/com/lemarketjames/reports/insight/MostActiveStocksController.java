package com.lemarketjames.reports.insight;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Analyst-only insight for the stocks with the highest traded value this week. */
@RestController
@RequestMapping("/api/v1/reports/insights")
public class MostActiveStocksController {

    private final MostActiveStocksService service;

    public MostActiveStocksController(MostActiveStocksService service) {
        this.service = service;
    }

    @GetMapping("/most-active-stocks")
    public MostActiveStocksResponse currentWeek() {
        return service.currentWeek();
    }
}