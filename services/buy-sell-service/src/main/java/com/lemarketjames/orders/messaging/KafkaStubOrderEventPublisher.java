package com.lemarketjames.orders.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Stands in for a Kafka producer until a broker exists: it logs the record it would have sent and
 * sends nothing. Replace it with a KafkaTemplate-backed {@link OrderEventPublisher} to go live.
 */
@Component
public class KafkaStubOrderEventPublisher implements OrderEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(KafkaStubOrderEventPublisher.class);

    @Override
    public void publish(String topic, String key, Object event) {
        log.info("Kafka stub: would publish topic={} key={} event={}", topic, key, event);
    }
}
