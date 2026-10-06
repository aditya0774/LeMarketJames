package com.lemarketjames.orders;

import com.lemarketjames.orders.service.AccountAccess;
import com.lemarketjames.orders.service.CashValidationService;
import com.lemarketjames.orders.service.OrderService;
import com.lemarketjames.orders.stream.OrderStatusStreamService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OrderStatusStreamApiTest {

    @MockBean
    private OrderService orderService;

    @MockBean
    private CashValidationService cashValidationService;

    @MockBean
    private AccountAccess accountAccess;

    @MockBean
    private OrderStatusStreamService orderStatusStreamService;

    @MockBean
    private com.lemarketjames.orders.execution.OrderExecutionService execution;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void clientCanOpenOrderStatusStream() throws Exception {
        when(accountAccess.callerAccountId()).thenReturn(7);
        when(orderStatusStreamService.subscribe(7)).thenReturn(new SseEmitter());

        mockMvc.perform(get("/api/v1/orders/stream").with(user("alice").roles("CLIENT")))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));

        verify(accountAccess).callerAccountId();
        verify(orderStatusStreamService).subscribe(7);
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/orders/stream"))
            .andExpect(status().isUnauthorized());

        verify(accountAccess, never()).callerAccountId();
        verify(orderStatusStreamService, never()).subscribe(org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void nonClientRoleIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/orders/stream").with(user("ops").roles("TRADING_OPS")))
            .andExpect(status().isForbidden());

        verify(accountAccess, never()).callerAccountId();
        verify(orderStatusStreamService, never()).subscribe(org.mockito.ArgumentMatchers.anyInt());
    }
}
