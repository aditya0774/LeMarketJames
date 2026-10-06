package com.lemarketjames.common.audit;

import org.hibernate.annotations.Immutable;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.CrudRepository;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Contract C2: no application feature can update or delete an audit event. */
class AuditEventRepositoryTest {

    // getMethods() includes inherited ones, so this fails as soon as the repository extends an
    // interface that brings delete or bulk methods back, or someone declares one.
    @Test
    void offersOnlyInsertAndRead() {
        Set<String> methods = Arrays.stream(AuditEventRepository.class.getMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());

        assertEquals(Set.of("save", "findByOrderIdOrderByOccurredAtAsc", "findByRequestIdOrderByAuditIdAsc"), methods);
    }

    @Test
    void doesNotInheritTheGenericDeleteMethods() {
        assertFalse(CrudRepository.class.isAssignableFrom(AuditEventRepository.class));
    }

    @Test
    void aStoredEventIsNeverWrittenBack() {
        assertTrue(AuditEventEntity.class.isAnnotationPresent(Immutable.class));
    }

    // A setter would let a loaded event be changed in memory and then mistaken for the stored one.
    @Test
    void anEventHasNoSetters() {
        assertTrue(Arrays.stream(AuditEventEntity.class.getMethods())
                .noneMatch(method -> method.getName().startsWith("set")));
    }
}
