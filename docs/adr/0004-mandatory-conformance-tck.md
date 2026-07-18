# 4. Mandatory SPI conformance TCK in `velocity-testkit`

Date: 2026-07-18

## Status

Accepted.

## Context

The whole value proposition of `velocity-spi` is that a caller can swap backends and the engine
"never pretends a backend can do something it cannot" (§1). ADR 0003 makes each backend declare its
capabilities in `BackendCapabilities` and implement only the matching mix-ins. But a *declaration* is
just a promise. Nothing in the type system stops a backend from:

- declaring `readYourWrite = atomic` while actually being eventually consistent;
- declaring `SUM` in the descriptor but not honoring `BigDecimal`-cents precision (FR-10);
- accepting an HLL-distinct feature on a sliding window even though ADR 0005 forbids it;
- returning a silent `0` when the backend is partitioned instead of a distinguishable failure
  (the single most-cited review gap, §15 R2);
- silently producing wrong read-your-write results on a `besteffort` backend instead of *flagging*
  them.

The engineering architect made a shared conformance suite part of the "one decision" (§15 R7,
building on NFR-13). This is the mechanism that keeps the pluggable abstraction honest — without it,
"pluggable" degrades to "pluggable if each backend author happened to get the contract right."

The sibling `pk-auth` project already proves the pattern: `pk-auth-testkit` ships `*Scenarios`
classes (`ChallengeStoreScenarios`, `AccessTokenStoreScenarios`, `RefreshTokenScenarios`, …) — a
`*Scenarios` object is constructed with a store instance and driven from each backend's own test
class against a fresh store, so the contract (e.g. `takeOnce` consumes exactly once, even under
concurrency) is verified identically for the in-memory, JDBI, and DynamoDB backends.

## Decision

Ship a **mandatory conformance TCK** in `velocity-testkit` (new NFR-18). Every `velocity-backend-*`
MUST pass a shared `*Scenarios` contract suite for its **declared** capabilities before it is
considered a conformant backend. This is a v1 acceptance criterion (§11 #3) and gates both v1
reference backends (JDBI, Redis).

### Shape (mirrors the `pk-auth` testkit pattern)

`velocity-testkit` provides `*Scenarios` classes constructed with the backend under test and its
declared `BackendCapabilities`:

- `CountStoreScenarios`, `SumStoreScenarios`, `DistinctStoreScenarios`,
- `SlidingSupportScenarios`, `TumblingSupportScenarios`, `SeedSupportScenarios`,
- `CapabilityConformanceScenarios` (the descriptor-vs-implementation agreement suite).

Each backend module drives them from its own test class, passing a fresh store plus its declared
capabilities. The suite reads `BackendCapabilities` and runs the **positive** scenarios for every
capability the backend declares — and, critically, the **negative** scenarios for the ones it does
not.

### The suite asserts capability↔implementation agreement, including negatives

Positive (for declared capabilities):

- `COUNT`/`SUM`/`DISTINCT` produce correct aggregates, atomic under concurrent apply for backends
  that declare `atomic` (NFR-6);
- `SUM` preserves `BigDecimal`-cents precision and does not silently overflow (FR-10);
- tumbling multi-bucket windows merge correctly; sliding windows cover `[now - duration, now]`
  against the **backend clock** (FR-3, §15 R9);
- window correctness holds independent of TTL lag — query-time bucket filtering, not TTL timing
  (§11 #12, §15 R14);
- a declared read-your-write level is actually honored (an `atomic` backend reflects the caller's
  own write to every bucket it touched — NFR-7).

Negative (the tests that keep the abstraction honest, §15 R7):

- **HLL-distinct on a sliding window is rejected**, not silently accepted (FR-11, ADR 0005);
- **read-your-write on a `besteffort` backend is flagged**, never returned as if exact (ADR 0007);
- an **unsupported window fast-rejects** with a clear error rather than silently degrading (FR-13);
- a feature whose largest window exceeds `maxRetention` is rejected (FR-22a, FR-29);
- with the backend stalled/partitioned, apply/query **fail fast within the caller deadline** and
  return a distinguishable `UNAVAILABLE`/`DEADLINE_EXCEEDED` — never a silent `0` (§15 R2, §11 #5);
- a backend that declares a capability in `BackendCapabilities` implements the matching mix-in, and
  one that does not, does not (the ADR 0003 coherence check).

## Consequences

### Positive

- **Declaration becomes verifiable.** `BackendCapabilities` stops being an unchecked promise: the
  TCK reads it and proves the backend behaves as declared. This is what makes "swap the backend"
  safe.
- **Negative tests are first-class.** Most conformance suites only check that supported things work.
  Here the *refusals* are contract — HLL-on-sliding rejected, best-effort flagged, unsupported window
  fast-rejected — because a substrate on a fraud/abuse path that silently returns a wrong or `0`
  count is worse than one that errors (§15 R2, the fraud/rules and gateway reviewers).
- **Third-party backends have a definition of done.** An external backend author runs the TCK for
  their declared capabilities; passing it *is* conformance. No prose spec to interpret.

### Negative

- **Writing a backend now includes passing the TCK.** More up-front work than "implement the
  interface and ship." That is the point — it is the cost of the pluggability guarantee.
- **The TCK must stay in lockstep with the SPI.** Every additive SPI change (NFR-17) needs
  corresponding scenarios, or a new capability ships unverified. The TCK is part of the SPI's
  release surface, not an afterthought.
- **Concurrency/fault scenarios need real infrastructure.** Atomicity and partition scenarios run
  against Testcontainers (Redis, Postgres), DynamoDB Local, and LocalStack/MinIO (NFR-13), which is
  slower and heavier than pure unit tests. Accepted: these are exactly the properties that cannot be
  proven single-threaded or in-memory.

### Neutral

- The in-memory `velocity-testkit` backend is itself a TCK subject — it must pass the scenarios for
  the capabilities it claims — which doubles as the suite's own smoke test.

## References

- Requirements §15 R7 (mandatory conformance TCK), NFR-13 (testkit + integration tests),
  NFR-18 (new), §11 #3/#5/#12 (acceptance), FR-11/13 (distinct, fast-reject), FR-22a/29 (retention
  validation), §15 R2 (bounded distinguishable failure), §15 R14 (TTL vs. window correctness).
- pk-auth `pk-auth-testkit` `*Scenarios` pattern (e.g. `ChallengeStoreScenarios`) — the shared
  contract suite driven per-backend against a fresh store.
- Related: ADR 0003 (capability mix-ins the TCK verifies), ADR 0005/0006/0007/0008.
