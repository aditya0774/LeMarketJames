package com.lemarketjames.surveillance;

import com.lemarketjames.common.events.OrderEventConsumerSwitch;
import com.lemarketjames.common.events.OrderEventReader;
import com.lemarketjames.common.events.OrderEventTopicNames;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * The Kafka side of this service: receives every newly placed order (contract C6) and hands it to
 * {@link SurveillanceService}. It exists only when the consumer is switched on, so a stack
 * without a broker starts no listener and contacts nothing.
 */
@Component
@ConditionalOnProperty(name = OrderEventConsumerSwitch.CONSUMER_PROPERTY, havingValue = OrderEventConsumerSwitch.KAFKA)
public class OrderSubmittedListener {

    /**
     * This service's consumer group. Every instance of the service shares it, so each order is
     * reviewed once however many instances run, and the group's committed position is where the
     * service carries on after a restart.
     */
    public static final String GROUP_ID = "surveillance-service";

    private final OrderEventReader reader;
    private final SurveillanceService surveillance;

    public OrderSubmittedListener(OrderEventReader reader, SurveillanceService surveillance) {
        this.reader = reader;
        this.surveillance = surveillance;
    }

    /** @param value the record value: an {@code OrderSubmitted} as JSON */
    @KafkaListener(topics = OrderEventTopicNames.SUBMITTED, groupId = GROUP_ID)
    public void onSubmitted(String value) {
        reader.read(OrderEventTopicNames.SUBMITTED, value, OrderSubmittedMessage.class)
            .ifPresent(surveillance::review);
    }
}
