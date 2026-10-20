package com.lemarketjames.market.repository;

import com.lemarketjames.market.entity.PriceCandleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PriceCandleRepository
        extends JpaRepository<PriceCandleEntity, PriceCandleEntity.Key>, PriceCandleBatchInsert {

    /** One instrument's 1-minute candles starting at or after {@code from} (UTC), oldest first. */
    List<PriceCandleEntity> findByInstrumentIdAndIntervalStartGreaterThanEqualOrderByIntervalStart(
            Integer instrumentId, LocalDateTime from);

    /**
     * The start of each instrument's latest candle at or after {@code from} (UTC), as
     * {@code [instrumentId, intervalStart]} rows; instruments with none are absent. Startup reads
     * it to know which part of today's session is already stored.
     */
    @Query("SELECT c.instrumentId, MAX(c.intervalStart) FROM PriceCandleEntity c "
            + "WHERE c.intervalStart >= :from GROUP BY c.instrumentId")
    List<Object[]> findLatestIntervalStarts(@Param("from") LocalDateTime from);
}
