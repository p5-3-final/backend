plugins {
    id("vlm.spring-boot-service")
}

dependencies {
    implementation(project(":libs:common-web"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.security:spring-security-crypto")
    implementation("org.springframework.security:spring-security-oauth2-jose")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.swagger.request.validator.mockmvc)
}

// 계약 원본은 docs 레포에 있다. 로컬은 작업 공간 구조(project/repos/backend)를 따르고, CI는 CONTRACTS_DIR로 넘긴다.
tasks.test {
    val contractsDir =
        providers
            .environmentVariable("CONTRACTS_DIR")
            .orElse(rootDir.resolve("../../docs/contracts").canonicalPath)
    systemProperty("vlm.contracts.dir", contractsDir.get())
}

// 로컬 실행용 서명 키(개인키 포함 JWK)를 출력한다. 값은 .env의 AUTH_JWT_SIGNING_KEY에 넣는다.
tasks.register<JavaExec>("generateSigningKey") {
    group = "application"
    description = "Prints a new EC P-256 signing JWK for local runs."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.vlm.auth.tools.SigningKeyGenerator")
}
