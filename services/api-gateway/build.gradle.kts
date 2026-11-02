plugins {
    id("vlm.spring-boot-service")
}

dependencies {
    implementation(project(":libs:common-core"))
    implementation("org.springframework.cloud:spring-cloud-starter-gateway-server-webflux")

    testImplementation(libs.wiremock.standalone)
}
