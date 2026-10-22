package com.lemarketjames.reports.overnight;

import com.lemarketjames.config.ReportingDataSourceConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the files overnight runs produced ({@code report_outputs}, migration 030). Read only;
 * {@link ReportRunWriter} stores them. Through the reports' pool, like the run list it belongs to.
 */
@Component
public class ReportOutputs {

    private final JdbcTemplate jdbc;

    public ReportOutputs(@Qualifier(ReportingDataSourceConfig.JDBC) JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** The file names of the runs on or after {@code since}, by run: the list shows a download for each. */
    public Map<UUID, String> fileNamesSince(LocalDate since) {
        Map<UUID, String> names = new HashMap<>();
        jdbc.query("""
                SELECT o.run_id, o.file_name FROM report_outputs o JOIN report_runs r ON r.run_id = o.run_id
                WHERE r.run_date >= ?
                """, rs -> {
            names.put(rs.getObject("run_id", UUID.class), rs.getString("file_name"));
        }, since);
        return names;
    }

    /** The file one run produced, if it produced one. */
    public Optional<ReportOutput> find(UUID runId) {
        return jdbc.query("SELECT file_name, content_type, body FROM report_outputs WHERE run_id = ?",
                (rs, i) -> new ReportOutput(rs.getString("file_name"), rs.getString("content_type"), rs.getString("body")),
                runId).stream().findFirst();
    }
}
