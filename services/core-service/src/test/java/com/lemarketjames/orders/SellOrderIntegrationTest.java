package com.lemarketjames.orders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lemarketjames.common.domain.*;
import com.lemarketjames.common.security.JwtAuthenticationFilter;
import com.lemarketjames.common.security.JwtService;
import com.lemarketjames.holdings.entity.HoldingsEntity;
import com.lemarketjames.holdings.repository.HoldingsRepository;
import com.lemarketjames.orders.repository.InstrumentRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** No test transaction: HTTP writes must commit before an independent SQL readback. */
@SpringBootTest
@AutoConfigureMockMvc
class SellOrderIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AccountRepository accounts;
    @Autowired ClientRepository clients;
    @Autowired AddressRepository addresses;
    @Autowired JwtService jwt;
    @Autowired InstrumentRepository instruments;
    @Autowired HoldingsRepository holdings;
    @Autowired JdbcTemplate jdbc;
    String username;
    Integer accountId, instrumentId;
    Cookie cookie;

    @BeforeEach
    void seedAccountWithShares() {
        username = "sell" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        accountId = register(username);
        instrumentId = instruments.findByTicker("AAPL").orElseThrow().getInstrumentId();
        holdings.saveAndFlush(new HoldingsEntity(accountId, instrumentId, new BigDecimal("3")));
        cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, jwt.generateToken(username));
    }

    @AfterEach
    void cleanup() {
        // Remove only this test's committed rows, including on the disposable PostgreSQL profile.
        if (accountId != null) {
            jdbc.update("DELETE FROM orders WHERE account_id=?", accountId);
            jdbc.update("DELETE FROM holdings WHERE account_id=?", accountId);
            jdbc.update("DELETE FROM accounts WHERE account_id=?", accountId);
        }
        jdbc.update("DELETE FROM addresses WHERE client_id IN (SELECT client_id FROM clients WHERE username=?)", username);
        jdbc.update("DELETE FROM clients WHERE username=?", username);
    }

    private String request(int account, int instrument, Object quantity) throws Exception {
        return json.writeValueAsString(Map.of("accountId", account, "instrumentId", instrument,
            "orderType", "SELL", "quantity", quantity));
    }

    @Test
    void sellCommitsAndReturnsConfirmationWithoutExecuting() throws Exception {
        var result = mvc.perform(post("/api/v1/orders").cookie(cookie).contentType(MediaType.APPLICATION_JSON)
            .content(request(accountId, instrumentId, 3)))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.orderType").value("SELL"))
            .andExpect(jsonPath("$.orderStatus").value("SUBMITTED"))
            .andExpect(jsonPath("$.submittedAt").isNotEmpty()).andReturn();
        int id = json.readTree(result.getResponse().getContentAsString()).get("orderId").asInt();
        var row = jdbc.queryForMap("SELECT account_id, instrument_id, quantity, order_type, order_status FROM orders WHERE order_id=?", id);
        assertEquals(accountId, ((Number) row.get("account_id")).intValue());
        assertEquals(instrumentId, ((Number) row.get("instrument_id")).intValue());
        assertEquals(0, new BigDecimal("3").compareTo((BigDecimal) row.get("quantity")));
        assertEquals("SELL", row.get("order_type"));
        assertEquals("SUBMITTED", row.get("order_status"));
        mvc.perform(get("/api/v1/orders/" + id).cookie(cookie))
            .andExpect(status().isOk()).andExpect(jsonPath("$.orderId").value(id));
        mvc.perform(get("/api/v1/orders/account/" + accountId).cookie(cookie))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].orderId").value(id));
        assertEquals(0, new BigDecimal("3").compareTo(holdings.findByAccountIdAndInstrumentId(accountId, instrumentId).orElseThrow().getQuantity()));
        assertEquals(0, new BigDecimal("500").compareTo(accounts.findById(accountId).orElseThrow().getCashBalance()));
    }

    @Test
    void directRequestsCannotBypassHoldingsValidation() throws Exception {
        mvc.perform(post("/api/v1/orders").cookie(cookie).contentType(MediaType.APPLICATION_JSON)
            .content(request(accountId, instrumentId, 4)))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INSUFFICIENT_HOLDINGS"));
        holdings.deleteAll(holdings.findByAccountId(accountId));
        mvc.perform(post("/api/v1/orders").cookie(cookie).contentType(MediaType.APPLICATION_JSON)
            .content(request(accountId, instrumentId, 1)))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INSUFFICIENT_HOLDINGS"));
        assertNoOrders();
    }

    @Test
    void invalidUnauthorizedAndNonTradableRequestsDoNotPersist() throws Exception {
        for (int quantity : new int[]{0, -1}) {
            mvc.perform(post("/api/v1/orders").cookie(cookie).contentType(MediaType.APPLICATION_JSON)
                .content(request(accountId, instrumentId, quantity)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.quantity").exists());
        }
        mvc.perform(post("/api/v1/orders").cookie(cookie).contentType(MediaType.APPLICATION_JSON)
            .content(request(Integer.MAX_VALUE, instrumentId, 1)))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON)
            .content(request(accountId, instrumentId, 1)))
            .andExpect(status().isUnauthorized());
        int nonTradable = instruments.findByTicker("GOOGL").orElseThrow().getInstrumentId();
        mvc.perform(post("/api/v1/orders").cookie(cookie).contentType(MediaType.APPLICATION_JSON)
            .content(request(accountId, nonTradable, 1)))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("NOT_TRADABLE"));
        assertNoOrders();
    }

    private void assertNoOrders() {
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM orders WHERE account_id=?", Integer.class, accountId));
    }

    // Auth is a separate service; seed its shared account data and use its JWT format.
    private Integer register(String username) {
        ClientEntity client = new ClientEntity();
        client.setUsername(username);
        client.setPassword("not-used-by-core-service");
        client.setEmail(username + "@example.com");
        client.setFullName("Sell Test");
        client.setDateOfBirth(LocalDate.of(1990, 1, 1));
        client.setPhone("5551234567");
        client.setRegisteredDate(LocalDateTime.now());
        client.setSsn("test-only");
        client.setEmploymentStatus("EMPLOYED");
        client.setInvestmentExperience("beginner");
        client.setAccountStatus("ACTIVE");
        client = clients.saveAndFlush(client);
        AddressEntity address = new AddressEntity();
        address.setClientId(client.getClientId());
        address.setAddressType("RESIDENTIAL");
        address.setStreetAddress("123 Main St");
        address.setCity("Boston");
        address.setState("MA");
        address.setPostalCode("02110");
        address.setCountry("US");
        addresses.saveAndFlush(address);
        AccountEntity account = new AccountEntity();
        account.setClientId(client.getClientId());
        account.setCashBalance(new BigDecimal("500"));
        account.setCurrency("USD");
        account.setTradingEnabled(true);
        account.setOpenedDate(LocalDate.now());
        return accounts.saveAndFlush(account).getAccountId();
    }
}
