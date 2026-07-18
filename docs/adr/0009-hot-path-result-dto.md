# 9. The hot-path result is a frozen value-or-failure sum type

Date: 2026-07-18

## Status

Accepted. Completes [ADR 0007](0007-read-your-write-declared-capability.md), which froze only the
read-your-write level onto the result and explicitly deferred the rest of the result's shape.

## Context

Every `apply()` (record) and `query()` call on the `velocity-spi` data-plane mix-ins
([ADR 0003](0003-spi-capability-mixins.md)) returns a **feature-value result**. Because
`velocity-spi` is a *published* compatibility surface with additive-only evolution (NFR-17), the
*shape* of that result — the fields it can carry — is frozen the moment the SPI ships. Behavior can
be filled in later; the envelope cannot grow a new field without re-cutting the published DTO.

The v0.2 review (§15) put four decision-bearing pieces of information onto this one result, but
tiered them across phases:

- **read-your-write level** (§15 R2 / ADR 0007) — Tier-1, *already frozen* onto the result.
- **per-feature apply status** `applied|failed|skipped` (§15 R3 / FR-34) — was Tier-2.
- **a distinguishable failure** `UNAVAILABLE|DEADLINE_EXCEEDED|…`, never a silent `0`
  (§15 R2 second half / NFR-19) — was Tier-2, and ADR 0007 explicitly deferred it "to its own
  Tier-2 ADR."
- **the feature-definition version/hash** the value was computed under (§15 R12 / FR-40) — was Tier-3.

The architect and both adopter reviews (fraud/rules and gateway) independently reached the same
conclusion on re-review: these are not *behavior*, they are the *shape of the result*. A result that
can only ever *be* a value cannot later *become* a distinguishable `DEADLINE_EXCEEDED`; adding that
variant in phase 2 re-cuts the published DTO — the exact phase-3 re-cut the §15 tiering exists to
prevent. For a synchronous authorization caller, "did this count fail, or is it truly zero?" is *the*
correctness question, and the sliding cardinality-cap outcome ([ADR 0005](0005-hll-tumbling-only.md))
needs a defined result too. This result rides on every call, so it is the single highest-value thing
to freeze correctly.

The *enforcement* of these fields — deadline plumbing, load-shedding, hot-reload skew handling — stays
Tier-2/Tier-3. Only the **envelope** is pulled into the Tier-1 freeze here.

## Decision

Freeze the data-plane result as a **value-or-failure sum type**, carrying all decision-bearing fields,
before the SPI ships. Fields whose behavior lands later are present in the frozen shape from day one
and populated when the behavior arrives.

```
FeatureResult = Success { FeatureValue }
              | Failure { FailureCode code, String detail }

FeatureValue  = { FeatureRef        feature,
                  BigDecimal        value,            // uniform: integer-valued for COUNT/DISTINCT, cents for SUM (P3)
                  Exactness         exactness,        // EXACT | APPROXIMATE          (FR-7)
                  ReadYourWriteLevel readYourWriteLevel, // ATOMIC | SNAPSHOT | BESTEFFORT (ADR 0007)
                  String            definitionVersionHash, // FR-40; nullable until R12 lands
                  WindowBounds      windowBounds,     // FR-7
                  Instant           asOf }            // FR-7

FailureCode   = UNAVAILABLE            // backend down/partitioned (NFR-19)
              | DEADLINE_EXCEEDED      // caller deadline hit before a result (NFR-19)
              | CARDINALITY_CAP_EXCEEDED // sliding exact-distinct over its cap (ADR 0005)
              | UNSUPPORTED_WINDOW     // FR-13
              | VALIDATION             // malformed request
              | ...                    // extensible; new codes are an ADDITIVE change

ApplyResult   = { List<PerFeature> perFeature, ... }
PerFeature    = { FeatureRef feature,
                  ApplyStatus status,      // APPLIED | FAILED | SKIPPED   (FR-34)
                  FeatureResult result }
```

Rules that make the freeze hold:

1. **A result is a value OR a distinguishable failure — never an ambiguous `0`.** A backend that is
   down/slow returns `Failure{UNAVAILABLE}` / `Failure{DEADLINE_EXCEEDED}`, so the caller can choose
   fail-open vs fail-closed deterministically. Returning `Success{value: 0}` for "I don't know" is
   forbidden and is a `velocity-testkit` TCK negative test ([ADR 0004](0004-mandatory-conformance-tck.md)).
2. **`FailureCode` is an extensible enum; adding a code is additive** (consumers must tolerate unknown
   codes — treat as a generic failure). This is the one place growth is expected.
3. **`definitionVersionHash` is part of the frozen shape now, nullable until FR-40's behavior lands.**
   Same for populating apply-status semantics — the field exists at freeze; its value is filled in
   when the feature (hot-reload versioning, load-shedding) ships.
4. **The result carries the read-your-write level *per feature value*, not per call** — a single
   `record()` fans out across backends (ADR 0007), so each returned value states its own level.
5. **These DTOs are serialization-neutral** (see [ADR 0002](0002-velocity-spi-standalone-module.md),
   amended): plain `velocity-spi` records with no Jackson binding; `velocity-core` and the wire layer
   own JSON. The AR-5 HTTP problem model gains matching `DEADLINE_EXCEEDED` and `OVERLOADED` types.

## Consequences

### Positive

- The SPI can be frozen and implemented behind without a foreseeable phase-2/3 re-cut of the
  universal result DTO — the concrete goal of §15's tiering.
- The gateway operator's milestone-1 test and the fraud adopter's "all five fields in one response"
  requirement are satisfiable against the *frozen* contract, not a future one.
- `CARDINALITY_CAP_EXCEEDED` gives ADR 0005's sliding exact-distinct cap a defined, typed outcome
  instead of an undefined reject/truncate.

### Negative

- More surface is frozen up front, including fields (`definitionVersionHash`, some `FailureCode`s)
  whose *behavior* does not exist in phase 1 — a small "declared but not yet populated" gap that must
  be documented so implementers don't assume a populated value.
- Callers must handle the `Failure` branch from day one, even before load-shedding/deadline
  enforcement is wired — slightly more caller code for behavior that is initially rare.

### Neutral

- The *enforcement* ADRs remain to be written (Tier-2: the deadline/bounded-failure behavior of
  NFR-19; load-shed/fairness NFR-22). This ADR freezes only the shape they will populate.

## Alternatives considered

- **Freeze only the value; add the failure variant additively later (ADR 0007's deferral).** Rejected:
  a sum type cannot gain a `Failure` arm additively without changing the published type; every
  consumer's exhaustiveness/deserialization breaks. This is the re-cut we are preventing.
- **Model failure out-of-band (exceptions instead of a result variant).** Rejected for the *per-feature*
  case: one fan-out `record()` can partially fail, so failure must be expressible per returned feature
  alongside successes in the same `ApplyResult`, which an all-or-nothing thrown exception cannot do.

## References

- Requirements §15 R2/R3/R12, FR-2, FR-7, FR-34, FR-40, NFR-19, NFR-17, AR-5.
- [ADR 0003](0003-spi-capability-mixins.md), [ADR 0004](0004-mandatory-conformance-tck.md),
  [ADR 0005](0005-hll-tumbling-only.md), [ADR 0007](0007-read-your-write-declared-capability.md).

## Open follow-ups

- Tier-2 ADR: hot-path deadline & bounded-failure *behavior* (BR-6 item (o)) — how the deadline is
  plumbed and enforced, and how `UNAVAILABLE`/`DEADLINE_EXCEEDED` are produced.
- Tier-2 ADR: load-shedding & per-namespace fairness (NFR-22), including the `OVERLOADED` result path.
