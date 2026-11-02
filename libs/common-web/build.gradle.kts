plugins {
    id("vlm.library-conventions")
}

dependencies {
    api(project(":libs:common-core"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-validation")
}
