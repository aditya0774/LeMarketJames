package com.lemarketjames.notifications;

import com.lemarketjames.common.domain.AccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Turns order status changes into notifications and reads a client's notifications back.
 *
 * <p>Kafka can deliver a record more than once, after a restart or a failed commit. Recording is
 * therefore idempotent: a status change that is already stored is left alone, so a client is
 * never told the same thing twice.
 */
@Service
public class NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    /** How many notifications a client gets back: the newest ones. */
    static final int LATEST = 50;

    private final NotificationRepository notifications;
    private final AccountRepository accounts;

    public NotificationService(NotificationRepository notifications, AccountRepository accounts) {
        this.notifications = notifications;
        this.accounts = accounts;
    }

    /**
     * Stores the notification for one status change.
     *
     * @param event the status change, as read from Kafka
     * @return whether a notification was stored; false for an event that was already recorded or
     *         that lacks a field a notification needs
     */
    @Transactional
    public boolean record(OrderStatusChangedMessage event) {
        if (!event.isComplete()) {
            log.warn("Kafka: skipped an incomplete status change: {}", event);
            return false;
        }
        if (notifications.existsByOrderIdAndStatus(event.orderId(), event.to())) {
            log.info("Kafka: order {} reaching {} was already notified", event.orderId(), event.to());
            return false;
        }
        notifications.save(new Notification(event, messageFor(event)));
        log.info("Notification stored: account={} order={} status={}", event.accountId(), event.orderId(), event.to());
        return true;
    }

    /**
     * @param username the signed-in client
     * @return the newest notifications of that client's account, newest first
     * @throws AccountNotFoundException when the user has no trading account
     */
    @Transactional(readOnly = true)
    public List<Notification> latestFor(String username) {
        Integer accountId = accounts.findAccountIdByUsername(username)
            .orElseThrow(() -> new AccountNotFoundException(username));
        return notifications.findByAccountIdOrderByOccurredAtDescNotificationIdDesc(accountId, PageRequest.of(0, LATEST));
    }

    /** The sentence the client reads. */
    static String messageFor(OrderStatusChangedMessage event) {
        return "Order #" + event.orderId() + " is now " + event.to() + " (was " + event.from() + ")";
    }
}
