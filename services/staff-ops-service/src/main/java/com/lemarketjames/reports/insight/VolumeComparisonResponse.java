package com.lemarketjames.reports.insight;

import com.lemarketjames.reports.insight.VolumeInsightResponse.PeriodVolume;
import com.lemarketjames.reports.insight.VolumeInsightResponse.VolumeChange;

import java.time.Instant;

/**
 * Two date ranges the caller chose, compared. Totals only, like {@link VolumeInsightResponse}.
 *
 * @param current     the range being looked at
 * @param previous    the range it is compared with
 * @param change      {@code current} minus {@code previous}
 * @param flag        whether the share volume moved enough to be flagged (BR-17)
 * @param generatedAt when the figures were computed
 */
public record VolumeComparisonResponse(PeriodVolume current, PeriodVolume previous, VolumeChange change,
                                       InsightFlag flag, Instant generatedAt) {
}
