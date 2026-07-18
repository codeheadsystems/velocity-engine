// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.spi.model;

/**
 * Whether a returned value is exact or an approximation (FR-7).
 *
 * <p>A window is {@link #APPROXIMATE} if any constituent bucket was served by an HLL sketch (ADR
 * 0006); otherwise it is {@link #EXACT}. Callers compare cardinality/velocity signals against
 * thresholds, so the flag lets them know whether a value carries HLL's ~0.81% error floor.
 */
public enum Exactness {
  /** The value is exact (set/count/sum with no sketch involved). */
  EXACT,
  /** The value is approximate (at least one HLL bucket contributed, ADR 0006). */
  APPROXIMATE
}
