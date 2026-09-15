package com.lemarketjames.sessions;

import com.lemarketjames.sessions.dto.SessionDto;
import com.lemarketjames.sessions.dto.SessionResponse;
import com.lemarketjames.sessions.exception.SessionExpiredException;
import com.lemarketjames.sessions.service.SessionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {
  private final SessionService sessionService;

  public SessionController(SessionService sessionService) {
    this.sessionService = sessionService;
  }

  /**
   * AC1: Validate session - check if JWT token is active and not expired
   */
  @PostMapping("/validate")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<SessionResponse> validateSession(
      @RequestParam Integer accountId,
      @RequestHeader("Authorization") String authHeader) {
    try {
      // Extract token from "Bearer <token>"
      String token = authHeader.replace("Bearer ", "");
      SessionDto session = sessionService.validateSession(accountId, token);
      return ResponseEntity.ok(new SessionResponse(true, session, "Session is active"));
    } catch (SessionExpiredException e) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
          .body(new SessionResponse(false, null, e.getMessage()));
    }
  }
}

