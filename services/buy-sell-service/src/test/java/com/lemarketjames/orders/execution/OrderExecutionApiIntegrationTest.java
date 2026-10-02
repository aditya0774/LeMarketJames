package com.lemarketjames.orders.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lemarketjames.common.audit.AuditEventEntity;
import com.lemarketjames.common.audit.AuditEventRepository;
import com.lemarketjames.common.audit.AuditEventType;
import com.lemarketjames.common.domain.*;
import com.lemarketjames.common.instruments.InstrumentRepository;
import com.lemarketjames.holdings.client.HoldingsSettlementClient;
import com.lemarketjames.market.model.QuoteSnapshot;
import com.lemarketjames.market.service.MarketDataService;
import com.lemarketjames.orders.entity.Order;
import com.lemarketjames.orders.events.OrderFilled;
import com.lemarketjames.orders.repository.OrderRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * LMKT-23 through the API: an ACCEPTED order is executed with the real executor, coordinator,
 * transitions and audit trail. Only the two remote services (quote feed, settlement) are mocked.
 * Market hours are switched off so the outcome doesn't depend on when the build runs.
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:executionApi;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "lmj.execution.respect-market-hours=false"})
@AutoConfigureMockMvc
@RecordApplicationEvents
@WithMockUser(username = "ops", roles = "TRADING_OPS")
class OrderExecutionApiIntegrationTest {
    /** Price the BUY was given at placement; a rejected execution must leave it untouched. */
    static final BigDecimal PLACEMENT_PRICE = new BigDecimal("100.0000");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired OrderRepository orders;
    @Autowired AccountRepository accounts;
    @Autowired ClientRepository clients;
    @Autowired InstrumentRepository instruments;
    @Autowired AuditEventRepository audit;
    @Autowired ApplicationEvents events;
    @MockBean MarketDataService market;
    @MockBean HoldingsSettlementClient settlement;
    Integer instrumentId;
    Integer accountId;

    @BeforeEach
    void seedAccount() {
        instrumentId = instruments.findByTicker("AAPL").orElseThrow().getInstrumentId();
        // The executor rechecks the client and account, so both must be real and allowed to trade.
        String username = "exec" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        ClientEntity client = new ClientEntity();
        client.setUsername(username);
        client.setPassword("not-used-by-buy-sell-service");
        client.setEmail(username + "@example.com");
        client.setFullName("Execution Test");
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
        account.setCashBalance(new BigDecimal("10000"));
        account.setCurrency("USD");
        account.setTradingEnabled(true);
        account.setOpenedDate(LocalDate.now());
        accountId = accounts.saveAndFlush(account).getAccountId();
    }

    /** An order that placement and acceptance have already handled, as in the seed data (C3). */
    private int acceptedBuy() {
        Order order = new Order(accountId, instrumentId, Order.OrderType.BUY, new BigDecimal("2"));
        order.setPricePerUnit(PLACEMENT_PRICE);
        order.setOrderStatus(Order.OrderStatus.ACCEPTED);
        return orders.saveAndFlush(order).getOrderId();
    }

    private void quoted(Instant quoteTime) {
        when(market.findByInstrumentId(instrumentId)).thenReturn(Optional.of(
            new QuoteSnapshot(null, 101.00, 100.76543, 101.23456, 100, 102, 99, 100, 0, quoteTime, null)));
    }

    private JsonNode fill(int orderId) throws Exception {
        String body = mvc.perform(put("/api/v1/orders/" + orderId + "/status/FILLED"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    private List<AuditEventEntity> auditOf(int orderId, AuditEventType type) {
        return audit.findByOrderIdOrderByOccurredAtAsc(orderId).stream()
            .filter(event -> event.getEventType() == type).toList();
    }

    // AC1 + AC3 + AC4: a fresh quote fills the order at that quote, and the quote is kept with the fill.
    @Test
    void freshQuoteFillsAtThatQuoteAndStoresIt() throws Exception {
        // Millisecond precision, so the value survives the database and JSON unchanged.
        Instant quoteTime = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        quoted(quoteTime);
        int orderId = acceptedBuy();

        JsonNode filled = fill(orderId);

        assertEquals("FILLED", filled.get("orderStatus").asText());
        // A BUY fills at the ask, rounded to four decimals; the placement price is replaced.
        assertEquals(0, new BigDecimal("101.2346").compareTo(filled.get("pricePerUnit").decimalValue()));
        assertEquals(MarketOrderExecutor.QUOTE_SOURCE, filled.get("quoteSource").asText());
        assertEquals(quoteTime, Instant.parse(filled.get("quoteTime").asText()));
        assertFalse(filled.get("filledAt").isNull(), "execution time is stored with the fill");
        assertTrue(filled.get("rejectionReason").isNull());

        // The same values are what a later read returns, i.e. they were committed.
        JsonNode reread = json.readTree(mvc.perform(get("/api/v1/orders/" + orderId))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        for (String field : List.of("orderStatus", "pricePerUnit", "quoteSource", "quoteTime")) {
            assertEquals(filled.get(field), reread.get(field), field);
        }
        assertFalse(reread.get("filledAt").isNull());

        // Settlement was asked to settle at the quote's price, not the placement price.
        verify(settlement).settle(argThat(sent ->
            new BigDecimal("101.2346").compareTo(sent.getPricePerUnit()) == 0));

        List<OrderFilled> published = events.stream(OrderFilled.class).toList();
        assertEquals(1, published.size());
        OrderFilled event = published.get(0);
        assertEquals(orderId, event.orderId());
        assertEquals(Order.OrderType.BUY, event.side());
        assertEquals(0, new BigDecimal("101.2346").compareTo(event.price()));
        assertEquals(MarketOrderExecutor.QUOTE_SOURCE, event.quoteSource());
        assertEquals(quoteTime, event.quoteTime());
        assertNotNull(event.filledAt());

        assertEquals(1, auditOf(orderId, AuditEventType.FILLED).size());
        assertTrue(auditOf(orderId, AuditEventType.REJECTED).isEmpty());
    }

    // AC2 + AC4: a stale quote rejects the order; its price is never used.
    @Test
    void staleQuoteRejectsAndIsNeverFilledAt() throws Exception {
        quoted(Instant.now().minusSeconds(60));
        int orderId = acceptedBuy();

        JsonNode rejected = fill(orderId);

        assertRejectedWithoutAFill(orderId, rejected, "STALE_QUOTE");
    }

    // AC2 + AC4: with no quote at all the order rejects as price unavailable.
    @Test
    void missingQuoteRejectsAsPriceUnavailable() throws Exception {
        when(market.findByInstrumentId(instrumentId)).thenReturn(Optional.empty());
        int orderId = acceptedBuy();

        JsonNode rejected = fill(orderId);

        assertRejectedWithoutAFill(orderId, rejected, "PRICE_UNAVAILABLE");
    }

    private void assertRejectedWithoutAFill(int orderId, JsonNode rejected, String reason) {
        assertEquals("REJECTED", rejected.get("orderStatus").asText());
        assertEquals(reason, rejected.get("rejectionReason").asText());
        assertTrue(rejected.get("filledAt").isNull());
        assertTrue(rejected.get("quoteSource").isNull());
        assertTrue(rejected.get("quoteTime").isNull());
        assertEquals(0, PLACEMENT_PRICE.compareTo(rejected.get("pricePerUnit").decimalValue()),
            "the unusable quote's price must not be written to the order");
        verify(settlement, never()).settle(any());
        assertEquals(0, events.stream(OrderFilled.class).count());

        List<AuditEventEntity> rejections = auditOf(orderId, AuditEventType.REJECTED);
        assertEquals(1, rejections.size());
        assertEquals(reason, rejections.get(0).getDetails().get("reason"));
        assertTrue(auditOf(orderId, AuditEventType.FILLED).isEmpty());
    }
}
