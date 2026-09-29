package com.lemarketjames.common.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Read side of the audit trail; write only through {@link AuditRecorder}. */
public interface AuditEventRepository extends JpaRepository<AuditEventEntity, Long> {

    List<AuditEventEntity> findByOrderIdOrderByOccurredAtAsc(Integer orderId);
}
