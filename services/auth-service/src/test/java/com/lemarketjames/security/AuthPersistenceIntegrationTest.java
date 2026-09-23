package com.lemarketjames.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lemarketjames.auth.AuthService;
import com.lemarketjames.auth.dto.LoginRequest;
import com.lemarketjames.auth.dto.RegisterRequest;
import com.lemarketjames.common.domain.AccountRepository;
import com.lemarketjames.common.domain.AddressRepository;
import com.lemarketjames.common.domain.ClientRepository;
import com.lemarketjames.common.security.JwtService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Registration and login against real repositories; also run against PostgreSQL in Jenkins.
 * Moved from core-service's OwnDataIntegrationTest when auth became its own service.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthPersistenceIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AuthService auth;
    @Autowired AccountRepository accounts;
    @Autowired ClientRepository clients;
    @Autowired AddressRepository addresses;
    @Autowired JwtService jwt;
    String alice;
    Integer aliceAccount;
    Cookie cookie;

    @BeforeEach
    void registerAndLogin() throws Exception {
        alice = "alice" + UUID.randomUUID().toString().substring(0, 8);
        RegisterRequest request = json.readValue("""
            {"password":"Pass123!","fullName":"Test User","streetAddress":"123 Main St",
             "city":"Boston","state":"MA","zipCode":"02110","country":"US","ssn":"123-45-6789",
             "initialDeposit":500,"investmentExperience":"beginner","employmentStatus":"employed",
             "dateOfBirth":"1990-01-01","phoneNumber":"5551234567"}
            """, RegisterRequest.class);
        request.setUsername(alice);
        request.setEmail(alice + "@example.com");
        auth.register(request);
        aliceAccount = accounts.findAccountIdByUsername(alice).orElseThrow();

        var login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("username", alice + "@example.com", "password", "Pass123!"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.accountId").value(aliceAccount)).andReturn();
        cookie = login.getResponse().getCookies()[0];
    }

    @Test
    void meIsScopedToCookie() throws Exception {
        mvc.perform(get("/api/auth/me").cookie(cookie)).andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value(alice))
            .andExpect(jsonPath("$.accountId").value(aliceAccount));
    }

    @Test
    void anonymousMeIsRejected() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void aFreshAuthServiceCanLogInUsingPersistedCredentials() {
        AuthService restarted = new AuthService(jwt, clients, addresses, accounts, 30000);
        var request = new LoginRequest();
        request.setUsername(alice + "@example.com");
        request.setPassword("Pass123!");
        assertEquals(alice, restarted.login(request).getUsername());
        assertEquals(aliceAccount, restarted.getAccountId(alice));
    }
}
