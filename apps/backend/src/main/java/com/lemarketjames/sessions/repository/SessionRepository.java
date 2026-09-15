package com.lemarketjames.sessions.repository;

import com.lemarketjames.sessions.entity.SessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SessionRepository extends JpaRepository<SessionEntity, Integer> {
  Optional<SessionEntity> findBySessionId(Integer sessionId);
  Optional<SessionEntity> findByAccountId(Integer accountId);
}
