package com.lemarketjames.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lemarketjames.common.domain.AccountEntity;
import com.lemarketjames.common.domain.AccountRepository;
import com.lemarketjames.common.domain.AddressEntity;
import com.lemarketjames.common.domain.AddressRepository;
import com.lemarketjames.common.domain.ClientEntity;
import com.lemarketjames.common.domain.ClientRepository;
import com.lemarketjames.common.security.JwtAuthenticationFilter;
import com.lemarketjames.common.security.JwtService;
import com.lemarketjames.holdings.entity.HoldingsEntity;
import com.lemarketjames.holdings.repository.HoldingsRepository;
import com.lemarketjames.orders.entity.Instrument;
import com.lemarketjames.orders.entity.Order;
import com.lemarketjames.orders.repository.InstrumentRepository;
import com.lemarketjames.orders.repository.OrderRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real service/repository tests; also run against PostgreSQL in Jenkins. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OwnDataIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AccountRepository accounts;
    @Autowired ClientRepository clients;
    @Autowired AddressRepository addresses;
    @Autowired JwtService jwt;
    @Autowired InstrumentRepository instruments;
    @Autowired OrderRepository orders;
    @Autowired HoldingsRepository holdings;
    Integer aliceAccount, bobAccount, instrumentId, aliceOrder, bobOrder;
    String alice, bob;
    Cookie cookie;

    @BeforeEach
    void seedTwoUsers() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        alice = "alice" + suffix;
        bob = "bob" + suffix;
        aliceAccount = register(alice);
        bobAccount = register(bob);
        Instrument instrument = new Instrument();
        instrument.setTicker("IA" + suffix);
        instrument.setName("Isolation test");
        instrument.setAssetClass(Instrument.AssetClass.EQUITY);
        instrument.setCurrency("USD");
        instrument.setTradable(true);
        instrumentId = instruments.saveAndFlush(instrument).getInstrumentId();
        aliceOrder = orders.saveAndFlush(new Order(aliceAccount, instrumentId, Order.OrderType.BUY, BigDecimal.ONE)).getOrderId();
        bobOrder = orders.saveAndFlush(new Order(bobAccount, instrumentId, Order.OrderType.BUY, BigDecimal.TEN)).getOrderId();
        holdings.saveAndFlush(new HoldingsEntity(aliceAccount, instrumentId, BigDecimal.ONE));
        holdings.saveAndFlush(new HoldingsEntity(bobAccount, instrumentId, BigDecimal.TEN));
        // auth-service issues this cookie in production; core-service only has to accept it.
        cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, jwt.generateToken(alice));
    }

    // Registration belongs to auth-service; seed the same shared clients/addresses/accounts rows it writes.
    private Integer register(String username) {
        ClientEntity client = new ClientEntity();
        client.setUsername(username);
        client.setPassword("not-used-by-core-service");
        client.setEmail(username + "@example.com");
        client.setFullName("Test User");
        client.setDateOfBirth(LocalDate.of(1990, 1, 1));
        client.setPhone("5551234567");
        client.setRegisteredDate(LocalDateTime.now());
        client.setSsn("not-used-by-core-service");
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

    @Test
    void ownHoldingsAndSessionAreScopedToCookie() throws Exception {
        mvc.perform(get("/api/v1/holdings").param("accountId", aliceAccount.toString()).cookie(cookie))
            .andExpect(status().isOk()).andExpect(jsonPath("$.holdings.length()").value(1))
            .andExpect(jsonPath("$.holdings[0].quantity").value(1));
        mvc.perform(get("/api/v1/holdings").param("accountId", bobAccount.toString()).cookie(cookie))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCOUNT_ACCESS_DENIED"))
            .andExpect(jsonPath("$.error").value("Access denied"));
    }

    @Test
    void orderReadsAndInstrumentSearchCannotExposeOtherUsers() throws Exception {
        mvc.perform(get("/api/v1/orders/" + aliceOrder).cookie(cookie)).andExpect(status().isOk());
        for (String path : new String[]{"/api/v1/orders/" + bobOrder,
                "/api/v1/orders/account/" + bobAccount,
                "/api/v1/orders/account/" + bobAccount + "/status/SUBMITTED"}) {
            mvc.perform(get(path).cookie(cookie)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_ACCESS_DENIED"));
        }
        mvc.perform(get("/api/v1/orders/instrument/" + instrumentId).cookie(cookie))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].accountId").value(aliceAccount));
        mvc.perform(get("/api/v1/orders/account/" + aliceAccount).cookie(cookie))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/v1/orders/account/" + aliceAccount + "/status/SUBMITTED").cookie(cookie))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/v1/orders/2147483647").cookie(cookie))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCOUNT_ACCESS_DENIED"));
    }

    @Test
    void cannotMutateOrCreateOrdersForOtherUsers() throws Exception {
        mvc.perform(put("/api/v1/orders/" + bobOrder + "/status/FILLED").cookie(cookie))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/orders/" + bobOrder + "/reject").cookie(cookie))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/orders").cookie(cookie).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(java.util.Map.of("accountId", bobAccount,
                "instrumentId", instrumentId, "orderType", "BUY", "quantity", 1))))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/holdings/validate").cookie(cookie).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(java.util.Map.of("accountId", bobAccount,
                "instrumentId", instrumentId, "sellQuantity", 1))))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCOUNT_ACCESS_DENIED"));
        assertEquals(Order.OrderStatus.SUBMITTED, orders.findById(bobOrder).orElseThrow().getOrderStatus());
        assertEquals(1, orders.findByAccountId(bobAccount).size());
    }

    @Test
    void anonymousRequestsCannotReadPrivateData() throws Exception {
        mvc.perform(get("/api/v1/holdings").param("accountId", aliceAccount.toString()))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/orders/" + aliceOrder)).andExpect(status().isUnauthorized());
    }

    @Test
    void sessionValidationRejectsAnotherAccount() throws Exception {
        mvc.perform(post("/api/sessions/validate").cookie(cookie)
            .header("Authorization", "Bearer " + cookie.getValue()).param("accountId", aliceAccount.toString()))
            .andExpect(status().isOk());
        mvc.perform(post("/api/sessions/validate").cookie(cookie)
            .header("Authorization", "Bearer " + cookie.getValue()).param("accountId", bobAccount.toString()))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCOUNT_ACCESS_DENIED"));
    }

    @Test
    void anotherUsersTokenCannotBeUsedWithTheCurrentCookie() throws Exception {
        mvc.perform(post("/api/sessions/validate").cookie(cookie)
            .header("Authorization", "Bearer " + jwt.generateToken(bob)).param("accountId", bobAccount.toString()))
            .andExpect(status().isForbidden());
    }

    @Test
    void emptyOwnHoldingsAreSuccessful() throws Exception {
        holdings.deleteAll(holdings.findByAccountId(aliceAccount));
        holdings.flush();
        mvc.perform(get("/api/v1/holdings").param("accountId", aliceAccount.toString()).cookie(cookie))
            .andExpect(status().isOk()).andExpect(jsonPath("$.holdings.length()").value(0));
    }
}
