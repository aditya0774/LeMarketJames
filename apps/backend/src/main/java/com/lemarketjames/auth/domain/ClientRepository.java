package com.lemarketjames.auth.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** findByUsername backs login; existsByUsername/existsByEmail back registration's duplicate checks. */
public interface ClientRepository extends JpaRepository<ClientEntity, Integer> {
    Optional<ClientEntity> findByUsername(String username);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
}
