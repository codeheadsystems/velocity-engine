// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.spi.model;

import java.util.List;
import java.util.Objects;

/**
 * A named counter definition: {@code (name, aggregation, windows)} — e.g. {@code card.count.1h}.
 *
 * <p>The feature is the core abstraction (CLAUDE.md): one {@code record()} fans an event out to
 * every matching feature. It names its aggregation and the set of windows it is tracked over; the
 * backend it is bound to owns how those windows are stored.
 *
 * @param name the non-blank feature name
 * @param aggregation what the feature aggregates
 * @param windows the non-empty set of windows the feature is tracked over; defensively copied to an
 *     unmodifiable list
 */
public record Feature(String name, Aggregation aggregation, List<Window> windows) {

  /** Validates the name and windows, and stores an unmodifiable defensive copy of the windows. */
  public Feature {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(aggregation, "aggregation");
    Objects.requireNonNull(windows, "windows");
    if (name.isBlank()) {
      throw new IllegalArgumentException("feature name must not be blank");
    }
    if (windows.isEmpty()) {
      throw new IllegalArgumentException("feature must declare at least one window");
    }
    windows = List.copyOf(windows);
  }
}
