import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.tasks.GenerateModuleMetadata

plugins {
    `java-library`
    `maven-publish`
    signing
    id("io.spring.dependency-management")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
    withSourcesJar()
    withJavadocJar()
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.3.5")
    }
}

dependencies {
    api("org.springframework:spring-webmvc")
    api("org.springframework.boot:spring-boot")
    api("com.fasterxml.jackson.core:jackson-databind")
    implementation("ch.qos.logback:logback-classic")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("jakarta.servlet:jakarta.servlet-api")
}

tasks.test {
    useJUnitPlatform()
}

// This project uses Spring's dependency-management BOM import. Publishing Gradle module metadata
// fails validation because dependency versions are supplied via the BOM (not inline).
// Maven consumers only need the generated POM, so disable `.module` metadata publication.
tasks.withType<GenerateModuleMetadata>().configureEach {
    enabled = false
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = "spring-devtools-ui-core"
            pom {
                name.set("spring-devtools-ui-core")
                description.set("Core types and runtime plumbing for the Spring DevTools UI dashboard.")
                url.set("https://github.com/ogheck/spring-dev")
                licenses {
                    license {
                        name.set("Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0")
                    }
                }
                developers {
                    developer {
                        id.set("ogheck")
                        name.set("Daniel Heck")
                    }
                }
                scm {
                    connection.set("scm:git:https://github.com/ogheck/spring-dev.git")
                    developerConnection.set("scm:git:ssh://git@github.com:ogheck/spring-dev.git")
                    url.set("https://github.com/ogheck/spring-dev")
                }
            }
        }
    }
}

signing {
    val signingKey = findProperty("signingKey") as String? ?: System.getenv("SIGNING_KEY")
    val signingPassword = findProperty("signingPassword") as String? ?: System.getenv("SIGNING_PASSWORD")

    if (!signingKey.isNullOrBlank()) {
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications)
    }
}
