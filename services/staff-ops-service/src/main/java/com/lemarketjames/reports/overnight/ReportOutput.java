package com.lemarketjames.reports.overnight;

/**
 * The file an overnight run produced, stored with the run ({@code report_outputs}, migration 030)
 * and downloaded from the Analyst dashboard ({@code GET /api/v1/reports/overnight/{runId}/output}).
 *
 * @param fileName    the name the analyst's browser saves it as
 * @param contentType its media type, e.g. {@code text/csv}
 * @param body        the file itself; aggregates only, like every report (README, rule 3)
 */
public record ReportOutput(String fileName, String contentType, String body) {
}
