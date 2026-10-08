package com.lemarketjames.notifications;

import com.lemarketjames.common.events.OrderEventConsumerSwitch;
import com.lemarketjames.common.events.OrderEventReader;
import com.lemarketjames.common.events.OrderEventTopicNames;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * The Kafka side of this service: receives every order status change (contract C6) and hands it
 * to {@link NotificationService}. It exists only when the consumer is switched on, so a stack
 * without a broker starts no listener and contacts nothing.
 */
@Component
@ConditionalOnProperty(name = OrderEventConsumerSwitch.CONSUMER_PROPERTY, havingValue = OrderEventConsumerSwitch.KAFKA)
public class OrderStatusListener {

    /**
     * This service's consumer group. Every instance of the service shares it, so each status
     * change is handled once however many instances run, and the group's committed position is
     * where the service carries on after a restart.
     */
    public static final String GROUP_ID = "notification-service";

    private final OrderEventReader reader;
    private final NotificationService notifications;

    public OrderStatusListener(OrderEventReader reader, NotificationService notifications) {
        this.reader = reader;
        this.notifications = notifications;
    }

    /** @param value the record value: an {@code OrderStatusChanged} as JSON */
    @KafkaListener(topics = OrderEventTopicNames.STATUS_CHANGED, groupId = GROUP_ID)
    public void onStatusChanged(String value) {
        reader.read(OrderEventTopicNames.STATUS_CHANGED, value, OrderStatusChangedMessage.class)
            .ifPresent(notifications::record);
    }
}
