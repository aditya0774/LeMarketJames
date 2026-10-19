package com.lemarketjames.sessions.exception;

public class SessionExpiredException extends RuntimeException {
  public SessionExpiredException(String message) {
    super(message);
  }
}
