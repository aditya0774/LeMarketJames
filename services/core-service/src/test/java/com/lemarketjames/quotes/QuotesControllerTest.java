package com.lemarketjames.quotes;

import com.lemarketjames.common.security.JwtAuthenticationFilter;
import com.lemarketjames.common.security.JwtService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class QuotesControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Test
    void quoteEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/quotes/AAPL"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedRequestReturnsQuotePayload() throws Exception {
        Cookie jwtCookie = loginAs("quotesuser1");

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

        mockMvc.perform(get("/api/quotes/INVALID").cookie(jwtCookie))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Symbol not found"));
    }

    // auth-service issues the jwt cookie in production; here the shared JwtService mints an
    // equivalent token so this service can be tested on its own.
    private Cookie loginAs(String username) {
        return new Cookie(JwtAuthenticationFilter.COOKIE_NAME, jwtService.generateToken(username));
    }
}
