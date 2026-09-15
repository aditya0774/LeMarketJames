package com.lemarketjames.sessions;

import com.lemarketjames.sessions.dto.SessionDto;
import com.lemarketjames.sessions.exception.SessionExpiredException;
import com.lemarketjames.sessions.service.SessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SessionControllerTest {
  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private SessionService sessionService;

  /**
   * AC1: Validate session endpoint returns 200 for valid session
   */
  @Test
  @WithMockUser
  void validateSessionReturns200ForValidSession() throws Exception {
    SessionDto validSession = new SessionDto(1, 1, LocalDateTime.now().plusMinutes(30), true);
    when(sessionService.validateSession(1, 1)).thenReturn(validSession);

    mockMvc.perform(post("/api/sessions/validate")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"sessionId\": 1, \"accountId\": 1}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));
  }

  /**
   * AC1: Validate session endpoint returns 401 for expired session
   */
  @Test
  @WithMockUser
  void validateSessionReturns401ForExpiredSession() throws Exception {
    when(sessionService.validateSession(any(), any()))
        .thenThrow(new SessionExpiredException("Session has expired"));

    mockMvc.perform(post("/api/sessions/validate")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"sessionId\": 1, \"accountId\": 1}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.success").value(false));
  }

  /**
   * AC2: Create session endpoint returns 201 with new session
   */
  @Test
  @WithMockUser
  void createSessionReturns201WithNewSession() throws Exception {
    SessionDto newSession = new SessionDto(1, 1, LocalDateTime.now().plusMinutes(30), true);
    when(sessionService.createSession(1)).thenReturn(newSession);

    mockMvc.perform(post("/api/sessions/create?accountId=1"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true));
  }
}
