package com.lemarketjames.notifications;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Reads and writes the {@code notifications} table. */
public interface NotificationRepository extends JpaRepository<Notification, Integer> {

    /** @return whether this status change of this order has been recorded already */
    boolean existsByOrderIdAndStatus(Integer orderId, String status);

    /** @return one page of an account's notifications, newest first */
    List<Notification> findByAccountIdOrderByOccurredAtDescNotificationIdDesc(Integer accountId, Pageable page);
}
