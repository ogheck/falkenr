# Falkenr — Compatibility

This document defines the supported runtime matrix for the current local-first release line and the validation strategy behind it.

## Supported Baseline

| Surface | Supported range | Notes |
| --- | --- | --- |
| Java | 17 and 21 | Java 17 is the minimum runtime. Java 21 is the primary forward-compatibility target. |
| Spring Boot | 3.2.x and 3.3.x | Current line is tested against the latest patch in each supported minor line through CI matrix builds. |
| Spring Framework style | Servlet / Spring MVC | The MVP relies on `HandlerInterceptor` and `RequestMappingHandlerMapping`; WebFlux is not supported yet. |
| Build tools | Gradle and Maven consumers | Gradle consumers are built in-repo. Maven consumers are validated through the example app and Maven Local publishing. |
| Packaging | Fat jar and local `bootRun` | Local development flow is the primary supported mode. |

## Compatibility Guarantees

- Patch releases may fix bugs and tighten masking or safety defaults without changing the intended API contract.
- Minor releases may add fields to JSON responses or add new dashboard panels, but should preserve existing endpoints and starter setup.
- Major releases may change JSON contracts, starter behavior, or module layout.
- The `/_dev` surface is considered a developer API. New fields may be added, but removals or renamed fields require a major version bump.

## Current Validation Matrix

CI validates:

- Java 17 with Spring Boot 3.2.12
- Java 17 with Spring Boot 3.3.5
- Java 21 with Spring Boot 3.2.12
- Java 21 with Spring Boot 3.3.5
- Maven consumer install flow using `com.devtools:spring-devtools-ui`

## Consumer Validation Strategy

Gradle consumer validation:

- root Gradle build verifies the bundled examples
- compatibility workflow overrides `springBootVersion` to validate supported Boot lines

Maven consumer validation:

- publish the starter to Maven Local with a release version
- build the Maven example app against that local artifact

## Known Non-Goals For 0.x

- Spring Boot 2.x compatibility
- WebFlux-specific request capture
- production deployment support
- remote or hosted execution modes

## When To Update This Document

Update this file whenever any of the following changes:

- minimum Java version
- supported Spring Boot minors
- supported build tools
- `/_dev` contract guarantees
- CI validation matrix
