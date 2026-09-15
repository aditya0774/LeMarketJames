package com.lemarketjames.orders;

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
 * Integration tests for Orders endpoint authorization.
 * 
 * Tests that users can only access their own account's orders.
 * Verifies that 403 Forbidden is returned when unauthorized.
 */
@SpringBootTest
@AutoConfigureMockMvc
public class OrdersControllerAuthorizationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    /**
     * Test 1: User accessing their OWN orders - should succeed (200)
     */
    @Test
    @WithMockUser(username = "joanna_trader")
    public void testGetOwnOrders_Success() throws Exception {
        // joanna_trader owns account 1
        mockMvc.perform(get("/api/v1/orders/account/1"))
            .andExpect(status().isOk());
    }
    
    /**
     * Test 2: User accessing SOMEONE ELSE'S orders - should fail (403)
     * 
     * This is the CRITICAL TEST that proves our security fix works!
     * joanna_trader tries to access david_investor's orders (account 2)
     */
    @Test
    @WithMockUser(username = "joanna_trader")
    public void testGetOtherUsersOrders_Forbidden() throws Exception {
        // joanna_trader owns account 1, NOT account 2
        // david_investor owns account 2
        mockMvc.perform(get("/api/v1/orders/account/2"))
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
    public void testGetOtherUsersOrders_AlsoDenied() throws Exception {
        // priya_analyst owns account 3, NOT account 2
        mockMvc.perform(get("/api/v1/orders/account/2"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED_ACCESS"));
    }
    
    /**
     * Test 4: User accessing their own orders (account 2) - should succeed
     */
    @Test
    @WithMockUser(username = "david_investor")
    public void testGetOwnOrders_Account2_Success() throws Exception {
        // david_investor owns account 2
        mockMvc.perform(get("/api/v1/orders/account/2"))
            .andExpect(status().isOk());
    }
    
    /**
     * Test 5: User accessing account 1's orders (not theirs) - should fail
     */
    @Test
    @WithMockUser(username = "david_investor")
    public void testGetOtherUsersOrders_Account1_Forbidden() throws Exception {
        // david_investor owns account 2, NOT account 1
        mockMvc.perform(get("/api/v1/orders/account/1"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED_ACCESS"));
    }
}
