// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.spi.model;

import java.time.Duration;
import java.util.Objects;

/**
 * A time window of a given {@link WindowType} and duration (FR-14).
 *
 * <p>The window names a span (e.g. one hour) and how time is modeled over it (sliding vs.
 * tumbling). The backend owns how the span is realized into buckets/ranges (ADR 0003).
 *
 * @param duration the window span; must be strictly positive
 * @param type sliding or tumbling
 */
public record Window(Duration duration, WindowType type) {

  /** Validates the duration is strictly positive. */
  public Window {
    Objects.requireNonNull(duration, "duration");
    Objects.requireNonNull(type, "type");
    if (duration.isZero() || duration.isNegative()) {
      throw new IllegalArgumentException("window duration must be positive, was " + duration);
    }
  }
}
