package com.lemarketjames.accountstatus;

import com.lemarketjames.auth.AuthService;
import com.lemarketjames.auth.dto.RegisterRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * BR-03 account status history against the real schema (database/schema/027): the history is
 * insert-only for the application account, and the closing check reads the real holdings and
 * orders tables.
 *
 * <p>PostgreSQL only (Jenkins' postgres-test stage): grants and the other services' tables are not
 * in the H2 unit-test schema. Each test rolls back, so it leaves nothing behind.
 */
@SpringBootTest
@Transactional
class AccountStatusPostgresIntegrationTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired AuthService auth;
    @Autowired AccountStatusService statuses;
    @Autowired AccountContents contents;
    String username;

    @BeforeEach
    void registerAClient() {
        String database = jdbc.execute((ConnectionCallback<String>) c -> c.getMetaData().getDatabaseProductName());
        assumeTrue("PostgreSQL".equals(database), "grants are PostgreSQL's; run with the postgres-test profile");

        username = "status" + UUID.randomUUID().toString().substring(0, 8);
        RegisterRequest request = new RegisterRequest(username, "Pass123!", username + "@example.com", "Test User");
        request.setStreetAddress("123 Main St");
        request.setCity("Boston");
        request.setState("MA");
        request.setZipCode("02110");
        request.setCountry("US");
        request.setSsn("123-45-6789");
        request.setInitialDeposit(BigDecimal.valueOf(5000));
        request.setInvestmentExperience("beginner");
        request.setEmploymentStatus("employed");
        request.setDateOfBirth(LocalDate.of(1990, 1, 1));
        request.setPhoneNumber("(555) 123-4567");
        auth.register(request);
    }

    @Test
    void theApplicationAccountMayOnlyReadAndAddHistory() {
        for (String allowed : List.of("SELECT", "INSERT")) {
            assertTrue(hasPrivilege(allowed), allowed);
        }
        for (String refused : List.of("UPDATE", "DELETE", "TRUNCATE")) {
            assertFalse(hasPrivilege(refused), refused);
        }
    }

    @Test
    void aChangeIsStoredInTheHistoryTable() {
        int clientId = jdbc.queryForObject("SELECT client_id FROM clients WHERE username = ?", Integer.class, username);

        var view = statuses.change(clientId, "EXPIRED", "Passport lapsed", "ops-it");

        assertEquals("EXPIRED", view.status().name());
        assertEquals(1, view.changes().size());
        assertEquals("ACTIVE", jdbc.queryForObject(
                "SELECT old_status FROM account_status_changes WHERE client_id = ?", String.class, clientId));
    }

    @Test
    void theClosingCheckCountsHoldingsFromTheRealTables() {
        assertTrue(contents.of(username).orElseThrow().closable());

        int accountId = jdbc.queryForObject("SELECT a.account_id FROM accounts a JOIN clients c "
                + "ON c.client_id = a.client_id WHERE c.username = ?", Integer.class, username);
        int instrumentId = jdbc.queryForObject("SELECT MIN(instrument_id) FROM instruments", Integer.class);
        jdbc.update("INSERT INTO holdings (account_id, instrument_id, quantity) VALUES (?, ?, 2)", accountId, instrumentId);

        AccountContents.Contents left = contents.of(username).orElseThrow();
        assertEquals(1, left.holdings());
        assertEquals(0, left.openOrders());
        assertFalse(left.closable());
    }

    private boolean hasPrivilege(String privilege) {
        return jdbc.queryForObject("SELECT has_table_privilege(current_user, 'account_status_changes', ?)",
                Boolean.class, privilege);
    }
}
