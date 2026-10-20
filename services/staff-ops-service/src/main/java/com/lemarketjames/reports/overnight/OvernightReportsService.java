package com.lemarketjames.reports.overnight;

import com.lemarketjames.reports.period.ReportCalendar;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;

/** Builds the overnight reports list the Analyst dashboard shows (LMKT-45). */
@Service
public class OvernightReportsService {

    /** How many nights back the list goes (contract C6). */
    static final int RECENT_NIGHTS = 30;

    private final ReportRunRepository runs;
    private final ReportCalendar calendar;
    private final Clock clock;

    public OvernightReportsService(ReportRunRepository runs, ReportCalendar calendar, Clock clock) {
        this.runs = runs;
        this.calendar = calendar;
        this.clock = clock;
    }

    /** Runs of the last {@value #RECENT_NIGHTS} nights, the most recent first. */
    public OvernightReportsResponse recentRuns() {
        LocalDate today = calendar.reportDay(clock.instant());
        var data = runs.findByRunDateGreaterThanEqualOrderByRunDateDescReportNameAsc(today.minusDays(RECENT_NIGHTS))
                .stream()
                .map(OvernightReportsResponse.Run::of)
                .toList();
        return new OvernightReportsResponse(true, calendar.zone().getId(), data);
    }
}
