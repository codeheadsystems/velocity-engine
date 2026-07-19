# Velocity Engine — Requirements

> Status: **Draft v0.3** · Date: 2026-07-18 · Owner: Ned Wolpert
> License: **BSD-3-Clause** · Group: `com.codeheadsystems` · Root package: `com.codeheadsystems.velocity`

This document captures the functional and non-functional requirements for the Velocity Engine,
an open-source, pluggable, backend-agnostic system for recording events and querying their
*velocity* — counts, sums, and distinct cardinalities of events per key over time windows.

Requirement IDs (`FR-*`, `NFR-*`) are stable references for design docs, ADRs, and issues.
Anything not yet decided lives in [§13 Open Questions](#13-open-questions).

> **v0.2 changelog.** Revised after a four-role review (fraud/rules adopter, gateway security
> operator, engineering architect, business leader). New requirements and the fixes for
> contradictions surfaced by that review are consolidated in **[§15 Review-Driven Revisions](#15-review-driven-revisions-v02)**
> and **[§16 Governance & Enterprise Readiness](#16-governance--enterprise-readiness)**; the hard
> contradictions they found (universal read-your-write, HLL-on-sliding, single storage-key schema,
> tumbling-only v1) are also corrected inline below. Where §15 amends an earlier clause, the earlier
> clause carries a "→ see §15" pointer.

> **v0.3 changelog.** After a second pass by the same four reviewers (evaluating readiness to start
> building), the following were folded in: the hot-path result DTO is **frozen complete in phase 1**
> as a value-or-failure sum type (**[ADR 0009](adr/0009-hot-path-result-dto.md)**, completing ADR 0007);
> `velocity-spi` is **serialization-neutral** (Jackson moves to `velocity-core`); HLL sketches are
> **same-implementation-only** (cross-backend HLL migration dropped from v1); Postgres **and** Redis
> are both committed **production-grade** v1 backends; and new acceptance gates were added
> (idempotency-exactness #15, seed-implemented #16) along with KMS key-custody for R11 and
> `deadline-exceeded`/`overloaded` problem types in AR-5.

---

## 1. Purpose & Vision

A velocity engine answers questions of the form: *"how many / how much / how many distinct X has
happened for this subject in the last N units of time?"* — the primitive behind rate limiting,
fraud/abuse detection, quota enforcement, and anomaly monitoring.

The Velocity Engine is a **counting substrate**, not a decisioning platform. It records events and
returns aggregated velocities. **The caller owns all thresholds, rules, and decisions.** A
higher-level rules/decisioning layer may be built *on top of* the engine later, but is explicitly
out of scope for v1 (see [§12 Non-Goals](#12-non-goals-v1)).

Design tenets, inherited from the `pk-auth` sibling project (`../pk-auth`):

- **Pluggable backends** — the storage backend is an SPI defined in its own module
  (`velocity-spi`); nothing in the engine hard-depends on any one datastore.
- **Dropwizard is *the* framework** — Dropwizard is the **only** supported standalone runtime.
  Unlike the `pk-auth` sibling, the engine deliberately does **not** ship Spring, Micronaut, or any
  other framework adapter. The counting logic is a plain library (so it *can* be embedded in an
  existing process), but the only shipped and supported service is the Dropwizard one.
- **Capability-driven backends** — each backend declares what it supports: windows, aggregations,
  exactness, and **maximum data retention**. The engine never pretends a backend can do something it
  cannot, and validates every feature definition against the backend it targets.
- **Portable config & data** — feature definitions import/export as **YAML**; event records import
  as **YAML or JSON**. Configuration and onboarding data are versionable, reviewable, and portable
  between environments.

---

## 2. Scope Decisions (locked)

These were decided at project kickoff and constrain everything below.

| # | Decision | Choice |
|---|----------|--------|
| D1 | Engine responsibility | **Counting substrate only.** No rules/decision engine in v1. |
| D2 | Aggregations | **Count**, **Sum** (of a numeric value), **Distinct/unique count**. (No min/max/avg in v1.) |
| D3 | Window model | **Both sliding & tumbling, declared per-backend as a capability.** |
| D4 | Primary interaction | **Record-and-return-velocities** in a single synchronous call. Read-your-write strength is a **declared backend capability** (`atomic` / `snapshot` / `besteffort`), *not* universal — exact backends are `atomic`; approximate backends (S3) are `besteffort` and flag results. → see [§15 R2](#15-review-driven-revisions-v02). |
| D5 | Key model | **Structured key + fan-out.** One `record()` updates many `(subject, aggregation, window)` tuples. |
| D6 | Tenancy | **Namespace is first-class**, threaded through API, SPI, and storage keys. |
| D7 | Event time | **Server clock only** in v1. Ingest time = event time. No client backdating/replay. |
| D8 | Distributed state | **Stateless app tier; backend is the source of truth.** Atomic backend ops. Local buffering deferred (D8a). |

- **D8a (deferred):** an opt-in "local buffer + periodic flush" mode (trading exactness for
  throughput, valuable for S3-class backends) is a *future* backend/runtime option, not v1.

### 2.1 Resolved parameters

Decisions resolved after kickoff (formerly open questions). These are binding for v1.

| # | Topic | Resolution |
|---|-------|-----------|
| P1 | Root package | `com.codeheadsystems.velocity` |
| P2 | Artifact naming | `velocity-*` (e.g. `velocity-core`, `velocity-backend-jdbi`) |
| P3 | `SUM` numeric type | **`BigDecimal`, denominated in cents** (integer-cent scale; no binary-float money). |
| P4 | Distinct implementation | **Exact where cheap; HyperLogLog above a per-feature cardinality threshold.** Result flagged exact/approx. |
| P5 | Subject types | **Free-form** typed strings (`type:value`); no pre-registration required. |
| P6 | Feature-definition reload | **Hot-reload in v1** — feature definitions are live-reconfigurable without redeploy. |
| P7 | Rate limits / quotas | **Deferred** to a later version. |
| P8 | SLOs | **Defined per backend tier** (each backend module documents its own latency/throughput/accuracy SLOs). |
| P9 | Service auth (v1) | **API keys** as the default mechanism; auth remains pluggable (mTLS/JWT later). |
| P10 | Backend build order | **v1 ships two backends: JDBI/PostgreSQL (tumbling reference) *and* Redis (sliding, hot-path)**, so v1 covers both window shapes. Then DynamoDB → S3. *(Revised in v0.2 per review — see [§15 R8](#15-review-driven-revisions-v02); Redis moved from phase 4 into phase 2.)* |
| P11 | Client generation | **`openapi-generator`** from the committed OpenAPI document. |
| P12 | Core abstraction | The **feature** (see [§3](#3-domain-model--terminology)) is the canonical unit — a named velocity counter. |
| P13 | Standalone framework | **Dropwizard only.** No Spring/Micronaut/other adapters (unlike `pk-auth`). Core is still a plain embeddable library. |
| P14 | SPI packaging | The core↔backend contract lives in its **own module, `velocity-spi`**; core and every backend depend on it. |
| P15 | Feature ↔ backend binding | Each **feature definition names the backend** that provides it; definitions are validated against that backend's capabilities. |
| P16 | Config & data I/O | **Feature definitions ⇄ YAML** (import/export). **Event records ⇄ YAML or JSON** (bulk import for onboarding). |
| P17 | Retention | **Maximum data lifetime is a backend-declared capability** (`maxRetention`), the reason window ranges differ per backend. |
| P18 | Backend numbers | A backend's concrete windows/retention/exactness/SLOs are **fixed at that backend's dev time** and documented in its module — never hardcoded in core. |

---

## 3. Domain Model & Terminology

- **Namespace** — tenant/isolation boundary. All keys are scoped by namespace. (`acme`)
- **Subject** — the entity a velocity is measured *for*: a typed key. (`card:1234`, `user:42`,
  `ip:203.0.113.7`)
- **Event** — a single occurrence submitted via `record()`. Carries the subject, an optional set of
  **dimensions**, and an optional numeric **value**.
- **Dimension** — an attribute of the event used for distinct-counting or as an additional
  fan-out subject. (`ip`, `merchant`, `deviceId`)
- **Value** — the numeric payload summed for `SUM` aggregations, a `BigDecimal` in **cents**
  (P3). (e.g. transaction `amount` = `14950` cents)
- **Aggregation** — `COUNT`, `SUM`, or `DISTINCT(dimension)`.
- **Window** — a time span over which an aggregation is computed. Has a *duration* and a *type*
  (`SLIDING` | `TUMBLING`), and belongs to a backend-declared set (e.g. `5s, 30s, 1m, 30m` for
  Redis; `1h, 1d, 1w, 30d` for S3).
- **Feature** *(canonical unit, P12)* — a **named velocity counter**: the tuple
  `(subject-type, aggregation, window[, dimension])` given a stable name, e.g.
  `card.count.1h` or `card.distinct.ip.1d`. Features are the engine's primary abstraction —
  what callers configure, record into, and query. This matches the "feature" of fraud/velocity
  and feature-store systems.
- **Feature definition** — the configuration for a feature: which event pattern feeds it, its
  aggregation, window, optional dimension, and (for distinct) its exact/HLL threshold. Definitions
  are per-namespace, hot-reloadable (P6). The set of definitions that a single event matches is
  its **fan-out**.
- **Feature value (velocity)** — the computed result for a feature at a point in time:
  `(namespace, subject, feature) -> number` plus metadata (exact vs approximate, window bounds,
  as-of timestamp).

### 3.1 Example

```
record(
  namespace = "acme",
  subject   = card:1234,
  dimensions = { ip: 203.0.113.7, merchant: m-88 },
  value     = 14950            // transaction amount, BigDecimal cents ($149.50)
)

# feature definitions configured for namespace "acme" match this event; its fan-out updates:
#   feature card.count.1h        (card:1234 / COUNT        / {5s,1m,1h})
#   feature card.sum.1h          (card:1234 / SUM(value)   / {1m,1h})
#   feature card.distinct.ip.1d  (card:1234 / DISTINCT(ip) / {1h,1d})
#   feature ip.count.1h          (ip:203..  / COUNT        / {1m,1h})
#
# and returns the post-update feature values for all matched features (read-your-write).
```

---

## 4. Functional Requirements

### 4.1 Recording

- **FR-1** The engine SHALL provide `record()` accepting `(namespace, subject, dimensions?, value?,
  requestedWindows?)` and applying all configured fan-out rules. Atomicity is guaranteed **per storage
  key**; cross-key/cross-backend fan-out atomicity is a declared capability, and `record()` reports
  per-feature apply status. → see [§15 R3](#15-review-driven-revisions-v02).
- **FR-2** `record()` SHALL, in the same call, return the post-increment velocities for the affected
  `(subject, aggregation, window)` tuples, as the frozen **`FeatureResult` value-or-failure** type
  ([ADR 0009](adr/0009-hot-path-result-dto.md)): per feature, a value (with read-your-write level,
  `exact|approximate`, definition-version hash) or a distinguishable failure, plus an apply status.
  A caller MAY request a subset of windows/aggregations to bound payload size. The *strength* of
  "post-increment" (read-your-write) is per D4's declared capability, not universal.
  → see [§15 R2](#15-review-driven-revisions-v02).
- **FR-3** Event time SHALL be the server's ingest clock (D7); for sliding windows the **backend
  clock is authoritative** (not the pod clock). Any client-supplied timestamp is ignored in v1 (MAY
  be captured for audit but MUST NOT affect bucketing). → see [§15 R9](#15-review-driven-revisions-v02).
- **FR-4** A `record()` with no matching fan-out rule and no explicit target SHALL be a no-op that
  returns an empty velocity set (not an error).
- **FR-5** `record()` SHALL be idempotent *only* when the caller supplies an idempotency key AND the
  backend declares idempotency support; otherwise it is at-least-once. (See NFR-8.)

### 4.2 Querying

- **FR-6** The engine SHALL provide `query()` accepting `(namespace, subject, aggregation, window)`
  (single or batched) and returning the current velocity **without** recording an event.
- **FR-7** Query results SHALL carry: value, window type & bounds, `exact|approximate` flag, and an
  `as-of` timestamp.
- **FR-8** The engine SHALL support batched queries for multiple tuples in one call to minimize
  round trips.

### 4.3 Aggregations (D2)

- **FR-9** SHALL support `COUNT` — number of events for a subject in the window.
- **FR-10** SHALL support `SUM` — sum of the event `value` for a subject in the window. `value` and
  the resulting sum are **`BigDecimal` denominated in cents** (P3); the engine MUST NOT use binary
  floating-point for money. Sums MUST NOT silently overflow (unbounded `BigDecimal`).
- **FR-11** SHALL support `DISTINCT(dimension)` — cardinality of a dimension's values for a subject in
  the window. A feature is **exact where cheap and switches to HyperLogLog above a per-feature
  cardinality threshold** (P4); the threshold is configurable per feature definition **but is
  clampable by the backend** (e.g. DynamoDB's item-size ceiling). Every result MUST be flagged
  `exact|approximate` (FR-7). **HLL is only valid on tumbling windows** (a sketch cannot evict aging
  members); HLL-distinct on a sliding window MUST be rejected — sliding distinct is exact-only with a
  bounded cardinality cap. Per-bucket exact/HLL heterogeneity and the read-time merge rule are
  defined in → [§15 R5/R6](#15-review-driven-revisions-v02).

### 4.4 Windows (D3)

- **FR-12** Each backend SHALL declare, via the capability SPI, the set of windows it supports, and
  for each: duration, type (`SLIDING`|`TUMBLING`), exactness, and granularity/resolution.
- **FR-13** The engine SHALL reject (fast, with a clear error) a `record()`/`query()` that targets a
  window the active backend does not support, rather than silently degrading.
- **FR-14** `SLIDING` windows SHALL cover `[now - duration, now]`. `TUMBLING` windows SHALL be
  aligned, fixed buckets; a multi-bucket window is the merge of its buckets and MAY be
  edge-approximate at the current bucket boundary (this MUST be documented per backend).
- **FR-15** Window sets are backend-characteristic but SHOULD be configurable within the backend's
  declared capabilities (e.g. Redis operator picks `5s/30s/1m/30m`).

### 4.5 Feature definitions & fan-out

- **FR-16** Feature definitions SHALL be expressible as configuration (data, not code), scoped per
  namespace, each with a stable feature name, subject-type, aggregation, window, optional dimension,
  (for distinct) an exact/HLL threshold, and the **target backend** that provides it (a feature is
  bound to exactly one backend; see FR-29).
- **FR-17** Feature definitions SHALL be **hot-reloadable at runtime** (P6): adding, removing, or
  changing a definition MUST take effect without redeploying or restarting the service. Reloads MUST
  be atomic (no request sees a half-applied definition set) and versioned/observable for audit.
- **FR-18** A single event SHALL be able to fan out to multiple features across multiple subjects
  (the primary subject plus dimension-derived subjects, e.g. also counting per-`ip`).
- **FR-28** Feature definitions SHALL be **importable and exportable as YAML** (round-trippable), so
  a namespace's feature set is versionable in source control and portable between environments. The
  YAML document is the canonical external representation of feature configuration.
- **FR-29** On import/reload, every feature definition SHALL be **validated against its target
  backend's declared capabilities** (window supported, aggregation supported, retention sufficient,
  distinct exact/HLL feasible). Invalid definitions MUST be rejected with a precise error and MUST
  NOT partially apply (atomic with FR-17).

### 4.6 Namespaces / multi-tenancy (D6)

- **FR-19** Every API and SPI operation SHALL require a namespace; storage keys SHALL be
  namespace-prefixed so tenants never collide.
- **FR-20** The engine SHALL support many namespaces within one deployment.
- **FR-21** Per-namespace configuration (fan-out rules, allowed windows, limits) SHALL be supported;
  per-namespace quotas/rate-limits are deferred to a later version (P7).

### 4.7 Lifecycle / retention

- **FR-22** Windowed data SHALL expire automatically once no window can reference it (TTL/eviction),
  using native backend TTL where available (Redis TTL, DynamoDB TTL, S3 lifecycle, SQL sweep job).
- **FR-22a** The **maximum time data can exist in a datastore is a property of the backend** and
  SHALL be declared via the capability SPI as `maxRetention`. A feature definition whose largest
  window exceeds its backend's `maxRetention` MUST be rejected (FR-29). This is *why* window ranges
  differ per backend (Redis seconds–minutes; S3 hours–months): the retention ceiling is intrinsic to
  the store, not an arbitrary choice.
- **FR-23** The engine SHALL provide an administrative `reset`/`purge` for a `(namespace, subject)`
  or namespace (e.g. GDPR erasure, test cleanup). This is an admin operation, separate from the
  hot path.

### 4.8 Data onboarding & bulk record I/O

Adopting the engine, seeding a new namespace, or migrating from another system requires getting
existing event data *in*. This is a first-class concern, distinct from the hot `record()` path.

- **FR-30** The engine SHALL support **bulk import of event records** from a **YAML or JSON**
  document (batch ingest), applying the same fan-out and validation as single-event `record()`.
- **FR-31** Bulk import SHALL be **idempotent-friendly** (resumable/re-runnable via per-record
  idempotency keys where the backend supports it, FR-5) and SHALL report a per-record success/reject
  summary rather than failing the whole batch on one bad record.
- **FR-32** Onboarding SHALL respect the v1 **server-clock** semantics (D7): imported records are
  counted at import time, not at any historical timestamp they may carry. For seeding *pre-computed*
  historical aggregates (as opposed to replaying events), the engine SHALL, where the target backend
  can represent it, support **seeding aggregate/bucket values directly** as an admin operation —
  clearly flagged as onboarding-seeded rather than organically recorded. (Backends that cannot
  represent seeded buckets MUST declare so; see [§13](#13-open-questions) OQ-F.)
- **FR-33** Bulk import and seeding are **admin/operational** surfaces, rate-isolated from the hot
  path so onboarding a large dataset does not degrade live record/query latency.

### 4.9 Delivery surfaces

- **FR-24** The counting logic SHALL be usable as an **in-process Java library** with no network
  dependency (embeddable core).
- **FR-25** A **Dropwizard service** SHALL expose the engine over HTTP/JSON per the OpenAPI contract
  ([§7](#7-api--client)).
- **FR-26** The service API SHALL be specified by a committed **OpenAPI 3.1 (Swagger) document** that
  is the source of truth for the generated Java client.
- **FR-27** A **default Java client** SHALL be generated from the OpenAPI document and published to
  Maven Central.

---

## 5. Non-Functional Requirements

- **NFR-1 (Java/build):** Java 21 (language + toolchain). Gradle (latest, wrapper-pinned),
  multi-module, with `build-logic` convention plugins mirroring `pk-auth`.
- **NFR-2 (DI):** Dagger 2 for compile-time dependency injection. **No Spring.** No runtime
  reflective IoC container.
- **NFR-3 (Framework):** Dropwizard (latest major, tracked) for the service tier — see ADR to mirror
  `pk-auth` ADR 0010 ("track latest Dropwizard").
- **NFR-4 (Nullability/serialization):** jspecify annotations; Jackson 3 for JSON (mirror `pk-auth`).
- **NFR-5 (Statelessness):** The app tier MUST be stateless (D8). Any instance serves any request;
  horizontal scale is "add pods." All shared counting state lives in the backend.
- **NFR-6 (Atomicity):** For any backend claiming exactness, record-and-return MUST be atomic under
  concurrency (e.g. Redis `INCR`/pipelines/Lua, DynamoDB atomic `ADD` + conditional writes). Backends
  that cannot MUST declare `approximate`.
- **NFR-7 (Consistency):** Read-your-write is a **declared capability** (`atomic` / `snapshot` /
  `besteffort`), not universal, and is defined precisely as "the returned value reflects the caller's
  own write to every bucket it touched; window-aggregate consistency is per the backend's declared
  level." Exact backends are `atomic`; approximate backends are `besteffort` and MUST flag results and
  document staleness/error bounds. → see [§15 R2](#15-review-driven-revisions-v02).
- **NFR-8 (Delivery semantics):** Default at-least-once. Because non-idempotent COUNT double-counts
  under retries, **v1 reference backends (JDBI, Redis) MUST declare idempotency support**, and an
  idempotency key is the **recommended posture for inline callers** (FR-5). §8 "Exact" is understood as
  *exact given exactly-once / idempotency-key delivery*. → see [§15 R15](#15-review-driven-revisions-v02).
- **NFR-9 (Performance targets, per backend tier):** Redis tier SHOULD serve record-and-return at
  single-digit-millisecond p99 for a modest fan-out. Concrete SLOs are set per backend (P8, OQ-A); the doc
  MUST state that S3-class backends trade latency/accuracy for cost by design.
- **NFR-10 (Portability):** Runs as a single process, on a VM/bare server, or as replicas under
  Kubernetes or AWS ECS with no code change — only configuration.
- **NFR-11 (Observability):** Structured logging (slf4j), Micrometer metrics (record/query rates,
  latencies, backend errors, fan-out counts), and health checks (Dropwizard). Metrics MUST be
  namespace-labelable but MUST NOT explode cardinality by subject; the `namespace × feature ×
  exact/approx × backend` label product MUST be explicitly bounded (→ [§15 R16](#15-review-driven-revisions-v02)).
- **NFR-12 (Security):** Transport TLS; pluggable authn/authz on the service API. **v1 default is
  API keys** (P9), with the auth layer pluggable so mTLS/JWT (incl. reuse of `pk-auth`) can be added
  without API changes. The engine MUST NOT log raw subject/dimension values at default levels (PII
  risk). Default deny on unauthenticated mutating calls.
- **NFR-13 (Testability):** A `testkit` module (mirroring `pk-auth-testkit`) provides in-memory
  backend + fixtures. Backend modules run integration tests against real engines (Redis via
  Testcontainers, DynamoDB Local, Postgres via Testcontainers, S3 via LocalStack/MinIO).
- **NFR-14 (Coverage):** Convention-plugin coverage gates mirroring `pk-auth` (core held to a higher
  line bar than adapters; Dagger-generated classes excluded).
- **NFR-15 (Licensing):** BSD-3-Clause across all modules; SPDX headers; every published artifact
  carries license + sources + javadoc jars.
- **NFR-16 (Publishing):** All library modules publish signed artifacts to Maven Central via the
  Sonatype Central Portal using the `nmcp` aggregation flow, exactly as `pk-auth` does. Version
  derived from git tag `vX.Y.Z`, SNAPSHOT otherwise.
- **NFR-17 (Compatibility):** Semantic versioning. The OpenAPI contract and the **`velocity-spi`
  interfaces + DTOs** are compatibility surfaces; SPI evolution MUST be **additive-only** with new
  capability fields defaulting to "unsupported." There is **no single storage-key schema** — each
  backend owns its own key schema as *its* compatibility surface (Redis ZSET keys, Dynamo items, S3
  paths are not one schema). → see [§15 R1](#15-review-driven-revisions-v02).

---

## 6. Architecture Overview

```
                 ┌─────────────────────────────────────────────┐
   HTTP/JSON     │              Delivery tier                  │
   clients  ───► │  velocity-dropwizard (stateless)            │
                 │   • Jersey resources  • Dagger wiring        │
                 │   • authn/z filter    • OpenAPI-described    │
                 └───────────────────┬─────────────────────────┘
                                     │ calls
                 ┌───────────────────▼─────────────────────────┐
   embedded      │              Engine core (library)          │
   library   ───►│  velocity-core                              │
   users         │   • record()/query()  • fan-out resolver    │
                 │   • window/aggregation model  • YAML I/O     │
                 └───────────────────┬─────────────────────────┘
                                     │ depends on
                 ┌───────────────────▼─────────────────────────┐
                 │   velocity-spi  (the contract module)       │
                 │   • capability mix-ins • BackendCapabilities│
                 │   • intents • FeatureResult (value|failure) │
                 └───────────────────┬─────────────────────────┘
                                     │ implemented by
        ┌──────────────┬─────────────┼───────────────┬──────────────┐
        ▼              ▼             ▼               ▼              ▼
   backend-jdbi   backend-dynamodb backend-redis backend-s3   (in-memory,
   (postgres,     (volume)         (speed)       (cost,        testkit)
    v1 ref)                                      approx)
```

### 6.1 The SPI module (`velocity-spi`)

The contract between the core and the backends lives in its **own module**, `velocity-spi`, so a
backend can be built and versioned against a stable contract without depending on the engine's
internals. Both `velocity-core` and every `velocity-backend-*` depend on `velocity-spi`; backends do
**not** depend on `velocity-core`.

It defines the data-plane contract as **capability mix-ins** (not one fat interface — see
[§15 R1](#15-review-driven-revisions-v02)) plus a descriptor and shared DTOs. `velocity-core` resolves
fan-out to **backend-neutral intents** `(feature, subject, aggregation, member|value)`; the backend
owns bucket keying, sliding/tumbling semantics, eviction, and its own key schema.

1. **Data-plane mix-ins** — a backend implements only the ones it declares:
   `CountStore`, `SumStore`, `DistinctStore`, `SlidingSupport`, `TumblingSupport`, `SeedSupport`
   (onboarding `seed(...)`, FR-32 — its schema resolved before freeze per
   [§15 R3b](#15-review-driven-revisions-v02)). Common ops: `apply(intents) -> ApplyResult`,
   `query(tuples) -> FeatureResult[]`, `purge(...)`. `apply`/`query` honor a **caller deadline**, and
   the result is a **frozen value-or-failure sum type** — `FeatureResult = Success{FeatureValue} |
   Failure{FailureCode}` — carrying, per feature, the read-your-write level, `exact|approximate`,
   the definition-version hash, and (for `apply`) the per-feature apply status. This full shape is
   frozen in phase 1 (**[ADR 0009](adr/0009-hot-path-result-dto.md)**), so a stalled backend returns a
   distinguishable `UNAVAILABLE`/`DEADLINE_EXCEEDED`, never a silent `0`. The SPI DTOs are
   **serialization-neutral** (plain records, no Jackson; JSON lives in `velocity-core`/the wire layer —
   [ADR 0002](adr/0002-velocity-spi-standalone-module.md), [§15 R2b](#15-review-driven-revisions-v02)).
2. **`BackendCapabilities`** — the descriptor: supported windows (duration/type/exactness), supported
   aggregations, `distinctHllSliding`, distinct exact-cardinality clamp & default threshold,
   **`maxRetention`** (FR-22a), read-your-write level (`atomic|snapshot|besteffort`), idempotency
   support, seed support, and **`maxAtomicFanOut`**. The engine consults this to validate feature
   definitions and requests (FR-12/13/29) and to shape defaults.

Every backend MUST pass the `velocity-testkit` conformance TCK for its declared capabilities,
including negative tests ([§15 R7](#15-review-driven-revisions-v02)).

### 6.2 Proposed module layout (mirrors `pk-auth`)

| Module | Purpose | Build phase | Publishes |
|--------|---------|-------------|-----------|
| `velocity-spi` | **The contract module**: capability mix-ins, `BackendCapabilities`, serialization-neutral SPI DTOs (`FeatureResult`) | 1 | ✅ |
| `velocity-core` | Engine, feature/fan-out resolver, window/aggregation model, YAML I/O (depends on `velocity-spi`) | 1 | ✅ |
| `velocity-api` | OpenAPI 3.1 document + shared API DTOs (source of truth) | 1 | ✅ (spec artifact) |
| `velocity-testkit` | In-memory `velocity-spi` impl, fixtures, Testcontainers helpers | 1 | ✅ |
| `velocity-backend-jdbi` | PostgreSQL/JDBI backend (tumbling, reuse existing infra) — **v1 tumbling ref** | 2 | ✅ |
| `velocity-backend-redis` | Redis backend (sliding, exact, low-latency) — **v1 sliding/hot-path ref** | 2 | ✅ |
| `velocity-dropwizard` | Dropwizard bundle, Jersey resources, Dagger wiring, API-key auth | 2 | ✅ |
| `velocity-client-java` | Java client generated from OpenAPI via `openapi-generator` | 2 | ✅ |
| `velocity-backend-dynamodb` | DynamoDB backend (volume; single-table, atomic ADD) | 3 | ✅ |
| `velocity-backend-s3` | S3 backend (cost; tumbling, approximate) | 4 | ✅ |
| `examples:dropwizard-demo` | Runnable reference service | 2 | ❌ |

> **v1 ships both a tumbling backend (Postgres, reuse-infra) and a sliding backend (Redis,
> hot-path)** (P10, revised in v0.2), so the two window shapes — and the SPI's sliding/tumbling
> capability split ([§15 R1](#15-review-driven-revisions-v02)) — are both exercised before the SPI is
> frozen. DynamoDB (volume) and S3 (cost) follow. Postgres proves exact record-and-return against
> infrastructure most adopters already run; Redis proves the true-sliding low-latency path the
> fraud/gateway use cases need.

---

## 7. API & Client

- **AR-1** The HTTP API SHALL be defined by `velocity-api/openapi.yaml` (OpenAPI 3.1), which is
  the **single source of truth**.
- **AR-2** Core endpoints (indicative, to be finalized in the spec):
  - `POST /v1/{namespace}/record` → record event, return affected velocities (D4).
  - `POST /v1/{namespace}/query` → batched velocity query (no mutation).
  - `GET  /v1/{namespace}/capabilities` → backend-declared windows/aggregations/exactness.
  - `POST /v1/{namespace}/purge` → admin erasure for a subject/namespace.
- **AR-3** The Java client (`velocity-client-java`) SHALL be **generated by `openapi-generator`**
  (P11) from the OpenAPI document as part of the Gradle build (no hand-written drift), then published
  to Maven Central.
- **AR-4** The generated client SHALL be framework-neutral (usable outside Dropwizard) and depend only
  on the shared API DTOs + a standard HTTP client.
- **AR-5** Errors SHALL use a consistent problem model (RFC 9457 `application/problem+json`
  recommended) distinguishing: unknown namespace, unsupported window, validation error, backend
  **unavailable**, **`deadline-exceeded`** (distinct from unavailable — a caller's fail-open/closed
  policy may differ; NFR-19), **`overloaded`** (load-shed, NFR-22), and rate-limited. These mirror the
  SPI `FailureCode`s ([ADR 0009](adr/0009-hot-path-result-dto.md)) so a wire caller can branch the same
  way an embedded caller does.

---

## 8. Backend Capability Matrix

> **These numbers are placeholders, not commitments.** Each backend's concrete capabilities —
> exact window set, window type(s), exactness, `maxRetention`, distinct exact/HLL threshold, seed
> support, and SLOs — **are defined at development time for that `velocity-backend-*` module** and
> documented in the module's own README/ADR as it is built (P8, P10). The engine reads them at
> runtime from `BackendCapabilities`; it never hardcodes them. The table below only sketches the
> intended *character* of each backend to guide which one to reach for.

Listed in build order (P10) — **JDBI + Redis are both v1**. Values are indicative direction, to be
finalized per module.

| Backend | Optimizes for | Window character | Retention character | Exactness | DISTINCT | Atomic record+return |
|---------|---------------|------------------|---------------------|-----------|----------|----------------------|
| PostgreSQL (JDBI) — *v1 ref* | Reuse existing infra | Tumbling, minutes–days | Medium | Exact counts | Exact → HLL over threshold | Yes (upsert/txn) |
| DynamoDB | Volume / scale | Tumbling (+ short sliding) | Medium–long | Exact counts | Exact → HLL over threshold | Yes (atomic ADD) |
| Redis | Speed / latency | Sliding, seconds–minutes | Short | Exact | Exact only (ZSET member→ts, capped); **no HLL on sliding** | Yes (INCR/Lua) |
| S3 | Cost | Tumbling, hours–months | Long | Approximate | HLL | No — eventual/approx |

The engine surfaces each backend's *actual* declared capabilities truthfully to callers (FR-7,
FR-12, FR-22a) rather than papering over differences.

---

## 9. Deployment & Runtime

- **DR-1** Single-instance embedded (library) — no service, backend may even be in-memory for dev.
- **DR-2** Single service instance + shared backend (Redis/DDB/PG/S3).
- **DR-3** Multi-instance stateless service (K8s Deployment or ECS Service) behind a load balancer,
  all replicas sharing one backend (D8). No sticky sessions, no inter-instance coordination.
- **DR-4** Configuration (backend selection, window sets, fan-out rules, auth) via Dropwizard YAML +
  environment overrides. Backend is chosen by configuration, not by code change (NFR-10).

---

## 10. Build, Release & Governance (mirror `pk-auth`)

- **BR-1** `build-logic` convention plugins: `java-conventions`, `library-conventions`,
  `test-conventions`, `publish-conventions`, `e2e-conventions`.
- **BR-2** Gradle version catalog (`libs`) centralizes dependency versions.
- **BR-3** Configuration cache + parallel builds on; caching caveats documented as needed.
- **BR-4** `nmcp` settings plugin aggregates signed publications to the Central Portal; version from
  git tag.
- **BR-5** Repo docs to author alongside code: `README.md`, `DESIGN.md`, `GETTING_STARTED.md`,
  `CONTRIBUTING.md`, `SECURITY.md`, `CHANGELOG.md`, `RELEASE.md`, `LICENSE` (BSD-3-Clause), and ADRs
  under `docs/adr/`.
- **BR-6** First ADRs to record: (a) record ADRs, (b) Dagger for Dropwizard, (c) track-latest
  Dropwizard, (d) OpenAPI-first client generation with `openapi-generator`, (e) stateless tier /
  backend-is-truth, (f) backend capability SPI, (g) server-clock-only event time for v1,
  (h) feature as the core abstraction, (i) `BigDecimal`-cents money model, (j) exact→HLL distinct
  strategy, (k) API-key auth default (pluggable), (l) JDBI/Postgres as the v1 reference backend,
  (m) **SPI as capability mix-ins + mandatory conformance TCK** (§15 R1/R7),
  (n) **read-your-write as a declared capability, not universal** (§15 R2),
  (o) **hot-path deadline & bounded-failure contract** (§15 R2),
  (p) **HLL restricted to tumbling; sliding distinct exact-only** (§15 R5),
  (q) **namespace-scoped authorization** (§15 R4),
  (r) **keyed hashing of DISTINCT values at rest** (§15 R11),
  (s) **Redis (sliding) pulled into v1 alongside Postgres** (§15 R8).

---

## 11. Acceptance Criteria (v1 "done")

1. `velocity-core` + `velocity-backend-jdbi` (Postgres) implement record-and-return with COUNT,
   SUM (`BigDecimal` cents), and DISTINCT over ≥2 windows, exact, atomic under concurrent load
   (proven by Testcontainers-backed test).
2. **`velocity-backend-redis` (sliding, hot-path) is in v1** and implements true-sliding COUNT/SUM
   and exact sliding DISTINCT (ZSET-bounded), proven exact + read-your-write (`atomic`) under
   concurrent load. *(Both Postgres and Redis are committed v1 **production-grade** backends — both
   are load-tested with published SLOs and DR behavior per [GR-8](#16-governance--enterprise-readiness),
   not just TCK-passing — and the two window shapes exercise the SPI's sliding/tumbling split before
   freeze.)*
3. **SPI conformance TCK** ([§15 R7](#15-review-driven-revisions-v02)): both v1 backends pass the
   shared `*Scenarios` suite in `velocity-testkit`, including negative tests (HLL-on-sliding rejected;
   read-your-write on a `besteffort` backend flagged, not silently wrong).
4. Fan-out: one event updates multiple features across ≥2 subjects; `record()` returns **per-feature
   apply status** ([§15 R3](#15-review-driven-revisions-v02)).
5. **Hot-path failure contract** ([§15 R2](#15-review-driven-revisions-v02)): with the backend
   stalled/partitioned, record()/query() fail fast within the caller deadline and return a
   distinguishable `UNAVAILABLE`/`DEADLINE_EXCEEDED` (never a silent `0`), proven by a fault-injection
   test.
6. Feature definitions hot-reload at runtime with no restart (per-pod atomic swap); behavior under
   multi-pod version skew is defined and tested ([§15 R10](#15-review-driven-revisions-v02)).
7. Dropwizard service serves the OpenAPI contract behind API-key auth; **each API key is bound to an
   allowed namespace set and cross-namespace access is denied** ([§15 R4](#15-review-driven-revisions-v02));
   capabilities endpoint reflects the active backend.
8. **PII-at-rest**: exact DISTINCT dimension values are keyed-hashed at rest (per-namespace salt);
   no raw IP/token persists ([§15 R11](#15-review-driven-revisions-v02), resolves OQ-E).
9. **Feature discovery + versioning**: `GET /v1/{namespace}/features` lists definitions; every feature
   value carries the definition version/hash it was computed under ([§15 R12/R13](#15-review-driven-revisions-v02)).
10. `openapi-generator`-generated Java client round-trips record + query against the demo service
    in an integration test.
11. Namespaced storage keys proven isolated across ≥2 namespaces.
12. TTL/expiry verified (old data ages out) **and** window correctness proven independent of TTL lag
    (query-time bucket filtering, [§15 R14](#15-review-driven-revisions-v02)).
13. All modules build on Java 21 with Gradle, pass coverage gates, and produce Central-publishable
    signed artifacts (dry-run).
14. Admin **audit log** records purge/seed/config-change actions ([§16](#16-governance--enterprise-readiness)).
15. **Idempotency-exactness** ([§15 R15](#15-review-driven-revisions-v02)): JDBI and Redis declare
    idempotency support, and a **retry-storm test** proves COUNT stays exact under duplicate
    idempotency-key `record()`s (no double-count → no false fraud hit / false rate-limit trip).
16. **Seed actually implemented** ([§15 R3b](#15-review-driven-revisions-v02), ADR 0008): ≥1 v1 backend
    implements `SeedSupport`, and a `SeedSupportScenarios` test proves a **seeded** bucket and a
    **recorded** bucket merge identically through the same windowed read (so the seed contract is real
    in v1, not shelfware).

---

## 12. Non-Goals (v1)

- Rules / thresholds / decisioning (ALLOW/REVIEW/DENY) — caller's responsibility.
- Actions, webhooks, notifications, case management.
- Min/max/avg and percentile aggregations.
- Client-supplied event time and out-of-order replay (D7). *(Bulk onboarding import counts at
  ingest time; direct aggregate seeding is supported per FR-32 — neither is historical replay.)*
- **Framework adapters other than Dropwizard** — no Spring, Micronaut, Quarkus, etc. Dropwizard is
  the only supported standalone runtime (§1). Embedding the plain `velocity-core` library in another
  process is possible but is not a shipped/supported framework integration.
- Local-buffer/flush approximate mode (D8a) — future opt-in.
- Cross-namespace or global aggregations.
- A web UI / dashboard (metrics via Micrometer/Prometheus only).

---

## 13. Open Questions

Kickoff open questions are resolved in [§2.1 Resolved parameters](#21-resolved-parameters) (P1–P18).
The v0.2 review **reclassified several of these as blockers** (they gate a phase, not "later") and
**resolved OQ-E**:

- **OQ-A** Exact per-backend SLO numbers (NFR-9 / P8) — fixed in each backend module's ADR/README as
  it is built, starting with JDBI/Postgres and Redis. *(Follow-on.)*
- **OQ-B 🔴 BLOCKER (phase 2)** Default distinct exact→HLL threshold + HLL precision/error target —
  blocks JDBI/Redis distinct (acceptance #1/#2). Resolve before those backends ship. → [§15 R6](#15-review-driven-revisions-v02).
- **OQ-C** Feature-definition source of truth for hot-reload (P6): config file + watch, admin API, or
  a config table. Leaning: pluggable `FeatureDefinitionProvider` SPI. Interacts with version-skew
  ([§15 R10](#15-review-driven-revisions-v02)). *(Follow-on.)*
- **OQ-D ✅ RESOLVED** Wire money representation. Money — and every numeric `value` field (counts,
  cardinalities, sums) — crosses the wire as a **JSON string of a decimal integer** (`^-?\d+$`),
  money in integer cents (e.g. `"14950"` = $149.50), matching the SPI's `BigDecimal` scale-0 cents
  (P3). Chosen over a JSON number to avoid precision loss above 2⁵³ and float coercion in generated
  clients, and over a decimal string for uniformity with the integer-cent SPI. The OpenAPI spec at
  `velocity-api` (`Money`/`Value` schemas) encodes it. → [§15 R17](#15-review-driven-revisions-v02).
- **OQ-E ✅ RESOLVED** DISTINCT dimension values are keyed-hashed at rest (per-namespace salt). →
  [§15 R11](#15-review-driven-revisions-v02).
- **OQ-F 🔴 BLOCKER (SPI freeze, phase 1)** Seed/backfill schema — `seed()` is a *published* SPI
  method (§6.1); its contract (per-window bucket values) MUST be defined before the SPI is frozen, or
  `seed()` MUST be omitted from the v1 SPI. → [§15 R1/R3b](#15-review-driven-revisions-v02).
- **OQ-G** Onboarding import scale/transport: inline body vs bulk-file pointer, sync vs async (job +
  status). *(Follow-on.)*

---

## 14. Glossary

See [§3 Domain Model](#3-domain-model--terminology). Key terms: *namespace, subject, dimension,
value (`BigDecimal` cents), aggregation, window (sliding/tumbling), **feature**, feature definition,
feature value (velocity), fan-out, capabilities, read-your-write level, conformance TCK*.

---

## 15. Review-Driven Revisions (v0.2)

These requirements were added or amended after a four-role review of v0.1 (fraud/rules adopter,
gateway security operator, engineering architect, business leader). Each item notes the reviewers who
raised it. They are grouped by **tier** = when they must be resolved. Tier-1 items are the highest
priority: they gate the **freeze of the published `velocity-spi`**, and getting them wrong forces a
re-cut of a published SPI at phase 3.

### Tier 1 — must resolve BEFORE freezing `velocity-spi` (phase 1)

- **R1 — Locate the window/bucketing boundary inside the backend; split the fat SPI into capability
  mix-ins.** *(Architect B1, "one decision".)* `velocity-core` resolves fan-out to **backend-neutral
  intents** `(feature, subject, aggregation, member|value)`; the **backend** owns bucket keying,
  sliding-vs-tumbling semantics, eviction, and its own key schema. `VelocityStore` is split into
  capability interfaces — `CountStore`, `SumStore`, `DistinctStore`, `SlidingSupport`,
  `TumblingSupport`, `SeedSupport` — and a backend implements **only what it declares** in
  `BackendCapabilities`. Amends §6.1, NFR-17.
- **R5 — HLL is valid on tumbling windows only; forbid HLL-distinct on sliding.** *(Architect B3.)*
  A sketch cannot evict aging members, so sliding-window distinct MUST be exact (ZSET member→last-seen
  + range-evict + count), bounded by a cardinality cap. Add capability `distinctHllSliding=false`;
  **FR-29 MUST reject** HLL-distinct on a sliding window. HLL union across tumbling buckets (equal
  precision `p`) is lossless and remains the approximate path. Amends FR-11, §8.
- **R6 — Distinct exact→HLL threshold is a backend-clampable capability with a defined per-bucket
  merge rule.** *(Architect B4, resolves OQ-B.)* Each backend declares a max exact-distinct
  cardinality (e.g. **DynamoDB's item-size ceiling forces the cap**); the per-feature threshold (P4)
  is clamped to it. Exact→HLL is **stateful per `(subject,bucket)`**: once a bucket sheds members it
  is permanently approximate. **Merge rule:** a multi-bucket window is `approximate` if *any*
  constituent bucket is HLL; exact buckets are HLL-folded at read time. Pick a documented default
  threshold + HLL error target. Amends FR-11, FR-14.
- **R7 — Mandatory SPI conformance TCK.** *(Architect "one decision", NFR-13.)* Every
  `velocity-backend-*` MUST pass a shared `*Scenarios` contract suite in `velocity-testkit` (the
  `pk-auth` testkit pattern) that asserts its **declared** capabilities — including **negative tests**:
  HLL-on-sliding is rejected, read-your-write on a `besteffort` backend is flagged (not silently
  wrong), unsupported windows fast-reject. New NFR-18. This is what keeps the pluggable abstraction
  honest.
- **R3b — Resolve the `seed()` contract before publishing it (OQ-F).** *(Architect non-blocking #1.)*
  `seed()` is a phase-1 *published* SPI method; define its schema (**per-window bucket values**, not
  just a current total) now, or omit `seed()` from the v1 SPI and add it later additively. No
  published method may ship with a "TBD" contract (NFR-17).

### Tier 2 — must resolve BEFORE any inline / hot-path GA

- **R2 — Hot-path failure contract (deadline + bounded, distinguishable failure).** *(Security a,
  Rules Q, Architect B2 — the single most-cited gap.)* `record()`/`query()` MUST accept/enforce a
  **caller deadline** and **fail fast** within a bounded time, returning a **distinguishable**
  `UNAVAILABLE` / `DEADLINE_EXCEEDED` result — never a silent `0`/"no data" — so the *caller*
  deterministically chooses fail-open vs fail-closed. Backends MUST document timeouts and MUST NOT
  block unbounded. New NFR-19. Also: **read-your-write is a declared capability**
  (`atomic|snapshot|besteffort`), SHALL for exact backends, best-effort + `approximate`-flagged
  otherwise; reconciles D4, FR-2, NFR-7 (which previously overclaimed a universal guarantee).
  **v0.2 re-review update:** the *shape* of the result (the `Failure{UNAVAILABLE|DEADLINE_EXCEEDED|…}`
  variant) is **frozen in phase 1** via **[ADR 0009](adr/0009-hot-path-result-dto.md)** — only the
  deadline/bounded-failure *enforcement* stays Tier-2. Freezing the shape late would re-cut a
  published DTO.
- **R2b — `velocity-spi` is serialization-neutral.** *(Architect re-review, user decision.)* SPI DTOs
  are plain records with **no Jackson binding**; Jackson 3 is required in `velocity-core`/the wire
  layer, which owns JSON. Keeps the engine's serialization stack off a third-party backend author's
  classpath and out of the frozen surface (NFR-17). Amends ADR 0002; the skeleton's `jackson` on the
  `velocity-spi` `api` surface was removed.
- **R3 — Per-feature apply status + single-backend decision domain.** *(Rules A, Architect B6.)* Since
  one `record()` can fan out across features on *different* backends (FR-16 binds a feature to one
  backend), `record()` MUST return a **per-feature apply status** (`applied|failed|skipped`) and the
  engine MUST document whether fan-out is all-or-nothing or partial (and identify every feature that
  did not apply). A caller MUST be able to **constrain all features a single decision reads to one
  backend**, so they share one consistency + latency domain. New FR-34, FR-37, NFR-20. Note DynamoDB
  `TransactWriteItems` caps atomic fan-out at 100 items — declare `maxAtomicFanOut`. **v0.2 re-review
  update:** the per-feature apply-status *field* is **frozen in phase 1** on the result DTO
  ([ADR 0009](adr/0009-hot-path-result-dto.md)); gated in acceptance #4.
- **R4 — Namespace-scoped authorization (not just authentication).** *(Security d.)* Each API key MUST
  be **bound to an explicit allowed-namespace set**; a request whose path namespace is outside the
  key's scope MUST be **denied by default**. Authz is in scope for v1 (today any caller can pass any
  `namespace` and read/poison another tenant's counts). New NFR-21; amends NFR-12.
- **R11 — Keyed hashing of DISTINCT dimension values at rest (resolves OQ-E).** *(Security c, Business
  4, Rules, Architect B4.)* Exact distinct persists raw dimension values (IPs, tokens, device IDs).
  For DISTINCT dimensions the engine MUST support **keyed hashing/tokenization at rest** (per-namespace
  salt), so exact-distinct sets never store raw PII; HLL of hashed values is unaffected. New FR-38;
  amends NFR-12 (which only covered logging). **v0.2 re-review update (key custody):** the salt/key
  MUST live in a **separate trust domain (KMS/secret store), not co-resident** with the distinct sets —
  otherwise a low-entropy value like an IPv4 (2³² space) is brute-forceable offline after a single DB
  dump, defeating the "no raw PII" guarantee. High-entropy dimensions (tokens) are fine either way.
- **R15 — Idempotency as the inline default.** *(Security, Rules, Architect B6.)* At-least-once
  (NFR-8) makes COUNT provably inexact under retries. v1 reference backends (JDBI, Redis) MUST declare
  idempotency support; an idempotency key is the recommended inline posture; §8 "Exact" means "exact
  given idempotency/exactly-once delivery." Amends NFR-8, FR-5, §8.
- **R18 — Self-overload protection + per-namespace fairness.** *(Security b.)* Even though configurable
  quotas (P7) are deferred, the service MUST support **bounded concurrency / load-shedding** (shed with
  a typed `OVERLOADED` response, never queue unbounded) and **per-namespace fairness** (fair-share or
  per-namespace concurrency caps) in v1, so one tenant's flood cannot degrade every other tenant's
  inline latency. New NFR-22.

### Tier 3 — expectation, correctness-of-semantics, and roadmap

- **R8 — v1 ships a true-sliding backend (Redis in phase 2).** *(Security, Rules C — user decision.)*
  Postgres-tumbling alone permits the classic ~2× boundary burst (FR-14) and is a poor inline profile;
  Redis (sliding, exact, low-latency) is pulled into v1 so the hot-path shape §1 promises actually
  ships. Amends P10, §6.2, §8, §11. (For any tumbling window, its worst-case boundary error MUST still
  be documented = bucket granularity.)
- **R9 — Clock authority for sliding windows = backend clock.** *(Security, Architect non-blocking.)*
  Stateless pods (NFR-5) with skewed clocks would disagree on sliding `[now-duration, now]` edges; the
  **backend clock is authoritative**. Amends FR-3/D7.
- **R10 — Hot-reload is per-pod atomic, cluster-eventually-consistent; define version-skew behavior.**
  *(Architect B5.)* Under DR-3 (no inter-pod coordination, one shared backend), during a rollout pod A
  runs vN while pod B runs vN+1. FR-17 atomicity is **per-pod**, not per-cluster. The definition set
  MUST be **versioned + stamped**; readers MUST tolerate unknown features; convergence staleness MUST
  be bounded + documented. Also: **a hot-added feature accumulates from activation — no retroactive
  window** — and a feature whose window exceeds currently-retained data returns **partial + flagged**.
  Amends FR-17; new FR-42.
- **R12 — Feature values carry their definition version/hash.** *(Rules D.)* Hot-reload (P6) can change
  a feature's window/threshold under a running rule; every feature value MUST carry the
  **definition-version/hash** it was computed under, so a caller can detect drift. New FR-40.
  **v0.2 re-review update:** the `definitionVersionHash` *field* is **frozen in phase 1** on the result
  DTO ([ADR 0009](adr/0009-hot-path-result-dto.md)), nullable until the hot-reload versioning behavior
  lands; gated in acceptance #9.
- **R13 — Feature-discovery read API.** *(Rules D.)* Add `GET /v1/{namespace}/features` (list + by
  version) so a rules layer can discover, pin, and diff feature definitions (today only `/capabilities`
  exists). New FR-41; amends AR-2.
- **R14 — TTL is cost-cleanup, not the window boundary.** *(Architect non-blocking.)* Backend TTL lag
  (DynamoDB up to ~48h) means expired buckets linger; **window correctness MUST come from query-time
  bucket filtering**, not from TTL timing. Amends FR-22.
- **R16 — Bound metric label cardinality + expose/cap effective fan-out.** *(Security, Architect.)*
  Bound the `namespace × feature × exact/approx × backend` label product; expose and allow capping
  **effective fan-out per request** (a single `record()`'s tail latency scales with someone's feature
  config). Amends NFR-11; new FR-45.
- **R17 — Pin wire money representation (resolves OQ-D, blocks client-gen).** *(Rules, Architect.)*
  Choose integer-cents **or** decimal-string on the OpenAPI surface and document it before FR-27
  client generation, to avoid float coercion in generated clients.

### New requirement IDs introduced by §15

`FR-34` per-feature apply status · `FR-37` bind-decision-to-one-backend read constraint ·
`FR-38` keyed-hash DISTINCT values at rest · `FR-40` feature-value definition-version stamp ·
`FR-41` `GET /features` discovery · `FR-42` hot-add-from-activation / no-retroactive-window ·
`FR-45` effective-fan-out cap · `NFR-18` conformance TCK · `NFR-19` hot-path deadline & bounded
failure · `NFR-20` single-backend decision domain · `NFR-21` namespace-scoped authz ·
`NFR-22` self-protection & per-namespace fairness.

**Frozen in phase 1 by [ADR 0009](adr/0009-hot-path-result-dto.md)** (v0.2 re-review): the hot-path
result is a value-or-failure sum type carrying — per feature — the read-your-write level, `exact|
approximate`, the `definitionVersionHash` (FR-40), an apply status (FR-34), and a distinguishable
`FailureCode` (NFR-19). The *shape* is frozen now (only the enforcement behavior of R2/R10/R18 stays
Tier-2/3), because this DTO rides on every `apply()`/`query()` call and could not gain those fields
additively without re-cutting a published contract.

---

## 16. Governance & Enterprise Readiness

Raised primarily by the business-leader review: the primitive is credible, but adopting it on a
fraud/abuse-critical path needs more than code. None of the below changes the engine's design; they
are project/operational commitments to document.

- **GR-1 Positioning honesty.** README/§1 MUST state plainly that this is a **counting substrate, not
  a decisioning product** (D1): thresholds, rules, ALLOW/REVIEW/DENY, and actions are the adopter's to
  build or buy. Ship a thin **reference decisioning example** in `examples/` and a "what you still have
  to build" one-pager so no stakeholder mistakes §1's fraud/abuse language for a turnkey solution.
- **GR-2 Governance & support model.** Publish `GOVERNANCE.md` (maintainers, decision process,
  contribution cadence) and a support statement ("who do you call") so adopters can size the
  bus-factor / self-support risk. BSD (permissive) is chosen; license ≠ maintenance.
- **GR-3 Security disclosure.** Write the `SECURITY.md` disclosure process (already listed in BR-5 but
  unwritten) and a coordinated-disclosure contact.
- **GR-4 Supply-chain / provenance.** Dependency + vulnerability scanning in CI, an **SBOM**, and build
  provenance for published artifacts (beyond signing for Central, NFR-16).
- **GR-5 Admin audit log.** A durable, queryable **audit log of admin actions** — purge (FR-23), seed
  (FR-32), and config/feature-definition changes (FR-17) — distinct from Micrometer metrics. New
  requirement; in v1 acceptance (§11 #14).
- **GR-6 Data export / migration & exit story.** Feature definitions are portable YAML (low config
  lock-in, good), but counter **data** lock-in lives at the storage layer. Provide a documented path to
  **export counter data and migrate between backends**, and treat each backend's key schema as a
  versioned surface (NFR-17). Migration covers COUNT, SUM, and **exact** DISTINCT; **HLL-sketch
  distinct does not migrate across backends** (opaque, same-implementation-only — ADR 0006/0008),
  a v1 scope limit.
- **GR-7 Capacity & cost model.** Publish **cost-per-million-events** guidance per backend so TCO is
  quantifiable. The project itself commits to **two** production-grade backends in v1 (Postgres +
  Redis, per §11 #2 / GR-8), but still recommends an **adopter** pilot on **one** backend rather than
  running all of them.
- **GR-8 Production-readiness proof.** Beyond functional acceptance (§11), publish **load-tested
  throughput, tail-latency, and DR/failure behavior** for at least the v1 reference backends
  (Postgres, Redis), tied to the committed SLOs (OQ-A).
- **GR-9 Compliance surface.** Document the **PII data-flow inventory**, per-namespace retention
  (`maxRetention`, FR-22a), and a **verifiable** erasure guarantee for FR-23 purge (proves erasure, not
  best-effort) — prerequisites for an EU/regulated launch. Builds on R11.
