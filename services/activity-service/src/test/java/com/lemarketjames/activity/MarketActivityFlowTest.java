package com.lemarketjames.activity;

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

import java.time.Clock;
import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * From a Kafka record to the figures a signed-in user reads: the record goes into the real
 * listener, through the service and the database (H2), and the sums come back out of the endpoint
 * with a real signed token in the {@code jwt} cookie. Only the broker is left out; the listener
 * is given the record value directly. The time is fixed so the window's edge can be tested.
 */
@SpringBootTest(properties = "lmj.events.consumer=kafka")
@AutoConfigureMockMvc
class MarketActivityFlowTest {

    private static final String ACTIVITY = "/api/v1/market-activity";
    private static final Instant NOW = Instant.parse("2026-10-08T14:30:00Z");

    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @Autowired OrderFilledListener listener;
    @Autowired RecordedFillRepository stored;
    @MockitoBean Clock clock;

    @BeforeEach
    void noFillsAtAFixedTime() {
        stored.deleteAll();
        when(clock.instant()).thenReturn(NOW);
    }

    private Cookie tokenFor(Role role) {
        return new Cookie(JwtAuthenticationFilter.COOKIE_NAME, jwt.generateToken("someone", Set.of(role)));
    }

    private static String fill(int orderId, int instrumentId, String side, String quantity, String price,
                               String filledAt) {
        return "{\"orderId\":" + orderId + ",\"accountId\":7,\"instrumentId\":" + instrumentId + ",\"side\":\"" + side
            + "\",\"quantity\":" + quantity + ",\"price\":" + price + ",\"filledAt\":\"" + filledAt
            + "\",\"quoteSource\":\"SIMULATED\",\"quoteTime\":\"" + filledAt + "\"}";
    }

    @Test
    void fillsOnKafkaAreSummedPerStock() throws Exception {
        listener.onFilled(fill(41, 5, "BUY", "10", "200", "2026-10-08T14:00:00Z"));
        listener.onFilled(fill(42, 5, "SELL", "20", "210.5", "2026-10-08T14:10:00Z"));
        listener.onFilled(fill(43, 9, "BUY", "3", "50", "2026-10-08T14:20:00Z"));

        mvc.perform(get(ACTIVITY).cookie(tokenFor(Role.CLIENT)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.windowHours").value(24))
            .andExpect(jsonPath("$.since").value("2026-10-07T14:30:00Z"))
            .andExpect(jsonPath("$.activity.length()").value(2))
            .andExpect(jsonPath("$.activity[0].instrumentId").value(5))
            .andExpect(jsonPath("$.activity[0].trades").value(2))
            .andExpect(jsonPath("$.activity[0].volume").value(30.0))
            // 10 x 200 + 20 x 210.5
            .andExpect(jsonPath("$.activity[0].turnover").value(6210.0))
            .andExpect(jsonPath("$.activity[0].lastTradedAt").value("2026-10-08T14:10:00Z"))
            .andExpect(jsonPath("$.activity[1].instrumentId").value(9))
            .andExpect(jsonPath("$.activity[1].trades").value(1))
            .andExpect(jsonPath("$.activity[1].volume").value(3.0))
            .andExpect(jsonPath("$.activity[1].turnover").value(150.0));
    }

    @Test
    void aFillOlderThanTheWindowIsLeftOutAndOneOnItsEdgeIsCounted() throws Exception {
        listener.onFilled(fill(41, 5, "BUY", "10", "200", "2026-10-07T14:29:59Z"));
        listener.onFilled(fill(42, 5, "BUY", "20", "200", "2026-10-07T14:30:00Z"));

        mvc.perform(get(ACTIVITY).cookie(tokenFor(Role.CLIENT)))
            .andExpect(jsonPath("$.activity.length()").value(1))
            .andExpect(jsonPath("$.activity[0].trades").value(1))
            .andExpect(jsonPath("$.activity[0].volume").value(20.0));
    }

    /** Kafka delivers at least once, so the same record can arrive again. */
    @Test
    void theSameRecordDeliveredTwiceIsCountedOnce() throws Exception {
        String record = fill(42, 5, "BUY", "10", "200", "2026-10-08T14:00:00Z");
        listener.onFilled(record);
        listener.onFilled(record);

        assertEquals(1, stored.count());
        mvc.perform(get(ACTIVITY).cookie(tokenFor(Role.CLIENT)))
            .andExpect(jsonPath("$.activity[0].trades").value(1))
            .andExpect(jsonPath("$.activity[0].volume").value(10.0));
    }

    /** Activity is about the market, so it must never say who traded. */
    @Test
    void theAnswerNamesNoAccountAndNoOrder() throws Exception {
        listener.onFilled(fill(42, 5, "BUY", "10", "200", "2026-10-08T14:00:00Z"));

        mvc.perform(get(ACTIVITY).cookie(tokenFor(Role.CLIENT)))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("accountId"))))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("orderId"))));
    }

    @Test
    void aQuietMarketIsAnEmptyList() throws Exception {
        mvc.perform(get(ACTIVITY).cookie(tokenFor(Role.CLIENT)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.activity.length()").value(0));
    }

    /** Clients and staff alike: it is the market's activity, not anybody's own. */
    @ParameterizedTest
    @EnumSource(Role.class)
    void everySignedInRoleMayReadIt(Role role) throws Exception {
        mvc.perform(get(ACTIVITY).cookie(tokenFor(role))).andExpect(status().isOk());
    }

    @Test
    void withoutATokenIsUnauthorized() throws Exception {
        mvc.perform(get(ACTIVITY)).andExpect(status().isUnauthorized());
    }

    @Test
    void pathsOutsideMarketActivityAreClosed() throws Exception {
        mvc.perform(get("/api/v1/orders")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/orders").cookie(tokenFor(Role.CLIENT))).andExpect(status().isForbidden());
    }

    @Test
    void healthIsOpenSoTheSmokeTestCanReadIt() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
