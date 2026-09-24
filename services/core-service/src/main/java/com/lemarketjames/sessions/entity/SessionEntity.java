package com.lemarketjames.sessions.entity;

import java.time.LocalDateTime;

/**
 * Session data object for JWT-based stateless sessions.
 * Not persisted to database - sessions are validated through JWT token claims only.
 */
public class SessionEntity {
  private Integer sessionId;

  private Integer accountId;

  private LocalDateTime lastActivityAt;

  private LocalDateTime expiresAt;

  private LocalDateTime createdAt;

  public SessionEntity() {}

  public SessionEntity(Integer accountId, LocalDateTime expiresAt) {
    this.accountId = accountId;
    this.lastActivityAt = LocalDateTime.now();
    this.expiresAt = expiresAt;
    this.createdAt = LocalDateTime.now();
  }

  public Integer getSessionId() {
    return sessionId;
  }

  public Integer getAccountId() {
    return accountId;
  }

  public LocalDateTime getLastActivityAt() {
    return lastActivityAt;
  }

  public void setLastActivityAt(LocalDateTime lastActivityAt) {
    this.lastActivityAt = lastActivityAt;
  }

  public LocalDateTime getExpiresAt() {
    return expiresAt;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public boolean isExpired() {
    return LocalDateTime.now().isAfter(expiresAt);
  }
}
