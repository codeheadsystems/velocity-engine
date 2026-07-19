// SPDX-License-Identifier: BSD-3-Clause

/**
 * The {@code velocity-core} engine: the embeddable heart that turns a {@code record()} into
 * backend-neutral intents and back into per-feature results.
 *
 * <p>This package holds the engine <em>logic</em> — {@link
 * com.codeheadsystems.velocity.core.VelocityEngine} (the public API), the {@link
 * com.codeheadsystems.velocity.core.FanOutResolver fan-out resolver} (FR-18), the {@link
 * com.codeheadsystems.velocity.core.CapabilityValidator capability validator} (FR-29), the {@link
 * com.codeheadsystems.velocity.core.DimensionHasher keyed dimension hasher} (FR-38), the {@link
 * com.codeheadsystems.velocity.core.FeatureDefinitionProvider definition provider} with atomic
 * hot-reload (FR-17), the {@link com.codeheadsystems.velocity.core.BackendRegistry backend
 * registry}, and {@link com.codeheadsystems.velocity.core.FeatureDefinitionYaml YAML I/O} (FR-28).
 * The value types live in {@code com.codeheadsystems.velocity.core.model}.
 *
 * <p><strong>Deferred follow-ups (this increment).</strong> Micrometer metrics wiring (NFR-11) is
 * intentionally out — Micrometer stays a {@code compileOnly} dependency until the service tier
 * wires a real {@code MeterRegistry}. A KMS-backed {@link
 * com.codeheadsystems.velocity.core.NamespaceSaltProvider} is deferred (only an in-memory default
 * ships here; production MUST source the salt from a separate trust domain per §15 R11).
 * Non-primary hot-reload sources (loading definitions from a store/watcher, OQ-C) are deferred; the
 * {@link com.codeheadsystems.velocity.core.MutableFeatureDefinitionProvider} provides the
 * atomic-swap mechanism a source would drive.
 */
@NullMarked
package com.codeheadsystems.velocity.core;

import org.jspecify.annotations.NullMarked;
