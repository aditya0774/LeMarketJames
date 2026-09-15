package com.lemarketjames.sessions.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "sessions")
public class SessionEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer sessionId;

  @Column(nullable = false)
  private Integer accountId;

  @Column(nullable = false)
  private LocalDateTime lastActivityAt;

  @Column(nullable = false)
  private LocalDateTime expiresAt;

  @Column(nullable = false)
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
