// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.spi.model;

import java.util.List;
import java.util.Objects;

/**
 * The aggregate result of an {@code apply()} fan-out: one {@link PerFeature} entry per touched
 * feature (ADR 0009).
 *
 * @param perFeature the per-feature outcomes; defensively copied to an unmodifiable list
 */
public record ApplyResult(List<PerFeature> perFeature) {

  /** Stores an unmodifiable defensive copy of the per-feature outcomes. */
  public ApplyResult {
    Objects.requireNonNull(perFeature, "perFeature");
    perFeature = List.copyOf(perFeature);
  }
}
