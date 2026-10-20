package com.lemarketjames.sessions.dto;

import java.time.LocalDateTime;

public class SessionDto {
  private Integer sessionId;
  private Integer accountId;
  private LocalDateTime expiresAt;
  private Boolean isActive;

  public SessionDto(Integer sessionId, Integer accountId, LocalDateTime expiresAt, Boolean isActive) {
    this.sessionId = sessionId;
    this.accountId = accountId;
    this.expiresAt = expiresAt;
    this.isActive = isActive;
  }

  public Integer getSessionId() {
    return sessionId;
  }

  public Integer getAccountId() {
    return accountId;
  }

  public LocalDateTime getExpiresAt() {
    return expiresAt;
  }

  public Boolean getIsActive() {
    return isActive;
  }
}
