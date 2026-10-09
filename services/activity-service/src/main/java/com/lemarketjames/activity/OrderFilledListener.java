package com.lemarketjames.activity;

import com.lemarketjames.common.events.OrderEventConsumerSwitch;
import com.lemarketjames.common.events.OrderEventReader;
import com.lemarketjames.common.events.OrderEventTopicNames;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * The Kafka side of this service: receives every fill (contract C6) and hands it to
 * {@link MarketActivityService}. It exists only when the consumer is switched on, so a stack
 * without a broker starts no listener and contacts nothing.
 */
@Component
@ConditionalOnProperty(name = OrderEventConsumerSwitch.CONSUMER_PROPERTY, havingValue = OrderEventConsumerSwitch.KAFKA)
public class OrderFilledListener {

    /**
     * This service's consumer group. Every instance of the service shares it, so each fill is
     * counted once however many instances run, and the group's committed position is where the
     * service carries on after a restart.
     */
    public static final String GROUP_ID = "activity-service";

    private final OrderEventReader reader;
    private final MarketActivityService activity;

    public OrderFilledListener(OrderEventReader reader, MarketActivityService activity) {
        this.reader = reader;
        this.activity = activity;
    }

    /** @param value the record value: an {@code OrderFilled} as JSON */
    @KafkaListener(topics = OrderEventTopicNames.FILLED, groupId = GROUP_ID)
    public void onFilled(String value) {
        reader.read(OrderEventTopicNames.FILLED, value, OrderFilledMessage.class)
            .ifPresent(activity::record);
    }
}
