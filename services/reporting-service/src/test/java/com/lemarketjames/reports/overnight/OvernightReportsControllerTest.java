package com.lemarketjames.reports.overnight;

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
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * LMKT-45: access and JSON shape of the overnight reports list (contract C6), with real signed
 * tokens and the service mocked. Only ANALYST may read it (contract C7).
 */
@WebMvcTest(OvernightReportsController.class)
@Import({SecurityConfig.class, JwtService.class, PlatformSettings.class})
class OvernightReportsControllerTest {

    private static final String OVERNIGHT = "/api/v1/reports/overnight";
    private static final UUID READY_ID = UUID.fromString("8f2c0a4e-1111-4b6e-9c55-000000000001");

    @Autowired MockMvc mvc;
    // These tests sign tokens for users with no account row; the per-request account check (LMKT-57)
    // is tested in libs/common, and a mock answers "allowed".
    @MockitoBean AccountAccessCheck accessCheck;
    @Autowired JwtService jwt;

    @MockitoBean OvernightReportsService service;

    private Cookie tokenFor(Role role) {
        return new Cookie(JwtAuthenticationFilter.COOKIE_NAME, jwt.generateToken("someone", Set.of(role)));
    }

    private static OvernightReportsResponse sample() {
        LocalDate runDate = LocalDate.parse("2026-10-14");
        Instant scheduledFor = Instant.parse("2026-10-15T06:00:00Z");
        Instant readyBy = Instant.parse("2026-10-15T11:00:00Z");
        return new OvernightReportsResponse(true, "America/New_York", List.of(
                new OvernightReportsResponse.Run(READY_ID, "Activity by stock", runDate, RunStatus.READY,
                        scheduledFor, readyBy, Instant.parse("2026-10-15T06:04:12Z"), null),
                new OvernightReportsResponse.Run(UUID.randomUUID(), "Stub overnight report", runDate, RunStatus.FAILED,
                        scheduledFor, readyBy, null, "Source data missing"),
                new OvernightReportsResponse.Run(UUID.randomUUID(), "Activity by client segment", runDate, RunStatus.LATE,
                        scheduledFor, readyBy, Instant.parse("2026-10-15T11:12:00Z"), null)));
    }

    @Test
    void withoutATokenIsUnauthorized() throws Exception {
        mvc.perform(get(OVERNIGHT)).andExpect(status().isUnauthorized());
        verify(service, never()).recentRuns();
    }

    @Test
    void withATokenThatIsNotOursIsUnauthorized() throws Exception {
        mvc.perform(get(OVERNIGHT).cookie(new Cookie(JwtAuthenticationFilter.COOKIE_NAME, "not-a-token")))
                .andExpect(status().isUnauthorized());
    }

    /** Every role except ANALYST, so CLIENT and TRADING_OPS are covered and a new role is refused by default. */
    @ParameterizedTest
    @EnumSource(value = Role.class, mode = EnumSource.Mode.EXCLUDE, names = "ANALYST")
    void everyOtherRoleIsForbidden(Role role) throws Exception {
        mvc.perform(get(OVERNIGHT).cookie(tokenFor(role))).andExpect(status().isForbidden());
        verify(service, never()).recentRuns();
    }

    @Test
    void analystGetsEachRunWithItsStatusReadyTimeAndReason() throws Exception {
        when(service.recentRuns()).thenReturn(sample());

        mvc.perform(get(OVERNIGHT).cookie(tokenFor(Role.ANALYST)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.timeZone").value("America/New_York"))
                .andExpect(jsonPath("$.data[0].id").value(READY_ID.toString()))
                .andExpect(jsonPath("$.data[0].name").value("Activity by stock"))
                .andExpect(jsonPath("$.data[0].runDate").value("2026-10-14"))
                .andExpect(jsonPath("$.data[0].status").value("READY"))
                .andExpect(jsonPath("$.data[0].scheduledFor").value("2026-10-15T06:00:00Z"))
                .andExpect(jsonPath("$.data[0].readyBy").value("2026-10-15T11:00:00Z"))
                .andExpect(jsonPath("$.data[0].readyAt").value("2026-10-15T06:04:12Z"))
                .andExpect(jsonPath("$.data[0].failureReason").value(nullValue()))
                .andExpect(jsonPath("$.data[1].status").value("FAILED"))
                .andExpect(jsonPath("$.data[1].failureReason").value("Source data missing"))
                .andExpect(jsonPath("$.data[1].readyAt").value(nullValue()))
                .andExpect(jsonPath("$.data[2].status").value("LATE"))
                .andExpect(jsonPath("$.data[2].readyAt").value("2026-10-15T11:12:00Z"));
    }

    @Test
    void emptyFieldsAreSentAsNullNotLeftOut() throws Exception {
        // C6 says null; the staff app's model expects the keys to be there.
        when(service.recentRuns()).thenReturn(sample());

        String body = mvc.perform(get(OVERNIGHT).cookie(tokenFor(Role.ANALYST)))
                .andReturn().getResponse().getContentAsString();

        assertTrue(body.contains("\"readyAt\":null"), body);
        assertTrue(body.contains("\"failureReason\":null"), body);
    }

    @Test
    void theResponseNamesNoIndividual() throws Exception {
        when(service.recentRuns()).thenReturn(sample());

        String body = mvc.perform(get(OVERNIGHT).cookie(tokenFor(Role.ANALYST)))
                .andReturn().getResponse().getContentAsString();

        for (String forbidden : new String[]{"clientId", "client_id", "accountId", "account_id", "orderId", "order_id"}) {
            assertFalse(body.contains(forbidden), forbidden);
        }
    }

    @Test
    void anEmptyListIsStillASuccess() throws Exception {
        when(service.recentRuns()).thenReturn(new OvernightReportsResponse(true, "America/New_York", List.of()));

        mvc.perform(get(OVERNIGHT).cookie(tokenFor(Role.ANALYST)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }
}
