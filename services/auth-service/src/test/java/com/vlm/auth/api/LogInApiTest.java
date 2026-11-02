package com.vlm.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.vlm.auth.support.AuthApi;
import com.vlm.auth.support.IntegrationTest;
import com.vlm.auth.support.TestSigningKeys;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@IntegrationTest
class LogInApiTest {

  private static final String EMAIL = "user@example.com";
  private static final String PASSWORD = "secret123";

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void signUp() throws Exception {
    AuthApi.signUp(mockMvc, EMAIL, PASSWORD);
  }

  @Test
  @DisplayName("[SPEC-001 R-7] 로그인하면 계약 형식의 15분 접근 토큰을 본문으로 반환한다")
  void issuesAccessToken() throws Exception {
    MockHttpServletResponse response =
        AuthApi.logIn(mockMvc, EMAIL, PASSWORD)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.expiresIn").value(900))
            .andReturn()
            .getResponse();

    String token =
        com.jayway.jsonpath.JsonPath.read(response.getContentAsString(), "$.accessToken");
    SignedJWT jwt = SignedJWT.parse(token);
    assertThat(jwt.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.ES256);
    assertThat(jwt.getHeader().getKeyID()).isEqualTo(TestSigningKeys.CURRENT.getKeyID());
    assertThat(jwt.verify(new ECDSAVerifier(TestSigningKeys.CURRENT.toECPublicKey()))).isTrue();

    JWTClaimsSet claims = jwt.getJWTClaimsSet();
    String publicId =
        jdbcTemplate.queryForObject("SELECT public_id::text FROM users", String.class);
    assertThat(claims.getSubject()).isEqualTo(publicId);
    assertThat(claims.getStringListClaim("roles")).containsExactly("USER");
    assertThat(claims.getIssuer()).isEqualTo(TestSigningKeys.ISSUER);
    assertThat(claims.getAudience()).containsExactly("vlm-api");
    assertThat(claims.getJWTID()).isNotBlank();
    // Nimbus는 시각을 Date로 주므로 원본 JSON의 epoch 초(exp, iat)로 비교한다.
    Map<String, Object> payload = jwt.getPayload().toJSONObject();
    assertThat(
            ((Number) payload.get("exp")).longValue() - ((Number) payload.get("iat")).longValue())
        .isEqualTo(900L);
  }

  @Test
  @DisplayName("[SPEC-001 R-7] 로그인하면 14일 재발급 토큰을 HttpOnly·Secure·SameSite 쿠키로 주고 해시만 저장한다")
  void issuesRefreshTokenCookie() throws Exception {
    MockHttpServletResponse response =
        AuthApi.logIn(mockMvc, EMAIL, PASSWORD)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse();

    String setCookie = response.getHeader("Set-Cookie");
    assertThat(setCookie)
        .startsWith("refresh_token=")
        .contains("Path=/api/v1/auth")
        .contains("Max-Age=1209600")
        .contains("HttpOnly")
        .contains("Secure")
        .contains("SameSite=Strict");

    String rawToken = setCookie.substring("refresh_token=".length(), setCookie.indexOf(';'));
    String storedHash =
        jdbcTemplate.queryForObject("SELECT token_hash FROM refresh_tokens", String.class);
    assertThat(storedHash).isEqualTo(sha256Hex(rawToken)).isNotEqualTo(rawToken);
  }

  @Test
  @DisplayName("[SPEC-001 R-8] 없는 이메일과 틀린 비밀번호를 구분하지 않고 같은 401을 반환한다")
  void invalidCredentialsAreIndistinguishable() throws Exception {
    String unknownEmail =
        AuthApi.logIn(mockMvc, "nobody@example.com", PASSWORD)
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String wrongPassword =
        AuthApi.logIn(mockMvc, EMAIL, "wrong1234")
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(unknownEmail).isEqualTo(wrongPassword);
    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM refresh_tokens", Integer.class))
        .isZero();
  }

  @Test
  @DisplayName("[SPEC-001 R-9] 로그인 시도마다 시각, 대상 계정, 성공 여부, 요청 IP를 기록한다")
  void recordsLoginAttempts() throws Exception {
    Long userId = jdbcTemplate.queryForObject("SELECT id FROM users", Long.class);

    logInFrom("203.0.113.7", EMAIL, PASSWORD).andExpect(status().isOk());
    logInFrom("203.0.113.8", EMAIL, "wrong1234").andExpect(status().isUnauthorized());
    logInFrom("203.0.113.9", "nobody@example.com", PASSWORD).andExpect(status().isUnauthorized());

    List<Map<String, Object>> rows =
        jdbcTemplate.queryForList(
            "SELECT user_id, success, client_ip, occurred_at FROM login_audits ORDER BY id");
    assertThat(rows).hasSize(3);
    assertThat(rows.get(0))
        .containsEntry("user_id", userId)
        .containsEntry("success", true)
        .containsEntry("client_ip", "203.0.113.7");
    assertThat(rows.get(1))
        .containsEntry("user_id", userId)
        .containsEntry("success", false)
        .containsEntry("client_ip", "203.0.113.8");
    assertThat(rows.get(2))
        .containsEntry("user_id", null)
        .containsEntry("success", false)
        .containsEntry("client_ip", "203.0.113.9");
    assertThat(rows).allSatisfy(row -> assertThat(row.get("occurred_at")).isNotNull());
  }

  // 게이트웨이를 거친 요청처럼 클라이언트 IP를 X-Forwarded-For로 넘긴다.
  private ResultActions logInFrom(String clientIp, String email, String password) throws Exception {
    return mockMvc.perform(
        post("/api/v1/auth/login")
            .header("X-Forwarded-For", clientIp)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {"email":"%s","password":"%s"}
                """
                    .formatted(email, password)));
  }

  private static String sha256Hex(String value) throws Exception {
    byte[] digest =
        MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
    return HexFormat.of().formatHex(digest);
  }

  @Test
  @DisplayName("[SPEC-001 R-29] 연속 5회 실패하면 올바른 비밀번호여도 423 AUTH_ACCOUNT_LOCKED를 반환한다")
  void locksAfterFiveFailures() throws Exception {
    for (int i = 0; i < 5; i++) {
      AuthApi.logIn(mockMvc, EMAIL, "wrong1234").andExpect(status().isUnauthorized());
    }

    AuthApi.logIn(mockMvc, EMAIL, PASSWORD)
        .andExpect(status().isLocked())
        .andExpect(jsonPath("$.code").value("AUTH_ACCOUNT_LOCKED"));
  }

  @Test
  @DisplayName("[SPEC-001 R-29] 잠금 시간이 지나면 다시 로그인할 수 있다")
  void unlocksAfterLockPeriod() throws Exception {
    for (int i = 0; i < 5; i++) {
      AuthApi.logIn(mockMvc, EMAIL, "wrong1234").andExpect(status().isUnauthorized());
    }
    jdbcTemplate.update("UPDATE users SET locked_until = now() - interval '1 second'");

    AuthApi.logIn(mockMvc, EMAIL, PASSWORD).andExpect(status().isOk());
  }

  @Test
  @DisplayName("[SPEC-001 R-29] 로그인에 성공하면 실패 횟수를 초기화한다")
  void successResetsFailureCount() throws Exception {
    for (int i = 0; i < 4; i++) {
      AuthApi.logIn(mockMvc, EMAIL, "wrong1234").andExpect(status().isUnauthorized());
    }

    AuthApi.logIn(mockMvc, EMAIL, PASSWORD).andExpect(status().isOk());

    assertThat(jdbcTemplate.queryForObject("SELECT failed_login_count FROM users", Integer.class))
        .isZero();
  }
}
