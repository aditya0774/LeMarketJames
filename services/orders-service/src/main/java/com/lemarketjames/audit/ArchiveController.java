package com.lemarketjames.audit;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/v1/audit/archive}: archived audit events, TRADING_OPS only (SecurityConfig,
 * contract C6). Parameters arrive as text and are parsed here, so a malformed one is a 400 with a
 * message rather than a conversion error.
 */
@RestController
public class ArchiveController {

    private final ArchiveService archive;

    public ArchiveController(ArchiveService archive) {
        this.archive = archive;
    }

    @GetMapping("/api/v1/audit/archive")
    public Result search(@RequestParam(required = false) String orderId,
                         @RequestParam(required = false) String requestId,
                         @RequestParam(required = false) String clientId,
                         @RequestParam(required = false) String from,
                         @RequestParam(required = false) String to,
                         @RequestParam(required = false) String limit,
                         @RequestParam(required = false) String offset) {
        ArchiveQuery query = new ArchiveQuery(number(orderId, "orderId"), requestId, number(clientId, "clientId"),
                instant(from, "from"), instant(to, "to"),
                limit == null ? ArchiveQuery.DEFAULT_LIMIT : number(limit, "limit"),
                offset == null ? 0 : number(offset, "offset"));
        return new Result(true, archive.search(query));
    }

    public record Result(boolean success, List<ArchiveService.Event> events) {}

    private static Integer number(String value, String name) {
        if (value == null) return null;
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(name + " must be a whole number", ex);
        }
    }

    private static Instant instant(String value, String name) {
        if (value == null) return null;
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException(name + " must be an ISO-8601 instant, e.g. 2019-01-01T00:00:00Z", ex);
        }
    }
}
