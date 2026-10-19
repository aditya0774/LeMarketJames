package com.lemarketjames.surveillance;

import com.lemarketjames.common.events.OrderEventConsumerSwitch;
import com.lemarketjames.common.events.OrderEventReader;
import com.lemarketjames.common.events.OrderEventTopicNames;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * The second Kafka listener of this service: receives every order status change (contract C6) and
 * hands it to {@link SurveillanceService}, which alerts Trading Operations when a settlement was
 * refused. Like {@link OrderSubmittedListener} it exists only when the consumer is switched on.
 *
 * <p>It is a class of its own so that each listener reads one topic, and it joins the same
 * consumer group: the group is the service, whichever of its topics a record comes from.
 */
@Component
@ConditionalOnProperty(name = OrderEventConsumerSwitch.CONSUMER_PROPERTY, havingValue = OrderEventConsumerSwitch.KAFKA)
public class OrderStatusChangedListener {

    private final OrderEventReader reader;
    private final SurveillanceService surveillance;

    public OrderStatusChangedListener(OrderEventReader reader, SurveillanceService surveillance) {
        this.reader = reader;
        this.surveillance = surveillance;
    }

    /** @param value the record value: an {@code OrderStatusChanged} as JSON */
    @KafkaListener(topics = OrderEventTopicNames.STATUS_CHANGED, groupId = OrderSubmittedListener.GROUP_ID)
    public void onStatusChanged(String value) {
        reader.read(OrderEventTopicNames.STATUS_CHANGED, value, OrderStatusChangedMessage.class)
            .ifPresent(surveillance::reviewStatusChange);
    }
}
