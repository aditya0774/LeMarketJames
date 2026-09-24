package com.lemarketjames.security;

import com.lemarketjames.common.domain.AccountEntity;
import com.lemarketjames.common.domain.AccountRepository;
import com.lemarketjames.common.domain.AddressEntity;
import com.lemarketjames.common.domain.AddressRepository;
import com.lemarketjames.common.domain.ClientEntity;
import com.lemarketjames.common.domain.ClientRepository;
import com.lemarketjames.common.security.JwtAuthenticationFilter;
import com.lemarketjames.common.security.JwtService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Real service/repository tests; run with H2 locally, PostgreSQL in Jenkins via env vars. The
 * order-ownership assertions this class used to carry moved to buy-sell-service's
 * OrderOwnDataIntegrationTest when orders became its own service.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OwnDataIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired AccountRepository accounts;
    @Autowired ClientRepository clients;
    @Autowired AddressRepository addresses;
    @Autowired JwtService jwt;
    Integer aliceAccount, bobAccount;
    String alice, bob;
    Cookie cookie;

    @BeforeEach
    void seedTwoUsers() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        alice = "alice" + suffix;
        bob = "bob" + suffix;
        aliceAccount = register(alice);
        bobAccount = register(bob);
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
}
