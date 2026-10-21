package com.lemarketjames.accountstatus;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** A client's status history, newest first; the id breaks ties between changes in the same instant. */
public interface AccountStatusChangeRepository extends JpaRepository<AccountStatusChangeEntity, Long> {
    List<AccountStatusChangeEntity> findByClientIdOrderByChangedAtDescChangeIdDesc(Integer clientId);
}
