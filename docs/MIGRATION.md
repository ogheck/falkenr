# Migration: Local-Only To Hosted Projects

This document defines the intended migration path from local-only usage to hosted projects and hosted collaboration.

The goal is to make hosted adoption incremental and reversible. The free local workflow must remain the default and must not depend on hosted infrastructure.

## Principles

- Opt-in: hosted mode is never silently enabled.
- Reversible: users can disable hosted mode and keep local dashboards working.
- Safe defaults: do not upload secrets or raw payloads by default.
- Tenant boundaries: hosted state is scoped to `organizationId` and enforced server-side.
- Minimal friction: onboarding should be a short “connect relay” flow, not a new product install.

## Concepts

- Local app: a Spring Boot service with the starter and `/_dev` enabled.
- Relay: hosted control plane and hosted viewer surface.
- Organization: tenant (`organizationId`).
- Project: grouping layer under an organization (already implemented in relay).
- Session: debugging session that can be attached, shared, and synced.

## Migration stages

### Stage 0: Local-only (default)

- Local dashboard runs with localhost-only access.
- No outbound calls to a hosted relay.
- No hosted sessions exist.

### Stage 1: “Connect relay” (hosted account session only)

- User signs in / registers with the relay and receives a relay-owned account session token.
- The hosted dashboard at `/app?accountSession=...` becomes available.
- No local session state is uploaded until the user explicitly enables remote transport/sync.

Acceptance:

- A user can view their organization, entitlement, projects, and hosted sessions (empty at first) in the relay dashboard.

### Stage 2: Opt-in hosted attach (transport enabled, no persistence promises)

- Local app config points to the relay and attaches a session.
- Relay mints a lease and optionally a tunnel id; local dashboard shows transport status.
- Sync publishes a bounded hosted snapshot, plus selected artifacts, under the tenant.

Acceptance:

- A user can attach from a local dashboard and see the session in the hosted dashboard.

### Stage 3: Adopt hosted projects (grouping and ownership)

- User creates a project in the relay and assigns hosted sessions to it.
- Optional: local app declares a `projectId` to auto-associate sessions.
- Project dashboards become the default navigation layer in hosted UI.

Acceptance:

- Hosted sessions are grouped under projects and remain tenant-scoped.

### Stage 4: Multi-machine collaboration (share, invite, revoke)

- Owner issues invitation tokens or share links from the hosted dashboard.
- Teammates accept invitations and obtain their own relay-owned account sessions.
- Viewer sessions can be revoked by owners; hosted access material never needs to be copied into config files.

Acceptance:

- Another machine can open a hosted viewer via relay-owned auth and see the latest synced state.

## Data migration and continuity

There are two distinct migration surfaces:

1. Session-level collaboration state (activity, notes, recordings, request artifacts) that is already bounded and syncable.
2. Long-lived org metadata (accounts, projects, entitlement) that is relay-owned.

### Session state continuity

Session state is not migrated as a one-time bulk import. It is continuously published:

- Local app publishes snapshots via the sync endpoint.
- Relay stores bounded state and enforces quotas.
- Hosted viewers and remote debugging surfaces read from relay storage.

### Org/project continuity

Org metadata lives in the relay and does not depend on local state.

- Project creation and session assignment happen in the relay dashboard.
- Accounts/invitations happen in the relay dashboard.

## Import/export and rollback

The relay supports admin export/import as an operator tool for alpha recovery:

- `GET /sessions/admin/export`
- `POST /sessions/admin/import`

Rules:

- These endpoints must be disabled or strongly protected in any paid/shared deployment.
- Use them only for disaster recovery, not as a primary customer-facing migration UI.

Rollback path:

- User disables relay transport config in the local app.
- Local dashboard continues to work from in-memory collectors.
- Hosted sessions remain in the relay but are no longer updated; quotas and retention apply.

## UX flows to implement (future)

- Local dashboard: “Connect to relay” wizard that produces a relay URL + account session flow without copying tokens into logs.
- Local dashboard: “Publish session” explicit action that performs first sync and shows what will be uploaded.
- Hosted dashboard: “Create project” + “Assign session” (already implemented).
- Hosted dashboard: “Invite teammate” (already implemented).
- Hosted dashboard: “Revoke access” and “Rotate access material” (partially implemented via viewer session revocation).

## Security and privacy constraints

- Never upload raw secrets by default. Policy controls must govern capture before sync.
- Mask and truncate request bodies before persistence (already required by policy engine and artifact metadata).
- Hosted identity must be relay-owned; do not rely on local owner connection ids for hosted operations.

