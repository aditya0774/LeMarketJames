package com.lemarketjames.market.repository;

import com.lemarketjames.market.entity.PriceCandleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PriceCandleRepository extends JpaRepository<PriceCandleEntity, PriceCandleEntity.Key> {

    /** One instrument's 1-minute candles starting at or after {@code from} (UTC), oldest first. */
    List<PriceCandleEntity> findByInstrumentIdAndIntervalStartGreaterThanEqualOrderByIntervalStart(
            Integer instrumentId, LocalDateTime from);
}
