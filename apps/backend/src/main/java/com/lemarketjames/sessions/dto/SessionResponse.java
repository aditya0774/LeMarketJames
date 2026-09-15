package com.lemarketjames.sessions.dto;

public class SessionResponse {
  private Boolean success;
  private SessionDto session;
  private String message;

  public SessionResponse(Boolean success, SessionDto session, String message) {
    this.success = success;
    this.session = session;
    this.message = message;
  }

  public Boolean getSuccess() {
    return success;
  }

  public SessionDto getSession() {
    return session;
  }

  public String getMessage() {
    return message;
  }
}
