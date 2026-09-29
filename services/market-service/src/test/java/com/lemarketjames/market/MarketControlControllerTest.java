package com.lemarketjames.market;

import com.lemarketjames.market.config.MarketSimulationProperties;
import com.lemarketjames.market.model.MarketInstrument;
import com.lemarketjames.market.service.MarketSimulator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contract C4 through HTTP: the control endpoints steer what the quote endpoints return. */
class MarketControlControllerTest {

    private static final MarketInstrument AAPL = new MarketInstrument(1, "AAPL", "Apple Inc", "EQUITY", "USD", "US",
            227.55, 0.08, 0.25, 0.65, 2.0, 15_200_000_000L, 55_000_000L, 6.75, 1.00);

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        MarketSimulator simulator = new MarketSimulator(new MarketSimulationProperties(),
                Clock.fixed(Instant.parse("2026-09-16T15:00:00Z"), ZoneOffset.UTC));
        simulator.load(List.of(AAPL), Map.of());
        mvc = MockMvcBuilders.standaloneSetup(
                new MarketController(simulator, simulator), new MarketControlController(simulator)).build();
    }

    @Test
    void unavailableFeedAnswers503OnEveryQuoteEndpoint() throws Exception {
        mvc.perform(put("/internal/market/control/feed").contentType(MediaType.APPLICATION_JSON)
                .content("{\"mode\":\"UNAVAILABLE\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.feedMode").value("UNAVAILABLE"));

        mvc.perform(get("/api/market/quotes/AAPL")).andExpect(status().isServiceUnavailable());
        mvc.perform(get("/api/market/quotes/by-instrument/1")).andExpect(status().isServiceUnavailable());
        mvc.perform(get("/api/market/quotes")).andExpect(status().isServiceUnavailable());

        mvc.perform(post("/internal/market/control/reset")).andExpect(jsonPath("$.feedMode").value("LIVE"));
        mvc.perform(get("/api/market/quotes/AAPL")).andExpect(status().isOk());
    }

    @Test
    void setPriceIsVisibleInQuotesAndPinnedByDefault() throws Exception {
        mvc.perform(put("/internal/market/control/prices/aapl").contentType(MediaType.APPLICATION_JSON)
                .content("{\"price\":123.45}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.pinned[0]").value("AAPL"));

        mvc.perform(get("/api/market/quotes/AAPL")).andExpect(jsonPath("$.lastPrice").value(123.45));

        mvc.perform(delete("/internal/market/control/prices/AAPL"))
            .andExpect(jsonPath("$.pinned").isEmpty());
    }

    @Test
    void unknownTickerIsABadRequest() throws Exception {
        mvc.perform(put("/internal/market/control/prices/NOPE").contentType(MediaType.APPLICATION_JSON)
                .content("{\"price\":1}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Unknown ticker: NOPE"));
    }
}
