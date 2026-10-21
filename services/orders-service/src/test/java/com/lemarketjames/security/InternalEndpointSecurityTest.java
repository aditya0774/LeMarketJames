package com.lemarketjames.security;

import com.lemarketjames.common.security.InternalApiTokenFilter;
import com.lemarketjames.common.security.JwtAuthenticationFilter;
import com.lemarketjames.common.security.JwtService;
import com.lemarketjames.common.security.Role;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BR-02: the execution control can pause execution for the whole stack, so only a holder of the
 * shared internal token may use it, never a browser session, client or staff.
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:ordersInternalSecurity;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "lmj.execution.control-enabled=true"})
@AutoConfigureMockMvc
class InternalEndpointSecurityTest {

    private static final String CONTROL = "/internal/execution/control";
    // Changes nothing: execution is on and settlement available by default.
    private static final String UNCHANGED = "{\"enabled\":true,\"settlementAvailable\":true}";

    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @Value("${internal.api-token}") String token;

    @Test
    void theControlWithoutATokenIsUnauthorized() throws Exception {
        mvc.perform(put(CONTROL).contentType(MediaType.APPLICATION_JSON).content(UNCHANGED))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void theControlWithAWrongTokenIsUnauthorized() throws Exception {
        mvc.perform(put(CONTROL).header(InternalApiTokenFilter.HEADER, token + "x")
                .contentType(MediaType.APPLICATION_JSON).content(UNCHANGED))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void anOperationsSessionCannotUseTheControl() throws Exception {
        Cookie ops = new Cookie(JwtAuthenticationFilter.COOKIE_NAME,
            jwt.generateToken("olivia_ops", Set.of(Role.TRADING_OPS)));

        mvc.perform(put(CONTROL).cookie(ops).contentType(MediaType.APPLICATION_JSON).content(UNCHANGED))
            .andExpect(status().isForbidden());
    }

    @Test
    void theTokenReachesTheControl() throws Exception {
        mvc.perform(put(CONTROL).header(InternalApiTokenFilter.HEADER, token)
                .contentType(MediaType.APPLICATION_JSON).content(UNCHANGED))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.enabled").value(true));
    }
}
