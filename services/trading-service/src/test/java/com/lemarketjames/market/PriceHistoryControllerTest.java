package com.lemarketjames.market;

import com.lemarketjames.market.model.DailyBar;
import com.lemarketjames.market.model.PricePoint;
import com.lemarketjames.market.service.FeedMode;
import com.lemarketjames.market.service.MarketFeedControl;
import com.lemarketjames.market.service.PriceHistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The chart endpoints: history as JSON, and 503 while the feed is unavailable (contract C4). */
class PriceHistoryControllerTest {

    private final PriceHistoryService history = mock(PriceHistoryService.class);
    private final MarketFeedControl feed = mock(MarketFeedControl.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        when(feed.feedMode()).thenReturn(FeedMode.LIVE);
        mvc = MockMvcBuilders.standaloneSetup(new PriceHistoryController(history, feed)).build();
    }

    @Test
    void dailyBarsAreReturnedFromTheRequestedDay() throws Exception {
        LocalDate from = LocalDate.of(2026, 10, 1);
        when(history.dailyBars(1, from)).thenReturn(List.of(new DailyBar(from, 1, 2, 0.5, 1.5, 10)));

        mvc.perform(get("/api/market/history/1").param("from", "2026-10-01"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].close").value(1.5));
    }

    @Test
    void intradayPointsAreReturned() throws Exception {
        when(history.intraday(1)).thenReturn(List.of(new PricePoint(Instant.parse("2026-10-15T13:31:00Z"), 229.2)));

        mvc.perform(get("/api/market/intraday/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].price").value(229.2));
    }

    @Test
    void unavailableFeedAnswers503() throws Exception {
        when(feed.feedMode()).thenReturn(FeedMode.UNAVAILABLE);

        mvc.perform(get("/api/market/history/1").param("from", "2026-10-01"))
            .andExpect(status().isServiceUnavailable());
        mvc.perform(get("/api/market/intraday/1")).andExpect(status().isServiceUnavailable());
    }
}
