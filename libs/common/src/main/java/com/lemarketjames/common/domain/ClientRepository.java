package com.lemarketjames.common.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** findByEmail backs login; existsByUsername/existsByEmail back registration's duplicate checks. */
public interface ClientRepository extends JpaRepository<ClientEntity, Integer> {
    Optional<ClientEntity> findByUsername(String username);
    Optional<ClientEntity> findByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
}
