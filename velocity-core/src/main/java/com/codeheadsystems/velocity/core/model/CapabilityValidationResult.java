// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.core.model;

import java.util.List;
import java.util.Objects;

/**
 * The outcome of validating feature definitions against their target backends' capabilities
 * (FR-29).
 *
 * <p>Validation collects <em>all</em> violations rather than failing on the first, so a reload is
 * rejected with a complete, precise error list. A result with no violations is {@link #valid()}.
 *
 * @param violations every violation found; empty when all definitions are valid
 */
public record CapabilityValidationResult(List<Violation> violations) {

  /** Stores an unmodifiable copy of the violations. */
  public CapabilityValidationResult {
    Objects.requireNonNull(violations, "violations");
    violations = List.copyOf(violations);
  }

  /**
   * Whether every validated definition passed.
   *
   * @return {@code true} if there are no violations
   */
  public boolean valid() {
    return violations.isEmpty();
  }

  /**
   * A single capability violation of one feature definition (FR-29).
   *
   * @param feature the offending feature's name
   * @param message the precise, human-readable reason
   */
  public record Violation(String feature, String message) {

    /** Validates both components are present. */
    public Violation {
      Objects.requireNonNull(feature, "feature");
      Objects.requireNonNull(message, "message");
    }
  }
}
