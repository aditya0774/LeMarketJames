package com.lemarketjames.common.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Read side of the audit trail; write only through {@link AuditRecorder}. */
public interface AuditEventRepository extends JpaRepository<AuditEventEntity, Long> {

    List<AuditEventEntity> findByOrderIdOrderByOccurredAtAsc(Integer orderId);

    /**
     * One submission's events in the order they were written. The way to read a refused order's
     * trail, which has no order ID to look it up by.
     */
    List<AuditEventEntity> findByRequestIdOrderByAuditIdAsc(String requestId);
}
