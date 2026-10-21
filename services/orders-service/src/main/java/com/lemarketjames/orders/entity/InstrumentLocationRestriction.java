package com.lemarketjames.orders.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;

/**
 * One location whose residents may not trade one instrument (BR-05, database/schema/028). Only read
 * here: the rules are configuration, maintained by migrations, and the services may only select them.
 */
@Entity
@Table(name = "instrument_location_restrictions")
@IdClass(InstrumentLocationRestriction.Key.class)
public class InstrumentLocationRestriction {

    @Id
    @Column(name = "instrument_id", nullable = false)
    private Integer instrumentId;

    /** A US state name as stored in addresses.state, or an ISO country code. */
    @Id
    @Column(name = "location", nullable = false, length = 50)
    private String location;

    protected InstrumentLocationRestriction() {
    }

    public InstrumentLocationRestriction(Integer instrumentId, String location) {
        this.instrumentId = instrumentId;
        this.location = location;
    }

    public Integer getInstrumentId() {
        return instrumentId;
    }

    public String getLocation() {
        return location;
    }

    /** The composite primary key (instrument_id, location). */
    public static class Key implements Serializable {
        private Integer instrumentId;
        private String location;

        public Key() {
        }

        public Key(Integer instrumentId, String location) {
            this.instrumentId = instrumentId;
            this.location = location;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Key key
                && Objects.equals(instrumentId, key.instrumentId)
                && Objects.equals(location, key.location);
        }

        @Override
        public int hashCode() {
            return Objects.hash(instrumentId, location);
        }
    }
}
