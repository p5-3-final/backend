package com.vlm.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vlm.auth.domain.PasswordHasher;
import com.vlm.auth.domain.UserRepository;
import com.vlm.auth.support.AuthApi;
import com.vlm.auth.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

// 기동할 때 만든 관리자는 테스트마다 테이블을 비울 때 지워지므로, 테스트에서는 runner를 직접 실행한다.
class InitialAdminTest {

  private static final String ADMIN_EMAIL = "admin@example.com";
  private static final String ADMIN_PASSWORD = "admin1234";

  private static int adminCount(JdbcTemplate jdbcTemplate) {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM users WHERE role = 'ADMIN'", Integer.class);
  }

  private static void run(InitialAdminCreator creator) throws Exception {
    creator.run(new DefaultApplicationArguments());
  }

  @Nested
  @IntegrationTest
  @TestPropertySource(
      properties = {"AUTH_ADMIN_EMAIL=Admin@Example.com", "AUTH_ADMIN_PASSWORD=" + ADMIN_PASSWORD})
  class WithCredentials {

    @Autowired private InitialAdminCreator creator;
    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("[SPEC-001 R-31] 관리자가 없으면 설정한 이메일과 비밀번호로 ADMIN 계정을 만든다")
    void createsAdmin() throws Exception {
      run(creator);

      assertThat(adminCount(jdbcTemplate)).isEqualTo(1);
      assertThat(
              jdbcTemplate.queryForObject(
                  "SELECT email FROM users WHERE role = 'ADMIN'", String.class))
          .isEqualTo(ADMIN_EMAIL);
      AuthApi.logIn(mockMvc, ADMIN_EMAIL, ADMIN_PASSWORD).andExpect(status().isOk());
    }

    @Test
    @DisplayName("[SPEC-001 R-31] 관리자가 이미 있으면 더 만들지 않는다")
    void skipsWhenAdminExists() throws Exception {
      run(creator);
      run(creator);

      assertThat(adminCount(jdbcTemplate)).isEqualTo(1);
    }

    @Test
    @DisplayName("[SPEC-001 R-31] 설정한 이메일로 일반 계정이 있으면 관리자로 바꾸지 않고 건너뛴다")
    void skipsWhenEmailTaken() throws Exception {
      AuthApi.signUp(mockMvc, ADMIN_EMAIL, "secret123");

      run(creator);

      assertThat(adminCount(jdbcTemplate)).isZero();
      AuthApi.logIn(mockMvc, ADMIN_EMAIL, ADMIN_PASSWORD)
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"));
    }
  }

  // 이 설정으로는 테스트 컨텍스트부터 시작하지 못하므로(의도한 동작) 의존성을 직접 넣어 확인한다.
  @Nested
  class WithWeakPassword {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final InitialAdminCreator creator =
        new InitialAdminCreator(
            new InitialAdminProperties(ADMIN_EMAIL, "short"),
            userRepository,
            mock(PasswordHasher.class));

    @Test
    @DisplayName("[SPEC-001 R-31] 비밀번호가 정책에 맞지 않으면 관리자를 만들지 않고 시작을 멈춘다")
    void rejectsWeakPassword() {
      assertThatThrownBy(() -> run(creator)).isInstanceOf(IllegalStateException.class);
      verify(userRepository, never()).save(any());
    }
  }

  @Nested
  @IntegrationTest
  @ExtendWith(OutputCaptureExtension.class)
  class WithoutCredentials {

    @Autowired private InitialAdminCreator creator;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("[SPEC-001 R-31] 이메일이나 비밀번호 설정이 없으면 경고를 남기고 건너뛴다")
    void warnsAndSkips(CapturedOutput output) throws Exception {
      run(creator);

      assertThat(adminCount(jdbcTemplate)).isZero();
      assertThat(output).contains("WARN").contains("AUTH_ADMIN_EMAIL");
    }
  }
}
