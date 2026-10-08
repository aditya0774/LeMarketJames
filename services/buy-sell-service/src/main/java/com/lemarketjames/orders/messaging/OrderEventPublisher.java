package com.lemarketjames.orders.messaging;

/**
 * The outbound side of the order events (contract C6): hands an event to a message broker so other
 * services can react to it. Callers depend on this interface rather than on a broker client, so the
 * Kafka stub and the real producer can be swapped by configuration without touching them.
 */
public interface OrderEventPublisher {

    /**
     * The setting that picks the implementation: {@value #KAFKA} publishes to a Kafka broker,
     * {@value #STUB} only logs. Unset means the stub, so a stack without a broker (unit tests, the
     * native Windows scripts) starts without one; Docker Compose and Jenkins set it to Kafka.
     */
    String PUBLISHER_PROPERTY = "lmj.events.publisher";
    String KAFKA = "kafka";
    String STUB = "stub";

    /**
     * Publishes one event.
     *
     * @param topic the topic to publish to
     * @param key   the record key; events with the same key keep their order
     * @param event the event payload
     */
    void publish(String topic, String key, Object event);
}
