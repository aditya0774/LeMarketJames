package com.lemarketjames.reports;

import com.lemarketjames.common.config.PlatformSettings;
import com.lemarketjames.common.security.JwtAuthenticationFilter;
import com.lemarketjames.common.security.JwtService;
import com.lemarketjames.common.security.Role;
import com.lemarketjames.config.SecurityConfig;
import com.lemarketjames.reports.controller.SegmentActivityController;
import com.lemarketjames.reports.dto.SegmentActivityRow;
import com.lemarketjames.reports.period.ReportCalendar;
import com.lemarketjames.reports.repository.SegmentActivityRepository;
import com.lemarketjames.reports.service.SegmentActivityService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SegmentActivityController.class)
@Import({SecurityConfig.class, JwtService.class, PlatformSettings.class,
        ReportCalendar.class, SegmentActivityService.class})
class SegmentActivityControllerTest {
    private static final String PATH = "/api/v1/reports/activity";
    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @MockBean SegmentActivityRepository repository;

    private Cookie token(Role role) {
        return new Cookie(JwtAuthenticationFilter.COOKIE_NAME,
                jwt.generateToken("report-test", Set.of(role)));
    }

    @Test
    void anonymousAndInvalidTokensAreUnauthorized() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        mvc.perform(get(PATH).cookie(new Cookie(JwtAuthenticationFilter.COOKIE_NAME, "invalid")))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(repository);
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, mode = EnumSource.Mode.EXCLUDE, names = "ANALYST")
    void nonAnalystsCannotReadTheReport(Role role) throws Exception {
        mvc.perform(get(PATH).cookie(token(role))).andExpect(status().isForbidden());
        verifyNoInteractions(repository);
    }

    @Test
    void returnsOnlyTheContractAggregateFieldsAndUsesTheSelectedDates() throws Exception {
        when(repository.aggregate(any(), any())).thenReturn(List.of(
                new SegmentActivityRow("SAMPLE_SEGMENT", 2, 7, new BigDecimal("1234.5678"))));
        mvc.perform(get(PATH).cookie(token(Role.ANALYST))
                        .param("startDate", "2026-03-08").param("endDate", "2026-03-08"))
                .andExpect(status().isOk())
                // Strict JSON rejects any additional field, including identifying information.
                .andExpect(content().json("""
                        {"success":true,"data":[{"segment":"SAMPLE_SEGMENT",
                          "activeClients":2,"tradeCount":7,"totalTradedValue":1234.5678}]}
                        """, true));
        verify(repository).aggregate(LocalDateTime.parse("2026-03-08T05:00:00"),
                LocalDateTime.parse("2026-03-09T04:00:00"));
    }

    @Test
    void noTradesReturnsAnEmptyArray() throws Exception {
        when(repository.aggregate(any(), any())).thenReturn(List.of());
        mvc.perform(get(PATH).cookie(token(Role.ANALYST)))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"success\":true,\"data\":[]}", true));
    }

    @Test
    void reversedDatesAreRejectedBeforeQuerying() throws Exception {
        mvc.perform(get(PATH).cookie(token(Role.ANALYST))
                        .param("startDate", "2026-10-02").param("endDate", "2026-10-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isString());
        verifyNoInteractions(repository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-date", "2026-02-30", "2026-13-01"})
    void malformedDatesAreRejectedBeforeQuerying(String date) throws Exception {
        mvc.perform(get(PATH).cookie(token(Role.ANALYST)).param("startDate", date))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(repository);
    }
}
