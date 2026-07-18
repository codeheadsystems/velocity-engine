// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.spi.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The successful value of a feature read/write, with all decision-bearing metadata (ADR 0009).
 *
 * <p>This is the payload of a {@link FeatureResult.Success}. The {@code value} is a {@link
 * BigDecimal} for every aggregation — integer-valued for {@code COUNT}/{@code DISTINCT}, integer
 * cents for {@code SUM} (P3) — so callers do not juggle {@code long} vs. {@code BigDecimal}.
 *
 * @param feature the feature this value is for
 * @param window the window the value covers
 * @param value the aggregate value (integer for COUNT/DISTINCT, cents for SUM)
 * @param exactness whether the value is exact or approximate (FR-7)
 * @param readYourWriteLevel the level under which this value was produced (ADR 0007)
 * @param definitionVersionHash the feature-definition version the value was computed under (FR-40);
 *     <strong>nullable until FR-40's behavior lands</strong> — the field is part of the frozen
 *     shape now (ADR 0009 rule 3) but is not populated in phase 1
 * @param windowBounds the concrete interval the value covers (FR-7)
 * @param asOf the backend-clock instant the value is as-of (FR-7)
 */
public record FeatureValue(
    Feature feature,
    Window window,
    BigDecimal value,
    Exactness exactness,
    ReadYourWriteLevel readYourWriteLevel,
    @Nullable String definitionVersionHash,
    WindowBounds windowBounds,
    Instant asOf) {

  /**
   * Validates all non-nullable components are present ({@code definitionVersionHash} may be null).
   */
  public FeatureValue {
    Objects.requireNonNull(feature, "feature");
    Objects.requireNonNull(window, "window");
    Objects.requireNonNull(value, "value");
    Objects.requireNonNull(exactness, "exactness");
    Objects.requireNonNull(readYourWriteLevel, "readYourWriteLevel");
    Objects.requireNonNull(windowBounds, "windowBounds");
    Objects.requireNonNull(asOf, "asOf");
  }
}
