package com.lemarketjames.activity;

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
class OrderFilledListenerTest {

    // Built the way Spring Boot builds the mapper the service injects.
    final ObjectMapper json = Jackson2ObjectMapperBuilder.json().build();
    final MarketActivityService activity = mock(MarketActivityService.class);
    final OrderFilledListener listener = new OrderFilledListener(new OrderEventReader(json), activity);

    @Test
    void aFillRecordIsHandedOnWithoutItsAccount() {
        // The record value of contract C6, with the publisher's fields in the publisher's format.
        listener.onFilled("{\"orderId\":42,\"accountId\":7,\"instrumentId\":5,\"side\":\"BUY\",\"quantity\":10,"
            + "\"price\":244.2366,\"filledAt\":\"2026-10-08T14:30:00Z\",\"quoteSource\":\"SIMULATED\","
            + "\"quoteTime\":\"2026-10-08T14:29:59.512Z\"}");

        verify(activity).record(new OrderFilledMessage(42, 5, "BUY", new BigDecimal("10"),
            new BigDecimal("244.2366"), Instant.parse("2026-10-08T14:30:00Z")));
    }

    /** One bad record must not stop the listener: it is skipped and the next one is read. */
    @Test
    void aRecordThatCannotBeReadIsSkipped() {
        listener.onFilled("not json");

        verifyNoInteractions(activity);
    }

    final ApplicationContextRunner context = new ApplicationContextRunner()
        .withBean(OrderEventReader.class, () -> new OrderEventReader(json))
        .withBean(MarketActivityService.class, () -> activity)
        .withUserConfiguration(OrderFilledListener.class);

    @Test
    void thereIsNoListenerUntilTheConsumerIsSwitchedOn() {
        context.run(started -> assertThat(started).doesNotHaveBean(OrderFilledListener.class));
    }

    @Test
    void switchingTheConsumerOnCreatesTheListener() {
        context.withPropertyValues(OrderEventConsumerSwitch.CONSUMER_PROPERTY + "=" + OrderEventConsumerSwitch.KAFKA)
            .run(started -> assertThat(started).hasSingleBean(OrderFilledListener.class));
    }
}
