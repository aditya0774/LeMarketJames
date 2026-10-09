package com.lemarketjames.common.events;

/**
 * The setting that decides whether a service listens to the order events on Kafka (contract C5).
 * It is the consuming side's counterpart of buy-sell-service's {@code lmj.events.publisher}.
 *
 * <p>Unset means the service does not listen and contacts no broker, so unit tests and a stack
 * started without Kafka (the native Windows scripts) need none; Docker Compose and Jenkins set it
 * to {@value #KAFKA}. A listener is switched with
 * {@code @ConditionalOnProperty(name = CONSUMER_PROPERTY, havingValue = KAFKA)}.
 */
public final class OrderEventConsumerSwitch {

    /** The property; as an environment variable, {@code LMJ_EVENTS_CONSUMER}. */
    public static final String CONSUMER_PROPERTY = "lmj.events.consumer";
    /** The value that switches listening on. */
    public static final String KAFKA = "kafka";

    private OrderEventConsumerSwitch() {
    }
}
