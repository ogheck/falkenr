import org.springframework.boot.gradle.plugin.SpringBootPlugin
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
        mavenBom(SpringBootPlugin.BOM_COORDINATES)
    }
}

dependencies {
    api(project(":spring-devtools-ui-core"))
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    annotationProcessor("org.springframework.boot:spring-boot-autoconfigure-processor")

    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation("org.springframework.boot:spring-boot-starter-web")

    testImplementation("org.springframework.boot:spring-boot-starter-actuator")
    testImplementation("org.springframework.boot:spring-boot-starter-jdbc")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("com.h2database:h2")
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    val uiDistDir = project(":spring-devtools-ui-ui").layout.projectDirectory.dir("dist")
    from(uiDistDir) {
        into("META-INF/spring-devtools-ui")
        include("**/*")
        includeEmptyDirs = false
    }
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
            artifactId = "spring-devtools-ui-autoconfigure"
            pom {
                name.set("spring-devtools-ui-autoconfigure")
                description.set("Spring Boot autoconfiguration for the Spring DevTools UI dashboard.")
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
