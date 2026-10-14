package com.lemarketjames.audit;

import com.lemarketjames.common.audit.*;
import com.lemarketjames.common.config.PlatformSettings;
import java.time.*;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:archive;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "lmj.audit.online-retention=30d"
})
@AutoConfigureMockMvc
@Transactional
class ArchiveApiIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-10-14T12:00:00Z");
    @Autowired MockMvc mvc;
    @Autowired AuditEventRepository events;
    @Autowired PlatformSettings settings;
    @MockitoBean Clock clock;

    @BeforeEach
    void seedBackdatedEvents() {
        when(clock.instant()).thenReturn(NOW);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        add("2018-10-14T12:00:00Z", "old-request");
        add("2019-10-14T12:00:00Z", "seven-years");
        add("2026-09-14T11:59:59Z", "before-cutoff");
        add("2026-09-14T12:00:00Z", "at-cutoff");
        add("2026-09-14T12:00:01Z", "after-cutoff");
    }

    private void add(String time, String requestId) {
        events.save(new AuditEventEntity(new SubmissionAuditEvent(requestId,
                AuditEventType.SUBMITTED, null, null, null, 17,
                Map.of("side", "BUY")), Instant.parse(time)));
    }

    @Test
    void archivesStrictlyOlderThanConfiguredWindowWithoutChangingRecords() throws Exception {
        assertEquals(Duration.ofDays(30), settings.getAudit().getOnlineRetention());
        mvc.perform(get("/api/v1/audit/archive").with(user("ops").roles("TRADING_OPS")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.events", hasSize(3)))
                .andExpect(jsonPath("$.events[0].requestId").value("old-request"))
                .andExpect(jsonPath("$.events[1].requestId").value("seven-years"))
                .andExpect(jsonPath("$.events[2].requestId").value("before-cutoff"));
        var old = events.findByRequestIdOrderByAuditIdAsc("old-request");
        assertEquals(1, old.size());
        assertFalse(old.get(0).isArchived());
        assertEquals(Instant.parse("2018-10-14T12:00:00Z"), old.get(0).getOccurredAt());
        assertEquals(Map.of("side", "BUY"), old.get(0).getDetails());
    }

    @Test
    void recordEntersArchiveWhenTimePassesWithoutAnyWriteJob() throws Exception {
        when(clock.instant()).thenReturn(NOW.plusSeconds(1));
        mvc.perform(get("/api/v1/audit/archive").param("requestId", "at-cutoff")
                .with(user("ops").roles("TRADING_OPS")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.events", hasSize(1)));
    }

    @Test
    void retrievesRefusedSubmissionBeyondSevenYearsWithOriginalDetails() throws Exception {
        mvc.perform(get("/api/v1/audit/archive").param("requestId", "old-request")
                .with(user("ops").roles("TRADING_OPS")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.events", hasSize(1)))
                .andExpect(jsonPath("$.events[0].occurredAt").value("2018-10-14T12:00:00Z"))
                .andExpect(jsonPath("$.events[0].details.side").value("BUY"));
    }

    @Test
    void filtersDatesAndClientAndPaginatesChronologically() throws Exception {
        mvc.perform(get("/api/v1/audit/archive").param("clientId", "17")
                .param("from", "2018-10-14T12:00:00Z").param("to", "2026-09-14T11:59:59Z")
                .param("limit", "1").param("offset", "1").with(user("ops").roles("TRADING_OPS")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.events", hasSize(1)))
                .andExpect(jsonPath("$.events[0].requestId").value("seven-years"));
        mvc.perform(get("/api/v1/audit/archive").param("orderId", "123")
                .with(user("ops").roles("TRADING_OPS")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.events", hasSize(0)));
    }

    @Test
    void rejectsInvalidFiltersAndRestrictsAccess() throws Exception {
        for (String role : new String[]{"CLIENT", "ANALYST"}) {
            mvc.perform(get("/api/v1/audit/archive").with(user("other").roles(role)))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/v1/audit/archive")).andExpect(status().isUnauthorized());
        for (String[] invalid : new String[][]{{"limit", "501"}, {"offset", "-1"},
                {"clientId", "0"}, {"from", "not-a-date"}, {"requestId", " "}}) {
            mvc.perform(get("/api/v1/audit/archive").param(invalid[0], invalid[1])
                    .with(user("ops").roles("TRADING_OPS"))).andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/v1/audit/archive").param("from", NOW.toString())
                .param("to", NOW.toString()).with(user("ops").roles("TRADING_OPS")))
                .andExpect(status().isBadRequest());
        assertThrows(IllegalArgumentException.class,
                () -> settings.getAudit().setOnlineRetention(Duration.ZERO));
    }
}
