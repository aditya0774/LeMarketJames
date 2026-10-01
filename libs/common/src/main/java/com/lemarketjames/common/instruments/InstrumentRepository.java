package com.lemarketjames.common.instruments;

import com.lemarketjames.common.instruments.Instrument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InstrumentRepository extends JpaRepository<Instrument, Integer> {
    Optional<Instrument> findByTicker(String ticker);
}
