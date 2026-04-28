# Falkenr Security And Data Handling

Falkenr is a local-first developer tool for Spring Boot applications. The local dashboard runs inside your application and is designed to be useful without sending data to Falkenr.

Hosted collaboration is optional. Teams use it when they want to share a debugging session with another developer.

## What Runs Locally

The local starter runs in your Spring Boot application and exposes a local dashboard at:

```text
/_dev
```

Local features include endpoint inspection, request capture, config visibility, logs, jobs, DB query visibility, feature flags, and collaboration preparation.

By default, local usage does not require the hosted relay.

## What Leaves Your Machine

Data leaves your machine only when you connect a local session to the hosted relay or use hosted collaboration features.

Hosted relay data may include:

- Session id and session metadata.
- Organization and relay account identifiers.
- Share-link and viewer-session metadata.
- Published hosted session state.
- Request metadata and request artifacts that your local app sends to the relay.
- Collaboration activity, notes, and recording snapshot metadata.
- Audit events for collaboration-sensitive actions.
- Billing identifiers, such as Stripe customer and subscription ids.

Do not use hosted collaboration for secrets, regulated production data, or sensitive customer payloads during beta.

## Authentication And Access

The hosted relay uses relay-owned account sessions and viewer sessions.

- Organization owners can manage Team features in the hosted dashboard.
- Owners can create invitations for teammates.
- Viewer sessions are scoped to a hosted session.
- Team-gated features require a trialing or active Team entitlement.
- Revoked and expired tokens are rejected by the relay.

## Billing Data

Stripe handles payment collection. Falkenr stores only the Stripe identifiers needed to link a relay organization to its subscription state.

Falkenr does not store card numbers.

## Current Beta Limits

Hosted collaboration is currently beta infrastructure.

Current limits:

- Hosted relay state is file-backed.
- Backup and restore are currently operator-run workflows.
- Full organization deletion is manual during beta.
- There is no public uptime SLA.
- There is no claim of SOC 2, HIPAA, PCI, or enterprise compliance readiness.
- Hosted collaboration should be used for development and debugging workflows, not regulated production data.

## Customer Data Requests

During beta, customers can request:

- Export of hosted relay state related to their organization.
- Deletion of hosted relay state related to their organization.
- Revocation of viewer sessions.
- Removal of organization members.

Some requests require manual operator work until self-service workflows are added.

## Security Roadmap

Before broad self-serve launch, Falkenr needs:

- Automated organization and session deletion.
- Stronger tenant isolation review.
- Durable datastore with organization partitioning.
- Automated backup retention.
- Public status page or equivalent monitoring disclosure.
- Formal secret-rotation procedure.
- Security review of request artifact handling.

## Contact

For beta security or data-handling questions, contact:

```text
info@falkenr.com
```
