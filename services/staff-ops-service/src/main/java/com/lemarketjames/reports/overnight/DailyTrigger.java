package com.lemarketjames.reports.overnight;

import com.lemarketjames.reports.period.ReportCalendar;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.TriggerContext;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Fires once a day at a time of day in the reports time zone, every day of the year.
 *
 * <p>Not a cron trigger, because cron skips a time that a daylight-saving change jumps over: on
 * the night clocks go forward in New York, 02:00 never happens, and a cron run at 02:00 would
 * simply not run that night. Times are resolved by {@link ReportCalendar#at}, which moves a
 * skipped time to just after the gap (03:00 that night), so every night has its run.
 */
record DailyTrigger(LocalTime time, ReportCalendar calendar) implements Trigger {

    @Override
    public Instant nextExecution(TriggerContext context) {
        // After the last run if there was one, otherwise after now.
        Instant after = context.lastCompletion() != null ? context.lastCompletion() : context.getClock().instant();
        LocalDate day = calendar.reportDay(after);
        Instant candidate = calendar.at(day, time);
        return candidate.isAfter(after) ? candidate : calendar.at(day.plusDays(1), time);
    }

    @Override
    public String toString() {
        return "daily at " + time + " " + calendar.zone().getId();
    }
}
