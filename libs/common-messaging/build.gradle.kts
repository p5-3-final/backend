plugins {
    id("vlm.library-conventions")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    api("org.springframework.kafka:spring-kafka")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    // traceparent를 Outbox에 남길 때만 쓴다. 추적이 없는 서비스에서도 동작하도록 선택 의존성으로 둔다.
    compileOnly("io.micrometer:micrometer-tracing")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.kafka)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation("org.springframework.boot:spring-boot-starter-actuator")
    testImplementation("io.micrometer:micrometer-tracing-bridge-otel")
    testRuntimeOnly("org.postgresql:postgresql")
}
