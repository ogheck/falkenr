pluginManagement {
    val springBootVersion: String by settings
    val dependencyManagementVersion: String by settings

    repositories {
        gradlePluginPortal()
        mavenCentral()
    }

    plugins {
        id("org.springframework.boot") version springBootVersion
        id("io.spring.dependency-management") version dependencyManagementVersion
    }
}

rootProject.name = "spring-devtools-ui"

include("spring-devtools-ui-starter")
include("spring-devtools-ui-core")
include("spring-devtools-ui-autoconfigure")
include("spring-devtools-ui-relay-server")
include("spring-devtools-ui-ui")
include("examples:demo-app")
include("examples:actuator-demo-app")
include("examples:baseline-app")
