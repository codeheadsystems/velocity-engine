// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.spi.model;

/**
 * The per-feature outcome of an {@code apply()} fan-out (FR-34, ADR 0009).
 *
 * <p>Because one {@code record()} fans out across the matching features, each touched feature
 * carries its own status alongside its {@link FeatureResult} in a {@link PerFeature} entry.
 */
public enum ApplyStatus {
  /** The write was applied to the feature. */
  APPLIED,
  /**
   * The write failed for the feature; the accompanying {@link FeatureResult} carries the reason.
   */
  FAILED,
  /** The write was intentionally skipped (e.g. idempotent replay, load-shed) for the feature. */
  SKIPPED
}
