package com.lemarketjames.sessions;

import com.lemarketjames.sessions.dto.SessionDto;
import com.lemarketjames.sessions.dto.SessionResponse;
import com.lemarketjames.sessions.dto.ValidateSessionRequest;
import com.lemarketjames.sessions.exception.SessionExpiredException;
import com.lemarketjames.sessions.service.SessionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {
  private final SessionService sessionService;

  public SessionController(SessionService sessionService) {
    this.sessionService = sessionService;
  }

  /**
   * AC1: Validate session - check if active and not expired
   */
  @PostMapping("/validate")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<SessionResponse> validateSession(@RequestBody ValidateSessionRequest request) {
    try {
      SessionDto session = sessionService.validateSession(request.getAccountId(), request.getSessionId());
      return ResponseEntity.ok(new SessionResponse(true, session, "Session is active"));
    } catch (SessionExpiredException e) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
          .body(new SessionResponse(false, null, e.getMessage()));
    }
  }

  /**
   * Create a new session for authenticated user
   */
  @PostMapping("/create")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<SessionResponse> createSession(@RequestParam Integer accountId) {
    try {
      SessionDto session = sessionService.createSession(accountId);
      return ResponseEntity.status(HttpStatus.CREATED)
          .body(new SessionResponse(true, session, "Session created"));
    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body(new SessionResponse(false, null, e.getMessage()));
    }
  }
}
