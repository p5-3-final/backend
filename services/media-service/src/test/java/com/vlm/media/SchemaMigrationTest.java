package com.vlm.media;

import static org.assertj.core.api.Assertions.assertThat;

import com.vlm.media.support.IntegrationTest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
}
