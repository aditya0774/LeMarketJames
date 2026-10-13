package com.lemarketjames.reports.insight;

import com.lemarketjames.common.config.PlatformSettings;
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
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Access and HTTP shape of the volume insight, with real signed tokens and the service mocked.
 * Only ANALYST may read it (contract C7); CLIENT and TRADING_OPS, like every other role, get 403.
 */
@WebMvcTest(VolumeInsightController.class)
@Import({SecurityConfig.class, JwtService.class, PlatformSettings.class})
class VolumeInsightControllerTest {

    private static final String VOLUME = "/api/v1/reports/insights/volume";

    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;

    @MockBean VolumeInsightService service;

    private Cookie tokenFor(Role role) {
        return new Cookie(JwtAuthenticationFilter.COOKIE_NAME, jwt.generateToken("someone", Set.of(role)));
    }

    private static VolumeInsightResponse sample() {
        var current = new VolumeInsightResponse.PeriodVolume(LocalDate.parse("2026-10-05"),
                LocalDate.parse("2026-10-11"), 40, new BigDecimal("1200"), new BigDecimal("54000.00"));
        var previous = new VolumeInsightResponse.PeriodVolume(LocalDate.parse("2026-09-28"),
                LocalDate.parse("2026-10-04"), 32, new BigDecimal("1000"), new BigDecimal("41000.00"));
        var change = new VolumeInsightResponse.VolumeChange(new BigDecimal("200"), new BigDecimal("20.00"),
                new BigDecimal("13000.00"), null);
        return new VolumeInsightResponse("WEEK", current, previous, change, Instant.parse("2026-10-12T14:00:00Z"));
    }

    @Test
    void withoutATokenIsUnauthorized() throws Exception {
        mvc.perform(get(VOLUME).param("periodType", "WEEK")).andExpect(status().isUnauthorized());
        verify(service, never()).compareWithPreviousPeriod(any());
    }

    @Test
    void withATokenThatIsNotOursIsUnauthorized() throws Exception {
        mvc.perform(get(VOLUME).param("periodType", "WEEK")
                        .cookie(new Cookie(JwtAuthenticationFilter.COOKIE_NAME, "not-a-token")))
                .andExpect(status().isUnauthorized());
    }

    /** Every role except ANALYST, so CLIENT and TRADING_OPS are covered and a new role is refused by default. */
    @ParameterizedTest
    @EnumSource(value = Role.class, mode = EnumSource.Mode.EXCLUDE, names = "ANALYST")
    void everyOtherRoleIsForbidden(Role role) throws Exception {
        mvc.perform(get(VOLUME).param("periodType", "WEEK").cookie(tokenFor(role)))
                .andExpect(status().isForbidden());
        verify(service, never()).compareWithPreviousPeriod(any());
    }

    @Test
    void clientAndTradingOpsAreNamedAndForbidden() throws Exception {
        mvc.perform(get(VOLUME).param("periodType", "DAY").cookie(tokenFor(Role.CLIENT)))
                .andExpect(status().isForbidden());
        mvc.perform(get(VOLUME).param("periodType", "DAY").cookie(tokenFor(Role.TRADING_OPS)))
                .andExpect(status().isForbidden());
    }

    @Test
    void analystGetsVolumeAndChange() throws Exception {
        when(service.compareWithPreviousPeriod("WEEK")).thenReturn(sample());

        mvc.perform(get(VOLUME).param("periodType", "WEEK").cookie(tokenFor(Role.ANALYST)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodType").value("WEEK"))
                .andExpect(jsonPath("$.current.startDate").value("2026-10-05"))
                .andExpect(jsonPath("$.current.endDate").value("2026-10-11"))
                .andExpect(jsonPath("$.current.tradeCount").value(40))
                .andExpect(jsonPath("$.current.shareVolume").value(1200))
                .andExpect(jsonPath("$.previous.grossAmount").value(41000.00))
                .andExpect(jsonPath("$.change.shareVolume").value(200))
                .andExpect(jsonPath("$.change.shareVolumePercent").value(20.00))
                .andExpect(jsonPath("$.change.grossAmountPercent").doesNotExist())
                .andExpect(jsonPath("$.generatedAt").value("2026-10-12T14:00:00Z"));
    }

    @Test
    void theResponseNamesNoIndividual() throws Exception {
        when(service.compareWithPreviousPeriod("WEEK")).thenReturn(sample());

        String body = mvc.perform(get(VOLUME).param("periodType", "WEEK").cookie(tokenFor(Role.ANALYST)))
                .andReturn().getResponse().getContentAsString();

        for (String forbidden : new String[]{"clientId", "client_id", "accountId", "account_id", "orderId", "order_id"}) {
            org.junit.jupiter.api.Assertions.assertFalse(body.contains(forbidden), forbidden);
        }
    }

    @Test
    void aMissingPeriodTypeIsABadRequest() throws Exception {
        when(service.compareWithPreviousPeriod(isNull()))
                .thenThrow(new IllegalArgumentException("periodType is required (DAY, WEEK, MONTH or YEAR)"));

        mvc.perform(get(VOLUME).cookie(tokenFor(Role.ANALYST)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("periodType is required (DAY, WEEK, MONTH or YEAR)"));
    }

    @Test
    void anUnknownPeriodTypeIsABadRequest() throws Exception {
        when(service.compareWithPreviousPeriod("FORTNIGHT"))
                .thenThrow(new IllegalArgumentException("Invalid periodType: FORTNIGHT"));

        mvc.perform(get(VOLUME).param("periodType", "FORTNIGHT").cookie(tokenFor(Role.ANALYST)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }
}
