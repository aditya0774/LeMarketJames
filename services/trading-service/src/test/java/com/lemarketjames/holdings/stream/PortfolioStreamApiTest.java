package com.lemarketjames.holdings.stream;

import com.lemarketjames.common.domain.AccountRepository;
import com.lemarketjames.common.security.AccountAccessCheck;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Who may open GET /api/v1/portfolio/stream, and whose account it is for. */
@SpringBootTest
@AutoConfigureMockMvc
class PortfolioStreamApiTest {

    private static final String STREAM = "/api/v1/portfolio/stream";

    @Autowired MockMvc mockMvc;
    @MockitoBean AccountRepository accounts;
    @MockitoBean AccountAccessCheck accessCheck;
    @MockitoBean PortfolioStreamService streams;

    @Test
    void clientCanOpenTheStreamOfTheirOwnAccount() throws Exception {
        when(accounts.findAccountIdByUsername("alice")).thenReturn(Optional.of(7));
        when(streams.subscribe(7)).thenReturn(new SseEmitter());

        mockMvc.perform(get(STREAM).with(user("alice").roles("CLIENT")))
            .andExpect(status().isOk())
            .andExpect(request().asyncStarted());

        verify(streams).subscribe(7);
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get(STREAM)).andExpect(status().isUnauthorized());

        verify(streams, never()).subscribe(anyInt());
    }

    @Test
    void staffAreRejected() throws Exception {
        mockMvc.perform(get(STREAM).with(user("ops").roles("TRADING_OPS")))
            .andExpect(status().isForbidden());

        verify(streams, never()).subscribe(anyInt());
    }

    @Test
    void clientWithoutAnAccountGetsNotFound() throws Exception {
        when(accounts.findAccountIdByUsername("nobody")).thenReturn(Optional.empty());

        mockMvc.perform(get(STREAM).with(user("nobody").roles("CLIENT")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));

        verify(streams, never()).subscribe(anyInt());
    }
}
