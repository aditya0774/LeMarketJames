package com.lemarketjames.reports.insight;

import com.lemarketjames.reports.insight.VolumeInsightResponse.PeriodVolume;
import com.lemarketjames.reports.insight.VolumeInsightResponse.VolumeChange;
import com.lemarketjames.reports.period.ReportBucket;
import com.lemarketjames.reports.period.ReportCalendar;
import com.lemarketjames.reports.period.ReportPeriod;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;

/**
 * Compares the traded volume of the last completed period with the period before it. Both are
 * whole periods, because a period still in progress would always look like a drop against a
 * finished one. Nothing is cached: every call reads the view, so the figures move forward by
 * themselves as each report-time-zone day ends. Share volume is the figure that is flagged (BR-17),
 * as it is the one the staff tile shows.
 */
@Service
public class VolumeInsightService {

    private final VolumeInsightRepository repository;
    private final ReportCalendar calendar;
    private final InsightFlagRule flagRule;
    private final Clock clock;

    public VolumeInsightService(VolumeInsightRepository repository, ReportCalendar calendar,
                                InsightFlagRule flagRule, Clock clock) {
        this.repository = repository;
        this.calendar = calendar;
        this.flagRule = flagRule;
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
                change(currentVolume, previousVolume), flag(currentVolume, previousVolume), Instant.now(clock));
    }

    /**
     * Compares two ranges the caller picked. Each range runs from its first to its last day, both
     * included, in the reports time zone; ranges may overlap and need not be the same length.
     *
     * @throws IllegalArgumentException when a day is missing or a range is reversed (answered with 400)
     */
    public VolumeComparisonResponse compareRanges(LocalDate currentFrom, LocalDate currentTo,
                                                  LocalDate previousFrom, LocalDate previousTo) {
        ReportPeriod current = calendar.between(currentFrom, currentTo);
        ReportPeriod previous = calendar.between(previousFrom, previousTo);

        PeriodVolume currentVolume = volumeOf(current, currentTo);
        PeriodVolume previousVolume = volumeOf(previous, previousTo);

        return new VolumeComparisonResponse(currentVolume, previousVolume,
                change(currentVolume, previousVolume), flag(currentVolume, previousVolume), Instant.now(clock));
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
        return new VolumeChange(shares, InsightFlagRule.percentOf(shares, previous.shareVolume()),
                amount, InsightFlagRule.percentOf(amount, previous.grossAmount()));
    }

    private InsightFlag flag(PeriodVolume current, PeriodVolume previous) {
        return flagRule.judge(current.shareVolume(), previous.shareVolume());
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
