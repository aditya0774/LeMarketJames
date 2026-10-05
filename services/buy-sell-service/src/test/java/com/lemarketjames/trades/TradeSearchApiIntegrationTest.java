package com.lemarketjames.trades;

import com.lemarketjames.common.domain.*;
import com.lemarketjames.common.instruments.InstrumentRepository;
import com.lemarketjames.orders.entity.Order;
import com.lemarketjames.orders.repository.OrderRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Exercises security, validation and the real database query for LMKT-39 AC1-AC3. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:tradeSearch;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Transactional
class TradeSearchApiIntegrationTest {
    private static final String PATH = "/api/v1/orders/trades/search";
    @Autowired MockMvc mvc;
    @Autowired OrderRepository orders;
    @Autowired AccountRepository accounts;
    @Autowired ClientRepository clients;
    @Autowired InstrumentRepository instruments;
    Integer accountId;
    Integer clientId;
    Integer instrumentId;
    Integer tradeId;
    String username;

    @BeforeEach
    void seed() {
        instrumentId = instruments.findByTicker("AAPL").orElseThrow().getInstrumentId();
        accountId = createAccount();
        clientId = accounts.findById(accountId).orElseThrow().getClientId();
        tradeId = order(accountId, "2026-01-10T12:00:00", Order.OrderStatus.FILLED);
    }

    private Integer createAccount() {
        username = "search" + UUID.randomUUID().toString().substring(0, 12);
        ClientEntity client = new ClientEntity();
        client.setUsername(username);
        client.setPassword("test-only");
        client.setEmail(username + "@example.com");
        client.setFullName("Search Test");
        client.setDateOfBirth(LocalDate.of(1990, 1, 1));
        client.setPhone("5551234567");
        client.setRegisteredDate(LocalDateTime.now());
        client.setSsn("test-only");
        client.setEmploymentStatus("EMPLOYED");
        client.setInvestmentExperience("beginner");
        client.setAccountStatus(AccountStatus.ACTIVE);
        client = clients.saveAndFlush(client);
        AccountEntity account = new AccountEntity();
        account.setClientId(client.getClientId());
        account.setCashBalance(new BigDecimal("1000"));
        account.setCurrency("USD");
        account.setTradingEnabled(true);
        account.setOpenedDate(LocalDate.of(2025, 1, 1));
        return accounts.saveAndFlush(account).getAccountId();
    }

    private Integer order(Integer account, String filledAt, Order.OrderStatus status) {
        Order order = new Order(account, instrumentId, Order.OrderType.BUY, new BigDecimal("2"));
        order.setOrderStatus(status);
        order.setPricePerUnit(new BigDecimal("123.4567"));
        order.setSubmittedAt(LocalDateTime.parse("2025-12-01T00:00:00"));
        order.setFilledAt(filledAt == null ? null : LocalDateTime.parse(filledAt));
        return orders.saveAndFlush(order).getOrderId();
    }

    @Test
    void operationsFindsTradeByOrderId() throws Exception {
        mvc.perform(get(PATH).param("orderId", tradeId.toString()).with(user("ops").roles("TRADING_OPS")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].orderId").value(tradeId))
            .andExpect(jsonPath("$[0].clientId").value(clientId))
            .andExpect(jsonPath("$[0].accountId").value(accountId))
            .andExpect(jsonPath("$[0].instrumentId").value(instrumentId))
            .andExpect(jsonPath("$[0].symbol").value("AAPL"))
            .andExpect(jsonPath("$[0].side").value("BUY"))
            .andExpect(jsonPath("$[0].quantity").value(2))
            .andExpect(jsonPath("$[0].pricePerUnit").value(123.4567))
            .andExpect(jsonPath("$[0].filledAt").value("2026-01-10T12:00:00"));
    }

    @Test
    void clientSearchUsesFillDateAndInclusiveUtcDays() throws Exception {
        int start = order(accountId, "2026-01-10T00:00:00", Order.OrderStatus.FILLED);
        int last = order(accountId, "2026-01-11T23:59:59.999999", Order.OrderStatus.FILLED);
        int tie = order(accountId, "2026-01-11T23:59:59.999999", Order.OrderStatus.FILLED);
        order(accountId, "2026-01-09T23:59:59", Order.OrderStatus.FILLED);
        order(accountId, "2026-01-12T00:00:00", Order.OrderStatus.FILLED);
        order(accountId, "2026-01-10T12:00:00", Order.OrderStatus.REJECTED);
        order(accountId, null, Order.OrderStatus.SUBMITTED);
        order(createAccount(), "2026-01-10T12:00:00", Order.OrderStatus.FILLED);
        mvc.perform(get(PATH).param("clientId", clientId.toString()).param("from", "2026-01-10")
                .param("to", "2026-01-11").with(user("ops").roles("TRADING_OPS")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(4))
            .andExpect(jsonPath("$[0].orderId").value(tie))
            .andExpect(jsonPath("$[1].orderId").value(last))
            .andExpect(jsonPath("$[2].orderId").value(tradeId))
            .andExpect(jsonPath("$[3].orderId").value(start));
    }

    @Test
    void unknownAndUnfilledOrdersAndEmptyRangesReturnEmptyLists() throws Exception {
        int unfilled = order(accountId, null, Order.OrderStatus.ACCEPTED);
        for (int id : new int[] {Integer.MAX_VALUE, unfilled}) {
            mvc.perform(get(PATH).param("orderId", String.valueOf(id)).with(user("ops").roles("TRADING_OPS")))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        }
        mvc.perform(get(PATH).param("clientId", clientId.toString()).param("from", "2027-01-01")
                .param("to", "2027-01-01").with(user("ops").roles("TRADING_OPS")))
            .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"CLIENT", "ANALYST", "COMPLIANCE"})
    void otherRolesDeniedEvenForOwnTrade(String role) throws Exception {
        mvc.perform(get(PATH).param("orderId", tradeId.toString()).with(user(username).roles(role)))
            .andExpect(status().isForbidden());
        mvc.perform(get(PATH).param("clientId", clientId.toString()).param("from", "2026-01-01")
                .param("to", "2026-01-31").with(user(username).roles(role)))
            .andExpect(status().isForbidden());
    }

    @Test
    void anonymousDenied() throws Exception {
        mvc.perform(get(PATH).param("orderId", tradeId.toString())).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "?orderId=", "?orderId=abc", "?orderId=2147483648", "?orderId=0",
        "?orderId=1&clientId=2", "?clientId=1", "?clientId=1&from=2026-02-30&to=2026-03-01",
        "?clientId=1&from=2026-02-02&to=2026-02-01", "?clientId=1&from=&to=2026-02-01"})
    void badInputReturnsContractError(String query) throws Exception {
        mvc.perform(get(PATH + query).with(user("ops").roles("TRADING_OPS")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").isNotEmpty());
    }
}
