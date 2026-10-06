package com.lemarketjames.reports;

import com.lemarketjames.common.config.PlatformSettings;
import com.lemarketjames.common.security.JwtAuthenticationFilter;
import com.lemarketjames.common.security.JwtService;
import com.lemarketjames.common.security.Role;
import com.lemarketjames.config.SecurityConfig;
import com.lemarketjames.reports.period.ReportCalendar;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Who may read reports (contract C7), checked on the ping endpoint with real signed tokens in the
 * {@code jwt} cookie, so the shared filter's role mapping and this service's rules are both
 * exercised. Only the web layer is loaded: no database is needed to decide access.
 */
@WebMvcTest(ReportPingController.class)
@Import({SecurityConfig.class, JwtService.class, ReportCalendar.class, PlatformSettings.class})
class ReportAccessTest {

    private static final String PING = "/api/v1/reports/ping";

    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;

    private Cookie tokenFor(Role role) {
        return new Cookie(JwtAuthenticationFilter.COOKIE_NAME, jwt.generateToken("someone", Set.of(role)));
    }

    @Test
    void withoutATokenIsUnauthorized() throws Exception {
        mvc.perform(get(PING)).andExpect(status().isUnauthorized());
    }

    @Test
    void withATokenThatIsNotOursIsUnauthorized() throws Exception {
        mvc.perform(get(PING).cookie(new Cookie(JwtAuthenticationFilter.COOKIE_NAME, "not-a-token")))
            .andExpect(status().isUnauthorized());
    }

    /** Every role except ANALYST, so a role added later is refused until someone decides otherwise. */
    @ParameterizedTest
    @EnumSource(value = Role.class, mode = EnumSource.Mode.EXCLUDE, names = "ANALYST")
    void everyOtherRoleIsForbidden(Role role) throws Exception {
        mvc.perform(get(PING).cookie(tokenFor(role))).andExpect(status().isForbidden());
    }

    @Test
    void analystGetsThePingBody() throws Exception {
        mvc.perform(get(PING).cookie(tokenFor(Role.ANALYST)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.service").value(ReportPingController.SERVICE_NAME))
            .andExpect(jsonPath("$.timeZone").value(new PlatformSettings().getReports().getTimeZone().getId()));
    }

    @Test
    void pathsOutsideReportsAreClosedEvenToAnalysts() throws Exception {
        mvc.perform(get("/api/v1/orders")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/orders").cookie(tokenFor(Role.ANALYST))).andExpect(status().isForbidden());
    }
}
