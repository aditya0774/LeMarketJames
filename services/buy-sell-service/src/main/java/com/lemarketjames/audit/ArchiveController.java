package com.lemarketjames.audit;

import java.time.Instant;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ArchiveController {
    private final ArchiveService archive;

    public ArchiveController(ArchiveService archive) {
        this.archive = archive;
    }

    @GetMapping("/api/v1/audit/archive")
    public Result search(@RequestParam(required = false) Integer orderId,
            @RequestParam(required = false) String requestId,
            @RequestParam(required = false) Integer clientId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return new Result(true, archive.search(orderId, requestId, clientId, from, to, limit, offset));
    }

    public record Result(boolean success, List<ArchiveService.Event> events) {}
}
