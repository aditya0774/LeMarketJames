package com.lemarketjames.reports.service;

import com.lemarketjames.reports.dto.PeriodAggregation;
import com.lemarketjames.reports.dto.TradeReportRequest;
import com.lemarketjames.reports.dto.TradeReportResponse;
import com.lemarketjames.reports.entity.ReportingTrade;
import com.lemarketjames.reports.period.ReportBucket;
import com.lemarketjames.reports.period.ReportCalendar;
import com.lemarketjames.reports.period.ReportPeriod;
import com.lemarketjames.reports.repository.ReportingTradeRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Business logic for trade aggregation reports.
 * Groups settled trades by period (day, week, month, year) and aggregates counts and values.
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

        // Query trades within the date range
        ReportPeriod queryRange = reportCalendar.between(from, to);
        List<ReportingTrade> trades = tradeRepository.findByFilledAtBetween(queryRange.start(), queryRange.end());

        // Aggregate trades by period
        Map<String, PeriodAggregation> aggregations = new LinkedHashMap<>();

        for (ReportingTrade trade : trades) {
            // Determine which period this trade belongs to
            LocalDate tradeLocalDate = trade.getFilledAt().atZone(queryZone).toLocalDate();
            LocalDate bucketFirstDay = calculateBucketFirstDay(tradeLocalDate, periodType);
            
            // Format period key
            String periodKey = formatPeriod(bucketFirstDay, periodType);

            // Get or create aggregation for this period
            aggregations.computeIfAbsent(periodKey, key -> 
                new PeriodAggregation(key, 0, 0, 0, BigDecimal.ZERO)
            );

            // Update aggregation
            PeriodAggregation agg = aggregations.get(periodKey);
            agg.setTradeCount(agg.getTradeCount() + 1);
            
            if ("BUY".equals(trade.getSide())) {
                agg.setBuyCount(agg.getBuyCount() + 1);
            } else if ("SELL".equals(trade.getSide())) {
                agg.setSellCount(agg.getSellCount() + 1);
            }

            BigDecimal tradeValue = trade.getGrossAmount() != null ? trade.getGrossAmount() : BigDecimal.ZERO;
            agg.setTotalValue(agg.getTotalValue().add(tradeValue));
        }

        // Build response
        List<PeriodAggregation> data = new ArrayList<>(aggregations.values());
        LocalDateTime generatedAt = LocalDateTime.now(clock);

        return new TradeReportResponse(data, generatedAt);
    }

    /**
     * Calculate the first day of the bucket that the given day falls in.
     * Mirrors ReportBucket logic without accessing private methods.
     */
    private LocalDate calculateBucketFirstDay(LocalDate day, ReportBucket bucket) {
        return switch (bucket) {
            case DAY -> day;
            case WEEK -> day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTH -> day.withDayOfMonth(1);
            case YEAR -> day.withDayOfYear(1);
        };
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
