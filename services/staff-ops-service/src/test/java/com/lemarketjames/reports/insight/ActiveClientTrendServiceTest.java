package com.lemarketjames.reports.insight;

import com.lemarketjames.common.config.PlatformSettings;
import com.lemarketjames.reports.insight.ActiveClientTrendResponse.DayCount;
import com.lemarketjames.reports.insight.ActiveClientTrendResponse.WeekAverage;
import com.lemarketjames.reports.period.ReportCalendar;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which 30 days are reported and how each is counted. The calendar is the real one (default zone
 * America/New_York) and the clock is fixed, so local-day boundaries and daylight-saving days are
 * checked for real. The repository is a fake that returns the count registered for an exact UTC
 * range and zero for any other.
 */
class ActiveClientTrendServiceTest {

    /** Wednesday 14 October 2026, 10:00 in New York. */
    private static final Instant NOW = Instant.parse("2026-10-14T14:00:00Z");

    private final Map<List<LocalDateTime>, Long> countByRange = new HashMap<>();
    private final List<List<LocalDateTime>> rangesAsked = new ArrayList<>();

    private ActiveClientTrendService serviceAt(Instant now) {
        ActiveClientTrendRepository fake = (start, end) -> {
            rangesAsked.add(List.of(start, end));
            return countByRange.getOrDefault(List.of(start, end), 0L);
        };
        PlatformSettings settings = new PlatformSettings();
        return new ActiveClientTrendService(fake, new ReportCalendar(settings), new InsightFlagRule(settings),
                Clock.fixed(now, ZoneOffset.UTC));
    }

    private void register(String startUtc, String endUtc, long count) {
        countByRange.put(List.of(LocalDateTime.parse(startUtc), LocalDateTime.parse(endUtc)), count);
    }

    @Test
    void reportsThirtyDaysOldestFirstEndingToday() {
        ActiveClientTrendResponse response = serviceAt(NOW).trend();

        assertEquals(30, response.days().size());
        assertEquals(LocalDate.parse("2026-09-15"), response.startDate());
        assertEquals(LocalDate.parse("2026-10-14"), response.endDate());
        assertEquals(response.startDate(), response.days().get(0).date());
        assertEquals(response.endDate(), response.days().get(29).date());
        assertEquals(Instant.parse("2026-10-14T14:00:00Z"), response.generatedAt());
    }

    @Test
    void aDayWithoutTradesIsZeroNotMissing() {
        ActiveClientTrendResponse response = serviceAt(NOW).trend();

        assertTrue(response.days().stream().allMatch(d -> d.activeClients() == 0));
        assertEquals(30, response.days().size());
    }

    @Test
    void eachDayIsItsOwnLocalDayRangeInUtc() {
        // 14 Oct in New York (EDT, UTC-4) runs from 04:00Z to 04:00Z the next day.
        register("2026-10-14T04:00:00", "2026-10-15T04:00:00", 7);

        ActiveClientTrendResponse response = serviceAt(NOW).trend();

        assertEquals(new DayCount(LocalDate.parse("2026-10-14"), 7), response.days().get(29));
        assertEquals(0, response.days().get(28).activeClients());
    }

    @Test
    void theCountPassesThroughUnchangedForEachDay() {
        register("2026-09-15T04:00:00", "2026-09-16T04:00:00", 3);
        register("2026-09-16T04:00:00", "2026-09-17T04:00:00", 11);

        ActiveClientTrendResponse response = serviceAt(NOW).trend();

        assertEquals(3, response.days().get(0).activeClients());
        assertEquals(11, response.days().get(1).activeClients());
    }

    @Test
    void daylightSavingEndDayCoversTwentyFiveHours() {
        // 1 Nov 2026: clocks go back in New York, so that local day is 04:00Z to 05:00Z the next day.
        register("2026-11-01T04:00:00", "2026-11-02T05:00:00", 5);

        ActiveClientTrendResponse response = serviceAt(Instant.parse("2026-11-01T18:00:00Z")).trend();

        assertEquals(new DayCount(LocalDate.parse("2026-11-01"), 5), response.days().get(29));
    }

    @Test
    void theFlagComparesTheLastSevenWholeDaysWithTheSevenBefore() {
        // Today, 14 Oct, is left out. Last week: 7-13 Oct; the week before: 30 Sep-6 Oct.
        register("2026-10-14T04:00:00", "2026-10-15T04:00:00", 500);
        register("2026-10-07T04:00:00", "2026-10-08T04:00:00", 10);
        register("2026-10-13T04:00:00", "2026-10-14T04:00:00", 4);
        register("2026-09-30T04:00:00", "2026-10-01T04:00:00", 7);
        register("2026-10-06T04:00:00", "2026-10-07T04:00:00", 4);
        register("2026-09-29T04:00:00", "2026-09-30T04:00:00", 100);

        ActiveClientTrendResponse response = serviceAt(NOW).trend();

        assertEquals(new WeekAverage(LocalDate.parse("2026-10-07"), LocalDate.parse("2026-10-13"), new BigDecimal("2.00")),
                response.lastWeek());
        assertEquals(new WeekAverage(LocalDate.parse("2026-09-30"), LocalDate.parse("2026-10-06"), new BigDecimal("1.57")),
                response.weekBefore());
        // 14 against 11 clients: +27.27 %, over the 25 % default.
        assertTrue(response.flag().flagged());
        assertEquals(new BigDecimal("27.27"), response.flag().changePercent());
        assertEquals(InsightFlag.Direction.UP, response.flag().direction());
    }

    @Test
    void twoQuietWeeksAreNotFlagged() {
        ActiveClientTrendResponse response = serviceAt(NOW).trend();

        assertFalse(response.flag().flagged());
        assertEquals(InsightFlag.Direction.FLAT, response.flag().direction());
    }

    @Test
    void anEveningThatIsAlreadyTomorrowInUtcStillCountsAsToday() {
        // 23:30 on 14 Oct in New York is 03:30Z on 15 Oct; today must still be the 14th.
        ActiveClientTrendResponse response = serviceAt(Instant.parse("2026-10-15T03:30:00Z")).trend();

        assertEquals(LocalDate.parse("2026-10-14"), response.endDate());
    }
}
