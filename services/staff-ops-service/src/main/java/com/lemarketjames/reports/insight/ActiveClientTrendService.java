package com.lemarketjames.reports.insight;

import com.lemarketjames.reports.insight.ActiveClientTrendResponse.DayCount;
import com.lemarketjames.reports.insight.ActiveClientTrendResponse.WeekAverage;
import com.lemarketjames.reports.period.ReportCalendar;
import com.lemarketjames.reports.period.ReportPeriod;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
 *
 * <p>The flag (BR-17) compares the last seven whole days with the seven before. Whole weeks, so a
 * weekend without trades is in both, and today, still running, is in neither.
 */
@Service
public class ActiveClientTrendService {

    /** Length of the trend window, in report-time-zone days. */
    static final int WINDOW_DAYS = 30;

    /** Days in each of the two periods the flag compares. */
    static final int WEEK_DAYS = 7;

    private final ActiveClientTrendRepository repository;
    private final ReportCalendar calendar;
    private final InsightFlagRule flagRule;
    private final Clock clock;

    public ActiveClientTrendService(ActiveClientTrendRepository repository, ReportCalendar calendar,
                                    InsightFlagRule flagRule, Clock clock) {
        this.repository = repository;
        this.calendar = calendar;
        this.flagRule = flagRule;
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
        // Both weeks lie inside the window, so they are read from the counts above, not queried again.
        int todayIndex = days.size() - 1;
        List<DayCount> lastWeek = days.subList(todayIndex - WEEK_DAYS, todayIndex);
        List<DayCount> weekBefore = days.subList(todayIndex - 2 * WEEK_DAYS, todayIndex - WEEK_DAYS);
        // Flagged on the totals, not the rounded averages: the weeks are the same length, so the
        // percent is the same, without the rounding.
        InsightFlag flag = flagRule.judge(total(lastWeek), total(weekBefore));
        return new ActiveClientTrendResponse(first, today, List.copyOf(days), average(lastWeek), average(weekBefore),
                flag, Instant.now(clock));
    }

    private static BigDecimal total(List<DayCount> week) {
        return BigDecimal.valueOf(week.stream().mapToLong(DayCount::activeClients).sum());
    }

    private static WeekAverage average(List<DayCount> week) {
        BigDecimal average = total(week).divide(BigDecimal.valueOf(week.size()), 2, RoundingMode.HALF_UP);
        return new WeekAverage(week.get(0).date(), week.get(week.size() - 1).date(), average);
    }
}
