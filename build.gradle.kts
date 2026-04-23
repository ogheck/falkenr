import org.gradle.api.DefaultTask
import org.gradle.api.publish.maven.tasks.PublishToMavenLocal
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction

abstract class VersionPolicyCheck : DefaultTask() {
    @get:Input
    abstract val snapshotVersion: Property<String>

    @get:Input
    abstract val effectiveVersion: Property<String>

    @get:Input
    @get:Optional
    abstract val releaseVersion: Property<String>

    @get:Input
    abstract val requireReleaseVersion: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val gitRefType: Property<String>

    @get:Input
    @get:Optional
    abstract val gitRefName: Property<String>

    @TaskAction
    fun validate() {
        val semverRegex = Regex("""\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?""")
        val snapshot = snapshotVersion.get()
        require(snapshot.matches(semverRegex)) {
            "Snapshot version '$snapshot' must match semantic versioning and typically end with -SNAPSHOT."
        }
        require(snapshot.endsWith("-SNAPSHOT")) {
            "Snapshot version '$snapshot' must end with -SNAPSHOT."
        }

        val effective = effectiveVersion.get()
        require(effective.matches(semverRegex)) {
            "Effective version '$effective' must match semantic versioning."
        }

        if (requireReleaseVersion.get()) {
            val release = releaseVersion.orNull
            require(!release.isNullOrBlank()) {
                "Release publishing requires -PreleaseVersion=x.y.z."
            }
            require(!effective.endsWith("-SNAPSHOT")) {
                "Release version '$effective' must not end with -SNAPSHOT."
            }

            val refType = gitRefType.orNull
            val refName = gitRefName.orNull
            if (refType == "tag" && !refName.isNullOrBlank()) {
                require(refName == "v$effective") {
                    "Git tag '$refName' must match release version 'v$effective'."
                }
            }
        }
    }
}

plugins {
    id("io.github.gradle-nexus.publish-plugin") version "2.0.0"
    id("org.springframework.boot") apply false
    id("io.spring.dependency-management") apply false
}

group = "io.github.ogheck"

val baseSnapshotVersion = (findProperty("version") as String?)?.trim()?.takeUnless { it.isEmpty() } ?: "0.1.0-SNAPSHOT"
val requestedReleaseVersion = (findProperty("releaseVersion") as String?)?.trim()?.takeUnless { it.isEmpty() }
val resolvedVersion = requestedReleaseVersion ?: baseSnapshotVersion

version = resolvedVersion

tasks.register("printVersion") {
    group = "release"
    description = "Prints the effective project version."
    doLast {
        println(resolvedVersion)
    }
}

tasks.register<VersionPolicyCheck>("validateVersioning") {
    group = "release"
    description = "Validates snapshot and release version semantics."
    snapshotVersion.set(baseSnapshotVersion)
    effectiveVersion.set(resolvedVersion)
    requireReleaseVersion.set(false)
    gitRefType.set(providers.environmentVariable("GITHUB_REF_TYPE").orElse(""))
    gitRefName.set(providers.environmentVariable("GITHUB_REF_NAME").orElse(""))
    if (requestedReleaseVersion != null) {
        releaseVersion.set(requestedReleaseVersion)
    }
}

tasks.register<VersionPolicyCheck>("validateReleaseVersion") {
    group = "release"
    description = "Ensures a tagged release uses a non-SNAPSHOT version that matches the Git tag."
    dependsOn("validateVersioning")
    snapshotVersion.set(baseSnapshotVersion)
    effectiveVersion.set(resolvedVersion)
    requireReleaseVersion.set(true)
    gitRefType.set(providers.environmentVariable("GITHUB_REF_TYPE").orElse(""))
    gitRefName.set(providers.environmentVariable("GITHUB_REF_NAME").orElse(""))
    if (requestedReleaseVersion != null) {
        releaseVersion.set(requestedReleaseVersion)
    }
}

subprojects {
    group = rootProject.group
    version = rootProject.version

    repositories {
        mavenCentral()
    }

    tasks.withType<PublishToMavenLocal>().configureEach {
        dependsOn(rootProject.tasks.named("validateVersioning"))
    }
}

nexusPublishing {
    repositories {
        sonatype {
            nexusUrl.set(uri("https://ossrh-staging-api.central.sonatype.com/service/local/"))
            snapshotRepositoryUrl.set(uri("https://central.sonatype.com/repository/maven-snapshots/"))
            username.set(providers.gradleProperty("sonatypeUsername").orElse(providers.environmentVariable("SONATYPE_USERNAME")))
            password.set(providers.gradleProperty("sonatypePassword").orElse(providers.environmentVariable("SONATYPE_PASSWORD")))
        }
    }
}
