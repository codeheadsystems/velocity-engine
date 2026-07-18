// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.spi.model;

/**
 * How time is modeled for a window (D3, FR-14).
 *
 * <p>{@link #SLIDING} covers {@code [now - duration, now]} with the backend clock as authority
 * (FR-3); distinct on a sliding window is exact-only and capped (ADR 0005). {@link #TUMBLING} uses
 * aligned fixed buckets whose merge forms a multi-bucket window, edge-approximate at the current
 * boundary (FR-14); it is the only window type on which HLL-distinct is valid (ADR 0005).
 */
public enum WindowType {
  /** True sliding window {@code [now - duration, now]} (ADR 0005). */
  SLIDING,
  /**
   * Aligned fixed buckets merged into a window; the only place HLL-distinct is valid (ADR 0005).
   */
  TUMBLING
}
