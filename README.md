# Falkenr

Falkenr is a zero-config Spring Boot developer dashboard delivered by a single starter dependency.

## Install

Gradle:

```kotlin
implementation("io.github.ogheck:spring-devtools-ui:0.1.5")
```

Maven:

```xml
<dependency>
  <groupId>io.github.ogheck</groupId>
  <artifactId>spring-devtools-ui</artifactId>
  <version>0.1.5</version>
</dependency>
```

## Use

Add the dependency, restart your Spring Boot app, then open:

```text
http://localhost:8080/_dev
```

The dashboard is local-first and is disabled automatically when the `prod` profile is active.

## What It Shows

- Endpoints
- Request history
- Logs
- Config values
- Scheduled jobs
- Dependency graph
- DB queries when a JDBC datasource is present
- Feature flag overrides
- Time controls
- Fake external services
- Webhook replay tools

This project is intended for development and controlled non-production debugging. Do not expose `/_dev` publicly without an authenticated ingress and explicit access controls.

## Modules

- `spring-devtools-ui-starter`: dependency-only starter published as `io.github.ogheck:spring-devtools-ui`
- `spring-devtools-ui-core`: collector SPI, in-memory stores, policy helpers, and API models
- `spring-devtools-ui-autoconfigure`: Spring Boot auto-configuration, controllers, filters, and bundled dashboard assets
- `spring-devtools-ui-ui`: React/Vite dashboard source and committed `dist` bundle
- `examples/*`: Gradle and Maven consumer apps
- `website`: public landing/docs site

## Defaults

- Serves the dashboard at `/_dev`
- Restricts access to localhost by default
- Disables itself when the `prod` profile is active
- Keeps captured data in memory
- Masks common sensitive values before rendering

## Run Locally

```bash
./gradlew :examples:demo-app:bootRun
```

Then open `http://localhost:8080/_dev`.

## Build

```bash
cd spring-devtools-ui-ui
npm ci
npm test
npm run build
cd ..
./gradlew validateVersioning build
```

## Documentation

- [Compatibility](docs/COMPATIBILITY.md)
- [Compliance posture](docs/COMPLIANCE.md)
- [Migration notes](docs/MIGRATION.md)
- [Releasing](docs/RELEASING.md)
- [Upgrading](docs/UPGRADING.md)

## License

Apache License, Version 2.0.
