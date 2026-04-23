# Upgrading

This document defines how to upgrade `spring-devtools-ui` safely across releases.

## Upgrade Rules

- Patch upgrades within the same minor line should be low-risk and focus on fixes, safety improvements, and docs.
- Minor upgrades may add API response fields, new dashboard panels, or new configuration properties.
- Major upgrades may remove fields, rename APIs, or change packaging behavior.

## Before Upgrading

1. Read [CHANGELOG.md](../CHANGELOG.md) for the target version.
2. Check [COMPATIBILITY.md](./COMPATIBILITY.md) for supported Java and Spring Boot ranges.
3. If you depend on dashboard JSON in custom tooling, verify any new fields or panel changes against your usage.

## Upgrade Checklist

1. Update the starter dependency version.
2. Start the host app locally.
3. Open `http://localhost:<port>/_dev`.
4. Verify endpoints, request capture, config data, and logs still load.
5. If you use `prod` or custom profile flows, confirm the dashboard is still disabled as expected.

## Breaking Change Policy

- Removing or renaming `/_dev` APIs requires a major version bump.
- Removing fields from existing response payloads requires a major version bump.
- Adding optional response fields is allowed in minor releases.
- Tightening masking or security defaults is allowed in patch or minor releases when it reduces risk.

## Maintainer Process

For each release:

1. Move user-facing changes from `Unreleased` into a new version section in [CHANGELOG.md](../CHANGELOG.md).
2. Add upgrade notes here when behavior changes require user action.
3. Link any new compatibility decisions back to [COMPATIBILITY.md](./COMPATIBILITY.md).
