// 모든 Java 모듈의 공통 설정: 컴파일 검사, 포맷, 테스트. 서비스 모듈에서 이 설정을 끄거나 바꾸지 않는다.
import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone

plugins {
    java
    id("com.diffplug.spotless")
    id("net.ltgt.errorprone")
}

val libs = the<VersionCatalogsExtension>().named("libs")

fun version(alias: String): String = libs.findVersion(alias).get().requiredVersion

fun library(alias: String) = libs.findLibrary(alias).get()

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(version("java"))
    }
}

dependencies {
    errorprone(library("errorprone-core"))
    errorprone(library("nullaway"))
    compileOnly(library("lombok"))
    annotationProcessor(library("lombok"))
    testCompileOnly(library("lombok"))
    testAnnotationProcessor(library("lombok"))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    // -processing 제외: Lombok 프로세서가 처리하지 않는 Spring 어노테이션마다 경고가 나서 -Werror와 함께 쓸 수 없다.
    // -parameters: Spring이 생성자·메서드 파라미터 이름을 리플렉션으로 읽는다.
    options.compilerArgs.addAll(listOf("-Xlint:all,-processing", "-Werror", "-parameters"))
    options.errorprone {
        disableWarningsInGeneratedCode = true
        check("NullAway", CheckSeverity.ERROR)
        // lint.md 설정 단위 예외: NullAway 대상은 com.vlm 패키지로 한정한다.
        option("NullAway:AnnotatedPackages", "com.vlm")
    }
}

// 테스트 코드는 Mockito·AssertJ 사용 패턴 때문에 NullAway 오탐이 많아 검사 대상에서 뺀다.
tasks.named<JavaCompile>("compileTestJava") {
    options.errorprone.check("NullAway", CheckSeverity.OFF)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

spotless {
    java {
        target("src/**/*.java")
        googleJavaFormat(version("google-java-format"))
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint(version("ktlint"))
    }
}
