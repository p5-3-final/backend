package com.vlm.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vlm.media.support.IntegrationTest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import software.amazon.awssdk.services.s3.S3Client;

@IntegrationTest
class SchemaMigrationTest {

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private S3Client s3;

  @Test
  @DisplayName("기동하면 Flyway가 videos, outbox_events 테이블을 만들고 저장소 버킷에 닿는다")
  void createsMediaTablesAndReachesStorage() {
    List<String> tables =
        jdbcTemplate.queryForList(
            "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
            String.class);

    assertThat(tables).contains("videos", "outbox_events");
    assertThat(
            s3.headBucket(b -> b.bucket(TestcontainersConfiguration.BUCKET))
                .sdkHttpResponse()
                .isSuccessful())
        .isTrue();
  }

  @Test
  @DisplayName("[SPEC-030 R-5] 영상에 보존 등급 열이 있고 기존 영상은 STANDARD다")
  void createsRetentionGrade() {
    String defaultGrade =
        jdbcTemplate.queryForObject(
            "SELECT column_default FROM information_schema.columns"
                + " WHERE table_name = 'videos' AND column_name = 'retention_grade'",
            String.class);

    assertThat(defaultGrade).startsWith("'STANDARD'");
  }

  @Test
  @DisplayName("[SPEC-017] 작업 세션 테이블과 영상의 세션·역할 컬럼을 만든다")
  void createsWorkSessionColumns() {
    List<String> columns =
        jdbcTemplate.queryForList(
            "SELECT column_name FROM information_schema.columns WHERE table_name = 'videos'",
            String.class);

    assertThat(
            jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
                String.class))
        .contains("work_sessions");
    assertThat(columns).contains("session_id", "session_role");
  }

  @Test
  @DisplayName("[SPEC-017] 영상의 세션과 역할은 함께 있거나 함께 없어야 한다")
  void sessionAndRoleGoTogether() {
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    """
                    INSERT INTO videos (public_id, owner_id, file_name, size_bytes, content_type,
                        status, storage_key, expires_at, session_role)
                    VALUES (gen_random_uuid(), gen_random_uuid(), 'a.mp4', 1, 'video/mp4',
                        'UPLOADING', 'k', now(), 'BEFORE')
                    """))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("[SPEC-018] 보존 설정·최소 기록 테이블과 영상 동의 컬럼(기본 false)을 만든다")
  void createsRetentionTables() {
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
                String.class))
        .contains("retention_settings", "retained_videos");
    assertThat(
            jdbcTemplate.queryForObject(
                """
                SELECT column_default FROM information_schema.columns
                WHERE table_name = 'videos' AND column_name = 'retention_consent'
                """,
                String.class))
        .isEqualTo("false");
  }
}
