package com.lemarketjames.security;

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
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real service/repository tests; also run against PostgreSQL in Jenkins. Moved from core-service's
 * OwnDataIntegrationTest when holdings became its own service (see auth-service's
 * AuthPersistenceIntegrationTest for the same pattern from the auth extraction).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class HoldingsOwnDataIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired AccountRepository accounts;
    @Autowired ClientRepository clients;
    @Autowired AddressRepository addresses;
    @Autowired JwtService jwt;
    @Autowired HoldingsRepository holdings;
    @Autowired JdbcTemplate jdbcTemplate;

    Integer aliceAccount, bobAccount, instrumentId;
    String alice, bob;
    Cookie cookie;

    @BeforeEach
    void seedTwoUsers() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        alice = "alice" + suffix;
        bob = "bob" + suffix;
        aliceAccount = register(alice);
        bobAccount = register(bob);

        // No Instrument JPA entity here (that stays in core-service); insert the row directly.
        jdbcTemplate.update(
            "INSERT INTO instruments (ticker, name, asset_class, currency, tradable) VALUES (?, ?, 'EQUITY', 'USD', TRUE)",
            "IA" + suffix, "Isolation test");
        instrumentId = jdbcTemplate.queryForObject(
            "SELECT instrument_id FROM instruments WHERE ticker = ?", Integer.class, "IA" + suffix);

        holdings.saveAndFlush(new HoldingsEntity(aliceAccount, instrumentId, BigDecimal.ONE));
        holdings.saveAndFlush(new HoldingsEntity(bobAccount, instrumentId, BigDecimal.TEN));
        // auth-service issues this cookie in production; holdings-service only has to accept it.
        cookie = new Cookie(JwtAuthenticationFilter.COOKIE_NAME, jwt.generateToken(alice));
    }

    // Registration belongs to auth-service; seed the same shared clients/addresses/accounts rows it writes.
    private Integer register(String username) {
        ClientEntity client = new ClientEntity();
        client.setUsername(username);
        client.setPassword("not-used-by-holdings-service");
        client.setEmail(username + "@example.com");
        client.setFullName("Test User");
        client.setDateOfBirth(LocalDate.of(1990, 1, 1));
        client.setPhone("5551234567");
        client.setRegisteredDate(LocalDateTime.now());
        client.setSsn("not-used-by-holdings-service");
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
    void ownHoldingsAreScopedToCookie() throws Exception {
        mvc.perform(get("/api/v1/holdings").param("accountId", aliceAccount.toString()).cookie(cookie))
            .andExpect(status().isOk()).andExpect(jsonPath("$.holdings.length()").value(1))
            .andExpect(jsonPath("$.holdings[0].quantity").value(1));
        mvc.perform(get("/api/v1/holdings").param("accountId", bobAccount.toString()).cookie(cookie))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCOUNT_ACCESS_DENIED"))
            .andExpect(jsonPath("$.error").value("Access denied"));
    }

    @Test
    void validateHoldingsCannotExposeOtherUsers() throws Exception {
        mvc.perform(post("/api/v1/holdings/validate").cookie(cookie).contentType(MediaType.APPLICATION_JSON)
            .content("{\"accountId\":" + bobAccount + ",\"instrumentId\":" + instrumentId + ",\"sellQuantity\":1}"))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCOUNT_ACCESS_DENIED"));
    }

    @Test
    void anonymousRequestsCannotReadPrivateData() throws Exception {
        mvc.perform(get("/api/v1/holdings").param("accountId", aliceAccount.toString()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void emptyOwnHoldingsAreSuccessful() throws Exception {
        holdings.deleteAll(holdings.findByAccountId(aliceAccount));
        holdings.flush();
        mvc.perform(get("/api/v1/holdings").param("accountId", aliceAccount.toString()).cookie(cookie))
            .andExpect(status().isOk()).andExpect(jsonPath("$.holdings.length()").value(0));
    }
}
