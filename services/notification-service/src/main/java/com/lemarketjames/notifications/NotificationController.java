package com.lemarketjames.notifications;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * The client's notifications (contract C6). There is no account in the path: the account is the
 * signed-in client's own, so nobody can ask for another client's notifications.
 */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    /**
     * @param caller the signed-in client
     * @return the newest notifications of the caller's account, newest first
     */
    @GetMapping
    public NotificationList latest(Authentication caller) {
        return new NotificationList(true, notifications.latestFor(caller.getName()).stream()
            .map(NotificationView::of)
            .toList());
    }

    /** The same answer holdings-service gives a login that has no account. */
    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<Map<String, Object>> accountNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
            "success", false,
            "error", "Account not found",
            "code", "ACCOUNT_NOT_FOUND"));
    }

    /** The response body. */
    public record NotificationList(boolean success, List<NotificationView> notifications) {
    }

    /**
     * One notification as the client sees it.
     *
     * @param notificationId this notification
     * @param orderId        the order it is about
     * @param status         the status the order reached
     * @param previousStatus the status it left
     * @param message        the sentence to show
     * @param occurredAt     when the order changed status (UTC)
     */
    public record NotificationView(Integer notificationId, Integer orderId, String status, String previousStatus,
                                   String message, Instant occurredAt) {

        static NotificationView of(Notification notification) {
            return new NotificationView(notification.getNotificationId(), notification.getOrderId(),
                notification.getStatus(), notification.getPreviousStatus(), notification.getMessage(),
                notification.getOccurredAt());
        }
    }
}
