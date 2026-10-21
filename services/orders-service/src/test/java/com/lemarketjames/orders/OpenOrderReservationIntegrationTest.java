package com.lemarketjames.orders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lemarketjames.common.domain.AccountEntity;
import com.lemarketjames.common.domain.AccountRepository;
import com.lemarketjames.common.domain.AccountStatus;
import com.lemarketjames.common.domain.AddressEntity;
import com.lemarketjames.common.domain.AddressRepository;
import com.lemarketjames.common.domain.ClientEntity;
import com.lemarketjames.common.domain.ClientRepository;
import com.lemarketjames.common.instruments.InstrumentRepository;
import com.lemarketjames.common.security.JwtAuthenticationFilter;
import com.lemarketjames.common.security.JwtService;
import com.lemarketjames.holdings.client.HoldingsValidationClient;
import com.lemarketjames.market.model.QuoteSnapshot;
import com.lemarketjames.market.service.MarketDataService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BR-05: open orders reserve what they will spend, and two orders on one account are checked one
 * after the other. No test transaction: each placement must commit for the next to see it. Runs
 * with H2 by default and the real migrated PostgreSQL schema in Jenkins.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OpenOrderReservationIntegrationTest {
    // $500 of cash and a $100 ask: one order for 3 shares fits, a second doesn't.
    private static final BigDecimal CASH = new BigDecimal("500");
    private static final double ASK = 100.0;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AccountRepository accounts;
    @Autowired ClientRepository clients;
    @Autowired AddressRepository addresses;
    @Autowired InstrumentRepository instruments;
    @Autowired JwtService jwt;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuditTestCleanup cleanup;
    @MockBean MarketDataService market;
    @MockBean HoldingsValidationClient holdings;
    String username;
    Integer accountId;
    Integer instrumentId;
    Cookie cookie;

    @BeforeEach
    void registerAndLogin() {
        username = "rsv" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        accountId = register(username);
        instrumentId = instruments.findByTicker("AAPL").orElseThrow().getInstrumentId();
        cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, jwt.generateToken(username));
        when(market.findByInstrumentId(instrumentId)).thenAnswer(call -> Optional.of(quote()));
    }

    @AfterEach
    void removeOnlyThisTestsCommittedData() {
        cleanup.removeClient(username);
    }

    @Test
    void aSecondBuyCantSpendCashTheFirstReserved() throws Exception {
        place("BUY", 3).andExpect(status().isCreated());

        place("BUY", 3).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INSUFFICIENT_CASH"))
            // The refusal names what is left after the open order, not the whole balance.
            .andExpect(jsonPath("$.reason").value("Insufficient balance. Required: $300.00, Available: $200.00"));
        place("BUY", 2).andExpect(status().isCreated());
        assertEquals(2, openOrders());
    }

    @Test
    void twoBuysPlacedAtOnceAreCheckedOneAfterTheOther() throws Exception {
        // A slow quote holds each placement between its cash check and its save; without the
        // account lock both would check the same $500 and both be saved.
        when(market.findByInstrumentId(instrumentId)).thenAnswer(call -> {
            Thread.sleep(300);
            return Optional.of(quote());
        });
        CyclicBarrier start = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<String>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                results.add(pool.submit(() -> {
                    start.await(5, TimeUnit.SECONDS);
                    var response = place("BUY", 3).andReturn().getResponse();
                    return response.getStatus() + " " + json.readTree(response.getContentAsString()).path("code").asText();
                }));
            }
            List<String> outcomes = new ArrayList<>();
            for (Future<String> result : results) {
                outcomes.add(result.get(30, TimeUnit.SECONDS));
            }
            outcomes.sort(null);
            assertEquals(List.of("201 null", "400 INSUFFICIENT_CASH"), outcomes);
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, openOrders());
    }

    @Test
    void aSellIsCheckedAgainstTheSharesOpenSellsAlreadyPromise() throws Exception {
        place("SELL", 4).andExpect(status().isCreated());
        place("SELL", 2).andExpect(status().isCreated());

        // trading-service is told about the 4 already in an open sell, then about all 6.
        verify(holdings).validateSufficientHoldings(eq(accountId), eq(username), eq(instrumentId),
            argThat(sell -> sell.compareTo(new BigDecimal("2")) == 0),
            argThat(reserved -> reserved.compareTo(new BigDecimal("4")) == 0));
    }

    private org.springframework.test.web.servlet.ResultActions place(String side, int quantity) throws Exception {
        return mvc.perform(post("/api/v1/orders").cookie(cookie).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("accountId", accountId, "instrumentId", instrumentId,
                "orderType", side, "quantity", quantity))));
    }

    private int openOrders() {
        return jdbc.queryForObject("SELECT count(*) FROM orders WHERE account_id=?", Integer.class, accountId);
    }

    private static QuoteSnapshot quote() {
        return new QuoteSnapshot(null, ASK, ASK, ASK, ASK, ASK, ASK, ASK, 0, Instant.now(), null);
    }

    // Auth is a separate service; seed its shared account data and use its JWT format.
    private Integer register(String username) {
        ClientEntity client = new ClientEntity();
        client.setUsername(username);
        client.setPassword("not-used-by-orders-service");
        client.setEmail(username + "@example.com");
        client.setFullName("Reservation Test");
        client.setDateOfBirth(LocalDate.of(1990, 1, 1));
        client.setPhone("5551234567");
        client.setRegisteredDate(LocalDateTime.now());
        client.setSsn("test-only");
        client.setEmploymentStatus("EMPLOYED");
        client.setInvestmentExperience("beginner");
        client.setAccountStatus(AccountStatus.ACTIVE);
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
        account.setCashBalance(CASH);
        account.setCurrency("USD");
        account.setTradingEnabled(true);
        account.setOpenedDate(LocalDate.now());
        return accounts.saveAndFlush(account).getAccountId();
    }
}
