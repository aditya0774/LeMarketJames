package com.lemarketjames.reports.insight;

import com.lemarketjames.common.config.PlatformSettings;
import com.lemarketjames.common.security.AccountAccessCheck;
import com.lemarketjames.common.security.JwtAuthenticationFilter;
import com.lemarketjames.common.security.JwtService;
import com.lemarketjames.common.security.Role;
import com.lemarketjames.config.SecurityConfig;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Access and HTTP shape of the active-client trend, with real signed tokens and the service mocked.
 * Only ANALYST may read it (contract C7); CLIENT and TRADING_OPS, like every other role, get 403.
 */
@WebMvcTest(ActiveClientTrendController.class)
@Import({SecurityConfig.class, JwtService.class, PlatformSettings.class})
class ActiveClientTrendControllerTest {

    private static final String PATH = "/api/v1/reports/insights/active-clients";

    @Autowired MockMvc mvc;
    // Tokens are signed for users with no account row; the per-request account check is tested in libs/common.
    @MockitoBean AccountAccessCheck accessCheck;
    @Autowired JwtService jwt;

    @MockBean ActiveClientTrendService service;

    private Cookie tokenFor(Role role) {
        return new Cookie(JwtAuthenticationFilter.COOKIE_NAME, jwt.generateToken("someone", Set.of(role)));
    }

    private static ActiveClientTrendResponse sample() {
        return new ActiveClientTrendResponse(LocalDate.parse("2026-09-15"), LocalDate.parse("2026-09-16"),
                List.of(new ActiveClientTrendResponse.DayCount(LocalDate.parse("2026-09-15"), 12),
                        new ActiveClientTrendResponse.DayCount(LocalDate.parse("2026-09-16"), 0)),
                Instant.parse("2026-09-16T14:00:00Z"));
    }

    @Test
    void withoutATokenIsUnauthorized() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        verify(service, never()).trend();
    }

    @Test
    void withATokenThatIsNotOursIsUnauthorized() throws Exception {
        mvc.perform(get(PATH).cookie(new Cookie(JwtAuthenticationFilter.COOKIE_NAME, "not-a-token")))
                .andExpect(status().isUnauthorized());
    }

    /** Every role except ANALYST, so a new role is refused by default. */
    @ParameterizedTest
    @EnumSource(value = Role.class, mode = EnumSource.Mode.EXCLUDE, names = "ANALYST")
    void everyOtherRoleIsForbidden(Role role) throws Exception {
        mvc.perform(get(PATH).cookie(tokenFor(role))).andExpect(status().isForbidden());
        verify(service, never()).trend();
    }

    @Test
    void clientAndTradingOpsAreNamedAndForbidden() throws Exception {
        mvc.perform(get(PATH).cookie(tokenFor(Role.CLIENT))).andExpect(status().isForbidden());
        mvc.perform(get(PATH).cookie(tokenFor(Role.TRADING_OPS))).andExpect(status().isForbidden());
    }

    @Test
    void analystGetsTheDailyCounts() throws Exception {
        when(service.trend()).thenReturn(sample());

        mvc.perform(get(PATH).cookie(tokenFor(Role.ANALYST)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startDate").value("2026-09-15"))
                .andExpect(jsonPath("$.endDate").value("2026-09-16"))
                .andExpect(jsonPath("$.days.length()").value(2))
                .andExpect(jsonPath("$.days[0].date").value("2026-09-15"))
                .andExpect(jsonPath("$.days[0].activeClients").value(12))
                .andExpect(jsonPath("$.days[1].activeClients").value(0))
                .andExpect(jsonPath("$.generatedAt").value("2026-09-16T14:00:00Z"));
    }

    @Test
    void theResponseNamesNoIndividual() throws Exception {
        when(service.trend()).thenReturn(sample());

        String body = mvc.perform(get(PATH).cookie(tokenFor(Role.ANALYST)))
                .andReturn().getResponse().getContentAsString();

        for (String forbidden : new String[]{"clientId", "client_id", "accountId", "account_id", "orderId", "order_id"}) {
            assertFalse(body.contains(forbidden), forbidden);
        }
    }
}
