package com.lemarketjames.market.repository;

import com.lemarketjames.market.entity.PriceHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface PriceHistoryRepository extends JpaRepository<PriceHistoryEntity, PriceHistoryEntity.Key> {

    /** One instrument's daily bars from {@code from} onwards, oldest first. */
    List<PriceHistoryEntity> findByInstrumentIdAndTradingDateGreaterThanEqualOrderByTradingDate(
            Integer instrumentId, LocalDate from);
}
