package com.lemarketjames.orders.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** Which publisher the {@value OrderEventPublisher#PUBLISHER_PROPERTY} setting gives the forwarder. No broker needed. */
class OrderEventPublisherSelectionTest {
    final ApplicationContextRunner context = new ApplicationContextRunner().withUserConfiguration(
        KafkaClients.class, KafkaStubOrderEventPublisher.class, KafkaOrderEventPublisher.class,
        OrderEventTopics.class, OrderEventForwarder.class);

    @Test void theStubIsUsedWhenNothingIsConfigured() {
        context.run(started -> {
            assertThat(started).getBean(OrderEventPublisher.class).isInstanceOf(KafkaStubOrderEventPublisher.class);
            // No topics to create, so nothing reaches for a broker at startup.
            assertThat(started).doesNotHaveBean(NewTopic.class);
        });
    }

    @Test void theStubCanBeNamedExplicitly() {
        context.withPropertyValues(OrderEventPublisher.PUBLISHER_PROPERTY + "=" + OrderEventPublisher.STUB)
            .run(started -> assertThat(started).getBean(OrderEventPublisher.class)
                .isInstanceOf(KafkaStubOrderEventPublisher.class));
    }

    @Test void kafkaReplacesTheStubAndDeclaresTheForwardersTopics() {
        context.withPropertyValues(OrderEventPublisher.PUBLISHER_PROPERTY + "=" + OrderEventPublisher.KAFKA)
            .run(started -> {
                assertThat(started).getBean(OrderEventPublisher.class).isInstanceOf(KafkaOrderEventPublisher.class);
                var topics = started.getBeansOfType(NewTopic.class).values();
                assertThat(topics.stream().map(NewTopic::name).collect(Collectors.toSet()))
                    .isEqualTo(Set.of(OrderEventForwarder.STATUS_CHANGED_TOPIC, OrderEventForwarder.FILLED_TOPIC));
                assertThat(topics).allSatisfy(topic -> {
                    assertThat(topic.numPartitions()).isEqualTo(OrderEventTopics.PARTITIONS);
                    assertThat(topic.replicationFactor()).isEqualTo((short) OrderEventTopics.REPLICAS);
                });
            });
    }

    /** Stands in for Spring Boot's Kafka and Jackson auto-configuration. */
    @Configuration
    static class KafkaClients {
        @Bean
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> kafkaTemplate() {
            return mock(KafkaTemplate.class);
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
