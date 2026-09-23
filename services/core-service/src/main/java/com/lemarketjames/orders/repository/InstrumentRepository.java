package com.lemarketjames.orders.repository;

import com.lemarketjames.orders.entity.Instrument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InstrumentRepository extends JpaRepository<Instrument, Integer> {
    Optional<Instrument> findByTicker(String ticker);
}
