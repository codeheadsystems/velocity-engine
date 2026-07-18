// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.spi.model;

import java.util.Objects;

/**
 * One bucket's worth of seeded aggregate state (ADR 0008).
 *
 * <p>A seed operation supplies a list of these for a {@code (namespace, subject, feature)} so the
 * backend can place the state into exactly the buckets a windowed query will later merge (FR-14). A
 * single total is explicitly not a valid seed.
 *
 * @param bounds the bucket's time span
 * @param aggregate the pre-computed aggregate for the bucket
 */
public record BucketValue(WindowBounds bounds, SeedAggregate aggregate) {

  /** Validates both components are present. */
  public BucketValue {
    Objects.requireNonNull(bounds, "bounds");
    Objects.requireNonNull(aggregate, "aggregate");
  }
}
