package com.lemarketjames.reports.insight;

import com.lemarketjames.common.config.PlatformSettings;
import com.lemarketjames.common.security.AccountAccessCheck;
import com.lemarketjames.common.security.JwtAuthenticationFilter;
import com.lemarketjames.common.security.JwtService;
import com.lemarketjames.common.security.Role;
import com.lemarketjames.config.SecurityConfig;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MostActiveStocksController.class)
@Import({SecurityConfig.class, JwtService.class, PlatformSettings.class})
class MostActiveStocksControllerTest {

    private static final String PATH = "/api/v1/reports/insights/most-active-stocks";

    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @MockBean AccountAccessCheck accessCheck;
    @MockBean MostActiveStocksService service;

    @Test
    void analystGetsTheTopStocksAndRefreshTime() throws Exception {
        when(service.currentWeek()).thenReturn(new MostActiveStocksResponse(
                LocalDate.parse("2026-10-12"), LocalDate.parse("2026-10-14"),
                List.of(new MostActiveStock(1, "AAPL", new BigDecimal("1000.00"), 4,
                        new BigDecimal("10"))), Instant.parse("2026-10-14T14:00:00Z")));

        mvc.perform(get(PATH).cookie(tokenFor(Role.ANALYST)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startDate").value("2026-10-12"))
                .andExpect(jsonPath("$.stocks[0].rank").value(1))
                .andExpect(jsonPath("$.stocks[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$.stocks[0].tradedValue").value(1000.00))
                .andExpect(jsonPath("$.generatedAt").value("2026-10-14T14:00:00Z"));
    }

    @Test
    void withoutATokenIsUnauthorized() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, mode = EnumSource.Mode.EXCLUDE, names = "ANALYST")
    void everyOtherRoleIncludingClientsAndTradingOpsIsForbidden(Role role) throws Exception {
        mvc.perform(get(PATH).cookie(tokenFor(role))).andExpect(status().isForbidden());
    }

    private Cookie tokenFor(Role role) {
        return new Cookie(JwtAuthenticationFilter.COOKIE_NAME, jwt.generateToken("someone", Set.of(role)));
    }
}