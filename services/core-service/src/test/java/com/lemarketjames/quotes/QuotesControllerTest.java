package com.lemarketjames.quotes;

import com.lemarketjames.common.security.JwtAuthenticationFilter;
import com.lemarketjames.common.security.JwtService;
import com.lemarketjames.market.model.MarketInstrument;
import com.lemarketjames.market.model.QuoteSnapshot;
import com.lemarketjames.market.service.MarketDataService;
import jakarta.servlet.http.Cookie;
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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// market moved out to market-service; MarketDataService is mocked here instead of relying on the
// real simulator, since core-service now only holds MarketDataClient (an HTTP call over the network).
@SpringBootTest
@AutoConfigureMockMvc
class QuotesControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private MarketDataService marketData;

    @Test
    void quoteEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/quotes/AAPL"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedRequestReturnsQuotePayload() throws Exception {
        Cookie jwtCookie = loginAs("quotesuser1");
        when(marketData.findByTicker(eq("AAPL"))).thenReturn(Optional.of(sampleAaplQuote()));

        mockMvc.perform(get("/api/quotes/AAPL").cookie(jwtCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.quote.symbol").value("AAPL"))
                .andExpect(jsonPath("$.quote.name").isString())
                .andExpect(jsonPath("$.quote.price").isNumber())
                .andExpect(jsonPath("$.quote.priceChange").isNumber())
                .andExpect(jsonPath("$.quote.priceChangePercent").isNumber())
                .andExpect(jsonPath("$.quote.highPrice").isNumber())
                .andExpect(jsonPath("$.quote.lowPrice").isNumber())
                .andExpect(jsonPath("$.quote.openPrice").isNumber())
                .andExpect(jsonPath("$.quote.volume").isNumber())
                .andExpect(jsonPath("$.quote.marketCap").isNumber())
                .andExpect(jsonPath("$.quote.peRatio").isNumber())
                .andExpect(jsonPath("$.quote.dividendYield").isNumber())
                .andExpect(jsonPath("$.quote.lastUpdate").isString());
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

    // auth-service issues the jwt cookie in production; here the shared JwtService mints an
    // equivalent token so this service can be tested on its own.
    private Cookie loginAs(String username) {
        return new Cookie(JwtAuthenticationFilter.COOKIE_NAME, jwtService.generateToken(username));
    }

    private static QuoteSnapshot sampleAaplQuote() {
        MarketInstrument aapl = new MarketInstrument(
                1, "AAPL", "Apple Inc", "EQUITY", "USD", "US",
                227.55, 0.08, 0.25, 0.65, 1.5,
                15_200_000_000L, 55_000_000L, 6.75, 1.00);
        return new QuoteSnapshot(
                aapl, 228.10, 228.00, 228.20, 226.50, 229.00, 225.80, 226.90,
                12_345_678L, Instant.now(), LocalDate.now());
    }
}
