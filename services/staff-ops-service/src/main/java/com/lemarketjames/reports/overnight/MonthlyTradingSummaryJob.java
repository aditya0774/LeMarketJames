package com.lemarketjames.reports.overnight;

import com.lemarketjames.reports.period.ReportCalendar;
import com.lemarketjames.reports.summary.DailyTradeSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;

/**
 * The overnight "Monthly trading summary" (BR-16). Each night it
 * <ol>
 *   <li>brings the nightly trade summary up to the run date ({@link DailySummaryWriter}): the
 *       separated read side the long-range reports read in the day, and</li>
 *   <li>writes the month so far, from the 1st through the run date, as a CSV per stock and client
 *       segment ({@link MonthlySummaryCsv}), read from that summary rather than from live orders.</li>
 * </ol>
 * The analyst downloads the file from the overnight reports list. It runs at the overnight run
 * time, outside trading hours, like every overnight report (OvernightScheduleConfig).
 *
 * <p>{@code reporting.overnight.fail-with} makes it fail with that message before doing anything:
 * how a failed run is shown on a running stack and in tests.
 */
@Component
public class MonthlyTradingSummaryJob implements OvernightReportJob {

    static final String NAME = "Monthly trading summary";
    private static final Logger log = LoggerFactory.getLogger(MonthlyTradingSummaryJob.class);

    private final DailySummaryWriter refresher;
    private final DailyTradeSummary summary;
    private final ReportCalendar calendar;
    private final String failWith;

    public MonthlyTradingSummaryJob(DailySummaryWriter refresher, DailyTradeSummary summary, ReportCalendar calendar,
                                    @Value("${reporting.overnight.fail-with:}") String failWith) {
        this.refresher = refresher;
        this.summary = summary;
        this.calendar = calendar;
        this.failWith = failWith;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Optional<ReportOutput> run(LocalDate runDate) {
        if (!failWith.isBlank()) {
            throw new IllegalStateException(failWith);
        }
        DailySummaryWriter.SummarizedDays rebuilt = refresher.refreshThrough(runDate);
        log.info("Trade summary rebuilt for {} to {}", rebuilt.from(), rebuilt.to());

        LocalDate monthStart = runDate.withDayOfMonth(1);
        var rows = summary.byStockAndSegment(calendar.zone().getId(), monthStart, runDate);
        return Optional.of(new ReportOutput(MonthlySummaryCsv.fileName(monthStart, runDate),
                MonthlySummaryCsv.CONTENT_TYPE, MonthlySummaryCsv.write(rows)));
    }
}
