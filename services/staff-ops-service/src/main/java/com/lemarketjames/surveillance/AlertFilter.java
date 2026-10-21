package com.lemarketjames.surveillance;

import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * What Trading Operations narrow the alert list to (contract C6). Every part is optional; an empty
 * filter lists every alert. Days are whole UTC days, both included, like trade search's, since
 * Trading Ops read every time in UTC.
 *
 * @param reason only alerts raised for this reason
 * @param from   only alerts whose {@code occurredAt} is on or after this day
 * @param to     only alerts whose {@code occurredAt} is on or before this day
 */
public record AlertFilter(AlertReason reason, LocalDate from, LocalDate to) {

    /** Lists every alert. */
    public static final AlertFilter NONE = new AlertFilter(null, null, null);

    /** @throws IllegalArgumentException when {@code to} is before {@code from}; answered with 400 */
    public AlertFilter {
        if (from != null && to != null && to.isBefore(from)) {
            throw new IllegalArgumentException("to must not be before from");
        }
    }

    /** The filter as a query condition; a part that is not set adds none. */
    Specification<OrderAlert> toSpecification() {
        Specification<OrderAlert> spec = (root, query, cb) -> cb.conjunction();
        if (reason != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("reason"), reason));
        }
        if (from != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("occurredAt"),
                from.atStartOfDay(ZoneOffset.UTC).toInstant()));
        }
        if (to != null) {
            // The whole last day: up to, not including, the next day's midnight.
            spec = spec.and((root, query, cb) -> cb.lessThan(root.get("occurredAt"),
                to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()));
        }
        return spec;
    }
}
