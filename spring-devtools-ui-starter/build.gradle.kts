import org.gradle.api.publish.maven.MavenPublication

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
    api(project(":spring-devtools-ui-autoconfigure"))
}

tasks.jar {
    archiveBaseName.set("spring-devtools-ui")
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = "spring-devtools-ui"
            pom {
                name.set("spring-devtools-ui")
                description.set("Zero-config Spring Boot developer dashboard served at /_dev.")
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
