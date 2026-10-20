package com.lemarketjames.sessions.dto;

public class ValidateSessionRequest {
  private Integer sessionId;
  private Integer accountId;

  public ValidateSessionRequest() {}

  public ValidateSessionRequest(Integer sessionId, Integer accountId) {
    this.sessionId = sessionId;
    this.accountId = accountId;
  }

  public Integer getSessionId() {
    return sessionId;
  }

  public void setSessionId(Integer sessionId) {
    this.sessionId = sessionId;
  }

  public Integer getAccountId() {
    return accountId;
  }

  public void setAccountId(Integer accountId) {
    this.accountId = accountId;
  }
}
