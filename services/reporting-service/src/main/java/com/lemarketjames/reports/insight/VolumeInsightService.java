package com.lemarketjames.reports.insight;

import com.lemarketjames.reports.insight.VolumeInsightResponse.PeriodVolume;
import com.lemarketjames.reports.insight.VolumeInsightResponse.VolumeChange;
import com.lemarketjames.reports.period.ReportBucket;
import com.lemarketjames.reports.period.ReportCalendar;
import com.lemarketjames.reports.period.ReportPeriod;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;

/**
 * Compares the traded volume of the last completed period with the period before it. Both are
 * whole periods, because a period still in progress would always look like a drop against a
 * finished one. Nothing is cached: every call reads the view, so the figures move forward by
 * themselves as each report-time-zone day ends.
 */
@Service
public class VolumeInsightService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int PERCENT_SCALE = 2;

    private final VolumeInsightRepository repository;
    private final ReportCalendar calendar;
    private final Clock clock;

    public VolumeInsightService(VolumeInsightRepository repository, ReportCalendar calendar, Clock clock) {
        this.repository = repository;
        this.calendar = calendar;
        this.clock = clock;
    }

    /**
     * @param periodType DAY, WEEK, MONTH or YEAR, in any letter case
     * @throws IllegalArgumentException when the type is missing or unknown; answered with 400
     */
    public VolumeInsightResponse compareWithPreviousPeriod(String periodType) {
        ReportBucket bucket = parse(periodType);

        // "Today" is a day in the reports time zone; the period it falls in is still in progress.
        LocalDate today = LocalDate.now(clock.withZone(calendar.zone()));
        ReportPeriod inProgress = calendar.bucketOf(today, bucket);
        ReportPeriod current = calendar.bucketOf(inProgress.firstDay().minusDays(1), bucket);
        ReportPeriod previous = calendar.bucketOf(current.firstDay().minusDays(1), bucket);

        PeriodVolume currentVolume = volumeOf(current);
        PeriodVolume previousVolume = volumeOf(previous);
        // The running period is reported on its own so a trade made now shows up; it stays out of
        // the change, which compares whole periods only.
        PeriodVolume inProgressVolume = volumeOf(inProgress, today);

        return new VolumeInsightResponse(bucket.name(), currentVolume, previousVolume, inProgressVolume,
                change(currentVolume, previousVolume), Instant.now(clock));
    }

    private PeriodVolume volumeOf(ReportPeriod period) {
        // The period ends at the next one's first local midnight, so its last day is the day before.
        return volumeOf(period, calendar.reportDay(period.end()).minusDays(1));
    }

    private PeriodVolume volumeOf(ReportPeriod period, LocalDate lastDay) {
        VolumeTotals totals = repository.totalsBetween(period.start(), period.end());
        return new PeriodVolume(period.firstDay(), lastDay, totals.getTradeCount(),
                totals.getShareVolume(), totals.getGrossAmount());
    }

    private static VolumeChange change(PeriodVolume current, PeriodVolume previous) {
        BigDecimal shares = current.shareVolume().subtract(previous.shareVolume());
        BigDecimal amount = current.grossAmount().subtract(previous.grossAmount());
        return new VolumeChange(shares, percent(shares, previous.shareVolume()),
                amount, percent(amount, previous.grossAmount()));
    }

    /** The change as a percent of the previous value, or null when there was nothing to compare with. */
    private static BigDecimal percent(BigDecimal change, BigDecimal previous) {
        if (previous.signum() == 0) {
            return null;
        }
        return change.multiply(HUNDRED).divide(previous, PERCENT_SCALE, RoundingMode.HALF_UP);
    }

    private static ReportBucket parse(String periodType) {
        if (periodType == null || periodType.isBlank()) {
            throw new IllegalArgumentException("periodType is required (DAY, WEEK, MONTH or YEAR)");
        }
        try {
            return ReportBucket.valueOf(periodType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Invalid periodType: " + periodType + ". Valid values: DAY, WEEK, MONTH, YEAR");
        }
    }
}
