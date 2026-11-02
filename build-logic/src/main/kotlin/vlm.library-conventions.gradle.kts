// libs/ 모듈용. 도메인 로직 없는 공통 라이브러리이며, 버전은 Spring Boot BOM을 따른다.
plugins {
    id("vlm.java-conventions")
    `java-library`
    id("io.spring.dependency-management")
}

val libs = the<VersionCatalogsExtension>().named("libs")

dependencyManagement {
    imports {
        mavenBom(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES)
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
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
