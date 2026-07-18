// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.spi;

/**
 * Marker mix-in: the backend supports true sliding windows (ADR 0003, ADR 0005).
 *
 * <p>A sliding window covers {@code [now - duration, now]} with the <strong>backend clock as
 * authority</strong> (FR-3), not the stateless pod's clock. Distinct on a sliding window is
 * <strong>exact-only and capped</strong> — there is no HLL on sliding (ADR 0005), because a sketch
 * cannot evict aging members; exceeding the cap is a declared, flagged {@link
 * com.codeheadsystems.velocity.spi.model.FailureCode#CARDINALITY_CAP_EXCEEDED} condition.
 *
 * <p>This interface declares no methods: it is a capability flag whose coherence with {@link
 * com.codeheadsystems.velocity.spi.model.BackendCapabilities} is asserted by the conformance TCK
 * (ADR 0004).
 */
public interface SlidingSupport extends VelocityBackend {}
