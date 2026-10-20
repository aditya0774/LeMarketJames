package com.lemarketjames.orders.stream;

import com.lemarketjames.orders.events.OrderStatusChanged;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory subscriber registry for account-scoped order status streams.
 * Each account can have multiple active browser subscribers.
 */
@Component
public class OrderStatusStreamService {

    private static final long STREAM_TIMEOUT_MS = 0L;
    private static final String EVENT_NAME = "order-status-changed";

    private final ConcurrentHashMap<Integer, Set<SseEmitter>> subscribersByAccount = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Integer accountId) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        subscribersByAccount.computeIfAbsent(accountId, ignored -> ConcurrentHashMap.newKeySet()).add(emitter);

        emitter.onCompletion(() -> remove(accountId, emitter));
        emitter.onTimeout(() -> remove(accountId, emitter));
        emitter.onError(error -> remove(accountId, emitter));

        return emitter;
    }

    public void publish(OrderStatusChanged event) {
        Set<SseEmitter> subscribers = subscribersByAccount.get(event.accountId());
        if (subscribers == null || subscribers.isEmpty()) {
            return;
        }

        for (SseEmitter emitter : subscribers.toArray(SseEmitter[]::new)) {
            try {
                emitter.send(SseEmitter.event().name(EVENT_NAME).data(event));
            } catch (IOException | IllegalStateException sendFailure) {
                remove(event.accountId(), emitter);
                emitter.complete();
            }
        }
    }

    private void remove(Integer accountId, SseEmitter emitter) {
        subscribersByAccount.computeIfPresent(accountId, (ignored, current) -> {
            current.remove(emitter);
            return current.isEmpty() ? null : current;
        });
    }
}
