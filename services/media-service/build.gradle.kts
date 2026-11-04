plugins {
    id("vlm.spring-boot-service")
}

dependencies {
    implementation(project(":libs:common-web"))
    implementation(project(":libs:common-messaging"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation(platform(libs.aws.sdk.bom))
    // 운영 S3와 로컬·테스트 SeaweedFS를 같은 클라이언트로 쓴다(ADR-0006).
    implementation("software.amazon.awssdk:s3")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.kafka)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.swagger.request.validator.mockmvc)
    testImplementation(libs.json.schema.validator)
}

// 계약 원본은 docs 레포에 있다. 로컬은 작업 공간 구조(project/repos/backend)를 따르고, CI는 CONTRACTS_DIR로 넘긴다.
tasks.test {
    val contractsDir =
        providers
            .environmentVariable("CONTRACTS_DIR")
            .orElse(rootDir.resolve("../../service-contracts").canonicalPath)
    systemProperty("vlm.contracts.dir", contractsDir.get())
}
