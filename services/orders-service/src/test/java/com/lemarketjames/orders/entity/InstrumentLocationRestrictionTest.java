package com.lemarketjames.orders.entity;

import com.lemarketjames.orders.entity.InstrumentLocationRestriction.Key;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** BR-05: one location barred from one instrument, keyed by both. */
class InstrumentLocationRestrictionTest {

    @Test
    void keepsTheInstrumentAndTheLocation() {
        InstrumentLocationRestriction rule = new InstrumentLocationRestriction(7, "New Jersey");

        assertEquals(7, rule.getInstrumentId());
        assertEquals("New Jersey", rule.getLocation());
    }

    @Test
    void keysWithTheSameInstrumentAndLocationAreEqual() {
        Key key = new Key(7, "GB");

        assertEquals(new Key(7, "GB"), key);
        assertEquals(new Key(7, "GB").hashCode(), key.hashCode());
        assertEquals(new Key(), new Key());
    }

    @Test
    void keysDifferWhenEitherPartDiffers() {
        Key key = new Key(7, "GB");

        assertNotEquals(new Key(8, "GB"), key);
        assertNotEquals(new Key(7, "FR"), key);
        assertNotEquals(null, key);
    }
}
