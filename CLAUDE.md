# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this project is

An open-source **velocity engine**: a backend-agnostic system that records events and answers
"how many / how much / how many distinct X for this subject in the last N units of time." It is a
**counting substrate, not a decisioning platform** — it returns counts/sums/distinct-cardinalities
over time windows; thresholds, rules, and ALLOW/REVIEW/DENY decisions are the caller's job. BSD-3-Clause.

The project is early: the requirements, the Tier-1 ADRs, and a compiling Gradle skeleton exist, but
the modules are largely stubs (`package-info.java` only). It deliberately mirrors the conventions of
the sibling project at `../pk-auth` (build-logic, version catalog, nmcp publishing, ADR style).

## Source of truth — read before non-trivial work

- **`docs/requirements.md`** — the authoritative spec. Start with **§2.1 Resolved parameters
  (P1–P18)** for locked decisions, **§6 Architecture** for the module/SPI design, and **§15
  Review-Driven Revisions** for the corrections that shape the SPI. Requirement IDs (`FR-*`, `NFR-*`,
  `P*`, `D*`, `§15 R*`) are cited throughout the code/comments and ADRs — honor them.
- **`docs/adr/0001-0008`** — the Tier-1 architecture decisions. **These gate the freeze of the
  published `velocity-spi` contract.** Do not change the SPI's shape without reading 0003 (capability
  mix-ins), 0005/0006 (HLL/distinct), 0007 (read-your-write), 0008 (seed). `velocity-spi` and the
  OpenAPI contract are compatibility surfaces (NFR-17): SPI evolution is additive-only.

## Architecture (the big picture)

The design's central seam — the thing you must not get wrong — is **where windowing lives** (ADR 0003):

- **`velocity-core` resolves fan-out to backend-neutral *intents*** `(feature, subject, aggregation,
  member|value)`. It does **not** compute buckets or storage keys.
- **The backend owns everything time-shaped**: bucket keying, sliding-vs-tumbling semantics,
  eviction/TTL, and its own key schema. There is intentionally **no single storage-key schema**
  (Redis ZSET keys ≠ DynamoDB items ≠ S3 paths).
- The SPI is therefore **not one fat interface** but **capability mix-ins** in `velocity-spi`:
  `CountStore`, `SumStore`, `DistinctStore`, `SlidingSupport`, `TumblingSupport`, `SeedSupport`,
  plus a `BackendCapabilities` descriptor. A backend implements only what it declares.

The **feature** is the core abstraction: a named counter = `(subject-type, aggregation, window[,
dimension])`, e.g. `card.count.1h`. One `record()` fans an event out to every matching feature.
Everything is **capability-driven**: backends declare supported windows, aggregations, exactness,
`maxRetention`, read-your-write level (`atomic|snapshot|besteffort`), and idempotency; the engine
**validates feature definitions against those capabilities and never pretends** a backend can do
something it can't (fast-reject, not silent degradation).

Locked invariants that pervade the code (from §2.1): **namespace is first-class** (every key/SPI op
is namespace-scoped); **server-clock-only** event time (backend clock authoritative for sliding
windows); **stateless app tier, backend is source of truth**; money is **`BigDecimal` in cents**
(never binary float); DISTINCT is **exact→HLL over a threshold, HLL on tumbling only** (sliding
distinct is exact/ZSET-bounded); **Dropwizard is the only standalone runtime** (no Spring/Micronaut
adapters); **Dagger 2** for DI (compile-time, no reflective container).

### Modules and build phases (`settings.gradle.kts`, `docs/requirements.md` §6.2)

Phase 1 (present): `velocity-spi` (the contract), `velocity-core` (engine), `velocity-api` (OpenAPI +
DTOs), `velocity-testkit` (in-memory SPI impl + the **conformance TCK** every backend must pass).
Later phases are commented includes: v1 backends `velocity-backend-jdbi` (Postgres, tumbling ref) and
`velocity-backend-redis` (sliding, hot-path ref), then `velocity-dropwizard`, `velocity-client-java`,
then `velocity-backend-dynamodb`, `velocity-backend-s3`. Dependency direction: `velocity-core` and
every `velocity-backend-*` depend on `velocity-spi`; backends do **not** depend on core. Every backend
must pass the shared `*Scenarios` TCK in `velocity-testkit` for its **declared** capabilities,
including negative tests (HLL-on-sliding rejected, read-your-write flagged on `besteffort`).

## Build, test, lint

Gradle multi-module with convention plugins in `build-logic/` (`velocity.java-conventions`,
`velocity.library-conventions`, `velocity.test-conventions`, `velocity.publish-conventions`).
Dependency versions are centralized in `gradle/libs.versions.toml` (add new libs there).

```bash
./gradlew clean build test        # full build + all module tests (the CI gate)
./gradlew :velocity-core:test     # one module's tests
./gradlew :velocity-core:test --tests 'com.codeheadsystems.velocity.core.FooTest'         # one class
./gradlew :velocity-core:test --tests 'com.codeheadsystems.velocity.core.FooTest.myCase'  # one method
./gradlew spotlessApply           # auto-format (google-java-format) + apply the SPDX license header
./gradlew jacocoTestReport        # coverage HTML at <module>/build/reports/jacoco/
```

- **Formatting/lint is enforced.** `check` runs `spotlessCheck`; every `.java` file must start with
  the header `// SPDX-License-Identifier: BSD-3-Clause` (spotless adds it via `spotlessApply`).
  Library modules compile with `-Xlint:all -Werror` + Error Prone, so warnings fail the build.
- **Coverage gates** (`jacocoTestCoverageVerification`, part of `check`): library modules floor at
  **LINE ≥80% / BRANCH ≥70%**; a module may restate a *higher* limit but never lower. Trivial/generated
  code (records/DTOs, `**/dto/**`, `**/model/**`, generated DI) is excluded from the denominator, so
  the floor applies to logic. See the working model below and `CONTRIBUTING.md` → Definition of Done.
- **Spotless must run fresh** — it is marked incompatible with the configuration cache (a
  google-java-format/Guava class-loading bug). Seeing "Configuration cache entry discarded … spotless…"
  is expected, not an error. The build cache is also disabled globally in `gradle.properties` for the
  same reason.
- **Versioning**: `version` in `gradle.properties` is a SNAPSHOT; a build on an exact git tag
  `vX.Y.Z` publishes that release version (`settings.gradle.kts`). Publishing is nmcp →
  Sonatype Central Portal (`publishAggregationToCentralPortal`, needs `CENTRAL_PORTAL_*` creds).
- **Transient skeleton quirk**: `javadoc` prints "No public or protected classes found to document"
  for stub modules; `isFailOnError = false` in `velocity.java-conventions` keeps the build green. This
  self-resolves once a module gains public types — do not "fix" it by weakening anything else.

## Working model — how to write code here (required)

`CONTRIBUTING.md` → **Definition of Done** is binding for every code change. In short: tests ship
with the code; **≥80% line / ≥70% branch** on the affected module (a hard build gate); **no tests for
trivial code** (records/DTOs/getters and generated code are excluded from coverage — do not pad, and
do not hide real logic in an excluded package); and **a feature is not done until it has an
end-to-end/integration test** (a backend → the `velocity-testkit` conformance TCK; the service → an
HTTP/OpenAPI integration test).

For any **non-trivial** change, follow this loop before reporting the work complete:

1. Write the implementation **and** its tests together (unit tests for logic; the e2e/TCK test for a
   feature).
2. Run `./gradlew clean build test` (or the affected module's `build`) and confirm it is green,
   including `jacocoTestCoverageVerification`. **Never report a change done on a red or skipped gate.**
3. **Invoke the `change-validator` sub-agent** (`.claude/agents/change-validator.md`) via the Agent
   tool, telling it what the change is and which requirement/ADR (`FR-*`/`NFR-*`/ADR-N/§11 item) it
   implements. It independently checks that the tests are *meaningful* (would fail if the code were
   broken) and that the code actually satisfies that requirement and the frozen `velocity-spi`
   contract (NFR-17, additive-only).
4. If it returns `CHANGES REQUESTED`, address the findings and re-validate. Only call the change done
   after it returns `APPROVE` (or you have explicitly resolved each finding with the user).

Trivial, no-runtime-surface changes (docs, comments, a pure DTO/record addition, build-comment tweaks)
do not require the sub-agent — but a DTO addition still goes through the build. When in doubt, validate.

## CI / automation (`.github/`)

`ci.yml`'s **`build` job** (`./gradlew clean build test` on JDK 21) is the required status check.
Dependabot (`dependabot.yml`) runs **weekly on Tuesday** for the `gradle` and `github-actions`
ecosystems, grouped into one PR each. `auto-dependabot.yml` auto-approves and auto-merges only
**patch/minor** Dependabot PRs once CI is green; **major bumps and all github-actions updates are
held for human review** (a green build runs a dependency's build-time code but doesn't prove a new
release is trustworthy). Auto-merge also needs three repo settings enabled (allow auto-merge, allow
Actions to approve PRs, and branch protection requiring the `build` check) — see the header comment
in `auto-dependabot.yml`.
