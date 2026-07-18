// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.spi.model;

import java.util.Objects;

/**
 * The per-feature outcome of an {@code apply()} fan-out (FR-34, ADR 0009).
 *
 * <p>One {@code record()} fans out across the matching features; each touched feature reports its
 * {@link ApplyStatus} and its {@link FeatureResult} so a partial failure is expressible alongside
 * successes in the same {@link ApplyResult}.
 *
 * @param feature the feature this outcome is for
 * @param status whether the write was applied, failed, or skipped
 * @param result the value-or-failure result for the feature
 */
public record PerFeature(Feature feature, ApplyStatus status, FeatureResult result) {

  /** Validates all components are present. */
  public PerFeature {
    Objects.requireNonNull(feature, "feature");
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(result, "result");
  }
}
