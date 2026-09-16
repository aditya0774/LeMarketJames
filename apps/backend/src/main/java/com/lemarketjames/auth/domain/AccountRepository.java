package com.lemarketjames.auth.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** No custom queries yet; AuthService only ever inserts one row per client on registration. */
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
}
