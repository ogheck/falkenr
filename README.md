# Falkenr

Falkenr is a zero-config Spring Boot developer dashboard delivered by a single starter dependency.

Add one dependency, restart your app, open `http://localhost:8080/_dev`, and inspect runtime behavior without wiring a separate service.

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

## What It Shows

- Endpoints and request history
- Logs, config, scheduled jobs, and dependency graph
- DB query capture when a JDBC datasource is present
- Feature flag overrides, time controls, fake external services, and webhook replay tools
- Local session sharing primitives for development and alpha collaboration workflows

## Safety Defaults

- Enabled for local development by default
- Restricted to localhost requests in the default mode
- Disabled automatically when the `prod` Spring profile is active
- Masks sensitive config keys, headers, payload fields, SQL values, and session-like secrets
- Keeps request/log/query history bounded in memory unless persistence is explicitly configured

This project is intended for development and controlled non-production debugging. Do not expose `/_dev` publicly without an authenticated ingress and explicit access controls.

## Run Locally

```bash
./gradlew :examples:demo-app:bootRun
```

Then open:

```text
http://localhost:8080/_dev
```

For the public website:

```bash
cd website
npm ci
npm run build
npm run preview
```

## Build

```bash
cd spring-devtools-ui-ui
npm ci
npm test
npm run build
cd ..
./gradlew validateVersioning build
```

## Modules

- `spring-devtools-ui-starter`: dependency-only starter published as `io.github.ogheck:spring-devtools-ui`
- `spring-devtools-ui-core`: collectors, policy interfaces, and DTOs
- `spring-devtools-ui-autoconfigure`: Spring Boot auto-configuration, API controllers, filters, and bundled UI serving
- `spring-devtools-ui-relay-server`: hosted relay backend for managed attach and shared session viewing
- `spring-devtools-ui-ui`: React + Vite dashboard source and bundled assets
- `examples/*`: Gradle and Maven consumer apps
- `website`: public landing/docs site

## Documentation

- [Compatibility](docs/COMPATIBILITY.md)
- [Compliance posture](docs/COMPLIANCE.md)
- [Migration notes](docs/MIGRATION.md)
- [Releasing](docs/RELEASING.md)
- [Upgrading](docs/UPGRADING.md)

