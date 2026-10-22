package com.lemarketjames.reports.overnight;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * The overnight reports list for the Analyst dashboard. Access needs no code here: every path
 * under {@code /api/v1/reports/} is ANALYST-only in {@code SecurityConfig} (contract C7).
 */
@RestController
public class OvernightReportsController {

    private final OvernightReportsService service;

    public OvernightReportsController(OvernightReportsService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/reports/overnight")
    public OvernightReportsResponse list() {
        return service.recentRuns();
    }

    /**
     * The file a run produced (BR-16), as an attachment: {@code 404} when the run has none (it failed,
     * has not finished, or its report makes no file).
     */
    @GetMapping("/api/v1/reports/overnight/{runId}/output")
    public ResponseEntity<String> output(@PathVariable UUID runId) {
        ReportOutput file = service.output(runId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "This run has no file"));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.fileName()).build().toString())
                .body(file.body());
    }
}
