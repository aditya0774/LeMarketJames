package com.lemarketjames.holdings;

import com.lemarketjames.common.domain.AccountEntity;
import com.lemarketjames.common.domain.AccountRepository;
import com.lemarketjames.holdings.entity.HoldingsEntity;
import com.lemarketjames.holdings.repository.HoldingsRepository;
import com.lemarketjames.holdings.repository.SettlementReceiptRepository;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The settlement test control (contract C5), which the end-to-end tests of LMKT-72 and LMKT-74 use
 * to make a settlement fail part-way on a running stack: while an account is named, its
 * settlements fail and leave nothing behind; other accounts settle as usual; once the account is
 * released, the same request settles exactly once.
 *
 * <p>The control is switched on for this test only. Its own H2 database, so its accounts and
 * orders are nobody else's.
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:settlementControl;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "lmj.settlement.control-enabled=true"
})
@AutoConfigureMockMvc
class SettlementControlIntegrationTest {

    private static final String SETTLE = "/internal/holdings/settle";
    private static final String FAULTS = "/internal/holdings/control/settlement-faults/";
    private static final int INSTRUMENT = 1;

    @Autowired MockMvc mvc;
    @Autowired AccountRepository accounts;
    @Autowired HoldingsRepository holdings;
    @Autowired SettlementReceiptRepository receipts;
    @Autowired JdbcTemplate jdbc;

    private int accountWithCash(String cash) {
        AccountEntity account = new AccountEntity();
        account.setClientId((int) accounts.count() + 1);
        account.setCashBalance(new BigDecimal(cash));
        account.setCurrency("USD");
        account.setTradingEnabled(true);
        account.setOpenedDate(LocalDate.now());
        return accounts.saveAndFlush(account).getAccountId();
    }

    private static String body(String side, int accountId, int orderId, String quantity, String price) {
        return "{\"orderId\":" + orderId + ",\"accountId\":" + accountId + ",\"instrumentId\":" + INSTRUMENT
            + ",\"orderType\":\"" + side + "\",\"quantity\":" + quantity + ",\"pricePerUnit\":" + price + "}";
    }

    private ResultActions settle(String body) throws Exception {
        return mvc.perform(post(SETTLE).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    /**
     * MockMvc has no servlet container to turn an unhandled exception into a 500, so the failure
     * arrives here as the exception itself. On a running service it is the 500 that
     * orders-service retries; the end-to-end tests see that.
     */
    private void assertSettlementFails(String body) {
        ServletException failure = assertThrows(ServletException.class, () -> settle(body));
        assertInstanceOf(IllegalStateException.class, failure.getCause());
    }

    private BigDecimal cashOf(int accountId) {
        return jdbc.queryForObject("SELECT cash_balance FROM accounts WHERE account_id = ?", BigDecimal.class, accountId);
    }

    private int settledEventsOf(int orderId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE order_id = ? AND action = 'SETTLED'",
            Integer.class, orderId);
    }

    private static void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected " + expected + " but was " + actual);
    }

    @Test
    void aNamedAccountsBuyFailsLeavesNothingAndSettlesOnceWhenReleased() throws Exception {
        int accountId = accountWithCash("1000");
        int orderId = accountId * 10;
        String request = body("BUY", accountId, orderId, "3", "100.50");

        mvc.perform(put(FAULTS + accountId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accountId").value(accountId))
            .andExpect(jsonPath("$.failing").value(true))
            .andExpect(jsonPath("$.failures").value(0));

        // Every attempt fails, as each of orders-service's retries would.
        assertSettlementFails(request);
        assertSettlementFails(request);

        mvc.perform(get(FAULTS + accountId)).andExpect(jsonPath("$.failures").value(2));
        assertAmount("1000", cashOf(accountId));
        assertTrue(holdings.findByAccountIdAndInstrumentId(accountId, INSTRUMENT).isEmpty());
        assertTrue(receipts.findById(orderId).isEmpty());
        assertEquals(0, settledEventsOf(orderId));

        mvc.perform(delete(FAULTS + accountId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.failing").value(false))
            .andExpect(jsonPath("$.failures").value(0));

        settle(request).andExpect(status().isOk()).andExpect(jsonPath("$.rejectionReason").doesNotExist());
        settle(request).andExpect(status().isOk()).andExpect(jsonPath("$.rejectionReason").doesNotExist());

        assertAmount("698.50", cashOf(accountId));
        assertAmount("3", holdings.findByAccountIdAndInstrumentId(accountId, INSTRUMENT).orElseThrow().getQuantity());
        assertTrue(receipts.findById(orderId).isPresent());
        assertEquals(1, settledEventsOf(orderId));
    }

    /** Selling every share deletes the holding, so here the rollback has to bring a deleted row back. */
    @Test
    void aNamedAccountsSellOfTheWholePositionFailsAndPutsTheHoldingBack() throws Exception {
        int accountId = accountWithCash("1000");
        holdings.saveAndFlush(new HoldingsEntity(accountId, INSTRUMENT, new BigDecimal("3"), new BigDecimal("80")));
        int orderId = accountId * 10;
        String request = body("SELL", accountId, orderId, "3", "100.50");

        mvc.perform(put(FAULTS + accountId)).andExpect(status().isOk());
        assertSettlementFails(request);

        assertAmount("1000", cashOf(accountId));
        HoldingsEntity untouched = holdings.findByAccountIdAndInstrumentId(accountId, INSTRUMENT).orElseThrow();
        assertAmount("3", untouched.getQuantity());
        assertAmount("80", untouched.getAverageCost());
        assertTrue(receipts.findById(orderId).isEmpty());
        assertEquals(0, settledEventsOf(orderId));

        mvc.perform(delete(FAULTS + accountId)).andExpect(status().isOk());
        settle(request).andExpect(status().isOk()).andExpect(jsonPath("$.rejectionReason").doesNotExist());

        assertAmount("1301.50", cashOf(accountId));
        assertTrue(holdings.findByAccountIdAndInstrumentId(accountId, INSTRUMENT).isEmpty());
        assertEquals(1, settledEventsOf(orderId));
    }

    /** The failure is per account: a test that names its own account breaks nobody else's orders. */
    @Test
    void anAccountThatIsNotNamedSettlesWhileAnotherFails() throws Exception {
        int failing = accountWithCash("1000");
        int other = accountWithCash("1000");
        mvc.perform(put(FAULTS + failing)).andExpect(status().isOk());
        try {
            settle(body("BUY", other, other * 10, "1", "100")).andExpect(status().isOk());

            assertAmount("900", cashOf(other));
            mvc.perform(get(FAULTS + other))
                .andExpect(jsonPath("$.failing").value(false))
                .andExpect(jsonPath("$.failures").value(0));
        } finally {
            mvc.perform(delete(FAULTS + failing));
        }
    }

    /** A refusal is not a settlement that was applied, so it is answered as usual, not failed. */
    @Test
    void aRefusedSettlementOfANamedAccountIsStillRefused() throws Exception {
        int accountId = accountWithCash("100");
        mvc.perform(put(FAULTS + accountId)).andExpect(status().isOk());
        try {
            settle(body("BUY", accountId, accountId * 10, "2", "50.01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rejectionReason").value("INSUFFICIENT_CASH"));

            mvc.perform(get(FAULTS + accountId)).andExpect(jsonPath("$.failures").value(0));
        } finally {
            mvc.perform(delete(FAULTS + accountId));
        }
    }
}
