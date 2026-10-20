package com.lemarketjames.holdings.stream;

import com.lemarketjames.common.domain.AccountEntity;
import com.lemarketjames.common.domain.AccountRepository;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A settlement tells the account's portfolio streams only when it committed a change: once per
 * fill, and never for a replay, a refusal or a settlement that rolled back.
 *
 * <p>Its own H2 database, with the settlement test control switched on so a settlement can be made
 * to fail after everything is written and before the commit.
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:settlementStream;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "lmj.settlement.control-enabled=true"
})
@AutoConfigureMockMvc
class SettlementBalanceChangedIntegrationTest {

    private static final String SETTLE = "/internal/holdings/settle";
    private static final String FAULTS = "/internal/holdings/control/settlement-faults/";

    @Autowired MockMvc mvc;
    @Autowired AccountRepository accounts;
    @MockitoBean PortfolioStreamService streams;

    private int accountWithCash(String cash) {
        AccountEntity account = new AccountEntity();
        account.setClientId((int) accounts.count() + 1);
        account.setCashBalance(new BigDecimal(cash));
        account.setCurrency("USD");
        account.setTradingEnabled(true);
        account.setOpenedDate(LocalDate.now());
        return accounts.saveAndFlush(account).getAccountId();
    }

    private ResultActions buy(int accountId, int orderId, String quantity, String price) throws Exception {
        return mvc.perform(post(SETTLE).contentType(MediaType.APPLICATION_JSON).content(
            "{\"orderId\":" + orderId + ",\"accountId\":" + accountId + ",\"instrumentId\":1"
                + ",\"orderType\":\"BUY\",\"quantity\":" + quantity + ",\"pricePerUnit\":" + price + "}"));
    }

    @Test
    void aFillIsPublishedOnceWithTheNewCashAndItsReplayIsNot() throws Exception {
        int accountId = accountWithCash("1000");
        int orderId = accountId * 10;

        buy(accountId, orderId, "3", "100.50").andExpect(status().isOk());
        buy(accountId, orderId, "3", "100.50").andExpect(status().isOk());

        ArgumentCaptor<BalanceChanged> published = ArgumentCaptor.forClass(BalanceChanged.class);
        verify(streams).publish(published.capture());
        assertEquals(accountId, published.getValue().accountId());
        assertEquals(orderId, published.getValue().orderId());
        assertEquals(0, new BigDecimal("698.50").compareTo(published.getValue().cash()));
    }

    @Test
    void aRefusedSettlementPublishesNothing() throws Exception {
        int accountId = accountWithCash("100");

        buy(accountId, accountId * 10, "2", "50.01")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rejectionReason").value("INSUFFICIENT_CASH"));

        verify(streams, never()).publish(any());
    }

    @Test
    void aSettlementThatRollsBackPublishesNothing() throws Exception {
        int accountId = accountWithCash("1000");
        mvc.perform(put(FAULTS + accountId)).andExpect(status().isOk());
        try {
            // MockMvc hands the unhandled failure back as the exception (see SettlementControlIntegrationTest).
            assertThrows(ServletException.class, () -> buy(accountId, accountId * 10, "1", "100"));
        } finally {
            mvc.perform(delete(FAULTS + accountId));
        }

        verify(streams, never()).publish(any());
    }
}
