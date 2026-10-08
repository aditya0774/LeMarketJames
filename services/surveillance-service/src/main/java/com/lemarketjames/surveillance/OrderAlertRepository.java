package com.lemarketjames.surveillance;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Reads and writes the {@code order_alerts} table. */
public interface OrderAlertRepository extends JpaRepository<OrderAlert, Integer> {

    /** @return whether this order already has its alert */
    boolean existsByOrderId(Integer orderId);

    /** @return one page of alerts, newest order first */
    List<OrderAlert> findAllByOrderBySubmittedAtDescAlertIdDesc(Pageable page);
}
