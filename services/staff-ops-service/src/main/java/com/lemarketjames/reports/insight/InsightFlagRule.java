package com.lemarketjames.reports.insight;

import com.lemarketjames.common.config.PlatformSettings;
import com.lemarketjames.reports.insight.InsightFlag.Direction;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Decides whether an insight is flagged (BR-17): it moved by at least the configured percent
 * ({@code lmj.reports.insight-flag-percent}, contract C5) against the period it is compared with.
 * Kept apart from the insights so each one only says what it compares, and all of them flag alike.
 */
@Component
public class InsightFlagRule {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int PERCENT_SCALE = 2;

    private final BigDecimal thresholdPercent;

    /** @throws IllegalStateException when the setting is not above zero: every insight would be flagged */
    public InsightFlagRule(PlatformSettings settings) {
        this.thresholdPercent = settings.getReports().getInsightFlagPercent();
        if (thresholdPercent == null || thresholdPercent.signum() <= 0) {
            throw new IllegalStateException("lmj.reports.insight-flag-percent must be above zero, was "
                    + thresholdPercent);
        }
    }

    /**
     * @param current  the figure for the period looked at
     * @param previous the same figure for the period it is compared with
     */
    public InsightFlag judge(BigDecimal current, BigDecimal previous) {
        BigDecimal change = current.subtract(previous);
        BigDecimal percent = percentOf(change, previous);
        // From nothing to something has no percent, but it is the largest move there is.
        boolean flagged = percent == null
                ? change.signum() != 0
                : percent.abs().compareTo(thresholdPercent) >= 0;
        return new InsightFlag(flagged, percent, direction(change), thresholdPercent);
    }

    /** The change as a percent of the previous value, two decimals, or null when there was nothing to compare with. */
    static BigDecimal percentOf(BigDecimal change, BigDecimal previous) {
        if (previous.signum() == 0) {
            return null;
        }
        return change.multiply(HUNDRED).divide(previous, PERCENT_SCALE, RoundingMode.HALF_UP);
    }

    private static Direction direction(BigDecimal change) {
        return switch (change.signum()) {
            case 1 -> Direction.UP;
            case -1 -> Direction.DOWN;
            default -> Direction.FLAT;
        };
    }
}
