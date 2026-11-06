package com.vlm.quality.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vlm.quality.support.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class OperatorRoleTest {

  @Autowired MockMvc mvc;

  @Test
  @DisplayName("[SPEC-029 R-4] X-User-Roles에 OPERATOR가 없으면 403 QUALITY_FORBIDDEN이다")
  void rejectsWithoutOperator() throws Exception {
    mvc.perform(
            get("/api/v1/quality/review-items")
                .header("X-User-Id", UUID.randomUUID().toString())
                .header("X-User-Roles", "USER,ADMIN"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("QUALITY_FORBIDDEN"));
  }

  @Test
  @DisplayName("[SPEC-029 R-4] X-User-Id가 없으면 401이다")
  void rejectsWithoutUser() throws Exception {
    mvc.perform(get("/api/v1/quality/golden-sets").header("X-User-Roles", "OPERATOR"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH_TOKEN_MISSING"));
  }
}
