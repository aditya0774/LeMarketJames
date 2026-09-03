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

    @Test
    void passwordIsHashedWithSaltedHash() {
        String rawPassword = "Pass123!";
        String encodedPassword = authService.encodePassword(rawPassword);

        assertNotEquals(rawPassword, encodedPassword);
        assertTrue(authService.matchesPassword(rawPassword, encodedPassword));
    }

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
                  "employmentStatus": "employed",
                  "dateOfBirth": "1990-01-01",
                  "phoneNumber": "(555) 123-4567"
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

    @Test
    void registerRejectsMissingMandatoryFieldsWithMessages() throws Exception {
        String incompleteJson = """
                {
                  "username": "incomplete",
                  "password": "Pass123!"
                }
                """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(incompleteJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.fullName").exists())
                .andExpect(jsonPath("$.errors.streetAddress").exists());
    }

    @Test
    void registerRejectsDuplicateEmail() throws Exception {
        String registerJson = """
                {
                  "username": "firstuser",
                  "password": "Pass123!",
                  "email": "duplicate@example.com",
                  "fullName": "First User",
                  "streetAddress": "1 First St",
                  "city": "Springfield",
                  "state": "IL",
                  "zipCode": "62701",
                  "country": "USA",
                  "ssn": "123-45-6789",
                  "initialDeposit": 500,
                  "investmentExperience": "beginner",
                  "employmentStatus": "employed",
                  "dateOfBirth": "1990-01-01",
                  "phoneNumber": "(555) 123-4567"
                }
                """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson))
                .andExpect(status().isCreated());

        String duplicateEmailJson = """
                {
                  "username": "seconduser",
                  "password": "Pass123!",
                  "email": "duplicate@example.com",
                  "fullName": "Second User",
                  "streetAddress": "2 Second St",
                  "city": "Springfield",
                  "state": "IL",
                  "zipCode": "62701",
                  "country": "USA",
                  "ssn": "987-65-4321",
                  "initialDeposit": 500,
                  "investmentExperience": "beginner",
                  "employmentStatus": "employed",
                  "dateOfBirth": "1990-01-01",
                  "phoneNumber": "(555) 987-6543"
                }
                """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(duplicateEmailJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Email is already registered"));
    }
}
