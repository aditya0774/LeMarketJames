package com.lemarketjames.portfolio;

import com.lemarketjames.market.model.ChartRange;
import com.lemarketjames.portfolio.dto.PortfolioPoint;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PortfolioHistoryController.class)
class PortfolioHistoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PortfolioHistoryService historyService;

    @Test
    @WithMockUser(username = "alice")
    void returnsTheCallersHistoryForTheRange() throws Exception {
        when(historyService.history("alice", ChartRange.THREE_MONTHS)).thenReturn(List.of(
                new PortfolioPoint(Instant.parse("2026-10-14T20:00:00Z"), new BigDecimal("1234.50"))));

        mockMvc.perform(get("/api/v1/portfolio/history").param("range", "3m"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.range").value("3M"))
            .andExpect(jsonPath("$.points[0].value").value(1234.50));
    }

    @Test
    @WithMockUser(username = "alice")
    void unknownRangeIsABadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/portfolio/history").param("range", "5Y"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Unknown range"));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/portfolio/history")).andExpect(status().isUnauthorized());
    }
}
