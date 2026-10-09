package com.lemarketjames.notifications;

import com.lemarketjames.common.domain.AccountRepository;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * From a Kafka record to what the client reads: the record goes into the real listener, through
 * the service and the database (H2), and comes back out of the endpoint with a real signed token
 * in the {@code jwt} cookie. Only the broker is left out; the listener is given the record value
 * directly. Which account a login owns is stubbed, so no client has to be registered.
 */
@SpringBootTest(properties = "lmj.events.consumer=kafka")
@AutoConfigureMockMvc
class NotificationFlowTest {

    private static final String NOTIFICATIONS = "/api/v1/notifications";

    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @Autowired OrderStatusListener listener;
    @Autowired NotificationRepository stored;
    @MockitoBean AccountRepository accounts;

    @BeforeEach
    void twoClients() {
        stored.deleteAll();
        when(accounts.findAccountIdByUsername("alice")).thenReturn(Optional.of(7));
        when(accounts.findAccountIdByUsername("bob")).thenReturn(Optional.of(8));
    }

    private Cookie tokenFor(String username, Role role) {
        return new Cookie(JwtAuthenticationFilter.COOKIE_NAME, jwt.generateToken(username, Set.of(role)));
    }

    private static String statusChange(int orderId, int accountId, String from, String to, String occurredAt) {
        return "{\"orderId\":" + orderId + ",\"accountId\":" + accountId + ",\"from\":\"" + from + "\",\"to\":\"" + to
            + "\",\"occurredAt\":\"" + occurredAt + "\"}";
    }

    @Test
    void aStatusChangeOnKafkaBecomesANotificationForTheOrdersClient() throws Exception {
        listener.onStatusChanged(statusChange(42, 7, "PENDING", "FILLED", "2026-10-08T14:30:00Z"));

        mvc.perform(get(NOTIFICATIONS).cookie(tokenFor("alice", Role.CLIENT)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.notifications.length()").value(1))
            .andExpect(jsonPath("$.notifications[0].orderId").value(42))
            .andExpect(jsonPath("$.notifications[0].status").value("FILLED"))
            .andExpect(jsonPath("$.notifications[0].previousStatus").value("PENDING"))
            .andExpect(jsonPath("$.notifications[0].message").value("Order #42 is now FILLED (was PENDING)"))
            .andExpect(jsonPath("$.notifications[0].occurredAt").value("2026-10-08T14:30:00Z"));
    }

    @Test
    void aClientIsToldEveryStatusOfAnOrderNewestFirst() throws Exception {
        listener.onStatusChanged(statusChange(42, 7, "SUBMITTED", "ACCEPTED", "2026-10-08T14:30:00Z"));
        listener.onStatusChanged(statusChange(42, 7, "ACCEPTED", "FILLED", "2026-10-08T14:30:02Z"));

        mvc.perform(get(NOTIFICATIONS).cookie(tokenFor("alice", Role.CLIENT)))
            .andExpect(jsonPath("$.notifications.length()").value(2))
            .andExpect(jsonPath("$.notifications[0].status").value("FILLED"))
            .andExpect(jsonPath("$.notifications[1].status").value("ACCEPTED"));
    }

    /** Kafka delivers at least once, so the same record can arrive again. */
    @Test
    void theSameRecordDeliveredTwiceIsToldOnce() throws Exception {
        String record = statusChange(42, 7, "PENDING", "FILLED", "2026-10-08T14:30:00Z");
        listener.onStatusChanged(record);
        listener.onStatusChanged(record);

        assertEquals(1, stored.count());
    }

    @Test
    void aClientNeverSeesAnotherClientsNotifications() throws Exception {
        listener.onStatusChanged(statusChange(42, 7, "PENDING", "FILLED", "2026-10-08T14:30:00Z"));

        mvc.perform(get(NOTIFICATIONS).cookie(tokenFor("bob", Role.CLIENT)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.notifications.length()").value(0));
    }

    @Test
    void aClientWithoutAnAccountGetsTheUsualNotFound() throws Exception {
        mvc.perform(get(NOTIFICATIONS).cookie(tokenFor("nobody", Role.CLIENT)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
    }

    @Test
    void withoutATokenIsUnauthorized() throws Exception {
        mvc.perform(get(NOTIFICATIONS)).andExpect(status().isUnauthorized());
    }

    /** Every role except CLIENT, so a role added later is refused until someone decides otherwise. */
    @ParameterizedTest
    @EnumSource(value = Role.class, mode = EnumSource.Mode.EXCLUDE, names = "CLIENT")
    void staffAreForbidden(Role role) throws Exception {
        mvc.perform(get(NOTIFICATIONS).cookie(tokenFor("someone", role))).andExpect(status().isForbidden());
    }

    @Test
    void pathsOutsideNotificationsAreClosedEvenToClients() throws Exception {
        mvc.perform(get("/api/v1/orders")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/orders").cookie(tokenFor("alice", Role.CLIENT))).andExpect(status().isForbidden());
    }

    @Test
    void healthIsOpenSoTheSmokeTestCanReadIt() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
