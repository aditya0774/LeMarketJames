package com.lemarketjames.common.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/** findByEmail backs login; existsByUsername/existsByEmail back registration's duplicate checks. */
public interface ClientRepository extends JpaRepository<ClientEntity, Integer> {
    Optional<ClientEntity> findByUsername(String username);
    Optional<ClientEntity> findByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);

    /** Only the status, for the per-request access check (LMKT-57); empty when the client no longer exists. */
    @Query("select c.accountStatus from ClientEntity c where c.username = :username")
    Optional<AccountStatus> findAccountStatusByUsername(@Param("username") String username);
}
