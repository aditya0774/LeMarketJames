package com.lemarketjames.reports;

import com.lemarketjames.common.config.PlatformSettings;
import com.lemarketjames.common.security.JwtAuthenticationFilter;
import com.lemarketjames.common.security.JwtService;
import com.lemarketjames.common.security.Role;
import com.lemarketjames.config.SecurityConfig;
import com.lemarketjames.reports.dto.TradesByStockReportRow;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API contract and performance tests for trades-by-stock report endpoint.
 * Verifies:
 * 1. Response structure matches contract specification
 * 2. Performance: responds quickly (baseline check, SLA verified in integration environment)
 * 3. No individual client data is exposed in response
 *
 * Uses WebMvcTest to test the web layer only (no database needed).
 */
@WebMvcTest(TradesReportController.class)
@Import({SecurityConfig.class, JwtService.class, PlatformSettings.class})
class TradesReportPerformanceAndContractTest {

    private static final String TRADES_BY_STOCK = "/api/v1/reports/trades-by-stock";
    // Generous on purpose: Jenkins runs stages in parallel, so a tight wall-clock limit on a mocked
    // call fails from machine load alone. It only has to catch a hang; the 10 s SLA is checked elsewhere.
    private static final long PERFORMANCE_BASELINE_MS = 2000L;

    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    
    @MockBean TradesReportService tradesReportService;

    private Cookie tokenFor(Role role) {
        return new Cookie(JwtAuthenticationFilter.COOKIE_NAME, jwt.generateToken("analyst", Set.of(role)));
    }

    @Test
    void responseComformsToContractStructure() throws Exception {
        // Arrange: Mock service to return sample data
        List<TradesByStockReportRow> sampleData = List.of(
                new TradesByStockReportRow("AAPL", new BigDecimal("150"), new BigDecimal("22500.75"), 8, 5),
                new TradesByStockReportRow("MSFT", new BigDecimal("200"), new BigDecimal("50200.00"), 10, 3)
        );
        when(tradesReportService.getTradesAggregateByStock(any(), any())).thenReturn(sampleData);

        // Act & Assert
        mvc.perform(get(TRADES_BY_STOCK).cookie(tokenFor(Role.ANALYST)))
                .andExpect(status().isOk())
                // Top-level response has success flag
                .andExpect(jsonPath("$.success").value(true))
                // Data array exists
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                // Each row has required fields
                .andExpect(jsonPath("$.data[0].symbol").isString())
                .andExpect(jsonPath("$.data[0].totalQuantity").isNumber())
                .andExpect(jsonPath("$.data[0].totalGrossAmount").isNumber())
                .andExpect(jsonPath("$.data[0].buyCount").isNumber())
                .andExpect(jsonPath("$.data[0].sellCount").isNumber());
    }

    @Test
    void performanceBaseline() throws Exception {
        // Arrange: Mock service
        List<TradesByStockReportRow> sampleData = List.of(
                new TradesByStockReportRow("AAPL", new BigDecimal("150"), new BigDecimal("22500.75"), 8, 5)
        );
        when(tradesReportService.getTradesAggregateByStock(any(), any())).thenReturn(sampleData);

        // Act: Call endpoint and measure time
        long startTime = System.currentTimeMillis();
        mvc.perform(get(TRADES_BY_STOCK).cookie(tokenFor(Role.ANALYST)))
                .andExpect(status().isOk());
        long elapsed = System.currentTimeMillis() - startTime;

        // Assert: Should respond very quickly (baseline check)
        // Note: The 10-second SLA is validated in the full integration environment.
        assertTrue(elapsed < PERFORMANCE_BASELINE_MS,
                String.format("Response took %dms, baseline check failed. " +
                        "This is a quick sanity check; full SLA validation happens in integration tests.", elapsed));
    }

    @Test
    void emptyListResponseIsValid() throws Exception {
        // Arrange: Mock service to return empty list
        when(tradesReportService.getTradesAggregateByStock(any(), any())).thenReturn(List.of());

        // Act & Assert
        mvc.perform(get(TRADES_BY_STOCK).cookie(tokenFor(Role.ANALYST)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void noClientDataLeak() throws Exception {
        // Arrange: Mock data
        List<TradesByStockReportRow> data = List.of(
                new TradesByStockReportRow("GOOGL", new BigDecimal("50"), new BigDecimal("8500.50"), 2, 1)
        );
        when(tradesReportService.getTradesAggregateByStock(any(), any())).thenReturn(data);

        // Act & Assert: Verify response contains only aggregate fields
        mvc.perform(get(TRADES_BY_STOCK).cookie(tokenFor(Role.ANALYST)))
                .andExpect(status().isOk())
                // Aggregate fields should exist
                .andExpect(jsonPath("$.data[0].symbol").exists())
                .andExpect(jsonPath("$.data[0].totalQuantity").exists())
                .andExpect(jsonPath("$.data[0].totalGrossAmount").exists())
                .andExpect(jsonPath("$.data[0].buyCount").exists())
                .andExpect(jsonPath("$.data[0].sellCount").exists())
                // Individual identifiers must NOT exist
                .andExpect(jsonPath("$.data[0].clientId").doesNotExist())
                .andExpect(jsonPath("$.data[0].accountId").doesNotExist())
                .andExpect(jsonPath("$.data[0].orderId").doesNotExist());
    }
}
