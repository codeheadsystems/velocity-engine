# Changelog

All notable changes to the Velocity Engine are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versions follow
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

The project is pre-1.0. The `0.x` line is a single pre-stable development series:
the `velocity-spi` interfaces + DTOs and the OpenAPI contract are compatibility
surfaces (requirements §15/NFR-17), but they are **not yet frozen** and may
change before 1.0. No versions have been released to Maven Central yet.

## [Unreleased]

The project is in its **initial design and scaffolding** phase. The
specification and the Tier-1 architecture are settled; functional implementation
is just beginning. The modules are compiling skeletons — there is no working
counting code, no benchmarks, and no published artifacts yet.

### Added

- **Requirements specification** ([`docs/requirements.md`](./docs/requirements.md),
  Draft v0.2) — functional and non-functional requirements, the locked scope
  decisions (D1–D8) and resolved parameters (P1–P18), the architecture and SPI
  design (§6), and the review-driven revisions that shape the SPI (§15).
- **Tier-1 Architecture Decision Records** ([`docs/adr/0001`–`0008`](./docs/adr/)) —
  the decisions that gate freezing the published `velocity-spi` contract: record
  ADRs, `velocity-spi` as a standalone contract module, SPI capability mix-ins,
  the mandatory conformance TCK, HLL restricted to tumbling windows, the
  distinct exact→HLL threshold and merge rule, read-your-write as a declared
  capability, and the seed/backfill contract.
- **Gradle multi-module skeleton** — Phase 1 modules `velocity-spi`,
  `velocity-core`, `velocity-api`, and `velocity-testkit` as compiling stubs,
  with `build-logic` convention plugins, the `gradle/libs.versions.toml` version
  catalog, and nmcp → Sonatype Central Portal publishing wiring, mirroring the
  sibling `../pk-auth` project.
- **Project governance documentation** — `README.md`, `GOVERNANCE.md`,
  `SECURITY.md`, `CONTRIBUTING.md`, and this changelog (requirements GR-2/GR-3).

### Notes

- Nothing is published to Maven Central yet; the current version is
  `0.1.0-SNAPSHOT`.
- v1 will ship two production backends — Postgres (JDBI, tumbling reference) and
  Redis (sliding, hot-path reference); see the roadmap in
  [`GOVERNANCE.md`](./GOVERNANCE.md) and the v1 acceptance criteria in
  requirements §11.

[Unreleased]: https://github.com/codeheadsystems/velocity-engine/commits/main
