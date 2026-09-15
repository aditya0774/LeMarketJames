package com.lemarketjames.sessions.service;

import com.lemarketjames.sessions.dto.SessionDto;
import com.lemarketjames.sessions.entity.SessionEntity;
import com.lemarketjames.sessions.exception.SessionExpiredException;
import com.lemarketjames.sessions.repository.SessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
class SessionServiceTest {
  private SessionService service;

  @MockBean
  private SessionRepository sessionRepository;

  @BeforeEach
  void setUp() {
    service = new SessionService(sessionRepository);
    // Set session timeout via reflection since @Value doesn't inject in unit tests
    ReflectionTestUtils.setField(service, "sessionTimeoutMinutes", 30);
  }

  /**
   * AC1: Session validation succeeds for active, non-expired session
   */
  @Test
  void validateSessionSucceedsForActiveSession() {
    SessionEntity session = new SessionEntity(1, LocalDateTime.now().plusMinutes(30));
    session = spy(session);
    when(sessionRepository.findBySessionId(1)).thenReturn(Optional.of(session));
    when(session.isExpired()).thenReturn(false);
    when(sessionRepository.save(any())).thenReturn(session);

    SessionDto result = service.validateSession(1, 1);

    assertNotNull(result);
    assertTrue(result.getIsActive());
    verify(sessionRepository).save(any());
  }

  /**
   * AC1: Session validation fails for expired session
   */
  @Test
  void validateSessionThrowsForExpiredSession() {
    SessionEntity session = new SessionEntity(1, LocalDateTime.now().minusMinutes(5));
    session = spy(session);
    when(sessionRepository.findBySessionId(1)).thenReturn(Optional.of(session));
    when(session.isExpired()).thenReturn(true);

    assertThrows(SessionExpiredException.class, () -> {
      service.validateSession(1, 1);
    });
  }

  /**
   * AC1: Session validation fails when session not found
   */
  @Test
  void validateSessionThrowsForMissingSession() {
    when(sessionRepository.findBySessionId(1)).thenReturn(Optional.empty());

    assertThrows(SessionExpiredException.class, () -> {
      service.validateSession(1, 1);
    });
  }

  /**
   * AC1: Session validation fails for wrong account
   */
  @Test
  void validateSessionThrowsForWrongAccount() {
    SessionEntity session = new SessionEntity(2, LocalDateTime.now().plusMinutes(30));
    when(sessionRepository.findBySessionId(1)).thenReturn(Optional.of(session));

    assertThrows(SessionExpiredException.class, () -> {
      service.validateSession(1, 1);
    });
  }

  /**
   * AC2: Create session returns valid session with expiry
   */
  @Test
  void createSessionReturnsValidSession() {
    SessionEntity newSession = new SessionEntity(1, LocalDateTime.now().plusMinutes(30));
    when(sessionRepository.save(any())).thenReturn(newSession);

    SessionDto result = service.createSession(1);

    assertNotNull(result);
    assertEquals(1, result.getAccountId());
    assertTrue(result.getIsActive());
  }
}
