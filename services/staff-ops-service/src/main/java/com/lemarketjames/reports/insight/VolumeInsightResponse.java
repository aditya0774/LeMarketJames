package com.lemarketjames.reports.insight;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * The volume insight: the last completed period against the one before it. Totals only, so no
 * client, account or order can be identified from it.
 *
 * @param periodType the kind of period compared: DAY, WEEK, MONTH or YEAR
 * @param current    the last completed period
 * @param previous   the period just before {@code current}
 * @param inProgress the period still running, totalled up to today. Not part of {@code change}: an
 *                   unfinished period would always look like a drop against a finished one
 * @param change     {@code current} minus {@code previous}
 * @param flag       whether the share volume moved enough to be flagged (BR-17); like {@code change},
 *                   it leaves {@code inProgress} out
 * @param generatedAt when the figures were computed
 */
public record VolumeInsightResponse(String periodType, PeriodVolume current, PeriodVolume previous,
                                    PeriodVolume inProgress, VolumeChange change, InsightFlag flag,
                                    Instant generatedAt) {

    /**
     * One period's totals. {@code startDate} and {@code endDate} are both included and are days in
     * the reports time zone.
     */
    public record PeriodVolume(LocalDate startDate, LocalDate endDate, long tradeCount,
                               BigDecimal shareVolume, BigDecimal grossAmount) {
    }

    /**
     * The difference between the two periods. A percent is {@code null} when the previous period
     * had none to compare with (it would be a division by zero).
     */
    public record VolumeChange(BigDecimal shareVolume, BigDecimal shareVolumePercent,
                               BigDecimal grossAmount, BigDecimal grossAmountPercent) {
    }
}
