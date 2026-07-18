// SPDX-License-Identifier: BSD-3-Clause

/**
 * The value types of the {@code velocity-spi} contract: intents, tuples, the value-or-failure
 * result sum type, seed aggregates, and {@code BackendCapabilities}.
 *
 * <p>These are plain, immutable, <strong>serialization-neutral</strong> records/enums/sealed sum
 * types with no Jackson (or Dagger/Dropwizard) binding (ADR 0002) — a backend author does not
 * inherit the engine's serialization stack as part of the frozen contract. JSON lives in {@code
 * velocity-core} and the wire layer.
 *
 * <p>The two shape-defining artifacts here are the capability-mix-in inputs/outputs of <a
 * href="{@docRoot}/../docs/adr/0003-spi-capability-mixins.md">ADR 0003</a> (backend-neutral {@code
 * Intent}/{@code QueryTuple}, {@code BackendCapabilities}) and the frozen hot-path result of <a
 * href="{@docRoot}/../docs/adr/0009-hot-path-result-dto.md">ADR 0009</a> ({@code FeatureResult =
 * Success | Failure} — a down/slow backend returns a distinguishable {@code Failure}, never a
 * silent {@code 0}). The SPI is null-hostile by default: value types validate their invariants in
 * compact constructors and annotate the few nullable fields with {@code
 * org.jspecify.annotations.@Nullable}. Evolution is additive-only (NFR-17).
 */
@NullMarked
package com.codeheadsystems.velocity.spi.model;

import org.jspecify.annotations.NullMarked;
