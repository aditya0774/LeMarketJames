package com.lemarketjames.orders;

import com.lemarketjames.orders.dto.CreateOrderRequest;
import com.lemarketjames.orders.dto.OrderResponse;
import com.lemarketjames.orders.entity.Order;
import com.lemarketjames.orders.exception.NotTradableException;
import com.lemarketjames.orders.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderService orderService;

    @Test
    @WithMockUser(username = "testuser")
    void createOrderReturnsCreatedForTradableInstrument() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest(1, 1, Order.OrderType.BUY, new BigDecimal("10"));
        request.setPricePerUnit(new BigDecimal("200.50"));

        Order order = new Order(1, 1, Order.OrderType.BUY, new BigDecimal("10"));
        order.setOrderId(42);
        order.setPricePerUnit(new BigDecimal("200.50"));
        OrderResponse response = new OrderResponse(order);

        when(orderService.createOrder(any(CreateOrderRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.orderId").value(42))
            .andExpect(jsonPath("$.accountId").value(1))
            .andExpect(jsonPath("$.instrumentId").value(1));
    }

    @Test
    @WithMockUser(username = "testuser")
    void createOrderReturnsBadRequestForNonTradableInstrument() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest(1, 1, Order.OrderType.BUY, new BigDecimal("10"));

        when(orderService.createOrder(any(CreateOrderRequest.class)))
            .thenThrow(new NotTradableException("Instrument is currently not tradable"));

        mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("Instrument is currently not tradable"))
            .andExpect(jsonPath("$.code").value("NOT_TRADABLE"));
    }

    @Test
    @WithMockUser(username = "testuser")
    void createOrderReturnsValidationErrorsForInvalidRequest() throws Exception {
        String invalidRequestJson = """
            {
              "accountId": 0,
              "instrumentId": null,
              "quantity": -1
            }
            """;

        mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidRequestJson))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.accountId").exists())
            .andExpect(jsonPath("$.errors.instrumentId").exists())
            .andExpect(jsonPath("$.errors.orderType").exists())
            .andExpect(jsonPath("$.errors.quantity").exists());
    }
}
