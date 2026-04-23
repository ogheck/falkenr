# Changelog

All notable changes to `spring-devtools-ui` should be recorded here.

The format is intentionally simple:

- `Added` for new features
- `Changed` for behavior changes
- `Fixed` for bug fixes
- `Security` for masking, access, or safety changes

## [Unreleased]

### Added

- future changes land here before the next release

## [0.1.5] - 2026-04-07

### Added

- compatibility matrix documentation and CI workflow for Java 17/21 and Spring Boot 3.2.x/3.3.x
- Maven consumer example validated through Maven Local publishing
- Actuator-focused Gradle example app for broader endpoint verification
- release documentation, release validation tasks, and GitHub release automation
- docs landing page and initial visual brand assets
- public Maven Central publication for `io.github.ogheck:spring-devtools-ui:0.1.5`

### Changed

- project versioning is now snapshot-first via `gradle.properties` with release overrides through `-PreleaseVersion`
- publishing path now creates a real `mavenJava` publication for `io.github.ogheck:spring-devtools-ui`
- Sonatype release automation now uses the OSSRH Staging API compatible Gradle Nexus publish flow

### Security

- request/header masking, body truncation, binary payload detection, and localhost-only access remain default release guarantees

## [0.1.0] - 2026-04-07

### Added

- zero-config Spring Boot starter published as `io.github.ogheck:spring-devtools-ui`
- local `/_dev` dashboard with tabs for endpoints, requests, config, and logs
- in-memory endpoint explorer built from `RequestMappingHandlerMapping`
- request capture interceptor with last-100 retention
- config inspector backed by Spring `Environment`
- log viewer backed by in-memory Logback appender
- React + Vite + Tailwind dashboard bundled into the starter
- localhost-only protection and automatic disablement for `prod`
- demo Spring Boot example application
