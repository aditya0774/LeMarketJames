package com.lemarketjames.watchlist;

import com.lemarketjames.common.domain.AccountRepository;
import com.lemarketjames.common.instruments.Instrument;
import com.lemarketjames.common.instruments.InstrumentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Locale;

/**
 * The authenticated client's watchlist (S41): the stocks they follow, scoped to their own account
 * from the JWT username so one client can never read or change another's.
 */
@Service
public class WatchlistService {

    public static final String ACCOUNT_NOT_FOUND = "ACCOUNT_NOT_FOUND";
    public static final String SYMBOL_NOT_FOUND = "SYMBOL_NOT_FOUND";
    public static final String ALREADY_WATCHED = "ALREADY_WATCHED";

    private final WatchlistRepository watchlist;
    private final AccountRepository accounts;
    private final InstrumentRepository instruments;
    private final Clock clock;

    @Autowired
    public WatchlistService(WatchlistRepository watchlist, AccountRepository accounts,
                            InstrumentRepository instruments) {
        this(watchlist, accounts, instruments, Clock.systemUTC());
    }

    WatchlistService(WatchlistRepository watchlist, AccountRepository accounts,
                     InstrumentRepository instruments, Clock clock) {
        this.watchlist = watchlist;
        this.accounts = accounts;
        this.instruments = instruments;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<WatchlistItem> list(String username) {
        return watchlist.findItemsByAccountId(accountId(username));
    }

    @Transactional
    public WatchlistItem add(String username, String symbol) {
        Integer accountId = accountId(username);
        Instrument instrument = instrument(symbol);
        if (watchlist.existsByAccountIdAndInstrumentId(accountId, instrument.getInstrumentId())) {
            throw alreadyWatched(instrument);
        }
        try {
            WatchlistEntry saved = watchlist.saveAndFlush(
                    new WatchlistEntry(accountId, instrument.getInstrumentId(), clock.instant()));
            return new WatchlistItem(instrument.getTicker(), saved.getAddedAt());
        } catch (DataIntegrityViolationException ex) {
            // Another request (a second tab) added the same stock between the check and the insert;
            // the unique constraint caught it, so the answer is the same as the check's.
            throw alreadyWatched(instrument);
        }
    }

    /** Removing a stock that is not on the list changes nothing, so a repeated remove is harmless. */
    @Transactional
    public void remove(String username, String symbol) {
        Integer accountId = accountId(username);
        instruments.findByTicker(normalize(symbol))
                .ifPresent(instrument -> watchlist.deleteByAccountIdAndInstrumentId(
                        accountId, instrument.getInstrumentId()));
    }

    private Integer accountId(String username) {
        return accounts.findAccountIdByUsername(username)
                .orElseThrow(() -> new WatchlistException(ACCOUNT_NOT_FOUND, "Account not found"));
    }

    private Instrument instrument(String symbol) {
        return instruments.findByTicker(normalize(symbol))
                .orElseThrow(() -> new WatchlistException(SYMBOL_NOT_FOUND, "Symbol not found"));
    }

    private static WatchlistException alreadyWatched(Instrument instrument) {
        return new WatchlistException(ALREADY_WATCHED, instrument.getTicker() + " is already on your watchlist");
    }

    /** Tickers are stored upper case; a blank or missing symbol matches nothing. */
    private static String normalize(String symbol) {
        return symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
    }
}
