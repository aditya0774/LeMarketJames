package com.lemarketjames.accountstatus;

import com.lemarketjames.common.domain.ClientEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Loads a client with its row locked, so two status changes at once (two Trading Ops users, or a
 * client closing while staff suspend) run one after the other and each history row records the
 * status it really changed from.
 */
public interface ClientStatusRepository extends Repository<ClientEntity, Integer> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ClientEntity c where c.clientId = :clientId")
    Optional<ClientEntity> findLockedById(@Param("clientId") Integer clientId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ClientEntity c where c.username = :username")
    Optional<ClientEntity> findLockedByUsername(@Param("username") String username);
}
