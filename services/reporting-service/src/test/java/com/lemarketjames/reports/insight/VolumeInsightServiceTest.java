package com.lemarketjames.reports.insight;

import com.lemarketjames.common.config.PlatformSettings;
import com.lemarketjames.reports.period.ReportCalendar;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Which two periods are compared, and how the change is worked out. The calendar is the real one
 * (default zone America/New_York) and the clock is fixed, so period boundaries, evenings that are
 * already "tomorrow" in UTC, and daylight-saving days are checked for real. The repository is a
 * fake that returns the totals registered for an exact UTC range and zeros for any other.
 */
class VolumeInsightServiceTest {

    /** Monday 12 October 2026, 10:00 in New York. */
    private static final Instant MONDAY_MORNING = Instant.parse("2026-10-12T14:00:00Z");

    private final Map<List<LocalDateTime>, VolumeTotals> totalsByRange = new ConcurrentHashMap<>();
    private final List<List<LocalDateTime>> rangesAsked = new ArrayList<>();
    private final ReportCalendar calendar = new ReportCalendar(new PlatformSettings());

    private VolumeInsightService serviceAt(Instant now) {
        VolumeInsightRepository fake = (start, end) -> {
            rangesAsked.add(List.of(start, end));
            return totalsByRange.getOrDefault(List.of(start, end), totals(0, "0", "0"));
        };
        return new VolumeInsightService(fake, calendar, Clock.fixed(now, ZoneOffset.UTC));
    }

    private static VolumeTotals totals(long trades, String shares, String amount) {
        return new VolumeTotals() {
            public long getTradeCount() { return trades; }
            public BigDecimal getShareVolume() { return new BigDecimal(shares); }
            public BigDecimal getGrossAmount() { return new BigDecimal(amount); }
        };
    }

    private void register(String startUtc, String endUtc, VolumeTotals totals) {
        totalsByRange.put(List.of(LocalDateTime.parse(startUtc), LocalDateTime.parse(endUtc)), totals);
    }

    @BeforeEach
    void clear() {
        totalsByRange.clear();
        rangesAsked.clear();
    }

    @ParameterizedTest
    @CsvSource({
            "DAY,   2026-10-11, 2026-10-11, 2026-10-10, 2026-10-10",
            "WEEK,  2026-10-05, 2026-10-11, 2026-09-28, 2026-10-04",
            "MONTH, 2026-09-01, 2026-09-30, 2026-08-01, 2026-08-31",
            "YEAR,  2025-01-01, 2025-12-31, 2024-01-01, 2024-12-31"
    })
    void comparesTheLastTwoCompletedPeriods(String type, LocalDate curFirst, LocalDate curLast,
                                            LocalDate prevFirst, LocalDate prevLast) {
        VolumeInsightResponse r = serviceAt(MONDAY_MORNING).compareWithPreviousPeriod(type);

        assertEquals(type, r.periodType());
        assertEquals(curFirst, r.current().startDate());
        assertEquals(curLast, r.current().endDate());
        assertEquals(prevFirst, r.previous().startDate());
        assertEquals(prevLast, r.previous().endDate());
    }

    @Test
    void periodTypeIsCaseInsensitive() {
        assertEquals("WEEK", serviceAt(MONDAY_MORNING).compareWithPreviousPeriod("week").periodType());
    }

    @Test
    void weekRunsMondayToSundayInReportTime() {
        serviceAt(MONDAY_MORNING).compareWithPreviousPeriod("WEEK");

        // Current week: Mon 5 Oct 00:00 EDT to Mon 12 Oct 00:00 EDT, as UTC; previous: the week before;
        // then the week in progress.
        assertEquals(List.of(
                List.of(LocalDateTime.parse("2026-10-05T04:00:00"), LocalDateTime.parse("2026-10-12T04:00:00")),
                List.of(LocalDateTime.parse("2026-09-28T04:00:00"), LocalDateTime.parse("2026-10-05T04:00:00")),
                List.of(LocalDateTime.parse("2026-10-12T04:00:00"), LocalDateTime.parse("2026-10-19T04:00:00"))),
                rangesAsked);
    }

    @Test
    void reportsThePeriodInProgressUpToTodayWithoutTouchingTheChange() {
        register("2026-10-05T04:00:00", "2026-10-12T04:00:00", totals(40, "1200", "54000.00"));
        register("2026-09-28T04:00:00", "2026-10-05T04:00:00", totals(32, "1000", "41000.00"));
        register("2026-10-12T04:00:00", "2026-10-19T04:00:00", totals(3, "75", "900.00"));

        VolumeInsightResponse r = serviceAt(MONDAY_MORNING).compareWithPreviousPeriod("WEEK");

        assertEquals(LocalDate.parse("2026-10-12"), r.inProgress().startDate());
        assertEquals(LocalDate.parse("2026-10-12"), r.inProgress().endDate());
        assertEquals(3, r.inProgress().tradeCount());
        assertEquals(new BigDecimal("75"), r.inProgress().shareVolume());
        assertEquals(new BigDecimal("200"), r.change().shareVolume());
    }

    @Test
    void anEveningThatIsAlreadyTomorrowInUtcStillBelongsToTheReportDay() {
        // 22:00 on Mon 12 Oct in New York is 02:00 on 13 Oct UTC; "today" is still the 12th,
        // so the last completed day is Sunday the 11th, not Monday the 12th.
        VolumeInsightResponse r = serviceAt(Instant.parse("2026-10-13T02:00:00Z")).compareWithPreviousPeriod("DAY");

        assertEquals(LocalDate.parse("2026-10-11"), r.current().startDate());
    }

    @Test
    void aDaylightSavingDayIsMeasuredFromLocalMidnightToLocalMidnight() {
        // Clocks go back on Sun 1 Nov 2026, so that day has 25 hours. On Tue 3 Nov the previous
        // day is the long one: 04:00Z on the 1st until 05:00Z on the 2nd.
        serviceAt(Instant.parse("2026-11-03T15:00:00Z")).compareWithPreviousPeriod("DAY");

        assertEquals(List.of(LocalDateTime.parse("2026-11-01T04:00:00"), LocalDateTime.parse("2026-11-02T05:00:00")),
                rangesAsked.get(1));
    }

    @Test
    void reportsVolumeAndChangeWhenTradingGrew() {
        register("2026-10-05T04:00:00", "2026-10-12T04:00:00", totals(40, "1200", "54000.00"));
        register("2026-09-28T04:00:00", "2026-10-05T04:00:00", totals(32, "1000", "41000.00"));

        VolumeInsightResponse r = serviceAt(MONDAY_MORNING).compareWithPreviousPeriod("WEEK");

        assertEquals(40, r.current().tradeCount());
        assertEquals(new BigDecimal("1200"), r.current().shareVolume());
        assertEquals(new BigDecimal("41000.00"), r.previous().grossAmount());
        assertEquals(new BigDecimal("200"), r.change().shareVolume());
        assertEquals(new BigDecimal("20.00"), r.change().shareVolumePercent());
        assertEquals(new BigDecimal("13000.00"), r.change().grossAmount());
        assertEquals(new BigDecimal("31.71"), r.change().grossAmountPercent());
    }

    @Test
    void reportsANegativeChangeWhenTradingShrank() {
        register("2026-10-05T04:00:00", "2026-10-12T04:00:00", totals(5, "750", "10000.00"));
        register("2026-09-28T04:00:00", "2026-10-05T04:00:00", totals(8, "1000", "20000.00"));

        VolumeInsightResponse r = serviceAt(MONDAY_MORNING).compareWithPreviousPeriod("WEEK");

        assertEquals(new BigDecimal("-250"), r.change().shareVolume());
        assertEquals(new BigDecimal("-25.00"), r.change().shareVolumePercent());
        assertEquals(new BigDecimal("-50.00"), r.change().grossAmountPercent());
    }

    @Test
    void unchangedVolumeIsZeroPercent() {
        register("2026-10-05T04:00:00", "2026-10-12T04:00:00", totals(3, "100", "500.00"));
        register("2026-09-28T04:00:00", "2026-10-05T04:00:00", totals(3, "100", "500.00"));

        VolumeInsightResponse r = serviceAt(MONDAY_MORNING).compareWithPreviousPeriod("WEEK");

        assertEquals(0, r.change().shareVolumePercent().signum());
    }

    @Test
    void percentIsNullWhenThePreviousPeriodHadNoTrades() {
        register("2026-10-05T04:00:00", "2026-10-12T04:00:00", totals(4, "300", "900.00"));

        VolumeInsightResponse r = serviceAt(MONDAY_MORNING).compareWithPreviousPeriod("WEEK");

        assertEquals(new BigDecimal("300"), r.change().shareVolume());
        assertNull(r.change().shareVolumePercent());
        assertNull(r.change().grossAmountPercent());
    }

    @Test
    void twoQuietPeriodsGiveZerosAndNoPercent() {
        VolumeInsightResponse r = serviceAt(MONDAY_MORNING).compareWithPreviousPeriod("MONTH");

        assertEquals(0, r.current().tradeCount());
        assertEquals(0, r.change().shareVolume().signum());
        assertNull(r.change().shareVolumePercent());
    }

    @Test
    void stampsWhenTheFiguresWereComputed() {
        assertEquals(MONDAY_MORNING, serviceAt(MONDAY_MORNING).compareWithPreviousPeriod("DAY").generatedAt());
    }

    @ParameterizedTest
    @CsvSource(value = {"NULL", "''", "'  '", "DAILY", "fortnight"}, nullValues = "NULL")
    void aMissingOrUnknownPeriodTypeIsRejected(String type) {
        assertThrows(IllegalArgumentException.class, () -> serviceAt(MONDAY_MORNING).compareWithPreviousPeriod(type));
    }
}
