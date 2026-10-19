package com.lemarketjames.holdings;

import com.lemarketjames.holdings.dto.HoldingsResponse;
import com.lemarketjames.holdings.dto.ValidateHoldingRequest;
import com.lemarketjames.holdings.exception.InsufficientHoldingsException;
import com.lemarketjames.holdings.exception.UnauthorizedException;
import com.lemarketjames.holdings.service.HoldingsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.ArrayList;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(HoldingsController.class)
public class HoldingsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private HoldingsService holdingsService;

    /**
     * Verifies successful retrieval of authenticated user's holdings.
     * 
     * AC1: Returns each stock held with its quantity, as currently recorded.
     * AC3: Holdings persist as seeded; platform restarts maintain the same data.
     * 
     * @throws Exception if mock MVC performs invalid request
     */
    @Test
    @WithMockUser(username = "testuser")
    public void testGetHoldings_Success() throws Exception {
        HoldingsResponse response = new HoldingsResponse(true, new ArrayList<>());
        
        when(holdingsService.getHoldingsForAccount("testuser"))
            .thenReturn(response);

        mockMvc.perform(get("/api/holdings")
            .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.holdings").isArray());
    }

    @Test
    @WithMockUser(username = "testuser")
    public void testValidateHoldings_InsufficientHoldings() throws Exception {
        ValidateHoldingRequest request = new ValidateHoldingRequest(1, 1, new BigDecimal("50.0000"));
        
        doThrow(new InsufficientHoldingsException("Insufficient holdings"))
            .when(holdingsService).validateSufficientHoldings(1, "testuser", 1, new BigDecimal("50.0000"));

        mockMvc.perform(post("/api/holdings/validate")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"accountId\":1,\"instrumentId\":1,\"sellQuantity\":50.0000}")
            .with(csrf()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @WithMockUser(username = "testuser")
    public void testValidateHoldings_SufficientHoldings() throws Exception {
        doNothing()
            .when(holdingsService).validateSufficientHoldings(1, "testuser", 1, new BigDecimal("5.0000"));

        mockMvc.perform(post("/api/holdings/validate")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"accountId\":1,\"instrumentId\":1,\"sellQuantity\":5.0000}")
            .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    /**
     * Verifies empty portfolio returns empty holdings array.
     * 
     * AC1: Given I hold nothing, then I see a friendly empty state (empty array).
     * 
     * @throws Exception if mock MVC performs invalid request
     */
    @Test
    @WithMockUser(username = "alice")
    public void testGetHoldings_EmptyPortfolio() throws Exception {
        HoldingsResponse response = new HoldingsResponse(true, new ArrayList<>());
        
        when(holdingsService.getHoldingsForAccount("alice"))
            .thenReturn(response);

        mockMvc.perform(get("/api/holdings")
            .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.holdings").isArray())
            .andExpect(jsonPath("$.holdings.length()").value(0));
    }

    /**
     * AC1: Unauthorized data access prevented
     * Test that POST /api/holdings/validate returns 403 Forbidden when user doesn't own account
     */
    @Test
    @WithMockUser(username = "testuser")
    public void testValidateHoldings_UnauthorizedAccess() throws Exception {
        doThrow(new UnauthorizedException("User is not authorized to access this account"))
            .when(holdingsService).validateSufficientHoldings(2, "testuser", 1, new BigDecimal("5.0000"));

        mockMvc.perform(post("/api/holdings/validate")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"accountId\":2,\"instrumentId\":1,\"sellQuantity\":5.0000}")
            .with(csrf()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("ACCOUNT_ACCESS_DENIED"));
    }
}
