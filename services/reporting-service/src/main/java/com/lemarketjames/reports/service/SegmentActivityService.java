package com.lemarketjames.reports.service;

import com.lemarketjames.reports.dto.SegmentActivityRow;
import com.lemarketjames.reports.period.ReportCalendar;
import com.lemarketjames.reports.repository.SegmentActivityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.temporal.ChronoUnit;
import com.lemarketjames.reports.dto.SegmentComparisonResponse;

@Service
@Transactional(readOnly = true)
public class SegmentActivityService {
    private static final int DEFAULT_LOOKBACK_DAYS = 30;
    private final SegmentActivityRepository repository;
    private final ReportCalendar calendar;

    public SegmentActivityService(SegmentActivityRepository repository, ReportCalendar calendar) {
        this.repository = repository;
        this.calendar = calendar;
    }

    public List<SegmentActivityRow> report(LocalDate startDate, LocalDate endDate) {
        // Defaults use the report zone too, including around UTC midnight.
        LocalDate today = LocalDate.now(calendar.zone());
        var period = calendar.between(
                startDate == null ? today.minusDays(DEFAULT_LOOKBACK_DAYS) : startDate,
                endDate == null ? today : endDate);
        return repository.aggregate(period.start(), period.end());
    }

    public SegmentComparisonResponse compare(LocalDate startDate, LocalDate endDate,
            LocalDate comparisonStartDate, LocalDate comparisonEndDate) {
        LocalDate today = LocalDate.now(calendar.zone());
        LocalDate start = startDate == null ? today.minusDays(DEFAULT_LOOKBACK_DAYS) : startDate;
        LocalDate end = endDate == null ? today : endDate;
        var currentPeriod = calendar.between(start, end);
        if ((comparisonStartDate == null) != (comparisonEndDate == null)) {
            throw new IllegalArgumentException("Provide both comparison dates or neither");
        }
        // Count calendar days, not hours: daylight-saving changes must not shift the comparison.
        LocalDate previousEnd = comparisonEndDate == null ? start.minusDays(1) : comparisonEndDate;
        LocalDate previousStart = comparisonStartDate == null
                ? start.minusDays(ChronoUnit.DAYS.between(start, end) + 1) : comparisonStartDate;
        var previousPeriod = calendar.between(previousStart, previousEnd);
        var current = repository.aggregate(currentPeriod.start(), currentPeriod.end());
        var previous = repository.aggregate(previousPeriod.start(), previousPeriod.end());
        Map<String, SegmentActivityRow> currentBySegment = new TreeMap<>();
        Map<String, SegmentActivityRow> previousBySegment = new TreeMap<>();
        current.forEach(row -> currentBySegment.put(row.segment(), row));
        previous.forEach(row -> previousBySegment.put(row.segment(), row));
        var segments = new TreeSet<>(currentBySegment.keySet());
        segments.addAll(previousBySegment.keySet());
        var rows = segments.stream().map(segment -> {
            var zero = new SegmentActivityRow(segment, 0, 0, BigDecimal.ZERO);
            var c = currentBySegment.getOrDefault(segment, zero);
            var p = previousBySegment.getOrDefault(segment, zero);
            return new SegmentComparisonResponse.Row(segment, c, p,
                    change(BigDecimal.valueOf(c.activeClients()), BigDecimal.valueOf(p.activeClients())),
                    change(BigDecimal.valueOf(c.tradeCount()), BigDecimal.valueOf(p.tradeCount())),
                    change(c.totalTradedValue(), p.totalTradedValue()));
        }).toList();
        return new SegmentComparisonResponse(true, current, start, end, previousStart, previousEnd,
                calendar.zone().getId(), rows, repository.aggregateAllTime());
    }

    private static SegmentComparisonResponse.Change change(BigDecimal current, BigDecimal previous) {
        var difference = current.subtract(previous);
        return new SegmentComparisonResponse.Change(difference, previous.signum() == 0 ? null
                : difference.multiply(BigDecimal.valueOf(100)).divide(previous, 2, RoundingMode.HALF_UP));
    }
}
