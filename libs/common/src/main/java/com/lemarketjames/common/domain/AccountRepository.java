package com.lemarketjames.common.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

/** Account ownership is resolved from persisted client identities. */
public interface AccountRepository extends JpaRepository<AccountEntity, Integer> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM AccountEntity a WHERE a.accountId = :id")
    Optional<AccountEntity> findLockedById(@Param("id") Integer id);

		@Query("""
				SELECT COUNT(a) > 0
				FROM AccountEntity a
				JOIN ClientEntity c ON c.clientId = a.clientId
				WHERE a.accountId = :accountId
					AND c.username = :username
				""")
		boolean existsByAccountIdAndUsername(@Param("accountId") Integer accountId,
																				 @Param("username") String username);
    @Query("SELECT a.accountId FROM AccountEntity a JOIN ClientEntity c ON c.clientId = a.clientId WHERE c.username = :username")
    Optional<Integer> findAccountIdByUsername(@Param("username") String username);
}
