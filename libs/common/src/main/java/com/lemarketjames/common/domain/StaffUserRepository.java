package com.lemarketjames.common.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/** findByEmail backs staff login, which uses the same email-based login as clients. */
public interface StaffUserRepository extends JpaRepository<StaffUserEntity, Integer> {

    Optional<StaffUserEntity> findByEmail(String email);

    /** Only the active flag, for the per-request access check (LMKT-57); empty when the login no longer exists. */
    @Query("select s.active from StaffUserEntity s where s.username = :username")
    Optional<Boolean> findActiveByUsername(@Param("username") String username);

    @Query("select new com.lemarketjames.common.domain.AccountAccessState(null, s.active, s.sessionInvalidatedAt) "
            + "from StaffUserEntity s where s.username = :username")
    Optional<AccountAccessState> findAccessStateByUsername(@Param("username") String username);
}
