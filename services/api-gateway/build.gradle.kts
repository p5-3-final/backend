plugins {
    id("vlm.spring-boot-service")
}

dependencies {
    implementation(project(":libs:common-core"))
    implementation("org.springframework.cloud:spring-cloud-starter-gateway-server-webflux")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")

    testImplementation(libs.wiremock.standalone)
}
