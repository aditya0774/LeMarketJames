package com.lemarketjames.surveillance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Reads and writes the {@code order_alerts} table. Lists are read through
 * {@link JpaSpecificationExecutor}, so each filter Trading Ops leave empty adds no condition.
 */
public interface OrderAlertRepository extends JpaRepository<OrderAlert, Integer>,
    JpaSpecificationExecutor<OrderAlert> {

    /** @return whether this order already has its alert for this reason */
    boolean existsByOrderIdAndReason(Integer orderId, AlertReason reason);
}
