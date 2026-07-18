# Security Policy

The Velocity Engine is designed to sit on abuse- and fraud-critical paths, so
security reports are taken seriously and handled with priority. Thank you for
helping keep it and its users safe. This is requirement **GR-3** (coordinated
security disclosure).

## Supported versions

The project is **pre-1.0 and pre-release**: the only supported line is the
development trunk on `main`. There are no published releases yet, so there is no
back-port window — fixes land on `main`.

| Version | Supported |
| ------- | --------- |
| `main` (pre-release, `0.x-SNAPSHOT`) | :white_check_mark: |
| Anything else | :x: (nothing else exists yet) |

Once the project cuts releases, this table will move to "fixes land on the
latest published line; Maven Central releases are immutable, so a fix ships as a
new release rather than a re-publish — upgrade to pick it up."

## Reporting a vulnerability

**Please do not open a public issue, pull request, or discussion for a suspected
vulnerability.** Public disclosure before a fix is available puts downstream
users at risk.

Use one of these private channels instead:

1. **GitHub private vulnerability reporting (preferred).** On the repository, go
   to the **Security** tab → **Report a vulnerability**. This creates a private
   advisory thread visible only to you and the maintainer.
2. **Email.** Send details to **ned.wolpert@gmail.com** with
   `velocity-engine security` in the subject line.

Please include, as far as you can:

- The affected module(s) and commit/version (e.g. `velocity-core` at commit
  `abc1234`).
- A description of the issue and its impact — what an attacker can do.
- Steps to reproduce — a minimal proof of concept, failing test, or request
  sequence is ideal.
- Any relevant configuration (backend, feature definitions, auth setup).

## What to expect

- **Acknowledgement** within **3 business days**.
- An initial assessment (severity, affected surface, whether it reproduces)
  within **10 business days**.
- A fix targeted within **90 days** of triage; complex issues may take longer,
  and we will say so.
- **Coordinated disclosure.** We ask that you keep the report private until a fix
  is available and a GitHub Security Advisory is published. We are happy to
  credit you unless you prefer to remain anonymous.

Because this is a single-maintainer project (see [`GOVERNANCE.md`](./GOVERNANCE.md)),
these are good-faith targets, not a staffed SLA. Response may be slower during
travel or other absences; we will communicate if a timeline slips.

## Scope

This policy covers the code in this repository: the `velocity-*` library
modules, the Dropwizard service, the wire contract (OpenAPI), and the generated
client, as those land.

Security is **capability-driven** in this engine, and several security-relevant
behaviors are explicit requirements rather than incidental — reports against
them are in scope and welcome:

- **Namespace-scoped authorization** (NFR-21): each API key is bound to an
  allowed-namespace set; a request whose path namespace is outside that scope is
  denied by default. Cross-tenant read/poisoning is a security bug.
- **PII at rest** (FR-38): exact DISTINCT dimension values (IPs, tokens, device
  IDs) are keyed-hashed at rest with a per-namespace salt; raw PII must not
  persist.
- **Hot-path failure contract** (NFR-19): `record()`/`query()` must fail fast
  within the caller deadline and return a distinguishable `UNAVAILABLE` /
  `DEADLINE_EXCEEDED` — never a silent `0` that a caller could misread as "no
  velocity."
- **Self-overload protection** (NFR-22): bounded concurrency / load-shedding and
  per-namespace fairness, so one tenant's flood cannot degrade another's latency.

This policy does **not** cover the responsibilities a deploying operator
retains — TLS termination, network ingress, secrets management, backend
datastore hardening, and the decisioning/authorization logic the caller builds
*on top of* the engine's counts (the engine is a counting substrate, not a
decisioning platform — see [`README.md`](./README.md) and requirement GR-1).

> **Maturity note.** This project is pre-release, largely AI-authored, and has
> not undergone a formal third-party security review unless a note in the
> repository says otherwise. Evaluate it accordingly before production use.
