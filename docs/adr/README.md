# Architecture Decision Records

This directory holds the Velocity Engine's Architecture Decision Records in
[Nygard format](https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions),
numbered sequentially. ADRs are append-only: when a decision is reversed, a new
ADR is added and the prior one is marked `Superseded by NNNN`. See
[ADR 0001](./0001-record-architecture-decisions.md) for the format itself.

The initial batch (`0001`–`0009`) records the **Tier-1** decisions from requirements
[§15](../requirements.md#15-review-driven-revisions-v02) — the decisions that **gate freezing the
published `velocity-spi` contract** and so must be recorded before code. `0009` was added after the
v0.2 re-review: it completes `0007` by freezing the *full* hot-path result shape (value-or-failure)
before the SPI ships. The remaining BR-6 items (Dagger-for-Dropwizard, track-latest Dropwizard,
OpenAPI-first client generation, stateless tier, server-clock event time, feature-as-core-abstraction,
`BigDecimal`-cents money, API-key auth, JDBI reference backend) and the Tier-2/Tier-3 *behavior*
decisions (hot-path deadline & bounded-failure enforcement, load-shedding & fairness, namespace-scoped
authz, keyed-hash-at-rest) will be added as those phases land.

| # | Status | Date | Title |
|---|---|---|---|
| [0001](./0001-record-architecture-decisions.md) | Accepted | 2026-07-18 | Record architecture decisions |
| [0002](./0002-velocity-spi-standalone-module.md) | Accepted | 2026-07-18 | `velocity-spi` is a standalone contract module |
| [0003](./0003-spi-capability-mixins.md) | Accepted | 2026-07-18 | SPI is capability mix-ins; window/bucketing boundary lives inside the backend |
| [0004](./0004-mandatory-conformance-tck.md) | Accepted | 2026-07-18 | Mandatory SPI conformance TCK in `velocity-testkit` |
| [0005](./0005-hll-tumbling-only.md) | Accepted | 2026-07-18 | HyperLogLog restricted to tumbling windows; sliding DISTINCT is exact-only |
| [0006](./0006-distinct-exact-hll-threshold.md) | Accepted | 2026-07-18 | DISTINCT exact→HLL threshold is a backend-clampable capability with a per-bucket merge rule |
| [0007](./0007-read-your-write-declared-capability.md) | Accepted | 2026-07-18 | Read-your-write is a declared capability, not a universal guarantee |
| [0008](./0008-seed-backfill-contract.md) | Accepted | 2026-07-18 | Seed/backfill contract is resolved before SPI freeze |
| [0009](./0009-hot-path-result-dto.md) | Accepted | 2026-07-18 | Hot-path result is a frozen value-or-failure sum type (completes 0007) |
