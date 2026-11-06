plugins {
    id("vlm.spring-boot-service")
    alias(libs.plugins.openapi.generator)
}

dependencies {
    implementation(project(":libs:common-core"))
    implementation(project(":libs:common-web"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    // 조건부 UPDATE·ON CONFLICT·JSONB·집계 SQL이 대부분이라 JPA 대신 JDBC로 다룬다(plan "데이터 모델").
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.springframework.kafka:spring-kafka")
    implementation(platform(libs.aws.sdk.bom))
    // 운영 S3와 로컬·테스트 SeaweedFS를 같은 클라이언트로 쓴다(ADR-0006).
    implementation("software.amazon.awssdk:s3")
    implementation(libs.json.schema.validator)
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.kafka)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.swagger.request.validator.mockmvc)
}

// 계약 원본은 docs 레포에 있다. 로컬은 작업 공간 구조(project/repos/backend)를 따르고, CI·이미지 빌드는 CONTRACTS_DIR로 넘긴다.
val contractsDir =
    providers
        .environmentVariable("CONTRACTS_DIR")
        .orElse(rootDir.resolve("../../service-contracts").canonicalPath)

// 서버 인터페이스와 요청·응답 모델은 계약에서 만든다. 생성 패키지는 com.vlm 밖에 두어 NullAway·ArchUnit 기능 규칙 대상에서 뺀다.
openApiGenerate {
    generatorName = "spring"
    inputSpec.set(layout.file(contractsDir.map { file("$it/openapi/quality-service.yaml") }))
    outputDir.set(layout.buildDirectory.dir("generated/openapi"))
    apiPackage = "vlm.contracts.quality.api"
    modelPackage = "vlm.contracts.quality.model"
    // 계약 예시·설명은 생성 코드에 필요 없고, 경로 접두어가 같은 작업끼리 묶이지 않게 태그로 인터페이스를 나눈다.
    generateApiDocumentation = false
    generateModelDocumentation = false
    generateApiTests = false
    generateModelTests = false
    configOptions =
        mapOf(
            "interfaceOnly" to "true",
            "useSpringBoot3" to "true",
            "useTags" to "true",
            "useBeanValidation" to "true",
            "openApiNullable" to "false",
            "skipDefaultInterface" to "true",
            "documentationProvider" to "none",
            "annotationLibrary" to "none",
            "dateLibrary" to "java8",
            "sourceFolder" to "src/main/java",
        )
}

sourceSets {
    main {
        java.srcDir(layout.buildDirectory.dir("generated/openapi/src/main/java"))
    }
}

tasks.compileJava {
    dependsOn(tasks.openApiGenerate)
}

// 생성 소스는 spotless 대상(src/**)이 아니다. 받는 이벤트·매니페스트는 배포 JAR에 넣은 계약 스키마로 검증한다.
tasks.processResources {
    from(contractsDir) {
        include(
            "events/_envelope.schema.json",
            "events/_vlm-analysis.schema.json",
            "events/event.analyzed.schema.json",
            "events/video.deleted.schema.json",
            "events/stage.timing.schema.json",
            "events/alert.raised.schema.json",
            "events/alert.candidate.schema.json",
            "stages/v1/*.schema.json",
            "taxonomy/v2/*.schema.json",
            "quality/v1/golden-manifest.schema.json",
        )
        into("contracts")
    }
}

tasks.test {
    systemProperty("vlm.contracts.dir", contractsDir.get())
}
