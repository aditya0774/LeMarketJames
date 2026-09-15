package com.lemarketjames.sessions.service;

import com.lemarketjames.sessions.dto.SessionDto;
import com.lemarketjames.sessions.entity.SessionEntity;
import com.lemarketjames.sessions.exception.SessionExpiredException;
import com.lemarketjames.sessions.repository.SessionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class SessionService {
  private final SessionRepository sessionRepository;

  @Value("${session.timeout.minutes:30}")
  private Integer sessionTimeoutMinutes;

  public SessionService(SessionRepository sessionRepository) {
    this.sessionRepository = sessionRepository;
  }

  /**
   * AC1: Validate session is active and not expired
   */
  public SessionDto validateSession(Integer accountId, Integer sessionId) {
    Optional<SessionEntity> session = sessionRepository.findBySessionId(sessionId);

    if (session.isEmpty()) {
      throw new SessionExpiredException("Session not found");
    }

    SessionEntity sessionEntity = session.get();

    // Verify session belongs to the account
    if (!sessionEntity.getAccountId().equals(accountId)) {
      throw new SessionExpiredException("Session does not belong to this account");
    }

    // Check if expired
    if (sessionEntity.isExpired()) {
      throw new SessionExpiredException("Session has expired");
    }

    // Update last activity
    sessionEntity.setLastActivityAt(LocalDateTime.now());
    sessionRepository.save(sessionEntity);

    return new SessionDto(
        sessionEntity.getSessionId(),
        sessionEntity.getAccountId(),
        sessionEntity.getExpiresAt(),
        true
    );
  }

  /**
   * Create a new session for authenticated user
   */
  public SessionDto createSession(Integer accountId) {
    LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(sessionTimeoutMinutes);
    SessionEntity session = new SessionEntity(accountId, expiresAt);
    SessionEntity saved = sessionRepository.save(session);

    return new SessionDto(
        saved.getSessionId(),
        saved.getAccountId(),
        saved.getExpiresAt(),
        true
    );
  }
}
