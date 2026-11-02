package com.vlm.common.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vlm.common.error.BusinessException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

class ProblemDetailExceptionHandlerTest {

  private static final String VALIDATION_CODE = "SAMPLE_INVALID_REQUEST";

  private MockMvc mockMvc;

  @RestController
  static class SampleController {

    record SampleRequest(@Email String email) {}

    @GetMapping("/business")
    String business() {
      throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "잘못된 값입니다.");
    }

    @GetMapping("/unexpected")
    String unexpected() {
      throw new IllegalStateException("database password is wrong");
    }

    @PostMapping("/validated")
    String validated(@Valid @RequestBody SampleRequest request) {
      return request.email();
    }
  }

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new SampleController())
            .setControllerAdvice(new ProblemDetailExceptionHandler(VALIDATION_CODE))
            .build();
  }

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  @DisplayName("비즈니스 예외는 오류 코드의 상태와 code로 problem+json을 반환한다")
  void businessException() throws Exception {
    mockMvc
        .perform(get("/business"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.title").value("Bad Request"))
        .andExpect(jsonPath("$.detail").value("잘못된 값입니다."))
        .andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST"));
  }

  @Test
  @DisplayName("입력 검증 실패는 서비스가 정한 code와 필드별 오류를 담아 400을 반환한다")
  void validationFailure() throws Exception {
    mockMvc
        .perform(
            post("/validated")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"not-an-email\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value(VALIDATION_CODE))
        .andExpect(jsonPath("$.errors[0].field").value("email"));
  }

  @Test
  @DisplayName("JSON 본문을 읽을 수 없으면 서비스가 정한 code로 400을 반환한다")
  void unreadableBody() throws Exception {
    mockMvc
        .perform(post("/validated").contentType(MediaType.APPLICATION_JSON).content("{broken"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(VALIDATION_CODE));
  }

  @Test
  @DisplayName("예상하지 못한 예외는 내부 메시지를 숨기고 500을 반환한다")
  void unexpectedException() throws Exception {
    mockMvc
        .perform(get("/unexpected"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("COMMON_INTERNAL_ERROR"))
        .andExpect(jsonPath("$.detail").value("요청을 처리하지 못했습니다."));
  }

  @Test
  @DisplayName("추적 ID가 있으면 오류 응답에 traceId로 포함한다")
  void includesTraceId() throws Exception {
    MDC.put("traceId", "4bf92f3577b34da6a3ce929d0e0e4736");

    mockMvc
        .perform(get("/business"))
        .andExpect(jsonPath("$.traceId").value("4bf92f3577b34da6a3ce929d0e0e4736"));
  }
}
