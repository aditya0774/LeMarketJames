package com.lemarketjames.audit;

import com.lemarketjames.common.audit.AuditEventEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * Reads archived audit events. Read-only and separate from the append-only writer
 * (AuditRecorder): age alone makes an event archived, so nothing here writes to the trail.
 */
@Repository
public class ArchiveRepository {

    private final EntityManager entityManager;

    public ArchiveRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /** Events older than {@code cutoff} matching every filter given, oldest first; audit ID breaks ties. */
    public List<AuditEventEntity> search(Instant cutoff, ArchiveQuery query) {
        var cb = entityManager.getCriteriaBuilder();
        var select = cb.createQuery(AuditEventEntity.class);
        var event = select.from(AuditEventEntity.class);
        var predicates = new ArrayList<Predicate>();
        predicates.add(cb.lessThan(event.get("occurredAt"), cutoff));
        if (query.orderId() != null) predicates.add(cb.equal(event.get("orderId"), query.orderId()));
        if (query.requestId() != null) predicates.add(cb.equal(event.get("requestId"), query.requestId()));
        if (query.clientId() != null) predicates.add(cb.equal(event.get("clientId"), query.clientId()));
        if (query.from() != null) predicates.add(cb.greaterThanOrEqualTo(event.get("occurredAt"), query.from()));
        if (query.to() != null) predicates.add(cb.lessThan(event.get("occurredAt"), query.to()));
        select.select(event).where(predicates.toArray(Predicate[]::new))
                .orderBy(cb.asc(event.get("occurredAt")), cb.asc(event.get("auditId")));
        return entityManager.createQuery(select)
                .setFirstResult(query.offset())
                .setMaxResults(query.limit())
                .getResultList();
    }
}
