package com.lemarketjames.orders.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declares the order event topics, which Spring's KafkaAdmin creates on the broker at startup if
 * they are missing. The names come from {@link OrderEventForwarder}, so they are written in one
 * place; the broker's own auto-creation is off (docker-compose.yml), so a mistyped topic fails
 * instead of quietly appearing.
 */
@Configuration
@ConditionalOnProperty(name = OrderEventPublisher.PUBLISHER_PROPERTY, havingValue = OrderEventPublisher.KAFKA)
public class OrderEventTopics {
    /** More than one, so keying by order id matters: one order's events share a partition and keep their order. */
    static final int PARTITIONS = 3;
    /** The stack has a single broker. */
    static final int REPLICAS = 1;

    @Bean
    NewTopic orderSubmittedTopic() {
        return topic(OrderEventForwarder.SUBMITTED_TOPIC);
    }

    @Bean
    NewTopic orderStatusChangedTopic() {
        return topic(OrderEventForwarder.STATUS_CHANGED_TOPIC);
    }

    @Bean
    NewTopic orderFilledTopic() {
        return topic(OrderEventForwarder.FILLED_TOPIC);
    }

    private static NewTopic topic(String name) {
        return TopicBuilder.name(name).partitions(PARTITIONS).replicas(REPLICAS).build();
    }
}
