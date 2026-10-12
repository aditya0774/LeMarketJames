package com.lemarketjames.reports.service;

import com.lemarketjames.reports.dto.SegmentActivityRow;
import com.lemarketjames.reports.period.ReportCalendar;
import com.lemarketjames.reports.repository.SegmentActivityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

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
}
