package com.lemarketjames.market.repository;

import com.lemarketjames.market.entity.PriceCandleEntity;

import java.util.List;

/**
 * Plain batched inserts for {@code price_candles}, mixed into {@link PriceCandleRepository}.
 *
 * <p>JPA's {@code saveAll} merges entities with an assigned composite key, which reads every row
 * before writing it. That is fine for a few candles every snapshot but slow for a day's worth at
 * startup (up to ~20k), so candles are written in one JDBC batch instead.
 */
public interface PriceCandleBatchInsert {

    /**
     * Inserts the candles in one batch. A minute already stored for that instrument is left as it
     * is: candles are final once complete, so a repeat is a duplicate, not a correction.
     */
    void insertNew(List<PriceCandleEntity> candles);
}
