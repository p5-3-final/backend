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
      "vlm.media.jobs.scheduling-enabled=false"
    })
@AutoConfigureMockMvc
// 테스트에서는 Spring Boot가 추적을 꺼 두므로, 운영처럼 traceparent를 이어 받도록 켠다.
@AutoConfigureObservability(metrics = false)
@Import(TestcontainersConfiguration.class)
@Sql(
    statements = "TRUNCATE outbox_events, videos RESTART IDENTITY",
    executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
public @interface IntegrationTest {}
