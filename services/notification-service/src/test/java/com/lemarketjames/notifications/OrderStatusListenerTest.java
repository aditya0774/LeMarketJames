package com.lemarketjames.notifications;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lemarketjames.common.events.OrderEventConsumerSwitch;
import com.lemarketjames.common.events.OrderEventReader;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** The listener reads the record buy-sell-service publishes, and exists only when switched on. No broker needed. */
class OrderStatusListenerTest {

    // Built the way Spring Boot builds the mapper the service injects.
    final ObjectMapper json = Jackson2ObjectMapperBuilder.json().build();
    final NotificationService notifications = mock(NotificationService.class);
    final OrderStatusListener listener = new OrderStatusListener(new OrderEventReader(json), notifications);

    @Test
    void aStatusChangeRecordIsHandedOnAsItWasPublished() {
        // The record value of contract C6, with the publisher's fields in the publisher's format.
        listener.onStatusChanged("{\"orderId\":42,\"accountId\":7,\"from\":\"PENDING\",\"to\":\"FILLED\","
            + "\"occurredAt\":\"2026-10-08T14:30:00Z\"}");

        verify(notifications).record(
            new OrderStatusChangedMessage(42, 7, "PENDING", "FILLED", Instant.parse("2026-10-08T14:30:00Z")));
    }

    /** One bad record must not stop the listener: it is skipped and the next one is read. */
    @Test
    void aRecordThatCannotBeReadIsSkipped() {
        listener.onStatusChanged("not json");

        verifyNoInteractions(notifications);
    }

    final ApplicationContextRunner context = new ApplicationContextRunner()
        .withBean(OrderEventReader.class, () -> new OrderEventReader(json))
        .withBean(NotificationService.class, () -> notifications)
        .withUserConfiguration(OrderStatusListener.class);

    @Test
    void thereIsNoListenerUntilTheConsumerIsSwitchedOn() {
        context.run(started -> assertThat(started).doesNotHaveBean(OrderStatusListener.class));
    }

    @Test
    void switchingTheConsumerOnCreatesTheListener() {
        context.withPropertyValues(OrderEventConsumerSwitch.CONSUMER_PROPERTY + "=" + OrderEventConsumerSwitch.KAFKA)
            .run(started -> assertThat(started).hasSingleBean(OrderStatusListener.class));
    }
}
