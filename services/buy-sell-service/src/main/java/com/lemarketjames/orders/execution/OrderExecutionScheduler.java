package com.lemarketjames.orders.execution;

import com.lemarketjames.orders.entity.Order.OrderStatus;
import com.lemarketjames.orders.repository.OrderRepository;
import java.time.Clock;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OrderExecutionScheduler {
    private static final Logger log = LoggerFactory.getLogger(OrderExecutionScheduler.class);
    private final OrderRepository orders;
    private final OrderExecutionService execution;
    private final ExecutionSettings settings;
    public OrderExecutionScheduler(OrderRepository orders, OrderExecutionService execution, ExecutionSettings settings) {
        this.orders = orders; this.execution = execution; this.settings = settings;
    }
    @Scheduled(fixedDelayString = "#{@executionSettings.pollMs}")
    public void poll() {
        if (!settings.isEnabled()) return;
        var open = Arrays.stream(OrderStatus.values()).filter(OrderStatus::isOpen).toList();
        for (Integer id : orders.findExecutionCandidates(open)) {
            try { execution.execute(id, false); }
            catch (RuntimeException failure) { log.warn("Order {} execution will retry: {}", id, failure.getMessage()); }
        }
    }
    @Configuration
    static class TimeConfiguration {
        @Bean Clock executionClock() { return Clock.systemUTC(); }
    }
}
