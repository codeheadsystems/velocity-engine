// SPDX-License-Identifier: BSD-3-Clause

/**
 * The {@code velocity-spi} contract: the core↔backend seam as capability mix-ins (ADR 0003).
 *
 * <p>The data-plane SPI is deliberately <strong>not one fat interface</strong> but a set of
 * capability mix-ins on top of {@link com.codeheadsystems.velocity.spi.VelocityBackend}: the
 * aggregation stores {@link com.codeheadsystems.velocity.spi.CountStore}, {@link
 * com.codeheadsystems.velocity.spi.SumStore}, {@link
 * com.codeheadsystems.velocity.spi.DistinctStore}; the window-capability markers {@link
 * com.codeheadsystems.velocity.spi.SlidingSupport} and {@link
 * com.codeheadsystems.velocity.spi.TumblingSupport}; and the optional {@link
 * com.codeheadsystems.velocity.spi.SeedSupport}. A backend implements only the mix-ins for the
 * capabilities it declares in {@link com.codeheadsystems.velocity.spi.model.BackendCapabilities} —
 * the abstraction is honest by construction, with no runtime "unsupported" throw for a
 * declared-unsupported capability. The conformance TCK (ADR 0004) asserts that the implemented
 * mix-ins agree with the declared capabilities.
 *
 * <p>{@code velocity-core} resolves fan-out to backend-neutral {@code Intent}s; the backend owns
 * bucket keying, sliding/tumbling semantics, eviction, and its own key schema. Every {@code
 * apply()}/{@code query()} returns the frozen value-or-failure {@link
 * com.codeheadsystems.velocity.spi.model.FeatureResult} of <a
 * href="{@docRoot}/../docs/adr/0009-hot-path-result-dto.md">ADR 0009</a> — a down/slow backend
 * returns a distinguishable {@code Failure}, never a silent {@code 0}. The value types live in
 * {@link com.codeheadsystems.velocity.spi.model} and are serialization-neutral (ADR 0002).
 * Evolution is additive-only (NFR-17): a new capability is a new mix-in or a new capability field
 * defaulting to unsupported.
 */
@NullMarked
package com.codeheadsystems.velocity.spi;

import org.jspecify.annotations.NullMarked;
