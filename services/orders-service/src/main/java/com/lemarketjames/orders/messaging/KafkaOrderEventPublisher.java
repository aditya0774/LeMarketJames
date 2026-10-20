package com.lemarketjames.orders.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes order events to Kafka (contract C6). The record value is the event as plain JSON, written
 * with the service's own {@link ObjectMapper}, so times are ISO-8601 as in the REST API and a consumer
 * in any service can read it without this service's classes.
 *
 * <p>Publishing is best effort. The forwarder calls this after the order transaction has committed,
 * so a broker problem must not fail an order that is already stored: a record that can't be sent is
 * logged and dropped. Nothing replays it later (there is no outbox).
 */
@Component
@ConditionalOnProperty(name = OrderEventPublisher.PUBLISHER_PROPERTY, havingValue = OrderEventPublisher.KAFKA)
public class KafkaOrderEventPublisher implements OrderEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(KafkaOrderEventPublisher.class);

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper json;

    public KafkaOrderEventPublisher(KafkaTemplate<String, String> kafka, ObjectMapper json) {
        this.kafka = kafka;
        this.json = json;
    }

    @Override
    public void publish(String topic, String key, Object event) {
        try {
            // The send is asynchronous: the broker's answer arrives on the producer's own thread.
            kafka.send(topic, key, json.writeValueAsString(event)).whenComplete((sent, failure) -> {
                if (failure != null) {
                    notPublished(topic, key, event, failure);
                } else {
                    log.info("Kafka: published topic={} key={} partition={} offset={}", topic, key,
                        sent.getRecordMetadata().partition(), sent.getRecordMetadata().offset());
                }
            });
        } catch (JsonProcessingException | RuntimeException failure) {
            // send() itself throws when the broker can't be reached within max.block.ms.
            notPublished(topic, key, event, failure);
        }
    }

    private static void notPublished(String topic, String key, Object event, Throwable failure) {
        log.error("Kafka: could not publish topic={} key={} event={}", topic, key, event, failure);
    }
}
