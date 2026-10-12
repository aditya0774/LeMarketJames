package com.lemarketjames.reports;

import com.lemarketjames.common.config.PlatformSettings;
import com.lemarketjames.common.security.JwtAuthenticationFilter;
import com.lemarketjames.common.security.JwtService;
import com.lemarketjames.common.security.Role;
import com.lemarketjames.config.SecurityConfig;
import com.lemarketjames.reports.dto.TradesByStockReportRow;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for TradesReportController.
 * Verifies role enforcement (ANALYST only), role-based access control,
 * date range parameter handling, and response structure using WebMvcTest.
 */
@WebMvcTest(TradesReportController.class)
@Import({SecurityConfig.class, JwtService.class, PlatformSettings.class})
class TradesReportControllerTest {

    private static final String TRADES_BY_STOCK = "/api/v1/reports/trades-by-stock";

    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    
    @MockBean TradesReportService tradesReportService;

    private Cookie tokenFor(Role role) {
        return new Cookie(JwtAuthenticationFilter.COOKIE_NAME, jwt.generateToken("analyst-user", Set.of(role)));
    }

    @Test
    void withoutATokenIsUnauthorized() throws Exception {
        mvc.perform(get(TRADES_BY_STOCK))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void withAnInvalidTokenIsUnauthorized() throws Exception {
        mvc.perform(get(TRADES_BY_STOCK).cookie(
                new Cookie(JwtAuthenticationFilter.COOKIE_NAME, "invalid-token")))
                .andExpect(status().isUnauthorized());
    }

    /**
     * All roles except ANALYST should be forbidden.
     * This ensures analysts-only access and fails early if new roles are added without authorization review.
     */
    @ParameterizedTest
    @EnumSource(value = Role.class, mode = EnumSource.Mode.EXCLUDE, names = "ANALYST")
    void everyOtherRoleIsForbidden(Role role) throws Exception {
        mvc.perform(get(TRADES_BY_STOCK).cookie(tokenFor(role)))
                .andExpect(status().isForbidden());
    }

    @Test
    void analystGetsTradesAggregateSuccessfully() throws Exception {
        // Arrange: Mock the service to return sample aggregate data
        // Using untyped any() to match both null and LocalDate values
        List<TradesByStockReportRow> sampleData = List.of(
                new TradesByStockReportRow("AAPL", new BigDecimal("150"), new BigDecimal("22500.75"), 8, 5),
                new TradesByStockReportRow("MSFT", new BigDecimal("200"), new BigDecimal("50200.00"), 10, 3)
        );
        when(tradesReportService.getTradesAggregateByStock(any(), any())).thenReturn(sampleData);

        // Act & Assert
        mvc.perform(get(TRADES_BY_STOCK).cookie(tokenFor(Role.ANALYST)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$.data[0].totalQuantity").value(150))
                .andExpect(jsonPath("$.data[0].totalGrossAmount").value(22500.75))
                .andExpect(jsonPath("$.data[0].buyCount").value(8))
                .andExpect(jsonPath("$.data[0].sellCount").value(5))
                .andExpect(jsonPath("$.data[1].symbol").value("MSFT"))
                .andExpect(jsonPath("$.data[1].totalQuantity").value(200));
    }

    @Test
    void acceptsDateRangeQueryParameters() throws Exception {
        // Arrange
        List<TradesByStockReportRow> sampleData = List.of(
                new TradesByStockReportRow("AAPL", new BigDecimal("100"), new BigDecimal("15000.00"), 5, 3)
        );
        when(tradesReportService.getTradesAggregateByStock(
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 7)))
                .thenReturn(sampleData);

        // Act & Assert
        mvc.perform(get(TRADES_BY_STOCK)
                .param("startDate", "2026-10-01")
                .param("endDate", "2026-10-07")
                .cookie(tokenFor(Role.ANALYST)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void rejectInvalidDateRange() throws Exception {
        // Arrange: Mock the service to throw an exception for invalid date range
        when(tradesReportService.getTradesAggregateByStock(
                LocalDate.of(2026, 10, 7),
                LocalDate.of(2026, 10, 1)))
                .thenThrow(new IllegalArgumentException("Start date must not be after end date"));

        // Act & Assert
        mvc.perform(get(TRADES_BY_STOCK)
                .param("startDate", "2026-10-07")
                .param("endDate", "2026-10-01")
                .cookie(tokenFor(Role.ANALYST)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void analystGetsEmptyListWhenNoTradesExist() throws Exception {
        // Arrange: Mock the service to return empty list
        when(tradesReportService.getTradesAggregateByStock(any(), any())).thenReturn(List.of());

        // Act & Assert
        mvc.perform(get(TRADES_BY_STOCK).cookie(tokenFor(Role.ANALYST)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void noClientDataIsExposedInResponse() throws Exception {
        // Arrange: Mock data (no client_id, account_id, or order_id fields should exist)
        List<TradesByStockReportRow> data = List.of(
                new TradesByStockReportRow("GOOGL", new BigDecimal("50"), new BigDecimal("8500.50"), 2, 1)
        );
        when(tradesReportService.getTradesAggregateByStock(any(), any())).thenReturn(data);

        // Act & Assert: Verify only aggregate fields are in response
        mvc.perform(get(TRADES_BY_STOCK).cookie(tokenFor(Role.ANALYST)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].symbol").exists())
                .andExpect(jsonPath("$.data[0].totalQuantity").exists())
                .andExpect(jsonPath("$.data[0].totalGrossAmount").exists())
                .andExpect(jsonPath("$.data[0].buyCount").exists())
                .andExpect(jsonPath("$.data[0].sellCount").exists())
                // These should NOT exist in the response:
                .andExpect(jsonPath("$.data[0].clientId").doesNotExist())
                .andExpect(jsonPath("$.data[0].accountId").doesNotExist())
                .andExpect(jsonPath("$.data[0].orderId").doesNotExist());
    }
}
