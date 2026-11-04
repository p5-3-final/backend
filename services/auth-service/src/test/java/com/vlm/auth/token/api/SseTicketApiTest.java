package com.vlm.auth.token.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.vlm.auth.support.AuthApi;
import com.vlm.auth.support.IntegrationTest;
import com.vlm.auth.support.TestSigningKeys;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class SseTicketApiTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;

  private String publicId;

  @BeforeEach
  void signUp() throws Exception {
    AuthApi.signUp(mockMvc, "user@example.com", "secret123");
    publicId = jdbcTemplate.queryForObject("SELECT public_id::text FROM users", String.class);
  }

  @Test
  @DisplayName("[SPEC-001 R-26] 인증된 사용자에게 aud가 vlm-sse인 30초 티켓을 201로 발급한다")
  void issuesTicket() throws Exception {
    String body =
        mockMvc
            .perform(
                post("/api/v1/auth/sse-tickets")
                    .header("X-User-Id", publicId)
                    .header("X-User-Roles", "USER"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.expiresIn").value(30))
            .andReturn()
            .getResponse()
            .getContentAsString();

    SignedJWT ticket = SignedJWT.parse(JsonPath.<String>read(body, "$.ticket"));
    assertThat(ticket.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.ES256);
    assertThat(ticket.verify(new ECDSAVerifier(TestSigningKeys.CURRENT.toECPublicKey()))).isTrue();
    JWTClaimsSet claims = ticket.getJWTClaimsSet();
    assertThat(claims.getSubject()).isEqualTo(publicId);
    assertThat(claims.getStringListClaim("roles")).containsExactly("USER");
    assertThat(claims.getIssuer()).isEqualTo(TestSigningKeys.ISSUER);
    assertThat(claims.getAudience()).containsExactly("vlm-sse");
    assertThat(claims.getJWTID()).isNotBlank();
    Map<String, Object> payload = ticket.getPayload().toJSONObject();
    assertThat(
            ((Number) payload.get("exp")).longValue() - ((Number) payload.get("iat")).longValue())
        .isEqualTo(30L);
  }

  @Test
  @DisplayName("[SPEC-001 R-26] 사용자 ID 헤더가 없으면 401 AUTH_TOKEN_MISSING을 반환한다")
  void requiresAuthentication() throws Exception {
    mockMvc
        .perform(post("/api/v1/auth/sse-tickets"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH_TOKEN_MISSING"));
  }
}
