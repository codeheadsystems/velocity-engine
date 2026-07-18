// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.spi;

/**
 * Marker mix-in: the backend supports tumbling windows (ADR 0003, ADR 0005).
 *
 * <p>Tumbling windows are aligned, fixed buckets; a multi-bucket window is the merge of its
 * buckets, <strong>edge-approximate at the current boundary</strong> (FR-14). Tumbling is the
 * <strong>only window type on which HLL-distinct is valid</strong> (ADR 0005): a bucket is a closed
 * interval that never needs to shed members, and HLL's lossless union merges buckets at read time.
 *
 * <p>This interface declares no methods: it is a capability flag whose coherence with {@link
 * com.codeheadsystems.velocity.spi.model.BackendCapabilities} is asserted by the conformance TCK
 * (ADR 0004).
 */
public interface TumblingSupport extends VelocityBackend {}
