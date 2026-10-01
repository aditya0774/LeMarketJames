package com.lemarketjames.orders;

import com.lemarketjames.common.domain.AccountRepository;
import com.lemarketjames.orders.entity.Order;
import com.lemarketjames.orders.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Exercises HTTP binding, validation, service scoping and the real database range query. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(username = "history-client")
class OrderHistoryIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired OrderRepository orders;
    @MockBean AccountRepository accounts;
    private static final int ACCOUNT = 91001;
    private static final String URL = "/api/v1/orders/account/" + ACCOUNT;

    @BeforeEach
    void ownsOnlyOneAccount() {
        when(accounts.existsByAccountIdAndUsername(ACCOUNT, "history-client")).thenReturn(true);
    }

    private Order save(int account, LocalDateTime submitted, Order.OrderStatus status) {
        Order order = new Order(account, 1, Order.OrderType.BUY, BigDecimal.ONE);
        order.setSubmittedAt(submitted);
        order.setOrderStatus(status);
        // A different fill date proves history uses placement time.
        order.setFilledAt(submitted.plusYears(1));
        return orders.saveAndFlush(order);
    }

    @ParameterizedTest
    @CsvSource({
        "2026-03-08,America/New_York,2026-03-08T05:00,2026-03-09T04:00",
        "2026-11-01,America/New_York,2026-11-01T04:00,2026-11-02T05:00",
        "2024-02,UTC,2024-02-01T00:00,2024-03-01T00:00",
        "2026,Asia/Kolkata,2025-12-31T18:30,2026-12-31T18:30"
    })
    void returnsOnlyOwnOrdersWithinLocalPeriod(String date, String zone, String from, String to) throws Exception {
        var start = LocalDateTime.parse(from);
        var end = LocalDateTime.parse(to);
        save(ACCOUNT, start.minusSeconds(1), Order.OrderStatus.SUBMITTED);
        var first = save(ACCOUNT, start, Order.OrderStatus.SUBMITTED);
        var last = save(ACCOUNT, end.minusSeconds(1), Order.OrderStatus.FILLED);
        save(ACCOUNT, end, Order.OrderStatus.REJECTED);
        save(ACCOUNT + 1, start, Order.OrderStatus.SUBMITTED);
        mvc.perform(get(URL).param("date", date).param("timeZone", zone))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].orderId", org.hamcrest.Matchers.containsInAnyOrder(
                first.getOrderId(), last.getOrderId())));
        mvc.perform(get(URL + "/status/FILLED").param("date", date).param("timeZone", zone))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].orderId").value(last.getOrderId()));
        mvc.perform(get(URL)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(4));
    }

    @Test
    void emptyPeriodAndClearedStatusFilter() throws Exception {
        save(ACCOUNT, LocalDateTime.parse("2025-01-01T00:00"), Order.OrderStatus.FILLED);
        mvc.perform(get(URL).param("date", "2026").param("timeZone", "UTC"))
            .andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get(URL + "/status/FILLED"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
    }

    @ParameterizedTest
    @CsvSource({"2026-02-30,UTC", "2026,Bad/Zone", "2026,''", "'',UTC"})
    void invalidFiltersReturnBadRequest(String date, String zone) throws Exception {
        mvc.perform(get(URL).param("date", date).param("timeZone", zone))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void incompleteFilterAndForeignAccount() throws Exception {
        mvc.perform(get(URL).param("date", "2026")).andExpect(status().isBadRequest());
        mvc.perform(get(URL).param("timeZone", "UTC")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/orders/account/91002").param("date", "2026").param("timeZone", "UTC"))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCOUNT_ACCESS_DENIED"));
    }
}
