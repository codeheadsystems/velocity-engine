# 5. HyperLogLog is restricted to tumbling windows; sliding DISTINCT is exact-only

Date: 2026-07-18

## Status

Accepted.

## Context

`DISTINCT(dimension)` counts the cardinality of a dimension's values for a subject in a window
(FR-11). D2 makes distinct a first-class aggregation; P4 says it is "exact where cheap, HyperLogLog
above a per-feature cardinality threshold." D3 makes window *type* (sliding vs. tumbling) a
per-backend capability. The v0.1 draft did not constrain which window types HLL could serve.

The engineering architect (§15 R5, "Architect B3") flagged this as a Tier-1 correctness bug that
must be fixed before the SPI freeze, because the SPI otherwise implies a capability
(`sliding + HLL`) that is **mathematically unsound**.

### Why a sketch cannot do sliding

A sliding window covers `[now - duration, now]` (FR-14): as time advances, members that entered the
window more than `duration` ago must **leave** the cardinality. HyperLogLog is a lossy sketch of
register maxima — it supports insert and union, but it has **no eviction and no delete**: once a
value has bumped a register, you cannot remove its contribution without the original member set,
which the sketch deliberately does not keep. So a sliding HLL could only ever grow; it could never
shed an aging member. It would report monotonically increasing cardinality that never falls as the
window slides — a wrong answer, silently.

Exact structures do not have this problem: a Redis ZSET of `member → last-seen-timestamp` supports
`ZREMRANGEBYSCORE` to evict members older than `now - duration` and `ZCARD` to count what remains.
That is exact sliding distinct — bounded, because you must retain one entry per live distinct member.

### Why HLL *is* sound on tumbling

Tumbling windows are aligned, fixed buckets (FR-14); a multi-bucket window is the **merge** of its
buckets. Members never need to leave a *bucket* — a bucket is a closed time interval. And HLL's one
lossless operation is exactly **union**: merging two HLL sketches of equal precision `p` yields the
sketch of their combined set, with no additional error beyond each sketch's own. So a tumbling window
built from per-bucket HLL sketches is computed by unioning the constituent buckets' sketches at read
time — lossless union, no eviction ever required.

## Decision

**HLL-distinct is valid on tumbling windows only. Sliding-window DISTINCT is exact-only, bounded by
a cardinality cap.**

Concretely:

- `BackendCapabilities` carries `distinctHllSliding`, and it is **always `false`** — the field
  exists so the conformance TCK (ADR 0004) can assert the negative, and so the impossibility is a
  declared fact rather than an implicit one.
- **FR-29 MUST reject**, at feature-definition validation / import time, any `DISTINCT` feature that
  pairs HLL (or a threshold that would switch to HLL) with a `SLIDING` window. The rejection is a
  precise error, not a silent downgrade (FR-13).
- **Sliding DISTINCT is exact**, implemented as `member → last-seen` with range-evict of members
  older than `now - duration` and a count of survivors (the Redis ZSET pattern, §8). It is bounded
  by a **cardinality cap**: because every live distinct member costs one retained entry, a sliding
  distinct feature declares a maximum tracked cardinality; exceeding it is a declared, flagged
  condition (the backend documents its cap), not an unbounded memory blow-up.
- **Tumbling DISTINCT** follows the exact→HLL threshold rule of ADR 0006: exact per bucket while
  cheap, HLL per bucket above the threshold, and a multi-bucket window unions its buckets' sketches
  (equal precision `p`) at read time. Exact buckets are HLL-folded at read when any constituent
  bucket is HLL (ADR 0006's merge rule).

This is why §8's matrix lists Redis (sliding) as "Exact only (ZSET member→ts, capped); **no HLL on
sliding**" and S3 (tumbling) as HLL.

## Consequences

### Positive

- **No silently-wrong cardinalities.** The one configuration that would return a monotonically wrong
  answer — sliding HLL — is unrepresentable and rejected at definition time. The substrate cannot be
  configured into a lie.
- **Each window type uses the structure that fits it.** Tumbling gets HLL's fixed ~12 KiB-per-sketch
  cost and lossless union (ADR 0006); sliding gets exact ZSET range-evict. Neither is forced onto the
  other.
- **The negative is a declared capability, not tribal knowledge.** `distinctHllSliding = false` plus
  the TCK negative test means a new backend author cannot accidentally "support" sliding HLL.

### Negative

- **Sliding distinct does not scale to unbounded cardinality.** Exact sliding must retain one entry
  per live distinct member, so it is capped — a subject with millions of distinct sliding-window IP
  values is not a supported shape on a sliding backend. Callers who need very-high-cardinality
  distinct must use a tumbling window (where HLL applies) and accept tumbling's boundary semantics
  (FR-14). This is an intrinsic trade of sliding exactness, made explicit rather than hidden.
- **A feature's window type now constrains its distinct strategy.** An adopter cannot freely combine
  "sliding" and "approximate/HLL" — the two are mutually exclusive for distinct. This is surfaced as
  a validation error (FR-29), which is a better failure mode than a wrong count, but it is a
  constraint definition-authors must learn.

### Neutral

- HLL union across tumbling buckets requires **equal precision `p`** across those buckets; ADR 0006
  pins a single default `p` per backend so this holds by construction.

## References

- Requirements §15 R5 (HLL tumbling-only; forbid sliding HLL), FR-11 (distinct exact→HLL), FR-14
  (window semantics), FR-29/FR-13 (reject at validation, fast-reject), §8 (capability matrix),
  P4 (exact-where-cheap / HLL).
- Related: ADR 0006 (the exact→HLL threshold + per-bucket merge rule), ADR 0003
  (`SlidingSupport`/`TumblingSupport` mix-ins, `distinctHllSliding` capability), ADR 0004 (TCK
  negative test for HLL-on-sliding).
