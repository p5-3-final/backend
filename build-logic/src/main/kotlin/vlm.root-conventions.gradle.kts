// 루트 프로젝트 전용: 모듈에 속하지 않는 Gradle 스크립트(settings, build-logic)의 포맷을 검사한다.
plugins {
    id("com.diffplug.spotless")
}

val libs = the<VersionCatalogsExtension>().named("libs")

spotless {
    kotlinGradle {
        target("*.gradle.kts", "build-logic/*.gradle.kts", "build-logic/src/**/*.gradle.kts")
        ktlint(libs.findVersion("ktlint").get().requiredVersion)
    }
}
