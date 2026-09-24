package com.lemarketjames.orders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lemarketjames.orders.dto.OrderResponse;
import com.lemarketjames.orders.dto.SubmitBuyOrderRequest;
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

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BuyOrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderService orderService;

    @Test
    @WithMockUser(username = "testuser")
    void submitBuyOrderReturnsCreatedForValidRequest() throws Exception {
        SubmitBuyOrderRequest request = new SubmitBuyOrderRequest(
            1,
            1,
            new BigDecimal("10.0000"),
            new BigDecimal("100.00")
        );

        Order created = new Order(1, 1, Order.OrderType.BUY, new BigDecimal("10.0000"));
        created.setOrderId(123);
        created.setPricePerUnit(new BigDecimal("100.00"));
        OrderResponse response = new OrderResponse(created);

        when(orderService.submitBuyOrder(any(SubmitBuyOrderRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/buy-orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.orderId").value(123))
            .andExpect(jsonPath("$.orderType").value("BUY"));
    }

    @Test
    @WithMockUser(username = "testuser")
    void submitBuyOrderReturnsBadRequestForBusinessFailure() throws Exception {
        SubmitBuyOrderRequest request = new SubmitBuyOrderRequest(
            1,
            1,
            new BigDecimal("100.0000"),
            new BigDecimal("1000.00")
        );

        OrderResponse failureResponse = new OrderResponse(
            false,
            "Insufficient balance. Required: $100000.00, Available: $5000.00"
        );

        when(orderService.submitBuyOrder(any(SubmitBuyOrderRequest.class))).thenReturn(failureResponse);

        mockMvc.perform(post("/api/v1/buy-orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.reason").exists());
    }

    @Test
    @WithMockUser(username = "testuser")
    void submitBuyOrderReturnsValidationErrorsForInvalidRequest() throws Exception {
        String invalidJson = """
            {
              "accountId": 0,
              "instrumentId": null,
              "quantity": -1,
              "pricePerUnit": 0
            }
            """;

        mockMvc.perform(post("/api/v1/buy-orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.accountId").exists())
            .andExpect(jsonPath("$.errors.instrumentId").exists())
            .andExpect(jsonPath("$.errors.quantity").exists())
            .andExpect(jsonPath("$.errors.pricePerUnit").exists());
    }
}