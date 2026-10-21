package com.lemarketjames.reports.insight;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Top stocks by gross traded value for the current reports-time-zone week so far, each compared
 * with the same days of the week before ({@code previousStartDate} to {@code previousEndDate}), so
 * a Tuesday is never set against a whole week.
 */
public record MostActiveStocksResponse(LocalDate startDate, LocalDate endDate,
                                       LocalDate previousStartDate, LocalDate previousEndDate,
                                       List<MostActiveStock> stocks, Instant generatedAt) {
}
