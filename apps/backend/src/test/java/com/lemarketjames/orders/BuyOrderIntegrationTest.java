package com.lemarketjames.orders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lemarketjames.auth.domain.AccountRepository;
import com.lemarketjames.market.service.MarketDataService;
import com.lemarketjames.orders.repository.InstrumentRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** No test transaction: requests must commit and readback uses a separate database connection.
 * Runs with H2 by default and the real migrated PostgreSQL schema in Jenkins.
 */
@SpringBootTest
@AutoConfigureMockMvc
class BuyOrderIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AccountRepository accounts;
    @Autowired InstrumentRepository instruments;
    @Autowired MarketDataService market;
    @Autowired JdbcTemplate jdbc;
    String username;
    Integer accountId;
    Integer instrumentId;
    Cookie cookie;

    @BeforeEach
    void registerAndLogin() throws Exception {
        username = "buy" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        var registration = json.readTree("""
            {"password":"Pass123!","fullName":"Buy Test","streetAddress":"123 Main St",
             "city":"Boston","state":"MA","zipCode":"02110","country":"US","ssn":"123-45-6789",
             "initialDeposit":500,"investmentExperience":"beginner","employmentStatus":"employed",
             "dateOfBirth":"1990-01-01","phoneNumber":"5551234567","termsAccepted":true}
            """);
        ((com.fasterxml.jackson.databind.node.ObjectNode) registration).put("username", username)
            .put("email", username + "@example.com");
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(registration)))
            .andExpect(status().is2xxSuccessful());
        accountId = accounts.findAccountIdByUsername(username).orElseThrow();
        instrumentId = instruments.findByTicker("AAPL").orElseThrow().getInstrumentId();
        cookie = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("username", username + "@example.com", "password", "Pass123!"))))
            .andExpect(status().isOk()).andReturn().getResponse().getCookies()[0];
    }

    @AfterEach
    void removeOnlyThisTestsCommittedData() {
        // The disposable PostgreSQL suite also runs other tests; never truncate shared tables.
        jdbc.update("DELETE FROM orders WHERE account_id IN (SELECT a.account_id FROM accounts a JOIN clients c ON a.client_id=c.client_id WHERE c.username=?)", username);
        jdbc.update("DELETE FROM accounts WHERE client_id IN (SELECT client_id FROM clients WHERE username=?)", username);
        jdbc.update("DELETE FROM addresses WHERE client_id IN (SELECT client_id FROM clients WHERE username=?)", username);
        jdbc.update("DELETE FROM clients WHERE username=?", username);
    }

    private String request(int account, int instrument, int quantity) throws Exception {
        return json.writeValueAsString(Map.of("accountId", account, "instrumentId", instrument,
            "orderType", "BUY", "quantity", quantity));
    }

    @Test
    void buyCommitsPricedOrderAndCanBeReadInAnotherRequest() throws Exception {
        BigDecimal expectedPrice = BigDecimal.valueOf(market.findByInstrumentId(instrumentId).orElseThrow().askPrice())
            .setScale(4, RoundingMode.HALF_UP);
        var result = mvc.perform(post("/api/v1/orders").cookie(cookie).contentType(MediaType.APPLICATION_JSON)
            .content(request(accountId, instrumentId, 1)))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.orderStatus").value("SUBMITTED"))
            .andExpect(jsonPath("$.submittedAt").isNotEmpty()).andReturn();
        int orderId = json.readTree(result.getResponse().getContentAsString()).get("orderId").asInt();
        var row = jdbc.queryForMap("SELECT account_id, instrument_id, quantity, price_per_unit, order_status FROM orders WHERE order_id=?", orderId);
        assertEquals(accountId, ((Number) row.get("account_id")).intValue());
        assertEquals(instrumentId, ((Number) row.get("instrument_id")).intValue());
        assertEquals(0, BigDecimal.ONE.compareTo((BigDecimal) row.get("quantity")));
        assertEquals(0, expectedPrice.compareTo((BigDecimal) row.get("price_per_unit")));
        assertEquals("SUBMITTED", row.get("order_status"));
        mvc.perform(get("/api/v1/orders/" + orderId).cookie(cookie))
            .andExpect(status().isOk()).andExpect(jsonPath("$.orderId").value(orderId));
        mvc.perform(get("/api/v1/orders/account/" + accountId).cookie(cookie))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].orderId").value(orderId));
    }

    @Test
    void rejectedBuysDoNotPersist() throws Exception {
        mvc.perform(post("/api/v1/orders").cookie(cookie).contentType(MediaType.APPLICATION_JSON)
            .content(request(accountId, instrumentId, 10000)))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INSUFFICIENT_CASH"));
        mvc.perform(post("/api/v1/orders").cookie(cookie).contentType(MediaType.APPLICATION_JSON)
            .content(request(accountId, instrumentId, 0)))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.quantity").exists());
        int nonTradable = instruments.findByTicker("GOOGL").orElseThrow().getInstrumentId();
        mvc.perform(post("/api/v1/orders").cookie(cookie).contentType(MediaType.APPLICATION_JSON)
            .content(request(accountId, nonTradable, 1)))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("NOT_TRADABLE"));
        mvc.perform(post("/api/v1/orders").cookie(cookie).contentType(MediaType.APPLICATION_JSON)
            .content(request(Integer.MAX_VALUE, instrumentId, 1)))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON)
            .content(request(accountId, instrumentId, 1)))
            .andExpect(status().isUnauthorized());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM orders WHERE account_id=?", Integer.class, accountId));
    }
}
