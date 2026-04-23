# Compliance Posture

This document states the current compliance and customer-data position of `spring-devtools-ui` as it exists today. It is a reality document, not a marketing document.

## Current Posture

The project is not currently positioned as a compliance-certified product.

Today, this repository does **not** provide:

- SOC 2 certification
- HIPAA compliance claims
- GDPR processor tooling
- PCI-specific controls
- centralized retention management
- customer-managed encryption keys
- tenant-isolated hosted data guarantees
- formal data residency controls
- formal legal hold or eDiscovery controls
- production-safe default deployment posture

## What The Product Actually Does Today

The current codebase does provide some useful security and governance building blocks:

- localhost-only default access for local development
- automatic disablement when the `prod` profile is active
- staging and trusted-header SSO access modes
- RBAC on mutation endpoints
- approval workflow for selected high-risk staging and SSO actions
- audit logs for dashboard mutations
- masking for many obvious secrets in headers, config, SQL, and selected payload fields
- bounded in-memory retention for several collectors
- optional request sampling

These are helpful controls. They are not a substitute for a compliance program.

## Customer Data Boundaries

The safest way to think about the product today is:

- local mode is a developer tool
- staging mode is an operator-controlled debugging surface
- hosted collaboration is alpha infrastructure

You should assume the product may capture and expose:

- request paths and methods
- request and response bodies
- request headers
- config values
- SQL statements
- scheduled job metadata
- relay collaboration metadata

Even with masking enabled, the product should be treated as potentially handling sensitive operational data.

## Recommended Usage Boundaries

Safe current uses:

- local development on non-production data
- QA and staging environments with scrubbed or synthetic data
- internal alpha collaboration with trusted operators

Unsafe current uses:

- production environments carrying regulated customer data by default
- environments requiring formal compliance attestations from this product
- hosted multi-tenant customer-facing deployments with contractual security promises
- workflows that depend on guaranteed full secret redaction

## Hosted Relay Boundaries

The relay and hosted collaboration plane are still alpha.

Current limitations include:

- file-backed persistence rather than a hardened managed data plane
- no documented tenant isolation guarantees
- no documented backup, restore, or disaster-recovery policy for customers
- no formal access-review workflow for hosted organizations
- no external billing, entitlement, or procurement controls suitable for enterprise contracts

Do not represent the hosted relay as enterprise-ready.

## Data Handling Guidance

If you choose to run the product outside localhost:

- keep it off by default in production
- only enable staging or SSO mode intentionally
- use scrubbed or synthetic data when possible
- enable masking features
- limit retention budgets
- enable request sampling where capture volume is risky
- treat audit logs as local operational evidence, not a system-of-record compliance archive

## Claims We Can Make Today

Accurate claims:

- the product includes basic access control, masking, audit, RBAC, and approval primitives
- the product has an explicit local-first default posture
- the product has early governance controls for staging and internal testing

Inaccurate claims:

- compliant out of the box
- production safe by default
- enterprise ready
- suitable for regulated workloads without additional controls
- a replacement for formal security, privacy, or compliance infrastructure

## What Must Exist Before Stronger Compliance Claims

Before this project should make stronger compliance or enterprise-safety claims, it needs at least:

- centralized policy enforcement for capture and masking rules
- explicit production-safe mode with stricter defaults
- durable hosted security boundaries and tenant isolation guarantees
- documented retention and deletion behavior
- documented operator and customer responsibilities
- operational runbooks for incident response, backup, and recovery
- legal and security review of public product claims
