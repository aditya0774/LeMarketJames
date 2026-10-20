package com.lemarketjames.orders;

import com.lemarketjames.common.audit.AuditEventType;
import com.lemarketjames.orders.entity.Order;
import com.lemarketjames.orders.repository.OrderRepository;
import com.lemarketjames.common.audit.AuditEventEntity;
import com.lemarketjames.common.audit.AuditEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

/**
 * Integration tests for the order audit timeline endpoint.
 * 
 * <p>Tests the complete flow: security, access control, and correct data retrieval.
 * Uses an in-memory H2 database populated with test orders and audit events.
 * 
 * <p>Covers acceptance criteria AC1–AC3:
 * - AC1: Timeline is chronological, includes all event types with details
 * - AC2: Events come from the audit trail (audit_log table)
 * - AC3: Only TRADING_OPS can access; other roles and clients denied
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:timeline;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Transactional
@DisplayName("Order Audit Timeline API Integration Tests")
class OrderTimelineApiIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;

    private Integer orderId;
    private Integer accountId = 7;
    private Integer clientId = 1;

    @BeforeEach
    void setup() {
        // Create a test order
        Order order = new Order(accountId, 1, Order.OrderType.BUY, new BigDecimal("10"));
        order = orderRepository.save(order);
        orderId = order.getOrderId();

        // Create a complete audit trail: SUBMITTED → VALIDATED → ACCEPTED → FILLED → SETTLED
        auditEventRepository.save(new AuditEventEntity(
            orderId, accountId, clientId, AuditEventType.SUBMITTED,
            Map.of("side", "BUY", "quantity", 10, "price", 100.00),
            Instant.parse("2026-09-21T10:30:00Z")
        ));

        auditEventRepository.save(new AuditEventEntity(
            orderId, accountId, clientId, AuditEventType.VALIDATED,
            Map.of("checks", new String[]{"ACCOUNT", "TRADABLE", "CASH"}),
            Instant.parse("2026-09-21T10:30:01Z")
        ));

        auditEventRepository.save(new AuditEventEntity(
            orderId, accountId, clientId, AuditEventType.ACCEPTED,
            Map.of(),
            Instant.parse("2026-09-21T10:30:02Z")
        ));

        auditEventRepository.save(new AuditEventEntity(
            orderId, accountId, clientId, AuditEventType.FILLED,
            Map.of("quantity", 10, "price", 100.00),
            Instant.parse("2026-09-21T10:30:03Z")
        ));

        auditEventRepository.save(new AuditEventEntity(
            orderId, accountId, clientId, AuditEventType.SETTLED,
            Map.of("cashDelta", -1000.00, "quantityDelta", 10),
            Instant.parse("2026-09-21T10:30:04Z")
        ));
    }

    @Test
    @DisplayName("AC1: TRADING_OPS reads timeline, returns all events in chronological order")
    void tradingOpsCanReadOrderTimeline() throws Exception {
        mvc.perform(get("/api/v1/orders/{orderId}/timeline", orderId)
                .with(user("ops").roles("TRADING_OPS")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(5)))
            .andExpect(jsonPath("$[0].eventType", is("SUBMITTED")))
            .andExpect(jsonPath("$[1].eventType", is("VALIDATED")))
            .andExpect(jsonPath("$[2].eventType", is("ACCEPTED")))
            .andExpect(jsonPath("$[3].eventType", is("FILLED")))
            .andExpect(jsonPath("$[4].eventType", is("SETTLED")))
            .andExpect(jsonPath("$[0].occurredAt", is("2026-09-21T10:30:00Z")))
            .andExpect(jsonPath("$[4].occurredAt", is("2026-09-21T10:30:04Z")))
            .andExpect(jsonPath("$[0].details.side", is("BUY")))
            .andExpect(jsonPath("$[4].details.cashDelta", is(-1000.00)));
    }

    @Test
    @DisplayName("AC3: CLIENT role denied (403)")
    void clientRoleDeniedFromTimeline() throws Exception {
        mvc.perform(get("/api/v1/orders/{orderId}/timeline", orderId)
                .with(user("alice").roles("CLIENT")))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("AC3: ANALYST role denied (403)")
    void analystRoleDeniedFromTimeline() throws Exception {
        mvc.perform(get("/api/v1/orders/{orderId}/timeline", orderId)
                .with(user("analyst").roles("ANALYST")))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("AC3: Unknown order returns 403 (no info leak)")
    void unknownOrderReturns403() throws Exception {
        mvc.perform(get("/api/v1/orders/{orderId}/timeline", 99999)
                .with(user("ops").roles("TRADING_OPS")))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Unauthenticated request denied (401)")
    void unauthenticatedRequestDenied() throws Exception {
        mvc.perform(get("/api/v1/orders/{orderId}/timeline", orderId))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("AC2: Events come from audit repository (empty timeline when no events)")
    void emptyTimelineWhenNoAuditEvents() throws Exception {
        // Create order with no audit events (shouldn't happen in practice)
        Order order = new Order(accountId, 1, Order.OrderType.BUY, new BigDecimal("10"));
        order = orderRepository.save(order);
        
        mvc.perform(get("/api/v1/orders/{orderId}/timeline", order.getOrderId())
                .with(user("ops").roles("TRADING_OPS")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));
    }
}
