// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.spi.model;

import java.time.Duration;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The out-of-band context for an {@code apply()} call (ADR 0003).
 *
 * <p>Carries the namespace every SPI operation is scoped to (§2.1) and an optional caller deadline.
 *
 * @param namespace the tenancy scope
 * @param deadline the caller deadline; {@code null} means unbounded — but a backend SHOULD still
 *     fail fast and return {@link FailureCode#DEADLINE_EXCEEDED} rather than block indefinitely
 */
public record ApplyContext(Namespace namespace, @Nullable Duration deadline) {

  /** Validates the namespace is present ({@code deadline} may be null). */
  public ApplyContext {
    Objects.requireNonNull(namespace, "namespace");
  }
}
