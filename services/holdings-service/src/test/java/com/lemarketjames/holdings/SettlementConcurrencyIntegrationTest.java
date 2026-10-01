package com.lemarketjames.holdings;

import com.lemarketjames.common.domain.*;
import com.lemarketjames.holdings.dto.SettlementRequest;
import com.lemarketjames.holdings.repository.*;
import com.lemarketjames.holdings.service.HoldingsSettlementService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:settlementConcurrency;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
class SettlementConcurrencyIntegrationTest {
    @Autowired AccountRepository accounts;
    @Autowired HoldingsRepository holdings;
    @Autowired SettlementReceiptRepository receipts;
    @Autowired HoldingsSettlementService settlement;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    int account() {
        AccountEntity a = new AccountEntity(); a.setClientId((int)accounts.count() + 1);
        a.setCashBalance(new BigDecimal("150")); a.setCurrency("USD"); a.setTradingEnabled(true); a.setOpenedDate(LocalDate.now());
        return accounts.saveAndFlush(a).getAccountId();
    }
    SettlementRequest request(int account, int id) {
        var r = new SettlementRequest(); r.setOrderId(id); r.setAccountId(account); r.setInstrumentId(1);
        r.setOrderType(SettlementRequest.OrderType.BUY); r.setQuantity(BigDecimal.ONE); r.setPricePerUnit(new BigDecimal("100"));
        return r;
    }
    @Test void concurrentBuysCannotSpendTheSameCash() throws Exception {
        int id = account(); var first = request(id, id * 10); var second = request(id, id * 10 + 1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            var a = pool.submit(() -> { start.await(); return settlement.settle(first); });
            var b = pool.submit(() -> { start.await(); return settlement.settle(second); });
            start.countDown(); String left = a.get(10, TimeUnit.SECONDS), right = b.get(10, TimeUnit.SECONDS);
            assertTrue((left == null && "INSUFFICIENT_CASH".equals(right)) || (right == null && "INSUFFICIENT_CASH".equals(left)));
        }
        assertEquals(0, new BigDecimal("50").compareTo(accounts.findById(id).orElseThrow().getCashBalance()));
        assertEquals(0, BigDecimal.ONE.compareTo(holdings.findByAccountIdAndInstrumentId(id, 1).orElseThrow().getQuantity()));
    }
    @Test void duplicateSettlementHasOneEffectAndPayloadCannotChange() throws Exception {
        int id = account(); var request = request(id, id * 10);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> settlement.settle(request)); var b = pool.submit(() -> settlement.settle(request));
            assertNull(a.get(10, TimeUnit.SECONDS)); assertNull(b.get(10, TimeUnit.SECONDS));
        }
        assertEquals(0, new BigDecimal("50").compareTo(accounts.findById(id).orElseThrow().getCashBalance()));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE order_id=? AND action='SETTLED'", Integer.class, request.getOrderId()));
        request.setPricePerUnit(BigDecimal.ONE);
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> settlement.settle(request));
    }
    @Test void concurrentSellsCannotSellTheSameShareTwice() throws Exception {
        int id = account();
        assertNull(settlement.settle(request(id, id * 10)));
        var first = request(id, id * 10 + 1); first.setOrderType(SettlementRequest.OrderType.SELL);
        var second = request(id, id * 10 + 2); second.setOrderType(SettlementRequest.OrderType.SELL);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> settlement.settle(first));
            var b = pool.submit(() -> settlement.settle(second));
            String left = a.get(10, TimeUnit.SECONDS), right = b.get(10, TimeUnit.SECONDS);
            assertTrue((left == null && "INSUFFICIENT_HOLDINGS".equals(right))
                || (right == null && "INSUFFICIENT_HOLDINGS".equals(left)));
        }
        assertTrue(holdings.findByAccountIdAndInstrumentId(id, 1).isEmpty());
        assertEquals(0, new BigDecimal("150").compareTo(accounts.findById(id).orElseThrow().getCashBalance()));
    }
    @Test void rejectedOutcomeStaysRejectedOnRetryEvenAfterCashChanges() {
        int id = account(); var request = request(id, id * 10); request.setQuantity(BigDecimal.TEN);
        assertEquals("INSUFFICIENT_CASH", settlement.settle(request));
        var a = accounts.findById(id).orElseThrow(); a.setCashBalance(new BigDecimal("10000")); accounts.saveAndFlush(a);
        assertEquals("INSUFFICIENT_CASH", settlement.settle(request));
        assertTrue(holdings.findByAccountIdAndInstrumentId(id, 1).isEmpty());
    }
}
