# 8. Seed/backfill contract is resolved before SPI freeze

Date: 2026-07-18

## Status

Accepted.

## Context

Adopting the engine or migrating from another system requires getting existing aggregate state *in*
(§4.8). FR-32 distinguishes two onboarding paths: **bulk event import** (replay events through the
normal `record()` fan-out, counted at import time under v1 server-clock semantics, D7) and, distinctly,
**seeding pre-computed historical aggregates** — where the target backend can represent it — as an
admin operation clearly flagged as onboarding-seeded rather than organically recorded.

`seed(...)` is listed in §6.1 as a **published SPI method** on the `SeedSupport` mix-in. OQ-F was a
**🔴 phase-1 (SPI-freeze) blocker**, and §15 R3b ("Architect non-blocking #1") is blunt about why:
*no published method may ship with a "TBD" contract* (NFR-17). Either the seed contract is fully
defined before the SPI is frozen, or `seed()` is omitted from the v1 SPI and added later additively.
The trap to avoid: seeding a *single current total* per feature. A total cannot be correctly
apportioned into a window's buckets — the engine would not know how much of it belongs to the last
5 minutes vs. the last hour, so every windowed query after a seed would be wrong. The contract must
carry enough structure to place seeded state into the right buckets.

## Decision

**Define the `seed()` contract now, as per-window bucket values, and keep it optional via the
`SeedSupport` mix-in.** `seed()` ships in the v1 SPI with a real contract, not a TBD.

### 1. Seed schema = per-window bucket values, not a single total

A seed operation supplies, for a `(namespace, subject, feature)`, the aggregate value **per bucket**
of the feature's window(s):

```
SeedRequest = (namespace, subject, feature, [ BucketValue... ])
BucketValue = (bucketStart, bucketEnd, aggregate)
   aggregate = count | sumCents (BigDecimal cents, P3)
             | distinct( exactMembers[] | hllSketch )
```

- The seed is expressed at **bucket granularity** so the engine can place it into exactly the buckets
  a windowed query will later merge (FR-14). A single current total is explicitly **not** a valid
  seed — it cannot be apportioned.
- For `DISTINCT`, a bucket is seeded either with its exact member set (subject to the exact clamp,
  ADR 0006 — members are keyed-hashed at rest, FR-38) or with a pre-computed HLL sketch of the
  agreed precision `p` (ADR 0006, tumbling only per ADR 0005). A backend rejects a seed whose
  distinct representation it cannot store (e.g. an HLL sketch on a sliding feature — ADR 0005).
- Seeded state is **flagged onboarding-seeded**, distinct from organically recorded state (FR-32), so
  it is auditable (§16 GR-5 admin audit log) and distinguishable in exports (GR-6).

### 2. `SeedSupport` is an optional mix-in a backend declares

Per ADR 0003, `seed()` lives on the `SeedSupport` capability mix-in. A backend implements it **only
if** it can represent seeded buckets, and declares `seedSupport` in `BackendCapabilities`. A backend
that cannot represent seeded buckets (FR-32's explicit escape hatch) simply does not implement
`SeedSupport` and declares it unsupported — the engine then rejects seed requests targeting that
backend with a precise error (FR-29 style), rather than silently dropping them.

### 3. Seed is an admin/operational surface, rate-isolated from the hot path

Consistent with FR-33, `seed()` is an admin operation, rate-isolated from the live `record()`/`query()`
path so onboarding a large dataset does not degrade inline latency, and recorded in the admin audit
log (GR-5, §11 #14).

### 4. Conformance

`SeedSupportScenarios` in the TCK (ADR 0004) verifies, for a backend that declares seed support, that
seeded per-bucket values are queryable through the *same* windowed read path as organically recorded
data (a seeded bucket and a recorded bucket merge identically), that seeded state is flagged, and
that an unrepresentable seed (e.g. sliding HLL, or a seed on a backend that does not declare
`SeedSupport`) is rejected.

## Consequences

### Positive

- **`seed()` ships frozen with a real contract**, clearing the OQ-F blocker: the SPI can be frozen in
  phase 1 without a TBD method (NFR-17).
- **Seeded aggregates are correct under windowed queries.** Per-bucket granularity means a seeded
  feature answers window queries the same way an organically recorded one does — the buckets merge
  identically (FR-14) — instead of the wrong-apportionment failure a single-total seed would cause.
- **Optionality is honest.** A backend that genuinely cannot represent seeded buckets declares so and
  rejects seeds cleanly, rather than the engine pretending seeding works everywhere (§1).
- **Migration story has a foundation.** Per-bucket seed is the ingest side of the export/migration
  path business adopters asked for (GR-6): export counter data as bucket values, seed it into another
  backend.

### Negative

- **The caller must produce per-bucket values.** Seeding is more work than handing over a total — the
  migrating system must bucketize its historical aggregates to the target feature's window grid. This
  is unavoidable: bucketization is the only representation that queries correctly, and the alternative
  (a total) is silently wrong.
- **Distinct seeding inherits all of distinct's constraints.** Exact member seeds are bounded by the
  exact clamp (ADR 0006); HLL seeds must match the backend's precision `p` and are tumbling-only
  (ADR 0005). A seed cannot smuggle in a distinct shape the backend could not have produced itself.
- **More SPI surface frozen now.** `SeedSupport`, the `SeedRequest`/`BucketValue` DTOs, and the
  "onboarding-seeded" flag are all part of the frozen contract. Accepted deliberately: the §15 R3b
  alternative was to *omit* `seed()` from v1 and add it additively later. We keep it because
  onboarding is a first-class v1 concern (§4.8) and its per-bucket shape is well understood now.

### Neutral

- Historical **replay** (client-supplied event time, out-of-order) remains a v1 non-goal (§12, D7).
  Seeding pre-computed bucket aggregates is a different operation and is *not* replay — it does not
  reintroduce client-supplied event time onto the `record()` path.

## References

- Requirements §15 R3b (resolve `seed()` before publishing it), OQ-F (blocker), FR-32 (seed
  pre-computed aggregates; backends that cannot must declare so), FR-33 (admin/rate-isolated),
  §4.8 (onboarding), FR-14 (bucket merge), D7/§12 (server-clock, replay is a non-goal), NFR-17
  (no TBD published method), GR-5/GR-6 (audit, migration).
- Related: ADR 0003 (`SeedSupport` optional mix-in, `BackendCapabilities.seedSupport`), ADR 0005
  (distinct seed constraints), ADR 0006 (exact clamp / HLL precision for seeded distinct),
  ADR 0004 (`SeedSupportScenarios` TCK).
