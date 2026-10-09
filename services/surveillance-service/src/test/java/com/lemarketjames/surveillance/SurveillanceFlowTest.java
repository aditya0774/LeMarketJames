package com.lemarketjames.surveillance;

import com.lemarketjames.common.security.JwtAuthenticationFilter;
import com.lemarketjames.common.security.JwtService;
import com.lemarketjames.common.security.Role;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * From a Kafka record to what Trading Operations read: the record goes into the real listener,
 * through the service and the database (H2), and comes back out of the endpoint with a real
 * signed token in the {@code jwt} cookie. Only the broker is left out; the listener is given the
 * record value directly. The large-order quantity is set to 100 shares here so the test does not
 * depend on the default.
 */
@SpringBootTest(properties = {"lmj.events.consumer=kafka", "lmj.surveillance.large-order-quantity=100"})
@AutoConfigureMockMvc
class SurveillanceFlowTest {

    private static final String ALERTS = "/api/v1/surveillance/alerts";

    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @Autowired OrderSubmittedListener listener;
    @Autowired OrderAlertRepository stored;

    @BeforeEach
    void noAlerts() {
        stored.deleteAll();
    }

    private Cookie tokenFor(Role role) {
        return new Cookie(JwtAuthenticationFilter.COOKIE_NAME, jwt.generateToken("someone", Set.of(role)));
    }

    private static String newOrder(int orderId, String side, String quantity, String price, String submittedAt) {
        return "{\"orderId\":" + orderId + ",\"accountId\":7,\"instrumentId\":5,\"side\":\"" + side
            + "\",\"quantity\":" + quantity + ",\"price\":" + price + ",\"submittedAt\":\"" + submittedAt + "\"}";
    }

    @Test
    void aLargeOrderOnKafkaBecomesAnAlertForTradingOps() throws Exception {
        listener.onSubmitted(newOrder(42, "BUY", "150", "244.2366", "2026-10-08T14:30:00Z"));

        mvc.perform(get(ALERTS).cookie(tokenFor(Role.TRADING_OPS)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.largeOrderQuantity").value(100))
            .andExpect(jsonPath("$.alerts.length()").value(1))
            .andExpect(jsonPath("$.alerts[0].orderId").value(42))
            .andExpect(jsonPath("$.alerts[0].accountId").value(7))
            .andExpect(jsonPath("$.alerts[0].instrumentId").value(5))
            .andExpect(jsonPath("$.alerts[0].side").value("BUY"))
            .andExpect(jsonPath("$.alerts[0].quantity").value(150))
            .andExpect(jsonPath("$.alerts[0].price").value(244.2366))
            .andExpect(jsonPath("$.alerts[0].reason").value("LARGE_ORDER"))
            .andExpect(jsonPath("$.alerts[0].submittedAt").value("2026-10-08T14:30:00Z"));
    }

    @Test
    void anOrdinaryOrderRaisesNoAlert() throws Exception {
        listener.onSubmitted(newOrder(42, "BUY", "99", "244.2366", "2026-10-08T14:30:00Z"));

        mvc.perform(get(ALERTS).cookie(tokenFor(Role.TRADING_OPS)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.alerts.length()").value(0));
    }

    @Test
    void alertsAreListedNewestOrderFirst() throws Exception {
        listener.onSubmitted(newOrder(42, "BUY", "150", "244.2366", "2026-10-08T14:30:00Z"));
        listener.onSubmitted(newOrder(43, "SELL", "500", "null", "2026-10-08T14:31:00Z"));

        mvc.perform(get(ALERTS).cookie(tokenFor(Role.TRADING_OPS)))
            .andExpect(jsonPath("$.alerts.length()").value(2))
            .andExpect(jsonPath("$.alerts[0].orderId").value(43))
            .andExpect(jsonPath("$.alerts[0].price").doesNotExist())
            .andExpect(jsonPath("$.alerts[1].orderId").value(42));
    }

    /** Kafka delivers at least once, so the same record can arrive again. */
    @Test
    void theSameRecordDeliveredTwiceRaisesOneAlert() {
        String record = newOrder(42, "BUY", "150", "244.2366", "2026-10-08T14:30:00Z");
        listener.onSubmitted(record);
        listener.onSubmitted(record);

        assertEquals(1, stored.count());
    }

    @Test
    void withoutATokenIsUnauthorized() throws Exception {
        mvc.perform(get(ALERTS)).andExpect(status().isUnauthorized());
    }

    /** Every role except TRADING_OPS, so a role added later is refused until someone decides otherwise. */
    @ParameterizedTest
    @EnumSource(value = Role.class, mode = EnumSource.Mode.EXCLUDE, names = "TRADING_OPS")
    void everyOtherRoleIsForbidden(Role role) throws Exception {
        mvc.perform(get(ALERTS).cookie(tokenFor(role))).andExpect(status().isForbidden());
    }

    @Test
    void pathsOutsideSurveillanceAreClosedEvenToTradingOps() throws Exception {
        mvc.perform(get("/api/v1/orders")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/orders").cookie(tokenFor(Role.TRADING_OPS))).andExpect(status().isForbidden());
    }

    @Test
    void healthIsOpenSoTheSmokeTestCanReadIt() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
