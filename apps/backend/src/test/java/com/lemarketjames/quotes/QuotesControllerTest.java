package com.lemarketjames.quotes;

import com.lemarketjames.auth.security.JwtAuthenticationFilter;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class QuotesControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void quoteEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/quotes/AAPL"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedRequestReturnsQuotePayload() throws Exception {
        Cookie jwtCookie = registerAndLogin("quotesuser1", "quotesuser1@example.com");

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
        Cookie jwtCookie = registerAndLogin("quotesuser2", "quotesuser2@example.com");

        mockMvc.perform(get("/api/quotes/INVALID").cookie(jwtCookie))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Symbol not found"));
    }

    private Cookie registerAndLogin(String username, String email) throws Exception {
        String registerJson = """
                {
                  "username": "%s",
                  "password": "Pass123!",
                  "email": "%s",
                  "fullName": "Quote User",
                  "streetAddress": "123 Main St",
                  "city": "Springfield",
                  "state": "IL",
                  "zipCode": "62701",
                  "country": "USA",
                  "ssn": "123-45-6789",
                  "initialDeposit": 500,
                  "investmentExperience": "beginner",
                                                                        "employmentStatus": "employed",
                  "dateOfBirth": "1990-01-01",
                  "phoneNumber": "(555) 123-4567",
                  "termsAccepted": true
                }
                """.formatted(username, email);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson))
                .andExpect(status().isCreated());

        String loginJson = """
                {
                  "username": "%s",
                  "password": "Pass123!"
                }
                """.formatted(username);

        Cookie jwtCookie = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getCookie(JwtAuthenticationFilter.COOKIE_NAME);

        assertNotNull(jwtCookie);
        return jwtCookie;
    }
}
