package com.lemarketjames.activity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/** Reads and writes the {@code trade_activity} table. */
public interface RecordedFillRepository extends JpaRepository<RecordedFill, Integer> {

    /**
     * Sums the fills from a moment on, per stock. The database does the adding up, so the number
     * of fills in the window never becomes rows in memory.
     *
     * @param since the start of the window, included
     * @return one row per stock that traded, in instrument id order; a stock with no fill has none
     */
    @Query("""
            SELECT new com.lemarketjames.activity.InstrumentActivity(
                f.instrumentId, COUNT(f), SUM(f.quantity), SUM(f.quantity * f.price), MAX(f.filledAt))
            FROM RecordedFill f
            WHERE f.filledAt >= :since
            GROUP BY f.instrumentId
            ORDER BY f.instrumentId
            """)
    List<InstrumentActivity> summarizeSince(@Param("since") Instant since);
}
