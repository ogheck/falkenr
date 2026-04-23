# Releasing

`spring-devtools-ui` uses semantic versioning with a snapshot-first workflow.

## Versioning Policy

- Day-to-day development stays on a `-SNAPSHOT` version in [gradle.properties](../gradle.properties).
- Releases use `-PreleaseVersion=x.y.z` and must not include `-SNAPSHOT`.
- Git tags must match the release version exactly in the form `vX.Y.Z`.
- Backward compatibility is tracked at the starter API and `/_dev` JSON contract level for a given minor line.
- Breaking API or JSON response changes require a major version bump.

## Local Validation

Run the full release validation locally before tagging:

```bash
./gradlew validateVersioning build
cd spring-devtools-ui-ui
npm ci
npm test
npm run build
cd ..
git diff --exit-code -- spring-devtools-ui-ui/dist
```

## Local Release Smoke Test

Use Maven Local to verify the published starter coordinates and POM metadata:

```bash
./gradlew --no-configuration-cache :spring-devtools-ui-starter:publishToMavenLocal -PreleaseVersion=0.1.5
```

This produces the publishable artifact:

- `io.github.ogheck:spring-devtools-ui:0.1.5`

## GitHub Secrets Required

The release workflow expects:

- `SONATYPE_USERNAME`
- `SONATYPE_PASSWORD`
- `SIGNING_KEY`
- `SIGNING_PASSWORD`

The current release workflow uses the Gradle Nexus Publish plugin against Sonatype's OSSRH Staging API compatibility service. This follows Sonatype's documented Gradle path for Central Portal publishing and then closes/releases the staging repository in CI.

## Release Process

1. Update [gradle.properties](../gradle.properties) to the next development snapshot after the previous release branch is stable.
2. Ensure CI is green and the UI bundle is fresh.
3. Create and push a tag in the form `vX.Y.Z`.
4. GitHub Actions runs the release pipeline, validates the tag/version match, publishes to Sonatype, and creates a GitHub release.
5. After the release, move `gradle.properties` to the next `-SNAPSHOT` version.
