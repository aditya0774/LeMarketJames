package com.lemarketjames.orders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lemarketjames.common.domain.*;
import com.lemarketjames.common.security.JwtAuthenticationFilter;
import com.lemarketjames.common.security.JwtService;
import com.lemarketjames.market.model.QuoteSnapshot;
import org.springframework.boot.test.mock.mockito.MockBean;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import static org.mockito.Mockito.when;
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
    @MockBean MarketDataService market;
    @Autowired ClientRepository clients;
    @Autowired AddressRepository addresses;
    @Autowired JwtService jwt;
    @Autowired JdbcTemplate jdbc;
    String username;
    Integer accountId;
    Integer instrumentId;
    Cookie cookie;

    @BeforeEach
    void registerAndLogin() throws Exception {
        username = "buy" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        // Auth runs separately; seed shared account data and authenticate using its JWT format.
        accountId = register(username);
        instrumentId = instruments.findByTicker("AAPL").orElseThrow().getInstrumentId();
        cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, jwt.generateToken(username));
        // Keep remote market prices deterministic while exercising real order persistence.
        when(market.findByInstrumentId(instrumentId)).thenReturn(Optional.of(
            new QuoteSnapshot(null, 250.12345, 250.12345, 250.12345, 250.12345,
                250.12345, 250.12345, 250.12345, 0, null, null)));
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
    // Auth is a separate service; seed its shared account data and use its JWT format.
    private Integer register(String username) {
        ClientEntity client = new ClientEntity();
        client.setUsername(username);
        client.setPassword("not-used-by-core-service");
        client.setEmail(username + "@example.com");
        client.setFullName("Buy Test");
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
