package com.lemarketjames.holdings.stream;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortfolioStreamServiceTest {

    private static final BalanceChanged SETTLED_FOR_7 =
        new BalanceChanged(7, 42, new BigDecimal("9900.00"), Instant.parse("2026-10-20T10:00:00Z"));

    private final PortfolioStreamService service = new PortfolioStreamService();

    /** Keeps what it was sent, so a test can see which account's browser got the event. */
    private static final class RecordingEmitter extends SseEmitter {
        final List<String> sent = new ArrayList<>();

        @Override
        public void send(SseEventBuilder builder) {
            StringBuilder text = new StringBuilder();
            builder.build().forEach(part -> text.append(part.getData()));
            sent.add(text.toString());
        }
    }

    @Test
    void subscribeRegistersEmitter() {
        service.subscribe(7);
        assertEquals(1, subscriberCount(7));
    }

    @Test
    void publishReachesEveryStreamOfTheAccountAndNoOther() {
        RecordingEmitter first = register(7, new RecordingEmitter());
        RecordingEmitter second = register(7, new RecordingEmitter());
        RecordingEmitter other = register(8, new RecordingEmitter());

        service.publish(SETTLED_FOR_7);

        assertEquals(1, first.sent.size());
        assertTrue(first.sent.get(0).contains("event:balance-changed"));
        assertEquals(1, second.sent.size());
        assertTrue(other.sent.isEmpty());
    }

    @Test
    void publishRemovesEmitterWhenSendFails() {
        SseEmitter emitter = service.subscribe(7);
        emitter.complete();

        service.publish(SETTLED_FOR_7);

        // Closed emitters should be evicted when publish hits a send failure.
        assertEquals(0, subscriberCount(7));
    }

    private <E extends SseEmitter> E register(Integer accountId, E emitter) {
        subscribers().computeIfAbsent(accountId, ignored -> ConcurrentHashMap.newKeySet()).add(emitter);
        return emitter;
    }

    @SuppressWarnings("unchecked")
    private ConcurrentHashMap<Integer, Set<SseEmitter>> subscribers() {
        return (ConcurrentHashMap<Integer, Set<SseEmitter>>) ReflectionTestUtils.getField(service, "subscribersByAccount");
    }

    private int subscriberCount(Integer accountId) {
        return subscribers().getOrDefault(accountId, Set.of()).size();
    }
}
