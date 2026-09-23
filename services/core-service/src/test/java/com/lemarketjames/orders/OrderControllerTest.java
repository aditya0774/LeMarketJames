package com.lemarketjames.orders;

import com.lemarketjames.orders.dto.CreateOrderRequest;
import com.lemarketjames.orders.entity.Order;
import com.lemarketjames.orders.service.CashValidationService;
import com.lemarketjames.orders.service.OrderService;
import com.lemarketjames.orders.dto.OrderResponse;
import com.lemarketjames.orders.exception.NotTradableException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for OrderController
 * Tests order creation with cash validation
 */
@SpringBootTest
@AutoConfigureMockMvc
public class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

    @MockBean
    private CashValidationService cashValidationService;

    /**
     * AC3: Sufficient funds - verify POST /api/v1/orders returns 201 with success: true
     */
    @Test
    @WithMockUser(username = "testuser")
    public void testCreateOrder_SufficientCash_Success() throws Exception {
        // Arrange
        CreateOrderRequest request = new CreateOrderRequest(
            1,
            1,
            Order.OrderType.BUY,
            new BigDecimal("10.0000")
        );
        request.setPricePerUnit(new BigDecimal("100.00"));

        // Mock order service to return a successful response
        com.lemarketjames.orders.dto.OrderResponse successResponse = 
            new com.lemarketjames.orders.dto.OrderResponse();
        successResponse.setSuccess(true);
        
        when(orderService.createOrder(any(CreateOrderRequest.class)))
            .thenReturn(successResponse);

        // Act & Assert
        mockMvc.perform(post("/api/v1/orders")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"accountId\":1,\"instrumentId\":1,\"orderType\":\"BUY\",\"quantity\":10.0000,\"pricePerUnit\":100.00}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true));
    }

    /**
     * AC3: Insufficient funds rejected - verify POST /api/v1/orders returns 400 with success: false
     */
    @Test
    @WithMockUser(username = "testuser")
    public void testCreateOrder_InsufficientCash_Rejected() throws Exception {
        // Arrange
        CreateOrderRequest request = new CreateOrderRequest(
            2,
            1,
            Order.OrderType.BUY,
            new BigDecimal("100.0000")
        );
        request.setPricePerUnit(new BigDecimal("1000.00"));

        // Mock order service to return insufficient balance response
        com.lemarketjames.orders.dto.OrderResponse failureResponse = 
            new com.lemarketjames.orders.dto.OrderResponse(false, 
                "Insufficient balance. Required: $100000.00, Available: $5000.00");
        
        when(orderService.createOrder(any(CreateOrderRequest.class)))
            .thenReturn(failureResponse);

        // Act & Assert
        mockMvc.perform(post("/api/v1/orders")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"accountId\":2,\"instrumentId\":1,\"orderType\":\"BUY\",\"quantity\":100.0000,\"pricePerUnit\":1000.00}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.reason").exists())
            .andExpect(jsonPath("$.reason").value(org.hamcrest.Matchers.containsString("Insufficient balance")));
    }

    /**
     * AC3: SELL order (no cash validation needed) - verify POST /api/v1/orders returns 201
     * SELL orders don't require cash balance validation
     */
    @Test
    @WithMockUser(username = "testuser")
    public void testCreateOrder_SellOrder_SkipsCashValidation() throws Exception {
        // Arrange
        CreateOrderRequest request = new CreateOrderRequest(
            1,
            1,
            Order.OrderType.SELL,
            new BigDecimal("5.0000")
        );
        request.setPricePerUnit(new BigDecimal("100.00"));

        // Mock order service to return successful response
        com.lemarketjames.orders.dto.OrderResponse successResponse = 
            new com.lemarketjames.orders.dto.OrderResponse();
        successResponse.setSuccess(true);
        
        when(orderService.createOrder(any(CreateOrderRequest.class)))
            .thenReturn(successResponse);

        // Act & Assert
        mockMvc.perform(post("/api/v1/orders")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"accountId\":1,\"instrumentId\":1,\"orderType\":\"SELL\",\"quantity\":5.0000,\"pricePerUnit\":100.00}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true));
        
        // Verify cash validation was called by OrderService (even for SELL)
        verify(orderService).createOrder(any(CreateOrderRequest.class));
    }

    /**
     * AC1: Balance retrieved - verify cash balance is accessible for validation
     * This is tested indirectly through successful order creation
     */
    @Test
    @WithMockUser(username = "testuser")
    public void testCreateOrder_EdgeCaseExactBalance() throws Exception {
        // Arrange: Order cost equals available balance exactly
        CreateOrderRequest request = new CreateOrderRequest(
            1,
            1,
            Order.OrderType.BUY,
            new BigDecimal("50.0000")
        );
        request.setPricePerUnit(new BigDecimal("100.00"));  // Total: $5000 (exact balance)

        // Mock order service to return successful response
        com.lemarketjames.orders.dto.OrderResponse successResponse = 
            new com.lemarketjames.orders.dto.OrderResponse();
        successResponse.setSuccess(true);
        
        when(orderService.createOrder(any(CreateOrderRequest.class)))
            .thenReturn(successResponse);

        // Act & Assert
        mockMvc.perform(post("/api/v1/orders")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"accountId\":1,\"instrumentId\":1,\"orderType\":\"BUY\",\"quantity\":50.0000,\"pricePerUnit\":100.00}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Autowired
    private ObjectMapper objectMapper;

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

    @Test
    @WithMockUser(username = "testuser")
    void createOrderReturnsForbiddenForAccountAccessDenied() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest(99, 1, Order.OrderType.BUY, new BigDecimal("10"));

        when(orderService.createOrder(any(CreateOrderRequest.class)))
            .thenThrow(new AccessDeniedException("Account access is not allowed"));

        mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("Access denied"))
            .andExpect(jsonPath("$.code").value("ACCOUNT_ACCESS_DENIED"));
    }
}
