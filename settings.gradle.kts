pluginManagement {
    includeBuild("build-logic")
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
    }
}

rootProject.name = "backend"

include(
    "libs:common-core",
    "libs:common-web",
    "libs:common-messaging",
)

// 서비스 모듈은 해당 서비스 구현을 시작할 때 추가한다(AGENTS.md "새 서비스 모듈 추가").
include(
    "services:api-gateway",
    "services:auth-service",
    "services:media-service",
)
