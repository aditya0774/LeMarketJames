package com.lemarketjames.holdings;

import com.lemarketjames.common.audit.AuditEventEntity;
import com.lemarketjames.common.audit.AuditEventRepository;
import com.lemarketjames.common.audit.AuditEventType;
import com.lemarketjames.common.domain.AccountEntity;
import com.lemarketjames.common.domain.AccountRepository;
import com.lemarketjames.holdings.entity.HoldingsEntity;
import com.lemarketjames.holdings.repository.HoldingsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code POST /internal/holdings/settle} as buy-sell-service calls it (contract C6, "Automatic
 * execution and recovery"), for a buy (LMKT-71): the request goes through the real controller,
 * validation, service and database (H2), and the answer is checked together with what it left in
 * the account, the holding and the audit trail. Its own H2 database, so its accounts and orders
 * are nobody else's.
 *
 * <p>No test sends a token: the endpoint is server-to-server and outside the gateways, so a call
 * that gets through at all proves it needs none.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:settlementApi;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class SettlementApiIntegrationTest {

    private static final String SETTLE = "/internal/holdings/settle";
    private static final int INSTRUMENT = 1;

    @Autowired MockMvc mvc;
    @Autowired AccountRepository accounts;
    @Autowired HoldingsRepository holdings;
    @Autowired AuditEventRepository auditEvents;

    private int accountWithCash(String cash) {
        AccountEntity account = new AccountEntity();
        account.setClientId((int) accounts.count() + 1);
        account.setCashBalance(new BigDecimal(cash));
        account.setCurrency("USD");
        account.setTradingEnabled(true);
        account.setOpenedDate(LocalDate.now());
        return accounts.saveAndFlush(account).getAccountId();
    }

    /** The request body in the publisher's own format: numbers as JSON numbers, the side by name. */
    private static String buy(int accountId, int orderId, String quantity, String price) {
        return "{\"orderId\":" + orderId + ",\"accountId\":" + accountId + ",\"instrumentId\":" + INSTRUMENT
            + ",\"orderType\":\"BUY\",\"quantity\":" + quantity + ",\"pricePerUnit\":" + price + "}";
    }

    private ResultActions settle(String body) throws Exception {
        return mvc.perform(post(SETTLE).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private BigDecimal cashOf(int accountId) {
        return accounts.findById(accountId).orElseThrow().getCashBalance();
    }

    private List<AuditEventEntity> settledEventsOf(int orderId) {
        return auditEvents.findByOrderIdOrderByOccurredAtAsc(orderId).stream()
            .filter(event -> event.getEventType() == AuditEventType.SETTLED)
            .toList();
    }

    private static void assertAmount(String expected, Object actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(new BigDecimal(actual.toString())),
            "expected " + expected + " but was " + actual);
    }

    /** AC1 and AC5: cash down by quantity x price, the holding up by the quantity, and both audited. */
    @Test
    void aBuyIsSettledAndAudited() throws Exception {
        int accountId = accountWithCash("1000");
        int orderId = accountId * 10;

        settle(buy(accountId, orderId, "3", "100.50"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rejectionReason").doesNotExist());

        assertAmount("698.50", cashOf(accountId));
        HoldingsEntity bought = holdings.findByAccountIdAndInstrumentId(accountId, INSTRUMENT).orElseThrow();
        assertAmount("3", bought.getQuantity());
        assertAmount("100.50", bought.getAverageCost());

        List<AuditEventEntity> settled = settledEventsOf(orderId);
        assertEquals(1, settled.size());
        assertEquals(accountId, settled.get(0).getAccountId());
        assertAmount("-301.50", settled.get(0).getDetails().get("cashDelta"));
        assertAmount("3", settled.get(0).getDetails().get("quantityDelta"));
    }

    @Test
    void aSecondBuyAddsToTheHolding() throws Exception {
        int accountId = accountWithCash("1000");

        settle(buy(accountId, accountId * 10, "1", "100")).andExpect(status().isOk());
        settle(buy(accountId, accountId * 10 + 1, "1", "200")).andExpect(status().isOk());

        assertAmount("700", cashOf(accountId));
        HoldingsEntity bought = holdings.findByAccountIdAndInstrumentId(accountId, INSTRUMENT).orElseThrow();
        assertAmount("2", bought.getQuantity());
        assertAmount("150", bought.getAverageCost());
    }

    /** AC3: buy-sell-service repeats a request whose answer it lost; the fill is applied once. */
    @Test
    void theSameRequestTwiceIsAppliedOnceAndAnswersTheSame() throws Exception {
        int accountId = accountWithCash("1000");
        int orderId = accountId * 10;
        String request = buy(accountId, orderId, "3", "100.50");

        settle(request).andExpect(status().isOk()).andExpect(jsonPath("$.rejectionReason").doesNotExist());
        settle(request).andExpect(status().isOk()).andExpect(jsonPath("$.rejectionReason").doesNotExist());

        assertAmount("698.50", cashOf(accountId));
        assertAmount("3", holdings.findByAccountIdAndInstrumentId(accountId, INSTRUMENT).orElseThrow().getQuantity());
        assertEquals(1, settledEventsOf(orderId).size());
    }

    /** AC4: a buy the account can no longer pay for is refused, not applied; cash never goes below zero. */
    @Test
    void aBuyThatWouldMakeCashNegativeIsRefusedAndChangesNothing() throws Exception {
        int accountId = accountWithCash("100");
        int orderId = accountId * 10;
        String request = buy(accountId, orderId, "2", "50.01");

        settle(request)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rejectionReason").value("INSUFFICIENT_CASH"));

        assertAmount("100", cashOf(accountId));
        assertTrue(holdings.findByAccountIdAndInstrumentId(accountId, INSTRUMENT).isEmpty());
        assertTrue(settledEventsOf(orderId).isEmpty());

        // The refusal is the order's outcome: asked again, the answer is the same.
        settle(request).andExpect(jsonPath("$.rejectionReason").value("INSUFFICIENT_CASH"));
        assertAmount("100", cashOf(accountId));
    }

    @Test
    void aBuyThatTakesExactlyAllTheCashIsSettled() throws Exception {
        int accountId = accountWithCash("100");

        settle(buy(accountId, accountId * 10, "2", "50"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rejectionReason").doesNotExist());

        assertAmount("0", cashOf(accountId));
    }

    /** An order is settled at one price: the same order sent again with other figures is a conflict. */
    @Test
    void aRepeatedOrderWithADifferentPayloadIsAConflict() throws Exception {
        int accountId = accountWithCash("1000");
        int orderId = accountId * 10;
        settle(buy(accountId, orderId, "3", "100.50")).andExpect(status().isOk());

        settle(buy(accountId, orderId, "3", "99")).andExpect(status().isConflict());
        settle(buy(accountId, orderId, "4", "100.50")).andExpect(status().isConflict());

        assertAmount("698.50", cashOf(accountId));
        assertEquals(1, settledEventsOf(orderId).size());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{}",
        "{\"orderId\":1,\"accountId\":1,\"instrumentId\":1,\"orderType\":\"BUY\",\"quantity\":1}",
        "{\"orderId\":1,\"accountId\":1,\"instrumentId\":1,\"orderType\":\"BUY\",\"quantity\":0,\"pricePerUnit\":100}",
        "{\"orderId\":1,\"accountId\":1,\"instrumentId\":1,\"orderType\":\"BUY\",\"quantity\":1,\"pricePerUnit\":-100}",
        "{\"orderId\":1,\"accountId\":1,\"instrumentId\":1,\"orderType\":\"BUY\",\"quantity\":1.00001,\"pricePerUnit\":100}",
        "{\"orderId\":1,\"accountId\":1,\"instrumentId\":1,\"orderType\":\"HOLD\",\"quantity\":1,\"pricePerUnit\":100}",
        "not json"
    })
    void aMalformedRequestIsABadRequest(String body) throws Exception {
        settle(body).andExpect(status().isBadRequest());
    }
}
