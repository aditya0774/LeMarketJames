package com.lemarketjames.orders;

import com.lemarketjames.orders.dto.CreateOrderRequest;
import com.lemarketjames.orders.entity.Order;
import com.lemarketjames.orders.service.CashValidationService;
import com.lemarketjames.orders.service.OrderService;
import com.lemarketjames.orders.dto.OrderResponse;
import com.lemarketjames.orders.exception.NotTradableException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for OrderController
 * Tests order creation, retrieval, and status management
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Order Controller Unit Tests")
public class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

    @MockBean
    private CashValidationService cashValidationService;

    @Autowired
    private ObjectMapper objectMapper;

    // ========== Create Order Tests ==========

    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("createOrder returns 201 with sufficient cash")
    void testCreateOrderSufficientCash() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest(1, 1, Order.OrderType.BUY, new BigDecimal("10.0000"));
        request.setPricePerUnit(new BigDecimal("100.00"));

        Order order = new Order(1, 1, Order.OrderType.BUY, new BigDecimal("10"));
        order.setOrderId(1);
        OrderResponse response = new OrderResponse(order);

        when(orderService.createOrder(any(CreateOrderRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.orderId").value(1));
    }

    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("createOrder returns 400 with insufficient cash")
    void testCreateOrderInsufficientCash() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest(1, 1, Order.OrderType.BUY, new BigDecimal("100.0000"));
        request.setPricePerUnit(new BigDecimal("1000.00"));

        OrderResponse failureResponse = new OrderResponse(false, "Insufficient balance. Required: $100000.00, Available: $5000.00");

        when(orderService.createOrder(any(CreateOrderRequest.class))).thenReturn(failureResponse);

        mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("createOrder returns 201 for SELL orders")
    void testCreateOrderSellOrder() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest(1, 1, Order.OrderType.SELL, new BigDecimal("5.0000"));
        request.setPricePerUnit(new BigDecimal("100.00"));

        Order order = new Order(1, 1, Order.OrderType.SELL, new BigDecimal("5"));
        order.setOrderId(2);
        OrderResponse response = new OrderResponse(order);

        when(orderService.createOrder(any(CreateOrderRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("createOrder returns 201 for exact balance")
    void testCreateOrderExactBalance() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest(1, 1, Order.OrderType.BUY, new BigDecimal("50.0000"));
        request.setPricePerUnit(new BigDecimal("100.00"));

        Order order = new Order(1, 1, Order.OrderType.BUY, new BigDecimal("50"));
        order.setOrderId(3);
        OrderResponse response = new OrderResponse(order);

        when(orderService.createOrder(any(CreateOrderRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("createOrder returns 400 for non-tradable instrument")
    void testCreateOrderNonTradableInstrument() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest(1, 1, Order.OrderType.BUY, new BigDecimal("10"));

        when(orderService.createOrder(any(CreateOrderRequest.class)))
            .thenThrow(new NotTradableException("Instrument is currently not tradable"));

        mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("createOrder returns 400 for invalid request")
    void testCreateOrderInvalidRequest() throws Exception {
        String invalidRequestJson = "{\n  \"accountId\": 0,\n  \"instrumentId\": null,\n  \"quantity\": -1\n}";

        mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidRequestJson))
            .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("createOrder returns 403 for account access denied")
    void testCreateOrderAccountAccessDenied() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest(99, 1, Order.OrderType.BUY, new BigDecimal("10"));

        when(orderService.createOrder(any(CreateOrderRequest.class)))
            .thenThrow(new AccessDeniedException("Account access is not allowed"));

        mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false));
    }

    // ========== Get Order by ID Tests ==========

    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("getOrderById returns order successfully")
    void testGetOrderById() throws Exception {
        Order order = new Order(1, 1, Order.OrderType.BUY, new BigDecimal("10"));
        order.setOrderId(42);
        OrderResponse response = new OrderResponse(order);

        when(orderService.getOrderById(42)).thenReturn(response);

        mockMvc.perform(get("/api/v1/orders/42"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.orderId").value(42))
            .andExpect(jsonPath("$.accountId").value(1));
    }

    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("getOrderById returns order with all details")
    void testGetOrderByIdAllDetails() throws Exception {
        Order order = new Order(1, 2, Order.OrderType.SELL, new BigDecimal("15"));
        order.setOrderId(100);
        order.setPricePerUnit(new BigDecimal("50.25"));
        order.setOrderStatus(Order.OrderStatus.PENDING);
        OrderResponse response = new OrderResponse(order);

        when(orderService.getOrderById(100)).thenReturn(response);

        mockMvc.perform(get("/api/v1/orders/100"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.orderId").value(100))
            .andExpect(jsonPath("$.instrumentId").value(2))
            .andExpect(jsonPath("$.quantity").exists());
    }

    // ========== Get Orders by Account Tests ==========

    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("getOrdersByAccountId returns empty list")
    void testGetOrdersByAccountIdEmpty() throws Exception {
        when(orderService.getOrdersByAccountId(1)).thenReturn(new ArrayList<>());

        mockMvc.perform(get("/api/v1/orders/account/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("getOrdersByAccountId returns multiple orders")
    void testGetOrdersByAccountIdMultiple() throws Exception {
        Order order1 = new Order(1, 1, Order.OrderType.BUY, new BigDecimal("10"));
        order1.setOrderId(1);
        Order order2 = new Order(1, 2, Order.OrderType.SELL, new BigDecimal("5"));
        order2.setOrderId(2);

        List<OrderResponse> responses = List.of(new OrderResponse(order1), new OrderResponse(order2));
        when(orderService.getOrdersByAccountId(1)).thenReturn(responses);

        mockMvc.perform(get("/api/v1/orders/account/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(2));
    }

    // ========== Get Orders by Account and Status Tests ==========

    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("getOrdersByAccountAndStatus returns orders with status")
    void testGetOrdersByAccountAndStatus() throws Exception {
        Order order = new Order(1, 1, Order.OrderType.BUY, new BigDecimal("10"));
        order.setOrderId(1);
        order.setOrderStatus(Order.OrderStatus.PENDING);

        List<OrderResponse> responses = List.of(new OrderResponse(order));
        when(orderService.getOrdersByAccountAndStatus(1, Order.OrderStatus.PENDING)).thenReturn(responses);

        mockMvc.perform(get("/api/v1/orders/account/1/status/PENDING"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("getOrdersByAccountAndStatus returns empty for no matching status")
    void testGetOrdersByAccountAndStatusEmpty() throws Exception {
        when(orderService.getOrdersByAccountAndStatus(1, Order.OrderStatus.FILLED))
            .thenReturn(new ArrayList<>());

        mockMvc.perform(get("/api/v1/orders/account/1/status/FILLED"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
    }

    // ========== Get Orders by Instrument Tests ==========

    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("getOrdersByInstrumentId returns orders for instrument")
    void testGetOrdersByInstrumentId() throws Exception {
        Order order1 = new Order(1, 1, Order.OrderType.BUY, new BigDecimal("10"));
        order1.setOrderId(1);
        Order order2 = new Order(2, 1, Order.OrderType.SELL, new BigDecimal("5"));
        order2.setOrderId(2);

        List<OrderResponse> responses = List.of(new OrderResponse(order1), new OrderResponse(order2));
        when(orderService.getOrdersByInstrumentId(1)).thenReturn(responses);

        mockMvc.perform(get("/api/v1/orders/instrument/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("getOrdersByInstrumentId returns empty for no orders")
    void testGetOrdersByInstrumentIdEmpty() throws Exception {
        when(orderService.getOrdersByInstrumentId(999)).thenReturn(new ArrayList<>());

        mockMvc.perform(get("/api/v1/orders/instrument/999"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
    }

    // ========== Update Order Status Tests ==========

    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("updateOrderStatus updates status successfully")
    void testUpdateOrderStatus() throws Exception {
        Order order = new Order(1, 1, Order.OrderType.BUY, new BigDecimal("10"));
        order.setOrderId(1);
        order.setOrderStatus(Order.OrderStatus.FILLED);
        OrderResponse response = new OrderResponse(order);

        when(orderService.updateOrderStatus(1, Order.OrderStatus.FILLED)).thenReturn(response);

        mockMvc.perform(put("/api/v1/orders/1/status/FILLED"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("updateOrderStatus updates from PENDING to FILLED")
    void testUpdateOrderStatusPendingToFilled() throws Exception {
        Order order = new Order(1, 1, Order.OrderType.BUY, new BigDecimal("10"));
        order.setOrderId(50);
        order.setOrderStatus(Order.OrderStatus.FILLED);
        OrderResponse response = new OrderResponse(order);

        when(orderService.updateOrderStatus(50, Order.OrderStatus.FILLED)).thenReturn(response);

        mockMvc.perform(put("/api/v1/orders/50/status/FILLED"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.orderId").value(50));
    }

    // ========== Reject Order Tests ==========

    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("rejectOrder rejects with reason")
    void testRejectOrderWithReason() throws Exception {
        Order order = new Order(1, 1, Order.OrderType.BUY, new BigDecimal("10"));
        order.setOrderId(1);
        order.setOrderStatus(Order.OrderStatus.REJECTED);
        OrderResponse response = new OrderResponse(order);

        when(orderService.rejectOrder(1, "Insufficient inventory")).thenReturn(response);

        mockMvc.perform(post("/api/v1/orders/1/reject?reason=Insufficient+inventory"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("rejectOrder rejects with default reason")
    void testRejectOrderDefaultReason() throws Exception {
        Order order = new Order(1, 1, Order.OrderType.BUY, new BigDecimal("10"));
        order.setOrderId(1);
        order.setOrderStatus(Order.OrderStatus.REJECTED);
        OrderResponse response = new OrderResponse(order);

        when(orderService.rejectOrder(1, "No reason provided")).thenReturn(response);

        mockMvc.perform(post("/api/v1/orders/1/reject"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("rejectOrder with custom rejection reason")
    void testRejectOrderCustomReason() throws Exception {
        Order order = new Order(1, 1, Order.OrderType.BUY, new BigDecimal("10"));
        order.setOrderId(75);
        order.setOrderStatus(Order.OrderStatus.REJECTED);
        OrderResponse response = new OrderResponse(order);

        String reason = "Market hours outside trading session";
        when(orderService.rejectOrder(75, reason)).thenReturn(response);

        mockMvc.perform(post("/api/v1/orders/75/reject?reason=" + reason.replace(" ", "+")))
            .andExpect(status().isOk());
    }
}
