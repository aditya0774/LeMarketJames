package com.lemarketjames.watchlist;

import com.lemarketjames.common.domain.AccountRepository;
import com.lemarketjames.common.instruments.Instrument;
import com.lemarketjames.common.instruments.InstrumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WatchlistServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-20T14:00:00Z");

    private final WatchlistRepository watchlist = mock(WatchlistRepository.class);
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final InstrumentRepository instruments = mock(InstrumentRepository.class);
    private final WatchlistService service = new WatchlistService(
            watchlist, accounts, instruments, Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach
    void aliceHasAccountSevenAndAaplIsInstrumentThree() {
        when(accounts.findAccountIdByUsername("alice")).thenReturn(Optional.of(7));
        when(instruments.findByTicker("AAPL")).thenReturn(Optional.of(instrument(3, "AAPL")));
        when(watchlist.saveAndFlush(any())).thenAnswer(save -> save.getArgument(0));
    }

    @Test
    void listReadsOnlyTheCallersAccount() {
        List<WatchlistItem> items = List.of(new WatchlistItem("AAPL", NOW));
        when(watchlist.findItemsByAccountId(7)).thenReturn(items);

        assertEquals(items, service.list("alice"));
    }

    @Test
    void addStoresTheStockAgainstTheCallersAccountAndReturnsItsTicker() {
        WatchlistItem item = service.add("alice", " aapl ");

        assertEquals(new WatchlistItem("AAPL", NOW), item);
        verify(watchlist).saveAndFlush(any(WatchlistEntry.class));
    }

    @Test
    void addingAnUnknownSymbolIsSymbolNotFound() {
        when(instruments.findByTicker("NOPE")).thenReturn(Optional.empty());

        WatchlistException ex = assertThrows(WatchlistException.class, () -> service.add("alice", "NOPE"));

        assertEquals(WatchlistService.SYMBOL_NOT_FOUND, ex.getCode());
        assertEquals("Symbol not found", ex.getMessage());
        verify(watchlist, never()).saveAndFlush(any());
    }

    @Test
    void addingAMissingSymbolIsSymbolNotFound() {
        when(instruments.findByTicker("")).thenReturn(Optional.empty());

        WatchlistException ex = assertThrows(WatchlistException.class, () -> service.add("alice", null));

        assertEquals(WatchlistService.SYMBOL_NOT_FOUND, ex.getCode());
    }

    @Test
    void addingAStockAlreadyWatchedIsAlreadyWatched() {
        when(watchlist.existsByAccountIdAndInstrumentId(7, 3)).thenReturn(true);

        WatchlistException ex = assertThrows(WatchlistException.class, () -> service.add("alice", "AAPL"));

        assertEquals(WatchlistService.ALREADY_WATCHED, ex.getCode());
        verify(watchlist, never()).saveAndFlush(any());
    }

    @Test
    void aConcurrentDuplicateCaughtByTheConstraintIsAlsoAlreadyWatched() {
        when(watchlist.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));

        WatchlistException ex = assertThrows(WatchlistException.class, () -> service.add("alice", "AAPL"));

        assertEquals(WatchlistService.ALREADY_WATCHED, ex.getCode());
    }

    @Test
    void removeDeletesTheCallersRow() {
        service.remove("alice", "aapl");

        verify(watchlist).deleteByAccountIdAndInstrumentId(7, 3);
    }

    @Test
    void removingAnUnknownSymbolChangesNothingAndDoesNotFail() {
        when(instruments.findByTicker("NOPE")).thenReturn(Optional.empty());

        service.remove("alice", "NOPE");

        verify(watchlist, never()).deleteByAccountIdAndInstrumentId(any(), any());
    }

    @Test
    void aLoginWithoutAnAccountIsAccountNotFound() {
        when(accounts.findAccountIdByUsername("ops")).thenReturn(Optional.empty());

        assertEquals(WatchlistService.ACCOUNT_NOT_FOUND,
                assertThrows(WatchlistException.class, () -> service.list("ops")).getCode());
        assertEquals(WatchlistService.ACCOUNT_NOT_FOUND,
                assertThrows(WatchlistException.class, () -> service.add("ops", "AAPL")).getCode());
        assertEquals(WatchlistService.ACCOUNT_NOT_FOUND,
                assertThrows(WatchlistException.class, () -> service.remove("ops", "AAPL")).getCode());
    }

    private static Instrument instrument(int id, String ticker) {
        Instrument instrument = new Instrument();
        instrument.setInstrumentId(id);
        instrument.setTicker(ticker);
        return instrument;
    }
}
