package com.lemarketjames.common.audit;

import org.springframework.data.repository.Repository;

import java.util.List;

/**
 * The audit trail: insert and read, nothing else (contract C2). Write only through
 * {@link AuditRecorder}.
 *
 * <p>Extends the empty {@link Repository} marker rather than {@code JpaRepository} on purpose: an
 * audit event is never changed or removed, so no delete or bulk method exists for a feature to
 * call. The database refuses both as well (database/schema/016).
 */
public interface AuditEventRepository extends Repository<AuditEventEntity, Long> {

    /** Inserts a new event. An event that is already stored is never written again. */
    AuditEventEntity save(AuditEventEntity event);

    List<AuditEventEntity> findByOrderIdOrderByOccurredAtAsc(Integer orderId);

    /**
     * One submission's events in the order they were written. The way to read a refused order's
     * trail, which has no order ID to look it up by.
     */
    List<AuditEventEntity> findByRequestIdOrderByAuditIdAsc(String requestId);
}
