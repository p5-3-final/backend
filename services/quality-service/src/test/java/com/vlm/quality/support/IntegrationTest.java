package com.vlm.quality.support;

import com.vlm.quality.TestcontainersConfiguration;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.jdbc.Sql;

/** quality-service 통합 테스트 공통 설정. 테스트마다 테이블을 비워 서로 상태를 공유하지 않게 한다. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(
    properties = {
      // 주기 작업은 테스트가 직접 불러 시점을 정한다.
      "vlm.quality.jobs.scheduling-enabled=false",
      // media 재시도 대기를 줄여 실패 경로 테스트가 빨리 끝나게 한다.
      "vlm.quality.media.retry-wait=10ms",
      // 테스트마다 실패를 일부러 내므로 서킷이 다른 테스트에 번지지 않게 사실상 끈다.
      "vlm.quality.media.circuit-minimum-calls=100000"
    })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Sql(
    scripts = "classpath:sql/clean-tables.sql",
    executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
public @interface IntegrationTest {}
