package com.lemarketjames.accountstatus;

import com.lemarketjames.auth.AuthService;
import com.lemarketjames.auth.dto.RegisterRequest;
import com.lemarketjames.common.domain.StaffUserEntity;
import com.lemarketjames.common.domain.StaffUserRepository;
import com.lemarketjames.common.security.JwtAuthenticationFilter;
import com.lemarketjames.common.security.Role;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BR-03 account status changes through the API: Trading Ops suspend and reinstate a client, a
 * client closes their own account, and each change leaves a history row and ends the session.
 * Runs on the H2 test database, so the holdings and orders tables (other services') are made here
 * with only the columns the closing check reads.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class AccountStatusApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthService authService;

    @Autowired
    private StaffUserRepository staffUsers;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void createOtherServicesTables() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS holdings (account_id INTEGER, instrument_id INTEGER, quantity NUMERIC(14,4))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS orders (order_id INTEGER, account_id INTEGER, order_status VARCHAR(20))");
    }

    @Test
    void tradingOpsSuspendsAClientWhoseOpenSessionThenEnds(CapturedOutput output) throws Exception {
        Cookie client = registerAndLogIn("suspendme");
        int clientId = clientId("suspendme");
        Cookie ops = staffSession("ops_suspend", Role.TRADING_OPS);

        mockMvc.perform(get("/api/v1/clients/{id}/status", clientId).cookie(ops))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientId").value(clientId))
                .andExpect(jsonPath("$.fullName").value("Test User"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.changes.length()").value(0));

        mockMvc.perform(statusChange(clientId, ops, "suspended", "  Under investigation  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"))
                .andExpect(jsonPath("$.changes[0].oldStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.changes[0].newStatus").value("SUSPENDED"))
                .andExpect(jsonPath("$.changes[0].reason").value("Under investigation"))
                .andExpect(jsonPath("$.changes[0].changedBy").value("ops_suspend"))
                .andExpect(jsonPath("$.changes[0].changedAt").isNotEmpty());

        assertEquals(1, jdbc.queryForObject(
                "select count(*) from account_status_changes where client_id = ? and new_status = 'SUSPENDED'",
                Integer.class, clientId));
        // The client's session, opened before, is refused on its next request.
        mockMvc.perform(get("/api/auth/me").cookie(client))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ACCOUNT_INACTIVE"));
        assertTrue(output.getOut().contains(
                "ACCOUNT_STATUS_CHANGED username=suspendme from=ACTIVE to=SUSPENDED changedBy=ops_suspend"));
    }

    @Test
    void reinstatingAClientNeedsAFreshSignIn() throws Exception {
        Cookie oldSession = registerAndLogIn("reinstateme");
        int clientId = clientId("reinstateme");
        Cookie ops = staffSession("ops_reinstate", Role.TRADING_OPS);

        mockMvc.perform(statusChange(clientId, ops, "SUSPENDED", "Documents missing")).andExpect(status().isOk());
        mockMvc.perform(statusChange(clientId, ops, "ACTIVE", "Documents received"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.changes.length()").value(2))
                .andExpect(jsonPath("$.changes[0].newStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.changes[1].newStatus").value("SUSPENDED"));

        // The session from before the suspension stays ended; signing in again works.
        mockMvc.perform(get("/api/auth/me").cookie(oldSession))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ACCOUNT_INACTIVE"));
        mockMvc.perform(get("/api/auth/me").cookie(logIn("reinstateme")))
                .andExpect(status().isOk());
    }

    @Test
    void onlyTradingOpsMayReadOrChangeAStatus() throws Exception {
        Cookie client = registerAndLogIn("notops");
        int clientId = clientId("notops");
        Cookie analyst = staffSession("analyst_status", Role.ANALYST);

        mockMvc.perform(get("/api/v1/clients/{id}/status", clientId)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/clients/{id}/status", clientId).cookie(client)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/clients/{id}/status", clientId).cookie(analyst)).andExpect(status().isForbidden());
        mockMvc.perform(statusChange(clientId, client, "CLOSED", "Self-service")).andExpect(status().isForbidden());
        mockMvc.perform(statusChange(clientId, analyst, "CLOSED", "Not mine to do")).andExpect(status().isForbidden());

        assertEquals("ACTIVE", accountStatus("notops"));
        assertEquals(0, jdbc.queryForObject(
                "select count(*) from account_status_changes where client_id = ?", Integer.class, clientId));
    }

    @Test
    void aStatusChangeNeedsAKnownClientAValidNewStatusAndAReason() throws Exception {
        authService.register(registerRequest("badchange"));
        int clientId = clientId("badchange");
        Cookie ops = staffSession("ops_validation", Role.TRADING_OPS);

        mockMvc.perform(statusChange(clientId, ops, "FROZEN", "Because"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.status").value("Choose one of ACTIVE, SUSPENDED, CLOSED, EXPIRED."));
        mockMvc.perform(statusChange(clientId, ops, "SUSPENDED", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.reason").value("Give a reason for the change."));
        mockMvc.perform(statusChange(clientId, ops, "SUSPENDED", "x".repeat(501)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.reason").value("Keep the reason to 500 characters or fewer."));
        mockMvc.perform(statusChange(clientId, ops, "ACTIVE", "Already is"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.status").value("The account is already ACTIVE."));
        mockMvc.perform(statusChange(999_999, ops, "SUSPENDED", "Nobody"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CLIENT_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/clients/{id}/status", 999_999).cookie(ops))
                .andExpect(status().isNotFound());

        assertEquals("ACTIVE", accountStatus("badchange"));
    }

    @Test
    void aClientClosesAnEmptyAccountAndIsSignedOut() throws Exception {
        Cookie client = registerAndLogIn("closeself");
        int clientId = clientId("closeself");
        // A finished order does not stand in the way.
        jdbc.update("insert into orders (order_id, account_id, order_status) values (?, ?, 'FILLED')",
                9001, accountId("closeself"));

        mockMvc.perform(get("/api/auth/close-account").cookie(client))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closable").value(true))
                .andExpect(jsonPath("$.cashBalance").value(5000))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.holdings").value(0))
                .andExpect(jsonPath("$.openOrders").value(0));

        mockMvc.perform(close(client, "Wrong123!"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").value("The password is not correct."));
        assertEquals("ACTIVE", accountStatus("closeself"));

        MockHttpServletResponse closed = mockMvc.perform(close(client, "Pass123!"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Your account is closed."))
                .andExpect(jsonPath("$.cashBalance").value(5000))
                .andReturn().getResponse();
        String setCookie = closed.getHeader("Set-Cookie");
        assertNotNull(setCookie);
        assertTrue(setCookie.contains("Max-Age=0"), setCookie);

        assertEquals("CLOSED", accountStatus("closeself"));
        assertEquals(AccountClosureService.SELF_CLOSE_REASON, jdbc.queryForObject(
                "select reason from account_status_changes where client_id = ? and changed_by = 'closeself'",
                String.class, clientId));
        // The cash stays on the closed account.
        assertEquals(0, new BigDecimal("5000").compareTo(jdbc.queryForObject(
                "select cash_balance from accounts where account_id = ?", BigDecimal.class, accountId("closeself"))));
        mockMvc.perform(get("/api/auth/me").cookie(client))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ACCOUNT_INACTIVE"));
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "username": "closeself@example.com", "password": "Pass123!" }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_INACTIVE"));
    }

    @Test
    void closingIsRefusedWhileStocksOrOpenOrdersRemain() throws Exception {
        Cookie client = registerAndLogIn("notempty");
        int accountId = accountId("notempty");
        jdbc.update("insert into holdings (account_id, instrument_id, quantity) values (?, 1, 3)", accountId);

        mockMvc.perform(close(client, "Pass123!"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_EMPTY"))
                .andExpect(jsonPath("$.error").value("Sell your stocks before closing your account."))
                .andExpect(jsonPath("$.holdings").value(1))
                .andExpect(jsonPath("$.openOrders").value(0));

        jdbc.update("delete from holdings where account_id = ?", accountId);
        jdbc.update("insert into orders (order_id, account_id, order_status) values (?, ?, 'DELAYED')", 9002, accountId);
        mockMvc.perform(get("/api/auth/close-account").cookie(client))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closable").value(false))
                .andExpect(jsonPath("$.openOrders").value(1));
        mockMvc.perform(close(client, "Pass123!"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(containsString("open orders")));

        assertEquals("ACTIVE", accountStatus("notempty"));
        mockMvc.perform(get("/api/auth/me").cookie(client)).andExpect(status().isOk());
    }

    @Test
    void staffCannotCloseAnAccountThroughTheClientEndpoint() throws Exception {
        Cookie ops = staffSession("ops_close", Role.TRADING_OPS);
        mockMvc.perform(get("/api/auth/close-account").cookie(ops)).andExpect(status().isForbidden());
        mockMvc.perform(close(ops, "Pass123!")).andExpect(status().isForbidden());
    }

    private org.springframework.test.web.servlet.RequestBuilder statusChange(int clientId, Cookie session,
                                                                            String status, String reason) {
        return put("/api/v1/clients/{id}/status", clientId).cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"%s\",\"reason\":\"%s\"}".formatted(status, reason));
    }

    private org.springframework.test.web.servlet.RequestBuilder close(Cookie session, String password) {
        return post("/api/auth/close-account").cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"%s\"}".formatted(password));
    }

    private Cookie registerAndLogIn(String username) throws Exception {
        authService.register(registerRequest(username));
        return logIn(username);
    }

    private Cookie logIn(String username) throws Exception {
        return logInAs(username + "@example.com");
    }

    private Cookie staffSession(String username, Role role) throws Exception {
        StaffUserEntity staff = new StaffUserEntity();
        staff.setUsername(username);
        staff.setEmail(username + "@staff.example.com");
        staff.setPassword(authService.encodePassword("Pass123!"));
        staff.setFullName("Staff " + role.name().toLowerCase(Locale.ROOT));
        staff.setRole(role);
        staffUsers.save(staff);
        return logInAs(username + "@staff.example.com");
    }

    private Cookie logInAs(String email) throws Exception {
        Cookie cookie = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"Pass123!\"}".formatted(email)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie(JwtAuthenticationFilter.COOKIE_NAME);
        assertNotNull(cookie);
        return cookie;
    }

    private int clientId(String username) {
        return jdbc.queryForObject("select client_id from clients where username = ?", Integer.class, username);
    }

    private int accountId(String username) {
        return jdbc.queryForObject("select a.account_id from accounts a join clients c on c.client_id = a.client_id "
                + "where c.username = ?", Integer.class, username);
    }

    private String accountStatus(String username) {
        return jdbc.queryForObject("select account_status from clients where username = ?", String.class, username);
    }

    private static RegisterRequest registerRequest(String username) {
        RegisterRequest request = new RegisterRequest(username, "Pass123!", username + "@example.com", "Test User");
        request.setStreetAddress("123 Main St");
        request.setCity("Springfield");
        request.setState("IL");
        request.setZipCode("62701");
        request.setCountry("US");
        request.setSsn("123-45-6789");
        request.setInitialDeposit(BigDecimal.valueOf(5000));
        request.setInvestmentExperience("beginner");
        request.setEmploymentStatus("employed");
        request.setDateOfBirth(LocalDate.of(1990, 1, 1));
        request.setPhoneNumber("(555) 123-4567");
        return request;
    }
}
