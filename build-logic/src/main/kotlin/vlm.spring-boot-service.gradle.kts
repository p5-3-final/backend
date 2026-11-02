// services/ 모듈용. 실행 가능한 Spring Boot 애플리케이션과 공통 테스트 의존성을 설정한다.
plugins {
    id("vlm.java-conventions")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

val libs = the<VersionCatalogsExtension>().named("libs")

dependencyManagement {
    imports {
        mavenBom(
            libs
                .findLibrary("spring-cloud-dependencies")
                .get()
                .get()
                .toString(),
        )
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    // W3C traceparent를 이어 받고 넘긴다. 오류 응답과 로그의 traceId도 여기서 나온다(AGENTS.md 관측성).
    implementation("io.micrometer:micrometer-tracing-bridge-otel")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(libs.findLibrary("archunit-junit5").get())
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
