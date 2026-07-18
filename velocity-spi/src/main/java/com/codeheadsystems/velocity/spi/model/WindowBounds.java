// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.spi.model;

import java.time.Instant;
import java.util.Objects;

/**
 * The concrete {@code [start, end]} time span a returned value covers (FR-7).
 *
 * <p>Reported by the backend so a caller knows exactly which interval a value was computed over
 * (the backend clock is authority for sliding edges, FR-3).
 *
 * @param start the inclusive start instant
 * @param end the end instant; must not be before {@code start}
 */
public record WindowBounds(Instant start, Instant end) {

  /** Validates that {@code end} is not before {@code start}. */
  public WindowBounds {
    Objects.requireNonNull(start, "start");
    Objects.requireNonNull(end, "end");
    if (end.isBefore(start)) {
      throw new IllegalArgumentException(
          "window end " + end + " must not be before start " + start);
    }
  }
}
