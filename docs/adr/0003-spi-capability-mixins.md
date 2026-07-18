# 3. SPI is capability mix-ins; the window/bucketing boundary lives inside the backend

Date: 2026-07-18

## Status

Accepted.

## Context

This is the engineering architect's **"one decision"** (§15 R1, "Architect B1") — the single most
structurally consequential choice for the `velocity-spi` freeze, and the reason the SPI batch exists.

The v0.1 sketch (§6, pre-review) implied a single `VelocityStore` interface that every backend
implements. Two problems surfaced in the review:

1. **The backends are genuinely not the same shape.** Redis does true sliding windows, exact only,
   no HLL (D3, §8). Postgres/JDBI does tumbling, exact, exact→HLL over a threshold. S3 is tumbling,
   approximate, HLL. DynamoDB is tumbling (+ short sliding), exact→HLL, capped by item size. A single
   fat `VelocityStore` forces every backend to either implement methods it cannot honor (and throw at
   runtime) or pretend — exactly the "engine pretends a backend can do something it cannot" failure
   §1 forbids. The abstraction would leak on day one.

2. **Where does window/bucketing logic live?** If `velocity-core` computes bucket keys and sliding
   vs. tumbling semantics, then every backend is forced into the core's bucketing model, and a
   backend's native strengths (Redis ZSET range-evict, DynamoDB atomic `ADD` on a per-bucket item,
   S3 tumbling objects) become impossible to express. The window boundary was in the wrong place.

NFR-17 also forbids a single storage-key schema — Redis ZSET keys, Dynamo items, and S3 paths are
not one schema — which is only coherent if the backend, not the core, owns keying.

## Decision

Split the data-plane SPI into **capability mix-ins**, and **locate the window/bucketing boundary
inside the backend**. A backend implements only the interfaces for the capabilities it declares in
`BackendCapabilities`; it never implements a method for a capability it does not support.

### 1. The core resolves fan-out to backend-neutral intents

`velocity-core` does *not* compute buckets. It resolves an incoming `record()` against the matching
feature definitions (its fan-out, D5/FR-18) down to a list of **backend-neutral intents**:

```
Intent = (feature, subject, aggregation, member | value)
```

- `aggregation` ∈ `{COUNT, SUM, DISTINCT(dimension)}`.
- for `COUNT` there is neither member nor value; for `SUM` a `value` (BigDecimal cents, P3);
  for `DISTINCT` a `member` (the — keyed-hashed, per FR-38 — dimension value).

The intent names the *feature* (which carries its window set and type) but says nothing about bucket
keys, TTLs, sliding ranges, or eviction. `query()` symmetrically passes **tuples**
`(namespace, subject, aggregation, window)`.

### 2. The backend owns keying, windowing, and eviction

The backend owns:

- bucket keying and **its own key schema** (its compatibility surface per NFR-17);
- **sliding vs. tumbling semantics** — how `[now - duration, now]` is realized (Redis ZSET
  range-evict) vs. how tumbling buckets are aligned and merged (FR-14);
- **eviction / TTL** as cost-cleanup, with window correctness coming from query-time bucket
  filtering, not TTL timing (FR-22, §15 R14);
- the **backend clock** as authority for sliding-window edges (FR-3, §15 R9), not the stateless
  pod's clock.

### 3. The mix-ins

`velocity-spi` defines these interfaces. A backend implements the subset it declares:

**Aggregation capabilities** (what can be counted):

| Mix-in | Contract |
|---|---|
| `CountStore` | `COUNT` — apply/query count intents. |
| `SumStore` | `SUM` — apply/query `BigDecimal`-cents value intents; no binary float, no silent overflow (FR-10). |
| `DistinctStore` | `DISTINCT(dimension)` — apply/query cardinality; carries the exact↔HLL rule (ADR 0005, 0006). |

**Window capabilities** (how time is modeled):

| Mix-in | Contract |
|---|---|
| `SlidingSupport` | true sliding `[now - duration, now]`; distinct here is **exact-only, capped** (ADR 0005). |
| `TumblingSupport` | aligned fixed buckets; multi-bucket window = merge of buckets, edge-approximate at the current boundary (FR-14); the **only** window type on which HLL-distinct is valid (ADR 0005). |

**Operational capability:**

| Mix-in | Contract |
|---|---|
| `SeedSupport` | optional onboarding `seed(...)` of per-window bucket values (FR-32; schema fixed in ADR 0008). A backend that cannot represent seeded buckets simply does not implement it. |

Common data-plane methods, carried on the aggregation mix-ins:

- `apply(intents) -> featureValues` — the record path; honors a caller deadline and returns a
  **read-your-write level per result** (ADR 0007) and the `exact|approximate` flag (FR-7).
- `query(tuples) -> featureValues` — the read path, same result shape.
- `purge(namespace, subject?)` — admin erasure (FR-23).

### 4. `BackendCapabilities` is the single source of truth for what a backend can do

The descriptor the engine reads at runtime (never hardcoded — P18) to validate feature definitions
and requests (FR-12/13/29) and to shape defaults:

- supported windows: each with `duration`, `type` (`SLIDING`|`TUMBLING`), `exactness`, granularity;
- supported aggregations (which of `COUNT`/`SUM`/`DISTINCT`);
- `distinctHllSliding` — always `false` (ADR 0005), present as an explicit capability so the TCK can
  assert the negative;
- distinct exact-cardinality clamp + default threshold (ADR 0006);
- `maxRetention` (FR-22a) — the retention ceiling that is *why* window ranges differ per backend;
- read-your-write level `atomic | snapshot | besteffort` (ADR 0007);
- idempotency support (FR-5, §15 R15);
- seed support (whether `SeedSupport` is implemented);
- `maxAtomicFanOut` (e.g. DynamoDB `TransactWriteItems`'s 100-item cap — §15 R3).

**The interface set a backend implements MUST agree with what `BackendCapabilities` declares.** A
backend that declares `SUM` implements `SumStore`; one that does not, does not. The conformance TCK
(ADR 0004) asserts exactly this agreement, including the negatives.

## Consequences

### Positive

- **The abstraction is honest by construction.** A backend cannot be handed a call it cannot honor,
  because the call only exists on a mix-in it chose to implement. There is no "throw
  `UnsupportedOperationException` at runtime" path for a declared-unsupported capability — the compiler
  and the capability descriptor keep them out of reach. This is the mechanism §1's "never pretends"
  promise needs.
- **Backends keep their native strengths.** Redis expresses true sliding with ZSET range-evict;
  DynamoDB uses atomic `ADD` on a per-bucket item; S3 writes tumbling objects — none is forced into a
  core-imposed bucketing model, because the core never computes buckets. Intents are
  backend-neutral; keying is the backend's.
- **The key-schema plurality of NFR-17 becomes coherent.** With keying owned by the backend, "no
  single storage-key schema" is the natural state, and each backend's key schema is cleanly *its*
  versioned surface.
- **Additive evolution is a new mix-in or a new capability field.** Adding `min/max/avg` later (a
  v1 non-goal, §12) is a new aggregation mix-in defaulting to unsupported — additive per NFR-17, no
  re-cut of the existing contract.

### Negative

- **More types than one fat interface.** Six mix-ins plus the descriptor is more surface than a
  single `VelocityStore`. We accept it: the alternative is runtime failures for unsupported
  capabilities, which the review named as the core risk.
- **Capability coherence must be enforced, not assumed.** Nothing in the type system stops a backend
  from declaring `SUM` in `BackendCapabilities` while not implementing `SumStore`, or vice-versa.
  This is precisely why the conformance TCK (ADR 0004) is *mandatory* — it is the check that keeps
  declaration and implementation in agreement.
- **The core gives up bucket-level knowledge.** `velocity-core` cannot reason about individual
  buckets (it only sees features and windows); anything that genuinely needs per-bucket logic must be
  expressed through the SPI, not assumed in the core. This is the intended trade — bucket logic
  belongs to the store.

### Neutral

- Window/aggregation *validation* still lives in the core (FR-29: validate a feature definition
  against `BackendCapabilities` before it is accepted). The core owns "is this feature *allowed* on
  this backend"; the backend owns "how is it *stored*." The line is the capability descriptor.

## References

- Requirements §15 R1 (the "one decision"), §6.1 (SPI shape), NFR-17 (compatibility surfaces,
  key-schema plurality), FR-11/14 (windows and distinct), FR-12/13/29 (capability validation),
  D3/D5 (window model, fan-out), P18 (numbers are backend-owned).
- Related: ADR 0004 (conformance TCK enforces declaration↔implementation agreement),
  ADR 0005 (HLL-on-tumbling-only), ADR 0006 (distinct threshold clamp), ADR 0007 (read-your-write
  level field), ADR 0008 (`SeedSupport` schema).
