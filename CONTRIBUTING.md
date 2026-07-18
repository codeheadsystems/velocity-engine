# Contributing

Contributions are welcome. This project is early — the design is settled and
implementation is starting — so the most valuable contributions right now are
faithful to the specification and the frozen contract. Please read this before
opening a pull request.

## Working agreements

1. **Spec-first.** [`docs/requirements.md`](./docs/requirements.md) is the
   source of truth. Its §2 locked decisions (D1–D8) and §2.1 resolved parameters
   (P1–P18) constrain scope; honor them, and cite requirement IDs (`FR-*`,
   `NFR-*`, `P*`, `D*`, `§15 R*`) in code comments, commits, and ADRs where they
   apply.
2. **ADR-first for design changes.** Non-trivial, cross-module decisions get an
   ADR under [`docs/adr/`](./docs/adr/) (Nygard format, numbered sequentially).
   This is **mandatory** for anything touching the **frozen `velocity-spi`
   contract** — the SPI interfaces + DTOs and the OpenAPI document are
   compatibility surfaces (requirements §15/NFR-17), and SPI evolution is
   **additive-only** with new capability fields defaulting to "unsupported."
   Read ADRs `0003` (capability mix-ins), `0005`/`0006` (HLL/distinct), `0007`
   (read-your-write), and `0008` (seed) before reshaping the SPI.
3. **Dependencies go through the version catalog.** New libraries are added in
   [`gradle/libs.versions.toml`](./gradle/libs.versions.toml) — never inline a
   version in a module's `build.gradle.kts` — and are justified in the commit
   message or an ADR.
4. **No `TODO` in `main`** unless it is paired with a tracking issue link.
5. **Correct → tested → fast.** Don't optimize prematurely.

## Enforced conventions

These are gated by the build; `./gradlew clean build test` fails if any is
violated, so run it before you push.

- **Formatting.** `google-java-format`, applied by Spotless. Run
  `./gradlew spotlessApply` to format.
- **SPDX license header.** Every `.java` file must start with
  `// SPDX-License-Identifier: BSD-3-Clause`. Spotless adds it via
  `spotlessApply` and `spotlessCheck` (part of `check`) enforces it.
- **Warnings are errors.** Library modules compile with `-Xlint:all -Werror`
  plus Error Prone; a warning fails the build.
- **Coverage floors** (`jacocoTestCoverageVerification`, part of `check`):
  library modules floor at **LINE ≥ 80% / BRANCH ≥ 70%**; a module may restate a
  *higher* limit but never a lower one. Trivial/generated code is excluded from
  the denominator (see the Definition of Done below), so the floor applies to
  logic, not boilerplate.
- **Nullability.** jspecify annotations; make nullness explicit at public
  surfaces.

Two build quirks are expected, not errors:

- Spotless is marked incompatible with the configuration cache (a
  google-java-format/Guava class-loading bug), so
  `Configuration cache entry discarded … spotless…` is normal. The build cache
  is disabled globally in `gradle.properties` for the same reason.
- `javadoc` prints "No public or protected classes found to document" for stub
  modules; `isFailOnError = false` keeps the build green. This self-resolves once
  a module gains public types — do not "fix" it by weakening anything else.

## Definition of Done

A change is not complete until all of the following hold. This applies to every
contributor, human or AI-assisted.

1. **Tests exist for the code the change adds or alters.** New or changed
   behavior ships with unit tests in the same PR. Code without tests is not done.
2. **≥ 80% line and ≥ 70% branch coverage** on the affected library module,
   enforced by `jacocoTestCoverageVerification`. This is a hard gate, not a
   target.
3. **Trivial code is exempt — and only trivial code.** Do **not** write tests for
   getters/setters, plain records/DTOs, or generated code, and do not pad
   coverage with tests that assert nothing. Such code is excluded from the
   coverage denominator: JaCoCo already filters record-generated members, and the
   build additionally excludes `**/dto/**`, `**/model/**`, and generated DI
   classes (`velocity.test-conventions`). A PR that adds *only* DTOs/records may
   therefore legitimately add no tests and still pass. **Never park real logic in
   an excluded package to dodge the gate** — that is the one thing this carve-out
   must not become.
4. **A feature is not done until it has an end-to-end / integration test.** Unit
   tests prove units; a feature must also be exercised end to end before it
   counts as complete. For a backend, that is the `velocity-testkit` conformance
   TCK (`*Scenarios`, ADR `0004`) run against the real engine (Testcontainers /
   local emulator). For the service tier, it is an integration test through the
   HTTP/OpenAPI surface. These map to the §11 acceptance criteria.
5. **`./gradlew clean build test` passes locally** before the PR is opened.

The build enforces (2) and (5); (1), (3), and (4) are review responsibilities.
An AI-assisted change must additionally pass the sub-agent validation described
in [`CLAUDE.md`](./CLAUDE.md) (a reviewer agent checks that the tests are
*meaningful* and that the code actually satisfies the requirement/ADR it targets)
before it is considered done.

## Build & test

```sh
./gradlew clean build test        # full build + all module tests (the CI gate)
./gradlew :velocity-core:test     # one module's tests
./gradlew :velocity-core:test --tests 'com.codeheadsystems.velocity.core.FooTest'         # one class
./gradlew :velocity-core:test --tests 'com.codeheadsystems.velocity.core.FooTest.myCase'  # one method
./gradlew spotlessApply           # auto-format + apply the SPDX header
./gradlew jacocoTestReport        # coverage HTML at <module>/build/reports/jacoco/
```

**JDK 21** is required; Gradle's toolchain will fetch one if it is not present.

When you add or change a backend, remember every `velocity-backend-*` must pass
the shared conformance TCK (`*Scenarios`) in `velocity-testkit` for its
**declared** capabilities, including the negative tests (HLL-on-sliding
rejected; read-your-write flagged, not silently wrong, on a `besteffort`
backend). See ADR `0004`.

## Pull request flow

1. For anything beyond a small fix, open an issue first so the approach can be
   agreed — especially for changes touching the SPI contract.
2. Branch from `main`, keep commits small and atomic, and write clear commit
   messages that cite the relevant requirement or ADR.
3. Ensure `./gradlew clean build test` passes locally.
4. Open the pull request against `main`. CI runs the same command on JDK 21 and
   is the required status check.

See [`GOVERNANCE.md`](./GOVERNANCE.md) for the decision model and how one becomes
a maintainer.
