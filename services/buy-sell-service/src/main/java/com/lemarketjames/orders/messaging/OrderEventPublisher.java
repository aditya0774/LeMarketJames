package com.lemarketjames.orders.messaging;

/**
 * The outbound side of the order events (contract C6): hands an event to a message broker so other
 * services can react to it. Callers depend on this interface rather than on a broker client, so the
 * Kafka stub can be swapped for a real producer without touching them.
 */
public interface OrderEventPublisher {

    /**
     * Publishes one event.
     *
     * @param topic the topic to publish to
     * @param key   the record key; events with the same key keep their order
     * @param event the event payload
     */
    void publish(String topic, String key, Object event);
}
