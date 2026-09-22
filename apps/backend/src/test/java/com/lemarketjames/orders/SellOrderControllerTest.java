package com.lemarketjames.orders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lemarketjames.holdings.exception.InsufficientHoldingsException;
import com.lemarketjames.orders.dto.OrderResponse;
import com.lemarketjames.orders.dto.SubmitSellOrderRequest;
import com.lemarketjames.orders.entity.Order;
import com.lemarketjames.orders.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SellOrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderService orderService;

    @Test
    @WithMockUser(username = "testuser")
    void submitSellOrderReturnsCreatedForValidRequest() throws Exception {
        SubmitSellOrderRequest request = new SubmitSellOrderRequest(
            1,
            2,
            new BigDecimal("3.0000")
        );

        Order created = new Order(1, 2, Order.OrderType.SELL, new BigDecimal("3.0000"));
        created.setOrderId(321);
        OrderResponse response = new OrderResponse(created);

        when(orderService.submitSellOrder(any(SubmitSellOrderRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/sell-orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.orderId").value(321))
            .andExpect(jsonPath("$.orderType").value("SELL"));
    }

    @Test
    @WithMockUser(username = "testuser")
    void submitSellOrderReturnsValidationErrorsForInvalidRequest() throws Exception {
        String invalidJson = """
            {
              "accountId": 0,
              "instrumentId": null,
              "quantity": -1
            }
            """;

        mockMvc.perform(post("/api/v1/sell-orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.accountId").exists())
            .andExpect(jsonPath("$.errors.instrumentId").exists())
            .andExpect(jsonPath("$.errors.quantity").exists());
    }

    @Test
    @WithMockUser(username = "testuser")
    void submitSellOrderReturnsBadRequestForInsufficientHoldings() throws Exception {
        SubmitSellOrderRequest request = new SubmitSellOrderRequest(
            1,
            2,
            new BigDecimal("100.0000")
        );

        when(orderService.submitSellOrder(any(SubmitSellOrderRequest.class)))
            .thenThrow(new InsufficientHoldingsException("Insufficient holdings. Available: 5, Requested: 100"));

        mockMvc.perform(post("/api/v1/sell-orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("INSUFFICIENT_HOLDINGS"));
    }

    @Test
    @WithMockUser(username = "testuser")
    void submitSellOrderReturnsForbiddenForAccountAccessDenied() throws Exception {
        SubmitSellOrderRequest request = new SubmitSellOrderRequest(
            99,
            2,
            new BigDecimal("1.0000")
        );

        when(orderService.submitSellOrder(any(SubmitSellOrderRequest.class)))
            .thenThrow(new AccessDeniedException("Account access is not allowed"));

        mockMvc.perform(post("/api/v1/sell-orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("ACCOUNT_ACCESS_DENIED"));
    }
}