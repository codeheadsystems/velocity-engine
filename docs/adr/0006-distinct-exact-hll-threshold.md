# 6. DISTINCT exact→HLL threshold is a backend-clampable capability with a per-bucket merge rule

Date: 2026-07-18

## Status

Accepted.

## Context

P4 says `DISTINCT` is "exact where cheap; HyperLogLog above a per-feature cardinality threshold,"
and FR-11 makes the threshold configurable per feature definition **but clampable by the backend**.
OQ-B was a **🔴 phase-2 blocker**: the actual default threshold and HLL precision/error target were
never chosen, and they block JDBI/Redis distinct (acceptance §11 #1/#2). §15 R6 ("Architect B4")
resolves OQ-B and pins down three things the SPI freeze needs: (a) that the threshold is a
backend-clampable capability, (b) the per-`(subject,bucket)` stateful switch semantics, and (c) the
read-time merge rule for a multi-bucket window with mixed exact/HLL buckets. ADR 0005 already
established that this whole mechanism applies to **tumbling** distinct only (sliding is exact-capped).

The concrete numbers matter for the freeze because `BackendCapabilities` must carry the default
threshold and the clamp, and the DTOs must carry the `exact|approximate` flag those numbers drive
(FR-7).

## Decision

### 1. The threshold is a per-feature knob, clamped by a backend capability

A feature definition may set its exact→HLL cardinality threshold (P4/FR-11). The backend declares a
**maximum exact-distinct cardinality** in `BackendCapabilities`; the engine **clamps** the
per-feature threshold down to that maximum at validation time (FR-29). The per-feature value is a
*ceiling request*; the backend's declared max is the hard cap.

The driving example is DynamoDB: a single item is capped at **400 KB**, so an exact-distinct set
materialized in one item cannot grow without bound — the item-size ceiling *forces* a clamp
(§15 R6). A backend whose exact set lives in an unbounded table (Postgres rows) can declare a higher
max; a backend whose exact set lives in one bounded object/item declares a lower one. The clamp is
therefore intrinsic to the store, not an arbitrary policy — the same reasoning as `maxRetention`
(FR-22a).

### 2. Chosen defaults (resolves OQ-B)

- **Default exact→HLL threshold: 10,000 distinct members per `(subject, bucket)`.**
- **HLL precision `p = 14`** → `m = 2^14 = 16,384` registers, 6 bits each → **12 KiB** dense sketch,
  standard error `≈ 1.04 / √m = 1.04 / 128 ≈ 0.81%`.

Rationale:

- **10,000 exact members is genuinely cheap; beyond it, exact cost grows unbounded while HLL is
  flat.** At 10k members of fixed-width keyed-hashed values (FR-38 stores 16-byte truncated HMACs,
  not raw IPs/tokens — so per-member size is predictable), an exact set is on the order of a few
  hundred KB. Above that, exact storage and read cost climb linearly with cardinality, whereas the
  HLL sketch is a fixed 12 KiB regardless of how many distinct members arrive. 10k is the point where
  "just keep the set" stops being obviously cheaper than "switch to the sketch."
- **`p = 14` is the industry-standard operating point, and it is exactly what Redis's native HLL
  uses** (12 KiB, ~0.81% error). Aligning the default with Redis's well-known choice means the number
  is battle-tested, keeps sub-1% error (tight enough for velocity/fraud cardinality signals, which
  are compared against thresholds, not billed to the cent), and lets any backend that leans on a
  native HLL implementation use its stock semantics without re-tuning. The 12 KiB sketch also sits far
  under DynamoDB's 400 KB item ceiling, so an HLL bucket always fits where an exact set might not.

Both numbers are **defaults on `BackendCapabilities`**, not hardcoded in core (P18): a backend
documents its own values at dev time (§8 note, OQ-A) and may raise/lower them, but the TCK
(ADR 0004) verifies whatever it declares.

### 3. The exact→HLL switch is stateful per `(subject, bucket)`

The switch is **stateful per `(subject, bucket)`**, not per feature and not global: a given bucket
starts exact and, once it sheds members to a sketch (crossing the clamped threshold), is
**permanently approximate** for its lifetime. You cannot go back to exact from a sketch — the members
are gone (the same no-inverse property as ADR 0005). Different buckets of the same feature, and the
same bucket for different subjects, switch independently based on their own observed cardinality.

### 4. Per-bucket merge rule for a multi-bucket window

A multi-bucket tumbling window (FR-14) is evaluated at read time by merging its constituent buckets:

- **A window is `approximate` if *any* constituent bucket is HLL.** Exactness does not survive a
  single approximate bucket — the result is flagged `approximate` (FR-7).
- **Exact buckets are HLL-folded at read time.** When the window has at least one HLL bucket, each
  exact bucket's member set is folded into an HLL sketch of the **same precision `p`**, and all
  buckets are unioned as sketches (lossless union at equal `p`, per ADR 0005). When *every*
  constituent bucket is exact, the window stays exact (set union of the buckets) and is flagged
  `exact`.

This makes the `exact|approximate` flag correct at the window granularity the caller actually reads,
while letting each bucket independently be as exact as its cardinality allowed.

## Consequences

### Positive

- **OQ-B is closed with defensible numbers.** JDBI and Redis distinct can ship (§11 #1/#2) against a
  documented default (10k / `p = 14` / ~0.81%) instead of a TBD.
- **Memory/cost is bounded per bucket.** Above the threshold a bucket costs a flat 12 KiB regardless
  of cardinality, so a high-cardinality subject cannot blow up storage — and the 12 KiB sketch fits
  inside DynamoDB's item ceiling where an exact set would not.
- **The flag is honest at read granularity.** "Approximate if any bucket is HLL" plus read-time
  folding means a caller is never told `exact` for a window that mixed in a sketch, and never
  needlessly told `approximate` for an all-exact window.
- **Backends stay truthful about their own limits.** The clamp is a declared capability, so DynamoDB's
  item-size reality is surfaced (a lower max exact cardinality) rather than hidden behind a runtime
  surprise.

### Negative

- **The exact→approximate transition is one-way and per-bucket.** A bucket that briefly spiked past
  10k distinct members stays approximate for its life even if later reads would have been cheap to
  keep exact. This is inherent to sketches (no inverse) and is the correct trade against retaining an
  unbounded exact set.
- **Read-time folding costs CPU on mixed windows.** Folding exact buckets into `p = 14` sketches at
  query time is work proportional to the exact members involved. Bounded (the exact buckets are, by
  definition, under the threshold) but non-zero.
- **~0.81% is a real error floor for approximate distinct.** For a caller who needs exact
  high-cardinality distinct, the answer is a tumbling exact configuration under the clamp or a
  different backend — not a tighter HLL. `p = 14` is a deliberate default, not a maximum; a backend
  may declare a larger `p` (more registers, more memory, less error) if its module documents it.

### Neutral

- The single-`p`-per-backend rule is what makes ADR 0005's "equal precision union is lossless" hold
  by construction; changing `p` is a backend-level decision, not a per-feature one.

## Concrete values chosen (summary)

| Parameter | Value | Why |
|---|---|---|
| Default exact→HLL threshold | **10,000 distinct members / `(subject, bucket)`** | Exact set still cheap (~hundreds of KB of fixed-width hashed members); above it exact cost grows unbounded while HLL is flat. |
| HLL precision `p` | **14** (`m = 16,384` registers) | Industry-standard; matches Redis's native HLL operating point. |
| HLL sketch size | **12 KiB** dense | Fits far under DynamoDB's 400 KB item ceiling. |
| HLL standard error | **≈ 0.81%** (`1.04/√m`) | Sub-1%, tight enough for threshold-compared velocity signals. |

All four are **`BackendCapabilities` defaults** (P18), TCK-verified per backend (ADR 0004), not
core constants.

## References

- Requirements §15 R6 (resolves OQ-B), OQ-B (blocker), P4/FR-11 (exact→HLL, clampable), FR-14
  (tumbling merge), FR-7 (exact/approximate flag), FR-22a (capability-declared limits precedent),
  FR-38 (keyed-hash members → fixed-width), §8 (capability matrix), P18 (numbers are backend-owned).
- Related: ADR 0005 (tumbling-only HLL; equal-`p` lossless union), ADR 0003 (`DistinctStore`,
  `BackendCapabilities`), ADR 0004 (TCK verifies declared threshold/clamp).
