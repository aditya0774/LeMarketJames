package com.lemarketjames.reports;

import com.lemarketjames.reports.period.ReportCalendar;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A health-style report endpoint: it reads no data, so a caller (or a test) can tell whether the
 * service is reachable and whether their role may read reports before any real report exists.
 */
@RestController
@RequestMapping("/api/v1/reports")
public class ReportPingController {

    /** The name this service answers with. */
    static final String SERVICE_NAME = "reporting-service";

    private final ReportCalendar calendar;

    public ReportPingController(ReportCalendar calendar) {
        this.calendar = calendar;
    }

    /** @return that reports are available, and the time zone their days, weeks, months and years are in */
    @GetMapping("/ping")
    public PingResponse ping() {
        return new PingResponse(true, SERVICE_NAME, calendar.zone().getId());
    }

    /** The ping body; {@code timeZone} is an IANA zone ID. */
    public record PingResponse(boolean success, String service, String timeZone) {
    }
}
