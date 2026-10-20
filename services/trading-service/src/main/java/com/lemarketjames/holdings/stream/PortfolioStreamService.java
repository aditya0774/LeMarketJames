package com.lemarketjames.holdings.stream;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory subscriber registry for account-scoped portfolio streams.
 * Each account can have multiple active browser subscribers.
 */
@Component
public class PortfolioStreamService {

    private static final long STREAM_TIMEOUT_MS = 0L;
    private static final String EVENT_NAME = "balance-changed";

    private final ConcurrentHashMap<Integer, Set<SseEmitter>> subscribersByAccount = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Integer accountId) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        subscribersByAccount.computeIfAbsent(accountId, ignored -> ConcurrentHashMap.newKeySet()).add(emitter);

        emitter.onCompletion(() -> remove(accountId, emitter));
        emitter.onTimeout(() -> remove(accountId, emitter));
        emitter.onError(error -> remove(accountId, emitter));

        // Spring sends no headers until the first write. Without this comment the browser's onopen
        // would not fire on a reconnect, and the client would never catch up on what it missed.
        try {
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (IOException | IllegalStateException sendFailure) {
            remove(accountId, emitter);
            emitter.complete();
        }
        return emitter;
    }

    public void publish(BalanceChanged event) {
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
