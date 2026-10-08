package com.lemarketjames.surveillance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lemarketjames.common.events.OrderEventConsumerSwitch;
import com.lemarketjames.common.events.OrderEventReader;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** The listener reads the record buy-sell-service publishes, and exists only when switched on. No broker needed. */
class OrderSubmittedListenerTest {

    // Built the way Spring Boot builds the mapper the service injects.
    final ObjectMapper json = Jackson2ObjectMapperBuilder.json().build();
    final SurveillanceService surveillance = mock(SurveillanceService.class);
    final OrderSubmittedListener listener = new OrderSubmittedListener(new OrderEventReader(json), surveillance);

    @Test
    void aNewOrderRecordIsHandedOnAsItWasPublished() {
        // The record value of contract C6, with the publisher's fields in the publisher's format.
        listener.onSubmitted("{\"orderId\":42,\"accountId\":7,\"instrumentId\":5,\"side\":\"BUY\",\"quantity\":150,"
            + "\"price\":244.2366,\"submittedAt\":\"2026-10-08T14:30:00Z\"}");

        verify(surveillance).review(new OrderSubmittedMessage(42, 7, 5, "BUY", new BigDecimal("150"),
            new BigDecimal("244.2366"), Instant.parse("2026-10-08T14:30:00Z")));
    }

    @Test
    void aSellRecordHasNoPrice() {
        listener.onSubmitted("{\"orderId\":43,\"accountId\":7,\"instrumentId\":5,\"side\":\"SELL\",\"quantity\":150,"
            + "\"price\":null,\"submittedAt\":\"2026-10-08T14:30:00Z\"}");

        verify(surveillance).review(new OrderSubmittedMessage(43, 7, 5, "SELL", new BigDecimal("150"), null,
            Instant.parse("2026-10-08T14:30:00Z")));
    }

    /** One bad record must not stop the listener: it is skipped and the next one is read. */
    @Test
    void aRecordThatCannotBeReadIsSkipped() {
        listener.onSubmitted("not json");

        verifyNoInteractions(surveillance);
    }

    final ApplicationContextRunner context = new ApplicationContextRunner()
        .withBean(OrderEventReader.class, () -> new OrderEventReader(json))
        .withBean(SurveillanceService.class, () -> surveillance)
        .withUserConfiguration(OrderSubmittedListener.class);

    @Test
    void thereIsNoListenerUntilTheConsumerIsSwitchedOn() {
        context.run(started -> assertThat(started).doesNotHaveBean(OrderSubmittedListener.class));
    }

    @Test
    void switchingTheConsumerOnCreatesTheListener() {
        context.withPropertyValues(OrderEventConsumerSwitch.CONSUMER_PROPERTY + "=" + OrderEventConsumerSwitch.KAFKA)
            .run(started -> assertThat(started).hasSingleBean(OrderSubmittedListener.class));
    }
}
