package com.lemarketjames.market.repository;

import com.lemarketjames.market.entity.PriceCandleEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

/** {@link PriceCandleBatchInsert} on JDBC; Spring Data finds it by the {@code Impl} suffix. */
class PriceCandleBatchInsertImpl implements PriceCandleBatchInsert {

    // ON CONFLICT DO NOTHING without a target works in Postgres and in H2's PostgreSQL mode (tests).
    private static final String INSERT = "INSERT INTO price_candles "
            + "(instrument_id, interval_start, open_price, high_price, low_price, close_price, volume) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING";

    private final JdbcTemplate jdbc;

    PriceCandleBatchInsertImpl(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insertNew(List<PriceCandleEntity> candles) {
        if (candles.isEmpty()) {
            return;
        }
        jdbc.batchUpdate(INSERT, candles, candles.size(), (statement, candle) -> {
            statement.setInt(1, candle.getInstrumentId());
            statement.setObject(2, candle.getIntervalStart());
            statement.setBigDecimal(3, candle.getOpenPrice());
            statement.setBigDecimal(4, candle.getHighPrice());
            statement.setBigDecimal(5, candle.getLowPrice());
            statement.setBigDecimal(6, candle.getClosePrice());
            statement.setLong(7, candle.getVolume());
        });
    }
}
