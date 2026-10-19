package com.lemarketjames.reports.overnight;

import com.lemarketjames.reports.period.ReportCalendar;
import com.lemarketjames.reports.period.ReportPeriod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Stands in for the heavy reports until they are built (LMKT-45 is built against a stub job).
 *
 * <p>It reads what a real report would: the run date's trades from {@code reporting_trades}, the
 * one data source of every report, counted only (no single trade, nothing about a client). That
 * exercises the read-only safeguards and the run date's range the way a real report will. The seed
 * data (C3) already has trades on many days, so no seeding is needed for it.
 *
 * <p>It succeeds, unless {@code reporting.overnight.stub.fail-with} is set, in which case it fails
 * with that message: that is how a failed run is shown on a running stack.
 */
@Component
public class StubOvernightReportJob implements OvernightReportJob {

    static final String NAME = "Stub overnight report";
    private static final Logger log = LoggerFactory.getLogger(StubOvernightReportJob.class);

    private final JdbcTemplate jdbc;
    private final ReportCalendar calendar;
    private final String failWith;

    public StubOvernightReportJob(JdbcTemplate jdbc, ReportCalendar calendar,
                                  @Value("${reporting.overnight.stub.fail-with:}") String failWith) {
        this.jdbc = jdbc;
        this.calendar = calendar;
        this.failWith = failWith;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void run(LocalDate runDate) {
        if (!failWith.isBlank()) {
            throw new IllegalStateException(failWith);
        }
        // The run date as a half-open UTC range, from ReportCalendar like every report (README rule 4).
        ReportPeriod day = calendar.between(runDate, runDate);
        Long trades = jdbc.queryForObject(
                "SELECT COUNT(*) FROM reporting_trades WHERE filled_at >= ? AND filled_at < ?",
                Long.class, day.start(), day.end());
        log.info("Stub overnight report produced for {}: {} trades", runDate, trades);
    }
}
