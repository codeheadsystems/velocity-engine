// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.spi.model;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The frozen value-or-failure result of every data-plane {@code apply()}/{@code query()} (ADR
 * 0009).
 *
 * <p>A result is <strong>either</strong> a {@link Success} carrying a {@link FeatureValue}
 * <strong>or</strong> a {@link Failure} carrying a distinguishable {@link FailureCode} — never an
 * ambiguous {@code Success(value=0)}. A backend that is down or slow returns {@code
 * Failure(UNAVAILABLE)} / {@code Failure(DEADLINE_EXCEEDED)} so a synchronous authorization caller
 * can choose fail-open vs. fail-closed deterministically. Returning a silent {@code 0} for "I don't
 * know" is forbidden and is a conformance-TCK negative test (ADR 0009 rule 1, ADR 0004).
 *
 * <p>This is a sealed sum type precisely so the failure arm cannot be bolted on later: a published
 * value-only type could not grow a {@code Failure} variant without re-cutting the DTO (NFR-17). New
 * {@link FailureCode}s, by contrast, are additive.
 */
public sealed interface FeatureResult permits FeatureResult.Success, FeatureResult.Failure {

  /**
   * Whether this result is a {@link Success}.
   *
   * @return {@code true} for {@link Success}, {@code false} for {@link Failure}
   */
  default boolean isSuccess() {
    return this instanceof Success;
  }

  /**
   * A successful result.
   *
   * @param value the feature value; never null
   * @return a {@link Success} wrapping the value
   */
  static Success success(FeatureValue value) {
    return new Success(value);
  }

  /**
   * A failed result.
   *
   * @param code the distinguishable failure code; never null
   * @param detail an optional human-readable detail; may be null
   * @return a {@link Failure} wrapping the code and detail
   */
  static Failure failure(FailureCode code, @Nullable String detail) {
    return new Failure(code, detail);
  }

  /**
   * A successful feature value.
   *
   * @param value the computed feature value
   */
  record Success(FeatureValue value) implements FeatureResult {

    /** Validates the value is present. */
    public Success {
      Objects.requireNonNull(value, "value");
    }
  }

  /**
   * A distinguishable failure — never a silent {@code 0} (ADR 0009 rule 1).
   *
   * @param code the failure code
   * @param detail an optional human-readable detail; may be null
   */
  record Failure(FailureCode code, @Nullable String detail) implements FeatureResult {

    /** Validates the code is present ({@code detail} may be null). */
    public Failure {
      Objects.requireNonNull(code, "code");
    }
  }
}
