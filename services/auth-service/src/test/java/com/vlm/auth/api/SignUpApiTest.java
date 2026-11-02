package com.vlm.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vlm.auth.support.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@IntegrationTest
class SignUpApiTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;

  private ResultActions signUp(String email, String password, String nickname) throws Exception {
    String body =
        """
        {"email":"%s","password":"%s","nickname":"%s"}
        """
            .formatted(email, password, nickname);
    return mockMvc.perform(
        post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body));
  }

  @Test
  @DisplayName("[SPEC-001 R-1] 가입되지 않은 이메일로 가입하면 USER 계정을 만들고 201을 반환한다")
  void signUpCreatesUser() throws Exception {
    signUp("user@example.com", "secret123", "영상분석가")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").isString())
        .andExpect(jsonPath("$.email").value("user@example.com"))
        .andExpect(jsonPath("$.nickname").value("영상분석가"))
        .andExpect(jsonPath("$.roles[0]").value("USER"));

    Map<String, Object> row = jdbcTemplate.queryForMap("SELECT email, nickname, role FROM users");
    assertThat(row)
        .containsEntry("email", "user@example.com")
        .containsEntry("nickname", "영상분석가")
        .containsEntry("role", "USER");
  }

  @Test
  @DisplayName("[SPEC-001 R-2] 이미 가입된 이메일이면 409와 AUTH_EMAIL_DUPLICATED를 반환한다")
  void duplicatedEmail() throws Exception {
    signUp("user@example.com", "secret123", "첫번째").andExpect(status().isCreated());

    signUp("user@example.com", "secret456", "두번째")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("AUTH_EMAIL_DUPLICATED"));
  }

  @Test
  @DisplayName("[SPEC-001 R-2] 대소문자만 다른 이메일도 중복으로 본다")
  void duplicatedEmailIgnoringCase() throws Exception {
    signUp("user@example.com", "secret123", "첫번째").andExpect(status().isCreated());

    signUp("User@Example.COM", "secret456", "두번째")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("AUTH_EMAIL_DUPLICATED"));
  }

  @Test
  @DisplayName("[SPEC-001 R-2] 같은 이메일로 동시에 가입해도 하나만 성공하고 나머지는 409를 받는다")
  void concurrentSignUp() throws Exception {
    int attempts = 4;
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(attempts);
    try {
      Callable<Integer> task =
          () -> {
            start.await();
            return signUp("race@example.com", "secret123", "동시가입")
                .andReturn()
                .getResponse()
                .getStatus();
          };
      List<Future<Integer>> futures = new ArrayList<>();
      for (int i = 0; i < attempts; i++) {
        futures.add(executor.submit(task));
      }
      start.countDown();
      List<Integer> statuses = new ArrayList<>();
      for (Future<Integer> future : futures) {
        statuses.add(future.get());
      }

      assertThat(statuses).containsOnly(201, 409);
      assertThat(statuses).filteredOn(code -> code == 201).hasSize(1);
    } finally {
      executor.shutdownNow();
    }
  }

  @ParameterizedTest(name = "이메일: \"{0}\"")
  @ValueSource(strings = {"not-an-email", "user@", "@example.com", ""})
  @DisplayName("[SPEC-001 R-3] 이메일 형식이 올바르지 않으면 400과 AUTH_INVALID_REQUEST를 반환한다")
  void invalidEmail(String email) throws Exception {
    signUp(email, "secret123", "영상분석가")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("AUTH_INVALID_REQUEST"));

    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM users", Integer.class)).isZero();
  }

  @Test
  @DisplayName("[SPEC-001 R-4] 비밀번호가 정책에 맞지 않으면 400과 AUTH_PASSWORD_POLICY를 반환한다")
  void passwordPolicyViolation() throws Exception {
    signUp("user@example.com", "onlyletters", "영상분석가")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("AUTH_PASSWORD_POLICY"));

    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM users", Integer.class)).isZero();
  }

  @ParameterizedTest(name = "닉네임: \"{0}\"")
  @ValueSource(strings = {"가", "가나다라마바사아자차카타파하가나다라마바사", "닉네임!", "nick name", "ㄱㄴㄷ", ""})
  @DisplayName("[SPEC-001 R-5] 닉네임이 2~20자가 아니거나 한글·영문·숫자 외 문자를 포함하면 400을 반환한다")
  void invalidNickname(String nickname) throws Exception {
    signUp("user@example.com", "secret123", nickname)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("AUTH_INVALID_REQUEST"));
  }

  @ParameterizedTest(name = "닉네임: \"{0}\"")
  @ValueSource(strings = {"가나", "Analyst99", "가나다라마바사아자차카타파하가나다라마바"})
  @DisplayName("[SPEC-001 R-5] 2~20자의 한글·영문·숫자 닉네임은 허용한다")
  void validNickname(String nickname) throws Exception {
    signUp("user@example.com", "secret123", nickname).andExpect(status().isCreated());
  }

  @Test
  @DisplayName("[SPEC-001 R-6] 비밀번호는 원문이 아닌 BCrypt 해시로만 저장한다")
  void storesPasswordHashOnly() throws Exception {
    signUp("user@example.com", "secret123", "영상분석가").andExpect(status().isCreated());

    String stored = jdbcTemplate.queryForObject("SELECT password_hash FROM users", String.class);
    assertThat(stored).isNotEqualTo("secret123").doesNotContain("secret123");
    assertThat(new BCryptPasswordEncoder().matches("secret123", stored)).isTrue();
  }
}
