package com.lemarketjames.reports.insight;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Top stocks by gross traded value for the current reports-time-zone week. */
public record MostActiveStocksResponse(LocalDate startDate, LocalDate endDate,
                                       List<MostActiveStock> stocks, Instant generatedAt) {
}