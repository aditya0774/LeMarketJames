package com.lemarketjames.watchlist;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface WatchlistRepository extends JpaRepository<WatchlistEntry, Integer> {

    /** The account's watched stocks with their tickers, newest first (ties by insert order). */
    @Query("""
            SELECT new com.lemarketjames.watchlist.WatchlistItem(i.ticker, w.addedAt)
            FROM WatchlistEntry w, Instrument i
            WHERE i.instrumentId = w.instrumentId AND w.accountId = :accountId
            ORDER BY w.addedAt DESC, w.id DESC
            """)
    List<WatchlistItem> findItemsByAccountId(@Param("accountId") Integer accountId);

    boolean existsByAccountIdAndInstrumentId(Integer accountId, Integer instrumentId);

    long deleteByAccountIdAndInstrumentId(Integer accountId, Integer instrumentId);
}
