package com.lemarketjames.reports.overnight;

import com.lemarketjames.common.config.PlatformSettings;
import com.lemarketjames.reports.period.ReportCalendar;
import com.lemarketjames.reports.summary.DailyTradeSummary;
import com.lemarketjames.reports.summary.DailyTradeSummary.StockSegmentTotals;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** BR-16: the overnight monthly summary brings the trade summary up to date, then writes the month so far as CSV. */
class MonthlyTradingSummaryJobTest {

    private static final LocalDate RUN_DATE = LocalDate.parse("2026-10-20");

    private final DailySummaryWriter refresher = mock(DailySummaryWriter.class);
    private final DailyTradeSummary summary = mock(DailyTradeSummary.class);
    private final ReportCalendar calendar = new ReportCalendar(new PlatformSettings());

    @Test
    void refreshesTheSummaryThenWritesTheMonthSoFarFromIt() {
        when(refresher.refreshThrough(RUN_DATE)).thenReturn(new DailySummaryWriter.SummarizedDays(RUN_DATE, RUN_DATE));
        when(summary.byStockAndSegment("America/New_York", LocalDate.parse("2026-10-01"), RUN_DATE)).thenReturn(List.of(
                new StockSegmentTotals("AAPL", "RETAIL", 3, 2, 1, new BigDecimal("12.0000"), new BigDecimal("2700.123456")),
                new StockSegmentTotals("MSFT", "HIGH_NET_WORTH", 1, 1, 0, new BigDecimal("0.5000"), new BigDecimal("210"))));

        ReportOutput file = new MonthlyTradingSummaryJob(refresher, summary, calendar, "").run(RUN_DATE).orElseThrow();

        InOrder order = inOrder(refresher, summary);
        order.verify(refresher).refreshThrough(RUN_DATE);
        order.verify(summary).byStockAndSegment("America/New_York", LocalDate.parse("2026-10-01"), RUN_DATE);
        assertEquals("monthly-trading-summary-2026-10-01-to-2026-10-20.csv", file.fileName());
        assertEquals("text/csv", file.contentType());
        assertEquals("""
                symbol,segment,trades,buys,sells,shares,gross_amount_usd\r
                AAPL,RETAIL,3,2,1,12,2700.12\r
                MSFT,HIGH_NET_WORTH,1,1,0,0.5,210.00\r
                """, file.body());
    }

    @Test
    void theFirstOfTheMonthCoversThatOneDay() {
        LocalDate first = LocalDate.parse("2026-11-01");
        when(refresher.refreshThrough(first)).thenReturn(new DailySummaryWriter.SummarizedDays(first, first));
        when(summary.byStockAndSegment("America/New_York", first, first)).thenReturn(List.of());

        ReportOutput file = new MonthlyTradingSummaryJob(refresher, summary, calendar, "").run(first).orElseThrow();

        assertEquals("monthly-trading-summary-2026-11-01-to-2026-11-01.csv", file.fileName());
        assertEquals(MonthlySummaryCsv.HEADER + "\r\n", file.body());
    }

    @Test
    void failsWithTheConfiguredMessageWithoutTouchingTheDatabase() {
        var job = new MonthlyTradingSummaryJob(refresher, summary, calendar, "Summary disabled for the demo");

        var failure = assertThrows(IllegalStateException.class, () -> job.run(RUN_DATE));

        assertEquals("Summary disabled for the demo", failure.getMessage());
        verifyNoInteractions(refresher, summary);
    }

    @Test
    void quotesAValueThatNeedsItAndDefusesAFormula() {
        assertEquals("AAPL", MonthlySummaryCsv.field("AAPL"));
        assertEquals("\"A,B\"", MonthlySummaryCsv.field("A,B"));
        assertEquals("\"say \"\"hi\"\"\"", MonthlySummaryCsv.field("say \"hi\""));
        assertEquals("'=1+1", MonthlySummaryCsv.field("=1+1"));
    }
}
