plugins {
    java
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

springBoot {
    mainClass.set("com.devtools.ui.relay.RelayServerApplication")
}

dependencies {
    implementation(project(":spring-devtools-ui-core"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    implementation("org.springframework.boot:spring-boot-configuration-processor")
    implementation("com.auth0:java-jwt:4.4.0")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    from(rootProject.layout.projectDirectory.dir("website/dist")) {
        into("site")
    }
}
