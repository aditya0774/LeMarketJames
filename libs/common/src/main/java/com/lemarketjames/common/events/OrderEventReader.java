package com.lemarketjames.common.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Reads the value of an order event record (contract C6) for a consuming service. The value is
 * plain JSON with no type headers, so each consumer reads it into a record of its own that names
 * only the fields it uses; fields it does not name are ignored, which lets the publisher add one
 * without breaking anybody.
 *
 * <p>A value that is not valid JSON can never be read, however often it is delivered again, so
 * it is logged and skipped instead of failing the listener and blocking the records behind it.
 */
@Component
public class OrderEventReader {
    private static final Logger log = LoggerFactory.getLogger(OrderEventReader.class);

    private final ObjectMapper json;

    public OrderEventReader(ObjectMapper json) {
        this.json = json;
    }

    /**
     * @param topic the topic the record came from, for the log
     * @param value the record value
     * @param type  the consumer's own shape of the event
     * @return the event, or empty when the value could not be read and was skipped
     */
    public <T> Optional<T> read(String topic, String value, Class<T> type) {
        if (value == null || value.isBlank()) {
            log.warn("Kafka: skipped an empty record on topic={}", topic);
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(json.readValue(value, type));
        } catch (JsonProcessingException unreadable) {
            log.error("Kafka: skipped an unreadable record on topic={} value={}", topic, value, unreadable);
            return Optional.empty();
        }
    }
}
