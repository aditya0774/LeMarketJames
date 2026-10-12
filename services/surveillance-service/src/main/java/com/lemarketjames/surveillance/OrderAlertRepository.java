package com.lemarketjames.surveillance;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Reads and writes the {@code order_alerts} table. */
public interface OrderAlertRepository extends JpaRepository<OrderAlert, Integer> {

    /** @return whether this order already has its alert for this reason */
    boolean existsByOrderIdAndReason(Integer orderId, AlertReason reason);

    /** @return one page of alerts, newest first */
    List<OrderAlert> findAllByOrderByOccurredAtDescAlertIdDesc(Pageable page);
}
