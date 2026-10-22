package com.lemarketjames.reports.overnight;

import com.lemarketjames.reports.period.ReportCalendar;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Builds the overnight reports list the Analyst dashboard shows (LMKT-45). */
@Service
public class OvernightReportsService {

    /** How many nights back the list goes (contract C6). */
    static final int RECENT_NIGHTS = 30;

    private final ReportRunRepository runs;
    private final ReportOutputs outputs;
    private final ReportCalendar calendar;
    private final Clock clock;

    public OvernightReportsService(ReportRunRepository runs, ReportOutputs outputs, ReportCalendar calendar, Clock clock) {
        this.runs = runs;
        this.outputs = outputs;
        this.calendar = calendar;
        this.clock = clock;
    }

    /** Runs of the last {@value #RECENT_NIGHTS} nights, the most recent first. */
    public OvernightReportsResponse recentRuns() {
        LocalDate since = calendar.reportDay(clock.instant()).minusDays(RECENT_NIGHTS);
        Map<UUID, String> files = outputs.fileNamesSince(since);
        var data = runs.findByRunDateGreaterThanEqualOrderByRunDateDescReportNameAsc(since)
                .stream()
                .map(run -> OvernightReportsResponse.Run.of(run, files.get(run.getId())))
                .toList();
        return new OvernightReportsResponse(true, calendar.zone().getId(), data);
    }

    /** The file a run produced, for download. */
    public Optional<ReportOutput> output(UUID runId) {
        return outputs.find(runId);
    }
}
