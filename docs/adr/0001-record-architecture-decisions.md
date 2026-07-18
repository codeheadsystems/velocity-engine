# 1. Record architecture decisions

Date: 2026-07-18

## Status

Accepted.

## Context

The Velocity Engine is a multi-module, open-source library project (requirements §6) whose defining
promise is a **pluggable, backend-agnostic** counting substrate: a published SPI module
(`velocity-spi`) that third-party backends implement without depending on the engine's internals
(P14, §6.1). The consequence of that promise is that a whole class of decisions is expensive to
reverse: anything that shapes the `velocity-spi` contract, a backend's key schema, the OpenAPI
surface, the money model, the consistency guarantees, or the security posture becomes a compatibility
surface the moment it is published (NFR-17).

The v0.2 four-role review (fraud/rules adopter, gateway security operator, engineering architect,
business leader) surfaced several decisions that must be **frozen before the SPI is published** —
getting them wrong forces a re-cut of a published SPI at phase 3 (§15 Tier 1). BR-6 enumerates the
first ADRs to record. We need a lightweight, durable record of these decisions, the context in which
they were made, and the consequences they imply, so that an implementer arriving before the code
exists can see *why* the contract is shaped the way it is.

## Decision

Use Markdown Architecture Decision Records (ADRs) under `docs/adr/`, numbered sequentially starting
from `0001`, in the Michael Nygard format — mirroring the sibling `pk-auth` project's ADR convention
(`../pk-auth/docs/adr/`) so contributors moving between the two repositories find the same structure:

- **Title** — short, imperative, prefixed with the ADR number (`# N. Title`).
- **Date** — ISO date.
- **Status** — `Proposed` | `Accepted` | `Superseded by NNNN` | `Deprecated`.
- **Context** — the forces at play, grounded in the stable requirement IDs (`FR-*`, `NFR-*`,
  `D*`, `P*`, `§15 R*`, `OQ-*`) so an ADR stays traceable to the requirement it discharges.
- **Decision** — what we are doing.
- **Consequences** — what follows, including the downsides and what the decision costs.

This first batch (`0001`–`0008`) records the **Tier-1** decisions from §15 — the ones that gate
freezing the published `velocity-spi` contract. They are written *before* the code so that the frozen
SPI is a deliberate artifact, not an accident of the first backend's implementation. Later ADRs will
record the Tier-2/Tier-3 decisions (hot-path deadline & bounded failure, namespace-scoped authz,
keyed-hash-at-rest, backend-specific numbers) and the remaining BR-6 items (Dagger-for-Dropwizard,
track-latest-Dropwizard, OpenAPI-first client generation, stateless-tier, server-clock time,
feature-as-core-abstraction, `BigDecimal`-cents money, API-key auth, JDBI reference backend) as those
phases land.

## Consequences

- Every non-trivial decision that touches the `velocity-spi` contract, a backend key schema, the
  OpenAPI surface, security posture, or a cross-module compatibility surface lands as an ADR before
  or alongside the change that introduces it. Per `CONTRIBUTING.md` (BR-5), new dependencies are
  justified in either a commit message or an ADR.
- ADRs are append-only: when a decision is reversed, a new ADR is added with status `Accepted` and
  the prior ADR is updated to `Superseded by NNNN`.
- The Tier-1 batch (`0002`–`0008`) is the **gate for SPI freeze**. Publishing `velocity-spi` without
  these decisions recorded (in particular the seed contract, ADR 0008, and the read-your-write level,
  ADR 0007) would ship a published method with a "TBD" contract, which NFR-17 forbids.
- Downside: process overhead. We accept it for decisions that change a compatibility surface
  (SPI, OpenAPI, key schema, security, money model); reversible choices — internal package layout,
  method names, class-level structure inside a single module — do not need an ADR.
