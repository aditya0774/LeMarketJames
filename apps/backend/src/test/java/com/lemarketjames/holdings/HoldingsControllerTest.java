package com.lemarketjames.holdings;

import com.lemarketjames.holdings.dto.HoldingsResponse;
import com.lemarketjames.holdings.dto.ValidateHoldingRequest;
import com.lemarketjames.holdings.exception.InsufficientHoldingsException;
import com.lemarketjames.holdings.service.HoldingsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.ArrayList;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class HoldingsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private HoldingsService holdingsService;

    /**
     * AC1: Holdings retrieved - verify GET /api/holdings returns 200 with holdings
     */
    @Test
    @WithMockUser(username = "testuser")
    public void testGetHoldings_Success() throws Exception {
        Integer accountId = 1;
        HoldingsResponse response = new HoldingsResponse(true, new ArrayList<>());
        
        when(holdingsService.getHoldingsForAccount(accountId))
            .thenReturn(response);

        mockMvc.perform(get("/api/holdings?accountId=" + accountId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.holdings").isArray());
    }

    /**
     * AC2: Overselling rejected - verify POST /api/holdings/validate returns 400 on insufficient
     */
    @Test
    @WithMockUser(username = "testuser")
    public void testValidateHoldings_InsufficientHoldings() throws Exception {
        ValidateHoldingRequest request = new ValidateHoldingRequest(1, 1, new BigDecimal("50.0000"));
        
        doThrow(new InsufficientHoldingsException("Insufficient holdings"))
            .when(holdingsService).validateSufficientHoldings(1, 1, new BigDecimal("50.0000"));

        mockMvc.perform(post("/api/holdings/validate")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"accountId\":1,\"instrumentId\":1,\"sellQuantity\":50.0000}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").exists());
    }

    /**
     * AC2: Overselling rejected - verify POST /api/holdings/validate returns 200 on sufficient
     */
    @Test
    @WithMockUser(username = "testuser")
    public void testValidateHoldings_SufficientHoldings() throws Exception {
        doNothing()
            .when(holdingsService).validateSufficientHoldings(1, 1, new BigDecimal("5.0000"));

        mockMvc.perform(post("/api/holdings/validate")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"accountId\":1,\"instrumentId\":1,\"sellQuantity\":5.0000}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }
}
