package com.lemarketjames.reports.insight;

import com.lemarketjames.reports.insight.ActiveClientTrendResponse.DayCount;
import com.lemarketjames.reports.period.ReportCalendar;
import com.lemarketjames.reports.period.ReportPeriod;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the daily count of distinct trading clients over the last {@value #WINDOW_DAYS} days,
 * today included (counted so far). Each day is its own range from {@link ReportCalendar}, so days,
 * evenings that are already "tomorrow" in UTC and daylight-saving changes follow the same rules as
 * every other report; a quiet day is a {@code 0}, never a gap. Nothing is cached.
 */
@Service
public class ActiveClientTrendService {

    /** Length of the trend window, in report-time-zone days. */
    static final int WINDOW_DAYS = 30;

    private final ActiveClientTrendRepository repository;
    private final ReportCalendar calendar;
    private final Clock clock;

    public ActiveClientTrendService(ActiveClientTrendRepository repository, ReportCalendar calendar, Clock clock) {
        this.repository = repository;
        this.calendar = calendar;
        this.clock = clock;
    }

    public ActiveClientTrendResponse trend() {
        LocalDate today = LocalDate.now(clock.withZone(calendar.zone()));
        LocalDate first = today.minusDays(WINDOW_DAYS - 1L);

        List<DayCount> days = new ArrayList<>(WINDOW_DAYS);
        for (LocalDate day = first; !day.isAfter(today); day = day.plusDays(1)) {
            ReportPeriod period = calendar.between(day, day);
            days.add(new DayCount(day, repository.countActiveClientsBetween(period.start(), period.end())));
        }
        return new ActiveClientTrendResponse(first, today, List.copyOf(days), Instant.now(clock));
    }
}
