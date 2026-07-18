// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.spi.model;

import java.util.Objects;

/**
 * A backend-neutral read request {@code (subject, aggregation, window)} (ADR 0003).
 *
 * <p>The read-path symmetric counterpart to {@link Intent}: {@code query()} passes tuples and the
 * backend resolves them against its own keying/windowing. The namespace is carried out-of-band on
 * the {@link QueryContext}.
 *
 * @param subject the subject to read
 * @param aggregation the aggregation to read
 * @param window the window to read over
 */
public record QueryTuple(Subject subject, Aggregation aggregation, Window window) {

  /** Validates all components are present. */
  public QueryTuple {
    Objects.requireNonNull(subject, "subject");
    Objects.requireNonNull(aggregation, "aggregation");
    Objects.requireNonNull(window, "window");
  }
}
