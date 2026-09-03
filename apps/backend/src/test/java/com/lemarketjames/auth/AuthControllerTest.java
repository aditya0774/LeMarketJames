package com.lemarketjames.auth;

import com.lemarketjames.auth.security.JwtAuthenticationFilter;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthService authService;

    // Passwords must be stored as a salted hash, never in plain text.
    @Test
    void passwordIsHashedWithSaltedHash() {
        String rawPassword = "Pass123!";
        String encodedPassword = authService.encodePassword(rawPassword);

        assertNotEquals(rawPassword, encodedPassword);
        assertTrue(authService.matchesPassword(rawPassword, encodedPassword));
    }

    // Full end-to-end flow: register over HTTP, then log in, access a protected route, and reject unauthenticated access.
    @Test
    void registerAndLoginEndpointsWork() throws Exception {
        String registerJson = """
                {
                  "username": "testuser",
                  "password": "Pass123!",
                  "email": "test@example.com",
                  "fullName": "Test User",
                  "streetAddress": "123 Main St",
                  "city": "Springfield",
                  "state": "IL",
                  "zipCode": "62701",
                  "country": "USA",
                  "ssn": "123-45-6789",
                  "initialDeposit": 500,
                  "investmentExperience": "beginner",
                  "dateOfBirth": "1990-01-01",
                  "phoneNumber": "(555) 123-4567",
                  "termsAccepted": true
                }
                """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("testuser"))
                .andExpect(jsonPath("$.message").value("User registered successfully"));

        String loginJson = """
                {
                  "username": "testuser",
                  "password": "Pass123!"
                }
                """;

        Cookie jwtCookie = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("testuser"))
                .andExpect(jsonPath("$.message").value("Login successful"))
                .andReturn()
                .getResponse()
                .getCookie(JwtAuthenticationFilter.COOKIE_NAME);

        assertNotNull(jwtCookie);

        mockMvc.perform(get("/api/auth/me").cookie(jwtCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("testuser"));

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    // Registering with a username that already exists should return a 400 with an explanatory message.
    @Test
    void registerRejectsDuplicateUsername() throws Exception {
        String registerJson = """
                {
                  "username": "duplicateuser",
                  "password": "Pass123!",
                  "email": "dup@example.com",
                  "fullName": "Dup User",
                  "streetAddress": "123 Main St",
                  "city": "Springfield",
                  "state": "IL",
                  "zipCode": "62701",
                  "country": "USA",
                  "ssn": "123-45-6789",
                  "initialDeposit": 500,
                  "investmentExperience": "beginner",
                  "dateOfBirth": "1990-01-01",
                  "phoneNumber": "(555) 123-4567",
                  "termsAccepted": true
                }
                """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Username is already taken"));
    }

    // Registering with an email that's already in use (different username) should return a 400.
    @Test
    void registerRejectsDuplicateEmail() throws Exception {
        String firstRegisterJson = """
                {
                  "username": "emailowner",
                  "password": "Pass123!",
                  "email": "shared@example.com",
                  "fullName": "First User",
                  "streetAddress": "123 Main St",
                  "city": "Springfield",
                  "state": "IL",
                  "zipCode": "62701",
                  "country": "USA",
                  "ssn": "123-45-6780",
                  "initialDeposit": 500,
                  "investmentExperience": "beginner",
                  "dateOfBirth": "1990-01-01",
                  "phoneNumber": "(555) 123-4567",
                  "termsAccepted": true
                }
                """;
        String secondRegisterJson = """
                {
                  "username": "emailowner2",
                  "password": "Pass123!",
                  "email": "shared@example.com",
                  "fullName": "Second User",
                  "streetAddress": "123 Main St",
                  "city": "Springfield",
                  "state": "IL",
                  "zipCode": "62701",
                  "country": "USA",
                  "ssn": "123-45-6781",
                  "initialDeposit": 500,
                  "investmentExperience": "beginner",
                  "dateOfBirth": "1990-01-01",
                  "phoneNumber": "(555) 123-4567",
                  "termsAccepted": true
                }
                """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstRegisterJson))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(secondRegisterJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Email is already registered"));
    }

    // Registering without accepting the terms and conditions should return a 400.
    @Test
    void registerRejectsWhenTermsNotAccepted() throws Exception {
        String registerJson = """
                {
                  "username": "notermsuser",
                  "password": "Pass123!",
                  "email": "noterms@example.com",
                  "fullName": "No Terms User",
                  "streetAddress": "123 Main St",
                  "city": "Springfield",
                  "state": "IL",
                  "zipCode": "62701",
                  "country": "USA",
                  "ssn": "123-45-6789",
                  "initialDeposit": 500,
                  "investmentExperience": "beginner",
                  "dateOfBirth": "1990-01-01",
                  "phoneNumber": "(555) 123-4567",
                  "termsAccepted": false
                }
                """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Terms and conditions must be accepted"));
    }

    // Logging in with a username/password that doesn't match any account should return a 400.
    @Test
    void loginRejectsInvalidCredentials() throws Exception {
        String loginJson = """
                {
                  "username": "nobody",
                  "password": "WrongPass!"
                }
                """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    // The session cookie must be HttpOnly/Secure/SameSite so it can be safely reused on a later visit.
    @Test
    void loginCookieIsHardenedForSecureReturnVisits() throws Exception {
        String registerJson = """
                {
                  "username": "cookieuser",
                  "password": "Pass123!",
                  "email": "cookieuser@example.com",
                  "fullName": "Cookie User",
                  "streetAddress": "123 Main St",
                  "city": "Springfield",
                  "state": "IL",
                  "zipCode": "62701",
                  "country": "USA",
                  "ssn": "123-45-6799",
                  "initialDeposit": 500,
                  "investmentExperience": "beginner",
                  "dateOfBirth": "1990-01-01",
                  "phoneNumber": "(555) 123-4567",
                  "termsAccepted": true
                }
                """;
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson))
                .andExpect(status().isCreated());

        String loginJson = """
                {
                  "username": "cookieuser",
                  "password": "Pass123!"
                }
                """;

        String setCookieHeader = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getHeader("Set-Cookie");

        assertNotNull(setCookieHeader);
        assertTrue(setCookieHeader.contains("HttpOnly"));
        assertTrue(setCookieHeader.contains("Secure"));
        assertTrue(setCookieHeader.contains("SameSite=Lax"));
    }
}
