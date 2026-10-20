package com.lemarketjames.market.repository;

import com.lemarketjames.market.entity.PriceCandleEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The batched candle insert and the latest-candle lookup, against the (H2, PostgreSQL mode) test database. */
@SpringBootTest
class PriceCandleRepositoryTest {

    private static final LocalDateTime OPEN = LocalDateTime.of(2026, 9, 16, 13, 30);

    @Autowired
    private PriceCandleRepository repository;

    @AfterEach
    void cleanUp() {
        repository.deleteAll();
    }

    @Test
    void insertsInOneBatchAndIgnoresAMinuteAlreadyStored() {
        repository.insertNew(List.of(candle(1, OPEN, "100.00"), candle(1, OPEN.plusMinutes(1), "101.00")));

        // A repeat of a stored minute is left as it was.
        repository.insertNew(List.of(candle(1, OPEN.plusMinutes(1), "999.00"), candle(1, OPEN.plusMinutes(2), "102.00")));

        List<PriceCandleEntity> stored = repository.findByInstrumentIdAndIntervalStartGreaterThanEqualOrderByIntervalStart(1, OPEN);
        assertEquals(3, stored.size());
        assertEquals(0, new BigDecimal("101.00").compareTo(stored.get(1).getClosePrice()));
    }

    @Test
    void findsEachInstrumentsLatestCandleSinceATime() {
        repository.insertNew(List.of(candle(1, OPEN, "100.00"), candle(1, OPEN.plusMinutes(5), "100.00"),
                candle(2, OPEN.plusMinutes(3), "50.00"), candle(3, OPEN.minusDays(1), "10.00")));

        List<Object[]> latest = repository.findLatestIntervalStarts(OPEN);

        assertEquals(2, latest.size(), "instrument 3 has nothing since the open");
        for (Object[] row : latest) {
            int id = ((Number) row[0]).intValue();
            assertEquals(id == 1 ? OPEN.plusMinutes(5) : OPEN.plusMinutes(3), row[1]);
        }
    }

    private static PriceCandleEntity candle(int instrumentId, LocalDateTime start, String close) {
        BigDecimal price = new BigDecimal(close);
        return new PriceCandleEntity(instrumentId, start, price, price, price, price, 10L);
    }
}
