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

    /** Account state for the per-request access check; empty when the client no longer exists. */
    @Query("select c.accountStatus from ClientEntity c where c.username = :username")
    Optional<AccountStatus> findAccountStatusByUsername(@Param("username") String username);

    @Query("select new com.lemarketjames.common.domain.AccountAccessState(c.accountStatus, null, c.sessionInvalidatedAt) "
            + "from ClientEntity c where c.username = :username")
    Optional<AccountAccessState> findAccessStateByUsername(@Param("username") String username);
}
