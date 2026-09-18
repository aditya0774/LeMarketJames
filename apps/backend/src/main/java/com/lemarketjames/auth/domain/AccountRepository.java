package com.lemarketjames.auth.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

/** Account ownership is resolved from persisted client identities. */
public interface AccountRepository extends JpaRepository<AccountEntity, Integer> {
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
