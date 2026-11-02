package com.vlm.auth.support;

import com.vlm.auth.TestcontainersConfiguration;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.jdbc.Sql;

/** auth-service 통합 테스트 공통 설정. 테스트마다 테이블을 비워 서로 상태를 공유하지 않게 한다. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Sql(
    statements = "TRUNCATE login_audits, refresh_tokens, users RESTART IDENTITY CASCADE",
    executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
public @interface IntegrationTest {}
