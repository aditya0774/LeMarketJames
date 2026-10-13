package com.lemarketjames.reports.repository;

import com.lemarketjames.reports.dto.SegmentActivityRow;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/** Aggregate in PostgreSQL, avoiding loading individual trades into application memory. */
@Repository
public class SegmentActivityRepository {
    private final JdbcTemplate jdbc;

    public SegmentActivityRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<SegmentActivityRow> aggregate(LocalDateTime startUtc, LocalDateTime endUtc) {
        // DISTINCT counts a client once even when they trade through multiple accounts.
        return jdbc.query("""
                SELECT segment, COUNT(DISTINCT client_id) AS active_clients,
                       COUNT(*) AS trade_count, SUM(gross_amount) AS total_traded_value
                FROM reporting_trades
                WHERE filled_at >= ? AND filled_at < ?
                GROUP BY segment
                ORDER BY segment
                """, (rs, row) -> new SegmentActivityRow(
                rs.getString("segment"), rs.getLong("active_clients"),
                rs.getLong("trade_count"), rs.getBigDecimal("total_traded_value")),
                startUtc, endUtc);
    }
}
