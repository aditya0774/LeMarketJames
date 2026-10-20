package com.lemarketjames.auth;

import com.lemarketjames.auth.dto.RegisterRequest;
import com.lemarketjames.common.domain.StaffUserEntity;
import com.lemarketjames.common.domain.StaffUserRepository;
import com.lemarketjames.common.security.JwtAuthenticationFilter;
import com.lemarketjames.common.security.Role;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the AuthController endpoints.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthService authService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StaffUserRepository staffUserRepository;

    /**
     * Tests that passwords are properly encoded with bcrypt hashing.
     */
    @Test
    void passwordIsHashedWithSaltedHash() {
        String rawPassword = "Pass123!";
        String encodedPassword = authService.encodePassword(rawPassword);

        assertNotEquals(rawPassword, encodedPassword);
        assertTrue(authService.matchesPassword(rawPassword, encodedPassword));
    }

    /**
     * Tests the complete registration and login flow.
     * Verifies that a user can register, login, and access protected endpoints with the JWT token.
     *
     * @throws Exception if a test error occurs
     */
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
                  "country": "US",
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

        // Match the hand-authored PostgreSQL check constraint and bcrypt column size.
        assertEquals("beginner", jdbcTemplate.queryForObject(
                "select investment_experience from clients where username = ?", String.class, "testuser"));
        String storedSsn = jdbcTemplate.queryForObject(
                "select ssn from clients where username = ?", String.class, "testuser");
        assertNotNull(storedSsn);
        assertEquals(60, storedSsn.length());
        assertTrue(authService.matchesPassword("123-45-6789", storedSsn));

        String loginJson = """
                {
                  "username": "test@example.com",
                  "password": "Pass123!"
                }
                """;

        Cookie jwtCookie = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("testuser"))
                .andExpect(jsonPath("$.message").value("Login successful"))
                .andExpect(jsonPath("$.roles[0]").value("CLIENT"))
                .andReturn()
                .getResponse()
                .getCookie(JwtAuthenticationFilter.COOKIE_NAME);

        assertNotNull(jwtCookie);

        mockMvc.perform(get("/api/auth/me").cookie(jwtCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("testuser"))
                .andExpect(jsonPath("$.roles[0]").value("CLIENT"))
                .andExpect(jsonPath("$.accountId").exists())
                // The staff-only profile fields never appear for a client (LMKT-144).
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.fullName").doesNotExist());

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    // LMKT-144: a staff session carries the staff member's own email and full name, and no account.
    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"TRADING_OPS", "ANALYST"})
    void staffLoginAndMeCarryTheirOwnEmailAndFullName(Role role) throws Exception {
        String username = "staff_" + role.name().toLowerCase(Locale.ROOT);
        StaffUserEntity staff = new StaffUserEntity();
        staff.setUsername(username);
        staff.setEmail(username + "@staff.example.com");
        staff.setPassword(authService.encodePassword("Pass123!"));
        staff.setFullName("Staff " + role.name());
        staff.setRole(role);
        staffUserRepository.save(staff);

        Cookie jwtCookie = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "username": "%s@staff.example.com", "password": "Pass123!" }
                                """.formatted(username)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.roles[0]").value(role.name()))
                .andExpect(jsonPath("$.email").value(username + "@staff.example.com"))
                .andExpect(jsonPath("$.fullName").value("Staff " + role.name()))
                .andReturn()
                .getResponse()
                .getCookie(JwtAuthenticationFilter.COOKIE_NAME);
        assertNotNull(jwtCookie);

        mockMvc.perform(get("/api/auth/me").cookie(jwtCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.roles[0]").value(role.name()))
                .andExpect(jsonPath("$.email").value(username + "@staff.example.com"))
                .andExpect(jsonPath("$.fullName").value("Staff " + role.name()))
                .andExpect(jsonPath("$.accountId").doesNotExist());
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
                  "country": "US",
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

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Username is already taken"));
    }

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
                  "country": "US",
                  "ssn": "123-45-6780",
                  "initialDeposit": 500,
                  "investmentExperience": "beginner",
                  "employmentStatus": "employed",
                  "dateOfBirth": "1990-01-01",
                  "phoneNumber": "(555) 123-4567"
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
                  "country": "US",
                  "ssn": "123-45-6781",
                  "initialDeposit": 500,
                  "investmentExperience": "beginner",
                  "employmentStatus": "employed",
                  "dateOfBirth": "1990-01-01",
                  "phoneNumber": "(555) 123-4567"
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

    @Test
    void loginRejectsInvalidCredentials() throws Exception {
        String loginJson = """
                {
                  "username": "nobody@example.com",
                  "password": "WrongPass!"
                }
                """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    // LMKT-57 AC1: the right password for a closed account gets a distinct code the front end can match on.
    @Test
    void loginRefusesAClosedAccountWithAccountInactive() throws Exception {
        authService.register(validRegisterRequest("closeduser"));
        jdbcTemplate.update("update clients set account_status = 'CLOSED' where username = ?", "closeduser");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "username": "closeduser@example.com", "password": "Pass123!" }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("ACCOUNT_INACTIVE"))
                .andExpect(jsonPath("$.message").value("This account is no longer active. Please contact the firm."));
    }

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
                  "country": "US",
                  "ssn": "123-45-6799",
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

        String loginJson = """
                {
                  "username": "cookieuser@example.com",
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

    private RegisterRequest validRegisterRequest(String username) {
        RegisterRequest request = new RegisterRequest(username, "Pass123!", username + "@example.com", "Test User");
        request.setStreetAddress("123 Main St");
        request.setCity("Springfield");
        request.setState("IL");
        request.setZipCode("62701");
        request.setCountry("US");
        request.setSsn("123-45-6789");
        request.setInitialDeposit(BigDecimal.valueOf(500));
        request.setInvestmentExperience("beginner");
        request.setEmploymentStatus("employed");
        request.setDateOfBirth(LocalDate.of(1990, 1, 1));
        request.setPhoneNumber("(555) 123-4567");
        return request;
    }
}
