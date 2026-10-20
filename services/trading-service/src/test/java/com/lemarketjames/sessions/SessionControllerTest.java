package com.lemarketjames.sessions;

import com.lemarketjames.sessions.dto.SessionDto;
import com.lemarketjames.sessions.exception.SessionExpiredException;
import com.lemarketjames.sessions.service.SessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SessionController.class)
class SessionControllerTest {
  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private SessionService sessionService;

  /**
   * AC1: Validate session endpoint returns 200 for valid JWT token
   */
  @Test
  @WithMockUser
  void validateSessionReturns200ForValidToken() throws Exception {
    SessionDto validSession = new SessionDto(1, 1, LocalDateTime.now().plusMinutes(30), true);
    when(sessionService.validateSession(anyInt(), anyString())).thenReturn(validSession);

    mockMvc.perform(post("/api/sessions/validate?accountId=1")
        .header("Authorization", "Bearer valid.token.here")
        .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));
  }

  /**
   * AC1: Validate session endpoint returns 401 for expired JWT token
   */
  @Test
  @WithMockUser
  void validateSessionReturns401ForExpiredToken() throws Exception {
    when(sessionService.validateSession(anyInt(), anyString()))
        .thenThrow(new SessionExpiredException("Session token has expired"));

    mockMvc.perform(post("/api/sessions/validate?accountId=1")
        .header("Authorization", "Bearer expired.token.here")
        .with(csrf()))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.success").value(false));
  }
}

