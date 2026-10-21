package com.lemarketjames.orders.repository;

import com.lemarketjames.orders.entity.InstrumentLocationRestriction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface InstrumentLocationRestrictionRepository
        extends JpaRepository<InstrumentLocationRestriction, InstrumentLocationRestriction.Key> {

    /** The locations whose residents may not trade this instrument; empty when it has no rule. */
    @Query("SELECT r.location FROM InstrumentLocationRestriction r WHERE r.instrumentId = :instrumentId")
    List<String> findLocationsByInstrumentId(@Param("instrumentId") Integer instrumentId);
}
