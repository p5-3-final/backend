package com.vlm.auth.token.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vlm.auth.support.IntegrationTest;
import com.vlm.auth.support.TestSigningKeys;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class JwksApiTest {

  @Autowired private MockMvc mockMvc;

  @Test
  @DisplayName("[SPEC-001 R-11] 공개키 목록에 현재 서명 키와 교체 중인 이전 키의 공개키를 함께 반환한다")
  void returnsCurrentAndPreviousPublicKeys() throws Exception {
    mockMvc
        .perform(get("/.well-known/jwks.json"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.keys.length()").value(2))
        .andExpect(
            jsonPath("$.keys[*].kid")
                .value(
                    Matchers.containsInAnyOrder(
                        TestSigningKeys.CURRENT.getKeyID(), TestSigningKeys.PREVIOUS.getKeyID())))
        .andExpect(jsonPath("$.keys[0].kty").value("EC"))
        .andExpect(jsonPath("$.keys[0].crv").value("P-256"))
        .andExpect(jsonPath("$.keys[0].alg").value("ES256"))
        .andExpect(jsonPath("$.keys[0].use").value("sig"));
  }

  @Test
  @DisplayName("[SPEC-001 R-11] 공개키 목록에 개인키 값(d)을 넣지 않는다")
  void doesNotExposePrivateKey() throws Exception {
    mockMvc
        .perform(get("/.well-known/jwks.json"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.keys[*].d").isEmpty());
  }
}
