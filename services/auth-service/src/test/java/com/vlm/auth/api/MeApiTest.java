package com.vlm.auth.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vlm.auth.support.AuthApi;
import com.vlm.auth.support.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class MeApiTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;

  private String publicId;

  @BeforeEach
  void signUp() throws Exception {
    AuthApi.signUp(mockMvc, "user@example.com", "secret123");
    publicId = jdbcTemplate.queryForObject("SELECT public_id::text FROM users", String.class);
  }

  @Test
  @DisplayName("[SPEC-001 R-10] 게이트웨이가 넘긴 사용자 ID로 내 정보(ID, 이메일, 닉네임, 역할)를 반환한다")
  void returnsMyInfo() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/auth/me").header("X-User-Id", publicId).header("X-User-Roles", "USER"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(publicId))
        .andExpect(jsonPath("$.email").value("user@example.com"))
        .andExpect(jsonPath("$.nickname").value("테스터"))
        .andExpect(jsonPath("$.roles[0]").value("USER"));
  }

  @Test
  @DisplayName("[SPEC-001 R-10] 사용자 ID 헤더가 없으면 401 AUTH_TOKEN_MISSING을 반환한다")
  void missingUserHeader() throws Exception {
    mockMvc
        .perform(get("/api/v1/auth/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH_TOKEN_MISSING"));
  }

  @Test
  @DisplayName("[SPEC-001 R-10] 사용자 ID에 해당하는 계정이 없으면 401 AUTH_TOKEN_INVALID를 반환한다")
  void unknownUser() throws Exception {
    mockMvc
        .perform(get("/api/v1/auth/me").header("X-User-Id", UUID.randomUUID().toString()))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH_TOKEN_INVALID"));
  }
}
