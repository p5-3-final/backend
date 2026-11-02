package com.vlm.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SchemaMigrationTest {

  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("기동하면 Flyway가 users, refresh_tokens, login_audits 테이블을 만든다")
  void createsAuthTables() {
    List<String> tables =
        jdbcTemplate.queryForList(
            "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
            String.class);

    assertThat(tables).contains("users", "refresh_tokens", "login_audits");
  }
}
