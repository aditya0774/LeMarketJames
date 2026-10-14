package com.lemarketjames.audit;

import com.lemarketjames.common.audit.AuditEventEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Read-only archive access; age classifies records without modifying the immutable trail. */
@Repository
public class ArchiveRepository {
    private final EntityManager entityManager;

    public ArchiveRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public List<AuditEventEntity> search(Instant cutoff, Integer orderId, String requestId,
            Integer clientId, Instant from, Instant to, int limit, int offset) {
        var cb = entityManager.getCriteriaBuilder();
        var query = cb.createQuery(AuditEventEntity.class);
        var event = query.from(AuditEventEntity.class);
        var predicates = new ArrayList<Predicate>();
        predicates.add(cb.lessThan(event.get("occurredAt"), cutoff));
        if (orderId != null) predicates.add(cb.equal(event.get("orderId"), orderId));
        if (requestId != null) predicates.add(cb.equal(event.get("requestId"), requestId));
        if (clientId != null) predicates.add(cb.equal(event.get("clientId"), clientId));
        if (from != null) predicates.add(cb.greaterThanOrEqualTo(event.get("occurredAt"), from));
        if (to != null) predicates.add(cb.lessThan(event.get("occurredAt"), to));
        query.select(event).where(predicates.toArray(Predicate[]::new))
                .orderBy(cb.asc(event.get("occurredAt")), cb.asc(event.get("auditId")));
        return entityManager.createQuery(query).setFirstResult(offset).setMaxResults(limit).getResultList();
    }
}
