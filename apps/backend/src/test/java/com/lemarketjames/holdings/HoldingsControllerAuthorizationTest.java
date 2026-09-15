package com.lemarketjames.holdings;

import com.lemarketjames.holdings.dto.HoldingsResponse;
import com.lemarketjames.holdings.service.HoldingsService;
import com.lemarketjames.common.UnauthorizedAccessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * Integration tests for Holdings endpoint authorization.
 * 
 * Tests that users can only access their own account's holdings.
 * Verifies that 403 Forbidden is returned when unauthorized.
 */
@SpringBootTest
@AutoConfigureMockMvc
public class HoldingsControllerAuthorizationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    /**
     * Test 1: User accessing their OWN account - should succeed (200)
     */
    @Test
    @WithMockUser(username = "joanna_trader")
    public void testGetOwnHoldings_Success() throws Exception {
        // joanna_trader owns account 1
        mockMvc.perform(get("/api/holdings?accountId=1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }
    
    /**
     * Test 2: User accessing SOMEONE ELSE'S account - should fail (403)
     * 
     * This is the CRITICAL TEST that proves our security fix works!
     * joanna_trader tries to access david_investor's account (account 2)
     */
    @Test
    @WithMockUser(username = "joanna_trader")
    public void testGetOtherUsersHoldings_Forbidden() throws Exception {
        // joanna_trader owns account 1, NOT account 2
        // david_investor owns account 2
        mockMvc.perform(get("/api/holdings?accountId=2"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("You do not have permission to access this resource"))
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED_ACCESS"));
    }
    
    /**
     * Test 3: Different user also cannot access account 2 (unless they own it)
     */
    @Test
    @WithMockUser(username = "priya_analyst")
    public void testGetOtherUsersHoldings_AlsoDenied() throws Exception {
        // priya_analyst owns account 3, NOT account 2
        mockMvc.perform(get("/api/holdings?accountId=2"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED_ACCESS"));
    }
    
    /**
     * Test 4: User accessing their own account (account 2) - should succeed
     */
    @Test
    @WithMockUser(username = "david_investor")
    public void testGetOwnHoldings_Account2_Success() throws Exception {
        // david_investor owns account 2
        mockMvc.perform(get("/api/holdings?accountId=2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }
    
    /**
     * Test 5: User accessing account 1 (not theirs) - should fail
     */
    @Test
    @WithMockUser(username = "david_investor")
    public void testGetOtherUsersHoldings_Account1_Forbidden() throws Exception {
        // david_investor owns account 2, NOT account 1
        mockMvc.perform(get("/api/holdings?accountId=1"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED_ACCESS"));
    }
}
