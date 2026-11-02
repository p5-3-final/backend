package com.vlm.auth.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers;
import com.atlassian.oai.validator.report.LevelResolver;
import com.atlassian.oai.validator.report.ValidationReport;
import com.vlm.auth.support.AuthApi;
import com.vlm.auth.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** auth-service 요청·응답이 docs 레포 계약 {@code auth-service.yaml}과 일치하는지 검증한다(T050). */
@IntegrationTest
class AuthContractTest {

  private static final String EMAIL = "user@example.com";
  private static final String PASSWORD = "secret123";

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;

  private ResultMatcher matchesContract;
  private ResultMatcher responseMatchesContract;

  @BeforeEach
  void loadContract() {
    // 계약 파일이 없으면 검증 없이 통과하지 않도록 바로 실패시킨다.
    Path contract =
        Path.of(
            Objects.requireNonNull(
                System.getProperty("vlm.contracts.dir"), "vlm.contracts.dir is not set"),
            "openapi",
            "auth-service.yaml");
    assertThat(Files.isRegularFile(contract)).as("contract file %s", contract).isTrue();
    matchesContract = OpenApiValidationMatchers.openApi().isValid(contract.toString());
    // 일부러 계약을 어긴 요청을 보내는 실패 케이스는 응답만 검증한다.
    OpenApiInteractionValidator responseOnly =
        OpenApiInteractionValidator.createForSpecificationUrl(contract.toString())
            .withLevelResolver(
                LevelResolver.create()
                    .withLevel("validation.request", ValidationReport.Level.IGNORE)
                    .build())
            .build();
    responseMatchesContract = OpenApiValidationMatchers.openApi().isValid(responseOnly);
  }

  @Test
  @DisplayName("[SPEC-001 R-1] 가입 성공 응답이 계약과 일치한다")
  void signUpCreated() throws Exception {
    perform(signUpRequest(EMAIL, PASSWORD));
  }

  @Test
  @DisplayName("[SPEC-001 R-3] 가입 요청 형식 오류 응답이 계약과 일치한다")
  void signUpBadRequest() throws Exception {
    performInvalidRequest(signUpRequest("not-an-email", PASSWORD));
  }

  @Test
  @DisplayName("[SPEC-001 R-2] 중복 이메일 응답이 계약과 일치한다")
  void signUpConflict() throws Exception {
    AuthApi.signUp(mockMvc, EMAIL, PASSWORD);

    perform(signUpRequest(EMAIL, PASSWORD));
  }

  @Test
  @DisplayName("[SPEC-001 R-7] 로그인 성공 응답과 재발급 쿠키가 계약과 일치한다")
  void logInOk() throws Exception {
    AuthApi.signUp(mockMvc, EMAIL, PASSWORD);

    perform(logInRequest(PASSWORD));
  }

  @Test
  @DisplayName("[SPEC-001 R-8] 로그인 실패 응답이 계약과 일치한다")
  void logInUnauthorized() throws Exception {
    AuthApi.signUp(mockMvc, EMAIL, PASSWORD);

    perform(logInRequest("wrong1234"));
  }

  @Test
  @DisplayName("[SPEC-001 R-29] 계정 잠금 응답이 계약과 일치한다")
  void logInLocked() throws Exception {
    AuthApi.signUp(mockMvc, EMAIL, PASSWORD);
    jdbcTemplate.update("UPDATE users SET locked_until = now() + interval '15 minutes'");

    perform(logInRequest(PASSWORD));
  }

  @Test
  @DisplayName("[SPEC-001 R-22] 재발급 성공 응답이 계약과 일치한다")
  void refreshOk() throws Exception {
    perform(refreshRequest(signUpAndLogIn()));
  }

  @Test
  @DisplayName("[SPEC-001 R-24] 재발급 실패 응답이 계약과 일치한다")
  void refreshUnauthorized() throws Exception {
    perform(refreshRequest("unknown-token"));
  }

  @Test
  @DisplayName("[SPEC-001 R-25] 로그아웃 응답과 쿠키 삭제가 계약과 일치한다")
  void logOutNoContent() throws Exception {
    String refreshToken = signUpAndLogIn();

    perform(
        authenticated(post("/api/v1/auth/logout"))
            .cookie(new Cookie("refresh_token", refreshToken)));
  }

  @Test
  @DisplayName("[SPEC-001 R-10] 내 정보 응답이 계약과 일치한다")
  void meOk() throws Exception {
    AuthApi.signUp(mockMvc, EMAIL, PASSWORD);

    perform(authenticated(get("/api/v1/auth/me")));
  }

  @Test
  @DisplayName("[SPEC-001 R-10] 내 정보 인증 실패 응답이 계약과 일치한다")
  void meUnauthorized() throws Exception {
    performInvalidRequest(get("/api/v1/auth/me"));
  }

  @Test
  @DisplayName("[SPEC-001 R-26] 알림 티켓 발급 응답이 계약과 일치한다")
  void sseTicketCreated() throws Exception {
    AuthApi.signUp(mockMvc, EMAIL, PASSWORD);

    perform(authenticated(post("/api/v1/auth/sse-tickets")));
  }

  @Test
  @DisplayName("[SPEC-001 R-11] 공개키 목록 응답이 계약과 일치한다")
  void jwksOk() throws Exception {
    perform(get("/.well-known/jwks.json"));
  }

  private void perform(MockHttpServletRequestBuilder request) throws Exception {
    mockMvc.perform(request).andExpect(matchesContract);
  }

  private void performInvalidRequest(MockHttpServletRequestBuilder request) throws Exception {
    mockMvc.perform(request).andExpect(responseMatchesContract);
  }

  private String signUpAndLogIn() throws Exception {
    AuthApi.signUp(mockMvc, EMAIL, PASSWORD);
    return AuthApi.logInForRefreshToken(mockMvc, EMAIL, PASSWORD);
  }

  private MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder request) {
    String publicId =
        jdbcTemplate.queryForObject("SELECT public_id::text FROM users", String.class);
    return request.header("X-User-Id", publicId).header("X-User-Roles", "USER");
  }

  private static MockHttpServletRequestBuilder signUpRequest(String email, String password) {
    return post("/api/v1/auth/signup")
        .contentType(MediaType.APPLICATION_JSON)
        .content(
            """
            {"email":"%s","password":"%s","nickname":"테스터"}
            """
                .formatted(email, password));
  }

  private static MockHttpServletRequestBuilder logInRequest(String password) {
    return post("/api/v1/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content(
            """
            {"email":"%s","password":"%s"}
            """
                .formatted(EMAIL, password));
  }

  private static MockHttpServletRequestBuilder refreshRequest(String refreshToken) {
    return post("/api/v1/auth/refresh").cookie(new Cookie("refresh_token", refreshToken));
  }
}
