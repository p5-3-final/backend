plugins { id("vlm.spring-boot-service") }
dependencies {
    implementation(project(":libs:common-core"))
    implementation(project(":libs:common-messaging"))
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.springframework.kafka:spring-kafka")
    implementation(libs.json.schema.validator)
    runtimeOnly("org.postgresql:postgresql")
    testImplementation(platform(libs.aws.sdk.bom))
    testImplementation("software.amazon.awssdk:s3")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.kafka)
    testImplementation(libs.testcontainers.junit.jupiter)
}

val contractsDir = providers.environmentVariable("CONTRACTS_DIR").orElse(rootDir.resolve("../../service-contracts").canonicalPath)
tasks.processResources {
    from(contractsDir.map { "$it/events" }) {
        include(
            "_envelope.schema.json",
            "video.normalized.schema.json",
            "video.normalized.v2.schema.json",
            "chunk.requested.schema.json",
            "chunk.execution.finished.schema.json",
        )
        into("contracts/events")
    }
    doFirst {
        for (name in listOf("video.normalized.v2.schema.json", "chunk.requested.schema.json", "chunk.execution.finished.schema.json")) {
            check(file("${contractsDir.get()}/events/$name").isFile) { "Required contract is missing: $name" }
        }
    }
}

tasks.test {
    systemProperty(
        "chunk.processor.source",
        providers
            .environmentVariable("CHUNK_PROCESSOR_SOURCE_DIR")
            .orElse(rootDir.resolve("../ai").canonicalPath)
            .get(),
    )
}
