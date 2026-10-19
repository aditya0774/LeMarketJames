package com.lemarketjames.quotes;

import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.lemarketjames.common.security.AccountAccessCheck;
import com.lemarketjames.common.security.JwtAuthenticationFilter;
import com.lemarketjames.common.security.JwtService;
import com.lemarketjames.market.client.MarketFeedStatus;
import com.lemarketjames.market.model.MarketInstrument;
import com.lemarketjames.market.model.QuoteSnapshot;
import com.lemarketjames.market.model.DailyBar;
import com.lemarketjames.market.service.MarketDataService;
import com.lemarketjames.market.service.PriceHistoryService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// MarketDataService is mocked here instead of relying on the real simulator, so each test chooses
// the quote it gets (the simulator itself is covered by MarketSimulatorTest).
@SpringBootTest
@AutoConfigureMockMvc
class QuotesControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // These tests sign tokens for users with no account row; the per-request account check (LMKT-57)
    // is tested in libs/common, and a mock answers "allowed".
    @MockitoBean
    private AccountAccessCheck accessCheck;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private MarketDataService marketData;

    @MockBean
    private PriceHistoryService priceHistory;

    @Autowired
    private MarketFeedStatus feedStatus;

    @AfterEach
    void feedResumes() {
        // MarketFeedStatus is a real singleton shared across this class's tests; make sure an
        // outage test never leaks an "unavailable" status into the next test.
        feedStatus.recordSuccess();
    }

    @Test
    void quoteEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/quotes/AAPL"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedRequestReturnsQuotePayload() throws Exception {
        Cookie jwtCookie = loginAs("quotesuser1");
        Instant quoteTime = Instant.parse("2026-10-16T14:30:00.123Z");
        when(marketData.findByTicker(eq("AAPL"))).thenReturn(Optional.of(sampleAaplQuote(quoteTime)));

        mockMvc.perform(get("/api/quotes/AAPL").cookie(jwtCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.quote.symbol").value("AAPL"))
                .andExpect(jsonPath("$.quote.name").isString())
                .andExpect(jsonPath("$.quote.price").value(228.10))
                .andExpect(jsonPath("$.quote.priceChange").isNumber())
                .andExpect(jsonPath("$.quote.priceChangePercent").isNumber())
                .andExpect(jsonPath("$.quote.highPrice").isNumber())
                .andExpect(jsonPath("$.quote.lowPrice").isNumber())
                .andExpect(jsonPath("$.quote.openPrice").isNumber())
                .andExpect(jsonPath("$.quote.volume").isNumber())
                .andExpect(jsonPath("$.quote.marketCap").isNumber())
                .andExpect(jsonPath("$.quote.peRatio").isNumber())
                .andExpect(jsonPath("$.quote.dividendYield").isNumber())
                .andExpect(jsonPath("$.quote.lastUpdate").value("2026-10-16T14:30:00.123Z"));
    }

    @Test
    void authenticatedUnknownSymbolReturns404ContractError() throws Exception {
        Cookie jwtCookie = loginAs("quotesuser2");
        when(marketData.findByTicker(eq("INVALID"))).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/quotes/INVALID").cookie(jwtCookie))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Symbol not found"));
    }

    @Test
    void authenticatedRequestDuringAnOutageReturns503PriceUnavailable() throws Exception {
        Cookie jwtCookie = loginAs("quotesuser4");
        feedStatus.recordFailure();

        mockMvc.perform(get("/api/quotes/AAPL").cookie(jwtCookie))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Price unavailable"));
    }

    @Test
    void allQuotesEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/quotes"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedRequestReturnsEveryQuote() throws Exception {
        Cookie jwtCookie = loginAs("quotesuser3");
        when(marketData.findAll()).thenReturn(List.of(sampleAaplQuote()));

        mockMvc.perform(get("/api/quotes").cookie(jwtCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.quotes.length()").value(1))
                .andExpect(jsonPath("$.quotes[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$.quotes[0].price").isNumber())
                .andExpect(jsonPath("$.quotes[0].lastUpdate").isString());
    }

    @Test
    void allQuotesEndpointDuringAnOutageReturns503PriceUnavailable() throws Exception {
        Cookie jwtCookie = loginAs("quotesuser5");
        feedStatus.recordFailure();

        mockMvc.perform(get("/api/quotes").cookie(jwtCookie))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Price unavailable"));
    }

    @Test
    void allQuotesEndpointResumesAutomaticallyOnceTheFeedRecovers() throws Exception {
        Cookie jwtCookie = loginAs("quotesuser6");
        feedStatus.recordFailure();
        mockMvc.perform(get("/api/quotes").cookie(jwtCookie)).andExpect(status().isServiceUnavailable());

        feedStatus.recordSuccess();
        when(marketData.findAll()).thenReturn(List.of(sampleAaplQuote()));

        mockMvc.perform(get("/api/quotes").cookie(jwtCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.quotes[0].symbol").value("AAPL"));
    }

    @Test
    void historyEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/quotes/AAPL/history").param("range", "1M"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedRequestReturnsTheStocksHistory() throws Exception {
        Cookie jwtCookie = loginAs("quotesuser7");
        when(marketData.findByTicker(eq("AAPL"))).thenReturn(Optional.of(sampleAaplQuote()));
        when(priceHistory.dailyBars(eq(1), any(LocalDate.class)))
                .thenReturn(List.of(new DailyBar(LocalDate.of(2026, 9, 15), 1, 2, 0.5, 225.1, 10)));

        mockMvc.perform(get("/api/quotes/aapl/history").param("range", "1m").cookie(jwtCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.range").value("1M"))
                .andExpect(jsonPath("$.points[0].time").value("2026-09-15T20:00:00Z"))
                .andExpect(jsonPath("$.points[0].price").value(225.10));
    }

    @Test
    void historyWithAnUnknownRangeIsABadRequest() throws Exception {
        mockMvc.perform(get("/api/quotes/AAPL/history").param("range", "5Y").cookie(loginAs("quotesuser8")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Unknown range"));
    }

    @Test
    void historyDuringAnOutageReturns503PriceUnavailable() throws Exception {
        feedStatus.recordFailure();
        when(marketData.findByTicker(eq("AAPL"))).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/quotes/AAPL/history").param("range", "1M").cookie(loginAs("quotesuser10")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("Price unavailable"));
    }

    @Test
    void historyOfAnUnknownSymbolIs404() throws Exception {
        when(marketData.findByTicker(eq("INVALID"))).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/quotes/INVALID/history").cookie(loginAs("quotesuser9")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Symbol not found"));
    }

    // auth-service issues the jwt cookie in production; here the shared JwtService mints an
    // equivalent token so this service can be tested on its own.
    private Cookie loginAs(String username) {
        return new Cookie(JwtAuthenticationFilter.COOKIE_NAME, jwtService.generateToken(username));
    }

    private static QuoteSnapshot sampleAaplQuote() {
        return sampleAaplQuote(Instant.now());
    }

    private static QuoteSnapshot sampleAaplQuote(Instant quoteTime) {
        MarketInstrument aapl = new MarketInstrument(
                1, "AAPL", "Apple Inc", "EQUITY", "USD", "US",
                227.55, 0.08, 0.25, 0.65, 1.5,
                15_200_000_000L, 55_000_000L, 6.75, 1.00);
        return new QuoteSnapshot(
                aapl, 228.10, 228.00, 228.20, 226.50, 229.00, 225.80, 226.90,
                12_345_678L, quoteTime, LocalDate.now());
    }
}
