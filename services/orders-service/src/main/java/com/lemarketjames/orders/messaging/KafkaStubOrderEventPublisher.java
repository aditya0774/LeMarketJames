package com.lemarketjames.orders.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Stands in for the Kafka producer where no broker is running: it logs the record it would have
 * sent and sends nothing. It is the default, so unit tests and a stack started without Kafka need
 * no broker; {@link KafkaOrderEventPublisher} replaces it when
 * {@value OrderEventPublisher#PUBLISHER_PROPERTY} is {@value OrderEventPublisher#KAFKA}.
 */
@Component
@ConditionalOnProperty(name = OrderEventPublisher.PUBLISHER_PROPERTY, havingValue = OrderEventPublisher.STUB,
    matchIfMissing = true)
public class KafkaStubOrderEventPublisher implements OrderEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(KafkaStubOrderEventPublisher.class);

    @Override
    public void publish(String topic, String key, Object event) {
        log.info("Kafka stub: would publish topic={} key={} event={}", topic, key, event);
    }
}
