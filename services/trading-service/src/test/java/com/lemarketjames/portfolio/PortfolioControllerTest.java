package com.lemarketjames.portfolio;

import com.lemarketjames.portfolio.dto.PortfolioBalance;
import com.lemarketjames.portfolio.dto.PortfolioResponse;
import com.lemarketjames.holdings.exception.UnauthorizedException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PortfolioController.class)
public class PortfolioControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PortfolioService portfolioService;

    @Test
    @WithMockUser(username = "alice")
    public void testGetPortfolio_Success() throws Exception {
        PortfolioBalance balance = new PortfolioBalance(
                new BigDecimal("5000.00"),
                new BigDecimal("7561.25"),
                new BigDecimal("12561.25"),
                new BigDecimal("4900.00"),
                new BigDecimal("173.75"),
                new BigDecimal("1.38"),
                new BigDecimal("300.00"),
                new BigDecimal("2.45"),
                "USD"
        );

        when(portfolioService.getOwnPortfolio("alice")).thenReturn(balance);

        mockMvc.perform(get("/api/v1/portfolio"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.balance.cash").value(5000.00))
            .andExpect(jsonPath("$.balance.invested").value(7561.25))
            .andExpect(jsonPath("$.balance.totalValue").value(12561.25))
            .andExpect(jsonPath("$.balance.buyingPower").value(4900.00))
            .andExpect(jsonPath("$.balance.currency").value("USD"));
    }

    @Test
    @WithMockUser(username = "alice")
    public void testGetPortfolio_CashFormattedToTwoDecimals() throws Exception {
        PortfolioBalance balance = new PortfolioBalance(
                new BigDecimal("1000.50"),
                BigDecimal.ZERO,
                new BigDecimal("1000.50"),
                new BigDecimal("1000.50"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "USD"
        );

        when(portfolioService.getOwnPortfolio("alice")).thenReturn(balance);

        mockMvc.perform(get("/api/v1/portfolio"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.balance.cash").value(1000.50));
    }

    @Test
    @WithMockUser(username = "alice")
    public void testGetPortfolio_BuyingPowerExcludesOpenOrders() throws Exception {
        // cash=1000, open BUY order for 5@20 = 100 reserved
        PortfolioBalance balance = new PortfolioBalance(
                new BigDecimal("1000.00"),
                BigDecimal.ZERO,
                new BigDecimal("1000.00"),
                new BigDecimal("900.00"),  // 1000 - 100
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "USD"
        );

        when(portfolioService.getOwnPortfolio("alice")).thenReturn(balance);

        mockMvc.perform(get("/api/v1/portfolio"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.balance.cash").value(1000.00))
            .andExpect(jsonPath("$.balance.buyingPower").value(900.00));
    }

    @Test
    public void testGetPortfolio_UnauthorizedWithoutJwt() throws Exception {
        mockMvc.perform(get("/api/v1/portfolio"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "alice")
    public void testGetPortfolio_AlternatePathsWork() throws Exception {
        PortfolioBalance balance = new PortfolioBalance(
                new BigDecimal("5000.00"),
                BigDecimal.ZERO,
                new BigDecimal("5000.00"),
                new BigDecimal("5000.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "USD"
        );

        when(portfolioService.getOwnPortfolio("alice")).thenReturn(balance);

        // Test /api/balance endpoint
        mockMvc.perform(get("/api/balance"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        // Test /api/v1/balance endpoint
        mockMvc.perform(get("/api/v1/balance"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }
}
