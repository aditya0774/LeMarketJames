package com.lemarketjames.cash;

import com.lemarketjames.portfolio.PortfolioService;
import com.lemarketjames.portfolio.dto.PortfolioBalance;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CashMovementController.class)
class CashMovementControllerTest {

    private static final UUID REQUEST_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");

    @Autowired private MockMvc mockMvc;
    @MockBean private CashMovementService movements;
    @MockBean private PortfolioService portfolio;

    @Test
    @WithMockUser(username = "alice")
    void depositIsScopedFromAuthenticationAndReturnsTheBalanceShape() throws Exception {
        when(movements.move("alice", CashMovementType.DEPOSIT, new BigDecimal("250.25"), REQUEST_ID))
                .thenReturn(new CashMovementResult(new BigDecimal("1250.25"), false));
        when(portfolio.getOwnPortfolio("alice")).thenReturn(balance("1250.25", "1250.25"));

        mockMvc.perform(post("/api/v1/portfolio/deposits").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":250.25,"requestId":"11111111-1111-4111-8111-111111111111"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.balance.cash").value(1250.25))
                .andExpect(jsonPath("$.balance.buyingPower").value(1250.25))
                .andExpect(jsonPath("$.balance.currency").value("USD"));

        verify(movements).move("alice", CashMovementType.DEPOSIT, new BigDecimal("250.25"), REQUEST_ID);
    }

    @Test
    @WithMockUser(username = "alice")
    void withdrawalUsesItsOwnMovementType() throws Exception {
        when(movements.move("alice", CashMovementType.WITHDRAWAL, new BigDecimal("100.00"), REQUEST_ID))
                .thenReturn(new CashMovementResult(new BigDecimal("900.00"), false));
        when(portfolio.getOwnPortfolio("alice")).thenReturn(balance("900.00", "900.00"));

        mockMvc.perform(post("/api/v1/portfolio/withdrawals").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":100.00,"requestId":"11111111-1111-4111-8111-111111111111"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance.cash").value(900.00));

        verify(movements).move("alice", CashMovementType.WITHDRAWAL, new BigDecimal("100.00"), REQUEST_ID);
    }

    @Test
    @WithMockUser(username = "alice")
    void movementRefusalsKeepTheirContractCodesAndMessages() throws Exception {
        when(movements.move(eq("alice"), eq(CashMovementType.WITHDRAWAL), any(), eq(REQUEST_ID)))
                .thenThrow(new CashMovementException(
                        CashMovementService.INSUFFICIENT_FUNDS, "You can withdraw up to $800.00."));

        mockMvc.perform(post("/api/v1/portfolio/withdrawals").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":800.01,"requestId":"11111111-1111-4111-8111-111111111111"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"))
                .andExpect(jsonPath("$.error").value("You can withdraw up to $800.00."));
    }

    @Test
    @WithMockUser(username = "ops")
    void staffWithoutAnAccountGetsNotFound() throws Exception {
        when(movements.move(eq("ops"), eq(CashMovementType.DEPOSIT), any(), eq(REQUEST_ID)))
                .thenThrow(new CashMovementException(CashMovementService.ACCOUNT_NOT_FOUND, "Account not found"));

        mockMvc.perform(post("/api/v1/portfolio/deposits").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":10,"requestId":"11111111-1111-4111-8111-111111111111"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"))
                .andExpect(jsonPath("$.error").value("Account not found"));
    }

    @Test
    @WithMockUser(username = "alice")
    void malformedRequestIdIsInvalidAmount() throws Exception {
        mockMvc.perform(post("/api/v1/portfolio/deposits").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":10,"requestId":"not-a-uuid"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INVALID_AMOUNT"))
                .andExpect(jsonPath("$.error").value("Request ID must be a UUID"));
    }

    @Test
    void anonymousRequestsAreUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/portfolio/deposits").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":10,"requestId":"11111111-1111-4111-8111-111111111111"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    private static PortfolioBalance balance(String cash, String buyingPower) {
        return new PortfolioBalance(
                new BigDecimal(cash), BigDecimal.ZERO, new BigDecimal(cash), new BigDecimal(buyingPower),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "USD");
    }
}
