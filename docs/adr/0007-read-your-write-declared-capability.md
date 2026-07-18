# 7. Read-your-write is a declared capability, not a universal guarantee

Date: 2026-07-18

## Status

Accepted. **Completed by [ADR 0009](0009-hot-path-result-dto.md)**, which freezes the rest of the
hot-path result shape (the failure variant, per-feature apply status, and definition-version stamp)
that this ADR deferred.

## Context

D4 makes the primary interaction **record-and-return-velocities in a single synchronous call**, and
FR-2 says `record()` returns the *post-increment* velocities in the same call. The v0.1 draft, D4,
FR-2, and NFR-7 all read as though this read-your-write ("the value I get back reflects the write I
just made") were a **universal** guarantee across every backend.

The four-role review (§15 R2 — raised by the security operator, the fraud/rules adopter, *and* the
architect, the single most-cited gap) showed that universal read-your-write is a promise the backend
matrix cannot keep:

- An **exact** backend (Redis `INCR`/Lua, Postgres upsert-in-txn, DynamoDB atomic `ADD`) genuinely
  can return the value including the caller's own write, atomically (NFR-6).
- An **approximate** backend (S3, §8) is eventual/approximate by design; it *cannot* atomically
  reflect the caller's write in the returned value, and pretending it does would be a silent lie on a
  fraud/abuse path.

So a universal guarantee either bans the approximate backends the project explicitly wants (S3 for
cost) or forces them to lie. §15 R2 resolves this by making read-your-write a **declared capability**
with a per-result level. Because it adds a field to `BackendCapabilities` *and* a per-result level to
the SPI's feature-value DTO, it is a **Tier-1** change — it must be fixed before the SPI is frozen,
or the DTO gets re-cut at phase 3.

(§15 R2 also introduces the hot-path deadline & bounded-failure contract — `UNAVAILABLE` /
`DEADLINE_EXCEEDED`, never a silent `0`. That is a Tier-2 hot-path-GA concern and gets its own ADR;
the *SPI-shaping* half — the read-your-write level field and per-result level — is recorded here
because it is what must be frozen into the contract.)

## Decision

Read-your-write is a **declared backend capability with three levels**, carried both on
`BackendCapabilities` and on **every feature-value result** returned by `apply()`/`query()`
(ADR 0003). It is **not** a universal guarantee.

### The three levels

| Level | Meaning | Backends |
|---|---|---|
| `atomic` | The returned value reflects the caller's own write to every bucket it touched, atomically under concurrency (NFR-6). | Exact backends: Redis, JDBI/Postgres, DynamoDB. |
| `snapshot` | The returned value is a consistent snapshot but may not include the caller's just-applied write (e.g. read from a replica / async-materialized view). | A backend that reads a consistent-but-lagging view. |
| `besteffort` | No read-your-write guarantee; the value may be stale/approximate. MUST be flagged `approximate` (FR-7) and document staleness/error bounds. | Approximate backends: S3. |

The precise definition (NFR-7, amended by §15 R2): read-your-write means *"the returned value
reflects the caller's own write to every bucket it touched; window-aggregate consistency is per the
backend's declared level."* Exact backends are `atomic` (SHALL); approximate backends are
`besteffort` and MUST flag results.

### It lives in two places in the SPI (why this is a freeze-blocker)

1. **`BackendCapabilities.readYourWrite`** — the backend's declared level, so the engine and callers
   know the guarantee before making a call.
2. **A per-result `readYourWriteLevel`** on the feature-value DTO — because a single `record()` can
   fan out across features on *different* backends (FR-16 binds a feature to one backend), the level
   can differ *per returned feature value*, so it cannot be a single call-level flag. Each returned
   value states the level under which *that* value was produced.

Both are additive `velocity-spi` surface (NFR-17). Adding them after freeze would re-cut a published
DTO — hence Tier-1.

### Reconciliation with D4 / FR-2 / NFR-7

- **D4** already carries the resolved wording ("Read-your-write strength is a declared backend
  capability … exact backends are `atomic`; approximate backends (S3) are `besteffort` and flag
  results"). This ADR records the SPI mechanism that implements it.
- **FR-2**'s "post-increment velocities" is now read as *post-increment at the returned value's
  declared level* — `atomic` means truly post-increment; `besteffort` means "our best current
  estimate, flagged approximate," not a guarantee.
- **NFR-7** previously overclaimed a universal guarantee; it is amended to the per-level definition
  above. The conformance TCK (ADR 0004) asserts the reconciliation with a **negative test**:
  read-your-write on a `besteffort` backend must be *flagged*, never returned as if `atomic`.

## Consequences

### Positive

- **Approximate backends are honest, not banned.** S3 (cost) can be a first-class backend because it
  declares `besteffort` and flags results, instead of being forced to fake atomic read-your-write or
  being excluded outright.
- **The caller decides based on a stated guarantee.** A fraud rule that needs "the count including my
  event, right now" targets an `atomic` feature/backend; one that tolerates staleness can use
  `besteffort` — and it knows which it got, per result. No silent downgrade.
- **Frozen before it is expensive.** Putting the capability field and per-result level into the DTO
  now avoids a phase-3 re-cut of a published SPI — the whole reason §15 R2 is Tier-1.
- **Composes with fan-out across backends.** The per-result level means a mixed fan-out (some
  features `atomic`, some `besteffort`) returns accurate per-feature guarantees rather than one
  lowest-common-denominator flag.

### Negative

- **Callers must handle heterogeneity.** A caller can no longer assume every returned value is
  atomically read-your-write; it must read the per-result level (and the `exact|approximate` flag).
  This is more caller-side logic, but it is the honest shape of a multi-backend substrate — and pairs
  naturally with the "constrain a decision's features to one backend" option (§15 R3/NFR-20) for
  callers who want one uniform level.
- **`snapshot` needs a crisp definition per backend.** The middle level is the easiest to
  under-specify; any backend declaring `snapshot` must document exactly what snapshot it reads and
  its staleness bound, and the TCK must pin it — otherwise `snapshot` risks becoming a vague
  "somewhere between atomic and best-effort."

### Neutral

- v1's two reference backends (JDBI, Redis) are both `atomic`, so the `snapshot`/`besteffort` levels
  are exercised first by the in-memory testkit's negative scenarios and later by S3 (phase 4) — but
  the *field* must exist in the frozen SPI regardless, which is the point of recording it now.

## References

- Requirements §15 R2 (read-your-write as declared capability — the SPI-shaping half), D4 (primary
  interaction), FR-2 (post-increment return), NFR-6 (atomicity), NFR-7 (consistency, amended), FR-16
  (feature bound to one backend), FR-7 (exact/approximate flag), NFR-17 (additive SPI surface).
- Related: ADR 0003 (`BackendCapabilities`, per-result feature-value DTO), ADR 0004 (TCK negative
  test: best-effort must be flagged). The hot-path deadline & bounded-failure half of §15 R2 (BR-6
  item *o*) is deferred to its own Tier-2 ADR.
