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
        return aggregateWhere("WHERE filled_at >= ? AND filled_at < ?", startUtc, endUtc);
    }

    public List<SegmentActivityRow> aggregateAllTime() {
        return aggregateWhere("");
    }

    private List<SegmentActivityRow> aggregateWhere(String filter, Object... parameters) {
        // DISTINCT spans the entire range, not a sum of monthly distinct-client counts.
        return jdbc.query("""
                SELECT segment, COUNT(DISTINCT client_id) AS active_clients,
                       COUNT(*) AS trade_count, SUM(gross_amount) AS total_traded_value
                FROM reporting_trades
                """ + filter + " GROUP BY segment ORDER BY segment",
                (rs, row) -> new SegmentActivityRow(
                    rs.getString("segment"), rs.getLong("active_clients"),
                    rs.getLong("trade_count"), rs.getBigDecimal("total_traded_value")), parameters);
    }
}
