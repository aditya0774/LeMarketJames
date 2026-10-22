package com.lemarketjames.audit;

import com.lemarketjames.common.audit.AuditEventEntity;
import com.lemarketjames.common.audit.AuditEventRepository;
import com.lemarketjames.common.audit.AuditEventType;
import com.lemarketjames.common.audit.SubmissionAuditEvent;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BR-14 AC1–AC3 (ported from LMKT-101): an audit event older than the online retention window is
 * archived, not deleted; it is kept beyond seven years; and Trading Ops can retrieve it, including
 * a refused submission that has no order. On H2, like the timeline tests; rolled back after each.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:archive;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Transactional
class AuditArchiveApiIntegrationTest {

    static final String ARCHIVE = "/api/v1/audit/archive";
    /** The default window is 365 days (contract C5). */
    static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    static final Instant EIGHT_YEARS_AGO = NOW.minus(Duration.ofDays(8 * 365));
    static final Instant TWO_YEARS_AGO = NOW.minus(Duration.ofDays(730));
    static final Instant YESTERDAY = NOW.minus(Duration.ofDays(1));

    @Autowired MockMvc mvc;
    @Autowired AuditEventRepository audit;
    int orderId = 90_001;
    int clientId = 4_242;
    String refusedRequest = UUID.randomUUID().toString();

    @BeforeEach
    void anOrderWhoseTrailSpansTheWindowAndARefusedSubmission() {
        audit.save(new AuditEventEntity(orderId, 7, clientId, AuditEventType.SUBMITTED, Map.of("side", "BUY"), EIGHT_YEARS_AGO));
        audit.save(new AuditEventEntity(orderId, 7, clientId, AuditEventType.FILLED, Map.of("price", 10), TWO_YEARS_AGO));
        audit.save(new AuditEventEntity(orderId, 7, clientId, AuditEventType.SETTLED, Map.of(), YESTERDAY));
        audit.save(new AuditEventEntity(new SubmissionAuditEvent(refusedRequest, AuditEventType.REJECTED, null, null,
            null, clientId, Map.of("reason", "INSUFFICIENT_CASH")), TWO_YEARS_AGO.plusSeconds(1)));
    }

    @Test
    void onlyEventsOlderThanTheWindowComeBackOldestFirstWithTheirDetails() throws Exception {
        mvc.perform(get(ARCHIVE).param("orderId", String.valueOf(orderId)).with(user("ops").roles("TRADING_OPS")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success", is(true)))
            .andExpect(jsonPath("$.events[*].eventType", contains("SUBMITTED", "FILLED")))
            .andExpect(jsonPath("$.events[0].occurredAt", is(EIGHT_YEARS_AGO.toString())))
            .andExpect(jsonPath("$.events[0].details.side", is("BUY")))
            .andExpect(jsonPath("$.events[1].details.price", is(10)));
    }

    @Test
    void aRefusedSubmissionWithNoOrderIsFoundByItsRequestId() throws Exception {
        mvc.perform(get(ARCHIVE).param("requestId", refusedRequest).with(user("ops").roles("TRADING_OPS")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.events", hasSize(1)))
            .andExpect(jsonPath("$.events[0].eventType", is("REJECTED")))
            .andExpect(jsonPath("$.events[0].orderId").doesNotExist())
            .andExpect(jsonPath("$.events[0].details.reason", is("INSUFFICIENT_CASH")));
    }

    @Test
    void filtersCombineAndPagesFollowTheOrder() throws Exception {
        // from is inclusive and to exclusive: this window holds the FILLED event and the refusal.
        mvc.perform(get(ARCHIVE).param("clientId", String.valueOf(clientId))
                .param("from", TWO_YEARS_AGO.toString()).param("to", TWO_YEARS_AGO.plusSeconds(2).toString())
                .with(user("ops").roles("TRADING_OPS")))
            .andExpect(jsonPath("$.events[*].eventType", contains("FILLED", "REJECTED")));
        mvc.perform(get(ARCHIVE).param("clientId", String.valueOf(clientId)).param("limit", "1").param("offset", "1")
                .with(user("ops").roles("TRADING_OPS")))
            .andExpect(jsonPath("$.events[*].eventType", contains("FILLED")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"orderId=abc", "orderId=0", "clientId=-1", "from=yesterday", "limit=0", "limit=501",
        "offset=-1", "from=2020-01-02T00:00:00Z&to=2020-01-01T00:00:00Z"})
    void aMalformedSearchIsRefused(String query) throws Exception {
        mvc.perform(get(ARCHIVE + "?" + query).with(user("ops").roles("TRADING_OPS")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void onlyTradingOpsMayReadTheArchive() throws Exception {
        mvc.perform(get(ARCHIVE)).andExpect(status().isUnauthorized());
        mvc.perform(get(ARCHIVE).with(user("alice").roles("CLIENT"))).andExpect(status().isForbidden());
        mvc.perform(get(ARCHIVE).with(user("analyst").roles("ANALYST"))).andExpect(status().isForbidden());
    }
}
