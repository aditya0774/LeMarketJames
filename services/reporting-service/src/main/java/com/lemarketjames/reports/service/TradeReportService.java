package com.lemarketjames.reports.service;

import com.lemarketjames.reports.dto.PeriodAggregation;
import com.lemarketjames.reports.dto.TradeReportRequest;
import com.lemarketjames.reports.dto.TradeReportResponse;
import com.lemarketjames.reports.period.ReportBucket;
import com.lemarketjames.reports.period.ReportCalendar;
import com.lemarketjames.reports.period.ReportPeriod;
import com.lemarketjames.reports.repository.ReportingTradeRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.IsoFields;
import java.util.List;
import java.util.Locale;

/**
 * Business logic for trade aggregation reports.
 * Counts and totals settled trades per period (day, week, month, year); the grouping itself
 * runs in the database ({@link ReportingTradeRepository#aggregateByPeriod}), and this class
 * validates the request and labels each period.
 */
@Service
public class TradeReportService {

    private final ReportingTradeRepository tradeRepository;
    private final ReportCalendar reportCalendar;
    private final Clock clock;

    public TradeReportService(ReportingTradeRepository tradeRepository, ReportCalendar reportCalendar, Clock clock) {
        this.tradeRepository = tradeRepository;
        this.reportCalendar = reportCalendar;
        this.clock = clock;
    }

    /**
     * Generate a trade aggregation report.
     *
     * @param request contains periodType (required), from/to (optional), timeZone (optional)
     * @return response with aggregated data by period and generation timestamp
     * @throws IllegalArgumentException if periodType is missing or invalid, or date range is invalid
     */
    public TradeReportResponse generateReport(TradeReportRequest request) {
        // Validate periodType
        String periodTypeStr = request.getPeriodType();
        if (periodTypeStr == null || periodTypeStr.isBlank()) {
            throw new IllegalArgumentException("periodType is required (DAILY, WEEKLY, MONTHLY, YEARLY)");
        }

        ReportBucket periodType;
        try {
            periodType = ReportBucket.valueOf(periodTypeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid periodType: " + periodTypeStr + 
                    ". Valid values: DAILY, WEEKLY, MONTHLY, YEARLY");
        }

        // Apply defaults
        LocalDate from = request.getFrom();
        LocalDate to = request.getTo();
        String timeZoneStr = request.getTimeZone();

        if (from == null || to == null) {
            LocalDate today = LocalDate.now(reportCalendar.zone());
            from = today.minusDays(365);
            to = today;
        }

        // Get query time zone (report zone, not for display)
        ZoneId queryZone = reportCalendar.zone();

        // Group in the database, so only one row per period comes back instead of every trade.
        // Loading every trade held a pooled connection and the shared database long enough to
        // slow live order execution under report load (LMKT-44).
        ReportPeriod queryRange = reportCalendar.between(from, to);
        // ReportBucket's names are date_trunc's units: day, week, month, year.
        String unit = periodType.name().toLowerCase(Locale.ROOT);
        List<PeriodAggregation> data = tradeRepository
                .aggregateByPeriod(unit, queryZone.getId(), queryRange.start(), queryRange.end())
                .stream()
                .map(row -> new PeriodAggregation(
                        formatPeriod(LocalDate.parse(row.getPeriodStart()), periodType),
                        Math.toIntExact(row.getTradeCount()),
                        Math.toIntExact(row.getBuyCount()),
                        Math.toIntExact(row.getSellCount()),
                        row.getTotalValue()))
                .toList();

        LocalDateTime generatedAt = LocalDateTime.now(clock);

        return new TradeReportResponse(data, generatedAt);
    }

    /**
     * Format a period's first day according to the bucket type.
     *
     * @param firstDay the first day of the period in the report time zone
     * @param bucket   the period type (DAY, WEEK, MONTH, YEAR)
     * @return formatted period string (YYYY-MM-DD, YYYY-Www, YYYY-MM, YYYY)
     */
    private String formatPeriod(LocalDate firstDay, ReportBucket bucket) {
        return switch (bucket) {
            case DAY -> firstDay.toString();
            case WEEK -> {
                int week = firstDay.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
                yield String.format("%d-W%02d", firstDay.getYear(), week);
            }
            case MONTH -> String.format("%d-%02d", firstDay.getYear(), firstDay.getMonthValue());
            case YEAR -> String.valueOf(firstDay.getYear());
        };
    }
}
