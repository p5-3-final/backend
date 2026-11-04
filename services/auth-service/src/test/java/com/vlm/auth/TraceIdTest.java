package com.vlm.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vlm.auth.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class TraceIdTest {

  // W3C trace context 예시 값(trace-id 32자 hex)
  private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
  private static final String TRACEPARENT = "00-" + TRACE_ID + "-00f067aa0ba902b7-01";

  @Autowired private MockMvc mockMvc;

  @Test
  @DisplayName("[SPEC-001 NFR-4] 오류 응답에 추적 ID를 넣는다")
  void errorResponseHasTraceId() throws Exception {
    mockMvc
        .perform(get("/api/v1/auth/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(
            jsonPath("$.traceId").value(org.hamcrest.Matchers.matchesPattern("[0-9a-f]{32}")));
  }

  @Test
  @DisplayName("[SPEC-001 NFR-4] 게이트웨이가 넘긴 traceparent의 추적 ID를 이어 쓴다")
  void continuesIncomingTrace() throws Exception {
    mockMvc
        .perform(get("/api/v1/auth/me").header("traceparent", TRACEPARENT))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.traceId").value(TRACE_ID));
  }
}
