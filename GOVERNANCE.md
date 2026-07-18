# Governance

This document describes how the Velocity Engine is governed: who makes
decisions, how those decisions are recorded, how the roadmap is sequenced, and
how you can become a contributor or maintainer. It is deliberately honest about
the project's youth — this is requirement **GR-2** (governance & support model).

## Maintainers

| Maintainer | Role | Contact |
|------------|------|---------|
| Ned Wolpert | Lead maintainer, architect | ned.wolpert@gmail.com |

**Bus factor: one.** Today this is a single-maintainer project. There is no
foundation, steering committee, or company behind it, and adopters should size
their self-support risk accordingly: a permissive BSD-3-Clause license grants
you broad rights, but a license is not a maintenance guarantee. If you are
considering the engine on a fraud/abuse-critical path, plan to be able to
self-support the code you depend on.

## Decision model

While the project has a single maintainer, decisions are made by the maintainer,
informed by issues, discussion, and the constraints already captured in the
specification. As the project attracts contributors, the intent is to move
toward lazy consensus — a proposal that draws no sustained objection within a
reasonable window is accepted — with the lead maintainer as the tiebreaker.

Two things constrain every decision and are not casually revisited:

- **The specification.** [`docs/requirements.md`](./docs/requirements.md) is the
  source of truth for scope and intent. Its §2 *locked* scope decisions (D1–D8)
  and §2.1 *resolved* parameters (P1–P18) were decided at kickoff and constrain
  everything below them; changing one is a deliberate act, recorded in an ADR.
- **The compatibility surfaces.** The `velocity-spi` interfaces + DTOs and the
  OpenAPI contract are compatibility surfaces (NFR-17). SPI evolution is
  **additive-only**, with new capability fields defaulting to "unsupported."

## How decisions are recorded

Non-trivial, cross-module decisions are captured as **Architecture Decision
Records** under [`docs/adr/`](./docs/adr/), in
[Nygard format](https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions),
numbered sequentially. ADRs are append-only: when a decision is reversed, a new
ADR supersedes the old one rather than editing history.

The initial batch (`0001`–`0008`) records the **Tier-1** decisions that gate
freezing the published `velocity-spi` contract — the decisions that are
expensive to get wrong because they would force a re-cut of a published SPI.
Anything that touches the frozen SPI contract is **ADR-first**: write the ADR
before the code.

## Roadmap & phasing

The roadmap is phased by module build order, described in requirements
[§6.2](./docs/requirements.md#62-proposed-module-layout-mirrors-pk-auth) and
[§11](./docs/requirements.md#11-acceptance-criteria-v1-done). The phases are not
speculative wishlist items — each gates the next:

- **Phase 1 (in progress).** The contract and engine skeleton: `velocity-spi`,
  `velocity-core`, `velocity-api`, `velocity-testkit`. The Tier-1 ADRs must be
  settled here because the SPI is frozen at the end of this work.
- **Phase 2 (v1).** The two committed production backends —
  `velocity-backend-jdbi` (Postgres, tumbling reference) and
  `velocity-backend-redis` (sliding, hot-path reference) — plus the
  `velocity-dropwizard` service and the generated `velocity-client-java`. Both
  window shapes are exercised through the SPI's sliding/tumbling capability split
  before the contract is considered stable. v1 "done" is defined by the
  acceptance criteria in §11.
- **Phase 3.** `velocity-backend-dynamodb` (volume).
- **Phase 4.** `velocity-backend-s3` (cost, approximate).

Enterprise-readiness commitments (governance, security disclosure, supply-chain
provenance, admin audit log, data export/migration, cost model, load-tested
production-readiness proof) are tracked as the **GR-** requirements in
[§16](./docs/requirements.md#16-governance--enterprise-readiness). This document
and [`SECURITY.md`](./SECURITY.md) are themselves GR-2 and GR-3.

## Becoming a contributor

Contributions are welcome. The path is ordinary open-source practice:

1. Read [`CONTRIBUTING.md`](./CONTRIBUTING.md) for the build, the enforced
   conventions, and the ADR-first expectation for design changes.
2. For anything beyond a small fix, open an issue first so the approach can be
   agreed before you invest in a pull request — especially for anything touching
   the `velocity-spi` contract, which is a frozen compatibility surface.
3. Submit a pull request against `main`. CI (`./gradlew clean build test` on
   JDK 21) is the required status check.

## Becoming a maintainer

There is no committee to petition. Maintainership is earned through a sustained
track record of high-quality contributions and reviews, and is extended by the
lead maintainer. Growing the maintainer set — and thereby the bus factor — is an
explicit goal for the project, not an afterthought. If you are contributing
regularly and want to take on more responsibility, say so.
