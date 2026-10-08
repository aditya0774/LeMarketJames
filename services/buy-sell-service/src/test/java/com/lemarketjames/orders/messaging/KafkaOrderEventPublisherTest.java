package com.lemarketjames.orders.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.lemarketjames.orders.entity.Order;
import com.lemarketjames.orders.events.OrderFilled;
import com.lemarketjames.orders.events.OrderStatusChanged;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** No broker: the KafkaTemplate is mocked, so this runs everywhere. The real path is OrderEventKafkaIntegrationTest. */
@ExtendWith(OutputCaptureExtension.class)
class KafkaOrderEventPublisherTest {
    static final String TOPIC = OrderEventForwarder.FILLED_TOPIC;

    @SuppressWarnings("unchecked")
    final KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    // Dates as Spring Boot's own ObjectMapper writes them: ISO-8601 text, not numbers.
    final ObjectMapper json = JsonMapper.builder().addModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
    final KafkaOrderEventPublisher publisher = new KafkaOrderEventPublisher(kafka, json);
    final CompletableFuture<SendResult<String, String>> answer = new CompletableFuture<>();
    final Instant now = Instant.parse("2026-09-30T15:00:00Z");
    final OrderFilled filled = new OrderFilled(7, 1, 3, Order.OrderType.BUY, BigDecimal.TEN,
        new BigDecimal("101.2346"), now, "SIMULATED_FEED", now.minusSeconds(1));

    @Test void sendsTheEventAsJsonUnderTheGivenTopicAndKey() throws Exception {
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(answer);

        publisher.publish(TOPIC, "7", filled);

        ArgumentCaptor<String> value = ArgumentCaptor.forClass(String.class);
        verify(kafka).send(eq(TOPIC), eq("7"), value.capture());
        JsonNode sent = json.readTree(value.getValue());
        assertEquals(7, sent.get("orderId").asInt());
        assertEquals(1, sent.get("accountId").asInt());
        assertEquals(3, sent.get("instrumentId").asInt());
        assertEquals("BUY", sent.get("side").asText());
        assertEquals(0, new BigDecimal("101.2346").compareTo(sent.get("price").decimalValue()));
        assertEquals("2026-09-30T15:00:00Z", sent.get("filledAt").asText());
        assertEquals("SIMULATED_FEED", sent.get("quoteSource").asText());
        assertEquals("2026-09-30T14:59:59Z", sent.get("quoteTime").asText());
    }

    @Test void sendsAStatusChangeWithBothStatuses() throws Exception {
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(answer);

        publisher.publish(OrderEventForwarder.STATUS_CHANGED_TOPIC, "7",
            new OrderStatusChanged(7, 1, Order.OrderStatus.ACCEPTED, Order.OrderStatus.FILLED, now));

        ArgumentCaptor<String> value = ArgumentCaptor.forClass(String.class);
        verify(kafka).send(eq(OrderEventForwarder.STATUS_CHANGED_TOPIC), eq("7"), value.capture());
        JsonNode sent = json.readTree(value.getValue());
        assertEquals("ACCEPTED", sent.get("from").asText());
        assertEquals("FILLED", sent.get("to").asText());
        assertEquals("2026-09-30T15:00:00Z", sent.get("occurredAt").asText());
    }

    @Test void logsWhereTheBrokerStoredTheRecord(CapturedOutput output) {
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(answer);
        publisher.publish(TOPIC, "7", filled);

        // Offset 40 + 2: the batch's first offset plus this record's place in it.
        answer.complete(new SendResult<>(new ProducerRecord<>(TOPIC, "7", "{}"),
            new RecordMetadata(new TopicPartition(TOPIC, 2), 40, 2, 0, 1, 2)));

        assertTrue(output.getOut().contains("Kafka: published topic=" + TOPIC + " key=7 partition=2 offset=42"));
    }

    // The order has already committed, so neither failure below may reach the caller.
    @Test void aSendTheBrokerRefusesIsLoggedNotThrown(CapturedOutput output) {
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(answer);
        publisher.publish(TOPIC, "7", filled);

        answer.completeExceptionally(new KafkaException("broker said no"));

        assertTrue(output.getOut().contains("Kafka: could not publish topic=" + TOPIC + " key=7"));
        assertTrue(output.getOut().contains("broker said no"));
    }

    @Test void anUnreachableBrokerIsLoggedNotThrown(CapturedOutput output) {
        when(kafka.send(anyString(), anyString(), anyString())).thenThrow(new KafkaException("no broker"));

        assertDoesNotThrow(() -> publisher.publish(TOPIC, "7", filled));

        assertTrue(output.getOut().contains("Kafka: could not publish topic=" + TOPIC + " key=7"));
    }

    @Test void anEventThatCannotBeWrittenAsJsonIsLoggedNotThrown(CapturedOutput output) {
        // An object that refers to itself has no JSON form.
        Object[] unwritable = new Object[1];
        unwritable[0] = unwritable;

        assertDoesNotThrow(() -> publisher.publish(TOPIC, "7", unwritable));

        assertTrue(output.getOut().contains("Kafka: could not publish topic=" + TOPIC + " key=7"));
        verifyNoInteractions(kafka);
    }
}
