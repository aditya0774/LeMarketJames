package com.lemarketjames.common.events;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Contract C6: a consumer reads an order event with a record of its own, and survives a bad record. */
class OrderEventReaderTest {

    /** What a consumer might keep of an OrderStatusChanged. */
    record StatusChange(Integer orderId, String to, Instant occurredAt) {
    }

    // Configured as Spring Boot configures the mapper every service injects.
    private final OrderEventReader reader = new OrderEventReader(new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));

    @Test
    void readsTheFieldsTheConsumerNamesAndIgnoresTheRest() {
        Optional<StatusChange> event = reader.read(OrderEventTopicNames.STATUS_CHANGED,
                "{\"orderId\":7,\"accountId\":3,\"from\":\"PENDING\",\"to\":\"FILLED\","
                        + "\"occurredAt\":\"2026-10-08T14:30:00Z\"}", StatusChange.class);

        assertEquals(Optional.of(new StatusChange(7, "FILLED", Instant.parse("2026-10-08T14:30:00Z"))), event);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "not json", "{\"orderId\":\"seven\"}", "null"})
    void aRecordThatCannotBeReadIsSkipped(String value) {
        assertEquals(Optional.empty(), reader.read(OrderEventTopicNames.STATUS_CHANGED, value, StatusChange.class));
    }

    @Test
    void theTopicsAreTheOnesInTheContract() {
        assertEquals("lemarket.orders.submitted", OrderEventTopicNames.SUBMITTED);
        assertEquals("lemarket.orders.status-changed", OrderEventTopicNames.STATUS_CHANGED);
        assertEquals("lemarket.orders.filled", OrderEventTopicNames.FILLED);
    }
}
