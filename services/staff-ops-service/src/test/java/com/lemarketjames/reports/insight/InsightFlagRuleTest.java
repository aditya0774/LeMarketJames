package com.lemarketjames.reports.insight;

import com.lemarketjames.common.config.PlatformSettings;
import com.lemarketjames.reports.insight.InsightFlag.Direction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The BR-17 flag maths: a move of at least the setting's percent, either way, is flagged. */
class InsightFlagRuleTest {

    private final InsightFlagRule rule = new InsightFlagRule(new PlatformSettings());

    @ParameterizedTest(name = "{0} against {1}: flagged {2}, {3} %, {4}")
    @CsvSource({
            "125, 100, true, 25.00, UP",      // exactly the default 25 %: flagged
            "124.99, 100, false, 24.99, UP",  // just under it
            "75, 100, true, -25.00, DOWN",    // a fall counts the same as a rise
            "80, 100, false, -20.00, DOWN",
            "100, 100, false, 0.00, FLAT",
            "1, 3, true, -66.67, DOWN",       // rounded half up to two decimals
    })
    void flagsAMoveOfAtLeastTheThreshold(String current, String previous, boolean flagged,
                                         String percent, Direction direction) {
        InsightFlag flag = rule.judge(new BigDecimal(current), new BigDecimal(previous));

        assertEquals(new InsightFlag(flagged, new BigDecimal(percent), direction, new BigDecimal("25")), flag);
    }

    @Test
    void somethingAfterNothingIsFlaggedWithoutAPercent() {
        InsightFlag flag = rule.judge(new BigDecimal("3"), BigDecimal.ZERO);

        assertEquals(true, flag.flagged());
        assertNull(flag.changePercent());
        assertEquals(Direction.UP, flag.direction());
    }

    @Test
    void nothingAfterNothingIsNotFlagged() {
        assertEquals(new InsightFlag(false, null, Direction.FLAT, new BigDecimal("25")),
                rule.judge(BigDecimal.ZERO, BigDecimal.ZERO));
    }

    @Test
    void theThresholdComesFromTheSetting() {
        PlatformSettings settings = new PlatformSettings();
        settings.getReports().setInsightFlagPercent(new BigDecimal("10"));

        InsightFlag flag = new InsightFlagRule(settings).judge(new BigDecimal("112"), new BigDecimal("100"));

        assertEquals(true, flag.flagged());
        assertEquals(new BigDecimal("10"), flag.thresholdPercent());
    }

    @ParameterizedTest
    @CsvSource({"0", "-5"})
    void aThresholdThatFlagsEverythingIsRefusedAtStartUp(String percent) {
        PlatformSettings settings = new PlatformSettings();
        settings.getReports().setInsightFlagPercent(new BigDecimal(percent));

        assertThrows(IllegalStateException.class, () -> new InsightFlagRule(settings));
    }
}
