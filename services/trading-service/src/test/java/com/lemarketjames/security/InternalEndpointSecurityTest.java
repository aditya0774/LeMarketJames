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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BR-02: no endpoint without a credential. Settlement moves cash and shares for any account, so
 * only another service holding the shared internal token may call it; a browser session, even a
 * signed-in client's, may not. Prices stay readable without one.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:internalSecurity;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class InternalEndpointSecurityTest {

    private static final String SETTLE = "/internal/holdings/settle";

    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @Value("${internal.api-token}") String token;

    @Test
    void settlementWithoutATokenIsUnauthorized() throws Exception {
        mvc.perform(post(SETTLE).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void settlementWithAWrongTokenIsUnauthorized() throws Exception {
        mvc.perform(post(SETTLE).header(InternalApiTokenFilter.HEADER, "not-" + token)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void aClientSessionCannotSettle() throws Exception {
        Cookie client = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, jwt.generateToken("alice", Set.of(Role.CLIENT)));

        mvc.perform(post(SETTLE).cookie(client).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void theTokenReachesTheEndpoint() throws Exception {
        // An empty body is refused by the endpoint itself, which proves the request got past security.
        int status = mvc.perform(post(SETTLE).header(InternalApiTokenFilter.HEADER, token)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andReturn().getResponse().getStatus();

        assertEquals(400, status);
    }

    @Test
    void pricesAreReadableWithoutACredentialButNothingElseIsOpen() throws Exception {
        mvc.perform(get("/api/market/quotes")).andExpect(status().isOk());
        mvc.perform(post("/api/market/quotes")).andExpect(status().isUnauthorized());
    }
}
