package com.vlm.media.support;

import com.vlm.media.TestcontainersConfiguration;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.jdbc.Sql;

/** media-service 통합 테스트 공통 설정. 테스트마다 테이블을 비워 서로 상태를 공유하지 않게 한다. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(
    properties = {
      // 주기 작업은 테스트가 직접 불러 시점을 정한다.
      "vlm.outbox.scheduling-enabled=false",
      "vlm.media.jobs.scheduling-enabled=false",
      // 큰 파일 없이 멀티파트를 확인하려고 기준값을 낮춘다. 조각은 S3 최소 크기(5MiB)다. 기본값은 계약 값(100MB, 64MiB).
      "vlm.media.upload.single-max-bytes=1048576",
      "vlm.media.upload.part-size-bytes=5242880",
      // 테스트 전용 보존 키 비밀 키(0x00..0x1f 32바이트)
      "vlm.media.retention.hmac-key=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8="
    })
@AutoConfigureMockMvc
// 테스트에서는 Spring Boot가 추적을 꺼 두므로, 운영처럼 traceparent를 이어 받도록 켠다.
@AutoConfigureObservability(metrics = false)
@Import(TestcontainersConfiguration.class)
@Sql(
    statements =
        "TRUNCATE outbox_events, videos, work_sessions, retention_settings, retained_videos RESTART IDENTITY",
    executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
public @interface IntegrationTest {}
