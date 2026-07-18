# 2. `velocity-spi` is a standalone contract module

Date: 2026-07-18

## Status

Accepted.

## Context

The Velocity Engine's central design tenet is **pluggable backends**: the storage backend is an SPI,
and nothing in the engine hard-depends on any one datastore (§1). v1 alone ships four backend
targets of deliberately different shapes — PostgreSQL/JDBI (tumbling reference), Redis (sliding,
hot-path), DynamoDB (volume), S3 (cost/approximate) — plus an in-memory testkit implementation
(§6.2, P10). Third parties are expected to write their own.

The question this ADR settles is **where the core↔backend contract physically lives**. Two layouts
were on the table:

1. **Contract inside the core.** The `VelocityStore` mix-ins, `BackendCapabilities`, and the shared
   SPI DTOs live in `velocity-core`; backends depend on `velocity-core` to implement them. This is
   what the sibling `pk-auth` project does today — its `CredentialStore`, `ChallengeStore`,
   `UserLookup`, etc. live inside `pk-auth-core` (see pk-auth ADR 0006), and its DynamoDB/JDBI
   backends depend on the core to get at them.
2. **Contract in its own module.** A standalone `velocity-spi` module holds the mix-ins, the
   `BackendCapabilities` descriptor, and the shared SPI DTOs. Both `velocity-core` and every
   `velocity-backend-*` depend on `velocity-spi`; backends do **not** depend on `velocity-core`.

Option 1 is fewer modules and is fine when the backend count is small and all backends are shipped
from the same repository. But it couples a backend author to the engine's entire internal transitive
dependency graph and its release cadence: to implement a store you pull in the fan-out resolver, the
YAML I/O, the window/aggregation model, and everything they drag in. It also blurs the compatibility
surface — when the "contract" and the "engine" ship as one artifact, it is unclear which types are
the frozen SPI and which are internal, and an incompatible engine change can silently break a backend
that only ever wanted the contract.

P14 and §6.1 resolve this in favor of a dedicated module. NFR-17 names the `velocity-spi`
interfaces + DTOs as an explicit compatibility surface that must evolve additive-only. That surface
deserves to *be* an artifact.

## Decision

The core↔backend contract lives in its **own Gradle module, `velocity-spi`** (build phase 1,
published — §6.2). It contains, and only contains, the published contract:

- the data-plane capability mix-ins (`CountStore`, `SumStore`, `DistinctStore`, `SlidingSupport`,
  `TumblingSupport`, `SeedSupport` — see ADR 0003);
- the `BackendCapabilities` descriptor (see ADR 0003, 0007);
- the shared SPI DTOs — backend-neutral intents `(feature, subject, aggregation, member|value)`,
  feature-value results (carrying the `exact|approximate` flag and read-your-write level), query
  tuples, purge/seed requests.

Dependency direction is fixed:

```
velocity-core  ──depends-on──►  velocity-spi  ◄──depends-on──  velocity-backend-*
```

`velocity-core` depends on `velocity-spi`. Every `velocity-backend-*` depends on `velocity-spi`.
Backends **do not** depend on `velocity-core` — a backend author needs the contract, never the
engine's internals. `velocity-spi` itself depends on as little as possible (jspecify annotations,
the money type; no Dropwizard, no Dagger, **no Jackson**) so that implementing a backend does not drag
the engine's framework choices onto the backend author's classpath.

**Resolved (v0.2 re-review):** the SPI is **serialization-neutral**. Its DTOs (intents,
`FeatureResult`/`FeatureValue`, `BackendCapabilities`; see [ADR 0009](0009-hot-path-result-dto.md))
are plain records with no Jackson binding; Jackson 3 lives in `velocity-core` / the wire layer, which
owns JSON (NFR-4). The skeleton originally put `jackson` on `velocity-spi`'s `api` surface — that
would have frozen Jackson into the compatibility surface (NFR-17); it was removed so the code matches
this ADR.

`velocity-spi` is the versioned compatibility surface named in NFR-17: it evolves **additive-only**,
with new capability fields defaulting to "unsupported."

This is a **deliberate improvement over `pk-auth`**, which keeps its Store interfaces inside
`pk-auth-core` (ADR 0006). The Velocity Engine expects more backends, from more third parties, built
against a contract that must stay frozen while the engine evolves — so the contract earns its own
module here, where in pk-auth it did not.

## Consequences

### Positive

- **A backend depends on a small, stable artifact.** Implementing a store means depending on
  `velocity-spi` and nothing else of the engine's. A third-party backend never inherits the fan-out
  resolver, YAML I/O, or the engine's transitive graph.
- **The compatibility surface is a real boundary, not a convention.** "What is frozen" is exactly
  "what is in `velocity-spi`." NFR-17's additive-only rule has a concrete artifact to apply to, and
  an incompatible engine-internal change cannot leak into the contract because backends cannot see
  engine internals.
- **Independent versioning.** A backend can be built and released against a published `velocity-spi`
  version without tracking the engine's release cadence.
- **Clean layering with the core.** `velocity-core` resolves fan-out to backend-neutral intents
  (ADR 0003) expressed in `velocity-spi` DTOs; it holds no backend types. The seam is explicit.

### Negative

- **One more module.** Slightly more build ceremony than pk-auth's single-core layout, and one more
  published artifact to sign and release (NFR-16). We accept it as the cost of a genuine contract
  boundary.
- **Discipline required on what belongs in the SPI.** It is tempting to push a convenience type into
  `velocity-spi` because a backend "might want it." Anything that lands there is frozen (NFR-17), so
  the module must stay minimal: contract types only, no engine helpers.

### Neutral

- The in-memory testkit backend (`velocity-testkit`) also implements `velocity-spi`, which is what
  lets the conformance TCK (ADR 0004) run against it exactly as it runs against a real backend.

## References

- Requirements P14 (SPI packaging), §6.1 (the SPI module), NFR-17 (compatibility surfaces),
  §6.2 (module layout).
- pk-auth ADR 0006 (`UserLookup` is an SPI, not an owned table) — the same "persistence is an SPI"
  instinct, but kept inside `pk-auth-core`; this ADR moves the contract out into its own module.
