package com.lemarketjames.instruments;

import com.lemarketjames.common.security.JwtAuthenticationFilter;
import com.lemarketjames.common.security.JwtService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The supported stock list the frontend loads (from the test data.sql instruments). */
@SpringBootTest
@AutoConfigureMockMvc
class InstrumentControllerTest {

    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;

    @Test
    void listsInstrumentsForLoggedInUsers() throws Exception {
        mvc.perform(get("/api/v1/instruments")
                .cookie(new Cookie(JwtAuthenticationFilter.COOKIE_NAME, jwt.generateToken("anyone"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(greaterThan(0)))
            .andExpect(jsonPath("$[0].symbol").value("AAPL"))
            .andExpect(jsonPath("$[0].instrumentId").isNumber())
            .andExpect(jsonPath("$[0].name").isNotEmpty())
            .andExpect(jsonPath("$[0].tradable").value(true));
    }

    @Test
    void requiresLogin() throws Exception {
        mvc.perform(get("/api/v1/instruments")).andExpect(status().isUnauthorized());
    }
}
