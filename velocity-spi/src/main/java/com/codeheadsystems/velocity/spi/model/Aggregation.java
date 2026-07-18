// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.spi.model;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * What a feature aggregates: a {@link AggregationType} and, for {@link AggregationType#DISTINCT
 * DISTINCT}, the named dimension whose distinct values are counted (FR-11).
 *
 * <p>Invariant: {@code dimension} is non-null and non-blank <em>if and only if</em> the type is
 * {@code DISTINCT}. For {@code COUNT} and {@code SUM} it MUST be null. Use the static factories.
 *
 * @param type the aggregation kind
 * @param dimension the distinct dimension name (only for {@code DISTINCT}); otherwise null
 */
public record Aggregation(AggregationType type, @Nullable String dimension) {

  /** Enforces the dimension-iff-DISTINCT invariant. */
  public Aggregation {
    Objects.requireNonNull(type, "type");
    if (type == AggregationType.DISTINCT) {
      Objects.requireNonNull(dimension, "dimension is required for DISTINCT");
      if (dimension.isBlank()) {
        throw new IllegalArgumentException("dimension must not be blank for DISTINCT");
      }
    } else if (dimension != null) {
      throw new IllegalArgumentException("dimension must be null for " + type);
    }
  }

  /**
   * A COUNT aggregation.
   *
   * @return a {@code COUNT} aggregation with no dimension
   */
  public static Aggregation count() {
    return new Aggregation(AggregationType.COUNT, null);
  }

  /**
   * A SUM aggregation.
   *
   * @return a {@code SUM} aggregation with no dimension
   */
  public static Aggregation sum() {
    return new Aggregation(AggregationType.SUM, null);
  }

  /**
   * A DISTINCT aggregation over the given dimension.
   *
   * @param dimension the non-blank dimension name
   * @return a {@code DISTINCT} aggregation for the dimension
   */
  public static Aggregation distinct(String dimension) {
    return new Aggregation(AggregationType.DISTINCT, dimension);
  }
}
