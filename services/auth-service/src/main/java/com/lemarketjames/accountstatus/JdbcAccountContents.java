package com.lemarketjames.accountstatus;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Reads an account's contents straight from the shared database. auth-service maps neither
 * holdings nor orders and calls no other service (AGENTS.md: Auth is self-contained), so it reads
 * the two tables with plain SQL, in the caller's transaction.
 *
 * <p>An order is open unless it is FILLED or REJECTED, the two final statuses of
 * {@code OrderStatus.isOpen()} in orders-service (contract C1). Listing the final statuses rather
 * than the open ones means a status added later counts as open, so it blocks closing until someone
 * decides otherwise.
 */
@Component
public class JdbcAccountContents implements AccountContents {

    private static final String SQL = """
            SELECT a.cash_balance, a.currency,
                   (SELECT COUNT(*) FROM holdings h WHERE h.account_id = a.account_id AND h.quantity > 0) AS holdings,
                   (SELECT COUNT(*) FROM orders o
                     WHERE o.account_id = a.account_id AND o.order_status NOT IN ('FILLED', 'REJECTED')) AS open_orders
            FROM accounts a
            JOIN clients c ON c.client_id = a.client_id
            WHERE c.username = ?
            """;

    private final JdbcTemplate jdbc;

    public JdbcAccountContents(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Contents> of(String username) {
        return jdbc.query(SQL, (row, i) -> new Contents(
                        row.getBigDecimal("cash_balance"),
                        row.getString("currency"),
                        row.getLong("holdings"),
                        row.getLong("open_orders")), username)
                .stream()
                .findFirst();
    }
}
