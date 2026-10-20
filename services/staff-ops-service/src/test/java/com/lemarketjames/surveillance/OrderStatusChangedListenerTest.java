package com.lemarketjames.surveillance;

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

/** The listener reads the status change orders-service publishes, and exists only when switched on. No broker needed. */
class OrderStatusChangedListenerTest {

    // Built the way Spring Boot builds the mapper the service injects.
    final ObjectMapper json = Jackson2ObjectMapperBuilder.json().build();
    final SurveillanceService surveillance = mock(SurveillanceService.class);
    final OrderStatusChangedListener listener =
        new OrderStatusChangedListener(new OrderEventReader(json), surveillance);

    @Test
    void aRejectionRecordIsHandedOnAsItWasPublished() {
        // The record value of contract C6, with the publisher's fields in the publisher's format.
        listener.onStatusChanged("{\"orderId\":42,\"accountId\":7,\"from\":\"PENDING\",\"to\":\"REJECTED\","
            + "\"occurredAt\":\"2026-10-08T14:30:02Z\",\"rejectionReason\":\"INSUFFICIENT_CASH\"}");

        verify(surveillance).reviewStatusChange(new OrderStatusChangedMessage(42, 7, "REJECTED", "INSUFFICIENT_CASH",
            Instant.parse("2026-10-08T14:30:02Z")));
    }

    @Test
    void aChangeThatIsNotARejectionHasNoReason() {
        listener.onStatusChanged("{\"orderId\":42,\"accountId\":7,\"from\":\"PENDING\",\"to\":\"FILLED\","
            + "\"occurredAt\":\"2026-10-08T14:30:02Z\",\"rejectionReason\":null}");

        verify(surveillance).reviewStatusChange(
            new OrderStatusChangedMessage(42, 7, "FILLED", null, Instant.parse("2026-10-08T14:30:02Z")));
    }

    /** Records published before the event carried a reason are still on the topic, and a new group reads them. */
    @Test
    void aRecordFromBeforeTheReasonExistedIsReadWithoutOne() {
        listener.onStatusChanged("{\"orderId\":42,\"accountId\":7,\"from\":\"PENDING\",\"to\":\"REJECTED\","
            + "\"occurredAt\":\"2026-10-08T14:30:02Z\"}");

        verify(surveillance).reviewStatusChange(
            new OrderStatusChangedMessage(42, 7, "REJECTED", null, Instant.parse("2026-10-08T14:30:02Z")));
    }

    /** One bad record must not stop the listener: it is skipped and the next one is read. */
    @Test
    void aRecordThatCannotBeReadIsSkipped() {
        listener.onStatusChanged("not json");

        verifyNoInteractions(surveillance);
    }

    final ApplicationContextRunner context = new ApplicationContextRunner()
        .withBean(OrderEventReader.class, () -> new OrderEventReader(json))
        .withBean(SurveillanceService.class, () -> surveillance)
        .withUserConfiguration(OrderStatusChangedListener.class);

    @Test
    void thereIsNoListenerUntilTheConsumerIsSwitchedOn() {
        context.run(started -> assertThat(started).doesNotHaveBean(OrderStatusChangedListener.class));
    }

    @Test
    void switchingTheConsumerOnCreatesTheListener() {
        context.withPropertyValues(OrderEventConsumerSwitch.CONSUMER_PROPERTY + "=" + OrderEventConsumerSwitch.KAFKA)
            .run(started -> assertThat(started).hasSingleBean(OrderStatusChangedListener.class));
    }
}
