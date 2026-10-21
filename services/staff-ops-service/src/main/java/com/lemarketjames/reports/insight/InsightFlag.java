package com.lemarketjames.reports.insight;

import java.math.BigDecimal;

/**
 * Whether an insight moved enough against the period it is compared with to be brought to an
 * analyst's attention (BR-17). Made by {@link InsightFlagRule}, so every insight flags by the same
 * rule and the same setting.
 *
 * @param flagged          true when the move is at least {@code thresholdPercent}, either way, or
 *                         when the previous period had none and this one has some
 * @param changePercent    the move as a percent of the previous period, two decimals; null when
 *                         the previous period had none (it would be a division by zero)
 * @param direction        which way it moved
 * @param thresholdPercent the setting in force ({@code lmj.reports.insight-flag-percent}), so the
 *                         staff app can say what "flagged" means without its own copy
 */
public record InsightFlag(boolean flagged, BigDecimal changePercent, Direction direction,
                          BigDecimal thresholdPercent) {

    /** Which way an insight moved against the period before. */
    public enum Direction {
        UP, DOWN, FLAT
    }
}
