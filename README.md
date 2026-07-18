# velocity-engine

> **Status: pre-release (0.1.0-SNAPSHOT).** The design is complete and the
> Tier-1 architecture is decided; **implementation is just starting.** The
> modules today are compiling skeletons (`package-info.java` and stubs) — there
> is **no functional counting code, no benchmarks, and nothing published to
> Maven Central yet.** This README describes what the engine *is designed to be*
> and points at the authoritative design; do not read it as a description of
> shipped, working features.

A backend-agnostic **counting substrate** for the JVM: it records events and
answers *"how many / how much / how many distinct X has happened for this
subject in the last N units of time?"* — the primitive behind rate limiting,
fraud/abuse detection, quota enforcement, and anomaly monitoring.

## What it is — and what it is not

The Velocity Engine is a **counting substrate, not a decisioning platform.** It
records events and returns aggregated velocities (counts, sums, distinct
cardinalities) over time windows. **The caller owns every threshold, rule, and
ALLOW/REVIEW/DENY decision.** A rules/decisioning layer can be built *on top of*
the engine, but that is explicitly out of scope for v1. If you are evaluating
this for a fraud or abuse path, read that sentence again: the counting is ours,
the decision is yours. (This is requirement **GR-1** — positioning honesty.)

The core abstraction is the **feature**: a named velocity counter —
`(subject-type, aggregation, window[, dimension])` given a stable name such as
`card.count.1h` or `card.distinct.ip.1d`. One `record()` call fans a single
event out to every configured feature it matches, updating them together and
returning the post-update velocities.

## Design tenets

- **Pluggable backends.** The storage backend is an SPI in its own module
  (`velocity-spi`); nothing in the engine hard-depends on any one datastore.
- **Capability-driven.** Each backend *declares* what it supports — which
  windows, which aggregations, exactness, maximum retention, read-your-write
  strength, idempotency. The engine validates every feature definition against
  the backend it targets and **fast-rejects** what a backend cannot do rather
  than silently degrading. It never pretends a backend can do something it can't.
- **Dropwizard is *the* framework.** The only supported standalone runtime is
  Dropwizard. Unlike the sibling `pk-auth` project, the engine deliberately does
  **not** ship Spring, Micronaut, or any other framework adapter. The counting
  logic is a plain library, so it *can* be embedded in an existing process, but
  the only shipped, supported service is the Dropwizard one.
- **Portable config & data.** Feature definitions import/export as **YAML**;
  event records import as **YAML or JSON**. Configuration and onboarding data are
  versionable, reviewable, and portable between environments.

## Architecture at a glance

```
  HTTP/JSON clients ─▶  velocity-dropwizard  (stateless delivery tier)
                              │ calls
  embedded library ─▶  velocity-core         (record()/query(), fan-out, YAML I/O)
                              │ depends on
                       velocity-spi           (the contract: capability mix-ins + DTOs)
                              │ implemented by
        ┌───────────────┬─────┴──────┬────────────────┬───────────────┐
   backend-jdbi     backend-redis  backend-dynamodb  backend-s3    testkit
   (Postgres,       (sliding,      (volume)          (cost,        (in-memory
    tumbling, v1)    hot-path, v1)                    approx)       + TCK)
```

The central design seam is **where windowing lives**: `velocity-core` resolves
fan-out to backend-neutral *intents* `(feature, subject, aggregation,
member|value)` and does **not** compute buckets or storage keys. The **backend**
owns everything time-shaped — bucket keying, sliding-vs-tumbling semantics,
eviction/TTL, and its own key schema. There is intentionally **no single
storage-key schema** (Redis ZSET keys ≠ DynamoDB items ≠ S3 paths). The SPI is
therefore not one fat interface but **capability mix-ins**: a backend implements
only what it declares.

## Modules

Phase 1 modules exist as skeletons today; later phases are planned.

| Module | Purpose | Phase |
|--------|---------|-------|
| `velocity-spi` | The contract: capability mix-ins (`CountStore`, `SumStore`, `DistinctStore`, `SlidingSupport`, `TumblingSupport`, `SeedSupport`), `BackendCapabilities`, shared DTOs | 1 |
| `velocity-core` | Engine: `record()`/`query()`, feature/fan-out resolver, window/aggregation model, YAML I/O | 1 |
| `velocity-api` | OpenAPI 3.1 document + shared API DTOs (source of truth for the client) | 1 |
| `velocity-testkit` | In-memory SPI implementation, fixtures, and the **conformance TCK** every backend must pass | 1 |
| `velocity-backend-jdbi` | PostgreSQL/JDBI backend — **v1 tumbling reference** | 2 |
| `velocity-backend-redis` | Redis backend (sliding, exact, low-latency) — **v1 sliding/hot-path reference** | 2 |
| `velocity-dropwizard` | Dropwizard bundle, Jersey resources, Dagger wiring, API-key auth | 2 |
| `velocity-client-java` | Java client generated from the OpenAPI document | 2 |
| `velocity-backend-dynamodb` | DynamoDB backend (volume; single-table, atomic ADD) | 3 |
| `velocity-backend-s3` | S3 backend (cost; tumbling, approximate) | 4 |

**Both Postgres and Redis are committed v1 production backends** — Postgres
proves exact record-and-return against infrastructure most adopters already run;
Redis proves the true-sliding, low-latency hot path the fraud/gateway use cases
need. DynamoDB (volume) and S3 (cost) follow.

## Stack

- **Java 21** (language + toolchain), Gradle multi-module with `build-logic`
  convention plugins.
- **Dagger 2** for compile-time dependency injection — no Spring, no runtime
  reflective IoC container.
- **Dropwizard** for the service tier (the only standalone runtime).
- **`BigDecimal` denominated in cents** for money — never binary floating point.
- **jspecify** nullability annotations; **Jackson 3** for JSON.
- **BSD-3-Clause** across all modules, SPDX headers on every source file.

## Build

```sh
./gradlew clean build test        # full build + all module tests (the CI gate)
```

Requires **JDK 21** (Gradle's toolchain will fetch one if it is not present).

## Where the design lives

This project is design-first: the specification and the decision records are the
source of truth, ahead of the code.

- **[`docs/requirements.md`](./docs/requirements.md)** — the authoritative spec.
  Start with §1 (what it is), §2.1 (locked parameters P1–P18), §6 (architecture
  and the SPI), and §15 (the review-driven revisions that shape the SPI).
- **[`docs/adr/`](./docs/adr/)** — the Architecture Decision Records. The Tier-1
  batch (`0001`–`0008`) records the decisions that **gate freezing the published
  `velocity-spi` contract**.
- **[`CONTRIBUTING.md`](./CONTRIBUTING.md)** · **[`GOVERNANCE.md`](./GOVERNANCE.md)**
  · **[`SECURITY.md`](./SECURITY.md)** · **[`CHANGELOG.md`](./CHANGELOG.md)**.

The project deliberately mirrors the conventions of the sibling project at
`../pk-auth` (build-logic, version catalog, nmcp publishing, ADR style).

## License

BSD-3-Clause — see [`LICENSE`](./LICENSE). Copyright © 2026 Ned Wolpert.
