package com.lemarketjames.common.events;

/**
 * The Kafka topics of the order events (contract C6), one per event. They are here, rather than
 * in buy-sell-service, because the services that consume the events need the same names and
 * cannot depend on the service that publishes them. This is the only place the names are written.
 */
public final class OrderEventTopicNames {

    /** {@code OrderSubmitted}: a new order passed its placement checks and was saved. */
    public static final String SUBMITTED = "lemarket.orders.submitted";
    /** {@code OrderStatusChanged}: an order moved from one status to another. */
    public static final String STATUS_CHANGED = "lemarket.orders.status-changed";
    /** {@code OrderFilled}: an order filled and was settled. */
    public static final String FILLED = "lemarket.orders.filled";

    private OrderEventTopicNames() {
    }
}
