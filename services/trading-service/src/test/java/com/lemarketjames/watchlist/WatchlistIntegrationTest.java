package com.lemarketjames.watchlist;

import com.lemarketjames.common.domain.AccountEntity;
import com.lemarketjames.common.domain.AccountRepository;
import com.lemarketjames.common.domain.AccountStatus;
import com.lemarketjames.common.domain.ClientEntity;
import com.lemarketjames.common.domain.ClientRepository;
import com.lemarketjames.common.instruments.InstrumentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Database proof of the watchlist (S41): the list is per account, newest first, stays after a
 * remove of something else, and the unique constraint refuses a duplicate even past the service.
 */
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:watchlistIntegration;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
class WatchlistIntegrationTest {

    private static final AtomicInteger IDS = new AtomicInteger();

    @Autowired private ClientRepository clients;
    @Autowired private AccountRepository accounts;
    @Autowired private InstrumentRepository instruments;
    @Autowired private WatchlistRepository watchlist;
    @Autowired private WatchlistService service;

    @Test
    void addListAndRemoveAreScopedToTheCallerAndNewestFirst() {
        String alice = client();
        String bob = client();

        service.add(alice, "AAPL");
        service.add(alice, "msft");
        service.add(bob, "NVDA");

        assertEquals(List.of("MSFT", "AAPL"), symbols(alice));
        assertEquals(List.of("NVDA"), symbols(bob));

        service.remove(alice, "AAPL");
        service.remove(alice, "AAPL");

        assertEquals(List.of("MSFT"), symbols(alice));
        assertEquals(List.of("NVDA"), symbols(bob));
    }

    @Test
    void addingTheSameStockTwiceIsAlreadyWatchedAndKeepsOneRow() {
        String alice = client();
        service.add(alice, "AAPL");

        WatchlistException ex = assertThrows(WatchlistException.class, () -> service.add(alice, "AAPL"));

        assertEquals(WatchlistService.ALREADY_WATCHED, ex.getCode());
        assertEquals(List.of("AAPL"), symbols(alice));
    }

    @Test
    void theUniqueConstraintRefusesADuplicateThatSkipsTheCheck() {
        String alice = client();
        Integer accountId = accounts.findAccountIdByUsername(alice).orElseThrow();
        Integer aapl = instruments.findByTicker("AAPL").orElseThrow().getInstrumentId();
        watchlist.saveAndFlush(new WatchlistEntry(accountId, aapl, Instant.now()));

        assertThrows(DataIntegrityViolationException.class,
                () -> watchlist.saveAndFlush(new WatchlistEntry(accountId, aapl, Instant.now())));
    }

    private List<String> symbols(String username) {
        return service.list(username).stream().map(WatchlistItem::symbol).toList();
    }

    private String client() {
        int suffix = IDS.incrementAndGet();
        String username = "watch_client_" + suffix;
        ClientEntity client = new ClientEntity();
        client.setUsername(username);
        client.setPassword("unused");
        client.setEmail(username + "@test.invalid");
        client.setFullName("Watch Client " + suffix);
        client.setDateOfBirth(LocalDate.of(1990, 1, 1));
        client.setPhone("555100" + suffix);
        client.setRegisteredDate(LocalDateTime.now());
        client.setSsn("unused-w" + suffix);
        client.setEmploymentStatus("EMPLOYED");
        client.setAccountStatus(AccountStatus.ACTIVE);
        client.setInvestmentExperience("BEGINNER");
        client = clients.saveAndFlush(client);

        AccountEntity account = new AccountEntity();
        account.setClientId(client.getClientId());
        account.setCashBalance(new BigDecimal("1000.00"));
        account.setCurrency("USD");
        account.setTradingEnabled(true);
        account.setOpenedDate(LocalDate.now());
        accounts.saveAndFlush(account);
        return username;
    }
}
