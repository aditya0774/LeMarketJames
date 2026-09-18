package com.lemarketjames.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lemarketjames.auth.AuthService;
import com.lemarketjames.auth.dto.RegisterRequest;
import com.lemarketjames.auth.domain.AccountRepository;
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
    @Autowired AuthService auth;
    @Autowired AccountRepository accounts;
    @Autowired com.lemarketjames.auth.domain.ClientRepository clients;
    @Autowired com.lemarketjames.auth.domain.AddressRepository addresses;
    @Autowired com.lemarketjames.auth.security.JwtService jwt;
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
        var login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(java.util.Map.of("username", alice + "@example.com", "password", "Pass123!"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.accountId").value(aliceAccount)).andReturn();
        cookie = login.getResponse().getCookies()[0];
    }

    private Integer register(String username) throws Exception {
        RegisterRequest request = json.readValue("""
            {"password":"Pass123!","fullName":"Test User","streetAddress":"123 Main St",
             "city":"Boston","state":"MA","zipCode":"02110","country":"US","ssn":"123-45-6789",
             "initialDeposit":500,"investmentExperience":"beginner","employmentStatus":"employed",
             "dateOfBirth":"1990-01-01","phoneNumber":"5551234567"}
            """, RegisterRequest.class);
        request.setUsername(username);
        request.setEmail(username + "@example.com");
        auth.register(request);
        return accounts.findAccountIdByUsername(username).orElseThrow();
    }

    @Test
    void ownHoldingsAndSessionAreScopedToCookie() throws Exception {
        mvc.perform(get("/api/auth/me").cookie(cookie)).andExpect(status().isOk())
            .andExpect(jsonPath("$.accountId").value(aliceAccount));
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
    void aFreshAuthServiceCanLogInUsingPersistedCredentials() {
        AuthService restarted = new AuthService(jwt, clients, addresses, accounts, 30000);
        var request = new com.lemarketjames.auth.dto.LoginRequest();
        request.setUsername(alice + "@example.com");
        request.setPassword("Pass123!");
        assertEquals(alice, restarted.login(request).getUsername());
        assertEquals(aliceAccount, restarted.getAccountId(alice));
    }

    @Test
    void emptyOwnHoldingsAreSuccessful() throws Exception {
        holdings.deleteAll(holdings.findByAccountId(aliceAccount));
        holdings.flush();
        mvc.perform(get("/api/v1/holdings").param("accountId", aliceAccount.toString()).cookie(cookie))
            .andExpect(status().isOk()).andExpect(jsonPath("$.holdings.length()").value(0));
    }
}
